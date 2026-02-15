package com.ai.assistant.domain.model

data class Settings(
    val groqApiKey: String = "",
    val aiModel: String = "llama-3.3-70b-versatile",
    val voiceLanguage: String = "ru-RU",
    val autoExecute: Boolean = true,
    val maxStepsPerCommand: Int = 20,
    val actionDelayMs: Long = 500,
    val showDebugInfo: Boolean = false,
    val hapticFeedback: Boolean = true
)
