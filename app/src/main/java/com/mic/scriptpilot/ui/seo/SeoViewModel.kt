package com.mic.scriptpilot.ui.seo

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.ProjectRepository
import com.mic.scriptpilot.data.repository.ProfilePreferencesRepository
import com.mic.scriptpilot.data.repository.SeoRepository
import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.domain.model.ProjectType
import com.mic.scriptpilot.domain.model.SeoResultKind
import com.mic.scriptpilot.domain.model.SeoResultLine
import java.util.UUID
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
    val saveComplete: Boolean = false,
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
    private val projectRepository: ProjectRepository,
    private val profilePreferencesRepository: ProfilePreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SeoUiState())
    val uiState: StateFlow<SeoUiState> = _state.asStateFlow()

    fun selectTab(tab: SeoResultTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun updateDescription(text: String) {
        _state.update { state ->
            if (!state.hasGenerated) return@update state
            val cleanText = text.trim()
            val line =
                state.descriptions.firstOrNull()?.copy(text = cleanText)
                    ?: SeoResultLine(
                        id = UUID.randomUUID().toString(),
                        kind = SeoResultKind.DESCRIPTION,
                        text = cleanText,
                    )
            state.copy(descriptions = if (cleanText.isBlank()) emptyList() else listOf(line))
        }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun consumeSaveEvent() {
        _state.update { it.copy(saveComplete = false) }
    }

    fun generate(scriptDraft: String, topicHint: String, contentTypes: List<String>) {
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
                    saveComplete = false,
                )
            }
            runCatching {
                seoRepository.generate(scriptDraft, topicHint, contentTypes)
            }.onSuccess { gen ->
                val hasAny =
                    gen.titles.isNotEmpty() ||
                        gen.descriptions.isNotEmpty() ||
                        gen.tags.isNotEmpty()
                if (hasAny) {
                    profilePreferencesRepository.incrementSeoGenerations()
                }
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

    fun saveCurrentPackage(sourceText: String, contextHint: String) {
        val state = _state.value
        if (!state.hasGenerated) return
        viewModelScope.launch {
            val body = state.asPackageText()
            val title =
                listOf(contextHint, sourceText)
                    .firstOrNull { it.isNotBlank() }
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .take(80)
                    .ifBlank { appContext.getString(com.mic.scriptpilot.R.string.seo_package_title) }
            runCatching {
                projectRepository.save(
                    Project(
                        id = 0,
                        title = title,
                        script = body,
                        type = ProjectType.IDEA,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(saveComplete = true) }
            }.onFailure { e ->
                Log.e(TAG, "saveCurrentPackage failed", e)
                _state.update { it.copy(errorMessage = e.toUserFacingMessage(appContext)) }
            }
        }
    }

    private fun SeoUiState.asPackageText(): String =
        listOf(
            "Titles" to titles,
            "Descriptions" to descriptions,
            "Tags" to tags,
        ).joinToString("\n\n") { (label, lines) ->
            buildString {
                append(label)
                append("\n")
                lines.forEachIndexed { index, line ->
                    append(index + 1)
                    append(". ")
                    append(line.text)
                    append("\n")
                }
            }.trim()
        }

    private companion object {
        const val TAG = "SeoViewModel"
    }
}
