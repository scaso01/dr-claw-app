package com.scaso.drclawapp.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.scaso.drclawapp.data.ntfy.NtfyClient
import com.scaso.drclawapp.data.sync.MessageSyncWorker
import com.scaso.drclawapp.data.voice.PipecatClient
import com.scaso.drclawapp.data.websocket.CcBridgeClient
import com.scaso.drclawapp.data.websocket.GatewayClient

/**
 * Monitors network connectivity changes and triggers WebSocket reconnection
 * when the active network changes (e.g., WiFi ↔ 5G, tower handoff).
 *
 * Without this, network changes cause the WebSocket to hang silently until
 * the tick watchdog detects staleness (~90s). With this, reconnection happens
 * within ~500ms of the network change.
 */
class NetworkMonitor(
    private val context: Context,
    private val gatewayClient: GatewayClient,
    private val ccBridgeClient: CcBridgeClient,
    private val ntfyClient: NtfyClient,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var currentNetwork: Network? = null
    private var registered = false

    /**
     * [PipecatClient] is created per voice session (not a Hilt singleton -- see
     * VoiceViewModel), so it can't be a constructor dependency here. VoiceViewModel
     * registers/unregisters its instance across this slot while a voice session is alive;
     * null (no active session) is a normal no-op state, not a missing dependency.
     */
    var pipecatClient: PipecatClient? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = currentNetwork
            currentNetwork = network
            if (previous != null && previous != network) {
                // Network switched (e.g., WiFi → 5G, tower handoff)
                Log.i("NetworkMonitor", "Network changed: $previous → $network, triggering reconnect")
                gatewayClient.onNetworkChanged()
                ccBridgeClient.onNetworkChanged()
                ntfyClient.onNetworkChanged()
                pipecatClient?.onNetworkChanged()
                // Enqueue sync for any offline-queued messages
                MessageSyncWorker.enqueue(context)
            } else if (previous == null) {
                Log.i("NetworkMonitor", "Network available: $network")
                // Coming back online from no network — sync pending messages
                MessageSyncWorker.enqueue(context)
            }
        }

        override fun onLost(network: Network) {
            Log.i("NetworkMonitor", "Network lost: $network")
            if (currentNetwork == network) {
                currentNetwork = null
            }
            // Don't reconnect here — wait for onAvailable with a new network.
            // Reconnecting on onLost would just fail immediately.
        }
    }

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        currentNetwork = connectivityManager.activeNetwork
        registered = true
        Log.i("NetworkMonitor", "Started monitoring, current network: $currentNetwork")
    }

    fun stop() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        registered = false
        Log.i("NetworkMonitor", "Stopped monitoring")
    }
}
