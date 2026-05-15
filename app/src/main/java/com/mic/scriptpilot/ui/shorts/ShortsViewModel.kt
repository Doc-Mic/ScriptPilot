package com.mic.scriptpilot.ui.shorts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.ProjectRepository
import com.mic.scriptpilot.data.repository.ScriptRepository
import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.domain.model.ProjectType
import com.mic.scriptpilot.ui.util.toUserFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShortsUiState(
    val loading: Boolean = false,
    val scriptText: String? = null,
    val errorMessage: String? = null,
    val saveComplete: Boolean = false,
)

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository,
    private val projectRepository: ProjectRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShortsUiState())
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

    fun generate(topic: String) {
        viewModelScope.launch {
            _uiState.update { ShortsUiState(loading = true, scriptText = null, errorMessage = null, saveComplete = false) }
            runCatching {
                scriptRepository.generateShortScript(topic.trim())
            }.onSuccess { text ->
                _uiState.update {
                    ShortsUiState(loading = false, scriptText = text, errorMessage = null, saveComplete = false)
                }
            }.onFailure { e ->
                Log.e(TAG, "generate failed", e)
                _uiState.update {
                    ShortsUiState(loading = false, scriptText = null, errorMessage = e.toUserFacingMessage(appContext), saveComplete = false)
                }
            }
        }
    }

    fun save(topicLine: String, script: String) {
        viewModelScope.launch {
            val title = topicLine.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(80)
                .ifBlank { "Shorts script" }
            runCatching {
                projectRepository.save(
                    Project(
                        id = 0,
                        title = title,
                        script = script,
                        type = ProjectType.SHORT,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                _uiState.update { it.copy(saveComplete = true) }
            }.onFailure { e ->
                Log.e(TAG, "save failed", e)
                _uiState.update { it.copy(errorMessage = e.toUserFacingMessage(appContext)) }
            }
        }
    }

    fun consumeSaveEvent() {
        _uiState.update { it.copy(saveComplete = false) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        const val TAG = "ShortsViewModel"
    }
}
