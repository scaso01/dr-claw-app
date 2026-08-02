package com.scaso.drclawapp.data.brain

import app.cash.turbine.test
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.ErrorShape
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrainRepositoryTest {

    private lateinit var fakeClient: FakeBrainGatewayClient
    private lateinit var repository: BrainRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeBrainGatewayClient()
        repository = BrainRepository(fakeClient, scope)
    }

    @Test
    fun `recall sends query and detail params`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.encodeToJsonElement(
                RecallResult.serializer(),
                RecallResult(memories = listOf(BrainMemory(id = "m0", content = "seed"))),
            ),
        )

        repository.memories.test {
            skipItems(1)
            repository.recall("what did we decide", detail = "summary")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("brain.recall", fakeClient.lastMethod)
        val params = fakeClient.lastParams!!.jsonObject
        assertEquals("what did we decide", params["query"]!!.jsonPrimitive.content)
        assertEquals("summary", params["detail"]!!.jsonPrimitive.content)
    }

    @Test
    fun `recall populates memories on success`() = runTest {
        val result = RecallResult(memories = listOf(BrainMemory(id = "m1", content = "hello")), query = "q")
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(RecallResult.serializer(), result))

        repository.memories.test {
            skipItems(1)
            repository.recall("q")
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("hello", items[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recall sets error on ok=false response`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "ERR", message = "Recall broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.recall("q")
            assertEquals("Recall broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recall sets error on exception`() = runTest {
        fakeClient.shouldThrow = true

        repository.error.test {
            assertNull(awaitItem())
            repository.recall("q")
            assertEquals("Test brain exception", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `remember returns true on success`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)
        assertTrue(repository.remember("some fact"))
        assertEquals("brain.remember", fakeClient.lastMethod)
        assertEquals("some fact", fakeClient.lastParams!!.jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `remember returns false on exception`() = runTest {
        fakeClient.shouldThrow = true
        assertFalse(repository.remember("some fact"))
    }

    @Test
    fun `forget sends id param and returns ok`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)
        assertTrue(repository.forget("mem-1"))
        assertEquals("brain.forget", fakeClient.lastMethod)
        assertEquals("mem-1", fakeClient.lastParams!!.jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `forget returns false on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false)
        assertFalse(repository.forget("mem-1"))
    }

    @Test
    fun `loadGraph populates entities on success`() = runTest {
        val result = GraphResult(entities = listOf(GraphEntity(id = "e1", name = "Alex")))
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(GraphResult.serializer(), result))

        repository.entities.test {
            skipItems(1)
            repository.loadGraph()
            val items = awaitItem()
            assertEquals("Alex", items[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadGraph sets error on failure`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Graph broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.loadGraph()
            assertEquals("Graph broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadStatus is best-effort and swallows exceptions silently`() = runTest {
        fakeClient.shouldThrow = true

        repository.loadStatus()
        // Give the launched coroutine a chance to run; assert no crash and no state mutated.
        repository.error.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(repository.brainStatus.value)
    }

    @Test
    fun `loadStatus populates brainStatus on success`() = runTest {
        val status = BrainStatus(memory_count = 5)
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(BrainStatus.serializer(), status))

        repository.brainStatus.test {
            assertNull(awaitItem())
            repository.loadStatus()
            val result = awaitItem()
            assertEquals(5, result!!.memory_count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSummaries includes session_id param when provided`() = runTest {
        val seeded = RecallResult(memories = listOf(BrainMemory(id = "m0", content = "seed")))
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(RecallResult.serializer(), seeded))

        repository.summaries.test {
            skipItems(1)
            repository.loadSummaries(sessionId = "sess-1", limit = 5)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        val params = fakeClient.lastParams!!.jsonObject
        assertEquals("sess-1", params["session_id"]!!.jsonPrimitive.content)
        assertEquals(5, params["limit"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `loadSummaries omits session_id param when null`() = runTest {
        val seeded = RecallResult(memories = listOf(BrainMemory(id = "m0", content = "seed")))
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(RecallResult.serializer(), seeded))

        repository.summaries.test {
            skipItems(1)
            repository.loadSummaries(sessionId = null)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(fakeClient.lastParams!!.jsonObject.containsKey("session_id"))
    }

    @Test
    fun `loadContextVars populates variables on success`() = runTest {
        val result = ContextListResult(variables = listOf(ContextVariable(name = "ctx1")))
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(ContextListResult.serializer(), result))

        repository.contextVars.test {
            skipItems(1)
            repository.loadContextVars()
            val items = awaitItem()
            assertEquals("ctx1", items[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `contextSet returns false on exception`() = runTest {
        fakeClient.shouldThrow = true
        assertFalse(repository.contextSet("name", "content"))
    }

    @Test
    fun `contextPeek returns result on success`() = runTest {
        val peek = PeekResult(name = "ctx1", content = "hello world")
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(PeekResult.serializer(), peek))

        val result = repository.contextPeek("ctx1")
        assertEquals("hello world", result!!.content)
    }

    @Test
    fun `contextPeek returns null on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false)
        assertNull(repository.contextPeek("ctx1"))
    }

    @Test
    fun `contextPeek returns null on exception`() = runTest {
        fakeClient.shouldThrow = true
        assertNull(repository.contextPeek("ctx1"))
    }

    @Test
    fun `listPending populates pendingMemories on success`() = runTest {
        val result = PendingListResult(count = 1, memories = listOf(PendingMemory(id = 1L, content = "pending fact")))
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(PendingListResult.serializer(), result))

        repository.pendingMemories.test {
            skipItems(1)
            repository.listPending()
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("pending fact", items[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `approvePending removes item from pendingMemories on success`() = runTest {
        seedPending(id = 7L)
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)

        repository.pendingMemories.test {
            skipItems(1)
            repository.approvePending(7L)
            val items = awaitItem()
            assertTrue(items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("brain.pending.approve", fakeClient.lastMethod)
    }

    @Test
    fun `approvePending sets error and keeps item on failure`() = runTest {
        seedPending(id = 7L)
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Approve failed"))

        repository.error.test {
            assertNull(awaitItem())
            repository.approvePending(7L)
            assertEquals("Approve failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.pendingMemories.value.size)
    }

    @Test
    fun `rejectPending removes item from pendingMemories on success`() = runTest {
        seedPending(id = 9L)
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)

        repository.pendingMemories.test {
            skipItems(1)
            repository.rejectPending(9L)
            val items = awaitItem()
            assertTrue(items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("brain.pending.reject", fakeClient.lastMethod)
    }

    /** Seeds pendingMemories via a successful listPending() call so approve/reject have something to remove. */
    private suspend fun seedPending(id: Long) {
        val result = PendingListResult(count = 1, memories = listOf(PendingMemory(id = id, content = "seed")))
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.encodeToJsonElement(PendingListResult.serializer(), result))
        repository.pendingMemories.test {
            skipItems(1)
            repository.listPending()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Snapshots ────────────────────────────────────────────────────────────

    @Test
    fun `loadSnapshots populates list`() = runTest {
        val result = SnapshotListResult(
            snapshots = listOf(BrainSnapshot(id = 3, label = "nightly", memoryCount = 6800)),
        )
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.encodeToJsonElement(SnapshotListResult.serializer(), result),
        )

        repository.snapshots.test {
            skipItems(1)
            repository.loadSnapshots()
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("nightly", items[0].label)
            assertEquals(6800, items[0].memoryCount)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("brain.snapshot.list", fakeClient.lastMethod)
    }

    @Test
    fun `createSnapshot sends label`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)
        assertTrue(repository.createSnapshot("pre-experiment"))
        assertEquals("brain.snapshot.create", fakeClient.lastMethod)
        assertEquals(
            "pre-experiment",
            fakeClient.lastParams!!.jsonObject["label"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `restoreSnapshot sends id and returns report`() = runTest {
        val report = SnapshotRestoreResult(
            restored = true, snapshotId = 7,
            memoriesWritten = 6500, memoriesSoftDeleted = 120,
        )
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.encodeToJsonElement(SnapshotRestoreResult.serializer(), report),
        )

        val result = repository.restoreSnapshot(7)
        assertEquals("brain.snapshot.restore", fakeClient.lastMethod)
        assertEquals(7L, fakeClient.lastParams!!.jsonObject["id"]!!.jsonPrimitive.content.toLong())
        assertTrue(result!!.restored)
        assertEquals(6500, result.memoriesWritten)
        assertEquals(120, result.memoriesSoftDeleted)
    }

    @Test
    fun `deleteSnapshot returns false and surfaces error on failure`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = false,
            error = ErrorShape(code = "BRAIN_ERROR", message = "nope"),
        )
        assertFalse(repository.deleteSnapshot(3))
        assertEquals("nope", repository.error.value)
    }
}

private class FakeBrainGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var nextResponse = ResponseFrame(id = "0", ok = true)
    var shouldThrow = false
    var lastMethod: String? = null
    var lastParams: JsonElement? = null

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(replay = 64, extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        lastMethod = method
        lastParams = params
        if (shouldThrow) throw RuntimeException("Test brain exception")
        return nextResponse
    }
}
