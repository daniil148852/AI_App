package com.ai.assistant.domain.model

import java.time.LocalDateTime

data class CommandHistory(
    val id: Long = 0,
    val command: String,
    val status: Status,
    val stepsCompleted: Int,
    val totalSteps: Int,
    val resultMessage: String?,
    val timestamp: LocalDateTime,
    val durationMs: Long
) {
    enum class Status {
        SUCCESS, FAILED, PARTIAL, IN_PROGRESS
    }
}
