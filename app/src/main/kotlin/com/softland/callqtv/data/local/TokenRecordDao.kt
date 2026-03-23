package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TokenRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: TokenRecordEntity)

    @Query("SELECT * FROM token_records ORDER BY id DESC")
    suspend fun getAllRecords(): List<TokenRecordEntity>

    @Query("DELETE FROM token_records WHERE created_date != :today")
    suspend fun deleteOldRecords(today: String)

    @Query("UPDATE token_records SET is_uploaded = 1 WHERE id = :recordId")
    suspend fun markAsUploaded(recordId: Long)

    @Query("DELETE FROM token_records")
    suspend fun clearAll()
}
