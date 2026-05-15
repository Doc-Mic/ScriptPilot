package com.mic.scriptpilot.data.remote

import com.google.gson.annotations.SerializedName

class ScriptPilotApiException(message: String) : Exception(message)

data class GenerateIdeasResponse(
    /** Supports legacy `{ "data": [ ... ] }` idea lists from older deployments. */
    @SerializedName("ideas", alternate = ["data"])
    val ideas: List<IdeaItemDto> = emptyList(),
    @SerializedName("error")
    val error: String? = null,
)

data class IdeaItemDto(
    @SerializedName("title")
    val title: String? = "",
    @SerializedName("hook")
    val hook: String? = "",
    @SerializedName("angle")
    val angle: String? = "",
    @SerializedName("targetAudience")
    val targetAudience: String? = "",
    @SerializedName("format")
    val format: String? = "",
)

data class CreateScriptResponse(
    @SerializedName("script")
    val script: CreateScriptPayload? = null,
    /** Older `{ "data": { "hook", "sections", ... } }` long-form shape. */
    @SerializedName("data")
    val legacyLongForm: LegacyLongScriptBlock? = null,
    @SerializedName("error")
    val error: String? = null,
)

data class LegacyLongScriptBlock(
    @SerializedName("hook")
    val hook: String? = "",
    @SerializedName("intro")
    val intro: String? = "",
    @SerializedName("sections")
    val sections: List<LegacyScriptSectionDto> = emptyList(),
    @SerializedName("outro")
    val outro: String? = "",
)

data class LegacyScriptSectionDto(
    @SerializedName("heading")
    val heading: String? = "",
    @SerializedName("content")
    val content: String? = "",
)

data class CreateScriptPayload(
    @SerializedName("title")
    val title: String? = "",
    @SerializedName("hook")
    val hook: String? = "",
    @SerializedName("intro")
    val intro: String? = "",
    @SerializedName("body")
    val body: String? = "",
    @SerializedName("outro")
    val outro: String? = "",
    @SerializedName("cta")
    val cta: String? = "",
    @SerializedName("duration")
    val duration: String? = "",
    @SerializedName("tone")
    val tone: String? = "",
)

data class CreateShortResponse(
    @SerializedName("script")
    val script: String? = null,
    /** Older `{ "data": { "hook","body","cta" } }` shorts shape. */
    @SerializedName("data")
    val legacyShort: LegacyShortBlock? = null,
    @SerializedName("error")
    val error: String? = null,
)

data class LegacyShortBlock(
    @SerializedName("hook")
    val hook: String? = "",
    @SerializedName("body")
    val body: String? = "",
    @SerializedName("cta")
    val cta: String? = "",
)

data class SeoAssistantResponse(
    @SerializedName("titles")
    val titles: List<String> = emptyList(),
    @SerializedName("descriptions")
    val descriptions: List<String> = emptyList(),
    /** Legacy single-description field from older deployments. */
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("tags")
    val tags: List<String> = emptyList(),
    @SerializedName("error")
    val error: String? = null,
    /** Older `{ "data": { titles, tags, ... } }` envelope. */
    @SerializedName("data")
    val legacy: SeoLegacyBlock? = null,
)

data class SeoLegacyBlock(
    @SerializedName("titles")
    val titles: List<String> = emptyList(),
    @SerializedName("descriptions")
    val descriptions: List<String> = emptyList(),
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("tags")
    val tags: List<String> = emptyList(),
)
