package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AdFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<AdFileEntity>)

    @Query(
        "DELETE FROM ad_files WHERE device_id = :deviceId AND mac_address = :macAddress AND customer_id = :customerId"
    )
    fun deleteByDeviceAndCustomer(deviceId: Int, macAddress: String, customerId: String)

    @Query(
        "SELECT * FROM ad_files WHERE device_id = :deviceId ORDER BY position ASC"
    )
    fun getByDeviceId(deviceId: Int): List<AdFileEntity>

    @Query(
        "SELECT * FROM ad_files WHERE mac_address = :macAddress AND customer_id = :customerId ORDER BY position ASC"
    )
    fun getByMacAndCustomer(macAddress: String, customerId: String): List<AdFileEntity>
}
