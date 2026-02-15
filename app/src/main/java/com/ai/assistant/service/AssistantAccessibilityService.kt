package com.ai.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The core AccessibilityService that allows us to read screen contents
 * and perform actions on the UI.
 */
class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistantAccessibilityService? = null
            private set

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentPackage = MutableStateFlow<String?>(null)
        val currentPackageFlow: StateFlow<String?> = _currentPackage.asStateFlow()

        fun isServiceEnabled(): Boolean = instance != null && _isRunning.value
    }

    var currentPackageName: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isRunning.value = true

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                event.packageName?.toString()?.let { pkg ->
                    if (pkg != "com.ai.assistant") { // Don't track our own app
                        currentPackageName = pkg
                        _currentPackage.value = pkg
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isRunning.value = false
        _currentPackage.value = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isRunning.value = false
        return super.onUnbind(intent)
    }
}
