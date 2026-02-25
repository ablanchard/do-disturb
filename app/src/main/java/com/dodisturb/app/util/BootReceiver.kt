package com.dodisturb.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dodisturb.app.worker.CalendarSyncWorker

/**
 * Receives BOOT_COMPLETED broadcast to re-enqueue the calendar sync worker
 * after the device restarts.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, re-enqueuing calendar sync worker")
            CalendarSyncWorker.enqueue(context)
        }
    }
}
