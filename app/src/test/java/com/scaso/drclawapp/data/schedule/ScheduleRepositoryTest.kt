package com.scaso.drclawapp.data.schedule

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleRepositoryTest {

    private lateinit var fakeClient: FakeScheduleGatewayClient
    private lateinit var repository: ScheduleRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeScheduleGatewayClient()
        repository = ScheduleRepository(fakeClient, scope)
    }

    @Test
    fun `listSchedules parses nested schedules array`() = runTest {
        fakeClient.responsesByMethod["schedule.list"] = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"schedules": [{"id": "s1", "name": "backup", "cron": "0 3 * * *"}]}"""),
        )

        repository.schedules.test {
            skipItems(1)
            repository.listSchedules()
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("s1", result[0].id)
            assertEquals("0 3 * * *", result[0].cron)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listSchedules sets error when schedules key missing`() = runTest {
        fakeClient.responsesByMethod["schedule.list"] = ResponseFrame(id = "1", ok = true, payload = json.parseToJsonElement("{}"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listSchedules()
            assertEquals("Missing 'schedules' key in response", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listSchedules sets error on ok=false`() = runTest {
        fakeClient.responsesByMethod["schedule.list"] = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Schedule list broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listSchedules()
            assertEquals("Schedule list broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createSchedule sends name cron and action params`() = runTest {
        fakeClient.responsesByMethod["schedule.create"] = ResponseFrame(id = "1", ok = true)
        fakeClient.responsesByMethod["schedule.list"] = ResponseFrame(id = "2", ok = true, payload = json.parseToJsonElement("""{"schedules": []}"""))

        val result = repository.createSchedule("nightly-backup", "0 3 * * *", "backup.create")

        assertTrue(result)
        val call = fakeClient.calls.first { it.first == "schedule.create" }
        val params = call.second!!.jsonObject
        assertEquals("nightly-backup", params["name"]!!.jsonPrimitive.content)
        assertEquals("0 3 * * *", params["cron"]!!.jsonPrimitive.content)
        assertEquals("backup.create", params["action"]!!.jsonPrimitive.content)
    }

    @Test
    fun `createSchedule omits action key when null`() = runTest {
        fakeClient.responsesByMethod["schedule.create"] = ResponseFrame(id = "1", ok = true)
        fakeClient.responsesByMethod["schedule.list"] = ResponseFrame(id = "2", ok = true, payload = json.parseToJsonElement("""{"schedules": []}"""))

        repository.createSchedule("nightly-backup", "0 3 * * *", null)

        val call = fakeClient.calls.first { it.first == "schedule.create" }
        assertFalse(call.second!!.jsonObject.containsKey("action"))
    }

    @Test
    fun `createSchedule returns false on ok=false without reloading`() = runTest {
        fakeClient.responsesByMethod["schedule.create"] = ResponseFrame(id = "1", ok = false)

        val result = repository.createSchedule("x", "* * * * *", null)

        assertFalse(result)
        assertFalse(fakeClient.calls.any { it.first == "schedule.list" })
    }

    @Test
    fun `createSchedule returns false on exception`() = runTest {
        fakeClient.shouldThrow = true

        assertFalse(repository.createSchedule("x", "* * * * *", null))
    }

    @Test
    fun `toggleSchedule sends id and enabled params`() = runTest {
        fakeClient.responsesByMethod["schedule.toggle"] = ResponseFrame(id = "1", ok = true)

        val result = repository.toggleSchedule("s1", false)

        assertTrue(result)
        val call = fakeClient.calls.first { it.first == "schedule.toggle" }
        val params = call.second!!.jsonObject
        assertEquals("s1", params["id"]!!.jsonPrimitive.content)
        assertFalse(params["enabled"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `toggleSchedule returns false on exception`() = runTest {
        fakeClient.shouldThrow = true

        assertFalse(repository.toggleSchedule("s1", true))
    }

    @Test
    fun `deleteSchedule sends id param and reloads on success`() = runTest {
        fakeClient.responsesByMethod["schedule.delete"] = ResponseFrame(id = "1", ok = true)
        fakeClient.responsesByMethod["schedule.list"] = ResponseFrame(
            id = "2", ok = true,
            payload = json.parseToJsonElement("""{"schedules": [{"id": "s2", "name": "remaining"}]}"""),
        )

        repository.schedules.test {
            skipItems(1)
            val result = repository.deleteSchedule("s1")
            assertTrue(result)
            val reloaded = awaitItem()
            assertEquals(listOf("s2"), reloaded.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("s1", fakeClient.calls.first { it.first == "schedule.delete" }.second!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertTrue(fakeClient.calls.any { it.first == "schedule.list" })
    }

    @Test
    fun `deleteSchedule returns false on ok=false without reloading`() = runTest {
        fakeClient.responsesByMethod["schedule.delete"] = ResponseFrame(id = "1", ok = false)

        val result = repository.deleteSchedule("s1")

        assertFalse(result)
        assertFalse(fakeClient.calls.any { it.first == "schedule.list" })
    }
}

private class FakeScheduleGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val responsesByMethod = mutableMapOf<String, ResponseFrame>()
    val calls = mutableListOf<Pair<String, JsonElement?>>()
    var shouldThrow = false

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(replay = 64, extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        calls.add(method to params)
        if (shouldThrow) throw RuntimeException("Test schedule exception")
        return responsesByMethod[method] ?: ResponseFrame(id = "0", ok = true)
    }
}
