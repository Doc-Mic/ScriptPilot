package com.mic.scriptpilot.data.repository

import com.mic.scriptpilot.domain.model.Trend

interface TrendRepository {
    suspend fun findTrends(
        categoryId: Int,
        location: String,
        timeRange: String,
    ): List<Trend>
}
