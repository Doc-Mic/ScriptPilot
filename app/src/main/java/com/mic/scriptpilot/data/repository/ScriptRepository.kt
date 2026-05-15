package com.mic.scriptpilot.data.repository

import com.mic.scriptpilot.domain.model.ScriptOutline

interface ScriptRepository {
    suspend fun generateLongFormScript(
        idea: String,
        durationLabel: String,
        tone: String,
    ): ScriptOutline

    suspend fun generateShortScript(topic: String): String
}
