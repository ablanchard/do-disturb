package com.dodisturb.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages app preferences using SharedPreferences.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The name of the Google Calendar to watch for allowed timeframes.
     */
    var calendarName: String
        get() = prefs.getString(KEY_CALENDAR_NAME, DEFAULT_CALENDAR_NAME) ?: DEFAULT_CALENDAR_NAME
        set(value) = prefs.edit { putString(KEY_CALENDAR_NAME, value) }

    /**
     * The Google Calendar ID (resolved from the calendar name during sync).
     */
    var calendarId: String?
        get() = prefs.getString(KEY_CALENDAR_ID, null)
        set(value) = prefs.edit { putString(KEY_CALENDAR_ID, value) }

    /**
     * The Google account email used for Calendar API.
     */
    var googleAccountEmail: String?
        get() = prefs.getString(KEY_GOOGLE_ACCOUNT, null)
        set(value) = prefs.edit { putString(KEY_GOOGLE_ACCOUNT, value) }

    /**
     * Whether the app is actively blocking calls.
     */
    var isBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCKING_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_BLOCKING_ENABLED, value) }

    /**
     * Timestamp of the last successful calendar sync.
     */
    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        set(value) = prefs.edit { putLong(KEY_LAST_SYNC, value) }

    /**
     * The previous DND interruption filter value before we modified it,
     * so we can restore it when leaving an allowed timeframe.
     */
    var previousInterruptionFilter: Int
        get() = prefs.getInt(KEY_PREV_INTERRUPTION_FILTER, -1)
        set(value) = prefs.edit { putInt(KEY_PREV_INTERRUPTION_FILTER, value) }

    /**
     * Whether we are currently managing DND (i.e., we disabled it for a timeframe).
     */
    var isDndManagedByApp: Boolean
        get() = prefs.getBoolean(KEY_DND_MANAGED, false)
        set(value) = prefs.edit { putBoolean(KEY_DND_MANAGED, value) }

    /**
     * Last sync error message, or null if last sync was successful.
     */
    var lastSyncError: String?
        get() = prefs.getString(KEY_LAST_SYNC_ERROR, null)
        set(value) = prefs.edit { putString(KEY_LAST_SYNC_ERROR, value) }

    /**
     * Store the list of available calendars from the last sync as JSON.
     * Each entry has: summary, summaryOverride, id, accessRole.
     */
    fun setAvailableCalendars(calendars: List<CalendarInfo>) {
        val jsonArray = JSONArray()
        calendars.forEach { cal ->
            val obj = JSONObject().apply {
                put("summary", cal.summary)
                put("summaryOverride", cal.summaryOverride ?: JSONObject.NULL)
                put("id", cal.id)
                put("accessRole", cal.accessRole)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString(KEY_AVAILABLE_CALENDARS, jsonArray.toString()) }
    }

    /**
     * Get the list of available calendars from the last sync.
     */
    fun getAvailableCalendars(): List<CalendarInfo> {
        val json = prefs.getString(KEY_AVAILABLE_CALENDARS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                CalendarInfo(
                    summary = obj.getString("summary"),
                    summaryOverride = if (obj.isNull("summaryOverride")) null else obj.getString("summaryOverride"),
                    id = obj.getString("id"),
                    accessRole = obj.getString("accessRole")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "dodisturb_prefs"
        private const val KEY_CALENDAR_NAME = "calendar_name"
        private const val KEY_CALENDAR_ID = "calendar_id"
        private const val KEY_GOOGLE_ACCOUNT = "google_account"
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_PREV_INTERRUPTION_FILTER = "prev_interruption_filter"
        private const val KEY_DND_MANAGED = "dnd_managed"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        private const val KEY_AVAILABLE_CALENDARS = "available_calendars"
        const val DEFAULT_CALENDAR_NAME = "DoDisturb"
    }
}

/**
 * Simple data class for calendar info persisted via SharedPreferences.
 */
data class CalendarInfo(
    val summary: String,
    val summaryOverride: String?,
    val id: String,
    val accessRole: String
)
