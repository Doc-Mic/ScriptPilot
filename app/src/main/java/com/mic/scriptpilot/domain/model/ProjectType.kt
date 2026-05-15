package com.mic.scriptpilot.domain.model

enum class ProjectType(val storageKey: String) {
    IDEA("idea"),
    SCRIPT("script"),
    SHORT("short");

    companion object {
        fun fromStorage(value: String): ProjectType =
            entries.find { it.storageKey == value } ?: SCRIPT
    }
}
