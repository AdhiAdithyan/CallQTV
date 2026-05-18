package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TvConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(config: TvConfigEntity)

    @Query("SELECT * FROM tv_config WHERE device_id = :deviceId LIMIT 1")
    fun getByDeviceId(deviceId: Int): TvConfigEntity?

    @Query("SELECT * FROM tv_config WHERE mac_address = :macAddress AND customer_id = :customerId LIMIT 1")
    fun getByMacAndCustomer(macAddress: String, customerId: String): TvConfigEntity?
}
