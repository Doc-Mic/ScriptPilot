package com.mic.scriptpilot.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mic.scriptpilot.ui.common.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CreatorPreferences(
    val defaultScriptTone: String = ProfilePreferencesRepository.DEFAULT_SCRIPT_TONE,
    val defaultContentStyle: String = ProfilePreferencesRepository.DEFAULT_CONTENT_STYLE,
    val autoSaveProjects: Boolean = false,
    val themeMode: String = ProfilePreferencesRepository.DEFAULT_THEME_MODE,
    val pushNotifications: Boolean = false,
    val weeklyCreatorTips: Boolean = true,
)

data class UsageCounters(
    val scriptsCreated: Int = 0,
    val shortsGenerated: Int = 0,
    val seoGenerations: Int = 0,
    val ideasGenerated: Int = 0,
    val trendsExplored: Int = 0,
)

@Singleton
class ProfilePreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _creatorPreferences = MutableStateFlow(readCreatorPreferences())
    val creatorPreferences: StateFlow<CreatorPreferences> = _creatorPreferences.asStateFlow()

    private val _usageCounters = MutableStateFlow(readUsageCounters())
    val usageCounters: StateFlow<UsageCounters> = _usageCounters.asStateFlow()

    @Synchronized
    fun setDefaultScriptTone(value: String) {
        prefs.edit { putString(KEY_DEFAULT_SCRIPT_TONE, value) }
        refreshCreatorPreferences()
    }

    @Synchronized
    fun setDefaultContentStyle(value: String) {
        prefs.edit { putString(KEY_DEFAULT_CONTENT_STYLE, value) }
        refreshCreatorPreferences()
    }

    @Synchronized
    fun setAutoSaveProjects(enabled: Boolean) {
        // TODO: Apply this to automatic project creation once all generation outputs map cleanly to ProjectType.
        prefs.edit { putBoolean(KEY_AUTO_SAVE_PROJECTS, enabled) }
        refreshCreatorPreferences()
    }

    @Synchronized
    fun setThemeMode(value: String) {
        prefs.edit { putString(KEY_THEME_MODE, normalizeThemeMode(value)) }
        refreshCreatorPreferences()
    }

    @Synchronized
    fun setPushNotifications(enabled: Boolean) {
        // TODO: Connect this preference to Firebase Messaging when push campaigns are added.
        prefs.edit { putBoolean(KEY_PUSH_NOTIFICATIONS, enabled) }
        refreshCreatorPreferences()
    }

    @Synchronized
    fun setWeeklyCreatorTips(enabled: Boolean) {
        // TODO: Connect this preference to the future creator tips notification scheduler.
        prefs.edit { putBoolean(KEY_WEEKLY_CREATOR_TIPS, enabled) }
        refreshCreatorPreferences()
    }

    @Synchronized
    fun resetCreatorPreferences() {
        prefs.edit {
            remove(KEY_DEFAULT_SCRIPT_TONE)
            remove(KEY_DEFAULT_CONTENT_STYLE)
            remove(KEY_AUTO_SAVE_PROJECTS)
            remove(KEY_LEGACY_AUTO_SAVE_GENERATIONS)
            remove(KEY_LEGACY_DEFAULT_SCRIPT_DURATION)
            remove(KEY_THEME_MODE)
            remove(KEY_PUSH_NOTIFICATIONS)
            remove(KEY_WEEKLY_CREATOR_TIPS)
        }
        refreshCreatorPreferences()
    }

    fun incrementScriptsCreated() {
        incrementCounter(KEY_SCRIPTS_CREATED)
    }

    fun incrementShortsGenerated() {
        incrementCounter(KEY_SHORTS_GENERATED)
    }

    fun incrementSeoGenerations() {
        incrementCounter(KEY_SEO_GENERATIONS)
    }

    fun addIdeasGenerated(count: Int) {
        if (count <= 0) return
        incrementCounter(KEY_IDEAS_GENERATED, count)
    }

    fun incrementTrendsExplored() {
        incrementCounter(KEY_TRENDS_EXPLORED)
    }

    @Synchronized
    fun resetUsageCounters() {
        prefs.edit {
            remove(KEY_SCRIPTS_CREATED)
            remove(KEY_SHORTS_GENERATED)
            remove(KEY_SEO_GENERATIONS)
            remove(KEY_IDEAS_GENERATED)
            remove(KEY_TRENDS_EXPLORED)
        }
        refreshUsageCounters()
    }

    @Synchronized
    private fun incrementCounter(key: String, amount: Int = 1) {
        val next = prefs.getInt(key, 0) + amount
        prefs.edit { putInt(key, next.coerceAtLeast(0)) }
        refreshUsageCounters()
    }

    private fun refreshCreatorPreferences() {
        _creatorPreferences.value = readCreatorPreferences()
    }

    private fun refreshUsageCounters() {
        _usageCounters.value = readUsageCounters()
    }

    private fun readCreatorPreferences(): CreatorPreferences =
        CreatorPreferences(
            defaultScriptTone = prefs.getString(KEY_DEFAULT_SCRIPT_TONE, DEFAULT_SCRIPT_TONE)
                ?: DEFAULT_SCRIPT_TONE,
            defaultContentStyle = prefs.getString(KEY_DEFAULT_CONTENT_STYLE, DEFAULT_CONTENT_STYLE)
                ?: DEFAULT_CONTENT_STYLE,
            autoSaveProjects =
                prefs.getBoolean(
                    KEY_AUTO_SAVE_PROJECTS,
                    prefs.getBoolean(KEY_LEGACY_AUTO_SAVE_GENERATIONS, false),
                ),
            themeMode = normalizeThemeMode(prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE),
            pushNotifications = prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, false),
            weeklyCreatorTips = prefs.getBoolean(KEY_WEEKLY_CREATOR_TIPS, true),
        )

    private fun readUsageCounters(): UsageCounters =
        UsageCounters(
            scriptsCreated = prefs.getInt(KEY_SCRIPTS_CREATED, 0),
            shortsGenerated = prefs.getInt(KEY_SHORTS_GENERATED, 0),
            seoGenerations = prefs.getInt(KEY_SEO_GENERATIONS, 0),
            ideasGenerated = prefs.getInt(KEY_IDEAS_GENERATED, 0),
            trendsExplored = prefs.getInt(KEY_TRENDS_EXPLORED, 0),
        )

    companion object {
        const val DEFAULT_SCRIPT_TONE = "Friendly"
        const val DEFAULT_CONTENT_STYLE = "Energetic host"
        const val DEFAULT_THEME_MODE = "DARK"

        private const val PREFS_NAME = "scriptpilot_profile"
        private const val KEY_DEFAULT_SCRIPT_TONE = "default_script_tone"
        private const val KEY_DEFAULT_CONTENT_STYLE = "default_content_style"
        private const val KEY_AUTO_SAVE_PROJECTS = "auto_save_projects"
        private const val KEY_LEGACY_DEFAULT_SCRIPT_DURATION = "default_script_duration"
        private const val KEY_LEGACY_AUTO_SAVE_GENERATIONS = "auto_save_generations"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PUSH_NOTIFICATIONS = "push_notifications"
        private const val KEY_WEEKLY_CREATOR_TIPS = "weekly_creator_tips"
        private const val KEY_SCRIPTS_CREATED = "scripts_created"
        private const val KEY_SHORTS_GENERATED = "shorts_generated"
        private const val KEY_SEO_GENERATIONS = "seo_generations"
        private const val KEY_IDEAS_GENERATED = "ideas_generated"
        private const val KEY_TRENDS_EXPLORED = "trends_explored"
    }
}

private fun normalizeThemeMode(value: String): String =
    AppTheme.fromStoredValue(value).name
