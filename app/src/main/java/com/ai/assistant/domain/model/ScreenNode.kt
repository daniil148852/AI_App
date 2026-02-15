package com.ai.assistant.domain.model

/**
 * Represents a single UI element on the screen as parsed from AccessibilityNodeInfo.
 * Contains all the information the AI needs to understand what's on screen.
 */
data class ScreenNode(
    val id: String?,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isFocusable: Boolean,
    val isEnabled: Boolean,
    val boundsInScreen: Bounds,
    val children: List<ScreenNode>,
    val packageName: String?,
    val index: Int
) {
    data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * Convert this node tree to a compact text representation for the AI.
     * This is crucial — the AI reads this to understand the screen.
     */
    fun toCompactString(depth: Int = 0): String {
        val indent = "  ".repeat(depth)
        val parts = mutableListOf<String>()

        // Node type
        val shortClass = className?.substringAfterLast('.') ?: "Unknown"
        parts.add("[$index]$shortClass")

        // Content
        text?.takeIf { it.isNotBlank() }?.let { parts.add("text=\"$it\"") }
        contentDescription?.takeIf { it.isNotBlank() }?.let { parts.add("desc=\"$it\"") }
        viewIdResourceName?.let { parts.add("id=\"${it.substringAfterLast('/')}\"") }

        // Capabilities
        val caps = mutableListOf<String>()
        if (isClickable) caps.add("clickable")
        if (isScrollable) caps.add("scrollable")
        if (isEditable) caps.add("editable")
        if (isCheckable) caps.add("checkable")
        if (isChecked) caps.add("checked")
        if (!isEnabled) caps.add("disabled")
        if (caps.isNotEmpty()) parts.add("{${caps.joinToString(",")}}")

        val self = "$indent${parts.joinToString(" ")}"

        val childStrings = children.map { it.toCompactString(depth + 1) }
            .filter { it.isNotBlank() }

        return if (childStrings.isEmpty()) {
            self
        } else {
            "$self\n${childStrings.joinToString("\n")}"
        }
    }

    /**
     * Flatten the tree into a list for easier searching.
     */
    fun flatten(): List<ScreenNode> {
        return listOf(this) + children.flatMap { it.flatten() }
    }
}
