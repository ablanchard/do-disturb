package com.dodisturb.app

import android.app.Application
import android.util.Log
import com.dodisturb.app.data.db.AppDatabase
import com.dodisturb.app.util.NotificationHelper
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import timber.log.Timber

/**
 * Application class for Do Disturb.
 * Initializes the database, notification channels, and logging/crash reporting.
 */
class DoDisturbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pre-initialize the database
        AppDatabase.getInstance(this)
        // Create notification channels for blocked call alerts and sync errors
        NotificationHelper.createNotificationChannels(this)
        // Initialize logging
        initLogging()
    }

    private fun initLogging() {
        // Always plant the Crashlytics tree so logs are captured as breadcrumbs
        Timber.plant(CrashlyticsTree())

        // In debug builds, also log to logcat
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * A Timber tree that forwards logs to Firebase Crashlytics as breadcrumbs.
     * Errors and exceptions are also recorded as non-fatal events.
     */
    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Send all log messages as Crashlytics breadcrumbs
            Firebase.crashlytics.log("${tag ?: "---"}: $message")

            // Record non-fatal exceptions for warnings and errors
            if (t != null && priority >= Log.WARN) {
                Firebase.crashlytics.recordException(t)
            }
        }
    }
}
