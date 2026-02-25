package com.dodisturb.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dodisturb.app.worker.CalendarSyncWorker
import timber.log.Timber

/**
 * Receives BOOT_COMPLETED broadcast to re-enqueue the calendar sync worker
 * after the device restarts.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed, re-enqueuing calendar sync worker")
            CalendarSyncWorker.enqueue(context)
        }
    }
}
