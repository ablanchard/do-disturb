package com.dodisturb.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a blocked call entry stored in the local database.
 */
@Entity(tableName = "blocked_calls")
data class BlockedCallInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val timestamp: Long,       // epoch milliseconds
    val reason: String         // "not_in_contacts", "blocking_enabled", etc.
)
