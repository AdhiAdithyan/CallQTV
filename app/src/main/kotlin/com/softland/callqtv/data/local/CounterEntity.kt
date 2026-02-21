package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-counter configuration for a TV device, derived from the "counters" array
 * in the TV config API (Response5/Response6).
 */
@Entity(tableName = "counters")
data class CounterEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "device_id")
    val deviceId: Int,

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "counter_id")
    val counterId: String?,

    @ColumnInfo(name = "default_name")
    val defaultName: String?,

    @ColumnInfo(name = "default_code")
    val defaultCode: String?,

    @ColumnInfo(name = "source_device")
    val sourceDevice: String?,

    @ColumnInfo(name = "button_index")
    val buttonIndex: Int?,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "code")
    val code: String?,

    @ColumnInfo(name = "row_span")
    val rowSpan: Int?,

    @ColumnInfo(name = "col_span")
    val colSpan: Int?,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean?,

    @ColumnInfo(name = "audio_url")
    val audioUrl: String?,

    @ColumnInfo(name = "audio_name")
    val audioName: String?,

    // Raw JSON for forward compatibility / debugging
    @ColumnInfo(name = "raw_json")
    val rawJson: String?
)
