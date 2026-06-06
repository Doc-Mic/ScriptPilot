package com.mic.scriptpilot.data.repository

import android.util.Log
import com.mic.scriptpilot.data.remote.ScriptPilotApi
import com.mic.scriptpilot.data.remote.SeoRequest
import com.mic.scriptpilot.data.remote.descriptionsList
import com.mic.scriptpilot.data.remote.effectiveTags
import com.mic.scriptpilot.data.remote.effectiveTitles
import com.mic.scriptpilot.data.remote.logParsed
import com.mic.scriptpilot.data.remote.requireSeoPayload
import com.mic.scriptpilot.domain.model.SeoGeneration
import com.mic.scriptpilot.domain.model.SeoResultKind
import com.mic.scriptpilot.domain.model.SeoResultLine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ApiSeoRepository @Inject constructor(
    private val scriptPilotApi: ScriptPilotApi,
) : SeoRepository {
    override suspend fun generate(
        scriptDraft: String,
        topicHint: String,
        contentTypes: List<String>,
    ): SeoGeneration =
        withContext(Dispatchers.IO) {
            val response =
                scriptPilotApi.generateSeo(
                    SeoRequest(
                        scriptDraft = scriptDraft,
                        topicHint = topicHint,
                        contentTypes = contentTypes,
                    ),
                )
            Log.d(
                TAG,
                "generateSeo raw: titles=${response.effectiveTitles().size}, descriptions=${response.descriptionsList().size}, tags=${response.effectiveTags().size}, error=${response.error}, legacy=${response.legacy != null}",
            )
            response.requireSeoPayload("generateSeo")

            val titles =
                response.effectiveTitles()
                    .ensureTitleCount(topicHint, scriptDraft)
                    .take(5)
            val description =
                response.descriptionsList()
                    .firstOrNull()
                    .orEmpty()
                    .ensureLongSeoDescription(topicHint, scriptDraft, titles)

            val result =
                SeoGeneration(
                    titles = titles.toLines(SeoResultKind.TITLE),
                    descriptions = listOf(description).toLines(SeoResultKind.DESCRIPTION),
                    tags = response.effectiveTags()
                        .ensureTagRange(topicHint, scriptDraft, titles)
                        .toLines(SeoResultKind.TAG),
                )
            logParsed(
                "generateSeo",
                "titles=${result.titles.size}, descriptions=${result.descriptions.size}, tags=${result.tags.size}",
            )
            result
        }

    private companion object {
        const val TAG = "ApiSeoRepository"
    }
}

private fun List<String>.toLines(kind: SeoResultKind): List<SeoResultLine> =
    mapNotNull { text ->
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            null
        } else {
            SeoResultLine(
                id = UUID.randomUUID().toString(),
                kind = kind,
                text = trimmed,
            )
        }
    }

private fun List<String>.ensureTagRange(topicHint: String, scriptDraft: String, titles: List<String>): List<String> {
    val cleaned = mapNotNull { it.cleanTagOrNull() }.distinctBy { it.lowercase() }.toMutableList()
    if (cleaned.size >= MIN_TAG_COUNT) return cleaned.take(MAX_TAG_COUNT)

    val fallbackSource = listOf(topicHint, titles.joinToString(" "), scriptDraft.take(600)).joinToString(" ")
    val words =
        fallbackSource
            .split(Regex("[^A-Za-z0-9]+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()

    words.forEach { word ->
        if (cleaned.size >= MIN_TAG_COUNT) return@forEach
        cleaned.add(word)
    }
    words.windowed(size = 2, step = 1).forEach { pair ->
        if (cleaned.size >= MIN_TAG_COUNT) return@forEach
        cleaned.add(pair.joinToString(" "))
    }

    val genericTags =
        listOf(
            "youtube tips",
            "content creation",
            "creator tools",
            "video ideas",
            "youtube seo",
            "creator workflow",
            "video strategy",
            "audience growth",
            "content planning",
            "online video",
            "digital creators",
            "video marketing",
            "creator productivity",
            "youtube growth",
            "content ideas",
        )
    genericTags.forEach { tag ->
        if (cleaned.size < MIN_TAG_COUNT && cleaned.none { it.equals(tag, ignoreCase = true) }) {
            cleaned.add(tag)
        }
    }

    return cleaned.take(MAX_TAG_COUNT)
}

private fun String.cleanTagOrNull(): String? =
    trim()
        .removePrefix("#")
        .replace(Regex("\\s+"), " ")
        .takeIf { it.isNotBlank() }
        ?.take(42)

private const val MIN_TAG_COUNT = 15
private const val MAX_TAG_COUNT = 20

private fun List<String>.ensureTitleCount(topicHint: String, scriptDraft: String): List<String> {
    val cleaned = map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.toMutableList()
    val topic = topicHint.ifBlank {
        scriptDraft.lineSequence().firstOrNull { it.isNotBlank() }?.take(80).orEmpty()
    }.ifBlank {
        "Your Next YouTube Video"
    }
    val fallbacks =
        listOf(
            "$topic: What You Need to Know",
            "I Tried $topic So You Don't Have To",
            "The Smart Creator's Guide to $topic",
            "Why $topic Matters Right Now",
            "$topic Explained: Tips, Mistakes, and Takeaways",
        )
    fallbacks.forEach { title ->
        if (cleaned.size < 5 && cleaned.none { it.equals(title, ignoreCase = true) }) {
            cleaned.add(title)
        }
    }
    return cleaned.take(5)
}

private fun String.ensureLongSeoDescription(topicHint: String, scriptDraft: String, titles: List<String>): String {
    val clean = trim()
    if (clean.wordCount() >= 300) return clean

    val topic =
        topicHint.ifBlank { titles.firstOrNull().orEmpty() }
            .ifBlank { scriptDraft.lineSequence().firstOrNull { it.isNotBlank() }?.take(100).orEmpty() }
            .ifBlank { "this video" }
    val keywords =
        listOf(topicHint, titles.joinToString(" "), scriptDraft.take(800))
            .joinToString(" ")
            .split(Regex("[^A-Za-z0-9]+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 4 && it !in STOP_WORDS }
            .distinct()
            .take(8)
            .joinToString(", ")
            .ifBlank { "YouTube growth, content planning, creator workflow" }
    val original = clean.ifBlank {
        "This video walks through $topic in a clear, practical way for creators and viewers who want useful takeaways."
    }
    return """
        $original

        In this video, we take a practical look at $topic and break it down in a way that is easy to follow, useful, and ready to apply. Whether you are researching the subject for the first time or trying to sharpen your existing workflow, this guide gives you the context, examples, and creator-focused insights you need without unnecessary fluff.

        The main goal is to help viewers understand the topic clearly while also seeing why it matters right now. We cover the important ideas, explain the key points naturally, and connect them to real outcomes that viewers can use in their own projects, studies, channels, or daily work. Important themes include $keywords, with the keywords included naturally so the description stays readable and helpful.

        By watching, you will learn what to focus on, what mistakes to avoid, and how to think about the topic from a smarter content and productivity perspective. You will also get a better sense of how this subject fits into modern YouTube creation, planning, audience growth, and practical decision-making.

        This description is designed for real YouTube packaging: it gives the algorithm enough context to understand the video while giving human viewers a clear reason to click, watch, and stay engaged. The wording keeps the topic searchable without repeating keywords unnaturally, so it can support discovery while still sounding like it belongs on a polished creator channel.

        If this video helps you, like the video, subscribe for more creator-friendly breakdowns, and leave a comment with the topic you want covered next. Share it with someone who wants to create faster, plan better videos, or publish with more confidence.

        #ScriptPilot #YouTubeTips #ContentCreation #CreatorTools
    """.trimIndent()
}

private fun String.wordCount(): Int =
    Regex("""[A-Za-z0-9]+(?:['-][A-Za-z0-9]+)?""").findAll(this).count()

private val STOP_WORDS =
    setOf(
        "the",
        "and",
        "for",
        "with",
        "your",
        "you",
        "this",
        "that",
        "from",
        "into",
        "about",
        "video",
        "script",
        "topic",
        "how",
        "why",
        "what",
    )
