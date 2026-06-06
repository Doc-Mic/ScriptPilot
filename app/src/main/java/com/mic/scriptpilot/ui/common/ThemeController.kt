package com.mic.scriptpilot.ui.common

import androidx.appcompat.app.AppCompatDelegate

enum class AppTheme(val label: String) {
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromStoredValue(value: String?): AppTheme =
            when (value?.trim()) {
                LIGHT.name, LIGHT.label -> LIGHT
                else -> DARK
            }

        fun fromLabel(label: String): AppTheme =
            entries.firstOrNull { it.label == label } ?: DARK
    }
}

object ThemeController {
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"

    fun normalize(mode: String): String =
        AppTheme.fromStoredValue(mode).name

    fun labelFor(mode: String): String =
        AppTheme.fromStoredValue(mode).label

    fun apply(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (AppTheme.fromStoredValue(mode)) {
                AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }
}
