package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Detailed record of every token call.
 * This table is NOT deduplicated (stores every call event).
 * It is cleared daily (only today's records are kept).
 */
@Entity(tableName = "token_records")
data class TokenRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "counter_id")
    val counterId: Int,

    @ColumnInfo(name = "counter_name")
    val counterName: String,

    @ColumnInfo(name = "token_number")
    val tokenNumber: String,

    @ColumnInfo(name = "keypad_serial_number")
    val keypadSerialNumber: String,

    @ColumnInfo(name = "created_date")
    val createdDate: String, // yyyy-MM-dd (for cleanup)

    @ColumnInfo(name = "created_time")
    val createdTime: String, // HH:mm:ss

    @ColumnInfo(name = "called_time")
    val calledTime: String,  // Provided by keypad/MQTT

    @ColumnInfo(name = "is_uploaded")
    var isUploaded: Int = 0  // 0 = pending, 1 = uploaded
)
