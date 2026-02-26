package com.dodisturb.app.worker

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dodisturb.app.data.db.AppDatabase
import com.dodisturb.app.data.model.AllowedTimeframe
import com.dodisturb.app.data.repository.CalendarInfo
import com.dodisturb.app.data.repository.PreferencesManager
import com.dodisturb.app.data.repository.TimeframeRepository
import com.dodisturb.app.util.AnalyticsHelper
import com.dodisturb.app.util.DndManager
import com.dodisturb.app.util.NotificationHelper
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that syncs events from the configured calendar
 * using Android's CalendarContract Content Provider and updates
 * the allowed timeframes in the local database.
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
            val workRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                15, TimeUnit.MINUTES
            ).build()

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

            val workRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
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
        Timber.i("Calendar sync started")
        AnalyticsHelper.logSyncStarted()

        val prefs = PreferencesManager(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)
        val repository = TimeframeRepository(db.timeframeDao())
        val dndManager = DndManager(applicationContext)

        try {
            // List ALL calendars from the device via CalendarContract
            val allCalendars = queryAllCalendars()
            Timber.i("Found %d calendars on device", allCalendars.size)

            // Persist the available calendars list for the debug screen
            prefs.setAvailableCalendars(allCalendars)

            // Find the calendar by name
            // Match against displayName (which is summaryOverride equivalent),
            // then fall back to summary (the account calendar name).
            val calendarName = prefs.calendarName
            Timber.i("Looking for target calendar among %d calendars", allCalendars.size)

            val calendarEntry = allCalendars.find { entry ->
                val displayName = entry.summaryOverride ?: entry.summary
                displayName.equals(calendarName, ignoreCase = true)
            }

            if (calendarEntry == null) {
                Timber.w("Target calendar NOT FOUND among %d calendars", allCalendars.size)
                AnalyticsHelper.logCalendarNotFound()

                // Notify user and persist error
                val availableNames = allCalendars.map { it.summaryOverride ?: it.summary }
                NotificationHelper.notifyCalendarNotFound(applicationContext, calendarName, availableNames)
                prefs.lastSyncError = "Calendar \"$calendarName\" not found"

                return Result.failure()
            }

            // Calendar found — dismiss any previous not-found notification
            NotificationHelper.dismissCalendarNotFound(applicationContext)

            val calendarId = calendarEntry.id
            Timber.i("Target calendar found")
            prefs.calendarId = calendarId

            // Fetch events for the next 30 days via CalendarContract
            val now = System.currentTimeMillis()
            val endTime = now + TimeUnit.DAYS.toMillis(30)

            Timber.i("Fetching events for next 30 days")

            val timeframes = queryEvents(calendarId.toLong(), now, endTime)
            Timber.i("Fetched %d events / timeframes", timeframes.size)

            // Update the database
            repository.replaceAllTimeframes(timeframes)
            repository.cleanupExpired()
            Timber.i("Database updated")

            // Update DND state
            val isInTimeframe = repository.isInAllowedTimeframe()
            dndManager.updateDndState(isInTimeframe)

            prefs.lastSyncTimestamp = System.currentTimeMillis()
            prefs.lastSyncError = null

            Timber.i("Calendar sync completed. In allowed timeframe: %s, timeframes: %d",
                isInTimeframe, timeframes.size)
            AnalyticsHelper.logSyncCompleted(timeframes.size, isInTimeframe)
            return Result.success()

        } catch (e: Exception) {
            Timber.e(e, "Calendar sync failed")
            AnalyticsHelper.logSyncFailed(e.javaClass.simpleName)
            return Result.retry()
        }
    }

    /**
     * Query all calendars on the device using CalendarContract.
     */
    private fun queryAllCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )

        applicationContext.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            CalendarContract.Calendars.ACCOUNT_NAME
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val displayNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val displayName = cursor.getString(displayNameIdx) ?: ""
                val accountName = cursor.getString(accountNameIdx) ?: ""

                calendars.add(
                    CalendarInfo(
                        summary = displayName,
                        summaryOverride = null,
                        id = id.toString(),
                        accessRole = accountName
                    )
                )
            }
        }

        return calendars
    }

    /**
     * Query events from a specific calendar within a time range using CalendarContract.
     * Uses the Instances table to automatically expand recurring events.
     */
    private fun queryEvents(calendarId: Long, startMillis: Long, endMillis: Long): List<AllowedTimeframe> {
        val timeframes = mutableListOf<AllowedTimeframe>()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END
        )

        val selection = "${CalendarContract.Instances.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        // Use Instances.query to get expanded recurring events
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        applicationContext.contentResolver.query(
            builder.build(),
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val eventIdIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)

            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(eventIdIdx)
                val title = cursor.getString(titleIdx) ?: "Untitled"
                val begin = cursor.getLong(beginIdx)
                val end = cursor.getLong(endIdx)

                if (begin > 0 && end > 0) {
                    timeframes.add(
                        AllowedTimeframe(
                            calendarEventId = eventId.toString(),
                            title = title,
                            startTime = begin,
                            endTime = end
                        )
                    )
                }
            }
        }

        return timeframes
    }
}
