package com.ai.assistant.domain.usecase

import com.ai.assistant.domain.model.ActionPlan
import com.ai.assistant.domain.model.ScreenNode
import com.ai.assistant.domain.repository.AiRepository
import javax.inject.Inject

class PlanActionsUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(
        userCommand: String,
        currentScreen: ScreenNode?,
        currentPackage: String?,
        previousActions: List<String> = emptyList(),
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<ActionPlan> {
        return aiRepository.planNextAction(
            userCommand = userCommand,
            currentScreen = currentScreen,
            currentPackage = currentPackage,
            previousActions = previousActions,
            conversationHistory = conversationHistory
        )
    }
}
