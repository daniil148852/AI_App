package com.ai.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.assistant.domain.model.UiAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenAnalyzer: ScreenAnalyzer
) {
    private val service: AssistantAccessibilityService?
        get() = AssistantAccessibilityService.instance

    suspend fun execute(action: UiAction): Boolean {
        val svc = service ?: return false

        return when (action) {
            is UiAction.Click -> executeClick(svc, action)
            is UiAction.LongClick -> executeLongClick(svc, action)
            is UiAction.TypeText -> executeTypeText(svc, action)
            is UiAction.SetText -> executeSetText(svc, action)
            is UiAction.ClearText -> executeClearText(svc, action)
            is UiAction.Scroll -> executeScroll(svc, action)
            is UiAction.PressButton -> executePressButton(svc, action)
            is UiAction.OpenApp -> executeOpenApp(action)
            is UiAction.Wait -> {
                delay(action.milliseconds)
                true
            }
            is UiAction.Swipe -> executeSwipe(svc, action)
            is UiAction.Done -> true
            is UiAction.Error -> false
        }
    }

    private fun executeClick(svc: AccessibilityService, action: UiAction.Click): Boolean {
        val node = findNode(svc, action.nodeIndex, action.nodeText, action.nodeId, action.nodeDescription)
            ?: return false
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            node.recycle()
        }
    }

    private fun executeLongClick(svc: AccessibilityService, action: UiAction.LongClick): Boolean {
        val node = findNode(svc, action.nodeIndex, action.nodeText, action.nodeId)
            ?: return false
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } finally {
            node.recycle()
        }
    }

    private fun executeTypeText(svc: AccessibilityService, action: UiAction.TypeText): Boolean {
        val node = findEditableNode(svc, action.nodeIndex, action.nodeId)
            ?: return false

        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    (node.text?.toString() ?: "") + action.text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            node.recycle()
        }
    }

    private fun executeSetText(svc: AccessibilityService, action: UiAction.SetText): Boolean {
        val node = findEditableNode(svc, action.nodeIndex, action.nodeId)
            ?: return false

        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    action.text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            node.recycle()
        }
    }

    private fun executeClearText(svc: AccessibilityService, action: UiAction.ClearText): Boolean {
        val node = findEditableNode(svc, action.nodeIndex, action.nodeId)
            ?: return false

        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            node.recycle()
        }
    }

    private fun executeScroll(svc: AccessibilityService, action: UiAction.Scroll): Boolean {
        val root = svc.rootInActiveWindow ?: return false

        return try {
            val scrollable = if (action.nodeIndex != null) {
                findNodeByIndex(root, action.nodeIndex)
            } else {
                findFirstScrollable(root)
            }

            if (scrollable != null) {
                val scrollAction = when (action.direction) {
                    UiAction.ScrollDirection.DOWN,
                    UiAction.ScrollDirection.RIGHT ->
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD

                    UiAction.ScrollDirection.UP,
                    UiAction.ScrollDirection.LEFT ->
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val result = scrollable.performAction(scrollAction)
                scrollable.recycle()
                result
            } else {
                false
            }
        } finally {
            root.recycle()
        }
    }

    private fun executePressButton(svc: AccessibilityService, action: UiAction.PressButton): Boolean {
        val globalAction = when (action.button) {
            UiAction.SystemButton.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            UiAction.SystemButton.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            UiAction.SystemButton.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            UiAction.SystemButton.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }
        return svc.performGlobalAction(globalAction)
    }

    private fun executeOpenApp(action: UiAction.OpenApp): Boolean {
        return try {
            val packageName = action.packageName
                ?: resolvePackageName(action.appName ?: return false)
                ?: return false

            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun executeSwipe(svc: AccessibilityService, action: UiAction.Swipe): Boolean {
        val path = Path().apply {
            moveTo(action.startX.toFloat(), action.startY.toFloat())
            lineTo(action.endX.toFloat(), action.endY.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, action.duration))
            .build()

        val deferred = CompletableDeferred<Boolean>()

        svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete(false)
            }
        }, null)

        return withTimeoutOrNull(5000) { deferred.await() } ?: false
    }

    // ═══════════════════════════════════════════════════
    // Node finding utilities
    // ═══════════════════════════════════════════════════

    private fun findNode(
        svc: AccessibilityService,
        nodeIndex: Int?,
        nodeText: String?,
        nodeId: String?,
        nodeDescription: String? = null
    ): AccessibilityNodeInfo? {
        val root = svc.rootInActiveWindow ?: return null

        // Try by resource ID first (most reliable)
        if (nodeId != null) {
            val nodes = root.findAccessibilityNodeInfosByViewId(nodeId)
            if (!nodes.isNullOrEmpty()) return nodes[0]

            // Try with package prefix
            if (!nodeId.contains(":id/")) {
                val pkg = root.packageName?.toString()
                if (pkg != null) {
                    val fullId = "$pkg:id/$nodeId"
                    val nodesById = root.findAccessibilityNodeInfosByViewId(fullId)
                    if (!nodesById.isNullOrEmpty()) return nodesById[0]
                }
            }
        }

        // Try by text
        if (nodeText != null) {
            val nodes = root.findAccessibilityNodeInfosByText(nodeText)
            if (!nodes.isNullOrEmpty()) {
                val clickable = nodes.firstOrNull { it.isClickable }
                if (clickable != null) return clickable

                val withParent = nodes.firstOrNull { findClickableParent(it) != null }
                if (withParent != null) {
                    val parent = findClickableParent(withParent)
                    if (parent != null) return parent
                }

                return nodes[0]
            }
        }

        // Try by content description
        if (nodeDescription != null) {
            val found = findNodeByContentDescription(root, nodeDescription)
            if (found != null) return found
        }

        // Try by index
        if (nodeIndex != null) {
            return findNodeByIndex(root, nodeIndex)
        }

        return null
    }

    private fun findEditableNode(
        svc: AccessibilityService,
        nodeIndex: Int?,
        nodeId: String?
    ): AccessibilityNodeInfo? {
        val root = svc.rootInActiveWindow ?: return null

        // By ID
        if (nodeId != null) {
            val fullId = if (!nodeId.contains(":id/")) {
                "${root.packageName}:id/$nodeId"
            } else {
                nodeId
            }
            val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
            val editable = nodes?.firstOrNull { it.isEditable }
            if (editable != null) return editable
        }

        // By index
        if (nodeIndex != null) {
            val node = findNodeByIndex(root, nodeIndex)
            if (node != null && node.isEditable) return node
        }

        // Fallback: find any editable field
        return findFirstEditable(root)
    }

    private var indexCounter = 0

    private fun findNodeByIndex(root: AccessibilityNodeInfo, targetIndex: Int): AccessibilityNodeInfo? {
        indexCounter = 0
        return findNodeByIndexRecursive(root, targetIndex)
    }

    private fun findNodeByIndexRecursive(
        node: AccessibilityNodeInfo,
        targetIndex: Int
    ): AccessibilityNodeInfo? {
        val currentIndex = indexCounter++
        if (currentIndex == targetIndex) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByIndexRecursive(child, targetIndex)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByContentDescription(
        node: AccessibilityNodeInfo,
        description: String
    ): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()
                ?.contains(description, ignoreCase = true) == true
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByContentDescription(child, description)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent ?: return null
        repeat(5) {
            if (current.isClickable) return current
            current = current.parent ?: return null
        }
        return null
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstScrollable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstEditable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun resolvePackageName(appName: String): String? {
        val lowerName = appName.lowercase()

        val knownApps = mapOf(
            "whatsapp" to "com.whatsapp",
            "вотсап" to "com.whatsapp",
            "ватсап" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "телеграм" to "org.telegram.messenger",
            "телега" to "org.telegram.messenger",
            "chrome" to "com.android.chrome",
            "хром" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "ютуб" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "инстаграм" to "com.instagram.android",
            "инста" to "com.instagram.android",
            "vk" to "com.vkontakte.android",
            "вк" to "com.vkontakte.android",
            "вконтакте" to "com.vkontakte.android",
            "gmail" to "com.google.android.gm",
            "камера" to "com.android.camera2",
            "camera" to "com.android.camera2",
            "настройки" to "com.android.settings",
            "settings" to "com.android.settings",
            "карты" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",
            "календарь" to "com.google.android.calendar",
            "calendar" to "com.google.android.calendar",
            "калькулятор" to "com.google.android.calculator",
            "calculator" to "com.google.android.calculator",
            "часы" to "com.google.android.deskclock",
            "clock" to "com.google.android.deskclock"
        )

        knownApps.entries.firstOrNull { lowerName.contains(it.key) }?.let {
            return it.value
        }

        // Search installed apps
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
            apps.firstOrNull { resolveInfo ->
                resolveInfo.loadLabel(pm).toString().contains(appName, ignoreCase = true)
            }?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }
}
