package com.dodisturb.app.service

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService
import com.dodisturb.app.data.db.AppDatabase
import com.dodisturb.app.data.model.BlockedCallInfo
import com.dodisturb.app.data.repository.PreferencesManager
import com.dodisturb.app.data.repository.TimeframeRepository
import com.dodisturb.app.util.ContactsHelper
import com.dodisturb.app.util.NotificationHelper
import timber.log.Timber

/**
 * Call screening service that blocks incoming calls from numbers
 * not in the user's contact list, unless we're in an allowed timeframe
 * (as defined by events in the configured Google Calendar).
 *
 * When a call is blocked, the event is persisted to the local database
 * and a notification is posted.
 */
class DoDisturbCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val handle: Uri? = callDetails.handle
        val phoneNumber = handle?.schemeSpecificPart ?: ""

        Timber.d("Screening call from: %s", phoneNumber)

        val prefs = PreferencesManager(this)

        // If blocking is disabled in settings, allow all calls
        if (!prefs.isBlockingEnabled) {
            Timber.d("Blocking is disabled, allowing call")
            allowCall(callDetails)
            return
        }

        // Check if we're in an allowed timeframe
        val db = AppDatabase.getInstance(this)
        val repository = TimeframeRepository(db.timeframeDao())
        if (repository.isInAllowedTimeframeSync()) {
            Timber.d("In allowed timeframe, allowing call from %s", phoneNumber)
            allowCall(callDetails)
            return
        }

        // Check if the number is in contacts
        if (phoneNumber.isNotEmpty() && ContactsHelper.isNumberInContacts(this, phoneNumber)) {
            Timber.d("Number %s is in contacts, allowing call", phoneNumber)
            allowCall(callDetails)
            return
        }

        // Number is not in contacts and we're not in an allowed timeframe -> block
        Timber.d("Blocking call from %s (not in contacts, not in allowed timeframe)", phoneNumber)

        // Persist the blocked call to the database
        val blockedCall = BlockedCallInfo(
            phoneNumber = phoneNumber,
            timestamp = System.currentTimeMillis(),
            reason = "not_in_contacts"
        )
        try {
            val rowId = db.blockedCallDao().insertSync(blockedCall)
            Timber.d("Saved blocked call to DB, id=%d", rowId)

            // Send a notification (use rowId as unique notification id)
            NotificationHelper.notifyBlockedCall(
                context = this,
                phoneNumber = phoneNumber,
                notificationId = rowId.toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save blocked call or send notification")
        }

        blockCall(callDetails)
    }

    private fun allowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }

    private fun blockCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(true)
            .setSkipCallLog(false)      // Still show in system call log
            .setSkipNotification(true)  // Don't show system missed-call notification
            .build()
        respondToCall(callDetails, response)
    }
}
