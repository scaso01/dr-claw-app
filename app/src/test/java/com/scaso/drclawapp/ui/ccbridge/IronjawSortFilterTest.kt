package com.scaso.drclawapp.ui.ccbridge

import com.scaso.drclawapp.data.ccbridge.ClaudeSession
import org.junit.Assert.assertEquals
import org.junit.Test

class IronjawSortFilterTest {

    private fun session(
        id: String,
        status: String = "idle",
        title: String? = null,
        project: String? = null,
        cwd: String = ".",
        lastActivity: String = "",
    ) = ClaudeSession(
        ccBridgeId = id,
        status = status,
        title = title,
        project = project,
        cwd = cwd,
        lastActivity = lastActivity,
    )

    @Test
    fun `filter ACTIVE keeps running and other live statuses`() {
        val list = listOf(
            session("a", status = "running"), // active subprocess status from handle_cc_sessions
            session("b", status = "idle"),
            session("c", status = "streaming"),
            session("d", status = "waiting_permission"),
        )
        val out = applyIronjawSortFilter(list, IronjawSort.RECENT, IronjawStatusFilter.ACTIVE)
        assertEquals(listOf("a", "c", "d"), out.map { it.ccBridgeId })
    }

    @Test
    fun `filter IDLE drops running statuses`() {
        val list = listOf(session("a", status = "running"), session("b", status = "idle"))
        val out = applyIronjawSortFilter(list, IronjawSort.RECENT, IronjawStatusFilter.IDLE)
        assertEquals(listOf("b"), out.map { it.ccBridgeId })
    }

    @Test
    fun `filter ALL keeps everything`() {
        val list = listOf(session("a", status = "running"), session("b", status = "idle"))
        val out = applyIronjawSortFilter(list, IronjawSort.RECENT, IronjawStatusFilter.ALL)
        assertEquals(listOf("a", "b"), out.map { it.ccBridgeId })
    }

    @Test
    fun `sort RECENT orders by lastActivity descending (ISO lexicographic)`() {
        val list = listOf(
            session("old", lastActivity = "2026-01-01T00:00:00Z"),
            session("new", lastActivity = "2026-06-24T10:00:00Z"),
        )
        val out = applyIronjawSortFilter(list, IronjawSort.RECENT, IronjawStatusFilter.ALL)
        assertEquals(listOf("new", "old"), out.map { it.ccBridgeId })
    }

    @Test
    fun `sort TITLE orders alphabetically, falling back to project when title is blank`() {
        val list = listOf(
            session("z", title = "Zebra task"),
            session("a", title = "Apple task"),
            session("m", project = "mango"), // no title -> uses project "mango"
        )
        val out = applyIronjawSortFilter(list, IronjawSort.TITLE, IronjawStatusFilter.ALL)
        assertEquals(listOf("a", "m", "z"), out.map { it.ccBridgeId })
    }

    @Test
    fun `sort PROJECT orders alphabetically using cwd fallback`() {
        val list = listOf(
            session("z", cwd = "/home/deploy/Projects/zebra"),
            session("a", project = "apple"),
        )
        val out = applyIronjawSortFilter(list, IronjawSort.PROJECT, IronjawStatusFilter.ALL)
        assertEquals(listOf("a", "z"), out.map { it.ccBridgeId })
    }

    @Test
    fun `filter and sort compose`() {
        val list = listOf(
            session("active-z", status = "running", title = "Zebra"),
            session("idle-a", status = "idle", title = "Apple"),
            session("active-a", status = "running", title = "Avocado"),
        )
        val out = applyIronjawSortFilter(list, IronjawSort.TITLE, IronjawStatusFilter.ACTIVE)
        assertEquals(listOf("active-a", "active-z"), out.map { it.ccBridgeId })
    }
}
