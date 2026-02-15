package com.ai.assistant.domain.model

/**
 * Represents a single action the AI wants to perform on the UI.
 * The ActionExecutor will translate these into actual AccessibilityService calls.
 */
sealed class UiAction {
    /** Click on a node identified by index or description */
    data class Click(
        val nodeIndex: Int? = null,
        val nodeText: String? = null,
        val nodeId: String? = null,
        val nodeDescription: String? = null
    ) : UiAction()

    /** Long click on a node */
    data class LongClick(
        val nodeIndex: Int? = null,
        val nodeText: String? = null,
        val nodeId: String? = null
    ) : UiAction()

    /** Type text into the currently focused or specified editable field */
    data class TypeText(
        val text: String,
        val nodeIndex: Int? = null,
        val nodeId: String? = null
    ) : UiAction()

    /** Clear text in an editable field */
    data class ClearText(
        val nodeIndex: Int? = null,
        val nodeId: String? = null
    ) : UiAction()

    /** Scroll in a direction */
    data class Scroll(
        val direction: ScrollDirection,
        val nodeIndex: Int? = null
    ) : UiAction()

    /** Press a system button */
    data class PressButton(val button: SystemButton) : UiAction()

    /** Open an app by package name */
    data class OpenApp(
        val packageName: String? = null,
        val appName: String? = null
    ) : UiAction()

    /** Wait for the screen to update */
    data class Wait(val milliseconds: Long = 1000) : UiAction()

    /** Swipe gesture */
    data class Swipe(
        val startX: Int, val startY: Int,
        val endX: Int, val endY: Int,
        val duration: Long = 300
    ) : UiAction()

    /** Set text directly via paste (for faster input) */
    data class SetText(
        val text: String,
        val nodeIndex: Int? = null,
        val nodeId: String? = null
    ) : UiAction()

    /** The task is complete */
    data class Done(val message: String) : UiAction()

    /** Report an error — cannot complete the task */
    data class Error(val reason: String) : UiAction()

    enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
    enum class SystemButton { BACK, HOME, RECENTS, NOTIFICATIONS }
}
