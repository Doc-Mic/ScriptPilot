package com.mic.scriptpilot.ui.script

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.ProjectRepository
import com.mic.scriptpilot.data.repository.ScriptRepository
import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.domain.model.ProjectType
import com.mic.scriptpilot.domain.model.ScriptOutline
import com.mic.scriptpilot.ui.util.toUserFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScriptUiState(
    val loading: Boolean = false,
    val outline: ScriptOutline? = null,
    val errorMessage: String? = null,
    val saveComplete: Boolean = false,
)

@HiltViewModel
class ScriptViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository,
    private val projectRepository: ProjectRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScriptUiState())
    val uiState: StateFlow<ScriptUiState> = _uiState.asStateFlow()

    fun generateScript(idea: String, durationLabel: String, tone: String) {
        viewModelScope.launch {
            _uiState.update { ScriptUiState(loading = true, outline = null, errorMessage = null, saveComplete = false) }
            runCatching {
                scriptRepository.generateLongFormScript(idea.trim(), durationLabel, tone)
            }.onSuccess { outline ->
                _uiState.update {
                    ScriptUiState(loading = false, outline = outline, errorMessage = null, saveComplete = false)
                }
            }.onFailure { e ->
                Log.e(TAG, "generateScript failed", e)
                _uiState.update {
                    ScriptUiState(loading = false, outline = null, errorMessage = e.toUserFacingMessage(appContext), saveComplete = false)
                }
            }
        }
    }

    fun saveCurrent(ideaLine: String, outline: ScriptOutline) {
        viewModelScope.launch {
            val titleCandidate = ideaLine.lines().firstOrNull { it.isNotBlank() }.orEmpty()
            val title = titleCandidate.take(80).ifBlank { "YouTube script" }
            val body = outline.asFullScript()
            runCatching {
                projectRepository.save(
                    Project(
                        id = 0,
                        title = title,
                        script = body,
                        type = ProjectType.SCRIPT,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                _uiState.update { it.copy(saveComplete = true) }
            }.onFailure { e ->
                Log.e(TAG, "saveCurrent failed", e)
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
        const val TAG = "ScriptViewModel"
    }
}
