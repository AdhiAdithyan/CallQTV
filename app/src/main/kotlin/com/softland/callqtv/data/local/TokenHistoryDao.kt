package com.softland.callqtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TokenHistoryDao {

    /** Load all saved tokens for a counter, ordered newest-first (position ASC). */
    @Query("SELECT * FROM token_history WHERE counter_key = :counterKey ORDER BY position ASC")
    suspend fun getHistory(counterKey: String): List<TokenHistoryEntity>

    /** Load all saved tokens for ALL counters, ordered newest-first per counter. */
    @Query("SELECT * FROM token_history ORDER BY counter_key ASC, position ASC")
    suspend fun getAllHistory(): List<TokenHistoryEntity>

    /** Upsert a single history row (counter_key + position is unique). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TokenHistoryEntity)

    /** Upsert a batch of history rows. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<TokenHistoryEntity>)

    /** Remove rows beyond the kept limit for a given counter. */
    @Query("""
        DELETE FROM token_history
        WHERE counter_key = :counterKey
          AND position >= :maxCount
    """)
    suspend fun pruneOldEntries(counterKey: String, maxCount: Int)

    /** Wipe all history (e.g. on full config reset). */
    @Query("DELETE FROM token_history")
    suspend fun clearAll()
}
