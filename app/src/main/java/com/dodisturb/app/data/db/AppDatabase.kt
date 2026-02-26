package com.dodisturb.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dodisturb.app.data.model.AllowedTimeframe
import com.dodisturb.app.data.model.BlockedCallInfo

@Database(
    entities = [AllowedTimeframe::class, BlockedCallInfo::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun timeframeDao(): TimeframeDao
    abstract fun blockedCallDao(): BlockedCallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dodisturb_db"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries() // Needed for CallScreeningService (runs on binder thread)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
