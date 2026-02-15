package com.ai.assistant.domain.repository

import com.ai.assistant.domain.model.ActionPlan
import com.ai.assistant.domain.model.ScreenNode

interface AiRepository {
    /**
     * Send the user's command + current screen state to AI,
     * receive back an ActionPlan with next steps.
     */
    suspend fun planNextAction(
        userCommand: String,
        currentScreen: ScreenNode?,
        currentPackage: String?,
        previousActions: List<String>,
        conversationHistory: List<Pair<String, String>>
    ): Result<ActionPlan>

    /**
     * Parse a user's natural language command to understand intent.
     */
    suspend fun parseCommand(rawCommand: String): Result<ParsedCommand>

    data class ParsedCommand(
        val intent: String,
        val targetApp: String?,
        val parameters: Map<String, String>
    )
}
