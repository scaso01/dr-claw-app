package com.scaso.drclawapp.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure, Context-free [UiActionReceiver.handle] secret gate + op mapping.
 * The configured secret is INJECTED (not read from BuildConfig), so the test is hermetic.
 */
class UiActionReceiverTest {

    private val secret = "s3cr3t"

    private fun run(
        op: String,
        sent: String,
        configured: String,
        viewId: String? = null,
        text: String? = null,
        inputText: String? = null,
        forward: Boolean = true,
    ): Pair<Boolean, UiActionRequest?> {
        var captured: UiActionRequest? = null
        val ok = UiActionReceiver.handle(op, sent, viewId, text, inputText, forward, configured) { captured = it }
        return ok to captured
    }

    @Test
    fun `wrong secret rejects and dispatches nothing`() {
        val (ok, req) = run("tap", "nope", secret, text = "OK")
        assertFalse(ok)
        assertNull(req)
    }

    @Test
    fun `blank configured secret rejects even a blank sent secret`() {
        val (ok, req) = run("tap", "", "", text = "OK")
        assertFalse(ok)
        assertNull(req)
    }

    @Test
    fun `correct secret with tap dispatches a confirmed TAP`() {
        val (ok, req) = run("tap", secret, secret, text = "Send")
        assertTrue(ok)
        assertEquals(UiActionType.TAP, req?.type)
        assertEquals("Send", req?.text)
        assertTrue("HITL is upstream so the request is pre-confirmed", req?.confirmed == true)
    }

    @Test
    fun `type op carries inputText`() {
        val (ok, req) = run("type", secret, secret, viewId = "field", text = "x", inputText = "hello")
        assertTrue(ok)
        assertEquals(UiActionType.TYPE, req?.type)
        assertEquals("hello", req?.inputText)
    }

    @Test
    fun `scroll op carries forward flag`() {
        val (ok, req) = run("scroll", secret, secret, viewId = "list", forward = false)
        assertTrue(ok)
        assertEquals(UiActionType.SCROLL, req?.type)
        assertFalse(req?.forward ?: true)
    }

    @Test
    fun `unknown op rejects`() {
        val (ok, req) = run("frobnicate", secret, secret)
        assertFalse(ok)
        assertNull(req)
    }

    @Test
    fun `op is case-insensitive`() {
        val (ok, req) = run("TAP", secret, secret, text = "X")
        assertTrue(ok)
        assertEquals(UiActionType.TAP, req?.type)
    }
}
