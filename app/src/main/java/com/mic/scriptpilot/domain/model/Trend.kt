package com.mic.scriptpilot.domain.model


/**
 * Domain trend item from YouTube trending API.
 * Maps to [com.mic.scriptpilot.ui.trends.TrendUiModel] in the result screen.
 */
data class Trend(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val publishedAt: String,
    val viralityScore: Int,
    val opportunityScore: Int,
    val competitionLabel: String,
    val momentumLabel: String,
    val explanation: String,
    val sources: List<String>,
)