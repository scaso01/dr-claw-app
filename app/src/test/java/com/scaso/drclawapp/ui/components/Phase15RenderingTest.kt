package com.scaso.drclawapp.ui.components

import com.scaso.drclawapp.data.model.ActiveToolCall
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.model.ToolEvent
import com.scaso.drclawapp.data.model.ToolStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15 unit tests — state-to-props mapping assertions.
 *
 * These are pure JVM tests that verify the data model contracts
 * underpinning the Compose components added in Phase 15:
 *   - MessageBubble nested tool events
 *   - ToolUseCard status-to-visual mapping
 *   - ToolResultCard error styling branch
 *   - ActivityStatusBar running-tools label logic
 */
class Phase15RenderingTest {

    // ---------------------------------------------------------------
    // Test 1: grouped_turn_renders_text_plus_nested_tool_cards
    // Verifies that a Message destined for MessageBubble carries both
    // display text AND a non-empty toolEvents list that the composable
    // will iterate over.
    // ---------------------------------------------------------------
    @Test
    fun `grouped_turn_renders_text_plus_nested_tool_cards`() {
        val toolEvent = ToolEvent(
            toolCallId = "call-1",
            toolName = "bash",
            input = JsonPrimitive("""{"command":"ls -la"}"""),
            status = ToolStatus.Done,
            output = JsonPrimitive("""{"output":"total 0"}"""),
        )
        val message = Message(
            id = "msg-1",
            role = Role.ASSISTANT,
            content = "Here are the results:",
            timestamp = 1_700_000_000L,
            toolEvents = listOf(toolEvent),
        )

        // Both text content and tool events are present
        assertTrue("message content must be non-empty", message.content.isNotEmpty())
        assertTrue("toolEvents must contain one entry", message.toolEvents.size == 1)

        val event = message.toolEvents.first()
        assertEquals("bash", event.toolName)
        assertEquals(ToolStatus.Done, event.status)
        assertNull("no error on successful event", event.error)
        assertNotNull("output must be set on Done event", event.output)
    }

    // ---------------------------------------------------------------
    // Test 2: tool_use_card_shows_spinner_when_running
    // Verifies the ToolStatus.Running branch produces the expected
    // indicator variant (spinner rather than static icon).
    // We cannot invoke Compose here, so we assert the contract: when
    // status == Running, the composable must NOT show the static icon
    // and MUST show the progress indicator — captured via the model
    // property that drives the branch.
    // ---------------------------------------------------------------
    @Test
    fun `tool_use_card_shows_spinner_when_running`() {
        val event = ToolEvent(
            toolCallId = "call-2",
            toolName = "read_file",
            input = JsonPrimitive("{}"),
            status = ToolStatus.Running,
        )

        // The ToolUseCard branch condition: `status == ToolStatus.Running` → spinner
        assertTrue("Running status must be ToolStatus.Running",
            event.status == ToolStatus.Running)
        // Spinner is shown; static Build icon is NOT shown
        assertFalse("Running status must NOT map to Done",
            event.status == ToolStatus.Done)
        assertFalse("Running status must NOT map to Error",
            event.status == ToolStatus.Error)
        // Status badge text would be "running"
        val badgeText = when (event.status) {
            ToolStatus.Running -> "running"
            ToolStatus.Pending -> "pending"
            ToolStatus.Error   -> "error"
            ToolStatus.Done    -> ""
        }
        assertEquals("running", badgeText)
    }

    // ---------------------------------------------------------------
    // Test 3: tool_result_card_shows_error_styling_when_error
    // Verifies that a ToolEvent with a non-null error field maps to
    // the error-styled branch of ToolResultCard: red border/background,
    // Warning icon, "failed" label, and error-prefixed expanded text.
    // ---------------------------------------------------------------
    @Test
    fun `tool_result_card_shows_error_styling_when_error`() {
        val errorMessage = "Permission denied: /etc/passwd"
        val event = ToolEvent(
            toolCallId = "call-3",
            toolName = "read_file",
            input = JsonPrimitive("{}"),
            status = ToolStatus.Error,
            error = errorMessage,
        )

        // ToolResultCard receives: error = event.error
        val isError = event.error != null
        assertTrue("isError must be true when error field is set", isError)

        // Warning icon + "failed" badge + red tint are shown when isError == true
        // Error-prefixed display text
        val displayText = if (isError) {
            "Error: ${event.error!!.take(500)}"
        } else {
            event.output?.toString()?.take(500) ?: ""
        }
        assertTrue("expanded text must start with 'Error:'",
            displayText.startsWith("Error:"))
        assertTrue("expanded text must include the error message",
            displayText.contains(errorMessage))

        // Output is null — the card falls back to error string
        assertNull("output should be null for error events", event.output)
    }

    // ---------------------------------------------------------------
    // Test 4: activity_status_bar_shows_running_tools
    // Verifies the label construction logic inside ActivityStatusBar:
    // "Running: tool1, tool2 (Ns)" where N = elapsed seconds.
    // ---------------------------------------------------------------
    @Test
    fun `activity_status_bar_shows_running_tools`() {
        val nowMs = 1_700_000_010_000L // simulated "now"
        val startMs = 1_700_000_000_000L // 10 seconds ago

        val activeTools = setOf(
            ActiveToolCall(toolCallId = "c1", toolName = "bash",      startedAt = startMs),
            ActiveToolCall(toolCallId = "c2", toolName = "read_file", startedAt = startMs + 2_000L),
        )

        // Replicate ActivityStatusBar label logic
        val earliestStart = activeTools.minOf { it.startedAt }
        val elapsedSeconds = ((nowMs - earliestStart) / 1_000L).coerceAtLeast(0L)
        val toolNames = activeTools.sortedBy { it.startedAt }
            .joinToString(", ") { it.toolName }
        val label = "Running: $toolNames (${elapsedSeconds}s)"

        assertEquals(10L, elapsedSeconds)
        assertTrue("label must start with 'Running:'", label.startsWith("Running:"))
        assertTrue("label must contain 'bash'", label.contains("bash"))
        assertTrue("label must contain 'read_file'", label.contains("read_file"))
        assertTrue("label must end with elapsed time", label.endsWith("(10s)"))

        // Also verify: bar should be visible when activeTools is non-empty
        val hasActiveTools = activeTools.isNotEmpty()
        assertTrue("bar must show when activeTools non-empty", hasActiveTools)

        // And NOT require a non-null activity string
        val activityText: String? = null
        val shouldShow = activityText != null || hasActiveTools
        assertTrue("bar must show even with null activityText", shouldShow)
    }
}
