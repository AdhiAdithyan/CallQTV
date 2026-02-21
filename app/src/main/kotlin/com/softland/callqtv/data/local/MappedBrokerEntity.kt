package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached mapped broker config for a device.
 *
 * Stored separately from tv_config for easier querying/usage.
 * One row per brokerId.
 */
@Entity(tableName = "mapped_broker")
data class MappedBrokerEntity(
    @PrimaryKey
    @ColumnInfo(name = "broker_id")
    val brokerId: Int,

    @ColumnInfo(name = "device_id")
    val deviceId: Int,

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "serial_number")
    val serialNumber: String?,

    @ColumnInfo(name = "device_type")
    val deviceType: String?,

    // Broker config (flattened for easy usage)
    @ColumnInfo(name = "ssid")
    val ssid: String?,

    @ColumnInfo(name = "password")
    val password: String?,

    @ColumnInfo(name = "host")
    val host: String?,

    @ColumnInfo(name = "port")
    val port: String?,

    @ColumnInfo(name = "topic")
    val topic: String?,

    // Keep raw JSON too (optional) for forward compatibility/debugging
    @ColumnInfo(name = "mapped_broker_json")
    val mappedBrokerJson: String?
)
