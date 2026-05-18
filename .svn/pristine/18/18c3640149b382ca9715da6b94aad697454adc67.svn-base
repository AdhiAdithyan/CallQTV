package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists the last N tokens received for each counter key.
 * counter_key  – the MQTT counter identifier (e.g. "1", "Ortho", "__default__")
 * token        – the token value (e.g. "101")
 * position     – 0 = most recent, 1 = second most recent, …
 * updated_at   – epoch millis, used for ordering / pruning
 * call_date    – date when the token was called (yyyy-MM-dd); used to clear previous days' data
 */
@Entity(
    tableName = "token_history",
    indices = [Index(value = ["counter_key", "position"], unique = true)]
)
data class TokenHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "counter_key")
    val counterKey: String,

    @ColumnInfo(name = "token")
    val token: String,

    @ColumnInfo(name = "position")
    val position: Int,          // 0 = latest

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "call_date")
    val callDate: String = ""   // yyyy-MM-dd; tokens with date != today are cleared on load
)
