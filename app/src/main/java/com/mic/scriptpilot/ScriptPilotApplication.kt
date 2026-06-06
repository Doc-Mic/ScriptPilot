package com.mic.scriptpilot

import android.app.Application
import com.mic.scriptpilot.data.repository.ProfilePreferencesRepository
import com.mic.scriptpilot.ui.common.ThemeController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ScriptPilotApplication : Application() {
    @Inject lateinit var profilePreferencesRepository: ProfilePreferencesRepository

    override fun onCreate() {
        super.onCreate()
        ThemeController.apply(profilePreferencesRepository.creatorPreferences.value.themeMode)
    }
}
