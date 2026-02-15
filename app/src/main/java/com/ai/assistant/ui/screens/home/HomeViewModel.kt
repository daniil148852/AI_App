package com.ai.assistant.ui.screens.home

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistant.domain.model.ActionPlan
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

    init {
        // Monitor accessibility service status
        viewModelScope.launch {
            AssistantAccessibilityService.isRunning.collect { running ->
                _serviceStatus.value = if (running) ServiceStatus.CONNECTED else ServiceStatus.DISCONNECTED
            }
        }

        // Monitor API key
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _hasApiKey.value = settings.groqApiKey.isNotBlank()
            }
        }

        // Monitor voice recognition results
        viewModelScope.launch {
            voiceManager.recognizedText.filterNotNull().collect { text ->
                _textInput.value = text
                voiceManager.clearResults()
                // Auto-send voice commands
                processCommand(text)
            }
        }

        // Add welcome message
        addMessage(ChatMessage(
            text = "👋 Привет! Я AI-ассистент. Скажи или напиши, что нужно сделать, и я выполню это за тебя.\n\nНапример:\n• \"Напиши маме в WhatsApp привет\"\n• \"Открой YouTube\"\n• \"Поставь таймер на 5 минут\"",
            isUser = false,
            timestamp = getCurrentTime()
        ))
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
                val settings = settingsRepository.getSettings()
                voiceManager.startListening(settings.voiceLanguage)
            }
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        return isAccessibilityServiceEnabled(application, AssistantAccessibilityService::class.java)
    }

    private fun processCommand(command: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            _serviceStatus.value = ServiceStatus.PROCESSING
            previousActions.clear()

            val startTime = System.currentTimeMillis()

            // Show user message
            addMessage(ChatMessage(
                text = command,
                isUser = true,
                timestamp = getCurrentTime()
            ))

            // Add "thinking" message
            addMessage(ChatMessage(
                text = "🤔 Анализирую команду...",
                isUser = false,
                isAction = true,
                actionStatus = ActionStatus.IN_PROGRESS,
                timestamp = getCurrentTime()
            ))

            val settings = settingsRepository.getSettings()
            var stepCount = 0
            var isComplete = false
            var lastError: String? = null

            try {
                while (!isComplete && stepCount < settings.maxStepsPerCommand) {
                    stepCount++

                    // Small delay to let screen update
                    delay(settings.actionDelayMs)

                    // Analyze current screen
                    val currentScreen = analyzeScreenUseCase()
                    val currentPackage = analyzeScreenUseCase.getCurrentPackage()

                    // Ask AI for next action
                    val planResult = planActionsUseCase(
                        userCommand = command,
                        currentScreen = currentScreen,
                        currentPackage = currentPackage,
                        previousActions = previousActions,
                        conversationHistory = conversationHistory
                    )

                    planResult.fold(
                        onSuccess = { plan ->
                            // Show reasoning
                            if (plan.reasoning.isNotBlank() && settings.showDebugInfo) {
                                addMessage(ChatMessage(
                                    text = "💭 ${plan.reasoning}",
                                    isUser = false,
                                    timestamp = getCurrentTime()
                                ))
                            }

                            // Execute actions
                            val results = executeActionPlanUseCase(plan, settings.actionDelayMs)

                            for (result in results) {
                                when (result.action) {
                                    is UiAction.Done -> {
                                        isComplete = true
                                        // Remove "thinking" and add success
                                        removeLastActionMessage()
                                        addMessage(ChatMessage(
                                            text = "✅ ${(result.action as UiAction.Done).message}",
                                            isUser = false,
                                            isAction = true,
                                            actionStatus = ActionStatus.SUCCESS,
                                            timestamp = getCurrentTime()
                                        ))
                                    }
                                    is UiAction.Error -> {
                                        isComplete = true
                                        lastError = (result.action as UiAction.Error).reason
                                        removeLastActionMessage()
                                        addMessage(ChatMessage(
                                            text = "❌ ${lastError}",
                                            isUser = false,
                                            isAction = true,
                                            actionStatus = ActionStatus.FAILED,
                                            timestamp = getCurrentTime()
                                        ))
                                    }
                                    else -> {
                                        previousActions.add(result.description)
                                        if (result.success) {
                                            updateLastActionMessage(
                                                "⚡ Шаг $stepCount: ${result.description}",
                                                ActionStatus.IN_PROGRESS
                                            )
                                        } else {
                                            updateLastActionMessage(
                                                "⚠️ Шаг $stepCount не удался: ${result.description}",
                                                ActionStatus.FAILED
                                            )
                                        }
                                    }
                                }
                            }

                            // Store conversation context
                            val screenDesc = currentScreen?.toCompactString()?.take(500) ?: "no screen"
                            conversationHistory.add(
                                screenDesc to (planResult.getOrNull()?.reasoning ?: "")
                            )
                            // Keep only last 5 exchanges
                            if (conversationHistory.size > 5) {
                                conversationHistory.removeAt(0)
                            }
                        },
                        onFailure = { error ->
                            isComplete = true
                            lastError = error.message
                            removeLastActionMessage()
                            addMessage(ChatMessage(
                                text = "❌ Ошибка AI: ${error.message}",
                                isUser = false,
                                isAction = true,
                                actionStatus = ActionStatus.FAILED,
                                timestamp = getCurrentTime()
                            ))
                        }
                    )
                }

                if (!isComplete) {
                    removeLastActionMessage()
                    addMessage(ChatMessage(
                        text = "⚠️ Достигнут лимит шагов ($stepCount). Задача может быть не завершена.",
                        isUser = false,
                        isAction = true,
                        actionStatus = ActionStatus.FAILED,
                        timestamp = getCurrentTime()
                    ))
                }

            } catch (e: Exception) {
                lastError = e.message
                removeLastActionMessage()
                addMessage(ChatMessage(
                    text = "❌ Ошибка: ${e.message}",
                    isUser = false,
                    isAction = true,
                    actionStatus = ActionStatus.FAILED,
                    timestamp = getCurrentTime()
                ))
            }

            val duration = System.currentTimeMillis() - startTime

            // Save to history
            historyRepository.addEntry(
                CommandHistory(
                    command = command,
                    status = when {
                        isComplete && lastError == null -> CommandHistory.Status.SUCCESS
                        lastError != null -> CommandHistory.Status.FAILED
                        else -> CommandHistory.Status.PARTIAL
                    },
                    stepsCompleted = stepCount,
                    totalSteps = stepCount,
                    resultMessage = lastError ?: "Completed",
                    timestamp = LocalDateTime.now(),
                    durationMs = duration
                )
            )

            _isProcessing.value = false
            _serviceStatus.value = if (isAccessibilityEnabled()) ServiceStatus.CONNECTED else ServiceStatus.DISCONNECTED
        }
    }

    private fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun removeLastActionMessage() {
        _messages.value = _messages.value.toMutableList().apply {
            val lastActionIndex = indexOfLast { it.isAction && !it.isUser }
            if (lastActionIndex >= 0) removeAt(lastActionIndex)
        }
    }

    private fun updateLastActionMessage(newText: String, status: ActionStatus) {
        _messages.value = _messages.value.toMutableList().apply {
            val lastActionIndex = indexOfLast { it.isAction && !it.isUser }
            if (lastActionIndex >= 0) {
                this[lastActionIndex] = this[lastActionIndex].copy(
                    text = newText,
                    actionStatus = status
                )
            } else {
                add(ChatMessage(
                    text = newText,
                    isUser = false,
                    isAction = true,
                    actionStatus = status,
                    timestamp = getCurrentTime()
                ))
            }
        }
    }

    private fun getCurrentTime(): String {
        return LocalDateTime.now().format(timeFormatter)
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
            val expectedComponentName = "${context.packageName}/${serviceClass.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
