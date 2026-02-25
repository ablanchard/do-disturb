package com.dodisturb.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dodisturb.app.data.model.AllowedTimeframe
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeframeDao {

    @Query("SELECT * FROM allowed_timeframes WHERE endTime > :now ORDER BY startTime ASC")
    fun getUpcomingTimeframes(now: Long): Flow<List<AllowedTimeframe>>

    @Query("SELECT * FROM allowed_timeframes WHERE startTime <= :now AND endTime > :now LIMIT 1")
    suspend fun getActiveTimeframe(now: Long): AllowedTimeframe?

    @Query("SELECT * FROM allowed_timeframes WHERE startTime <= :now AND endTime > :now LIMIT 1")
    fun getActiveTimeframeSync(now: Long): AllowedTimeframe?

    @Query("SELECT * FROM allowed_timeframes WHERE startTime > :now ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextTimeframe(now: Long): AllowedTimeframe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timeframes: List<AllowedTimeframe>)

    @Query("DELETE FROM allowed_timeframes WHERE endTime < :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM allowed_timeframes")
    suspend fun deleteAll()
}
