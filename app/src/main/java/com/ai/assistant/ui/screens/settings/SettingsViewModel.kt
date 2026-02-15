package com.ai.assistant.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistant.domain.model.Settings
import com.ai.assistant.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun updateApiKey(key: String) {
        viewModelScope.launch { repository.updateApiKey(key) }
    }

    fun updateModel(model: String) {
        viewModelScope.launch { repository.updateModel(model) }
    }

    fun updateVoiceLanguage(language: String) {
        viewModelScope.launch { repository.updateVoiceLanguage(language) }
    }

    fun updateAutoExecute(enabled: Boolean) {
        viewModelScope.launch { repository.updateAutoExecute(enabled) }
    }

    fun updateMaxSteps(steps: Int) {
        viewModelScope.launch { repository.updateMaxSteps(steps) }
    }

    fun updateActionDelay(delay: Long) {
        viewModelScope.launch { repository.updateActionDelay(delay) }
    }

    fun updateShowDebugInfo(show: Boolean) {
        viewModelScope.launch { repository.updateShowDebugInfo(show) }
    }

    fun updateHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { repository.updateHapticFeedback(enabled) }
    }
}
