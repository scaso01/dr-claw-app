package com.scaso.drclawapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.scaso.drclawapp.DrClawApp
import com.scaso.drclawapp.data.ntfy.NtfyClient
import com.scaso.drclawapp.data.ntfy.extractSessionKey
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the Gateway WebSocket alive when the app
 * is in the background.
 *
 * Key design points:
 * - [GatewayClient] and [ChatRepository] are Hilt singletons. The service
 *   simply holds references to the same instances the UI uses -- no duplicate
 *   connections.
 * - The persistent notification updates its text to reflect the current
 *   [ConnectionState].
 * - When a `chat.final` event arrives and the app is backgrounded, a
 *   high-priority chat notification is shown via [NotificationHelper].
 */
@AndroidEntryPoint
class ChatService : Service() {

    @Inject lateinit var gatewayClient: GatewayClient
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var ntfyClient: NtfyClient
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var networkMonitor: NetworkMonitor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // FIX #9: partial WakeLock + WifiLock held only while a chat turn is active, so a
    // long local-model turn (thinking + tool loop) doesn't get its socket dropped when
    // the device enters Doze. Acquired on the first turn-activity event, released on
    // the terminal event, with an inactivity safety-release so a missed terminal can't
    // leak the locks.
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lockReleaseJob: Job? = null

    companion object {
        private const val ACTION_STOP = "com.scaso.drclawapp.STOP_SERVICE"

        /** Inactivity window after which turn locks auto-release (safety net for a
         *  missed terminal event). Each activity event resets it; comfortably longer
         *  than the gateway's per-turn wall-clock deadline. */
        private const val TURN_LOCK_TIMEOUT_MS = 8L * 60L * 1000L

        /**
         * Convenience method to start the service.
         */
        fun start(context: Context) {
            val intent = Intent(context, ChatService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Convenience method to stop the service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ChatService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()

        val initialNotification = notificationHelper.buildServiceNotification("Connecting...")
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, initialNotification)

        networkMonitor.start()
        observeConnectionState()
        observeChatEvents()
        observeTurnLocks()
        observeCcCompleteEvents()
        observeNtfyPreferences()
        observeNtfyEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // If the system kills us, restart with a null intent so we reconnect.
        return START_STICKY
    }

    override fun onDestroy() {
        networkMonitor.stop()
        releaseTurnLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Private helpers ---

    /**
     * Collects [ConnectionState] changes and updates the persistent notification.
     */
    private fun observeConnectionState() {
        serviceScope.launch {
            gatewayClient.connectionState.collect { state ->
                val text = when (state) {
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Authenticating -> "Authenticating..."
                    is ConnectionState.Connected -> "Connected"
                    is ConnectionState.Error -> state.message
                    is ConnectionState.AuthFailed -> "Authentication failed"
                }
                val notification = notificationHelper.buildServiceNotification(text)
                notificationHelper.updateServiceNotification(notification)
            }
        }
    }

    /**
     * Collects gateway events. When a [GatewayEvent.ChatFinal] arrives,
     * shows a chat notification (the main agent will wire up the
     * foreground/background check separately if desired).
     */
    private fun observeChatEvents() {
        serviceScope.launch {
            gatewayClient.events.collect { event ->
                if (event is GatewayEvent.ChatFinal) {
                    val preview = event.text
                        ?.take(200)
                        ?.ifEmpty { null }
                    if (preview != null) {
                        notificationHelper.showMessageNotification(
                            title = "Dr. CLAW",
                            body = preview,
                        )
                    }
                }
            }
        }
    }

    /**
     * FIX #9: keep the CPU + WiFi radio awake while a chat turn is streaming or
     * running tools, so Doze can't drop the gateway socket mid-turn. Turn-activity
     * events (delta / catchup / tool start+result) acquire; terminal events (final /
     * aborted / error) release. The pre-first-token window is intentionally not
     * covered here — it is short and bounded, and the softened tick-watchdog keeps the
     * socket from being killed during it.
     */
    private fun observeTurnLocks() {
        serviceScope.launch {
            gatewayClient.events.collect { event ->
                when (event) {
                    is GatewayEvent.ChatDelta,
                    is GatewayEvent.ChatCatchup,
                    is GatewayEvent.NativeToolStart,
                    is GatewayEvent.NativeToolResult -> acquireTurnLocks()

                    is GatewayEvent.ChatFinal,
                    is GatewayEvent.ChatAborted,
                    is GatewayEvent.ChatError -> releaseTurnLocks()

                    else -> Unit
                }
            }
        }
    }

    /** Acquire (or refresh) the turn locks and re-arm the inactivity safety-release. */
    private fun acquireTurnLocks() {
        if (wakeLock == null) {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DrClaw:chatTurn")
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "DrClaw:chatTurn")
        }
        // acquire(timeout) auto-releases the WakeLock if a terminal event is ever
        // missed; re-acquiring on each activity refreshes that window.
        wakeLock?.acquire(TURN_LOCK_TIMEOUT_MS)
        if (wifiLock?.isHeld != true) wifiLock?.acquire()

        lockReleaseJob?.cancel()
        lockReleaseJob = serviceScope.launch {
            delay(TURN_LOCK_TIMEOUT_MS)
            releaseTurnLocks()
        }
    }

    /** Release the turn locks and cancel the safety-release timer. Idempotent. */
    private fun releaseTurnLocks() {
        lockReleaseJob?.cancel()
        lockReleaseJob = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }

    /**
     * Collects cc.complete events from the gateway. When the app is backgrounded,
     * shows a push notification so the user knows their CC session finished.
     */
    private fun observeCcCompleteEvents() {
        serviceScope.launch {
            gatewayClient.events.collect { event ->
                if (event is GatewayEvent.CcComplete && !DrClawApp.isAppInForeground) {
                    val sessionSnippet = event.sessionId.take(8)
                    notificationHelper.showNtfyNotification(
                        title = "CC Session Complete",
                        body = "Session $sessionSnippet finished processing",
                        sessionKey = event.sessionId,
                        priority = 4,
                    )
                }
            }
        }
    }

    /**
     * Watches ntfy preferences (enabled, topic, URL) and starts/stops the
     * SSE subscription accordingly.
     */
    private fun observeNtfyPreferences() {
        serviceScope.launch {
            combine(
                appPreferences.ntfyEnabled,
                appPreferences.ntfyTopic,
                appPreferences.ntfyUrl,
            ) { enabled, topic, url -> Triple(enabled, topic, url) }
                .distinctUntilChanged()
                .collect { (enabled, topic, url) ->
                    if (enabled && topic.isNotBlank()) {
                        ntfyClient.subscribe(url, topic)
                    } else {
                        ntfyClient.unsubscribe()
                    }
                }
        }
    }

    /**
     * Collects ntfy notification events and posts them as Android notifications.
     */
    private fun observeNtfyEvents() {
        serviceScope.launch {
            ntfyClient.events.collect { notification ->
                notificationHelper.showNtfyNotification(
                    title = notification.title ?: "Dr. CLAW",
                    body = notification.message,
                    sessionKey = notification.extractSessionKey(),
                    priority = notification.priority,
                )
            }
        }
    }
}
