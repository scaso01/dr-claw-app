package com.scaso.drclawapp.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for VoiceConversationManager data types and state machine enums.
 *
 * Tests that require Android Context (AudioRecord, SpeechRecognizer, MediaPlayer)
 * are in the instrumented test suite. These tests cover the state types, mode enum,
 * and FullDuplexState data class that drive the state machine.
 */
class VoiceConversationManagerTest {

    // ========== VoiceMode enum ==========

    @Test
    fun `VoiceMode has exactly two values`() {
        assertEquals(2, VoiceMode.entries.size)
    }

    @Test
    fun `VoiceMode contains HALF_DUPLEX and FULL_DUPLEX`() {
        assertTrue(VoiceMode.entries.contains(VoiceMode.HALF_DUPLEX))
        assertTrue(VoiceMode.entries.contains(VoiceMode.FULL_DUPLEX))
    }

    // ========== VoiceState enum ==========

    @Test
    fun `VoiceState has six values`() {
        assertEquals(6, VoiceState.entries.size)
    }

    @Test
    fun `VoiceState contains all expected values`() {
        val expected = setOf("IDLE", "LISTENING", "SENDING", "WAITING", "PLAYING", "ERROR")
        val actual = VoiceState.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    // ========== FullDuplexState data class ==========

    @Test
    fun `FullDuplexState defaults are all false`() {
        val state = FullDuplexState()
        assertFalse(state.micActive)
        assertFalse(state.userSpeaking)
        assertFalse(state.botSpeaking)
        assertFalse(state.interrupted)
        assertFalse(state.connected)
    }

    @Test
    fun `FullDuplexState equality`() {
        val state1 = FullDuplexState(micActive = true, connected = true)
        val state2 = FullDuplexState(micActive = true, connected = true)
        assertEquals(state1, state2)
    }

    @Test
    fun `FullDuplexState inequality`() {
        val state1 = FullDuplexState(micActive = true)
        val state2 = FullDuplexState(micActive = false)
        assertNotEquals(state1, state2)
    }

    @Test
    fun `FullDuplexState copy preserves unchanged fields`() {
        val state = FullDuplexState(connected = true, micActive = true)
        val updated = state.copy(botSpeaking = true)
        assertTrue(updated.connected)
        assertTrue(updated.micActive)
        assertTrue(updated.botSpeaking)
        assertFalse(updated.userSpeaking)
        assertFalse(updated.interrupted)
    }

    @Test
    fun `FullDuplexState copy can set interrupted`() {
        val state = FullDuplexState(botSpeaking = true)
        val interrupted = state.copy(interrupted = true, botSpeaking = false)
        assertTrue(interrupted.interrupted)
        assertFalse(interrupted.botSpeaking)
    }

    @Test
    fun `FullDuplexState hashCode is consistent with equals`() {
        val state1 = FullDuplexState(micActive = true, userSpeaking = true)
        val state2 = FullDuplexState(micActive = true, userSpeaking = true)
        assertEquals(state1.hashCode(), state2.hashCode())
    }

    @Test
    fun `FullDuplexState toString contains field names`() {
        val state = FullDuplexState(micActive = true)
        val str = state.toString()
        assertTrue(str.contains("micActive=true"))
        assertTrue(str.contains("userSpeaking=false"))
        assertTrue(str.contains("botSpeaking=false"))
        assertTrue(str.contains("interrupted=false"))
        assertTrue(str.contains("connected=false"))
    }

    // ========== State transition semantics ==========

    @Test
    fun `user speaking during bot speech represents interrupt scenario`() {
        val state = FullDuplexState(
            micActive = true,
            connected = true,
            userSpeaking = true,
            botSpeaking = true,
        )
        // After interrupt is processed:
        val afterInterrupt = state.copy(
            botSpeaking = false,
            interrupted = true,
        )
        assertTrue(afterInterrupt.userSpeaking)
        assertFalse(afterInterrupt.botSpeaking)
        assertTrue(afterInterrupt.interrupted)
    }

    @Test
    fun `full conversation flow state transitions`() {
        // Simulate full-duplex conversation state transitions
        var state = FullDuplexState()

        // 1. Connect
        state = state.copy(connected = true)
        assertTrue(state.connected)

        // 2. Start mic
        state = state.copy(micActive = true)
        assertTrue(state.micActive)

        // 3. User starts speaking (VAD)
        state = state.copy(userSpeaking = true)
        assertTrue(state.userSpeaking)

        // 4. User stops speaking
        state = state.copy(userSpeaking = false)
        assertFalse(state.userSpeaking)

        // 5. Bot starts speaking
        state = state.copy(botSpeaking = true)
        assertTrue(state.botSpeaking)

        // 6. Bot stops speaking
        state = state.copy(botSpeaking = false)
        assertFalse(state.botSpeaking)

        // 7. Stop mic
        state = state.copy(micActive = false)
        assertFalse(state.micActive)

        // 8. Disconnect
        state = state.copy(connected = false)
        assertEquals(FullDuplexState(), state)
    }

    @Test
    fun `interrupt resets to clean listening state`() {
        val duringInterrupt = FullDuplexState(
            connected = true,
            micActive = true,
            userSpeaking = true,
            botSpeaking = false,
            interrupted = true,
        )

        // After the new response starts coming in, clear interrupted flag
        val afterNewResponse = duringInterrupt.copy(
            interrupted = false,
            userSpeaking = false,
            botSpeaking = true,
        )

        assertTrue(afterNewResponse.botSpeaking)
        assertFalse(afterNewResponse.interrupted)
        assertFalse(afterNewResponse.userSpeaking)
    }

    @Test
    fun `disconnection clears all active flags`() {
        val active = FullDuplexState(
            connected = true,
            micActive = true,
            userSpeaking = true,
            botSpeaking = true,
            interrupted = true,
        )
        val disconnected = FullDuplexState() // all false
        assertFalse(disconnected.connected)
        assertFalse(disconnected.micActive)
        assertNotEquals(active, disconnected)
    }
}
