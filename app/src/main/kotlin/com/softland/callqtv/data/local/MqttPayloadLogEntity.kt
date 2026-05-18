package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mqtt_payload_logs")
data class MqttPayloadLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "MessagePayload")
    val messagePayload: String,

    @ColumnInfo(name = "ReaceivedTime")
    val reaceivedTime: String,

    @ColumnInfo(name = "DisplayedTime")
    val displayedTime: String,

    @ColumnInfo(name = "IsUploaded")
    val isUploaded: Int = 0
)
