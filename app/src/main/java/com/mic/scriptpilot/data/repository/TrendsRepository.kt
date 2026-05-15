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
import javax.inject.Inject
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

    val virality = (viralityScore ?: 0).coerceIn(0, 100)
    val opportunity = (opportunityScore ?: 0).coerceIn(0, 100)
    val competition = deriveCompetitionLabel(competitionScore ?: 50)
    val momentum = momentumLabel?.takeIf { it.isNotBlank() } ?: deriveMomentum(virality)
    val summary = explanation?.sanitizeTrendExplanation().orEmpty()

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

private fun deriveCompetitionLabel(score: Int): String =
    when {
        score >= 70 -> "High"
        score >= 40 -> "Medium"
        else -> "Low"
    }

private fun deriveMomentum(virality: Int): String =
    when {
        virality >= 85 -> "Exploding"
        virality >= 70 -> "Growing Fast"
        virality >= 50 -> "Stable"
        virality >= 30 -> "Rising"
        else -> "Low"
    }
