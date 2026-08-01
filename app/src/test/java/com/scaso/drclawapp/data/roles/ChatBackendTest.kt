package com.scaso.drclawapp.data.roles

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatBackendTest {
    @Test
    fun anthropicPrefixedModelIsCloud() {
        assertEquals(ChatBackend.CLOUD, chatBackendOf("anthropic/claude-sonnet-4-6"))
    }

    @Test
    fun openrouterPrefixedModelIsCloud() {
        assertEquals(ChatBackend.CLOUD, chatBackendOf("openrouter/meta-llama/llama-4-scout:free"))
    }

    @Test
    fun bareClaudeIdIsCloud() {
        assertEquals(ChatBackend.CLOUD, chatBackendOf("claude-opus-4-6"))
    }

    @Test
    fun localLlamaModelIsLocal() {
        assertEquals(ChatBackend.LOCAL, chatBackendOf("qwen3.5:27b-abliterated"))
    }

    @Test
    fun llamaServerPrefixedModelIsLocal() {
        assertEquals(ChatBackend.LOCAL, chatBackendOf("llama-server/qwen3:8b"))
    }

    @Test
    fun nullOrBlankDefaultsToLocal() {
        assertEquals(ChatBackend.LOCAL, chatBackendOf(null))
        assertEquals(ChatBackend.LOCAL, chatBackendOf("   "))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(ChatBackend.CLOUD, chatBackendOf("Anthropic/Claude-Sonnet-4-6"))
    }
}
