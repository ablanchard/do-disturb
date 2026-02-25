package com.dodisturb.app.util

import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Centralized helper for logging Firebase Analytics events.
 * All event names and parameter keys are defined here to avoid typos
 * and keep analytics consistent.
 *
 * IMPORTANT: No PII (phone numbers, emails, contact names, calendar IDs)
 * should ever be passed as event parameters.
 */
object AnalyticsHelper {

    private val analytics by lazy { Firebase.analytics }

    // --- Call screening events ---

    fun logCallAllowed(reason: String) {
        analytics.logEvent("call_allowed", Bundle().apply {
            putString("reason", reason)
        })
    }

    fun logCallBlocked() {
        analytics.logEvent("call_blocked", null)
    }

    // --- Calendar sync events ---

    fun logSyncStarted() {
        analytics.logEvent("sync_started", null)
    }

    fun logSyncCompleted(timeframeCount: Int, isInTimeframe: Boolean) {
        analytics.logEvent("sync_completed", Bundle().apply {
            putInt("timeframe_count", timeframeCount)
            putBoolean("in_timeframe", isInTimeframe)
        })
    }

    fun logSyncFailed(errorType: String) {
        analytics.logEvent("sync_failed", Bundle().apply {
            putString("error_type", errorType)
        })
    }

    fun logCalendarNotFound() {
        analytics.logEvent("calendar_not_found", null)
    }

    // --- DND events ---

    fun logDndDisabled() {
        analytics.logEvent("dnd_disabled", null)
    }

    fun logDndRestored() {
        analytics.logEvent("dnd_restored", null)
    }

    // --- User actions ---

    fun logBlockingToggled(enabled: Boolean) {
        analytics.logEvent("blocking_toggled", Bundle().apply {
            putBoolean("enabled", enabled)
        })
    }

    fun logManualSyncTriggered() {
        analytics.logEvent("manual_sync_triggered", null)
    }

    fun logSignInCompleted() {
        analytics.logEvent("sign_in_completed", null)
    }

    fun logSignInFailed(statusCode: Int) {
        analytics.logEvent("sign_in_failed", Bundle().apply {
            putInt("status_code", statusCode)
        })
    }
}
