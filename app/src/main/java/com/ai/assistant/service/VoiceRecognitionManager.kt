package com.ai.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VoiceRecognition"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow<String?>(null)
    val partialText: StateFlow<String?> = _partialText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun startListening(language: String = "ru-RU") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _error.value = "Speech recognition is not available"
            return
        }

        // SpeechRecognizer MUST be created and used on Main thread
        mainHandler.post {
            try {
                stopListeningInternal()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    .apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                _isListening.value = true
                                _error.value = null
                                Log.d(TAG, "Ready for speech")
                            }

                            override fun onBeginningOfSpeech() {
                                Log.d(TAG, "Beginning of speech")
                            }

                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                _isListening.value = false
                                Log.d(TAG, "End of speech")
                            }

                            override fun onError(errorCode: Int) {
                                _isListening.value = false
                                val msg = when (errorCode) {
                                    SpeechRecognizer.ERROR_AUDIO ->
                                        "Ошибка записи аудио"
                                    SpeechRecognizer.ERROR_CLIENT ->
                                        "Ошибка клиента"
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                        "Нет разрешения на микрофон"
                                    SpeechRecognizer.ERROR_NETWORK ->
                                        "Ошибка сети"
                                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                        "Таймаут сети"
                                    SpeechRecognizer.ERROR_NO_MATCH ->
                                        "Речь не распознана"
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                        "Распознаватель занят"
                                    SpeechRecognizer.ERROR_SERVER ->
                                        "Ошибка сервера"
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                        "Нет речи"
                                    else -> "Ошибка ($errorCode)"
                                }
                                _error.value = msg
                                Log.w(TAG, "Recognition error: $msg")
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                val text = matches?.firstOrNull()
                                Log.d(TAG, "Result: $text")
                                _recognizedText.value = text
                                _isListening.value = false
                            }

                            override fun onPartialResults(
                                partialResults: Bundle?
                            ) {
                                val partial = partialResults?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                _partialText.value = partial?.firstOrNull()
                            }

                            override fun onEvent(
                                eventType: Int,
                                params: Bundle?
                            ) {}
                        })
                    }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
                Log.d(TAG, "Started listening in $language")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening", e)
                _error.value = "Не удалось запустить: ${e.message}"
                _isListening.value = false
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.apply {
                stopListening()
                cancel()
                destroy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
        speechRecognizer = null
        _isListening.value = false
    }

    fun clearResults() {
        _recognizedText.value = null
        _partialText.value = null
        _error.value = null
    }

    fun destroy() {
        mainHandler.post {
            stopListeningInternal()
        }
    }
}
