package com.scaso.drclawapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import com.scaso.drclawapp.R
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service that manages the floating chat bubble overlay using Android's
 * Bubble API (Android 11+, API 30+) -- Phase 8D.
 *
 * When the app goes to the background, this service can show a floating
 * bubble that the user can tap to expand a mini chat view via
 * [BubbleActivity].
 *
 * The Bubble API is preferred over SYSTEM_ALERT_WINDOW because:
 * - It requires no special runtime permission on Android 11+.
 * - It integrates with the system notification shade.
 * - Bubbles are dismissible and respect DND/focus modes.
 *
 * This service listens for incoming chat final events and creates
 * a bubble notification for each completed response.
 */
@AndroidEntryPoint
class BubbleService : android.app.Service() {

    @Inject lateinit var gatewayClient: GatewayClient
    @Inject lateinit var chatRepository: ChatRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val BUBBLE_CHANNEL_ID = "dr_claw_bubble"
        const val BUBBLE_NOTIFICATION_ID = 100
        private const val BUBBLE_SHORTCUT_ID = "dr_claw_bubble_shortcut"

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createBubbleChannel()
        observeChatEvents()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    /**
     * Creates the notification channel for bubble notifications.
     * Must allow bubbles on the channel level.
     */
    private fun createBubbleChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BUBBLE_CHANNEL_ID,
                "Chat Bubbles",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Floating chat bubble for quick replies"
                setAllowBubbles(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Observes gateway events and shows a bubble when a chat response completes.
     */
    private fun observeChatEvents() {
        serviceScope.launch {
            gatewayClient.events.collect { event ->
                if (event is GatewayEvent.ChatFinal && !event.text.isNullOrBlank()) {
                    showBubble(event.text)
                }
            }
        }
    }

    /**
     * Shows a bubble notification with the latest message.
     *
     * On Android 11+ this creates a proper Bubble via BubbleMetadata.
     * On older versions it falls back to a standard notification.
     */
    private fun showBubble(messageText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open the bubble's expanded view
        val bubbleIntent = Intent(this, BubbleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        val bubblePendingIntent = PendingIntent.getActivity(
            this,
            0,
            bubbleIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val person = Person.Builder()
            .setName("Dr. CLAW")
            .setImportant(true)
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .build()

        val style = NotificationCompat.MessagingStyle(person)
            .addMessage(
                messageText.take(300),
                System.currentTimeMillis(),
                person,
            )

        val builder = NotificationCompat.Builder(this, BUBBLE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setShortcutId(BUBBLE_SHORTCUT_ID)
            .setLocusId(LocusIdCompat(BUBBLE_SHORTCUT_ID))

        // Add bubble metadata on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bubbleMetadata = NotificationCompat.BubbleMetadata.Builder(
                bubblePendingIntent,
                IconCompat.createWithResource(this, R.mipmap.ic_launcher),
            )
                .setDesiredHeight(600)
                .setAutoExpandBubble(false)
                .setSuppressNotification(false)
                .build()

            builder.setBubbleMetadata(bubbleMetadata)
        }

        nm.notify(BUBBLE_NOTIFICATION_ID, builder.build())
    }
}
