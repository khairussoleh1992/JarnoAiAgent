package com.example.jarnoai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

class JarnoAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarnoAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onInterrupt() {
        instance = null
    }

    fun getScreenText(): String {
        val rootNode = rootInActiveWindow ?: return "Layar tidak aktif."
        val builder = StringBuilder()
        extractText(rootNode, builder)
        return builder.toString()
    }

    private fun extractText(node: AccessibilityNodeInfo, builder: StringBuilder) {
        if (node.text != null && node.text.isNotBlank()) {
            builder.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                extractText(it, builder)
                it.recycle()
            }
        }
    }

    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun typeText(textToType: String) {
        val rootNode = rootInActiveWindow ?: return
        val activeNode = findFocusNode(rootNode)
        activeNode?.let {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }

    private fun findFocusNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                val found = findFocusNode(it)
                if (found != null) return found
                it.recycle()
            }
        }
        return null
    }
}
