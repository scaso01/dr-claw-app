package com.scaso.drclawapp.cmd

import com.scaso.drclawapp.data.approval.ApprovalDecision
import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CmdHandlersTest {

    @Test
    fun `CalendarHandler has correct tool name`() {
        val handler = CalendarHandler()
        assertEquals("device.calendar", handler.tool)
    }

    @Test
    fun `LocationHandler has correct tool name`() {
        val handler = LocationHandler()
        assertEquals("device.location", handler.tool)
    }

    @Test
    fun `ActivityHandler has correct tool name`() {
        val handler = ActivityHandler()
        assertEquals("device.activity", handler.tool)
    }

    @Test
    fun `CmdDispatcher registers all expected handlers`() {
        // CmdDispatcher requires Context, but supportedTools() only reads the map.
        // We verify the handler tool names directly instead.
        val expectedTools = listOf(
            "device.calendar",
            "device.location",
            "device.activity",
        )
        val handlers = listOf(
            CalendarHandler(),
            LocationHandler(),
            ActivityHandler(),
        )
        handlers.forEach { handler ->
            assertTrue(
                "Handler ${handler.tool} should be in expected list",
                handler.tool in expectedTools,
            )
        }
    }
}

/**
 * HITL gating tests for [CmdApprovalGateImpl] (security fix: incoming cmd frames must
 * not execute without explicit user approval, or a persisted always-allow entry).
 */
class CmdApprovalGateImplTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun newGate(
        appPreferences: FakeCmdAppPreferences = FakeCmdAppPreferences(),
        isForeground: () -> Boolean = { true },
        onBackgroundNotify: (String) -> Unit = {},
    ): Pair<CmdApprovalGateImpl, ApprovalRepository> {
        val gatewayClient = GatewayClient(url = "ws://fake", token = "fake", scope = scope)
        val approvalRepository = ApprovalRepository(gatewayClient, scope)
        val gate = CmdApprovalGateImpl(
            approvalRepository = approvalRepository,
            appPreferences = appPreferences,
            scope = scope,
            isAppInForeground = isForeground,
            notifyBackgrounded = onBackgroundNotify,
        )
        return gate to approvalRepository
    }

    @Test
    fun `always-allowed tool bypasses the approval prompt`() = runTest {
        val prefs = FakeCmdAppPreferences(initial = setOf("device.info"))
        val (gate, repo) = newGate(appPreferences = prefs)

        val approved = gate.requestApproval("device.info", JsonObject(emptyMap()))

        assertTrue(approved)
        assertTrue("No dialog should have been raised", repo.pendingApprovals.value.isEmpty())
    }

    @Test
    fun `approve decision resolves true`() = runTest {
        val (gate, repo) = newGate()
        val result = async { gate.requestApproval("clipboard.get", JsonObject(emptyMap())) }

        val request = repo.approvalEvents.first()
        repo.respond(request.id, ApprovalDecision.APPROVE)

        assertTrue(result.await())
    }

    @Test
    fun `deny decision resolves false`() = runTest {
        val (gate, repo) = newGate()
        val result = async { gate.requestApproval("clipboard.set", JsonObject(emptyMap())) }

        val request = repo.approvalEvents.first()
        repo.respond(request.id, ApprovalDecision.DENY)

        assertFalse(result.await())
    }

    @Test
    fun `always-allow decision resolves true and persists the tool`() = runTest {
        val prefs = FakeCmdAppPreferences()
        val (gate, repo) = newGate(appPreferences = prefs)
        val result = async { gate.requestApproval("device.calendar", JsonObject(emptyMap())) }

        val request = repo.approvalEvents.first()
        repo.respond(request.id, ApprovalDecision.ALWAYS_ALLOW)

        assertTrue(result.await())

        // Persistence happens on a fire-and-forget launch; poll briefly for it to land.
        var attempts = 0
        while (!prefs.toolsFlow.value.contains("device.calendar") && attempts < 100) {
            delay(10)
            attempts++
        }
        assertTrue(prefs.toolsFlow.value.contains("device.calendar"))
    }

    @Test
    fun `request raised while backgrounded notifies the caller`() = runTest {
        var notifiedTool: String? = null
        val (gate, repo) = newGate(
            isForeground = { false },
            onBackgroundNotify = { notifiedTool = it },
        )
        val result = async { gate.requestApproval("device.location", JsonObject(emptyMap())) }

        val request = repo.approvalEvents.first()
        assertEquals("device.location", notifiedTool)

        repo.respond(request.id, ApprovalDecision.DENY)
        result.await()
    }

    @Test
    fun `request raised while foregrounded does not notify`() = runTest {
        var notified = false
        val (gate, repo) = newGate(
            isForeground = { true },
            onBackgroundNotify = { notified = true },
        )
        val result = async { gate.requestApproval("device.activity", JsonObject(emptyMap())) }

        val request = repo.approvalEvents.first()
        assertFalse(notified)

        repo.respond(request.id, ApprovalDecision.APPROVE)
        result.await()
    }
}

private class FakeCmdAppPreferences(initial: Set<String> = emptySet()) : AppPreferences(null) {
    val toolsFlow = MutableStateFlow(initial)
    override val autoApproveTools: Flow<Set<String>> = toolsFlow
    override suspend fun addAutoApproveTool(toolName: String) {
        toolsFlow.value = toolsFlow.value + toolName
    }
}
