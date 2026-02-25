package com.dodisturb.app.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.dodisturb.app.data.repository.PreferencesManager

/**
 * Manages Do Not Disturb mode programmatically.
 * During allowed timeframes, DND is disabled so all notifications come through.
 * When the allowed timeframe ends, DND is restored to its previous state.
 */
class DndManager(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = PreferencesManager(context)

    companion object {
        private const val TAG = "DndManager"
    }

    /**
     * Checks if the app has permission to modify DND settings.
     */
    fun hasNotificationPolicyAccess(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    /**
     * Returns an intent to open the system settings for granting notification policy access.
     */
    fun getNotificationPolicyAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    /**
     * Gets the current DND interruption filter.
     */
    fun getCurrentInterruptionFilter(): Int {
        return notificationManager.currentInterruptionFilter
    }

    /**
     * Disables DND (sets interruption filter to ALL) for an allowed timeframe.
     * Saves the previous filter so it can be restored later.
     */
    fun disableDnd() {
        if (!hasNotificationPolicyAccess()) {
            Log.w(TAG, "No notification policy access, cannot disable DND")
            return
        }

        val currentFilter = notificationManager.currentInterruptionFilter

        // Only save and change if DND is currently active (not already ALL)
        if (currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            Log.d(TAG, "Disabling DND. Previous filter: $currentFilter")
            prefs.previousInterruptionFilter = currentFilter
            prefs.isDndManagedByApp = true
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        } else {
            Log.d(TAG, "DND already disabled (filter is ALL)")
            // Still mark as managed so we know we're in an active timeframe
            prefs.isDndManagedByApp = true
        }
    }

    /**
     * Restores DND to its previous state after an allowed timeframe ends.
     */
    fun restoreDnd() {
        if (!hasNotificationPolicyAccess()) {
            Log.w(TAG, "No notification policy access, cannot restore DND")
            return
        }

        if (!prefs.isDndManagedByApp) {
            Log.d(TAG, "DND was not managed by app, nothing to restore")
            return
        }

        val previousFilter = prefs.previousInterruptionFilter
        if (previousFilter != -1 && previousFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            Log.d(TAG, "Restoring DND to previous filter: $previousFilter")
            notificationManager.setInterruptionFilter(previousFilter)
        } else {
            Log.d(TAG, "No previous DND state to restore (was already ALL or not set)")
        }

        prefs.isDndManagedByApp = false
        prefs.previousInterruptionFilter = -1
    }

    /**
     * Updates DND state based on whether we're currently in an allowed timeframe.
     * Called by the sync worker after checking calendar events.
     *
     * @param isInAllowedTimeframe Whether the current time is within an allowed timeframe
     */
    fun updateDndState(isInAllowedTimeframe: Boolean) {
        if (isInAllowedTimeframe) {
            disableDnd()
        } else {
            restoreDnd()
        }
    }
}
