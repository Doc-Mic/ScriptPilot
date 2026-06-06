package com.mic.scriptpilot.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.AuthRepository
import com.mic.scriptpilot.BuildConfig
import com.mic.scriptpilot.R
import com.mic.scriptpilot.data.repository.CreatorPreferences
import com.mic.scriptpilot.data.repository.ProfilePreferencesRepository
import com.mic.scriptpilot.data.repository.ProjectRepository
import com.mic.scriptpilot.data.repository.UsageCounters
import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.domain.model.ProjectType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUsageStats(
    val scriptsCreated: Int = 0,
    val shortsGenerated: Int = 0,
    val seoGenerations: Int = 0,
    val ideasGenerated: Int = 0,
    val trendsExplored: Int = 0,
    val projectsSaved: Int = 0,
)

data class ProfileUiState(
    val usageStats: ProfileUsageStats = ProfileUsageStats(),
    val preferences: CreatorPreferences = CreatorPreferences(),
    val displayName: String = "",
    val email: String = "",
    val versionName: String = BuildConfig.VERSION_NAME,
)

sealed interface ProfileEvent {
    data class Message(val messageRes: Int) : ProfileEvent
    data object SignedOut : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository,
    private val profilePreferencesRepository: ProfilePreferencesRepository,
) : ViewModel() {
    val uiState =
        combine(
            projectRepository.observeAll(),
            profilePreferencesRepository.creatorPreferences,
            profilePreferencesRepository.usageCounters,
            authRepository.authState,
        ) { projects, preferences, counters, authUser ->
            ProfileUiState(
                usageStats = projects.toUsageStats(counters),
                preferences = preferences,
                displayName = authUser?.displayName.orEmpty(),
                email = authUser?.email.orEmpty(),
                versionName = BuildConfig.VERSION_NAME,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(),
        )

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events

    val premiumPlans: List<PremiumPlan> = PremiumPlansCatalog.plans(currentPlanId = PremiumPlansCatalog.FREE_PLAN_ID)

    fun setDefaultTone(value: String) {
        profilePreferencesRepository.setDefaultScriptTone(value)
    }

    fun setDefaultStyle(value: String) {
        profilePreferencesRepository.setDefaultContentStyle(value)
    }

    fun setAutoSaveProjects(enabled: Boolean) {
        profilePreferencesRepository.setAutoSaveProjects(enabled)
    }

    fun setThemeMode(value: String) {
        profilePreferencesRepository.setThemeMode(value)
    }

    fun setPushNotifications(enabled: Boolean) {
        profilePreferencesRepository.setPushNotifications(enabled)
    }

    fun setWeeklyCreatorTips(enabled: Boolean) {
        profilePreferencesRepository.setWeeklyCreatorTips(enabled)
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            val cleanName = name.trim()
            if (cleanName.isBlank()) {
                _events.emit(ProfileEvent.Message(R.string.profile_message_name_required))
                return@launch
            }
            runCatching {
                authRepository.updateDisplayName(cleanName)
            }.onSuccess {
                _events.emit(ProfileEvent.Message(R.string.profile_message_profile_updated))
            }.onFailure {
                _events.emit(ProfileEvent.Message(R.string.profile_message_profile_update_failed))
            }
        }
    }

    fun clearLocalHistory() {
        viewModelScope.launch {
            projectRepository.clearAll()
            profilePreferencesRepository.resetUsageCounters()
            _events.emit(ProfileEvent.Message(R.string.profile_message_history_cleared))
        }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            profilePreferencesRepository.resetCreatorPreferences()
            _events.emit(ProfileEvent.Message(R.string.profile_message_preferences_reset))
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _events.emit(ProfileEvent.SignedOut)
        }
    }
}

private fun List<Project>.toUsageStats(counters: UsageCounters): ProfileUsageStats {
    val savedScripts = count { it.type == ProjectType.SCRIPT }
    val savedShorts = count { it.type == ProjectType.SHORT }
    return ProfileUsageStats(
        scriptsCreated = maxOf(counters.scriptsCreated, savedScripts),
        shortsGenerated = maxOf(counters.shortsGenerated, savedShorts),
        seoGenerations = counters.seoGenerations,
        ideasGenerated = counters.ideasGenerated,
        trendsExplored = counters.trendsExplored,
        projectsSaved = size,
    )
}
