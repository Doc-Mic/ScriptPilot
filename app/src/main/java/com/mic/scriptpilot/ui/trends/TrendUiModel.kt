package com.mic.scriptpilot.ui.trends

import com.mic.scriptpilot.domain.model.Trend

/**
 * UI-ready trend row for the premium trend dashboard list.
 */
data class TrendUiModel(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val publishedAt: String,
    val momentumLabel: String,
    val virality: Int,
    val opportunity: Int,
    val competitionLabel: String,
    val explanation: String,
    val sources: List<String>,
)

fun Trend.toUiModel(): TrendUiModel =
    TrendUiModel(
        id               = id,
        title            = title,
        channel          = channel,
        thumbnail        = thumbnail,
        publishedAt      = publishedAt,
        momentumLabel    = momentumLabel,
        virality         = viralityScore.coerceIn(0, 100),
        opportunity      = opportunityScore.coerceIn(0, 100),
        competitionLabel = competitionLabel,
        explanation      = explanation,
        sources          = sources,
    )