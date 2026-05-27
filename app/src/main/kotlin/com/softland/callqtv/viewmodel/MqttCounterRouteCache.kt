package com.softland.callqtv.viewmodel

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Resolved TV counter for MQTT token routing (storage key + label). */
internal data class ResolvedCounterIdentity(
    val storageKey: String,
    val counterLabel: String,
)

/** Pure key/scope helpers for serial → counter route caching. */
internal object MqttCounterRouteKeys {
    const val CACHE_TTL_MS = 300_000L
    const val TOKEN_UI_CHANNEL_CAPACITY = 128
    const val CONFIG_REFRESH_CHANNEL_CAPACITY = 16

    /** Normalizes keypad serial for case-insensitive cache keys. */
    fun normalizeKeypadSerial(serial: String): String =
        serial.trim().uppercase(Locale.ROOT)

    /** Builds cache key from normalized serial and route index. */
    fun routeCacheKey(serial: String, routeIndex: String): String {
        val route = routeIndex.trim()
        return "${normalizeKeypadSerial(serial)}|$route"
    }

    /** Builds per-device scope key so cache invalidates when context changes. */
    fun deviceScope(macAddress: String, customerId: String): String =
        "${macAddress.trim()}|${customerId.trim()}"
}

internal sealed class RouteCacheLookup<out T> {
    data object Miss : RouteCacheLookup<Nothing>()
    data class Hit<T>(val value: T?) : RouteCacheLookup<T>()
}

/**
 * TTL cache for counter routing, scoped per device (MAC + customer).
 * Caches negative lookups ([null] values) as well as successful resolutions.
 */
internal class CounterRouteLookupCache<T>(
    private val ttlMs: Long,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry<T>(val value: T?, val expiresAtMs: Long)

    private val entries = ConcurrentHashMap<String, Entry<T>>()
    @Volatile
    private var activeScope: String = ""

    /** Clears all entries and resets active scope. */
    fun invalidate() {
        entries.clear()
        activeScope = ""
    }

    /** Ensures cache entries belong to the current device scope. */
    fun ensureScope(scope: String) {
        if (scope == activeScope) return
        entries.clear()
        activeScope = scope
    }

    /** Returns cached value (including cached null), or miss when absent/expired. */
    fun lookup(scope: String, key: String): RouteCacheLookup<T> {
        ensureScope(scope)
        val entry = entries[key] ?: return RouteCacheLookup.Miss
        val now = nowMs()
        if (entry.expiresAtMs <= now) {
            entries.remove(key, entry)
            return RouteCacheLookup.Miss
        }
        return RouteCacheLookup.Hit(entry.value)
    }

    /** Stores a cached value for a scope/key pair with TTL expiry. */
    fun store(scope: String, key: String, value: T?) {
        ensureScope(scope)
        entries[key] = Entry(value, nowMs() + ttlMs)
    }
}
