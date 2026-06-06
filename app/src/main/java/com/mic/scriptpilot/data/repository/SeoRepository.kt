package com.mic.scriptpilot.data.repository

import com.mic.scriptpilot.domain.model.SeoGeneration

interface SeoRepository {
    suspend fun generate(
        scriptDraft: String,
        topicHint: String,
        contentTypes: List<String>,
    ): SeoGeneration
}
