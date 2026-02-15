package com.ai.assistant.data.repository

import com.ai.assistant.data.local.datastore.SettingsDataStore
import com.ai.assistant.domain.model.Settings
import com.ai.assistant.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {

    override val settings: Flow<Settings> = combine(
        dataStore.apiKey,
        dataStore.aiModel,
        dataStore.voiceLanguage,
        dataStore.autoExecute,
        dataStore.maxSteps
    ) { apiKey, model, lang, autoExec, maxSteps ->
        Settings(
            groqApiKey = apiKey,
            aiModel = model,
            voiceLanguage = lang,
            autoExecute = autoExec,
            maxStepsPerCommand = maxSteps
        )
    }.combine(
        combine(
            dataStore.actionDelay,
            dataStore.showDebug,
            dataStore.hapticFeedback
        ) { delay, debug, haptic ->
            Triple(delay, debug, haptic)
        }
    ) { settings, (delay, debug, haptic) ->
        settings.copy(
            actionDelayMs = delay,
            showDebugInfo = debug,
            hapticFeedback = haptic
        )
    }

    override suspend fun getSettings(): Settings {
        return settings.first()
    }

    override suspend fun updateApiKey(key: String) {
        dataStore.setApiKey(key)
    }

    override suspend fun updateModel(model: String) {
        dataStore.setAiModel(model)
    }

    override suspend fun updateVoiceLanguage(language: String) {
        dataStore.setVoiceLanguage(language)
    }

    override suspend fun updateAutoExecute(enabled: Boolean) {
        dataStore.setAutoExecute(enabled)
    }

    override suspend fun updateMaxSteps(steps: Int) {
        dataStore.setMaxSteps(steps)
    }

    override suspend fun updateActionDelay(delayMs: Long) {
        dataStore.setActionDelay(delayMs)
    }

    override suspend fun updateShowDebugInfo(show: Boolean) {
        dataStore.setShowDebug(show)
    }

    override suspend fun updateHapticFeedback(enabled: Boolean) {
        dataStore.setHapticFeedback(enabled)
    }
}
