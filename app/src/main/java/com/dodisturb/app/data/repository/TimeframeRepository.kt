package com.dodisturb.app.data.repository

import com.dodisturb.app.data.db.TimeframeDao
import com.dodisturb.app.data.model.AllowedTimeframe
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing allowed timeframes from Google Calendar.
 */
class TimeframeRepository(private val dao: TimeframeDao) {

    /**
     * Returns a Flow of upcoming timeframes (for UI observation).
     */
    fun getUpcomingTimeframes(): Flow<List<AllowedTimeframe>> {
        return dao.getUpcomingTimeframes(System.currentTimeMillis())
    }

    /**
     * Checks if the current time falls within an allowed timeframe.
     * This is a suspend function for use in coroutines.
     */
    suspend fun isInAllowedTimeframe(): Boolean {
        return dao.getActiveTimeframe(System.currentTimeMillis()) != null
    }

    /**
     * Synchronous version for use in CallScreeningService (runs on binder thread).
     */
    fun isInAllowedTimeframeSync(): Boolean {
        return dao.getActiveTimeframeSync(System.currentTimeMillis()) != null
    }

    /**
     * Gets the currently active timeframe, if any.
     */
    suspend fun getActiveTimeframe(): AllowedTimeframe? {
        return dao.getActiveTimeframe(System.currentTimeMillis())
    }

    /**
     * Gets the next upcoming timeframe.
     */
    suspend fun getNextTimeframe(): AllowedTimeframe? {
        return dao.getNextTimeframe(System.currentTimeMillis())
    }

    /**
     * Replaces all cached timeframes with fresh data from Google Calendar.
     */
    suspend fun replaceAllTimeframes(timeframes: List<AllowedTimeframe>) {
        dao.deleteAll()
        dao.insertAll(timeframes)
    }

    /**
     * Removes expired timeframes to keep the DB clean.
     */
    suspend fun cleanupExpired() {
        dao.deleteExpired(System.currentTimeMillis())
    }
}
