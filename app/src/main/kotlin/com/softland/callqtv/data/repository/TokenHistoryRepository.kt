package com.softland.callqtv.data.repository

import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.local.TokenHistoryEntity
import android.util.Log
import java.time.LocalDate

/**
 * Manages persistence of the last [MAX_HISTORY] tokens per counter.
 *
 * The in-memory list is always ordered newest-first (index 0 = latest token).
 * The DB mirrors this ordering via the `position` column.
 * Each token is stored with [call_date] (yyyy-MM-dd). On load, tokens from previous days are
 * cleared so the UI shows only today's called tokens.
 */
class TokenHistoryRepository(private val database: AppDatabase, private val context: android.content.Context) {

    companion object {
        const val MAX_HISTORY = 15
    }

    private val dao get() = database.tokenHistoryDao()
    private val prefs = context.getSharedPreferences("token_history_prefs", android.content.Context.MODE_PRIVATE)

    /**
     * Persist a new token for [counterKey] with the current date.
     * The token is inserted at position 0 (newest); existing entries are shifted down.
     * Entries beyond [MAX_HISTORY] are pruned.
     */
    suspend fun saveToken(counterKey: String, token: String) {
        val trimmedKey = counterKey.trim()
        val trimmedToken = token.trim()
        
        if (trimmedKey.isEmpty() || trimmedToken.isEmpty() || trimmedToken == "0") return

        val today = LocalDate.now().toString()
        val existing = dao.getHistory(trimmedKey).toMutableList()
        
        // Remove existing instance of this token to move it to top
        existing.removeAll { it.token.trim() == trimmedToken }
        
        val newList = mutableListOf(trimmedToken) + existing.map { it.token.trim() }
        val trimmedList = newList.take(MAX_HISTORY)
        
        val entities = trimmedList.mapIndexed { index, t ->
            TokenHistoryEntity(
                counterKey = trimmedKey,
                token = t,
                position = index,
                updatedAt = System.currentTimeMillis(),
                callDate = today
            )
        }
        dao.upsertAll(entities)
        dao.pruneOldEntries(trimmedKey, MAX_HISTORY)
    }

    /**
     * Load all persisted token history as a Map<counterKey, List<token>> (newest-first).
     * Clears any tokens whose [call_date] is not today ONLY ONCE PER DAY.
     * Includes a guard for unsynced system clocks (common on TV startup).
     */
    suspend fun loadAll(): Map<String, List<String>> {
        val now = LocalDate.now()
        val today = now.toString()
        
        // Guard: If year is before 2024, the TV clock likely hasn't synced yet.
        // Clearing history based on a 1970 or 2010 date would wipe today's real data.
        if (now.year >= 2024) {
            val lastCleared = prefs.getString("last_clear_date", "")
            if (today != lastCleared) {
                dao.deleteWhereDateNot(today)
                prefs.edit().putString("last_clear_date", today).apply()
                Log.i("TokenHistoryRepo", "Cleared old history for new day: $today")
            }
        } else {
            Log.w("TokenHistoryRepo", "System clock not synced ($today). Skipping daily history clear.")
        }
        
        return dao.getAllHistory()
            .groupBy { it.counterKey.trim() }
            .mapValues { (_, entries) ->
                entries.sortedBy { it.position }.map { it.token.trim() }
            }
    }

    /** Clear all persisted history (e.g. on device reset). */
    suspend fun clearAll() = dao.clearAll()
}
