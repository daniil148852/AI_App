package com.ai.assistant.domain.usecase

import com.ai.assistant.domain.repository.AiRepository
import javax.inject.Inject

class ParseUserCommandUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(rawCommand: String): Result<AiRepository.ParsedCommand> {
        if (rawCommand.isBlank()) {
            return Result.failure(IllegalArgumentException("Command cannot be empty"))
        }
        return aiRepository.parseCommand(rawCommand)
    }
}
