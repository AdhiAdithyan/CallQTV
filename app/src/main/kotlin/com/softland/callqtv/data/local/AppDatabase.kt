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
        TokenRecordEntity::class,
        MqttPayloadLogEntity::class
    ],
    version = 17,
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
    abstract fun mqttPayloadLogDao(): MqttPayloadLogDao

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

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tv_config ADD COLUMN keypads_json TEXT")
                db.execSQL("ALTER TABLE tv_config ADD COLUMN scroll_text_color TEXT")

                db.execSQL("ALTER TABLE counters ADD COLUMN keypad_index TEXT")
                db.execSQL("ALTER TABLE counters ADD COLUMN dispenser_index INTEGER")
                db.execSQL("ALTER TABLE counters ADD COLUMN dispenser_button_number INTEGER")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mqtt_payload_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `MessagePayload` TEXT NOT NULL,
                        `ReaceivedTime` TEXT NOT NULL,
                        `DisplayedTime` TEXT NOT NULL,
                        `IsUploaded` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "callqtv.db"
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_13_14, MIGRATION_15_16, MIGRATION_16_17)
                    .fallbackToDestructiveMigration(dropAllTables = true) // For development; use proper migration in production
                    .build().also { INSTANCE = it }
            }
        }
    }
}
