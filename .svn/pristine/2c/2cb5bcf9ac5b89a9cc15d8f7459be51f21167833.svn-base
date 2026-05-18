package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores individual ad file items from tv_config.ad_files for each device.
 * This makes it easy to query and manage ads per device/counter.
 */
@Entity(tableName = "ad_files")
data class AdFileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "device_id")
    val deviceId: Int,

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    /** Index/position of this file in the original ad_files array (0‑based). */
    @ColumnInfo(name = "position")
    val position: Int,

    /** The ad file path / URL as provided by the backend. */
    @ColumnInfo(name = "file_path")
    val filePath: String,

    /** Raw JSON element for forward compatibility (optional). */
    @ColumnInfo(name = "raw_json")
    val rawJson: String?
)
