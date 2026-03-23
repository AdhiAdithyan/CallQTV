package com.softland.callqtv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TvConfigEntity::class,
        MappedBrokerEntity::class,
        CounterEntity::class,
        AdFileEntity::class,
        TokenHistoryEntity::class,
        ConnectedDeviceEntity::class,
        TokenRecordEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tvConfigDao(): TvConfigDao
    abstract fun mappedBrokerDao(): MappedBrokerDao
    abstract fun counterDao(): CounterDao
    abstract fun adFileDao(): AdFileDao
    abstract fun tokenHistoryDao(): TokenHistoryDao
    abstract fun connectedDeviceDao(): ConnectedDeviceDao
    abstract fun tokenRecordDao(): TokenRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE token_history ADD COLUMN call_date TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tv_config ADD COLUMN enable_counter_prifix INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "callqtv.db"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_13_14)
                    .fallbackToDestructiveMigration() // For development; use proper migration in production
                    .build().also { INSTANCE = it }
            }
        }
    }
}
