package com.scaso.drclawapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.scaso.drclawapp.MainActivity
import com.scaso.drclawapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates and manages notification channels and notifications for Dr. CLAW.
 *
 * Two channels:
 * - **Service channel** (low priority, silent): persistent foreground service notification.
 * - **Chat channel** (high priority, sound): incoming message notifications when app is backgrounded.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    companion object {
        const val CHANNEL_ID = "dr_claw_chat"
        const val SERVICE_CHANNEL_ID = "dr_claw_service"
        const val NTFY_CHANNEL_ID = "dr_claw_ntfy"
        const val NOTIFICATION_ID = 1
        const val SERVICE_NOTIFICATION_ID = 2
        const val NTFY_NOTIFICATION_ID = 3
        const val CMD_APPROVAL_NOTIFICATION_ID = 4

        /** Intent extra key for deep-linking to a specific session. */
        const val EXTRA_SESSION_KEY = "session_key"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Creates both notification channels. Safe to call multiple times --
     * the system ignores re-creation of existing channels.
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Connection Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent notification showing gateway connection status"
                setShowBadge(false)
            }

            val chatChannel = NotificationChannel(
                CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications for incoming chat messages"
                enableVibration(true)
            }

            val ntfyChannel = NotificationChannel(
                NTFY_CHANNEL_ID,
                "Push Notifications (ntfy)",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Push notifications from Dr. CLAW via ntfy"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, chatChannel, ntfyChannel)
            )
        }
    }

    /**
     * Builds the persistent foreground service notification.
     *
     * @param connectionText Status text, e.g. "Connected", "Reconnecting...", "Disconnected".
     * @return A [Notification] suitable for [android.app.Service.startForeground].
     */
    fun buildServiceNotification(connectionText: String): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("Dr. CLAW")
            .setContentText(connectionText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Updates the persistent foreground service notification in-place.
     * Call this when the connection state changes.
     *
     * @param notification A notification built via [buildServiceNotification].
     */
    fun updateServiceNotification(notification: Notification) {
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
    }

    /**
     * Shows a high-priority notification for an incoming chat message.
     * Intended to fire when a `chat.final` event arrives while the app is in the background.
     *
     * @param title Notification title (e.g. "Dr. CLAW").
     * @param body The message body text.
     */
    fun showMessageNotification(title: String, body: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Shows a push notification received via ntfy.
     * Supports deep-linking to a specific session via [sessionKey].
     *
     * @param title Notification title (e.g. "Dr. CLAW").
     * @param body The notification body text.
     * @param sessionKey Optional session key for deep-link navigation.
     * @param priority ntfy priority (1-5). Mapped to Android notification priority.
     */
    fun showNtfyNotification(
        title: String,
        body: String,
        sessionKey: String? = null,
        priority: Int = 3,
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (sessionKey != null) {
                putExtra(EXTRA_SESSION_KEY, sessionKey)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NTFY_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val androidPriority = when (priority) {
            1 -> NotificationCompat.PRIORITY_MIN
            2 -> NotificationCompat.PRIORITY_LOW
            4 -> NotificationCompat.PRIORITY_HIGH
            5 -> NotificationCompat.PRIORITY_MAX
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, NTFY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(androidPriority)
            .build()

        // Use a unique ID per notification so multiple ntfy messages don't overwrite each other
        val uniqueId = NTFY_NOTIFICATION_ID + (System.currentTimeMillis() % 10_000).toInt()
        notificationManager.notify(uniqueId, notification)
    }

    /**
     * Shows a high-priority notification when the gateway requests a device command
     * (clipboard, location, calendar, etc.) while the app is backgrounded. Tapping it
     * opens the app, where the pending [com.scaso.drclawapp.ui.approval.ApprovalDialog]
     * (global, shown in [com.scaso.drclawapp.ui.navigation.NavGraph]) is waiting for a decision.
     *
     * @param tool The requested command name (e.g. "clipboard.get").
     */
    fun showCmdApprovalNotification(tool: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            CMD_APPROVAL_NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Approval needed")
            .setContentText("Gateway wants to run \"$tool\" on this device")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(CMD_APPROVAL_NOTIFICATION_ID, notification)
    }
}
