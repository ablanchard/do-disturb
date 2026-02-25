package com.dodisturb.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an allowed timeframe fetched from Google Calendar.
 * During these timeframes, all incoming calls are allowed and DND is disabled.
 */
@Entity(tableName = "allowed_timeframes")
data class AllowedTimeframe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val calendarEventId: String,
    val title: String,
    val startTime: Long,  // epoch milliseconds
    val endTime: Long     // epoch milliseconds
)
