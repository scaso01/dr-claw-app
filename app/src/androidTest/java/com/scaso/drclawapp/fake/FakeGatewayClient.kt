package com.scaso.drclawapp.fake

import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.HelloOk
import com.scaso.drclawapp.data.websocket.Policy
import com.scaso.drclawapp.data.websocket.ResponseFrame
import com.scaso.drclawapp.data.websocket.ServerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Test double for [GatewayClient] that starts in [ConnectionState.Connected].
 *
 * All network operations are no-ops returning safe defaults, so instrumented
 * tests can render screens that depend on gateway connectivity without needing
 * a real Ironjaw Gateway.
 */
class FakeGatewayClient(scope: CoroutineScope) : GatewayClient(
    url = "ws://fake",
    token = "fake-token",
    scope = scope,
    appVersion = "test",
) {
    private val fakeHello = HelloOk(
        protocol = 4,
        server = ServerInfo(name = "fake-ironjaw", version = "0.0.0-test"),
        policy = Policy(),
    )

    private val _fakeConnectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Connected(fakeHello)
    )
    override val connectionState: StateFlow<ConnectionState> = _fakeConnectionState.asStateFlow()

    private val _fakeEvents = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<GatewayEvent> = _fakeEvents.asSharedFlow()

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame {
        return ResponseFrame(
            id = "fake-id",
            ok = true,
            payload = JsonNull,
        )
    }

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        return ResponseFrame(
            id = "fake-id",
            ok = true,
            payload = JsonNull,
        )
    }
}
