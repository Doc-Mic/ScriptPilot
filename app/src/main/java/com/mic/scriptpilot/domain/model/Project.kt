package com.mic.scriptpilot.domain.model

data class Project(
    val id: Long = 0,
    val title: String,
    val script: String,
    val type: ProjectType,
    val timestamp: Long,
)
