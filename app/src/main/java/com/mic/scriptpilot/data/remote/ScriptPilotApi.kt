package com.mic.scriptpilot.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ScriptPilotApi {
    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
    )
    @POST("generateIdeas")
    suspend fun generateIdeas(@Body request: IdeaRequest): GenerateIdeasResponse

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
    )
    @POST("createScript")
    suspend fun generateLongScript(@Body request: LongScriptRequest): CreateScriptResponse

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
    )
    @POST("createShort")
    suspend fun generateShortScript(@Body request: ShortScriptRequest): CreateShortResponse

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate",
        "Pragma: no-cache",
    )
    @POST("seoAssistant")
    suspend fun generateSeo(@Body request: SeoRequest): SeoAssistantResponse
}

data class IdeaRequest(
    @SerializedName("topic")
    val topic: String,
    @SerializedName("style")
    val style: String,
)

data class LongScriptRequest(
    @SerializedName("idea")
    val idea: String,
    @SerializedName("durationLabel")
    val durationLabel: String,
    @SerializedName("tone")
    val tone: String,
)

data class ShortScriptRequest(
    @SerializedName("topic")
    val topic: String,
)

data class SeoRequest(
    @SerializedName("scriptDraft")
    val scriptDraft: String,
    @SerializedName("workingTitle")
    val workingTitle: String = "",
    @SerializedName("topicHint")
    val topicHint: String,
    @SerializedName("contentTypes")
    val contentTypes: List<String> = emptyList(),
)
