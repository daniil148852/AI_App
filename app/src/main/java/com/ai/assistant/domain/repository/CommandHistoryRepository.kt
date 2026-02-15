package com.ai.assistant.domain.repository

import com.ai.assistant.domain.model.CommandHistory
import kotlinx.coroutines.flow.Flow

interface CommandHistoryRepository {
    fun getAllHistory(): Flow<List<CommandHistory>>
    fun getRecentHistory(limit: Int): Flow<List<CommandHistory>>
    suspend fun addEntry(entry: CommandHistory): Long
    suspend fun updateEntry(entry: CommandHistory)
    suspend fun deleteEntry(id: Long)
    suspend fun clearAll()
}
