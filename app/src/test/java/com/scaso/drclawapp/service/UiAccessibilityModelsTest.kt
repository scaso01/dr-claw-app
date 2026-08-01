package com.scaso.drclawapp.service

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAccessibilityModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `UiNode serializes and deserializes`() {
        val node = UiNode(
            className = "android.widget.Button",
            viewId = "com.example:id/btn_submit",
            text = "Submit",
            isClickable = true,
            bounds = UiBounds(10, 20, 200, 80),
            children = emptyList(),
        )
        val encoded = json.encodeToString(UiNode.serializer(), node)
        val decoded = json.decodeFromString(UiNode.serializer(), encoded)
        assertEquals("android.widget.Button", decoded.className)
        assertEquals("Submit", decoded.text)
        assertTrue(decoded.isClickable)
        assertEquals(10, decoded.bounds.left)
    }

    @Test
    fun `UiNode with children round-trips`() {
        val child = UiNode(className = "android.widget.TextView", text = "Hello")
        val parent = UiNode(
            className = "android.widget.LinearLayout",
            children = listOf(child),
        )
        val encoded = json.encodeToString(UiNode.serializer(), parent)
        val decoded = json.decodeFromString(UiNode.serializer(), encoded)
        assertEquals(1, decoded.children.size)
        assertEquals("Hello", decoded.children[0].text)
    }

    @Test
    fun `UiActionRequest serializes all types`() {
        val actions = listOf(
            UiActionRequest(type = UiActionType.TAP, viewId = "btn_ok"),
            UiActionRequest(type = UiActionType.TYPE, text = "field", inputText = "hello"),
            UiActionRequest(type = UiActionType.SCROLL, forward = false),
        )
        actions.forEach { request ->
            val encoded = json.encodeToString(UiActionRequest.serializer(), request)
            val decoded = json.decodeFromString(UiActionRequest.serializer(), encoded)
            assertEquals(request.type, decoded.type)
        }
    }

    @Test
    fun `UiBounds defaults to zero`() {
        val bounds = UiBounds()
        assertEquals(0, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(0, bounds.right)
        assertEquals(0, bounds.bottom)
    }
}
