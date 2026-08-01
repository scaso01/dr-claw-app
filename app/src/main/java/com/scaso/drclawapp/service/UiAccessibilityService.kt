package com.scaso.drclawapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference

/**
 * Accessibility service for reading UI hierarchy and executing actions.
 * Used for LLM-driven UI analysis and HITL-confirmed action execution (D9).
 */
class UiAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L
        }
        _isRunning.value = true
        _instanceRef = WeakReference(this)

        // Drive other apps' UIs on request. UiActionReceiver emits these (after upstream HITL).
        // The actionRequests flow was previously emitted but NEVER collected — without this
        // collector, tap/type/scroll were a dead no-op. This wires the flow to real execution.
        scope.launch {
            actionRequests.collect { req ->
                when (req.type) {
                    UiActionType.TAP -> executeTap(req.viewId, req.text)
                    UiActionType.TYPE -> executeType(req.viewId, req.text ?: "", req.inputText ?: "")
                    UiActionType.SCROLL -> executeScroll(req.viewId, req.text, req.forward)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are collected on-demand via readHierarchy(), not passively
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _instanceRef?.clear()
        _instanceRef = null
        scope.cancel()
    }

    /**
     * Read the current UI hierarchy and return a simplified tree.
     */
    fun readHierarchy(): UiNode? {
        val root = rootInActiveWindow ?: return null
        return buildTree(root, depth = 0)
    }

    /**
     * Execute a tap action on the node matching the given criteria.
     * Returns true if the action was performed.
     */
    fun executeTap(viewId: String?, text: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNode(root, viewId, text) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Type text into the currently focused field or one matching criteria.
     */
    fun executeType(viewId: String?, text: String, inputText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNode(root, viewId, text) ?: return false
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Perform a scroll action on matching node.
     */
    fun executeScroll(viewId: String?, text: String?, forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNode(root, viewId, text) ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return target.performAction(action)
    }

    private fun buildTree(node: AccessibilityNodeInfo, depth: Int): UiNode? {
        if (depth > 15) return null // Prevent infinite recursion

        val children = mutableListOf<UiNode>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            buildTree(child, depth + 1)?.let { children.add(it) }
        }

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        return UiNode(
            className = node.className?.toString() ?: "",
            viewId = node.viewIdResourceName ?: "",
            text = node.text?.toString() ?: "",
            contentDescription = node.contentDescription?.toString() ?: "",
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isEditable = node.isEditable,
            bounds = UiBounds(rect.left, rect.top, rect.right, rect.bottom),
            children = children,
        )
    }

    private fun findNode(root: AccessibilityNodeInfo, viewId: String?, text: String?): AccessibilityNodeInfo? {
        if (viewId != null && viewId.isNotBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        if (text != null && text.isNotBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) return nodes[0]
        }
        return null
    }

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _actionRequests = MutableSharedFlow<UiActionRequest>(extraBufferCapacity = 16)
        val actionRequests: SharedFlow<UiActionRequest> = _actionRequests

        private var _instanceRef: WeakReference<UiAccessibilityService>? = null
        val instance: UiAccessibilityService? get() = _instanceRef?.get()

        fun requestAction(request: UiActionRequest) {
            _actionRequests.tryEmit(request)
        }
    }
}

@Serializable
data class UiNode(
    val className: String = "",
    val viewId: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEditable: Boolean = false,
    val bounds: UiBounds = UiBounds(),
    val children: List<UiNode> = emptyList(),
)

@Serializable
data class UiBounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

@Serializable
data class UiActionRequest(
    val type: UiActionType,
    val viewId: String? = null,
    val text: String? = null,
    val inputText: String? = null,
    val forward: Boolean = true,
    val confirmed: Boolean = false,
)

@Serializable
enum class UiActionType {
    TAP, TYPE, SCROLL,
}
