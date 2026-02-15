package com.ai.assistant.domain.repository

import com.ai.assistant.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun getSettings(): Settings
    suspend fun updateApiKey(key: String)
    suspend fun updateModel(model: String)
    suspend fun updateVoiceLanguage(language: String)
    suspend fun updateAutoExecute(enabled: Boolean)
    suspend fun updateMaxSteps(steps: Int)
    suspend fun updateActionDelay(delayMs: Long)
    suspend fun updateShowDebugInfo(show: Boolean)
    suspend fun updateHapticFeedback(enabled: Boolean)
}
