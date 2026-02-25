package com.dodisturb.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dodisturb.app.data.db.AppDatabase
import com.dodisturb.app.data.model.AllowedTimeframe
import com.dodisturb.app.data.repository.CalendarInfo
import com.dodisturb.app.data.repository.PreferencesManager
import com.dodisturb.app.data.repository.TimeframeRepository
import com.dodisturb.app.util.DndManager
import com.dodisturb.app.util.NotificationHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that syncs events from the configured Google Calendar
 * and updates the allowed timeframes in the local database.
 * Also manages DND state based on whether we're in an allowed timeframe.
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CalendarSyncWorker"
        private const val WORK_NAME = "calendar_sync"
        private const val WORK_NAME_ONCE = "calendar_sync_once"

        /**
         * Enqueues the periodic sync worker (every 15 minutes).
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )

            Log.d(TAG, "Calendar sync worker enqueued (every 15 minutes)")
        }

        /**
         * Triggers an immediate one-time sync (for manual refresh).
         */
        fun syncNow(context: Context) {
            Log.d(TAG, ">>> Manual sync requested, enqueuing one-time work")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueue(workRequest)

            Log.d(TAG, ">>> One-time sync work enqueued")
        }

        /**
         * Cancels the periodic sync worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Calendar sync worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "========== CALENDAR SYNC STARTED ==========")

        val prefs = PreferencesManager(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)
        val repository = TimeframeRepository(db.timeframeDao())
        val dndManager = DndManager(applicationContext)

        try {
            // Check if we have a signed-in Google account
            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            if (account == null) {
                Log.w(TAG, "No Google account signed in, skipping sync")
                return Result.retry()
            }
            Log.i(TAG, "Google account: ${account.email}")
            Log.i(TAG, "Account object: ${account.account}")

            // Build the Google Calendar service
            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext,
                listOf(CalendarScopes.CALENDAR_READONLY)
            )
            credential.selectedAccount = account.account
            Log.i(TAG, "Credential created, selectedAccount: ${credential.selectedAccount}")

            val calendarService = Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("DoDisturb")
                .build()

            Log.i(TAG, "Calendar service built, fetching calendar list...")

            // List ALL calendars for debugging
            val calendarList = calendarService.calendarList().list().execute()
            val allCalendars = calendarList.items ?: emptyList()

            Log.i(TAG, "========== ALL CALENDARS (${allCalendars.size}) ==========")
            allCalendars.forEachIndexed { index, entry ->
                Log.i(TAG, "  [$index] summary=\"${entry.summary}\", " +
                        "summaryOverride=\"${entry.summaryOverride}\", " +
                        "id=\"${entry.id}\", accessRole=\"${entry.accessRole}\"")
            }
            Log.i(TAG, "==============================================")

            // Persist the available calendars list for the debug screen
            val calendarInfoList = allCalendars.map { entry ->
                CalendarInfo(
                    summary = entry.summary ?: "",
                    summaryOverride = entry.summaryOverride,
                    id = entry.id ?: "",
                    accessRole = entry.accessRole ?: ""
                )
            }
            prefs.setAvailableCalendars(calendarInfoList)

            // Find the calendar by name
            // Match against summaryOverride first (user-defined display name in Google Calendar),
            // then fall back to summary (the original calendar name/URL for imported calendars).
            val calendarName = prefs.calendarName
            Log.i(TAG, "Looking for calendar named: \"$calendarName\"")

            val calendarEntry = allCalendars.find { entry ->
                (entry.summaryOverride ?: entry.summary).equals(calendarName, ignoreCase = true)
            }

            if (calendarEntry == null) {
                Log.w(TAG, "Calendar '$calendarName' NOT FOUND among ${allCalendars.size} calendars")
                Log.w(TAG, "Available calendars (displayName -> summary):")
                allCalendars.forEach { entry ->
                    val displayName = entry.summaryOverride ?: entry.summary
                    Log.w(TAG, "  \"$displayName\" (summary=\"${entry.summary}\")")
                }

                // Notify user and persist error
                val availableNames = allCalendars.map { it.summaryOverride ?: it.summary ?: "" }
                NotificationHelper.notifyCalendarNotFound(applicationContext, calendarName, availableNames)
                prefs.lastSyncError = "Calendar \"$calendarName\" not found"

                return Result.failure()
            }

            // Calendar found — dismiss any previous not-found notification
            NotificationHelper.dismissCalendarNotFound(applicationContext)

            val calendarId = calendarEntry.id
            val displayName = calendarEntry.summaryOverride ?: calendarEntry.summary
            Log.i(TAG, "Found calendar: \"$displayName\" -> id=\"$calendarId\"")
            prefs.calendarId = calendarId

            // Fetch events for the next 30 days
            val now = System.currentTimeMillis()
            val endTime = now + TimeUnit.DAYS.toMillis(30)

            Log.i(TAG, "Fetching events from ${DateTime(now)} to ${DateTime(endTime)}")

            val events = calendarService.events().list(calendarId)
                .setTimeMin(DateTime(now))
                .setTimeMax(DateTime(endTime))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute()

            val rawItems = events.items ?: emptyList()
            Log.i(TAG, "========== EVENTS (${rawItems.size}) ==========")
            rawItems.forEachIndexed { index, event ->
                Log.i(TAG, "  [$index] summary=\"${event.summary}\", " +
                        "start=${event.start?.dateTime ?: event.start?.date}, " +
                        "end=${event.end?.dateTime ?: event.end?.date}, " +
                        "id=\"${event.id}\"")
            }
            Log.i(TAG, "===============================================")

            // Convert to AllowedTimeframe entities
            val timeframes = rawItems.mapNotNull { event ->
                val start = event.start?.dateTime?.value
                    ?: event.start?.date?.value
                    ?: run {
                        Log.w(TAG, "  Skipping event \"${event.summary}\": no start time")
                        return@mapNotNull null
                    }
                val end = event.end?.dateTime?.value
                    ?: event.end?.date?.value
                    ?: run {
                        Log.w(TAG, "  Skipping event \"${event.summary}\": no end time")
                        return@mapNotNull null
                    }

                AllowedTimeframe(
                    calendarEventId = event.id ?: "",
                    title = event.summary ?: "Untitled",
                    startTime = start,
                    endTime = end
                )
            }

            Log.i(TAG, "Converted ${timeframes.size} events to timeframes:")
            timeframes.forEach { tf ->
                Log.i(TAG, "  - \"${tf.title}\" from ${DateTime(tf.startTime)} to ${DateTime(tf.endTime)}")
            }

            // Update the database
            repository.replaceAllTimeframes(timeframes)
            repository.cleanupExpired()
            Log.i(TAG, "Database updated")

            // Update DND state
            val isInTimeframe = repository.isInAllowedTimeframe()
            dndManager.updateDndState(isInTimeframe)

            prefs.lastSyncTimestamp = System.currentTimeMillis()
            prefs.lastSyncError = null

            Log.i(TAG, "========== CALENDAR SYNC COMPLETED ==========")
            Log.i(TAG, "In allowed timeframe: $isInTimeframe")
            Log.i(TAG, "Timeframes in DB: ${timeframes.size}")
            Log.i(TAG, "===============================================")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "========== CALENDAR SYNC FAILED ==========")
            Log.e(TAG, "Error: ${e.message}", e)
            Log.e(TAG, "===========================================")
            return Result.retry()
        }
    }
}
