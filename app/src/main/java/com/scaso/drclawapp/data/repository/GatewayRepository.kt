package com.scaso.drclawapp.data.repository

import com.scaso.drclawapp.data.websocket.GatewayClient

/**
 * Thin wrapper over gateway connection-lifecycle calls (reconnect to a new URL, force a
 * fresh connection). No other repository owns transport reconnection, so this exists
 * purely so Settings-level ViewModels don't need a direct [GatewayClient] reference.
 *
 * No Android imports -- KMP-extractable.
 */
class GatewayRepository(
    private val gatewayClient: GatewayClient,
) {
    fun reconnectWith(url: String) = gatewayClient.reconnectWith(url)

    fun reconnect() = gatewayClient.reconnect()
}
