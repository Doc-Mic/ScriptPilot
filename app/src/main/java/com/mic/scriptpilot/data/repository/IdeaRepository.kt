package com.mic.scriptpilot.data.repository

import com.mic.scriptpilot.domain.model.IdeaItem

interface IdeaRepository {
    suspend fun generateIdeas(topic: String, style: String): List<IdeaItem>
}
