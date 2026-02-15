package com.ai.assistant.ui.screens.home

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistant.domain.model.CommandHistory
import com.ai.assistant.domain.model.UiAction
import com.ai.assistant.domain.repository.CommandHistoryRepository
import com.ai.assistant.domain.repository.SettingsRepository
import com.ai.assistant.domain.usecase.AnalyzeScreenUseCase
import com.ai.assistant.domain.usecase.ExecuteActionPlanUseCase
import com.ai.assistant.domain.usecase.PlanActionsUseCase
import com.ai.assistant.service.AssistantAccessibilityService
import com.ai.assistant.service.VoiceRecognitionManager
import com.ai.assistant.ui.components.ActionStatus
import com.ai.assistant.ui.components.ChatMessage
import com.ai.assistant.ui.components.ServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val planActionsUseCase: PlanActionsUseCase,
    private val analyzeScreenUseCase: AnalyzeScreenUseCase,
    private val executeActionPlanUseCase: ExecuteActionPlanUseCase,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: CommandHistoryRepository,
    val voiceManager: VoiceRecognitionManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HomeViewModel"

        fun isAccessibilityServiceEnabled(
            context: Context,
            serviceClass: Class<*>
        ): Boolean {
            return try {
                val expectedComponentName =
                    "${context.packageName}/${serviceClass.canonicalName}"
                val enabledServicesSetting = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServicesSetting)
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.equals(
                            expectedComponentName,
                            ignoreCase = true
                        )
                    ) {
                        return true
                    }
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking accessibility", e)
                false
            }
        }
    }

    private val _textInput = MutableStateFlow("")
    val textInput: StateFlow<String> = _textInput.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _serviceStatus = MutableStateFlow(ServiceStatus.DISCONNECTED)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val previousActions = mutableListOf<String>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private var messageIdCounter = 0L

    init {
        // Monitor accessibility service status
        viewModelScope.launch {
            AssistantAccessibilityService.isRunning.collect { running ->
                _serviceStatus.value = if (running) {
                    ServiceStatus.CONNECTED
                } else {
                    ServiceStatus.DISCONNECTED
                }
            }
        }

        // Monitor API key
        viewModelScope.launch {
            try {
                settingsRepository.settings.collect { settings ->
                    _hasApiKey.value = settings.groqApiKey.isNotBlank()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring settings", e)
            }
        }

        // Monitor voice recognition results
        viewModelScope.launch {
            voiceManager.recognizedText
                .filterNotNull()
                .collect { text ->
                    _textInput.value = text
                    voiceManager.clearResults()
                    processCommand(text)
                }
        }

        // Welcome message
        addMessage(
            ChatMessage(
                text = "👋 Привет! Я AI-ассистент. Скажи или напиши, " +
                        "что нужно сделать.\n\n" +
                        "Например:\n" +
                        "• \"Открой YouTube\"\n" +
                        "• \"Напиши маме в WhatsApp привет\"\n" +
                        "• \"Открой настройки Wi-Fi\"",
                isUser = false,
                timestamp = getCurrentTime()
            )
        )
    }

    fun updateTextInput(text: String) {
        _textInput.value = text
    }

    fun sendCommand() {
        val command = _textInput.value.trim()
        if (command.isBlank() || _isProcessing.value) return

        _textInput.value = ""
        processCommand(command)
    }

    fun toggleVoice() {
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
        } else {
            viewModelScope.launch {
                try {
                    val settings = settingsRepository.getSettings()
                    voiceManager.startListening(settings.voiceLanguage)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting voice", e)
                    addMessage(
                        ChatMessage(
                            text = "❌ Не удалось запустить распознавание речи: ${e.message}",
                            isUser = false,
                            isAction = true,
                            actionStatus = ActionStatus.FAILED,
                            timestamp = getCurrentTime()
                        )
                    )
                }
            }
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        return isAccessibilityServiceEnabled(
            application,
            AssistantAccessibilityService::class.java
        )
    }

    private fun processCommand(command: String) {
        if (_isProcessing.value) return

        viewModelScope.launch {
            _isProcessing.value = true
            _serviceStatus.value = ServiceStatus.PROCESSING
            previousActions.clear()
            conversationHistory.clear()

            val startTime = System.currentTimeMillis()
            var stepCount = 0
            var isComplete = false
            var lastError: String? = null

            try {
                // Show user message
                addMessage(
                    ChatMessage(
                        text = command,
                        isUser = true,
                        timestamp = getCurrentTime()
                    )
                )

                // Check prerequisites
                val settings = settingsRepository.getSettings()

                if (settings.groqApiKey.isBlank()) {
                    addMessage(
                        ChatMessage(
                            text = "❌ API ключ Groq не настроен. " +
                                    "Перейдите в Настройки и укажите ключ.",
                            isUser = false,
                            isAction = true,
                            actionStatus = ActionStatus.FAILED,
                            timestamp = getCurrentTime()
                        )
                    )
                    lastError = "API key not set"
                    _isProcessing.value = false
                    _serviceStatus.value = ServiceStatus.DISCONNECTED
                    return@launch
                }

                if (!isAccessibilityEnabled()) {
                    addMessage(
                        ChatMessage(
                            text = "❌ Служба специальных возможностей не включена. " +
                                    "Включите её в настройках.",
                            isUser = false,
                            isAction = true,
                            actionStatus = ActionStatus.FAILED,
                            timestamp = getCurrentTime()
                        )
                    )
                    lastError = "Accessibility not enabled"
                    _isProcessing.value = false
                    _serviceStatus.value = ServiceStatus.DISCONNECTED
                    return@launch
                }

                // Show thinking indicator
                addMessage(
                    ChatMessage(
                        text = "🤔 Анализирую команду...",
                        isUser = false,
                        isAction = true,
                        actionStatus = ActionStatus.IN_PROGRESS,
                        timestamp = getCurrentTime()
                    )
                )

                while (!isComplete && stepCount < settings.maxStepsPerCommand) {
                    stepCount++

                    // Delay to let screen update
                    if (stepCount > 1) {
                        delay(settings.actionDelayMs)
                    }

                    // Analyze current screen safely
                    val currentScreen = try {
                        analyzeScreenUseCase()
                    } catch (e: Exception) {
                        Log.e(TAG, "Screen analysis failed", e)
                        null
                    }

                    val currentPackage = try {
                        analyzeScreenUseCase.getCurrentPackage()
                    } catch (e: Exception) {
                        null
                    }

                    Log.d(
                        TAG,
                        "Step $stepCount: package=$currentPackage, " +
                                "hasScreen=${currentScreen != null}"
                    )

                    // Ask AI for next action
                    val planResult = try {
                        planActionsUseCase(
                            userCommand = command,
                            currentScreen = currentScreen,
                            currentPackage = currentPackage,
                            previousActions = previousActions.toList(),
                            conversationHistory = conversationHistory.toList()
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "AI planning failed", e)
                        Result.failure(e)
                    }

                    planResult.fold(
                        onSuccess = { plan ->
                            Log.d(TAG, "Plan: ${plan.reasoning}")

                            // Show reasoning if debug
                            if (plan.reasoning.isNotBlank() &&
                                settings.showDebugInfo
                            ) {
                                addMessage(
                                    ChatMessage(
                                        text = "💭 ${plan.reasoning}",
                                        isUser = false,
                                        timestamp = getCurrentTime()
                                    )
                                )
                            }

                            // Execute actions
                            val results = try {
                                executeActionPlanUseCase(
                                    plan,
                                    settings.actionDelayMs
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Execution failed", e)
                                isComplete = true
                                lastError = "Ошибка выполнения: ${e.message}"
                                removeLastActionMessage()
                                addMessage(
                                    ChatMessage(
                                        text = "❌ $lastError",
                                        isUser = false,
                                        isAction = true,
                                        actionStatus = ActionStatus.FAILED,
                                        timestamp = getCurrentTime()
                                    )
                                )
                                emptyList()
                            }

                            for (result in results) {
                                when (result.action) {
                                    is UiAction.Done -> {
                                        isComplete = true
                                        removeLastActionMessage()
                                        addMessage(
                                            ChatMessage(
                                                text = "✅ ${
                                                    (result.action as UiAction.Done)
                                                        .message
                                                }",
                                                isUser = false,
                                                isAction = true,
                                                actionStatus = ActionStatus.SUCCESS,
                                                timestamp = getCurrentTime()
                                            )
                                        )
                                    }

                                    is UiAction.Error -> {
                                        isComplete = true
                                        lastError =
                                            (result.action as UiAction.Error).reason
                                        removeLastActionMessage()
                                        addMessage(
                                            ChatMessage(
                                                text = "❌ $lastError",
                                                isUser = false,
                                                isAction = true,
                                                actionStatus = ActionStatus.FAILED,
                                                timestamp = getCurrentTime()
                                            )
                                        )
                                    }

                                    else -> {
                                        previousActions.add(result.description)
                                        updateLastActionMessage(
                                            "⚡ Шаг $stepCount: ${result.description}",
                                            if (result.success) {
                                                ActionStatus.IN_PROGRESS
                                            } else {
                                                ActionStatus.FAILED
                                            }
                                        )

                                        if (!result.success) {
                                            Log.w(
                                                TAG,
                                                "Step failed: ${result.description}"
                                            )
                                            // Don't stop on failure,
                                            // let AI decide
                                        }
                                    }
                                }
                            }

                            // Store conversation context
                            val screenDesc = currentScreen
                                ?.toCompactString()
                                ?.take(500) ?: "no screen"
                            conversationHistory.add(
                                screenDesc to (plan.reasoning)
                            )
                            if (conversationHistory.size > 5) {
                                conversationHistory.removeAt(0)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Plan failed", error)
                            isComplete = true
                            lastError = error.message ?: "Unknown error"
                            removeLastActionMessage()

                            val errorMsg = when {
                                lastError!!.contains("401") ||
                                        lastError!!.contains("Unauthorized") ->
                                    "❌ Неверный API ключ Groq. " +
                                            "Проверьте ключ в настройках."

                                lastError!!.contains("429") ||
                                        lastError!!.contains("rate") ->
                                    "❌ Превышен лимит запросов. " +
                                            "Подождите минуту."

                                lastError!!.contains("timeout") ||
                                        lastError!!.contains("Timeout") ->
                                    "❌ Таймаут запроса. " +
                                            "Проверьте интернет."

                                lastError!!.contains("Unable to resolve") ||
                                        lastError!!.contains("network") ->
                                    "❌ Нет подключения к интернету."

                                else ->
                                    "❌ Ошибка AI: $lastError"
                            }

                            addMessage(
                                ChatMessage(
                                    text = errorMsg,
                                    isUser = false,
                                    isAction = true,
                                    actionStatus = ActionStatus.FAILED,
                                    timestamp = getCurrentTime()
                                )
                            )
                        }
                    )
                }

                if (!isComplete) {
                    removeLastActionMessage()
                    addMessage(
                        ChatMessage(
                            text = "⚠️ Достигнут лимит шагов ($stepCount). " +
                                    "Задача может быть не завершена.",
                            isUser = false,
                            isAction = true,
                            actionStatus = ActionStatus.FAILED,
                            timestamp = getCurrentTime()
                        )
                    )
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in processCommand", e)
                lastError = e.message
                addMessage(
                    ChatMessage(
                        text = "❌ Непредвиденная ошибка: ${e.message}",
                        isUser = false,
                        isAction = true,
                        actionStatus = ActionStatus.FAILED,
                        timestamp = getCurrentTime()
                    )
                )
            } finally {
                val duration = System.currentTimeMillis() - startTime

                // Save to history safely
                try {
                    historyRepository.addEntry(
                        CommandHistory(
                            command = command,
                            status = when {
                                isComplete && lastError == null ->
                                    CommandHistory.Status.SUCCESS

                                lastError != null ->
                                    CommandHistory.Status.FAILED

                                else ->
                                    CommandHistory.Status.PARTIAL
                            },
                            stepsCompleted = stepCount,
                            totalSteps = stepCount,
                            resultMessage = lastError ?: "Completed",
                            timestamp = LocalDateTime.now(),
                            durationMs = duration
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save history", e)
                }

                _isProcessing.value = false
                _serviceStatus.value = if (isAccessibilityEnabled()) {
                    ServiceStatus.CONNECTED
                } else {
                    ServiceStatus.DISCONNECTED
                }
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        messageIdCounter++
        _messages.value = _messages.value + message
    }

    private fun removeLastActionMessage() {
        val current = _messages.value.toMutableList()
        val lastActionIndex = current.indexOfLast {
            it.isAction && !it.isUser
        }
        if (lastActionIndex >= 0) {
            current.removeAt(lastActionIndex)
            _messages.value = current
        }
    }

    private fun updateLastActionMessage(newText: String, status: ActionStatus) {
        val current = _messages.value.toMutableList()
        val lastActionIndex = current.indexOfLast {
            it.isAction && !it.isUser
        }
        if (lastActionIndex >= 0) {
            current[lastActionIndex] = current[lastActionIndex].copy(
                text = newText,
                actionStatus = status
            )
            _messages.value = current
        } else {
            addMessage(
                ChatMessage(
                    text = newText,
                    isUser = false,
                    isAction = true,
                    actionStatus = status,
                    timestamp = getCurrentTime()
                )
            )
        }
    }

    private fun getCurrentTime(): String {
        return LocalDateTime.now().format(timeFormatter)
    }
}
