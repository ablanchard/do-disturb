package com.dodisturb.app.worker

import android.content.Context
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
import timber.log.Timber
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

            Timber.d("Calendar sync worker enqueued (every 15 minutes)")
        }

        /**
         * Triggers an immediate one-time sync (for manual refresh).
         */
        fun syncNow(context: Context) {
            Timber.d("Manual sync requested, enqueuing one-time work")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueue(workRequest)

            Timber.d("One-time sync work enqueued")
        }

        /**
         * Cancels the periodic sync worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Calendar sync worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Timber.i("========== CALENDAR SYNC STARTED ==========")

        val prefs = PreferencesManager(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)
        val repository = TimeframeRepository(db.timeframeDao())
        val dndManager = DndManager(applicationContext)

        try {
            // Check if we have a signed-in Google account
            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            if (account == null) {
                Timber.w("No Google account signed in, skipping sync")
                return Result.retry()
            }
            Timber.i("Google account: %s", account.email)
            Timber.i("Account object: %s", account.account)

            // Build the Google Calendar service
            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext,
                listOf(CalendarScopes.CALENDAR_READONLY)
            )
            credential.selectedAccount = account.account
            Timber.i("Credential created, selectedAccount: %s", credential.selectedAccount)

            val calendarService = Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("DoDisturb")
                .build()

            Timber.i("Calendar service built, fetching calendar list...")

            // List ALL calendars for debugging
            val calendarList = calendarService.calendarList().list().execute()
            val allCalendars = calendarList.items ?: emptyList()

            Timber.i("========== ALL CALENDARS (%d) ==========", allCalendars.size)
            allCalendars.forEachIndexed { index, entry ->
                Timber.i("  [%d] summary=\"%s\", summaryOverride=\"%s\", id=\"%s\", accessRole=\"%s\"",
                    index, entry.summary, entry.summaryOverride, entry.id, entry.accessRole)
            }
            Timber.i("==============================================")

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
            Timber.i("Looking for calendar named: \"%s\"", calendarName)

            val calendarEntry = allCalendars.find { entry ->
                (entry.summaryOverride ?: entry.summary).equals(calendarName, ignoreCase = true)
            }

            if (calendarEntry == null) {
                Timber.w("Calendar '%s' NOT FOUND among %d calendars", calendarName, allCalendars.size)
                Timber.w("Available calendars (displayName -> summary):")
                allCalendars.forEach { entry ->
                    val displayName = entry.summaryOverride ?: entry.summary
                    Timber.w("  \"%s\" (summary=\"%s\")", displayName, entry.summary)
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
            Timber.i("Found calendar: \"%s\" -> id=\"%s\"", displayName, calendarId)
            prefs.calendarId = calendarId

            // Fetch events for the next 30 days
            val now = System.currentTimeMillis()
            val endTime = now + TimeUnit.DAYS.toMillis(30)

            Timber.i("Fetching events from %s to %s", DateTime(now), DateTime(endTime))

            val events = calendarService.events().list(calendarId)
                .setTimeMin(DateTime(now))
                .setTimeMax(DateTime(endTime))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute()

            val rawItems = events.items ?: emptyList()
            Timber.i("========== EVENTS (%d) ==========", rawItems.size)
            rawItems.forEachIndexed { index, event ->
                Timber.i("  [%d] summary=\"%s\", start=%s, end=%s, id=\"%s\"",
                    index, event.summary,
                    event.start?.dateTime ?: event.start?.date,
                    event.end?.dateTime ?: event.end?.date,
                    event.id)
            }
            Timber.i("===============================================")

            // Convert to AllowedTimeframe entities
            val timeframes = rawItems.mapNotNull { event ->
                val start = event.start?.dateTime?.value
                    ?: event.start?.date?.value
                    ?: run {
                        Timber.w("Skipping event \"%s\": no start time", event.summary)
                        return@mapNotNull null
                    }
                val end = event.end?.dateTime?.value
                    ?: event.end?.date?.value
                    ?: run {
                        Timber.w("Skipping event \"%s\": no end time", event.summary)
                        return@mapNotNull null
                    }

                AllowedTimeframe(
                    calendarEventId = event.id ?: "",
                    title = event.summary ?: "Untitled",
                    startTime = start,
                    endTime = end
                )
            }

            Timber.i("Converted %d events to timeframes:", timeframes.size)
            timeframes.forEach { tf ->
                Timber.i("  - \"%s\" from %s to %s", tf.title, DateTime(tf.startTime), DateTime(tf.endTime))
            }

            // Update the database
            repository.replaceAllTimeframes(timeframes)
            repository.cleanupExpired()
            Timber.i("Database updated")

            // Update DND state
            val isInTimeframe = repository.isInAllowedTimeframe()
            dndManager.updateDndState(isInTimeframe)

            prefs.lastSyncTimestamp = System.currentTimeMillis()
            prefs.lastSyncError = null

            Timber.i("========== CALENDAR SYNC COMPLETED ==========")
            Timber.i("In allowed timeframe: %s", isInTimeframe)
            Timber.i("Timeframes in DB: %d", timeframes.size)
            Timber.i("===============================================")
            return Result.success()

        } catch (e: Exception) {
            Timber.e(e, "========== CALENDAR SYNC FAILED ==========")
            return Result.retry()
        }
    }
}
