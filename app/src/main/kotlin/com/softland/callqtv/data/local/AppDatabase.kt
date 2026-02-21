package com.softland.callqtv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TvConfigEntity::class, MappedBrokerEntity::class, CounterEntity::class, AdFileEntity::class, TokenHistoryEntity::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tvConfigDao(): TvConfigDao
    abstract fun mappedBrokerDao(): MappedBrokerDao
    abstract fun counterDao(): CounterDao
    abstract fun adFileDao(): AdFileDao
    abstract fun tokenHistoryDao(): TokenHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "callqtv.db"
                )
                    .fallbackToDestructiveMigration() // For development; use proper migration in production
                    .build().also { INSTANCE = it }
            }
        }
    }
}
