package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connected_devices")
data class ConnectedDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Int = 0,

    @ColumnInfo(name = "device_id")
    val deviceId: Int, // The TV's device ID

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "remote_device_id")
    val remoteDeviceId: Int?,

    @ColumnInfo(name = "serial_number")
    val serialNumber: String?,

    @ColumnInfo(name = "device_type")
    val deviceType: String?,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "status")
    val status: String?,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean?,

    @ColumnInfo(name = "config_json")
    val configJson: String?
)
