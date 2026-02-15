package com.ai.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val status: String,
    val stepsCompleted: Int,
    val totalSteps: Int,
    val resultMessage: String?,
    val timestamp: Long,      // epoch millis
    val durationMs: Long
)
