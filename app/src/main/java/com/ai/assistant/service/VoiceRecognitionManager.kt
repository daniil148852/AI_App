package com.ai.assistant.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    private var speechRecognizer: SpeechRecognizer? = null

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

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _error.value = null
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(errorCode: Int) {
                    _isListening.value = false
                    _error.value = when (errorCode) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error ($errorCode)"
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    _recognizedText.value = matches?.firstOrNull()
                    _isListening.value = false
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    _partialText.value = partial?.firstOrNull()
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
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
        stopListening()
    }
}
