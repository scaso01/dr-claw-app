package com.scaso.drclawapp.data.repository

import app.cash.turbine.test
import com.scaso.drclawapp.data.model.AckState
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.HistoryEntry
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatRepositoryTest {

    private lateinit var fakeEvents: MutableSharedFlow<GatewayEvent>
    private lateinit var fakeConnectionState: MutableStateFlow<ConnectionState>
    private lateinit var fakeClient: FakeGatewayClient
    private lateinit var repository: ChatRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setup() {
        fakeEvents = MutableSharedFlow(extraBufferCapacity = 64)
        fakeConnectionState = MutableStateFlow(ConnectionState.Disconnected)
        fakeClient = FakeGatewayClient(fakeEvents, fakeConnectionState)
        repository = ChatRepository(fakeClient, scope)
    }

    @Test
    fun `initial messages list is empty`() {
        assertTrue(repository.messages.value.isEmpty())
    }

    @Test
    fun `ChatDelta creates new assistant message`() = runTest {
        repository.messages.test {
            assertEquals(emptyList<Any>(), awaitItem()) // initial

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Hello", seq = 1))

            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(Role.ASSISTANT, messages[0].role)
            assertEquals("Hello", messages[0].content)
            assertTrue(messages[0].isStreaming)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subsequent ChatDeltas accumulate text`() = runTest {
        repository.messages.test {
            skipItems(1) // initial empty

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Hello", seq = 1))
            val msg1 = awaitItem()
            assertEquals("Hello", msg1[0].content)

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = " world", seq = 2))
            val msg2 = awaitItem()
            assertEquals("Hello world", msg2[0].content)
            assertTrue(msg2[0].isStreaming)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatCatchup replaces partial content of existing streaming message`() = runTest {
        repository.messages.test {
            skipItems(1) // initial empty

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Hel", seq = 1))
            awaitItem()

            fakeEvents.emit(GatewayEvent.ChatCatchup(runId = "run-1", content = "Hello there, full accumulated text", seq = 5))
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("Hello there, full accumulated text", messages[0].content)
            assertTrue(messages[0].isStreaming)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatCatchup creates new streaming message when none exists`() = runTest {
        repository.messages.test {
            skipItems(1) // initial empty

            // No prior delta — simulates app restarted mid-run
            fakeEvents.emit(GatewayEvent.ChatCatchup(runId = "run-2", content = "resumed content", seq = 3))
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(Role.ASSISTANT, messages[0].role)
            assertEquals("resumed content", messages[0].content)
            assertTrue(messages[0].isStreaming)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delta after catchup appends to catchup content`() = runTest {
        repository.messages.test {
            skipItems(1) // initial empty

            fakeEvents.emit(GatewayEvent.ChatCatchup(runId = "run-3", content = "Accumulated so far", seq = 5))
            awaitItem()

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-3", text = " and more", seq = 6))
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("Accumulated so far and more", messages[0].content)
            assertTrue(messages[0].isStreaming)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatFinal finalizes streaming message`() = runTest {
        repository.messages.test {
            skipItems(1)

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Answer", seq = 1))
            awaitItem()

            fakeEvents.emit(GatewayEvent.ChatFinal(runId = "run-1", text = null, stopReason = "end_turn"))
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertFalse(messages[0].isStreaming)
            assertEquals("Answer", messages[0].content)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatFinal replaces with final text if present`() = runTest {
        repository.messages.test {
            skipItems(1)

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-2", text = "Part 1", seq = 1))
            awaitItem()

            fakeEvents.emit(GatewayEvent.ChatFinal(runId = "run-2", text = "Full response", stopReason = "end_turn"))
            val messages = awaitItem()
            assertEquals("Full response", messages[0].content)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatAborted stops streaming`() = runTest {
        repository.messages.test {
            skipItems(1)

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-3", text = "Partial", seq = 1))
            awaitItem()

            fakeEvents.emit(GatewayEvent.ChatAborted(runId = "run-3"))
            val messages = awaitItem()
            assertFalse(messages[0].isStreaming)
            assertEquals("Partial", messages[0].content)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatError appends error text`() = runTest {
        repository.messages.test {
            skipItems(1)

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-4", text = "Start", seq = 1))
            awaitItem()

            fakeEvents.emit(GatewayEvent.ChatError(runId = "run-4", message = "Server error"))
            val messages = awaitItem()
            assertFalse(messages[0].isStreaming)
            assertTrue(messages[0].content.contains("Server error"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `HistoryResult populates messages`() = runTest {
        repository.messages.test {
            skipItems(1)

            val entries = listOf(
                HistoryEntry(role = "user", content = "Hi", timestamp = 1000L),
                HistoryEntry(role = "assistant", content = "Hello!", timestamp = 2000L),
            )
            fakeEvents.emit(GatewayEvent.HistoryResult(entries))
            val messages = awaitItem()

            assertEquals(2, messages.size)
            assertEquals(Role.USER, messages[0].role)
            assertEquals("Hi", messages[0].content)
            assertEquals(Role.ASSISTANT, messages[1].role)
            assertEquals("Hello!", messages[1].content)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `HistoryResult does not duplicate existing messages`() = runTest {
        repository.messages.test {
            skipItems(1)

            val entries = listOf(
                HistoryEntry(role = "user", content = "First", timestamp = 1000L),
            )
            fakeEvents.emit(GatewayEvent.HistoryResult(entries))
            val first = awaitItem()
            assertEquals(1, first.size)

            // Emit same history again — StateFlow deduplicates equal lists,
            // so no new item is emitted
            fakeEvents.emit(GatewayEvent.HistoryResult(entries))
            expectNoEvents()

            // Verify current state hasn't grown
            assertEquals(1, repository.messages.value.size)

            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test
    fun `HistoryResult keeps chronological order when a stale local message is present`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        repository.messages.test {
            skipItems(1) // initial empty

            // User sends a message (ackState -> SENT) — simulates the device going offline
            // mid-turn, so no ChatFinal ever arrives locally to advance it to DONE.
            repository.sendMessage("What's the plan?")
            var sentMsg = awaitItem()
            if (sentMsg[0].ackState == AckState.PENDING) sentMsg = awaitItem()
            assertEquals(AckState.SENT, sentMsg[0].ackState)
            val userTimestamp = sentMsg[0].timestamp

            // Reconnect: history refresh reveals the run actually finished while disconnected
            // — the answer is now in server history, timestamped after the user's message.
            val entries = listOf(
                HistoryEntry(role = "assistant", content = "Here's the plan.", timestamp = userTimestamp + 5000L),
            )
            fakeEvents.emit(GatewayEvent.HistoryResult(entries))
            val messages = awaitItem()

            // Regression: `newHistory + localOnly` used to append the stale SENT user message
            // AFTER the freshly-fetched answer regardless of timestamp, so the reversed chat
            // list rendered the user's own message as the most recent item — visually burying
            // the real answer above it, which looked like "sent a message, got nothing back."
            assertEquals(2, messages.size)
            assertEquals(Role.USER, messages[0].role)
            assertEquals(Role.ASSISTANT, messages[1].role)
            assertEquals("Here's the plan.", messages[1].content)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessage while offline queues with PENDING syncState`() = runTest {
        // Default state is Disconnected
        repository.messages.test {
            skipItems(1) // initial empty

            repository.sendMessage("Offline message")
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(AckState.PENDING, messages[0].ackState)
            assertEquals(com.scaso.drclawapp.data.model.SyncState.PENDING, messages[0].syncState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessage sets ackState to SENT`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        repository.messages.test {
            skipItems(1) // initial empty

            repository.sendMessage("Hello")
            // First emission is PENDING, second is SENT (after gateway ack)
            var messages = awaitItem()
            if (messages[0].ackState == AckState.PENDING) {
                messages = awaitItem()
            }
            assertEquals(1, messages.size)
            assertEquals(AckState.SENT, messages[0].ackState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatDelta updates user message ackState to PROCESSING`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        repository.messages.test {
            skipItems(1) // initial empty

            repository.sendMessage("Hello")
            // Consume PENDING and SENT emissions
            var sent = awaitItem()
            if (sent[0].ackState == AckState.PENDING) sent = awaitItem()

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Hi", seq = 1))
            // May emit multiple updates (assistant message + ack state change)
            var messages = awaitItem()
            // Keep consuming until we see the ack state change
            while (messages.none { it.role == Role.USER && it.ackState == AckState.PROCESSING }) {
                messages = awaitItem()
            }
            val userMsg = messages.first { it.role == Role.USER }
            assertEquals(AckState.PROCESSING, userMsg.ackState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ChatFinal updates user message ackState to DONE`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        repository.messages.test {
            skipItems(1) // initial empty

            repository.sendMessage("Hello")
            // Consume PENDING and SENT emissions
            var sentMsg = awaitItem()
            if (sentMsg[0].ackState == AckState.PENDING) sentMsg = awaitItem()

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Resp", seq = 1))
            fakeEvents.emit(GatewayEvent.ChatFinal(runId = "run-1", text = "Full", stopReason = "end_turn"))
            // Consume updates until ackState reaches DONE
            var messages = awaitItem()
            while (messages.none { it.role == Role.USER && it.ackState == AckState.DONE }) {
                messages = awaitItem()
            }
            val userMsg = messages.first { it.role == Role.USER }
            assertEquals(AckState.DONE, userMsg.ackState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fetchPromptStats decodes the per-layer breakdown`() = runTest {
        val payload = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"total_chars":51200,"estimated_tokens":12800,"layers":[
                {"name":"identity","chars":2000,"estimated_tokens":500},
                {"name":"memories","chars":8000,"estimated_tokens":2000}]}""",
        )
        fakeClient.nextGenericResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        val stats = repository.fetchPromptStats()
        assertEquals(12800, stats!!.estimatedTokens)
        assertEquals(2, stats.layers.size)
        assertEquals("identity", stats.layers[0].name)
        assertEquals(500, stats.layers[0].estimatedTokens)
    }

    @Test
    fun `fetchPromptStats returns null on error response`() = runTest {
        fakeClient.nextGenericResponse = ResponseFrame(id = "1", ok = false)
        assertEquals(null, repository.fetchPromptStats())
    }
}

/**
 * Fake GatewayClient for testing ChatRepository in isolation.
 * Exposes controllable events and connection state flows.
 */
private class FakeGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
    override val events: SharedFlow<GatewayEvent> = fakeEvents

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    var nextGenericResponse: ResponseFrame? = null

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame = nextGenericResponse ?: ResponseFrame(id = "fake", ok = true)
}
