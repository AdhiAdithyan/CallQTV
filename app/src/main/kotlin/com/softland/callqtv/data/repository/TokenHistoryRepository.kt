package com.softland.callqtv.data.repository

import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.local.TokenHistoryEntity
import java.time.LocalDate

/**
 * Manages persistence of the last [MAX_HISTORY] tokens per counter.
 *
 * The in-memory list is always ordered newest-first (index 0 = latest token).
 * The DB mirrors this ordering via the `position` column.
 * Each token is stored with [call_date] (yyyy-MM-dd). On load, tokens from previous days are
 * cleared so the UI shows only today's called tokens.
 */
class TokenHistoryRepository(private val database: AppDatabase) {

    companion object {
        const val MAX_HISTORY = 15
    }

    private val dao get() = database.tokenHistoryDao()

    /**
     * Persist a new token for [counterKey] with the current date.
     * The token is inserted at position 0 (newest); existing entries are shifted down.
     * Entries beyond [MAX_HISTORY] are pruned.
     */
    suspend fun saveToken(counterKey: String, token: String) {
        val today = LocalDate.now().toString()
        val existing = dao.getHistory(counterKey).toMutableList()
        existing.removeAll { it.token == token }
        val newList = mutableListOf(token) + existing.map { it.token }
        val trimmed = newList.take(MAX_HISTORY)
        val entities = trimmed.mapIndexed { index, t ->
            TokenHistoryEntity(
                counterKey = counterKey,
                token = t,
                position = index,
                updatedAt = System.currentTimeMillis(),
                callDate = today
            )
        }
        dao.upsertAll(entities)
        dao.pruneOldEntries(counterKey, MAX_HISTORY)
    }

    /**
     * Load all persisted token history as a Map<counterKey, List<token>> (newest-first).
     * Clears any tokens whose [call_date] is not today so the UI shows only today's tokens.
     */
    suspend fun loadAll(): Map<String, List<String>> {
        val today = LocalDate.now().toString()
        dao.deleteWhereDateNot(today)
        return dao.getAllHistory()
            .groupBy { it.counterKey }
            .mapValues { (_, entries) ->
                entries.sortedBy { it.position }.map { it.token }
            }
    }

    /** Clear all persisted history (e.g. on device reset). */
    suspend fun clearAll() = dao.clearAll()
}
