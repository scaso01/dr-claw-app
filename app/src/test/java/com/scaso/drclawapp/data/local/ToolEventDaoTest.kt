package com.scaso.drclawapp.data.local

import com.scaso.drclawapp.data.local.dao.ToolEventDao
import com.scaso.drclawapp.data.local.entity.ToolEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ToolEventDao] contract using a pure-JVM fake implementation.
 * No Robolectric / room-testing artifact required — the fake mirrors the SQL semantics
 * (REPLACE on conflict, ordered by started_at).
 *
 * Phase 17 — added with DB migration 4 → 5.
 */
class ToolEventDaoTest {

    private lateinit var dao: ToolEventDao

    @Before
    fun setUp() {
        dao = FakeToolEventDao()
    }

    // -------------------------------------------------------------------------
    // Test 1: upsert and round-trip read
    // -------------------------------------------------------------------------

    @Test
    fun `toolEventDao_upsert_and_get_round_trip`() = runTest {
        val entity = ToolEventEntity(
            messageId = "msg-1",
            toolCallId = "call-abc",
            toolName = "bash",
            inputJson = """{"command":"ls"}""",
            outputJson = null,
            error = null,
            status = "Running",
            startedAt = 1_000L,
            durationMs = null,
        )

        dao.upsert(entity)

        val results = dao.getByMessageId("msg-1")
        assertEquals(1, results.size)
        assertEquals("call-abc", results[0].toolCallId)
        assertEquals("bash", results[0].toolName)
        assertEquals("""{"command":"ls"}""", results[0].inputJson)
        assertEquals("Running", results[0].status)
        assertNull(results[0].outputJson)
    }

    @Test
    fun `toolEventDao_upsert_replaces_existing_row_with_same_tool_call_id`() = runTest {
        val initial = ToolEventEntity(
            messageId = "msg-1",
            toolCallId = "call-abc",
            toolName = "bash",
            inputJson = "{}",
            outputJson = null,
            error = null,
            status = "Running",
            startedAt = 1_000L,
            durationMs = null,
        )
        dao.upsert(initial)

        val updated = initial.copy(status = "Done", outputJson = """{"out":"ok"}""", durationMs = 250L)
        dao.upsert(updated)

        val results = dao.getByMessageId("msg-1")
        // REPLACE semantics: still exactly 1 row
        assertEquals(1, results.size)
        assertEquals("Done", results[0].status)
        assertEquals("""{"out":"ok"}""", results[0].outputJson)
        assertEquals(250L, results[0].durationMs)
    }

    // -------------------------------------------------------------------------
    // Test 2: updateResult modifies row
    // -------------------------------------------------------------------------

    @Test
    fun `toolEventDao_update_result_modifies_row`() = runTest {
        val entity = ToolEventEntity(
            messageId = "msg-2",
            toolCallId = "call-xyz",
            toolName = "read_file",
            inputJson = """{"path":"/tmp/foo.txt"}""",
            outputJson = null,
            error = null,
            status = "Running",
            startedAt = 2_000L,
            durationMs = null,
        )
        dao.upsert(entity)

        dao.updateResult(
            toolCallId = "call-xyz",
            status = "Done",
            output = """{"content":"hello"}""",
            error = null,
            duration = 125L,
        )

        val result = dao.getByToolCallId("call-xyz")
        assertNotNull(result)
        assertEquals("Done", result!!.status)
        assertEquals("""{"content":"hello"}""", result.outputJson)
        assertNull(result.error)
        assertEquals(125L, result.durationMs)
    }

    @Test
    fun `toolEventDao_update_result_sets_error_status`() = runTest {
        dao.upsert(
            ToolEventEntity(
                messageId = "msg-3",
                toolCallId = "call-err",
                toolName = "exec",
                inputJson = "{}",
                outputJson = null,
                error = null,
                status = "Running",
                startedAt = 3_000L,
                durationMs = null,
            )
        )

        dao.updateResult(
            toolCallId = "call-err",
            status = "Error",
            output = null,
            error = "Permission denied",
            duration = 50L,
        )

        val result = dao.getByToolCallId("call-err")
        assertNotNull(result)
        assertEquals("Error", result!!.status)
        assertEquals("Permission denied", result.error)
        assertNull(result.outputJson)
    }

    @Test
    fun `toolEventDao_deleteByMessageId_removes_all_rows_for_message`() = runTest {
        dao.upsert(makeEntity("msg-del", "call-1", 100L))
        dao.upsert(makeEntity("msg-del", "call-2", 200L))
        dao.upsert(makeEntity("msg-other", "call-3", 300L))

        dao.deleteByMessageId("msg-del")

        assertEquals(0, dao.getByMessageId("msg-del").size)
        assertEquals(1, dao.getByMessageId("msg-other").size)
    }

    @Test
    fun `toolEventDao_getByMessageId_returns_events_ordered_by_started_at`() = runTest {
        dao.upsert(makeEntity("msg-ord", "call-b", 200L))
        dao.upsert(makeEntity("msg-ord", "call-a", 100L))
        dao.upsert(makeEntity("msg-ord", "call-c", 300L))

        val results = dao.getByMessageId("msg-ord")
        assertEquals(listOf("call-a", "call-b", "call-c"), results.map { it.toolCallId })
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeEntity(messageId: String, toolCallId: String, startedAt: Long) =
        ToolEventEntity(
            messageId = messageId,
            toolCallId = toolCallId,
            toolName = "test_tool",
            inputJson = "{}",
            outputJson = null,
            error = null,
            status = "Running",
            startedAt = startedAt,
            durationMs = null,
        )
}

// -----------------------------------------------------------------------------
// Fake DAO — pure JVM, no Room runtime
// -----------------------------------------------------------------------------

/**
 * In-memory implementation of [ToolEventDao] that mirrors the SQL contract:
 * - REPLACE strategy on upsert (unique tool_call_id)
 * - getByMessageId ordered by started_at ASC
 * - updateResult patches only status/output/error/durationMs
 */
private class FakeToolEventDao : ToolEventDao {

    private val rows = mutableListOf<ToolEventEntity>()
    private var nextId = 1L

    override suspend fun upsert(event: ToolEventEntity): Long {
        val existing = rows.indexOfFirst { it.toolCallId == event.toolCallId }
        return if (existing >= 0) {
            val assigned = rows[existing].id
            rows[existing] = event.copy(id = assigned)
            assigned
        } else {
            val assigned = nextId++
            rows.add(event.copy(id = assigned))
            assigned
        }
    }

    override suspend fun getByMessageId(messageId: String): List<ToolEventEntity> =
        rows.filter { it.messageId == messageId }.sortedBy { it.startedAt }

    override suspend fun getByToolCallId(toolCallId: String): ToolEventEntity? =
        rows.firstOrNull { it.toolCallId == toolCallId }

    override suspend fun updateResult(
        toolCallId: String,
        status: String,
        output: String?,
        error: String?,
        duration: Long?,
    ) {
        val idx = rows.indexOfFirst { it.toolCallId == toolCallId }
        if (idx >= 0) {
            rows[idx] = rows[idx].copy(
                status = status,
                outputJson = output,
                error = error,
                durationMs = duration,
            )
        }
    }

    override suspend fun deleteByMessageId(messageId: String) {
        rows.removeAll { it.messageId == messageId }
    }
}
