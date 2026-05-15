package com.mic.scriptpilot.ui.trends

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrendFilterUiState(
    val selectedCategoryId: Int = 0,
    val region: String = "Global",
    val timeRange: String = "Last 24 hours",
) {
    val selectedCategoryLabel: String
        get() = TrendCategoryCatalog.labelFor(selectedCategoryId)
}

@HiltViewModel
class TrendInputViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(TrendFilterUiState())
    val uiState: StateFlow<TrendFilterUiState> = _uiState.asStateFlow()

    fun setCategory(categoryId: Int) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun setRegion(region: String) {
        if (region.isBlank()) return
        _uiState.update { it.copy(region = region) }
    }

    fun setTimeRange(timeRange: String) {
        if (timeRange.isBlank()) return
        _uiState.update { it.copy(timeRange = timeRange) }
    }
}
