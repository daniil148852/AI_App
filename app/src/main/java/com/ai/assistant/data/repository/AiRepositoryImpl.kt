package com.ai.assistant.data.repository

import com.ai.assistant.data.local.datastore.SettingsDataStore
import com.ai.assistant.data.remote.api.GroqApiService
import com.ai.assistant.data.remote.dto.GroqChatRequest
import com.ai.assistant.data.remote.dto.GroqMessage
import com.ai.assistant.domain.model.ActionPlan
import com.ai.assistant.domain.model.ScreenNode
import com.ai.assistant.domain.model.UiAction
import com.ai.assistant.domain.repository.AiRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val groqApi: GroqApiService,
    private val settingsDataStore: SettingsDataStore,
    private val gson: Gson
) : AiRepository {

    companion object {
        private const val SYSTEM_PROMPT = """You are an AI assistant that controls an Android phone. You analyze the current screen and decide what actions to perform to fulfill the user's request.

IMPORTANT RULES:
1. Respond ONLY in valid JSON format
2. Think step by step — perform one logical action at a time
3. After each action, you'll see the updated screen
4. Use node indexes to identify UI elements
5. If a node has text/description matching what you need, prefer using nodeText/nodeDescription for reliability
6. Always explain your reasoning

AVAILABLE ACTIONS:
- click: {"action":"click", "nodeIndex":5, "nodeText":"Send", "nodeDescription":"Send button"}
- longClick: {"action":"longClick", "nodeIndex":5}
- typeText: {"action":"typeText", "text":"Hello", "nodeIndex":3}
- setText: {"action":"setText", "text":"Hello", "nodeIndex":3}  (replaces all text)
- clearText: {"action":"clearText", "nodeIndex":3}
- scroll: {"action":"scroll", "direction":"down", "nodeIndex":2}
- pressButton: {"action":"pressButton", "button":"back|home|recents|notifications"}
- openApp: {"action":"openApp", "packageName":"com.whatsapp", "appName":"WhatsApp"}
- wait: {"action":"wait", "milliseconds":1500}
- swipe: {"action":"swipe", "startX":540, "startY":1500, "endX":540, "endY":500}
- done: {"action":"done", "message":"Task completed successfully"}
- error: {"action":"error", "reason":"Cannot find the contact"}

RESPONSE FORMAT:
{
  "reasoning": "explanation of what I see and what I plan to do",
  "actions": [{"action":"...", ...}],
  "requiresScreenRefresh": true,
  "isComplete": false
}

COMMON PACKAGE NAMES:
- WhatsApp: com.whatsapp
- Telegram: org.telegram.messenger
- Chrome: com.android.chrome
- YouTube: com.google.android.youtube
- Gmail: com.google.android.gm
- Phone: com.android.dialer / com.google.android.dialer
- Messages: com.google.android.apps.messaging
- Camera: com.android.camera / com.google.android.GoogleCamera
- Settings: com.android.settings
- Maps: com.google.android.apps.maps
- Instagram: com.instagram.android
- VK: com.vkontakte.android
- Calendar: com.google.android.calendar

When user says "мама" or "mom" — search for this contact by name in the app's search/contact list.
When typing in messenger, look for the text input field (usually editable) and the send button.
Prefer using setText over typeText for reliability."""
    }

    override suspend fun planNextAction(
        userCommand: String,
        currentScreen: ScreenNode?,
        currentPackage: String?,
        previousActions: List<String>,
        conversationHistory: List<Pair<String, String>>
    ): Result<ActionPlan> {
        return try {
            val model = settingsDataStore.aiModel.first()

            val messages = mutableListOf<GroqMessage>()
            messages.add(GroqMessage("system", SYSTEM_PROMPT))

            // Add conversation history for context continuity
            for ((userMsg, assistantMsg) in conversationHistory) {
                messages.add(GroqMessage("user", userMsg))
                messages.add(GroqMessage("assistant", assistantMsg))
            }

            // Build current context message
            val contextBuilder = StringBuilder()
            contextBuilder.appendLine("USER COMMAND: $userCommand")
            contextBuilder.appendLine()

            if (previousActions.isNotEmpty()) {
                contextBuilder.appendLine("ACTIONS ALREADY PERFORMED:")
                previousActions.forEachIndexed { i, action ->
                    contextBuilder.appendLine("  ${i + 1}. $action")
                }
                contextBuilder.appendLine()
            }

            contextBuilder.appendLine("CURRENT PACKAGE: ${currentPackage ?: "unknown"}")
            contextBuilder.appendLine()

            if (currentScreen != null) {
                contextBuilder.appendLine("CURRENT SCREEN TREE:")
                contextBuilder.appendLine(currentScreen.toCompactString())
            } else {
                contextBuilder.appendLine("CURRENT SCREEN: Unable to read screen content")
            }

            messages.add(GroqMessage("user", contextBuilder.toString()))

            val request = GroqChatRequest(
                model = model,
                messages = messages,
                temperature = 0.1,
                maxTokens = 2048
            )

            val response = groqApi.createChatCompletion(request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Empty response from AI"))

            val plan = parseActionPlan(content)
            Result.success(plan)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun parseCommand(rawCommand: String): Result<AiRepository.ParsedCommand> {
        return try {
            val model = settingsDataStore.aiModel.first()

            val messages = listOf(
                GroqMessage("system", """You parse user commands for phone automation.
Respond in JSON: {"intent":"what the user wants to do", "targetApp":"app name or null", "parameters":{"key":"value"}}
Examples:
- "Напиши маме в WhatsApp привет" -> {"intent":"send_message","targetApp":"WhatsApp","parameters":{"contact":"мама","message":"привет"}}
- "Открой YouTube" -> {"intent":"open_app","targetApp":"YouTube","parameters":{}}
- "Позвони Саше" -> {"intent":"make_call","targetApp":"Phone","parameters":{"contact":"Саша"}}
- "Сделай фото" -> {"intent":"take_photo","targetApp":"Camera","parameters":{}}"""),
                GroqMessage("user", rawCommand)
            )

            val request = GroqChatRequest(
                model = model,
                messages = messages,
                temperature = 0.0,
                maxTokens = 512
            )

            val response = groqApi.createChatCompletion(request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Empty response"))

            val json = JsonParser.parseString(content).asJsonObject
            val parsed = AiRepository.ParsedCommand(
                intent = json.get("intent")?.asString ?: "unknown",
                targetApp = json.get("targetApp")?.asString,
                parameters = json.getAsJsonObject("parameters")?.entrySet()
                    ?.associate { it.key to it.value.asString } ?: emptyMap()
            )

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseActionPlan(jsonContent: String): ActionPlan {
        return try {
            // Clean the JSON string — sometimes AI adds markdown
            val cleanJson = jsonContent
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JsonParser.parseString(cleanJson).asJsonObject

            val reasoning = json.get("reasoning")?.asString ?: ""
            val isComplete = json.get("isComplete")?.asBoolean ?: false
            val requiresRefresh = json.get("requiresScreenRefresh")?.asBoolean ?: true

            val actionsArray = json.getAsJsonArray("actions") ?: run {
                return ActionPlan(reasoning, emptyList(), true, isComplete)
            }

            val actions = actionsArray.mapNotNull { element ->
                val actionObj = element.asJsonObject
                val actionType = actionObj.get("action")?.asString ?: return@mapNotNull null

                when (actionType) {
                    "click" -> UiAction.Click(
                        nodeIndex = actionObj.get("nodeIndex")?.asInt,
                        nodeText = actionObj.get("nodeText")?.asString,
                        nodeId = actionObj.get("nodeId")?.asString,
                        nodeDescription = actionObj.get("nodeDescription")?.asString
                    )
                    "longClick" -> UiAction.LongClick(
                        nodeIndex = actionObj.get("nodeIndex")?.asInt,
                        nodeText = actionObj.get("nodeText")?.asString,
                        nodeId = actionObj.get("nodeId")?.asString
                    )
                    "typeText" -> UiAction.TypeText(
                        text = actionObj.get("text")?.asString ?: "",
                        nodeIndex = actionObj.get("nodeIndex")?.asInt,
                        nodeId = actionObj.get("nodeId")?.asString
                    )
                    "setText" -> UiAction.SetText(
                        text = actionObj.get("text")?.asString ?: "",
                        nodeIndex = actionObj.get("nodeIndex")?.asInt,
                        nodeId = actionObj.get("nodeId")?.asString
                    )
                    "clearText" -> UiAction.ClearText(
                        nodeIndex = actionObj.get("nodeIndex")?.asInt,
                        nodeId = actionObj.get("nodeId")?.asString
                    )
                    "scroll" -> UiAction.Scroll(
                        direction = when (actionObj.get("direction")?.asString?.lowercase()) {
                            "up" -> UiAction.ScrollDirection.UP
                            "left" -> UiAction.ScrollDirection.LEFT
                            "right" -> UiAction.ScrollDirection.RIGHT
                            else -> UiAction.ScrollDirection.DOWN
                        },
                        nodeIndex = actionObj.get("nodeIndex")?.asInt
                    )
                    "pressButton" -> UiAction.PressButton(
                        button = when (actionObj.get("button")?.asString?.lowercase()) {
                            "home" -> UiAction.SystemButton.HOME
                            "recents" -> UiAction.SystemButton.RECENTS
                            "notifications" -> UiAction.SystemButton.NOTIFICATIONS
                            else -> UiAction.SystemButton.BACK
                        }
                    )
                    "openApp" -> UiAction.OpenApp(
                        packageName = actionObj.get("packageName")?.asString,
                        appName = actionObj.get("appName")?.asString
                    )
                    "wait" -> UiAction.Wait(
                        milliseconds = actionObj.get("milliseconds")?.asLong ?: 1000
                    )
                    "swipe" -> UiAction.Swipe(
                        startX = actionObj.get("startX")?.asInt ?: 0,
                        startY = actionObj.get("startY")?.asInt ?: 0,
                        endX = actionObj.get("endX")?.asInt ?: 0,
                        endY = actionObj.get("endY")?.asInt ?: 0,
                        duration = actionObj.get("duration")?.asLong ?: 300
                    )
                    "done" -> UiAction.Done(
                        message = actionObj.get("message")?.asString ?: "Task completed"
                    )
                    "error" -> UiAction.Error(
                        reason = actionObj.get("reason")?.asString ?: "Unknown error"
                    )
                    else -> null
                }
            }

            ActionPlan(
                reasoning = reasoning,
                actions = actions,
                requiresScreenRefresh = requiresRefresh,
                isComplete = isComplete
            )
        } catch (e: Exception) {
            ActionPlan(
                reasoning = "Failed to parse AI response: ${e.message}",
                actions = listOf(UiAction.Error("Failed to parse AI response: ${e.message}")),
                requiresScreenRefresh = false,
                isComplete = true,
                errorMessage = e.message
            )
        }
    }
}
