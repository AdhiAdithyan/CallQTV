package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MqttPayloadLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: MqttPayloadLogEntity)

    @Query("SELECT * FROM mqtt_payload_logs ORDER BY id DESC")
    suspend fun getAll(): List<MqttPayloadLogEntity>

    @Query("SELECT * FROM mqtt_payload_logs WHERE IsUploaded = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<MqttPayloadLogEntity>

    @Query("SELECT COUNT(1) FROM mqtt_payload_logs WHERE IsUploaded = 0 AND MessagePayload = :payload")
    suspend fun countPendingByPayload(payload: String): Int

    @Query("UPDATE mqtt_payload_logs SET IsUploaded = 1 WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>)

    @Query("UPDATE mqtt_payload_logs SET IsUploaded = 1 WHERE IsUploaded = 0 AND MessagePayload = :payload")
    suspend fun markPendingByPayloadAsUploaded(payload: String)

    @Query(
        "DELETE FROM mqtt_payload_logs " +
            "WHERE IsUploaded = 1 AND ReaceivedTime < :cutoffReceivedTime"
    )
    suspend fun deleteUploadedOlderThan(cutoffReceivedTime: String): Int

    @Query(
        "UPDATE mqtt_payload_logs SET DisplayedTime = :displayedTime " +
            "WHERE id = (SELECT id FROM mqtt_payload_logs WHERE MessagePayload = :payload ORDER BY id DESC LIMIT 1)"
    )
    suspend fun markDisplayedByPayload(payload: String, displayedTime: String)
}
