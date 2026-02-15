package com.ai.assistant.ui.screens.home

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
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
        private val idGenerator = AtomicLong(System.currentTimeMillis())

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
                    if (componentName.equals(expectedComponentName, ignoreCase = true)) {
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

    init {
        viewModelScope.launch {
            try {
                AssistantAccessibilityService.isRunning.collect { running ->
                    if (!_isProcessing.value) {
                        _serviceStatus.value = if (running) {
                            ServiceStatus.CONNECTED
                        } else {
                            ServiceStatus.DISCONNECTED
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring service", e)
            }
        }

        viewModelScope.launch {
            try {
                settingsRepository.settings.collect { settings ->
                    _hasApiKey.value = settings.groqApiKey.isNotBlank()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring settings", e)
            }
        }

        viewModelScope.launch {
            try {
                voiceManager.recognizedText
                    .filterNotNull()
                    .collect { text ->
                        _textInput.value = text
                        voiceManager.clearResults()
                        processCommand(text)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring voice", e)
            }
        }

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

    fun refreshServiceStatus() {
        val enabled = isAccessibilityEnabled()
        if (!_isProcessing.value) {
            _serviceStatus.value = if (enabled) {
                ServiceStatus.CONNECTED
            } else {
                ServiceStatus.DISCONNECTED
            }
        }
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
                            text = "❌ Не удалось запустить микрофон: ${e.message}",
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
                Log.d(TAG, "Processing command: $command")

                addMessage(
                    ChatMessage(
                        text = command,
                        isUser = true,
                        timestamp = getCurrentTime()
                    )
                )

                // Check API key
                val settings = try {
                    settingsRepository.getSettings()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get settings", e)
                    addMessage(
                        ChatMessage(
                            text = "❌ Ошибка чтения настроек: ${e.message}",
                            isUser = false,
                            isAction = true,
                            actionStatus = ActionStatus.FAILED,
                            timestamp = getCurrentTime()
                        )
                    )
                    return@launch
                }

                if (settings.groqApiKey.isBlank()) {
                    addMessage(
                        ChatMessage(
                            text = "❌ API ключ Groq не настроен. Перейдите в Настройки.",
                            isUser = false,
                            isAction = true,
                            actionStatus = ActionStatus.FAILED,
                            timestamp = getCurrentTime()
                        )
                    )
                    return@launch
                }

                if (!isAccessibilityEnabled()) {
                    addMessage(
                        ChatMessage(
                            text = "❌ Служба специальных возможностей не включена.",
                            isUser = false,
                            isAction = true,
                            actionStatus = ActionStatus.FAILED,
                            timestamp = getCurrentTime()
                        )
                    )
                    return@launch
                }

                addMessage(
                    ChatMessage(
                        text = "🤔 Анализирую...",
                        isUser = false,
                        isAction = true,
                        actionStatus = ActionStatus.IN_PROGRESS,
                        timestamp = getCurrentTime()
                    )
                )

                while (!isComplete && stepCount < settings.maxStepsPerCommand) {
                    stepCount++
                    Log.d(TAG, "Step $stepCount")

                    if (stepCount > 1) {
                        delay(settings.actionDelayMs)
                    }

                    val currentScreen = try {
                        analyzeScreenUseCase()
                    } catch (e: Exception) {
                        Log.e(TAG, "Screen analysis failed", e)
                        null
                    }

                    val currentPackage = try {
                        analyzeScreenUseCase.getCurrentPackage()
                    } catch (e: Exception) {
                        Log.e(TAG, "Get package failed", e)
                        null
                    }

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
                        Log.e(TAG, "Plan failed", e)
                        Result.failure(e)
                    }

                    planResult.fold(
                        onSuccess = { plan ->
                            if (plan.reasoning.isNotBlank() && settings.showDebugInfo) {
                                addMessage(
                                    ChatMessage(
                                        text = "💭 ${plan.reasoning}",
                                        isUser = false,
                                        timestamp = getCurrentTime()
                                    )
                                )
                            }

                            if (plan.actions.isEmpty()) {
                                Log.w(TAG, "Empty actions from AI")
                                isComplete = true
                                removeLastActionMessage()
                                addMessage(
                                    ChatMessage(
                                        text = "⚠️ AI не вернул действий. Попробуйте переформулировать.",
                                        isUser = false,
                                        isAction = true,
                                        actionStatus = ActionStatus.FAILED,
                                        timestamp = getCurrentTime()
                                    )
                                )
                                return@fold
                            }

                            val results = try {
                                executeActionPlanUseCase(plan, settings.actionDelayMs)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Execute failed", e)
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
                                                text = "✅ ${(result.action as UiAction.Done).message}",
                                                isUser = false,
                                                isAction = true,
                                                actionStatus = ActionStatus.SUCCESS,
                                                timestamp = getCurrentTime()
                                            )
                                        )
                                    }

                                    is UiAction.Error -> {
                                        isComplete = true
                                        lastError = (result.action as UiAction.Error).reason
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
                                            if (result.success) ActionStatus.IN_PROGRESS
                                            else ActionStatus.FAILED
                                        )
                                    }
                                }
                            }

                            val screenDesc = try {
                                currentScreen?.toCompactString()?.take(500) ?: "no screen"
                            } catch (e: Exception) {
                                "error reading screen"
                            }
                            conversationHistory.add(screenDesc to plan.reasoning)
                            if (conversationHistory.size > 5) {
                                conversationHistory.removeAt(0)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Plan result failure", error)
                            isComplete = true
                            lastError = error.message ?: "Unknown error"
                            removeLastActionMessage()

                            val errorMsg = when {
                                lastError!!.contains("401") -> "❌ Неверный API ключ."
                                lastError!!.contains("429") -> "❌ Лимит запросов."
                                else -> "❌ Ошибка: $lastError"
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

                if (!isComplete && stepCount >= settings.maxStepsPerCommand) {
                    removeLastActionMessage()
                    addMessage(
                        ChatMessage(
                            text = "⚠️ Лимит шагов ($stepCount). Задача не завершена.",
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
                Log.e(TAG, "FATAL error in processCommand", e)
                lastError = e.message
                addMessage(
                    ChatMessage(
                        text = "❌ Критическая ошибка: ${e.javaClass.simpleName}: ${e.message}",
                        isUser = false,
                        isAction = true,
                        actionStatus = ActionStatus.FAILED,
                        timestamp = getCurrentTime()
                    )
                )
            } finally {
                _isProcessing.value = false
                refreshServiceStatus()
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        // Гарантируем уникальный ID
        val uniqueMsg = message.copy(id = idGenerator.incrementAndGet())
        _messages.value = _messages.value + uniqueMsg
    }

    private fun removeLastActionMessage() {
        val current = _messages.value.toMutableList()
        val lastActionIndex = current.indexOfLast { it.isAction && !it.isUser }
        if (lastActionIndex >= 0) {
            current.removeAt(lastActionIndex)
            _messages.value = current
        }
    }

    private fun updateLastActionMessage(newText: String, status: ActionStatus) {
        val current = _messages.value.toMutableList()
        val lastActionIndex = current.indexOfLast { it.isAction && !it.isUser }
        if (lastActionIndex >= 0) {
            // Сохраняем ID, чтобы LazyColumn не перерисовывал весь список
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
