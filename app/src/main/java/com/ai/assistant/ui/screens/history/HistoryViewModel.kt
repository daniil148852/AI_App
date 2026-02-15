package com.ai.assistant.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistant.domain.model.CommandHistory
import com.ai.assistant.domain.repository.CommandHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: CommandHistoryRepository
) : ViewModel() {

    val history: StateFlow<List<CommandHistory>> = repository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
