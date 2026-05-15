package com.mic.scriptpilot.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mic.scriptpilot.data.repository.ProjectRepository
import com.mic.scriptpilot.domain.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    projectRepository: ProjectRepository,
) : ViewModel() {
    val recentProjects: StateFlow<List<Project>> = projectRepository
        .observeRecent(12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
