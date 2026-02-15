package com.ai.assistant.data.repository

import com.ai.assistant.data.local.dao.CommandHistoryDao
import com.ai.assistant.data.local.entity.CommandHistoryEntity
import com.ai.assistant.domain.model.CommandHistory
import com.ai.assistant.domain.repository.CommandHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandHistoryRepositoryImpl @Inject constructor(
    private val dao: CommandHistoryDao
) : CommandHistoryRepository {

    override fun getAllHistory(): Flow<List<CommandHistory>> {
        return dao.getAll().map { list -> list.map { it.toDomain() } }
    }

    override fun getRecentHistory(limit: Int): Flow<List<CommandHistory>> {
        return dao.getRecent(limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun addEntry(entry: CommandHistory): Long {
        return dao.insert(entry.toEntity())
    }

    override suspend fun updateEntry(entry: CommandHistory) {
        dao.update(entry.toEntity())
    }

    override suspend fun deleteEntry(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }

    private fun CommandHistoryEntity.toDomain() = CommandHistory(
        id = id,
        command = command,
        status = CommandHistory.Status.valueOf(status),
        stepsCompleted = stepsCompleted,
        totalSteps = totalSteps,
        resultMessage = resultMessage,
        timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()),
        durationMs = durationMs
    )

    private fun CommandHistory.toEntity() = CommandHistoryEntity(
        id = id,
        command = command,
        status = status.name,
        stepsCompleted = stepsCompleted,
        totalSteps = totalSteps,
        resultMessage = resultMessage,
        timestamp = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        durationMs = durationMs
    )
}
