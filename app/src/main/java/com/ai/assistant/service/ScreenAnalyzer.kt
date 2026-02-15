package com.ai.assistant.service

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.assistant.domain.model.ScreenNode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenAnalyzer @Inject constructor() {

    companion object {
        private const val TAG = "ScreenAnalyzer"
        private const val MAX_DEPTH = 12
        private const val MAX_NODES = 500
    }

    private var nodeCounter = 0

    fun captureCurrentScreen(): ScreenNode? {
        val service = AssistantAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "Accessibility service not running")
            return null
        }

        val rootNode = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get root node", e)
            return null
        }

        if (rootNode == null) {
            Log.w(TAG, "Root node is null")
            return null
        }

        nodeCounter = 0
        return try {
            parseNode(rootNode, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse screen", e)
            null
        } finally {
            try {
                rootNode.recycle()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun getCurrentPackageName(): String? {
        return try {
            AssistantAccessibilityService.instance?.currentPackageName
        } catch (e: Exception) {
            null
        }
    }

    private fun parseNode(node: AccessibilityNodeInfo, depth: Int): ScreenNode {
        val bounds = Rect()
        try {
            node.getBoundsInScreen(bounds)
        } catch (e: Exception) {
            // use default empty bounds
        }

        val currentIndex = nodeCounter++

        val children = mutableListOf<ScreenNode>()

        if (depth < MAX_DEPTH && nodeCounter < MAX_NODES) {
            val childCount = try {
                node.childCount
            } catch (e: Exception) {
                0
            }

            for (i in 0 until childCount) {
                if (nodeCounter >= MAX_NODES) break

                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                } ?: continue

                try {
                    if (isNodeRelevant(child)) {
                        children.add(parseNode(child, depth + 1))
                    } else {
                        // Check grandchildren
                        val grandChildCount = try {
                            child.childCount
                        } catch (e: Exception) {
                            0
                        }

                        for (j in 0 until grandChildCount) {
                            if (nodeCounter >= MAX_NODES) break

                            val grandchild = try {
                                child.getChild(j)
                            } catch (e: Exception) {
                                null
                            } ?: continue

                            try {
                                if (isNodeRelevant(grandchild)) {
                                    children.add(
                                        parseNode(grandchild, depth + 1)
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing grandchild", e)
                            } finally {
                                try {
                                    grandchild.recycle()
                                } catch (e: Exception) { }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing child $i", e)
                } finally {
                    try {
                        child.recycle()
                    } catch (e: Exception) { }
                }
            }
        }

        return ScreenNode(
            id = currentIndex.toString(),
            className = try { node.className?.toString() } catch (e: Exception) { null },
            text = try {
                node.text?.toString()?.take(100)
            } catch (e: Exception) { null },
            contentDescription = try {
                node.contentDescription?.toString()?.take(100)
            } catch (e: Exception) { null },
            viewIdResourceName = try {
                node.viewIdResourceName
            } catch (e: Exception) { null },
            isClickable = try { node.isClickable } catch (e: Exception) { false },
            isScrollable = try { node.isScrollable } catch (e: Exception) { false },
            isEditable = try { node.isEditable } catch (e: Exception) { false },
            isCheckable = try { node.isCheckable } catch (e: Exception) { false },
            isChecked = try { node.isChecked } catch (e: Exception) { false },
            isFocusable = try { node.isFocusable } catch (e: Exception) { false },
            isEnabled = try { node.isEnabled } catch (e: Exception) { true },
            boundsInScreen = ScreenNode.Bounds(
                bounds.left, bounds.top, bounds.right, bounds.bottom
            ),
            children = children,
            packageName = try {
                node.packageName?.toString()
            } catch (e: Exception) { null },
            index = currentIndex
        )
    }

    private fun isNodeRelevant(node: AccessibilityNodeInfo): Boolean {
        return try {
            node.text?.isNotBlank() == true ||
                    node.contentDescription?.isNotBlank() == true ||
                    node.isClickable ||
                    node.isScrollable ||
                    node.isEditable ||
                    node.isCheckable ||
                    node.viewIdResourceName != null ||
                    node.childCount > 0
        } catch (e: Exception) {
            false
        }
    }
}
