package com.ai.assistant.service

import android.view.accessibility.AccessibilityNodeInfo
import com.ai.assistant.domain.model.ScreenNode
import android.graphics.Rect
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes the current screen by reading the AccessibilityNodeInfo tree.
 * Converts the Android accessibility tree into our ScreenNode model
 * that can be serialized and sent to the AI.
 */
@Singleton
class ScreenAnalyzer @Inject constructor() {

    private var nodeCounter = 0

    /**
     * Called by the AccessibilityService to provide the root node.
     */
    fun captureCurrentScreen(): ScreenNode? {
        val service = AssistantAccessibilityService.instance ?: return null
        val rootNode = service.rootInActiveWindow ?: return null

        nodeCounter = 0
        return try {
            parseNode(rootNode)
        } catch (e: Exception) {
            null
        } finally {
            rootNode.recycle()
        }
    }

    fun getCurrentPackageName(): String? {
        return AssistantAccessibilityService.instance?.currentPackageName
    }

    /**
     * Get the raw AccessibilityNodeInfo root (for ActionExecutor).
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return AssistantAccessibilityService.instance?.rootInActiveWindow
    }

    private fun parseNode(node: AccessibilityNodeInfo, depth: Int = 0): ScreenNode {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val currentIndex = nodeCounter++

        val children = mutableListOf<ScreenNode>()
        if (depth < 15) { // Prevent stack overflow on deep trees
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    // Skip invisible/empty nodes to reduce token count
                    if (isNodeRelevant(child)) {
                        children.add(parseNode(child, depth + 1))
                    } else {
                        // Still recurse into children even if this node isn't interesting
                        for (j in 0 until child.childCount) {
                            val grandchild = child.getChild(j) ?: continue
                            try {
                                if (isNodeRelevant(grandchild)) {
                                    children.add(parseNode(grandchild, depth + 1))
                                }
                            } finally {
                                grandchild.recycle()
                            }
                        }
                    }
                } finally {
                    child.recycle()
                }
            }
        }

        return ScreenNode(
            id = currentIndex.toString(),
            className = node.className?.toString(),
            text = node.text?.toString()?.take(100), // Limit text length
            contentDescription = node.contentDescription?.toString()?.take(100),
            viewIdResourceName = node.viewIdResourceName,
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isEditable = node.isEditable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            isFocusable = node.isFocusable,
            isEnabled = node.isEnabled,
            boundsInScreen = ScreenNode.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
            children = children,
            packageName = node.packageName?.toString(),
            index = currentIndex
        )
    }

    private fun isNodeRelevant(node: AccessibilityNodeInfo): Boolean {
        // A node is relevant if it has text, is interactive, or has a content description
        return node.text?.isNotBlank() == true ||
                node.contentDescription?.isNotBlank() == true ||
                node.isClickable ||
                node.isScrollable ||
                node.isEditable ||
                node.isCheckable ||
                node.viewIdResourceName != null ||
                node.childCount > 0
    }
}
