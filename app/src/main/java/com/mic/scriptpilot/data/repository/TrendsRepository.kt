package com.mic.scriptpilot.data.repository

import android.content.Context
import android.util.Log
import com.mic.scriptpilot.R
import com.mic.scriptpilot.data.remote.TrendsApiService
import com.mic.scriptpilot.data.remote.model.TrendItemResponse
import com.mic.scriptpilot.data.remote.model.sanitizeTrendExplanation
import com.mic.scriptpilot.domain.model.Trend
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class TrendsRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val apiService: TrendsApiService,
) : TrendRepository {

    override suspend fun findTrends(
        categoryId: Int,
        location: String,
        timeRange: String,
    ): List<Trend> =
        withContext(Dispatchers.IO) {
            val category = categoryId.toApiCategoryQuery()
            val region = location.toRegionCode()
            try {
                val response =
                    apiService.getTrends(
                        category = category,
                        region = region,
                        maxResults = 20,
                    )

                Log.d(
                    TAG,
                    "getTrends parsed: count=${response.trends.size}, message=${response.message}, error=${response.error}",
                )

                if (!response.error.isNullOrBlank() && response.trends.isEmpty()) {
                    throw Exception(response.error ?: appContext.getString(R.string.trend_error_title))
                }

                response.trends.mapIndexedNotNull { index, item ->
                    item.toDomain(index = index, timeRange = timeRange)
                }
            } catch (e: HttpException) {
                val code = e.code()
                val errBody =
                    runCatching { e.response()?.errorBody()?.string() }
                        .getOrNull()
                        .orEmpty()
                Log.e(
                    TAG,
                    "getTrends HTTP failure code=$code category=$category region=$region body=$errBody",
                    e,
                )
                val message =
                    when (code) {
                        404 -> appContext.getString(R.string.error_trends_http_404)
                        in 500..599 -> appContext.getString(R.string.error_trends_http_server)
                        else -> appContext.getString(R.string.error_trends_http_generic, code)
                    }
                throw Exception(message, e)
            } catch (e: IOException) {
                Log.e(TAG, "getTrends I/O error category=$category region=$region", e)
                throw Exception(appContext.getString(R.string.error_trends_network), e)
            } catch (e: Exception) {
                Log.e(TAG, "getTrends unexpected error category=$category region=$region", e)
                throw Exception(appContext.getString(R.string.error_trends_network), e)
            }
        }

    private companion object {
        const val TAG = "TrendsRepository"
    }
}

/* ================================
   🌍 LOCATION → REGION CODE
================================ */
private fun String.toRegionCode(): String =
    when (trim().lowercase()) {
        "global", "worldwide", "" -> "US"
        "united states", "us", "usa" -> "US"
        "united kingdom", "uk", "gb" -> "GB"
        "india", "in" -> "IN"
        "canada", "ca" -> "CA"
        "australia", "au" -> "AU"
        "pakistan", "pk" -> "PK"
        "bangladesh", "bd" -> "BD"
        "germany", "de" -> "DE"
        "france", "fr" -> "FR"
        "brazil", "br" -> "BR"
        "japan", "jp" -> "JP"
        "south korea", "kr" -> "KR"
        "nigeria", "ng" -> "NG"
        "egypt", "eg" -> "EG"
        "saudi arabia", "sa" -> "SA"
        "uae", "united arab emirates" -> "AE"
        "turkey", "tr" -> "TR"
        "indonesia", "id" -> "ID"
        "mexico", "mx" -> "MX"
        "spain", "es" -> "ES"
        "italy", "it" -> "IT"
        "netherlands", "nl" -> "NL"
        "philippines", "ph" -> "PH"
        "malaysia", "my" -> "MY"
        else -> "US"
    }

/* ================================
   📦 RESPONSE → DOMAIN MODEL
================================ */
private fun Int.toApiCategoryQuery(): String? = takeIf { it > 0 }?.toString()

private fun TrendItemResponse.toDomain(index: Int, timeRange: String): Trend? {
    val safeTitle = title?.trim().orEmpty()
    if (safeTitle.isEmpty()) return null

    val daysOld = publishedAt.daysOldOrNull()
    val virality =
        deriveRealisticVirality(
            rawScore = viralityScore,
            title = safeTitle,
            index = index,
            daysOld = daysOld,
            viewCount = viewCount,
            likeCount = likeCount,
            commentCount = commentCount,
            timeRange = timeRange,
        )
    val competition =
        deriveCompetitionLabel(
            rawScore = competitionScore,
            title = safeTitle,
            category = category,
            virality = virality,
            index = index,
            viewCount = viewCount,
        )
    val opportunity =
        deriveOpportunityScore(
            rawScore = opportunityScore,
            virality = virality,
            competitionLabel = competition,
            title = safeTitle,
            index = index,
            daysOld = daysOld,
        )
    val momentum = normalizeMomentumLabel(momentumLabel, virality, competition, daysOld, index)
    val summary =
        explanation
            ?.sanitizeTrendExplanation()
            ?.takeUnless { it.isGenericTrendSummary() }
            ?: localTrendSummary(safeTitle, momentum, competition, opportunity)

    return Trend(
        id = "trend_${index}_${safeTitle.lowercase().replace(' ', '_')}",
        title = safeTitle,
        channel = channel?.trim().orEmpty(),
        thumbnail = thumbnail.orEmpty(),
        publishedAt = publishedAt.orEmpty(),
        viralityScore = virality,
        opportunityScore = opportunity,
        competitionLabel = competition,
        momentumLabel = momentum,
        explanation = summary,
        sources = emptyList(),
    )
}

private fun deriveRealisticVirality(
    rawScore: Int?,
    title: String,
    index: Int,
    daysOld: Double?,
    viewCount: Long?,
    likeCount: Long?,
    commentCount: Long?,
    timeRange: String,
): Int {
    val raw = rawScore?.coerceIn(0, 100)
    val variation = deterministicVariation("$title|virality|$index|$timeRange", -5, 5)
    val recencyScore = recencyScore(daysOld)
    val rankScore = (100 - index * 3).coerceIn(42, 100)

    val metricScore =
        if (viewCount != null && viewCount > 0L) {
            val safeDays = max(daysOld ?: 3.0, 1.0)
            val viewsPerDay = viewCount / safeDays
            val engagement =
                ((likeCount ?: 0L) + (commentCount ?: 0L) * 2).toDouble() / viewCount
            val viewPace = normalizeLog(viewsPerDay, 1_500.0, 3_500_000.0)
            val engagementScore = (((engagement - 0.004) / 0.075) * 100).toInt().coerceIn(18, 100)
            (viewPace * 0.44 + engagementScore * 0.22 + recencyScore * 0.18 + rankScore * 0.16).toInt()
        } else {
            val normalizedRaw =
                when {
                    raw == null || raw == 0 -> 72 - index.coerceAtMost(8)
                    raw >= 99 -> 92 - (index % 7)
                    raw >= 94 -> raw - 4
                    else -> raw
                }
            (normalizedRaw * 0.78 + recencyScore * 0.12 + rankScore * 0.1).toInt()
        }

    return (metricScore + variation).coerceIn(62, 98)
}

private fun deriveOpportunityScore(
    rawScore: Int?,
    virality: Int,
    competitionLabel: String,
    title: String,
    index: Int,
    daysOld: Double?,
): Int {
    val competitionScore =
        when (competitionLabel) {
            "High" -> 78
            "Medium" -> 55
            else -> 28
        }
    val nicheBoost = if (title.hasNicheSignals()) 8 else 0
    val variation = deterministicVariation("$title|opportunity|$index", -4, 4)
    val computed =
        (virality * 0.5 +
            (100 - competitionScore) * 0.3 +
            recencyScore(daysOld) * 0.12 +
            nicheBoost +
            variation).toInt()
    val raw = rawScore?.coerceIn(0, 100)
    val blended =
        when {
            raw == null || raw == 0 -> computed
            raw >= 98 -> (computed * 0.82 + raw * 0.18).toInt()
            else -> (computed * 0.65 + raw * 0.35).toInt()
        }
    return blended.coerceIn(55, if (competitionLabel == "High") 90 else 96)
}

private fun deriveCompetitionLabel(
    rawScore: Int?,
    title: String,
    category: String?,
    virality: Int,
    index: Int,
    viewCount: Long?,
): String {
    val raw = rawScore?.coerceIn(0, 100) ?: 45
    val broadBoost = title.broadTopicBoost() + category.categoryCompetitionBoost()
    val nichePenalty = if (title.hasNicheSignals()) 16 else 0
    val viewsBoost =
        when {
            (viewCount ?: 0L) >= 8_000_000L -> 20
            (viewCount ?: 0L) >= 2_000_000L -> 12
            virality >= 90 -> 12
            virality >= 82 -> 7
            else -> 0
        }
    val adjusted =
        (raw * 0.45 +
            broadBoost +
            viewsBoost +
            (100 - index * 4).coerceIn(20, 100) * 0.12 -
            nichePenalty +
            deterministicVariation("$title|competition|$index", -5, 5)).toInt()

    return when {
        adjusted >= 70 -> "High"
        adjusted >= 42 -> "Medium"
        else -> "Low"
    }
}

private fun normalizeMomentumLabel(
    label: String?,
    virality: Int,
    competitionLabel: String,
    daysOld: Double?,
    index: Int,
): String {
    val normalized =
        when (label?.trim()?.lowercase(Locale.US)) {
            "exploding" -> if (virality >= 94) "Exploding" else null
            "rising fast", "growing fast" -> "Rising Fast"
            "trending" -> "Trending"
            "emerging", "rising" -> "Emerging"
            "hot topic" -> "Hot Topic"
            else -> null
        }
    return normalized ?: deriveMomentum(virality, competitionLabel, daysOld, index)
}

private fun deriveMomentum(
    virality: Int,
    competitionLabel: String,
    daysOld: Double?,
    index: Int,
): String =
    when {
        virality >= 94 && (daysOld ?: 7.0) <= 3.0 -> "Exploding"
        virality >= 86 -> "Rising Fast"
        virality >= 79 && competitionLabel == "High" -> "Hot Topic"
        virality >= 72 -> "Trending"
        (daysOld ?: 7.0) <= 5.0 || index >= 10 -> "Emerging"
        else -> "Trending"
    }

private fun String?.daysOldOrNull(): Double? {
    if (isNullOrBlank()) return null
    val parser =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    val published = runCatching { parser.parse(this@daysOldOrNull) }.getOrNull() ?: return null
    val elapsed = System.currentTimeMillis() - published.time
    return elapsed.toDouble() / (1000.0 * 60 * 60 * 24)
}

private fun recencyScore(daysOld: Double?): Int =
    when {
        daysOld == null -> 72
        daysOld <= 1.0 -> 100
        daysOld <= 3.0 -> 91
        daysOld <= 7.0 -> 80
        daysOld <= 14.0 -> 67
        daysOld <= 30.0 -> 55
        else -> 45
    }

private fun normalizeLog(value: Double, min: Double, max: Double): Int {
    val safeValue = max(value, 1.0)
    val minLog = ln(min) / ln(10.0)
    val maxLog = ln(max) / ln(10.0)
    val valueLog = ln(safeValue) / ln(10.0)
    return (((valueLog - minLog) / (maxLog - minLog)) * 100).toInt().coerceIn(10, 100)
}

private fun deterministicVariation(seed: String, min: Int, max: Int): Int {
    val hash = seed.fold(0) { acc, char -> acc * 31 + char.code }.and(Int.MAX_VALUE)
    return min + (hash % (max - min + 1))
}

private fun String.broadTopicBoost(): Int {
    val lower = lowercase(Locale.US)
    val broadKeywords =
        listOf(
            "official",
            "music video",
            "trailer",
            "movie",
            "celebrity",
            "premiere",
            "live",
            "highlights",
            "breaking",
            "election",
            "world cup",
            "episode",
            "full match",
        )
    return if (broadKeywords.any(lower::contains)) 24 else 0
}

private fun String?.categoryCompetitionBoost(): Int =
    when (this?.trim()) {
        "10", "17", "20", "24", "25" -> 14
        else -> 0
    }

private fun String.hasNicheSignals(): Boolean {
    val lower = lowercase(Locale.US)
    val nicheKeywords =
        listOf(
            "how to",
            "tutorial",
            "guide",
            "tips",
            "workflow",
            "tools",
            "review",
            "explained",
            "for beginners",
            "case study",
            "setup",
            "template",
            "automation",
        )
    return nicheKeywords.any(lower::contains)
}

private fun String.isGenericTrendSummary(): Boolean {
    val lower = lowercase(Locale.US)
    return lower.contains("viral youtube trend") ||
        lower.contains("trending creator niche") ||
        lower == "this is a viral youtube trend."
}

private fun localTrendSummary(
    title: String,
    momentum: String,
    competition: String,
    opportunity: Int,
): String =
    when {
        momentum == "Exploding" ->
            "$title is accelerating quickly, giving creators a timely angle before the space gets crowded."
        momentum == "Rising Fast" ->
            "$title is gaining fresh momentum, with room for focused explainers, reactions, or quick tutorials."
        momentum == "Hot Topic" || competition == "High" ->
            "$title has broad audience pull right now, so sharper niche positioning can help creators stand out."
        opportunity >= 82 ->
            "$title is still opening up, creating space for practical creator angles and fast-turnaround videos."
        else ->
            "$title is building steady interest, with useful room for creator-led context and audience-specific takes."
    }
