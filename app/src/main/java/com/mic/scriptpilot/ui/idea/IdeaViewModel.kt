package com.mic.scriptpilot.ui.idea

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.IdeaRepository
import com.mic.scriptpilot.domain.model.IdeaItem
import com.mic.scriptpilot.ui.util.toUserFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeaUiState(
    val loading: Boolean = false,
    val ideas: List<IdeaItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class IdeaViewModel @Inject constructor(
    private val ideaRepository: IdeaRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IdeaUiState())
    val uiState: StateFlow<IdeaUiState> = _uiState.asStateFlow()

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun generateIdeas(topic: String, style: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, ideas = emptyList(), errorMessage = null)
            }
            runCatching {
                ideaRepository.generateIdeas(topic.trim(), style)
            }.onSuccess { list ->
                Log.d(TAG, "generateIdeas UI update: count=${list.size}")
                _uiState.update {
                    it.copy(loading = false, ideas = list, errorMessage = null)
                }
            }.onFailure { e ->
                Log.e(TAG, "generateIdeas failed", e)
                _uiState.update {
                    it.copy(
                        loading = false,
                        ideas = emptyList(),
                        errorMessage = e.toUserFacingMessage(appContext),
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "IdeaViewModel"
    }
}
