package com.dodisturb.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dodisturb.app.ui.MainActivity

/**
 * Handles notification channel creation and posting notifications.
 */
object NotificationHelper {

    const val CHANNEL_BLOCKED_CALLS = "blocked_calls"
    private const val CHANNEL_BLOCKED_CALLS_NAME = "Blocked Calls"
    private const val CHANNEL_BLOCKED_CALLS_DESC = "Notifications when incoming calls are blocked"

    const val CHANNEL_SYNC_ERRORS = "sync_errors"
    private const val CHANNEL_SYNC_ERRORS_NAME = "Sync Errors"
    private const val CHANNEL_SYNC_ERRORS_DESC = "Notifications when calendar sync encounters problems"

    private const val NOTIFICATION_ID_CALENDAR_NOT_FOUND = 9001

    /**
     * Create all notification channels. Safe to call multiple times.
     * Must be called before posting any notifications (typically in Application.onCreate).
     */
    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BLOCKED_CALLS,
                CHANNEL_BLOCKED_CALLS_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_BLOCKED_CALLS_DESC
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC_ERRORS,
                CHANNEL_SYNC_ERRORS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_SYNC_ERRORS_DESC
            }
        )
    }

    /**
     * Post a notification informing the user that a call was blocked.
     */
    fun notifyBlockedCall(context: Context, phoneNumber: String, notificationId: Int) {
        if (!canPostNotifications(context)) return

        val displayNumber = if (phoneNumber.isNotEmpty()) phoneNumber else "Unknown number"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "call_log")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCKED_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Call Blocked")
            .setContentText("Blocked call from $displayNumber")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Post a notification when the configured calendar is not found.
     */
    fun notifyCalendarNotFound(context: Context, calendarName: String, availableNames: List<String>) {
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "debug")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_CALENDAR_NOT_FOUND,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val suggestion = if (availableNames.isNotEmpty()) {
            "\nAvailable: ${availableNames.take(3).joinToString(", ")}" +
                    if (availableNames.size > 3) " (+${availableNames.size - 3} more)" else ""
        } else {
            "\nNo calendars found on this account."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC_ERRORS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Calendar Not Found")
            .setContentText("\"$calendarName\" was not found")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Calendar \"$calendarName\" was not found in your Google account.$suggestion\n\nTap to see all available calendars.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CALENDAR_NOT_FOUND, notification)
    }

    /**
     * Dismiss the calendar-not-found notification (e.g. after a successful sync).
     */
    fun dismissCalendarNotFound(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_CALENDAR_NOT_FOUND)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
