package com.softland.callqtv.viewmodel.mqtt

import android.app.Application
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.softland.callqtv.data.local.CounterDao
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigDao
import com.softland.callqtv.data.model.CounterConfig
import com.softland.callqtv.data.model.KeypadConfig
import com.softland.callqtv.viewmodel.CounterRouteLookupCache
import com.softland.callqtv.viewmodel.MqttCounterRouteKeys
import com.softland.callqtv.viewmodel.ResolvedCounterIdentity
import com.softland.callqtv.viewmodel.RouteCacheLookup
import com.softland.callqtv.viewmodel.mqttRouteMatchesString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Serial (+ optional CLR route) → token map storage key and counter label. */
internal class MqttCounterIdentityResolver(
    private val app: Application,
    private val counterDao: CounterDao,
    private val tvConfigDao: TvConfigDao,
    private val gson: Gson,
    private val routeCache: CounterRouteLookupCache<ResolvedCounterIdentity>,
    private val routingCacheScope: () -> String,
) {
    /** Resolves keypad serial to counter identity when route index is not provided. */
    suspend fun resolve(serial: String): ResolvedCounterIdentity? =
        resolve(serial, routeIndex = "")

    /** Resolves keypad serial (+optional route index) to storage key and display label. */
    suspend fun resolve(serial: String, routeIndex: String): ResolvedCounterIdentity? {
        if (serial.isBlank()) return null
        val scope = routingCacheScope()
        val cacheKey = MqttCounterRouteKeys.routeCacheKey(serial, routeIndex)
        when (val cached = routeCache.lookup(scope, cacheKey)) {
            is RouteCacheLookup.Hit -> return cached.value
            is RouteCacheLookup.Miss -> Unit
        }
        val resolved = resolveUncached(serial, routeIndex)
        routeCache.store(scope, cacheKey, resolved)
        return resolved
    }

    /** Checks whether a keypad counter config row matches CLR route index. */
    fun counterConfigMatchesRoute(cc: CounterConfig, route: String): Boolean {
        if (mqttRouteMatchesString(cc.keypadIndex, route)) return true
        return mqttRouteMatchesString(cc.buttonIndex?.toString(), route)
    }

    /** Checks whether a persisted counter entity matches CLR route index. */
    fun counterEntityMatchesRoute(counter: CounterEntity, route: String): Boolean {
        if (mqttRouteMatchesString(counter.keypadIndex, route)) return true
        return mqttRouteMatchesString(counter.buttonIndex?.toString(), route)
    }

    /** Checks whether any counter label/id variant matches the given label string. */
    fun counterMatchesLabel(counter: CounterEntity, label: String): Boolean {
        val key = label.trim()
        if (key.isEmpty()) return false
        return listOfNotNull(
            counter.counterId,
            counter.name,
            counter.defaultName,
            counter.code,
            counter.defaultCode,
        ).any { it.trim().equals(key, ignoreCase = true) }
    }

    /** Matches a Room counter entity against keypad config row aliases. */
    fun counterEntityMatchesKeypadCounter(counter: CounterEntity, keypadCounter: CounterConfig): Boolean {
        if (mqttRouteMatchesString(counter.keypadIndex, keypadCounter.keypadIndex.orEmpty())) return true
        return counter.counterId?.trim().equals(keypadCounter.counterId?.trim(), ignoreCase = true) == true ||
            counter.name?.trim().equals(keypadCounter.name?.trim(), ignoreCase = true) == true ||
            counter.defaultName?.trim().equals(keypadCounter.defaultName?.trim(), ignoreCase = true) == true
    }

    /** Converts keypad counter config + entity mapping into internal token map key aliases. */
    fun counterConfigToInternalMapKeys(cc: CounterConfig, counters: List<CounterEntity>): Set<String> {
        val entity = counters.firstOrNull { counterEntityMatchesKeypadCounter(it, cc) }
        return buildSet {
            cc.buttonIndex?.let { add(it.toString()) }
            entity?.buttonIndex?.let { add(it.toString()) }
            cc.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            entity?.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            entity?.counterId?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            cc.counterId?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    /** Collects all token-map keys that should be cleared for a CLR keypad payload. */
    suspend fun collectTokenMapKeysForClrKeypad(serial: String, routeIndex: String): Set<String> =
        withContext(Dispatchers.IO) {
            try {
                val customerId = MqttDeviceContext.customerId(app)
                val macAddress = MqttDeviceContext.macAddress(app)
                val counters = counterDao.getByMacAndCustomer(macAddress, customerId)
                val cfg = tvConfigDao.getByMacAndCustomer(macAddress, customerId)
                val keypads = cfg?.keypadsJson?.let { json ->
                    val type = object : TypeToken<List<KeypadConfig>>() {}.type
                    gson.fromJson<List<KeypadConfig>>(json, type) ?: emptyList()
                }.orEmpty()
                val keypad = keypads.firstOrNull {
                    it.keypadSn?.trim().equals(serial.trim(), ignoreCase = true)
                } ?: return@withContext emptySet()
                val configs = keypad.counters.orEmpty()
                if (configs.isEmpty()) return@withContext emptySet()
                val route = routeIndex.trim()
                val matching = when {
                    route.isEmpty() -> configs
                    else -> configs.filter { counterConfigMatchesRoute(it, route) }
                }
                val keys = linkedSetOf<String>()
                matching.forEach { cc -> keys.addAll(counterConfigToInternalMapKeys(cc, counters)) }
                keys
            } catch (_: Exception) {
                emptySet()
            }
        }

    /** Resolves legacy/internal alias keys used by CLR flows for a keypad route. */
    suspend fun resolveClrInternalMapAliasKeys(serial: String, routeIndex: String): Set<String> =
        withContext(Dispatchers.IO) {
            try {
                val customerId = MqttDeviceContext.customerId(app)
                val macAddress = MqttDeviceContext.macAddress(app)
                val counters = counterDao.getByMacAndCustomer(macAddress, customerId)
                val cfg = tvConfigDao.getByMacAndCustomer(macAddress, customerId)
                val keypads = cfg?.keypadsJson?.let { json ->
                    val type = object : TypeToken<List<KeypadConfig>>() {}.type
                    gson.fromJson<List<KeypadConfig>>(json, type) ?: emptyList()
                }.orEmpty()
                val keypadCounter = keypads
                    .firstOrNull { it.keypadSn?.trim().equals(serial.trim(), ignoreCase = true) }
                    ?.counters
                    ?.firstOrNull { counterConfigMatchesRoute(it, routeIndex) }
                val matched = keypadCounter?.let { cc ->
                    counters.firstOrNull { counter -> counterEntityMatchesKeypadCounter(counter, cc) }
                } ?: counters.firstOrNull { counterEntityMatchesRoute(it, routeIndex) }
                if (matched == null) return@withContext emptySet()
                buildSet {
                    matched.buttonIndex?.let { add(it.toString()) }
                    keypadCounter?.buttonIndex?.let { add(it.toString()) }
                    matched.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                    keypadCounter?.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (routeIndex.isNotBlank()) add(routeIndex.trim())
                }
            } catch (_: Exception) {
                emptySet()
            }
        }

    /** Resolves display text from keypad buttonStrings mapping when replace payload uses id. */
    suspend fun resolveButtonStringValue(
        serial: String,
        buttonStringId: String,
        tokenFallback: String,
    ): String? {
        if (serial.isBlank() || buttonStringId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val customerId = MqttDeviceContext.customerId(app)
                val macAddress = MqttDeviceContext.macAddress(app)
                val cfg = tvConfigDao.getByMacAndCustomer(macAddress, customerId) ?: return@withContext null
                val json = cfg.keypadsJson ?: return@withContext null
                val type = object : TypeToken<List<KeypadConfig>>() {}.type
                val keypads: List<KeypadConfig> = gson.fromJson(json, type) ?: emptyList()
                val keypad = keypads.firstOrNull {
                    it.keypadSn?.trim().equals(serial.trim(), ignoreCase = true)
                } ?: return@withContext null
                keypad.buttonStrings
                    ?.firstOrNull {
                        it.id?.trim() == buttonStringId.trim() || it.id?.trim() == tokenFallback.trim()
                    }
                    ?.value
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Performs uncached serial->counter resolution against keypadsJson and counters tables. */
    private suspend fun resolveUncached(serial: String, routeIndex: String): ResolvedCounterIdentity? =
        withContext(Dispatchers.IO) {
            try {
                val customerId = MqttDeviceContext.customerId(app)
                val macAddress = MqttDeviceContext.macAddress(app)
                val counters = counterDao.getByMacAndCustomer(macAddress, customerId)
                val cfg = tvConfigDao.getByMacAndCustomer(macAddress, customerId)
                val keypads = cfg?.keypadsJson?.let { json ->
                    val type = object : TypeToken<List<KeypadConfig>>() {}.type
                    gson.fromJson<List<KeypadConfig>>(json, type) ?: emptyList()
                }.orEmpty()
                val keypad = keypads.firstOrNull {
                    it.keypadSn?.trim().equals(serial.trim(), ignoreCase = true)
                } ?: run {
                    android.util.Log.d(
                        "MqttViewModel",
                        "resolveCounter: no keypadsJson entry for keypad_sn=${MqttCounterRouteKeys.normalizeKeypadSerial(serial)}",
                    )
                    return@withContext null
                }
                val configs = keypad.counters.orEmpty()
                if (configs.isEmpty()) {
                    android.util.Log.d(
                        "MqttViewModel",
                        "resolveCounter: no counters[] under keypad_sn=${MqttCounterRouteKeys.normalizeKeypadSerial(serial)}",
                    )
                    return@withContext null
                }
                val route = routeIndex.trim()
                val keypadCounter = if (route.isNotEmpty()) {
                    configs.firstOrNull { counterConfigMatchesRoute(it, route) }
                } else {
                    configs.singleOrNull()
                        ?: configs.firstOrNull { cc ->
                            counters.any { counterEntityMatchesKeypadCounter(it, cc) }
                        }
                        ?: configs.firstOrNull()
                } ?: run {
                    android.util.Log.d(
                        "MqttViewModel",
                        if (route.isNotEmpty()) {
                            "resolveCounter: no counter with keypad_index=$route under keypad_sn=" +
                                MqttCounterRouteKeys.normalizeKeypadSerial(serial)
                        } else {
                            "resolveCounter: no counter row for keypad_sn=" +
                                MqttCounterRouteKeys.normalizeKeypadSerial(serial)
                        },
                    )
                    return@withContext null
                }
                val matched = counters.firstOrNull { counter ->
                    counterEntityMatchesKeypadCounter(counter, keypadCounter)
                } ?: run {
                    android.util.Log.d(
                        "MqttViewModel",
                        "resolveCounter: counters[] row missing for keypad_sn=" +
                            MqttCounterRouteKeys.normalizeKeypadSerial(serial),
                    )
                    return@withContext null
                }
                val storageKey =
                    (keypadCounter.buttonIndex ?: matched.buttonIndex)?.toString()?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: matched.keypadIndex?.trim()?.takeIf { it.isNotBlank() }
                        ?: keypadCounter.keypadIndex?.trim()?.takeIf { it.isNotBlank() }
                        ?: return@withContext null
                val counterLabel = matched.counterId?.trim()?.takeIf { it.isNotBlank() }
                    ?: matched.name?.trim()?.takeIf { it.isNotBlank() }
                    ?: matched.defaultName?.trim()?.takeIf { it.isNotBlank() }
                    ?: keypadCounter.counterId?.trim()?.takeIf { it.isNotBlank() }
                    ?: storageKey
                android.util.Log.i(
                    "MqttViewModel",
                    "resolveCounter: SN=${MqttCounterRouteKeys.normalizeKeypadSerial(serial)}" +
                        (if (route.isNotEmpty()) " route=$route" else "") +
                        " UI key(button_index)=$storageKey -> counter=$counterLabel",
                )
                ResolvedCounterIdentity(storageKey = storageKey, counterLabel = counterLabel)
            } catch (_: Exception) {
                null
            }
        }
}
