package com.dodisturb.app.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import timber.log.Timber

/**
 * Helper to check if a phone number exists in the device contacts.
 */
object ContactsHelper {

    /**
     * Checks if the given phone number matches any contact on the device.
     * Uses ContactsContract.PhoneLookup which handles number normalization
     * (country codes, formatting differences, etc.).
     *
     * @param context Application context
     * @param phoneNumber The phone number to look up (e.g., "+33612345678")
     * @return true if the number belongs to a known contact
     */
    fun isNumberInContacts(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

        var cursor: Cursor? = null
        return try {
            val lookupUri: Uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            cursor = context.contentResolver.query(lookupUri, projection, null, null, null)
            val found = cursor != null && cursor.count > 0
            if (found) {
                cursor?.moveToFirst()
                val name = cursor?.getString(0) ?: "Unknown"
                Timber.d("Number %s found in contacts: %s", phoneNumber, name)
            } else {
                Timber.d("Number %s NOT found in contacts", phoneNumber)
            }
            found
        } catch (e: Exception) {
            Timber.e(e, "Error looking up phone number: %s", phoneNumber)
            // On error, fail open (allow the call) rather than blocking a potentially valid contact
            true
        } finally {
            cursor?.close()
        }
    }
}
