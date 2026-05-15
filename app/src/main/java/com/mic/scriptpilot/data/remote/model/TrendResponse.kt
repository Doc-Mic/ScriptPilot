package com.mic.scriptpilot.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * [findTrends] returns `{ "trends": [...] }`. Legacy `{ "data": [...] }` is still accepted.
 */
data class TrendResponse(
    @SerializedName("trends", alternate = ["data"])
    val trends: List<TrendItemResponse> = emptyList(),

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null,
)

data class TrendItemResponse(
    @SerializedName("title")
    val title: String? = null,

    @SerializedName("channel", alternate = ["source"])
    val channel: String? = null,

    @SerializedName("publishedAt")
    val publishedAt: String? = null,

    @SerializedName("thumbnail")
    val thumbnail: String? = null,

    @SerializedName("virality")
    val viralityScore: Int? = null,

    @SerializedName("competition")
    val competitionScore: Int? = null,

    @SerializedName("opportunity", alternate = ["score"])
    val opportunityScore: Int? = null,

    @SerializedName("momentum")
    val momentumLabel: String? = null,

    @SerializedName("summary", alternate = ["reason"])
    val explanation: String? = null,

    @SerializedName("category")
    val category: String? = null,

    @SerializedName("region")
    val region: String? = null,
)
