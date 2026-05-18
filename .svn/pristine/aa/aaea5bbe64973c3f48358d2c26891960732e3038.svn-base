package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MappedBrokerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: MappedBrokerEntity)

    @Query("SELECT * FROM mapped_broker WHERE device_id = :deviceId")
    fun getByDeviceId(deviceId: Int): List<MappedBrokerEntity>

    @Query("SELECT * FROM mapped_broker WHERE mac_address = :macAddress AND customer_id = :customerId")
    fun getAllByMacAndCustomer(macAddress: String, customerId: String): List<MappedBrokerEntity>

    @Query("SELECT * FROM mapped_broker WHERE mac_address = :macAddress AND customer_id = :customerId LIMIT 1")
    fun getByMacAndCustomer(macAddress: String, customerId: String): MappedBrokerEntity?
}
