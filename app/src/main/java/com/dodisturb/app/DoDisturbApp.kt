package com.dodisturb.app

import android.app.Application
import com.dodisturb.app.data.db.AppDatabase
import com.dodisturb.app.util.NotificationHelper

/**
 * Application class for Do Disturb.
 * Initializes the database and notification channel early so they're ready
 * for the CallScreeningService.
 */
class DoDisturbApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pre-initialize the database
        AppDatabase.getInstance(this)
        // Create notification channels for blocked call alerts and sync errors
        NotificationHelper.createNotificationChannels(this)
    }
}
