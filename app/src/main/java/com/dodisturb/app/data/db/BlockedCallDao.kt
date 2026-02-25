package com.dodisturb.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dodisturb.app.data.model.BlockedCallInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllBlockedCalls(): Flow<List<BlockedCallInfo>>

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBlockedCalls(limit: Int): Flow<List<BlockedCallInfo>>

    @Insert
    fun insertSync(blockedCall: BlockedCallInfo): Long

    @Query("DELETE FROM blocked_calls WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM blocked_calls")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM blocked_calls")
    fun getCount(): Flow<Int>
}
