package com.scaso.drclawapp.data.ntfy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * SSE client for ntfy push notifications.
 *
 * Subscribes to `<baseUrl>/<topic>/sse` and emits [NtfyNotification] events.
 * Automatically reconnects on failure with exponential backoff (max 30s).
 *
 * No Android imports — KMP-extractable.
 */
class NtfyClient(
    private val scope: CoroutineScope,
) {
    /** Long-lived HTTP client for SSE (no read timeout). */
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<NtfyNotification>(extraBufferCapacity = 16)
    val events: SharedFlow<NtfyNotification> = _events

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var subscriptionJob: Job? = null
    private var currentBaseUrl: String? = null
    private var currentTopic: String? = null
    private var isSubscribed = false

    /**
     * Start subscribing to the given ntfy topic.
     * Cancels any existing subscription first.
     */
    fun subscribe(baseUrl: String, topic: String) {
        unsubscribe()
        if (topic.isBlank()) return
        currentBaseUrl = baseUrl
        currentTopic = topic
        isSubscribed = true

        val url = "${baseUrl.trimEnd('/')}/$topic/sse"

        subscriptionJob = scope.launch(Dispatchers.IO) {
            var backoffMs = 1_000L

            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "text/event-stream")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val source = response.body?.source()

                        if (source == null || !response.isSuccessful) {
                            delay(backoffMs)
                            backoffMs = (backoffMs * 2).coerceAtMost(30_000)
                            return@use
                        }

                        _connected.value = true
                        backoffMs = 1_000L // Reset on successful connect

                        while (isActive && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break

                            // ntfy SSE format: "data: {json}" for message events
                            if (!line.startsWith("data: ")) continue

                            val jsonStr = line.removePrefix("data: ")
                            val notification = try {
                                json.decodeFromString<NtfyNotification>(jsonStr)
                            } catch (_: Exception) {
                                null
                            }

                            // Only emit actual message events (skip keepalive, open events)
                            if (notification != null && notification.event == "message") {
                                _events.emit(notification)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Network error — will reconnect after backoff
                }

                _connected.value = false
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000)
            }
        }
    }

    /**
     * Stop the current subscription and close the connection.
     */
    fun unsubscribe() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        isSubscribed = false
        _connected.value = false
    }

    /**
     * Called when the network changes (e.g., WiFi ↔ 5G, tower handoff). Cancels the current
     * SSE connection and resubscribes immediately, bypassing the in-progress backoff delay.
     * No-op if not currently subscribed.
     */
    fun onNetworkChanged() {
        if (!isSubscribed) return
        val baseUrl = currentBaseUrl ?: return
        val topic = currentTopic ?: return
        subscribe(baseUrl, topic)
    }
}
