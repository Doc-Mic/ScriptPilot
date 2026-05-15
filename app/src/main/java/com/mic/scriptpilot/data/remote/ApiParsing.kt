package com.mic.scriptpilot.data.remote

import android.util.Log
import com.mic.scriptpilot.domain.model.ScriptOutline

private const val LOG_TAG = "ScriptPilotApi"

internal fun String?.requireNonBlank(field: String): String {
    val v = this?.trim().orEmpty()
    if (v.isEmpty()) {
        throw ScriptPilotApiException("Missing or empty '$field' in API response.")
    }
    return v
}

internal fun GenerateIdeasResponse.requireIdeasPayload(operation: String) {
    if (!error.isNullOrBlank()) {
        Log.e(LOG_TAG, "$operation failed: $error")
        throw ScriptPilotApiException("$operation: $error")
    }
}

internal fun CreateScriptResponse.resolveCreateScriptPayload(
    operation: String,
    ideaTitle: String,
    durationLabel: String,
    tone: String,
): CreateScriptPayload {
    if (!error.isNullOrBlank()) {
        Log.e(LOG_TAG, "$operation failed: $error")
        throw ScriptPilotApiException("$operation: $error")
    }
    script?.let { return it }
    legacyLongForm?.let { return it.toCreateScriptPayload(ideaTitle, durationLabel, tone) }
    throw ScriptPilotApiException("$operation: script object is missing.")
}

internal fun CreateShortResponse.resolveShortScriptText(operation: String): String {
    if (!error.isNullOrBlank()) {
        Log.e(LOG_TAG, "$operation failed: $error")
        throw ScriptPilotApiException("$operation: $error")
    }
    script?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val b = legacyShort
    if (b != null) {
        val parts =
            listOfNotNull(
                b.hook?.trim(),
                b.body?.trim(),
                b.cta?.trim(),
            ).filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) return parts.joinToString("\n\n")
    }
    throw ScriptPilotApiException("$operation: script text is missing.")
}

internal fun SeoAssistantResponse.requireSeoPayload(operation: String) {
    if (!error.isNullOrBlank()) {
        Log.e(LOG_TAG, "$operation failed: $error")
        throw ScriptPilotApiException("$operation: $error")
    }
}

internal fun SeoAssistantResponse.effectiveTitles(): List<String> {
    val a = titles.filter { it.isNotBlank() }
    if (a.isNotEmpty()) return a
    return legacy?.titles?.filter { it.isNotBlank() } ?: emptyList()
}

internal fun SeoAssistantResponse.descriptionsList(): List<String> {
    val root = descriptions.filter { it.isNotBlank() }
    if (root.isNotEmpty()) return root
    val leg = legacy?.descriptions?.filter { it.isNotBlank() }.orEmpty()
    if (leg.isNotEmpty()) return leg
    val single = (description ?: legacy?.description)?.trim().orEmpty()
    return if (single.isNotEmpty()) listOf(single) else emptyList()
}

internal fun SeoAssistantResponse.effectiveTags(): List<String> {
    val a = tags.filter { it.isNotBlank() }
    if (a.isNotEmpty()) return a
    return legacy?.tags?.filter { it.isNotBlank() } ?: emptyList()
}

private fun List<LegacyScriptSectionDto>.toBodyMarkdown(): String =
    mapNotNull { section ->
        val heading = section.heading?.trim().orEmpty()
        val content = section.content?.trim().orEmpty()
        when {
            heading.isNotEmpty() && content.isNotEmpty() -> "### $heading\n\n$content"
            heading.isNotEmpty() -> "### $heading"
            content.isNotEmpty() -> content
            else -> null
        }
    }.joinToString("\n\n")
        .trim()

private fun LegacyLongScriptBlock.toCreateScriptPayload(
    titleLine: String,
    duration: String,
    toneLine: String,
): CreateScriptPayload {
    val bodyText = sections.toBodyMarkdown()
    val introClean = intro?.trim().orEmpty()
    return CreateScriptPayload(
        title = titleLine,
        hook = hook?.trim().orEmpty(),
        intro = introClean,
        body = bodyText.ifEmpty { introClean },
        outro = outro?.trim().orEmpty(),
        cta = "Subscribe and turn on notifications.",
        duration = duration,
        tone = toneLine,
    )
}

internal fun CreateScriptPayload.toScriptOutline(): ScriptOutline {
    val hookText =
        hook?.trim().orEmpty().ifEmpty {
            title?.trim().orEmpty().ifEmpty { "—" }
        }
    val introText = intro?.trim().orEmpty()
    val outroText = outro?.trim().orEmpty()
    val bodyRaw = body?.trim().orEmpty()
    val bodyText =
        if (bodyRaw.isNotEmpty()) {
            bodyRaw
        } else {
            listOfNotNull(
                cta?.trim()?.takeIf { it.isNotEmpty() }?.let { "### Call to action\n\n$it" },
            ).joinToString("\n\n").trim()
        }
    return ScriptOutline(
        hook = hookText,
        intro = introText,
        body = bodyText,
        outro = outroText,
    )
}

internal fun logParsed(operation: String, detail: String) {
    Log.d(LOG_TAG, "$operation parsed OK: $detail")
}
