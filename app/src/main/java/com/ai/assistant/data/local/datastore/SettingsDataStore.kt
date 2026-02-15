package com.ai.assistant.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_assistant_settings"
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val API_KEY = stringPreferencesKey("groq_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val AUTO_EXECUTE = booleanPreferencesKey("auto_execute")
        val MAX_STEPS = intPreferencesKey("max_steps")
        val ACTION_DELAY = longPreferencesKey("action_delay")
        val SHOW_DEBUG = booleanPreferencesKey("show_debug")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY] ?: "" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[Keys.AI_MODEL] ?: "llama-3.3-70b-versatile" }
    val voiceLanguage: Flow<String> = context.dataStore.data.map { it[Keys.VOICE_LANGUAGE] ?: "ru-RU" }
    val autoExecute: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_EXECUTE] ?: true }
    val maxSteps: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_STEPS] ?: 20 }
    val actionDelay: Flow<Long> = context.dataStore.data.map { it[Keys.ACTION_DELAY] ?: 500L }
    val showDebug: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_DEBUG] ?: false }
    val hapticFeedback: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTIC_FEEDBACK] ?: true }

    suspend fun setApiKey(value: String) = context.dataStore.edit { it[Keys.API_KEY] = value }
    suspend fun setAiModel(value: String) = context.dataStore.edit { it[Keys.AI_MODEL] = value }
    suspend fun setVoiceLanguage(value: String) = context.dataStore.edit { it[Keys.VOICE_LANGUAGE] = value }
    suspend fun setAutoExecute(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_EXECUTE] = value }
    suspend fun setMaxSteps(value: Int) = context.dataStore.edit { it[Keys.MAX_STEPS] = value }
    suspend fun setActionDelay(value: Long) = context.dataStore.edit { it[Keys.ACTION_DELAY] = value }
    suspend fun setShowDebug(value: Boolean) = context.dataStore.edit { it[Keys.SHOW_DEBUG] = value }
    suspend fun setHapticFeedback(value: Boolean) = context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = value }
}
