package com.ai.assistant.domain.usecase

import com.ai.assistant.domain.model.CommandHistory
import com.ai.assistant.domain.repository.CommandHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCommandHistoryUseCase @Inject constructor(
    private val repository: CommandHistoryRepository
) {
    operator fun invoke(limit: Int = 50): Flow<List<CommandHistory>> {
        return repository.getRecentHistory(limit)
    }

    fun all(): Flow<List<CommandHistory>> = repository.getAllHistory()
}
