package com.mic.scriptpilot.ui.trends

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.TrendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendResultUiState(
    val loading: Boolean = true,
    val trends: List<TrendUiModel> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val trendRepository: TrendRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrendResultUiState())
    val uiState: StateFlow<TrendResultUiState> = _uiState.asStateFlow()

    private var lastCategoryId: Int? = null
    private var lastLocation: String = ""
    private var lastTimeRange: String = ""

    fun load(categoryId: Int, location: String, timeRange: String) {
        lastCategoryId = categoryId
        lastLocation = location
        lastTimeRange = timeRange
        viewModelScope.launch {
            _uiState.update {
                TrendResultUiState(
                    loading = true,
                    trends = emptyList(),
                    errorMessage = null,
                )
            }
            runCatching {
                trendRepository.findTrends(categoryId, location, timeRange)
            }.onSuccess { list ->
                val models = list.map { it.toUiModel() }
                _uiState.update {
                    TrendResultUiState(
                        loading = false,
                        trends = models,
                        errorMessage = null,
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "load failed", e)
                _uiState.update {
                    TrendResultUiState(
                        loading = false,
                        trends = emptyList(),
                        errorMessage = e.message ?: "Unable to load trends.",
                    )
                }
            }
        }
    }

    fun retry() {
        val categoryId = lastCategoryId ?: return
        load(categoryId, lastLocation, lastTimeRange)
    }

    private companion object {
        const val TAG = "TrendsViewModel"
    }
}
