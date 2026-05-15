package com.mic.scriptpilot.ui.seo

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.SeoRepository
import com.mic.scriptpilot.domain.model.SeoResultLine
import com.mic.scriptpilot.ui.util.toUserFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SeoResultTab {
    TITLES,
    DESCRIPTIONS,
    TAGS,
}

data class SeoUiState(
    val tab: SeoResultTab = SeoResultTab.TITLES,
    val titles: List<SeoResultLine> = emptyList(),
    val descriptions: List<SeoResultLine> = emptyList(),
    val tags: List<SeoResultLine> = emptyList(),
    val isLoading: Boolean = false,
    val hasGenerated: Boolean = false,
    val errorMessage: String? = null,
) {
    fun itemsForTab(): List<SeoResultLine> =
        when (tab) {
            SeoResultTab.TITLES -> titles
            SeoResultTab.DESCRIPTIONS -> descriptions
            SeoResultTab.TAGS -> tags
        }

    fun shouldShowRecycler(): Boolean = !isLoading && hasGenerated && itemsForTab().isNotEmpty()
}

@HiltViewModel
class SeoViewModel @Inject constructor(
    private val seoRepository: SeoRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SeoUiState())
    val uiState: StateFlow<SeoUiState> = _state.asStateFlow()

    fun selectTab(tab: SeoResultTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun generate(scriptDraft: String, workingTitle: String, topicHint: String) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    hasGenerated = false,
                    titles = emptyList(),
                    descriptions = emptyList(),
                    tags = emptyList(),
                    errorMessage = null,
                )
            }
            runCatching {
                seoRepository.generate(scriptDraft, workingTitle, topicHint)
            }.onSuccess { gen ->
                val hasAny = gen.titles.isNotEmpty() || gen.descriptions.isNotEmpty() || gen.tags.isNotEmpty()
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasGenerated = hasAny,
                        titles = gen.titles,
                        descriptions = gen.descriptions,
                        tags = gen.tags,
                        errorMessage = null,
                    )
                }
                if (!hasAny) {
                    Log.w(TAG, "generate: API returned success but no SEO lines after mapping.")
                }
            }.onFailure { e ->
                Log.e(TAG, "generate failed", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasGenerated = false,
                        titles = emptyList(),
                        descriptions = emptyList(),
                        tags = emptyList(),
                        errorMessage = e.toUserFacingMessage(appContext),
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "SeoViewModel"
    }
}
