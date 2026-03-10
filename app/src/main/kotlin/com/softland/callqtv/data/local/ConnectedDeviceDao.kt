package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConnectedDeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(devices: List<ConnectedDeviceEntity>)

    @Query("DELETE FROM connected_devices WHERE device_id = :deviceId AND mac_address = :macAddress AND customer_id = :customerId")
    fun deleteByDeviceAndCustomer(deviceId: Int, macAddress: String, customerId: String)

    @Query("SELECT * FROM connected_devices WHERE mac_address = :macAddress AND customer_id = :customerId")
    fun getByMacAndCustomer(macAddress: String, customerId: String): List<ConnectedDeviceEntity>
}
