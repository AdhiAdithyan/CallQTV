package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CounterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<CounterEntity>)

    @Query(
        "DELETE FROM counters WHERE device_id = :deviceId AND mac_address = :macAddress AND customer_id = :customerId"
    )
    fun deleteByDeviceAndCustomer(deviceId: Int, macAddress: String, customerId: String)

    @Query(
        "SELECT * FROM counters WHERE device_id = :deviceId ORDER BY button_index ASC"
    )
    fun getByDeviceId(deviceId: Int): List<CounterEntity>

    @Query(
        "SELECT * FROM counters WHERE mac_address = :macAddress AND customer_id = :customerId ORDER BY button_index ASC"
    )
    fun getByMacAndCustomer(macAddress: String, customerId: String): List<CounterEntity>
}
