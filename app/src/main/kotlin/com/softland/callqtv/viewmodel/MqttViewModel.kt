package com.softland.callqtv.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.model.CounterConfig
import com.softland.callqtv.data.model.KeypadConfig
import com.softland.callqtv.data.repository.MqttClientManager
import com.softland.callqtv.data.repository.MqttPayloadLogRepository
import com.softland.callqtv.data.repository.TokenHistoryRepository
import com.softland.callqtv.utils.FileLogger
import com.softland.callqtv.utils.SemanticMqttParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class MqttViewModel(application: Application) : AndroidViewModel(application) {
    data class TokenUiEvent(
        val counter: String,
        val token: String,
        val payload: String,
        /** Fixed-protocol index-4 `D`: VIP / emergency; UI and speech always use ER prefix. */
        val isVipEmergency: Boolean = false,
    )

    /**
     * [playCueUi]: chime + token snapshot + blink (any user-visible token grid or VIP overlay change).
     * [speakTokenAnnouncement]: prior primary-key rules only (move / re-call after 10s) for TTS.
     */
    data class TokenUiProcessResult(
        val playCueUi: Boolean,
        val speakTokenAnnouncement: Boolean,
    )

    private data class ResolvedCounterIdentity(
        /** Token map / UI route key — `button_index` from TV config / counters row (not `keypad_index`). */
        val storageKey: String,
        /** Counter id/name for payload logs and announcements. */
        val counterLabel: String,
    )

    private val managers = mutableMapOf<String, MqttClientManager>()
    private val connectionDetailsMap = mutableMapOf<String, MqttConnectionDetails>()
    private val stateMutex = Mutex()
    private val gson = Gson()

    private val retryAttempts = mutableMapOf<String, Int>()
    private val retryJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    // Tracks whether a given serverUri has ever connected successfully. We only run our
    // own retry loop until the first success; after that, rely on Paho's auto-reconnect.
    private val everConnected = mutableMapOf<String, Boolean>()
    private val reachabilityJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val reachabilityStatusMap = mutableMapOf<String, Boolean>()
    private var continuousPublishJob: kotlinx.coroutines.Job? = null
    private var messageLivenessWatchdogJob: kotlinx.coroutines.Job? = null
    private var lastConfigRefreshAtMs: Long = 0L

    /** Last time *any* payload arrived on the subscribed topic (incl. broker "PING"), per broker URI. */
    private val lastMessageAtByServerUri = ConcurrentHashMap<String, Long>()
    private val staleTrafficStrikeByServerUri = ConcurrentHashMap<String, Int>()
    private val mqttLogThrottleByKey = ConcurrentHashMap<String, Long>()

    private val staleReconnectInFlight =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Tracks the number of verified tokens pending processing/announcement in UI.
    val announcementQueueSize = java.util.concurrent.atomic.AtomicInteger(0)

    private val tokenHistoryRepo: TokenHistoryRepository by lazy {
        TokenHistoryRepository(AppDatabase.getInstance(application), application)
    }

    private val connectedDeviceDao by lazy {
        AppDatabase.getInstance(application).connectedDeviceDao()
    }
    private val tvConfigDao by lazy {
        AppDatabase.getInstance(application).tvConfigDao()
    }
    private val counterDao by lazy {
        AppDatabase.getInstance(application).counterDao()
    }

    private val tokenRecordRepo: com.softland.callqtv.data.repository.TokenRecordRepository by lazy {
        com.softland.callqtv.data.repository.TokenRecordRepository(AppDatabase.getInstance(application))
    }

    private val mqttPayloadLogRepo: MqttPayloadLogRepository by lazy {
        MqttPayloadLogRepository(AppDatabase.getInstance(application), application)
    }

    init {
        loadPersistedHistory()
        performRecordsCleanup()
    }

    @Volatile
    private var lastPayloadSyncAtMs: Long = 0L

    /** Max tokens kept per counter key; from [com.softland.callqtv.data.local.TvConfigEntity.tokensPerCounter]. */
    @Volatile
    private var tokenHistoryLimit: Int = 15

    fun applyTokenHistoryLimitFromConfig(config: com.softland.callqtv.data.local.TvConfigEntity?) {
        tokenHistoryLimit = config?.tokensPerCounter?.coerceAtLeast(1) ?: 15
    }

    private fun trimTokenHistory(list: List<String>): List<String> =
        list.take(tokenHistoryLimit.coerceAtLeast(1))

    // In-memory cache of allowed keypad serials (normalized uppercase),
    // to avoid a DB lookup on every MQTT message.
    private val keypadSerials = mutableSetOf<String>()
    @Volatile
    private var keypadSerialsLoaded = false

    private data class MqttConnectionDetails(
        val serverUri: String,
        val clientId: String,
        val username: String?,
        val password: String?,
        val topic: String,
        val qos: Int,
    )

    /** Broker endpoint from TV config / mapped_broker table (used after config sync). */
    data class BrokerEndpoint(
        val serverUri: String,
        val clientId: String,
        val username: String?,
        val password: String?,
        val topic: String,
        val qos: Int = 1,
    )

    @Volatile
    private var isLicenseExpired = false

    fun setLicenseExpired(expired: Boolean) {
        isLicenseExpired = expired
        if (expired) {
            android.util.Log.w("MqttViewModel", "License expired; MQTT message processing is BLOCKED.")
        }
    }

    private val _receivedMessage = MutableLiveData<String>()
    fun getReceivedMessage(): LiveData<String> = _receivedMessage

    private val _lastPayload = MutableLiveData<String>("")
    fun getLastPayload(): LiveData<String> = _lastPayload

    private val _connectionStatusMap = MutableLiveData<Map<String, Boolean>>(emptyMap())
    fun getConnectionStatusMap(): LiveData<Map<String, Boolean>> = _connectionStatusMap

    private val _connectionStatus = MutableLiveData<Boolean>(false)
    fun getConnectionStatus(): LiveData<Boolean> = _connectionStatus

    private val _errorMessage = MutableLiveData<String>("")
    fun getErrorMessage(): LiveData<String> = _errorMessage

    private val _isConnectingToMqtt = MutableLiveData<Boolean>(false)
    fun isConnectingToMqtt(): LiveData<Boolean> = _isConnectingToMqtt

    private val _connectTimer = MutableLiveData<Int>(0)
    fun getConnectTimer(): LiveData<Int> = _connectTimer

    private var timerJob: kotlinx.coroutines.Job? = null
    private var connectTime = 0

    private fun startConnectTimer() {
        if (timerJob?.isActive == true) return

        // Reset only when starting a fresh timer job.
        connectTime = 0
        _connectTimer.postValue(0)

        _isConnectingToMqtt.postValue(true)
        // Run timer off main thread so UI load does not freeze timer updates.
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1000)
                connectTime++
                _connectTimer.postValue(connectTime)
            }
        }
    }

    private fun stopConnectTimer() {
        // Only stop if ALL brokers are considered connected.
        val allConnected = connectionDetailsMap.keys.all { managers[it]?.isConnected() == true }
        if (allConnected) {
            _isConnectingToMqtt.postValue(false)
            timerJob?.cancel()
            timerJob = null
        }
    }

    private val _isAutoRetryExhausted = MutableLiveData<Boolean>(false)
    fun isAutoRetryExhausted(): LiveData<Boolean> = _isAutoRetryExhausted

    // Exposes current MQTT retry attempt (for UI display)
    private val _mqttRetryAttempt = MutableLiveData<Int>(0)
    fun getMqttRetryAttempt(): LiveData<Int> = _mqttRetryAttempt

    // Exposes whether the broker host/port is reachable via a short TCP ping.
    private val _isBrokerReachable = MutableLiveData<Boolean>(false)
    fun isBrokerReachable(): LiveData<Boolean> = _isBrokerReachable

    private val _dispenseConnectedByButton = MutableLiveData<Map<String, Boolean>>(emptyMap())
    fun getDispenseConnectedByButton(): LiveData<Map<String, Boolean>> = _dispenseConnectedByButton

    private val _keypadConnectedByButton = MutableLiveData<Map<String, Boolean>>(emptyMap())
    fun getKeypadConnectedByButton(): LiveData<Map<String, Boolean>> = _keypadConnectedByButton

    private val lastDispenseSeenAtByButton = ConcurrentHashMap<String, Long>()
    private val lastKeypadSeenAtByButton = ConcurrentHashMap<String, Long>()
    private var indicatorWatchdogJob: kotlinx.coroutines.Job? = null

    val tokenUpdateChannel =
        kotlinx.coroutines.channels.Channel<TokenUiEvent>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)
    val tokenReplaceChannel =
        kotlinx.coroutines.channels.Channel<TokenUiEvent>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)
    /**
     * Requests to refresh TV configuration based on a *specific* MQTT response.
     * (Used to remove FCM dependency and keep refresh driven by MQTT.)
     */
    val configRefreshRequests =
        kotlinx.coroutines.channels.Channel<TvConfigRefreshSignal>(
            capacity = kotlinx.coroutines.channels.Channel.UNLIMITED
        )
    private val queuedTokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val queuedPayloadTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Queue of ALL raw MQTT payloads received (no filtering). Business logic consumes and filters
    // from this queue so we never lose received data due to early drops.
    private val rawMessageQueue: java.util.Queue<String> =
        java.util.concurrent.ConcurrentLinkedQueue<String>()

    private val _tokensPerCounter = MutableLiveData<Map<String, List<String>>>(emptyMap())
    fun getTokensPerCounter(): LiveData<Map<String, List<String>>> = _tokensPerCounter

    // Thread-safe internal state to prevent race conditions during updates
    private val internalTokenMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val announcedTokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Raw token value for which slot 0 should show VIP/emergency prefix (ER), per map key. */
    private val vipEmergencyTopTokenByKey = ConcurrentHashMap<String, String>()
    private val _vipEmergencyTopTokenByKey = MutableLiveData<Map<String, String>>(emptyMap())

    fun getVipEmergencyTopTokenByKey(): LiveData<Map<String, String>> = _vipEmergencyTopTokenByKey

    private fun postVipEmergencyTopTokenSnapshot() {
        _vipEmergencyTopTokenByKey.postValue(HashMap(vipEmergencyTopTokenByKey))
    }
    @Volatile
    private var isHistoryLoaded = false

    /**
     * Deduplicate rapid MQTT messages. Returns true if announced within the last 2 seconds.
     */
    fun isAlreadyAnnounced(counterKey: String, token: String): Boolean {
        val key = "${counterKey.trim()}_${token.trim()}"
        val lastTime = announcedTokenTimestamps[key] ?: 0L
        return (System.currentTimeMillis() - lastTime) < 2000L
    }

    /**
     * Returns true when the same token is already in first position for the given key
     * and its previous announcement was less than 10 seconds ago.
     * Use this to skip both UI update and re-announcement.
     */
    suspend fun shouldSkipTopTokenWithin10s(counterKey: String, token: String): Boolean {
        val key = counterKey.trim()
        val trimmedToken = token.trim()
        return stateMutex.withLock {
            val top = internalTokenMap[key]?.firstOrNull() ?: return@withLock false
            if (top != trimmedToken) return@withLock false
            val lastTime = announcedTokenTimestamps["${key}_$trimmedToken"] ?: 0L
            (System.currentTimeMillis() - lastTime) < 10000L
        }
    }

    fun markAsAnnounced(counterKey: String, token: String) {
        val key = "${counterKey.trim()}_${token.trim()}"
        announcedTokenTimestamps[key] = System.currentTimeMillis()

        // Cleanup old entries (simple way to prevent memory leak)
        if (announcedTokenTimestamps.size > 100) {
            val now = System.currentTimeMillis()
            announcedTokenTimestamps.entries.removeIf { now - it.value > 60000 }
        }
    }

    fun publishTokensSnapshot() {
        _tokensPerCounter.postValue(internalTokenMap.toMap())
    }

    fun loadPersistedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    Context.MODE_PRIVATE
                )
                val customerId = String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
                )
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
                applyTokenHistoryLimitFromConfig(tvConfigDao.getByMacAndCustomer(macAddress, customerId))
            } catch (_: Exception) {
                // Keep default limit when config is unavailable.
            }
            stateMutex.withLock {
                try {
                    val persisted = tokenHistoryRepo.loadAll()
                    val coldStart = !isHistoryLoaded
                    // Cold start: merge DB with any MQTT tokens that arrived early.
                    // After history is loaded, never re-insert keys removed by CLR (only update existing keys).
                    persisted.forEach { (key, tokens) ->
                        if (!coldStart && !internalTokenMap.containsKey(key)) return@forEach
                        val current = internalTokenMap[key] ?: emptyList()
                        val combined = trimTokenHistory((tokens + current).distinct())
                        if (combined.isNotEmpty()) {
                            internalTokenMap[key] = combined
                        }
                    }
                    isHistoryLoaded = true
                    _tokensPerCounter.postValue(internalTokenMap.toMap())
                } catch (e: Exception) {
                    android.util.Log.w(
                        "MqttViewModel",
                        "Failed to load token history: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearTokenHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            clearTokenHistorySync()
        }
    }

    /** Clears in-memory and persisted token state; use before a full TV config refresh. */
    suspend fun clearTokenHistorySync() {
        withContext(Dispatchers.IO) {
            stateMutex.withLock {
                try {
                    tokenHistoryRepo.clearAll()
                    internalTokenMap.clear()
                    isHistoryLoaded = true
                    _tokensPerCounter.postValue(emptyMap())
                    announcedTokenTimestamps.clear()
                    vipEmergencyTopTokenByKey.clear()
                    _vipEmergencyTopTokenByKey.postValue(emptyMap())
                    queuedTokenTimestamps.clear()
                    queuedPayloadTimestamps.clear()
                    announcementQueueSize.set(0)
                } catch (e: Exception) {
                    android.util.Log.w(
                        "MqttViewModel",
                        "Failed to clear token history: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun clearTokensForResolvedCounter(serial: String, routeIndex: String) {
        val trimmedRoute = routeIndex.trim()
        val resolved =
            if (trimmedRoute.isNotEmpty()) resolveCounterIdentityFromSerial(serial, trimmedRoute) else null
        val storageKey = resolved?.storageKey?.trim().orEmpty()
        val counterLabel = resolved?.counterLabel?.trim().orEmpty()
        val aliasKeys =
            if (trimmedRoute.isNotEmpty()) resolveClrInternalMapAliasKeys(serial, trimmedRoute) else emptySet()
        val keypadKeys = collectTokenMapKeysForClrKeypad(serial, trimmedRoute)
        val keysToClear = linkedSetOf<String>().apply {
            add(storageKey)
            add(counterLabel)
            if (trimmedRoute.isNotEmpty()) add(trimmedRoute)
            addAll(aliasKeys)
            addAll(keypadKeys)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        if (keysToClear.isEmpty()) {
            android.util.Log.d(
                "MqttViewModel",
                "CLR: no token map keys for serial='$serial' route='$trimmedRoute' (check tv_config keypads for this SN)"
            )
            return
        }

        stateMutex.withLock {
            var changed = false
            keysToClear.forEach { key ->
                changed = internalTokenMap.remove(key) != null || changed
                announcedTokenTimestamps.entries.removeIf { it.key.startsWith("${key}_") }
                queuedTokenTimestamps.entries.removeIf { it.key.startsWith("${key}_") }
                vipEmergencyTopTokenByKey.remove(key)
            }
            postVipEmergencyTopTokenSnapshot()
            tokenHistoryRepo.clearCounterKeys(keysToClear)
            clearQueuedPayloadsForCounter(serial, trimmedRoute)

            if (!changed) {
                android.util.Log.w(
                    "MqttViewModel",
                    "CLR cleared DB/history for keys=$keysToClear but no in-memory token map entries matched " +
                        "(serial=$serial route='$trimmedRoute')"
                )
            }
            _tokensPerCounter.postValue(internalTokenMap.toMap())
        }
    }

    /**
     * Builds every [internalTokenMap] key that may hold tokens for counters mapped to [serial] in
     * [TvConfigEntity.keypadsJson]. Uses [routeIndex] when it matches `keypad_index` on a counter entry
     * (payload route still selects the row); cleared keys include `button_index` and legacy aliases.
     * If nothing matches (or [routeIndex] is blank), every counter under that keypad is included.
     */
    private suspend fun collectTokenMapKeysForClrKeypad(serial: String, routeIndex: String): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    Context.MODE_PRIVATE
                )
                val customerId = String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
                )
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
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
    }

    private fun counterConfigToInternalMapKeys(cc: CounterConfig, counters: List<CounterEntity>): Set<String> {
        val entity = counters.firstOrNull { counter ->
            counterEntityMatchesKeypadCounter(counter, cc)
        }
        return buildSet {
            cc.buttonIndex?.let { add(it.toString()) }
            entity?.buttonIndex?.let { add(it.toString()) }
            cc.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            entity?.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            entity?.counterId?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            cc.counterId?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    /**
     * All [internalTokenMap] key variants for the counter targeted by a CLR (same order as token UI storage).
     */
    private suspend fun resolveClrInternalMapAliasKeys(serial: String, routeIndex: String): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    android.content.Context.MODE_PRIVATE
                )
                val customerId = String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
                )
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
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
    }

    private fun clearQueuedPayloadsForCounter(serial: String, routeIndex: String) {
        val route = routeIndex.trim()
        queuedPayloadTimestamps.entries.removeIf { entry ->
            val payloadSerial = KeypadPayloadParser.extractKeypadSerial(entry.key) ?: return@removeIf false
            if (!payloadSerial.trim().equals(serial.trim(), ignoreCase = true)) return@removeIf false
            if (route.isEmpty()) return@removeIf true
            payloadMatchesResolvedCounter(
                payload = entry.key,
                serial = serial,
                routeIndex = route,
            )
        }
    }

    private fun payloadMatchesResolvedCounter(
        payload: String,
        serial: String,
        routeIndex: String
    ): Boolean {
        val payloadSerial = KeypadPayloadParser.extractKeypadSerial(payload) ?: return false
        if (!payloadSerial.trim().equals(serial.trim(), ignoreCase = true)) return false

        KeypadPayloadParser.extractClearPayloadInfo(payload)?.let { clearInfo ->
            return routeMatches(clearInfo.routeIndex, routeIndex)
        }

        // Fixed frames route by keypad SN only; once serial matches, include in CLR dedupe reset.
        if (SemanticMqttParser.parseFixedPayload(payload) != null) {
            return true
        }

        // Legacy payloads do not consistently expose a separate route index, so once the
        // serial matches we allow them through again after a CLR-triggered reset.
        return true
    }

    private fun performRecordsCleanup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tokenRecordRepo.performDailyCleanup()
            } catch (e: Exception) {
                android.util.Log.e("MqttViewModel", "Token records cleanup failed", e)
            }
        }
    }

    /**
     * Applies broker rows after a config sync: drops removed brokers, reconnects when host,
     * topic, credentials, client id, or QoS change, and ensures subscribe/connect otherwise.
     */
    suspend fun reconcileBrokersAfterConfigSync(
        endpoints: List<BrokerEndpoint>,
        context: android.content.Context,
    ) {
        val desiredUris = endpoints.map { it.serverUri }.toSet()
        val toConnect = mutableListOf<BrokerEndpoint>()
        val unchangedConnectedUris = mutableListOf<String>()

        stateMutex.withLock {
            val staleUris = connectionDetailsMap.keys.filter { it !in desiredUris }
            for (uri in staleUris) {
                android.util.Log.i(
                    "MqttViewModel",
                    "Config sync: broker removed from TV config — disconnecting $uri",
                )
                detachBrokerLocked(uri)
            }

            for (endpoint in endpoints) {
                val newDetails = endpoint.toMqttConnectionDetails()
                val previous = connectionDetailsMap[endpoint.serverUri]
                val manager = managers[endpoint.serverUri]

                when {
                    previous == null -> toConnect.add(endpoint)
                    previous != newDetails -> {
                        android.util.Log.i(
                            "MqttViewModel",
                            "Config sync: MQTT settings changed for ${endpoint.serverUri} " +
                                "(host/topic/credentials/clientId/qos) — reconnecting",
                        )
                        detachBrokerLocked(endpoint.serverUri)
                        toConnect.add(endpoint)
                    }
                    manager?.isConnected() != true -> {
                        android.util.Log.d(
                            "MqttViewModel",
                            "Config sync: ${endpoint.serverUri} not connected — connecting",
                        )
                        connectionDetailsMap[endpoint.serverUri] = newDetails
                        toConnect.add(endpoint)
                    }
                    else -> {
                        connectionDetailsMap[endpoint.serverUri] = newDetails
                        manager.subscribe(endpoint.topic, endpoint.qos)
                        unchangedConnectedUris.add(endpoint.serverUri)
                    }
                }
            }
        }

        if (unchangedConnectedUris.isNotEmpty()) {
            stopConnectTimer()
            unchangedConnectedUris.forEach { updateStatus(it, true) }
        }

        for (endpoint in toConnect) {
            initAndConnect(
                serverUri = endpoint.serverUri,
                clientId = endpoint.clientId,
                username = endpoint.username,
                password = endpoint.password,
                topic = endpoint.topic,
                qos = endpoint.qos,
                context = context,
            )
        }
    }

    fun initAndConnect(
        serverUri: String,
        clientId: String,
        username: String?,
        password: String?,
        topic: String,
        qos: Int,
        context: android.content.Context
    ) {
        val newDetails = MqttConnectionDetails(serverUri, clientId, username, password, topic, qos)
        val previousDetails = connectionDetailsMap[serverUri]
        connectionDetailsMap[serverUri] = newDetails

        val existing = managers[serverUri]
        if (existing != null) {
            if (existing.clientId == clientId && previousDetails == newDetails) {
                existing.subscribe(topic, qos)
                if (existing.isConnected()) {
                    // Already connected: make sure UI does NOT show "connecting..."
                    stopConnectTimer()
                    updateStatus(serverUri, true)
                } else {
                    // Not connected yet: trigger a new connect attempt and timer
                    startConnectTimer()
                    if (!existing.isConnectingNow()) {
                        existing.connect(username, password)
                    }
                }
                return
            }
            detachBrokerSync(serverUri)
        }

        val manager = MqttClientManager(context.applicationContext, serverUri, clientId).apply {
            setMqttListener(object : MqttClientManager.MqttListener {
                override fun onAnyIncomingMqttTraffic() {
                    lastMessageAtByServerUri[serverUri] = System.currentTimeMillis()
                    staleTrafficStrikeByServerUri[serverUri] = 0
                }

                override fun onMessageReceived(topic: String, message: String) {
                    // Treat any payload traffic on subscribed topic as broker liveness.
                    // This prevents false "disconnected" flips when payload is non-business.
                    lastMessageAtByServerUri[serverUri] = System.currentTimeMillis()
                    staleTrafficStrikeByServerUri[serverUri] = 0
                    val trimmed = message.trim()
                    android.util.Log.i("MQTT_PAYLOAD_IN", trimmed)
                    logMqttToFileThrottled(
                        key = "MQTT_PAYLOAD_IN:$serverUri",
                        tag = "MQTT_PAYLOAD_IN",
                        message = trimmed,
                        minIntervalMs = 0L
                    )

                    if (isLicenseExpired) return
                    // Enqueue ALL raw payloads first, without any filtering.
                    rawMessageQueue.add(trimmed)
                    // Simple bound to avoid unbounded growth in extreme cases.
                    while (rawMessageQueue.size > 2000) {
                        rawMessageQueue.poll()
                    }

                    // Offload validation/processing to background so MQTT callback stays fast.
                    viewModelScope.launch(Dispatchers.Default) {
                        // Business filters operate on the queued payload content, but serial matching
                        // itself must remain dynamic and come from the keypad records in local storage.
                        if (!trimmed.startsWith("$")) return@launch
                        val extractedKeypadSerial = KeypadPayloadParser.extractKeypadSerial(trimmed)
                        val clearInfo = KeypadPayloadParser.extractClearPayloadInfo(trimmed)
                        val containsClr = trimmed.contains("CLR", ignoreCase = true)
                        // Accept both the newer fixed payloads and the older short keypad format.
                        if (trimmed.length < 24 && extractedKeypadSerial == null && !containsClr) return@launch
                        val isValidKeypadPayload = isValidKeypadMessage(trimmed)

                        // CLR-triggered config refresh path:
                        // e.g. "$02026bCAL0K0007001CLR0*"
                        // Only trigger when keypad serial is valid for this device/customer.
                        if (containsClr) {
                            if (!isValidKeypadPayload) {
                                android.util.Log.d(
                                    "MQTT_PAYLOAD_IN",
                                    "Ignored CLR refresh trigger: Keypad serial mismatch or device not found $trimmed"
                                )
                                return@launch
                            }
                            // Refresh-only payloads are still valid keypad messages and must be audited.
                            saveIncomingMqttPayload(trimmed)
                            _receivedMessage.postValue(trimmed)
                            _lastPayload.postValue(trimmed)
                            val clrSerial = clearInfo?.serial ?: extractedKeypadSerial
                            if (!clrSerial.isNullOrBlank()) {
                                // Route digit matches button_index or keypad_index; only that counter's tokens are cleared.
                                clearTokensForResolvedCounter(
                                    clrSerial,
                                    clearInfo?.routeIndex.orEmpty(),
                                )
                            } else {
                                android.util.Log.w(
                                    "MQTT_PAYLOAD_IN",
                                    "CLR accepted but could not extract keypad serial from payload: $trimmed"
                                )
                            }
                            markPayloadDisplayed(trimmed)
                            requestConfigRefresh(
                                "MQTT refresh trigger detected (payload contains CLR)",
                                forceImmediate = true,
                            )
                            return@launch
                        }

                        // Business rule: ignore responses where the 17th character is '0'
                        if (trimmed[16] == '0') {
                            if (isValidKeypadPayload) {
                                // Keep payload-log behavior aligned with the SRS even when the message exits early.
                                saveIncomingMqttPayload(trimmed)
                            }
                            requestConfigRefresh("MQTT refresh trigger detected (17th char '0')")
                            return@launch
                        }

                        // Validate against Keypad Serial Number (runs on IO dispatcher internally)
                        if (!isValidKeypadPayload) {
                            android.util.Log.d(
                                "MQTT_PAYLOAD_IN",
                                "Ignored message: Keypad serial mismatch or device not found $trimmed"
                            )
                            return@launch
                        }

                        // Save only keypad-validated payloads (received timestamp is stored here).
                        saveIncomingMqttPayload(trimmed)

                        android.util.Log.i(
                            "MQTT_PAYLOAD_IN",
                            "!!! VERIFIED MQTT MESSAGE !!! Topic: [$topic], Payload: $trimmed"
                        )

                        _receivedMessage.postValue(trimmed)
                        _lastPayload.postValue(trimmed)

                        // Parse and route token in the same coroutine to avoid extra launches
                        parseMqttMessage(topic, trimmed)
                    }
                }

                override fun onConnectionStatus(isConnected: Boolean) {
                    updateStatus(serverUri, isConnected)
                    if (isConnected) {
                        // Grace period until first PING/token: treat connect time as last activity.
                        lastMessageAtByServerUri[serverUri] = System.currentTimeMillis()
                        staleReconnectInFlight.remove(serverUri)
                        startMessageLivenessWatchdog()
                        everConnected[serverUri] = true
                        
                        // Check if we can stop the global timer
                        stopConnectTimer()

                        _isAutoRetryExhausted.postValue(false)
                        _errorMessage.postValue("")
                        // Connection success: reset backoff for this broker; UI shows max of others still failing.
                        retryAttempts[serverUri] = 0
                        retryJobs[serverUri]?.cancel()
                        _mqttRetryAttempt.postValue(retryAttempts.values.maxOrNull() ?: 0)

                        startContinuousPublishLoop()
                    } else {
                        // Keep timer running as long as at least one broker is out.
                        startConnectTimer()
                        stopContinuousPublishLoop()
                        scheduleReconnect(serverUri)
                    }
                }

                override fun onError(error: String, code: Int?) {
                    // Log the error to a date-wise file instead of showing it in the UI.
                    val logTag = "MQTT_ERROR"
                    val logMessage = if (code != null) "[$serverUri] $error (Code: $code)" else "[$serverUri] $error"
                    logMqttToFileThrottled(
                        key = "$logTag:$serverUri",
                        tag = logTag,
                        message = logMessage,
                        minIntervalMs = 15_000L
                    )

                    // Stop surfacing specific MQTT errors to the UI per user request.
                    // We keep the LiveData for state if needed, but we clear it or just don't update it.
                    _errorMessage.postValue("")

                    incrementMqttRetryAttemptCounter(serverUri)
                    scheduleReconnect(serverUri)
                }

                override fun onAutoRetryExhausted() {
                    stopConnectTimer()
                    _isAutoRetryExhausted.postValue(true)
                }
            })
        }

        // Start/ensure a reachability monitor for this broker host/port.
        startReachabilityMonitor(serverUri)

        startConnectTimer()
        managers[serverUri] = manager
        manager.subscribe(topic, qos)
        manager.connect(username, password)
    }

    /**
     * Extracts the keypad serial number from a raw MQTT message.
     *
     * Expected format (example): "$02026bCAL0K00071100030*"
     * After removing '$' and '*', we get: "02026bCAL0K00071100030"
     * The actual serial is characters 1..15 (Kotlin endIndex exclusive): "2026bCAL0K0007"
     */
    private fun extractKeypadSerial(message: String): String? {
        return KeypadPayloadParser.extractKeypadSerial(message)
    }

    private fun normalizeKeypadSerial(serial: String): String =
        serial.trim().uppercase(java.util.Locale.ROOT)

    /**
     * True only when [serial] is registered for this TV (MAC + customer) in local DB:
     * `connected_devices` KEYPAD rows and `tv_config.keypadsJson` → `keypad_sn`.
     */
    private suspend fun isRegisteredKeypadSerial(serial: String): Boolean {
        val normalized = normalizeKeypadSerial(serial)
        if (normalized.isBlank()) return false
        return withContext(Dispatchers.IO) {
            ensureKeypadSerialCache()
            stateMutex.withLock { keypadSerials.contains(normalized) }
        }
    }

    /**
     * Payload is processed only when its extracted keypad serial matches the database.
     * All UI updates, token history, payload logs, and token_records go through this gate.
     */
    private suspend fun isValidKeypadMessage(message: String): Boolean {
        val serialInMessage = extractKeypadSerial(message) ?: return false
        val valid = isRegisteredKeypadSerial(serialInMessage)
        if (!valid) {
            android.util.Log.d(
                "MqttViewModel",
                "Keypad serial not in database: ${normalizeKeypadSerial(serialInMessage)} payload=${message.take(80)}"
            )
        }
        return valid
    }

    /**
     * Populates the in-memory keypad serial cache from the connected_devices table.
     * Called on-demand from the IO dispatcher; safe to call repeatedly.
     */
    private suspend fun ensureKeypadSerialCache() {
        if (keypadSerialsLoaded) return

        stateMutex.withLock {
            if (keypadSerialsLoaded) return

            try {
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    android.content.Context.MODE_PRIVATE
                )
                val customerId = String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
                )
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)

                val devices = connectedDeviceDao.getByMacAndCustomer(macAddress, customerId)
                val tvConfig = tvConfigDao.getByMacAndCustomer(macAddress, customerId)

                keypadSerials.clear()
                devices.forEach { device ->
                    if (device.deviceType?.trim().equals("KEYPAD", ignoreCase = true)) {
                        device.serialNumber
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { keypadSerials.add(normalizeKeypadSerial(it)) }

                        // Also inspect configJson once to capture any additional embedded serials.
                        val cfg = device.configJson
                        if (!cfg.isNullOrBlank()) {
                            // Very lightweight scan: look for CAL0K-style serials and cache them.
                            val regex = "20[0-9A-Za-z]{11}".toRegex()
                            regex.findAll(cfg).forEach { m ->
                                keypadSerials.add(normalizeKeypadSerial(m.value))
                            }
                        }
                    }
                }

                tvConfig?.keypadsJson?.let { json ->
                    val type = object : TypeToken<List<KeypadConfig>>() {}.type
                    val keypads = gson.fromJson<List<KeypadConfig>>(json, type) ?: emptyList()
                    keypads.forEach { keypad ->
                        keypad.keypadSn
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { keypadSerials.add(normalizeKeypadSerial(it)) }
                    }
                }

                keypadSerialsLoaded = true
                android.util.Log.i(
                    "MqttViewModel",
                    "Keypad serial cache loaded (${keypadSerials.size} SNs) for MAC=$macAddress customer=$customerId"
                )
            } catch (e: Exception) {
                android.util.Log.w(
                    "MqttViewModel",
                    "Failed to build keypad serial cache: ${e.message}"
                )
            }
        }
    }

    /**
     * Allows other parts of the app (e.g., after TV config refresh) to invalidate the
     * keypad serial cache so it will be rebuilt on the next keypad message.
     */
    fun invalidateKeypadSerialCache() {
        stateMutex.tryLock()?.let { lockAcquired ->
            try {
                if (lockAcquired) {
                    keypadSerials.clear()
                    keypadSerialsLoaded = false
                }
            } finally {
                if (lockAcquired) stateMutex.unlock()
            }
        } ?: run {
            // If mutex is busy, fall back to simple volatile flag reset; cache will be refilled later.
            keypadSerialsLoaded = false
        }
    }

    fun retryConnect(resetAttempts: Boolean = false) {
        _errorMessage.postValue("")
        _isAutoRetryExhausted.postValue(false)
        if (resetAttempts) {
            connectionDetailsMap.keys.forEach { retryAttempts[it] = 0 }
            _mqttRetryAttempt.postValue(0)
        } else {
            var incremented = false
            connectionDetailsMap.keys.forEach { serverUri ->
                val manager = managers[serverUri]
                val isReadyOrInFlight = manager?.isConnected() == true || manager?.isConnectingNow() == true
                if (!isReadyOrInFlight) {
                    val next = (retryAttempts[serverUri] ?: 0) + 1
                    retryAttempts[serverUri] = next
                    android.util.Log.d("MqttViewModel", "Manual retry trigger: incrementing attempt for $serverUri to $next")
                    incremented = true
                }
            }
            if (!incremented) {
                val currentMax = _mqttRetryAttempt.value ?: 0
                _mqttRetryAttempt.postValue(currentMax + 1)
            } else {
                _mqttRetryAttempt.postValue(retryAttempts.values.maxOrNull() ?: 0)
            }
        }

        val anyDisconnected = connectionDetailsMap.keys.any { managers[it]?.isConnected() != true }
        if (anyDisconnected) {
            startConnectTimer()
        }
        connectionDetailsMap.keys.forEach { serverUri ->
            val manager = managers[serverUri]
            when {
                manager?.isConnected() == true -> Unit
                manager?.isConnectingNow() == true -> Unit
                else -> {
                    retryJobs[serverUri]?.cancel()
                    scheduleReconnect(serverUri, immediate = true)
                }
            }
        }
    }

    private fun updateStatus(serverUri: String, isConnected: Boolean) {
        viewModelScope.launch {
            stateMutex.withLock {
                val current = _connectionStatusMap.value?.toMutableMap() ?: mutableMapOf()
                current[serverUri] = isConnected
                _connectionStatusMap.postValue(current)
                val now = System.currentTimeMillis()
                val livenessTimeoutMs = getMqttLivenessTimeoutMs()
                val hasRecentTraffic = lastMessageAtByServerUri.values.any { ts ->
                    now - ts <= livenessTimeoutMs
                }
                // BLUCON should be considered connected if ANY configured broker is connected
                // or if we are still receiving recent MQTT traffic.
                _connectionStatus.postValue(current.values.any { it } || hasRecentTraffic)
            }
        }
    }

    /** Increments per-broker retry count and updates the single UI LiveData (max across brokers). */
    private fun incrementMqttRetryAttemptCounter(serverUri: String) {
        val next = (retryAttempts[serverUri] ?: 0) + 1
        retryAttempts[serverUri] = next
        val display = retryAttempts.values.maxOrNull() ?: next
        _mqttRetryAttempt.postValue(display)
    }

    /**
     * Schedules the next MQTT connect attempt. Complements Paho [MqttConnectOptions.isAutomaticReconnect]
     * (capped at [MqttClientManager] maxReconnectDelay) so drops recover in seconds, not minutes.
     */
    private fun scheduleReconnect(serverUri: String, immediate: Boolean = false) {
        if (connectionDetailsMap[serverUri] == null) return
        if (managers[serverUri]?.isConnected() == true) return
        retryJobs[serverUri]?.cancel()
        val attempt = retryAttempts[serverUri] ?: 0
        val delayMs = if (immediate) {
            0L
        } else {
            // Cold start: everConnected is in-memory only; use fast backoff on app launch too.
            reconnectDelayMs(attempt, afterFirstSuccess = true)
        }

        retryJobs[serverUri] = viewModelScope.launch {
            if (delayMs > 0L) {
                val logMsg = "Reconnecting to $serverUri in ${delayMs}ms (attempt ${attempt + 1})"
                android.util.Log.d("MqttViewModel", logMsg)
                logMqttToFileThrottled(
                    key = "MQTT_RETRY:$serverUri",
                    tag = "MQTT_RETRY",
                    message = "[$serverUri] $logMsg",
                    minIntervalMs = 10_000L,
                )
                delay(delayMs)
            }

            if (managers[serverUri]?.isConnected() == true) {
                android.util.Log.d("MqttViewModel", "Skipping reconnect for $serverUri: already connected.")
                return@launch
            }

            val details = connectionDetailsMap[serverUri] ?: return@launch
            val manager = managers[serverUri]
            if (manager != null) {
                if (!manager.isConnectingNow()) {
                    android.util.Log.i("MqttViewModel", "Reconnect attempt for $serverUri (try ${attempt + 1})")
                    manager.connect(details.username, details.password)
                }
            } else {
                initAndConnect(
                    details.serverUri,
                    details.clientId,
                    details.username,
                    details.password,
                    details.topic,
                    details.qos,
                    getApplication(),
                )
            }
        }
    }

    private fun reconnectDelayMs(attempt: Int, afterFirstSuccess: Boolean): Long {
        val lowNetwork = isLowBandwidthNetwork()
        return if (afterFirstSuccess) {
            when {
                attempt <= 0 -> 0L
                attempt == 1 -> 500L
                attempt == 2 -> 1_500L
                attempt == 3 -> 3_000L
                else -> 5_000L
            }
        } else if (lowNetwork) {
            when {
                attempt <= 0 -> 0L
                attempt == 1 -> 2_000L
                attempt == 2 -> 5_000L
                attempt == 3 -> 10_000L
                attempt == 4 -> 15_000L
                else -> 20_000L
            }
        } else {
            when {
                attempt <= 0 -> 0L
                attempt == 1 -> 1_000L
                attempt == 2 -> 2_000L
                attempt == 3 -> 4_000L
                attempt == 4 -> 6_000L
                else -> 8_000L
            }
        }
    }

    /**
     * Updates broker reachability from MQTT connection status only.
     * Does NOT create any extra TCP sockets - many broker devices (e.g. BLUCON) accept
     * only one connection; a second socket would hit connection limits and drop MQTT.
     */
    private fun startReachabilityMonitor(serverUri: String) {
        if (reachabilityJobs[serverUri]?.isActive == true) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                // Use MQTT connection status only. Never open a new socket - it would
                // compete with the MQTT session and cause connection limit issues.
                val connected = managers[serverUri]?.isConnected() == true
                
                stateMutex.withLock {
                    reachabilityStatusMap[serverUri] = connected
                    // BLUCON/broker is effectively reachable when ANY configured broker is reachable.
                    val anyReachable = reachabilityStatusMap.values.any { it }
                    _isBrokerReachable.postValue(anyReachable)
                }
                
                delay(5000L)
            }
        }

        reachabilityJobs[serverUri] = job
    }

    /**
     * Starts a background loop that publishes keypad serial heartbeats every 10 seconds.
     * Payload format: "$<SERIAL>000000#"
     */
    private fun startContinuousPublishLoop() {
        if (continuousPublishJob?.isActive == true) return

        continuousPublishJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                    ensureKeypadSerialCache()
                    val serials = stateMutex.withLock { keypadSerials.toList() }

                    if (serials.isNotEmpty()) {
                        managers.forEach { (serverUri, manager) ->
                            if (manager.isConnected()) {
                                val details = connectionDetailsMap[serverUri]
                                // Publish heartbeats specifically to the "fr/status" topic
                                val topic = "fr/status"

                                if (topic.isNotEmpty()) {
                                    serials.forEach { serial ->
                                        val payload = "$${serial}000000#"
                                        manager.publish(topic, payload)
                                    }
                                }
                            }
                        }
                    }
                delay(5000)
            }
        }
    }

    /**
     * Stops the continuous publish loop if no managers remain connected.
     */
    private fun stopContinuousPublishLoop() {
        viewModelScope.launch {
            stateMutex.withLock {
                val anyConnected = managers.values.any { it.isConnected() }
                if (!anyConnected) {
                    continuousPublishJob?.cancel()
                    continuousPublishJob = null
                }
            }
        }
    }

    private fun removeManager(serverUri: String) {
        viewModelScope.launch {
            stateMutex.withLock {
                detachBrokerLocked(serverUri)
            }
        }
    }

    private fun detachBrokerSync(serverUri: String) {
        retryJobs[serverUri]?.cancel()
        retryJobs.remove(serverUri)
        try {
            managers[serverUri]?.close()
        } catch (_: Exception) {
        }
        managers.remove(serverUri)
        connectionDetailsMap.remove(serverUri)
        reachabilityJobs[serverUri]?.cancel()
        reachabilityJobs.remove(serverUri)
        staleReconnectInFlight.remove(serverUri)
        lastMessageAtByServerUri.remove(serverUri)
        staleTrafficStrikeByServerUri.remove(serverUri)
        val current = _connectionStatusMap.value?.toMutableMap() ?: mutableMapOf()
        current[serverUri] = false
        _connectionStatusMap.postValue(current)
        val anyConnected = current.values.any { it }
        val now = System.currentTimeMillis()
        val hasRecentTraffic = lastMessageAtByServerUri.values.any { ts ->
            now - ts <= getMqttLivenessTimeoutMs()
        }
        _connectionStatus.postValue(anyConnected || hasRecentTraffic)
    }

    private fun detachBrokerLocked(serverUri: String) {
        detachBrokerSync(serverUri)
    }

    private fun BrokerEndpoint.toMqttConnectionDetails(): MqttConnectionDetails =
        MqttConnectionDetails(serverUri, clientId, username, password, topic, qos)

    suspend fun processTokenUpdate(counter: String, token: String): Boolean {
        val trimmedCounter = counter.trim()
        val trimmedToken = token.trim()

        if (trimmedToken == "0" || trimmedToken.isBlank() || trimmedCounter == "0" || trimmedCounter.isBlank()) return false
        if (trimmedToken.contains("CAL", ignoreCase = true)) return false

        return stateMutex.withLock {

            val list = (internalTokenMap[trimmedCounter] ?: emptyList()).toMutableList()
            val existingPosition = list.indexOf(trimmedToken)

            // If already at top, per user request: DO NOT announce unless > 10s passed
            if (existingPosition == 0) {
                val lastTime = announcedTokenTimestamps["${trimmedCounter}_$trimmedToken"] ?: 0L
                if (System.currentTimeMillis() - lastTime > 10000L) {
                    // Re-announce
                    persistToken(trimmedCounter, trimmedToken)
                    return@withLock true
                }
                return@withLock false
            }

            if (existingPosition > 0) {
                list.removeAt(existingPosition)
                // Clear the "announced" flag before moving to top, so it can be re-announced
                announcedTokenTimestamps.remove("${trimmedCounter}_$trimmedToken")
            }

            list.add(0, trimmedToken)
            val updatedList = trimTokenHistory(list)
            internalTokenMap[trimmedCounter] = updatedList
            _tokensPerCounter.postValue(internalTokenMap.toMap())

            persistToken(trimmedCounter, trimmedToken)
            true // Announcement should happen
        }
    }

    /**
     * Atomically add token to both primary and fallback keys in one map update.
     * Prevents race where two processTokenUpdate calls overwrite each other.
     *
     * [TokenUiProcessResult.playCueUi] is true whenever the token lists or VIP overlay changed
     * so the UI layer should play the chime and publish (not only on primary-key TTS rules).
     */
    suspend fun processTokenUpdateForKeys(
        primaryKey: String,
        token: String,
        fallbackKey: String?,
        publishImmediately: Boolean = true,
        isVipEmergency: Boolean = false,
    ): TokenUiProcessResult {
        val trimmedPrimary = primaryKey.trim()
        val trimmedToken = token.trim()
        val trimmedFallback = fallbackKey?.trim()

        if (trimmedToken == "0" || trimmedToken.isBlank() || trimmedPrimary == "0" || trimmedPrimary.isBlank()) {
            return TokenUiProcessResult(playCueUi = false, speakTokenAnnouncement = false)
        }
        if (trimmedToken.contains("CAL", ignoreCase = true)) {
            return TokenUiProcessResult(playCueUi = false, speakTokenAnnouncement = false)
        }

        return stateMutex.withLock {

            val keysToUpdate = mutableListOf(trimmedPrimary)
            if (trimmedFallback != null && trimmedFallback != trimmedPrimary) keysToUpdate.add(
                trimmedFallback
            )

            var shouldAnnounce = false
            var mapChanged = false
            val vipBefore = keysToUpdate.associateWith { vipEmergencyTopTokenByKey[it] }

            for (key in keysToUpdate) {
                val priorStored = internalTokenMap[key] ?: emptyList()
                val list = priorStored.toMutableList()
                // When normal tokens arrive after a special message, remove special-message placeholders.
                list.removeAll { it.contains("__MSG__", ignoreCase = false) }
                val existingPosition = list.indexOf(trimmedToken)

                // If it's already the most recent for this key
                if (existingPosition == 0) {
                    if (key == trimmedPrimary) {
                        val lastTime = announcedTokenTimestamps["${key}_$trimmedToken"] ?: 0L
                        if (System.currentTimeMillis() - lastTime > 10000L) {
                            shouldAnnounce = true
                        }
                    }
                    if (list != priorStored) {
                        internalTokenMap[key] = trimTokenHistory(list)
                        mapChanged = true
                    }
                    continue
                }

                // If it's the primary key and it's not at top, it's a candidate for announcement
                if (key == trimmedPrimary) {
                    shouldAnnounce = true
                }

                if (existingPosition > 0) {
                    list.removeAt(existingPosition)
                    // Clear flag so it can be re-announced since its position is changing
                    announcedTokenTimestamps.remove("${key}_$trimmedToken")
                }

                list.add(0, trimmedToken)
                internalTokenMap[key] = trimTokenHistory(list)
                persistToken(key, trimmedToken)
                mapChanged = true
            }

            for (key in keysToUpdate) {
                if (isVipEmergency) {
                    vipEmergencyTopTokenByKey[key] = trimmedToken
                } else {
                    vipEmergencyTopTokenByKey.remove(key)
                }
            }
            postVipEmergencyTopTokenSnapshot()

            val vipChanged = keysToUpdate.any { k ->
                vipBefore[k] != vipEmergencyTopTokenByKey[k]
            }

            val playCueUi = mapChanged || shouldAnnounce || vipChanged

            if (playCueUi && publishImmediately) {
                publishTokensSnapshot()
            }

            TokenUiProcessResult(
                playCueUi = playCueUi,
                speakTokenAnnouncement = shouldAnnounce,
            )
        }
    }

    /**
     * Shows a protocol-C / special-message token at the top of the counter list without
     * clearing previously called normal tokens. Prior `__MSG__` placeholders are removed first.
     */
    suspend fun replaceTokenForKeys(
        primaryKey: String,
        token: String,
        fallbackKey: String?,
        publishImmediately: Boolean = true
    ): Boolean {
        val trimmedPrimary = primaryKey.trim()
        val trimmedToken = token.trim()
        val trimmedFallback = fallbackKey?.trim()
        if (trimmedPrimary.isBlank() || trimmedToken.isBlank() || trimmedPrimary == "0") return false

        return stateMutex.withLock {
            val keysToUpdate = mutableListOf(trimmedPrimary)
            if (!trimmedFallback.isNullOrBlank() && trimmedFallback != trimmedPrimary) {
                keysToUpdate.add(trimmedFallback)
            }

            val isSpecialOverlay = trimmedToken.contains("__MSG__", ignoreCase = false)
            keysToUpdate.forEach { key ->
                val list = (internalTokenMap[key] ?: emptyList()).toMutableList()
                list.removeAll { it.contains("__MSG__", ignoreCase = false) }
                val existingPosition = list.indexOf(trimmedToken)
                if (existingPosition > 0) {
                    list.removeAt(existingPosition)
                }
                list.add(0, trimmedToken)
                internalTokenMap[key] = trimTokenHistory(list)
                announcedTokenTimestamps.remove("${key}_$trimmedToken")
                // Special messages are ephemeral UI overlays; do not rewrite persisted token history.
                if (!isSpecialOverlay) {
                    persistToken(key, trimmedToken)
                }
                vipEmergencyTopTokenByKey.remove(key)
            }
            postVipEmergencyTopTokenSnapshot()
            if (publishImmediately) {
                publishTokensSnapshot()
            }
            true
        }
    }

    private fun persistToken(key: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tokenHistoryRepo.saveToken(key, token)
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "Persist failed: ${e.message}")
            }
        }
    }

    /**
     * Parses a verified MQTT message and enqueues token updates for processing.
     * Runs on a background dispatcher (caller responsibility).
     */
    private suspend fun parseMqttMessage(topic: String, message: String) {
        try {
            val payloadSerial = extractKeypadSerial(message)?.let { normalizeKeypadSerial(it) }
            if (payloadSerial.isNullOrBlank() || !isRegisteredKeypadSerial(payloadSerial)) {
                android.util.Log.d(
                    "MQTT_PAYLOAD_IN",
                    "parseMqttMessage skipped: keypad serial not registered (${payloadSerial ?: "missing"})"
                )
                return
            }

            val fixed = SemanticMqttParser.parseFixedPayload(message)
            if (fixed != null) {
                val fixedSerial = normalizeKeypadSerial(fixed.serial)
                if (fixedSerial != payloadSerial || !isRegisteredKeypadSerial(fixedSerial)) {
                    android.util.Log.w(
                        "MQTT_PAYLOAD_IN",
                        "parseMqttMessage skipped: fixed-protocol serial mismatch payload=$payloadSerial fixed=$fixedSerial"
                    )
                    return
                }
                val identity = resolveCounterIdentityFromSerial(fixed.serial)
                if (identity == null) {
                    android.util.Log.w(
                        "MQTT_PAYLOAD_IN",
                        "No counter for keypad_sn=${normalizeKeypadSerial(fixed.serial)} — UI not updated"
                    )
                    return
                }
                val routedCounter = identity.storageKey
                val token = if (fixed.action == SemanticMqttParser.PayloadAction.REPLACE_COUNTER) {
                    resolveButtonStringValue(fixed.serial, fixed.buttonStringId, fixed.token) ?: fixed.token
                } else {
                    fixed.token
                }

                // Keep detailed log for all valid fixed-protocol payloads.
                saveTokenRecord(message, identity.counterLabel, token)

                when (fixed.action) {
                    SemanticMqttParser.PayloadAction.DB_ONLY -> return
                    SemanticMqttParser.PayloadAction.REPLACE_COUNTER -> {
                        if (shouldSuppressRepeatedPayload(message)) return
                        announcementQueueSize.incrementAndGet()
                        tokenReplaceChannel.send(TokenUiEvent(routedCounter, token, message))
                        return
                    }
                    SemanticMqttParser.PayloadAction.NORMAL -> {
                        if (shouldSuppressRepeatedPayload(message)) return
                        announcementQueueSize.incrementAndGet()
                        tokenUpdateChannel.send(
                            TokenUiEvent(
                                routedCounter,
                                token,
                                message,
                                isVipEmergency = fixed.isVipEmergency,
                            )
                        )
                        return
                    }
                }
            }

            // Semantic parsing is pure/CPU-bound; keep it on Default.
            val result = SemanticMqttParser.parse(message, topic)
            if (result != null) {
                val (_, token) = result
                if (token == "0" || token.isBlank()) return
                val identity = resolveCounterIdentityFromSerial(payloadSerial)
                    ?: return
                markKeypadSeen(identity.storageKey)
                markDispenseSeen(identity.storageKey)
                val key = "${identity.storageKey.trim()}_${token.trim()}"
                val now = System.currentTimeMillis()
                val lastQueued = queuedTokenTimestamps[key] ?: 0L
                if (now - lastQueued < 10000L) return
                queuedTokenTimestamps[key] = now
                if (queuedTokenTimestamps.size > 500) {
                    queuedTokenTimestamps.entries.removeIf { now - it.value > 60000L }
                }
                saveTokenRecord(message, identity.counterLabel, token)
                if (shouldSuppressRepeatedPayload(message)) return
                announcementQueueSize.incrementAndGet()
                tokenUpdateChannel.send(
                    TokenUiEvent(
                        counter = identity.storageKey,
                        token = token,
                        payload = message,
                    )
                )
            }
        } catch (e: Exception) {
            // Swallow and log – a bad payload should never break the stream.
            android.util.Log.w(
                "MQTT_PAYLOAD_IN",
                "Failed to parse MQTT message '$message' on topic '$topic': ${e.message}"
            )
        }
    }

    private fun normalizeButtonKey(counter: String?): String? {
        val raw = counter?.trim().orEmpty()
        if (raw.isEmpty()) return null
        raw.toIntOrNull()?.let { return it.toString() }
        if (raw.length == 1 && raw[0].isLetter()) {
            val idx = raw[0].uppercaseChar() - 'A' + 1
            if (idx in 1..26) return idx.toString()
        }
        return null
    }

    /**
     * Deduplicates identical raw payload announcements within a rolling 10-second window.
     * Keeps one announcement for repeated packets while still allowing future calls.
     */
    private fun shouldSuppressRepeatedPayload(payload: String): Boolean {
        val key = payload.trim()
        if (key.isEmpty()) return false
        val now = System.currentTimeMillis()
        val last = queuedPayloadTimestamps[key] ?: 0L
        if (now - last < 10_000L) {
            return true
        }
        queuedPayloadTimestamps[key] = now
        if (queuedPayloadTimestamps.size > 2000) {
            queuedPayloadTimestamps.entries.removeIf { now - it.value > 60_000L }
        }
        return false
    }

    private suspend fun resolveCounterRouteFromSerial(serial: String, routeIndex: String): String? {
        return resolveCounterIdentityFromSerial(serial, routeIndex)?.storageKey
    }

    /**
     * Resolves the TV counter for token / special-message UI using [serial] only.
     * Fixed-protocol index 18 (0-based 17) is part of the keypad SN, not `keypad_index`.
     */
    private suspend fun resolveCounterIdentityFromSerial(serial: String): ResolvedCounterIdentity? {
        return resolveCounterIdentityFromSerial(serial, routeIndex = "")
    }

    private suspend fun resolveCounterIdentityFromSerial(
        serial: String,
        routeIndex: String,
    ): ResolvedCounterIdentity? {
        if (serial.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    android.content.Context.MODE_PRIVATE
                )
                val customerId = String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
                )
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
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
                        "resolveCounter: no keypadsJson entry for keypad_sn=${normalizeKeypadSerial(serial)}"
                    )
                    return@withContext null
                }

                val configs = keypad.counters.orEmpty()
                if (configs.isEmpty()) {
                    android.util.Log.d(
                        "MqttViewModel",
                        "resolveCounter: no counters[] under keypad_sn=${normalizeKeypadSerial(serial)}"
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
                            "resolveCounter: no counter with keypad_index=$route under keypad_sn=${normalizeKeypadSerial(serial)}"
                        } else {
                            "resolveCounter: no counter row for keypad_sn=${normalizeKeypadSerial(serial)}"
                        }
                    )
                    return@withContext null
                }

                val matched = counters.firstOrNull { counter ->
                    counterEntityMatchesKeypadCounter(counter, keypadCounter)
                } ?: run {
                    android.util.Log.d(
                        "MqttViewModel",
                        "resolveCounter: counters[] row missing for keypad_sn=${normalizeKeypadSerial(serial)}"
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
                    "resolveCounter: SN=${normalizeKeypadSerial(serial)}" +
                        (if (route.isNotEmpty()) " route=$route" else "") +
                        " UI key(button_index)=$storageKey -> counter=$counterLabel"
                )

                ResolvedCounterIdentity(
                    storageKey = storageKey,
                    counterLabel = counterLabel,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun counterMatchesLabel(counter: CounterEntity, label: String): Boolean {
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

    private fun counterEntityMatchesKeypadCounter(
        counter: CounterEntity,
        keypadCounter: CounterConfig,
    ): Boolean {
        if (routeMatches(counter.keypadIndex, keypadCounter.keypadIndex.orEmpty())) return true
        return counter.counterId?.trim().equals(keypadCounter.counterId?.trim(), ignoreCase = true) == true ||
            counter.name?.trim().equals(keypadCounter.name?.trim(), ignoreCase = true) == true ||
            counter.defaultName?.trim().equals(keypadCounter.defaultName?.trim(), ignoreCase = true) == true
    }

    private fun routeMatches(candidate: String?, routeIndex: String): Boolean {
        val left = candidate?.trim().orEmpty()
        val right = routeIndex.trim()
        if (left.isBlank() || right.isBlank()) return false
        if (left.equals(right, ignoreCase = true)) return true

        val leftNumber = left.toIntOrNull()
        val rightNumber = right.toIntOrNull()
        return leftNumber != null && rightNumber != null && leftNumber == rightNumber
    }

    /** CLR route digit from payload (char before `CLR`) — matches TV config `button_index` or `keypad_index`. */
    private fun counterConfigMatchesRoute(cc: CounterConfig, route: String): Boolean {
        if (routeMatches(cc.keypadIndex, route)) return true
        return routeMatches(cc.buttonIndex?.toString(), route)
    }

    private fun counterEntityMatchesRoute(counter: CounterEntity, route: String): Boolean {
        if (routeMatches(counter.keypadIndex, route)) return true
        return routeMatches(counter.buttonIndex?.toString(), route)
    }

    private suspend fun resolveButtonStringValue(
        serial: String,
        buttonStringId: String,
        tokenFallback: String
    ): String? {
        if (serial.isBlank() || buttonStringId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    android.content.Context.MODE_PRIVATE
                )
                val customerId = String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
                )
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
                val cfg = tvConfigDao.getByMacAndCustomer(macAddress, customerId) ?: return@withContext null
                val json = cfg.keypadsJson ?: return@withContext null
                val type = object : TypeToken<List<KeypadConfig>>() {}.type
                val keypads: List<KeypadConfig> = gson.fromJson(json, type) ?: emptyList()
                val keypad = keypads.firstOrNull {
                    it.keypadSn?.trim().equals(serial.trim(), ignoreCase = true)
                } ?: return@withContext null

                val value = keypad.buttonStrings
                    ?.firstOrNull {
                        it.id?.trim() == buttonStringId.trim() || it.id?.trim() == tokenFallback.trim()
                    }
                    ?.value
                    ?.trim()
                value?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun markPayloadDisplayed(payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mqttPayloadLogRepo.markDisplayed(payload)
                scheduleMqttPayloadSync(force = true)
            } catch (_: Exception) {
            }
        }
    }

    private fun markDispenseSeen(buttonKey: String) {
        val now = System.currentTimeMillis()
        lastDispenseSeenAtByButton[buttonKey] = now
        val current = _dispenseConnectedByButton.value?.toMutableMap() ?: mutableMapOf()
        if (current[buttonKey] != true) {
            current[buttonKey] = true
            _dispenseConnectedByButton.postValue(current)
        }
        startIndicatorWatchdog()
    }

    private fun markKeypadSeen(buttonKey: String) {
        val now = System.currentTimeMillis()
        lastKeypadSeenAtByButton[buttonKey] = now
        val current = _keypadConnectedByButton.value?.toMutableMap() ?: mutableMapOf()
        if (current[buttonKey] != true) {
            current[buttonKey] = true
            _keypadConnectedByButton.postValue(current)
        }
        startIndicatorWatchdog()
    }

    private fun startIndicatorWatchdog() {
        if (indicatorWatchdogJob?.isActive == true) return
        indicatorWatchdogJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val dispense = mutableMapOf<String, Boolean>()
                val keypad = mutableMapOf<String, Boolean>()

                lastDispenseSeenAtByButton.forEach { (button, ts) ->
                    if (now - ts <= INDICATOR_STALE_TIMEOUT_MS) {
                        dispense[button] = true
                    }
                }
                lastKeypadSeenAtByButton.forEach { (button, ts) ->
                    if (now - ts <= INDICATOR_STALE_TIMEOUT_MS) {
                        keypad[button] = true
                    }
                }

                _dispenseConnectedByButton.postValue(dispense)
                _keypadConnectedByButton.postValue(keypad)
            }
        }
    }

    private fun saveTokenRecord(message: String, counterKey: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serial = extractKeypadSerial(message)?.let { normalizeKeypadSerial(it) }
                if (serial.isNullOrBlank() || !isRegisteredKeypadSerial(serial)) {
                    android.util.Log.d(
                        "MqttViewModel",
                        "saveTokenRecord skipped: keypad serial not registered ($serial)"
                    )
                    return@launch
                }
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    android.content.Context.MODE_PRIVATE
                )
                val customerId = String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
                )
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
                
                // Find counter details for ID and Name
                val counters = AppDatabase.getInstance(app).counterDao().getByMacAndCustomer(macAddress, customerId)
                val matchingCounter = counters.find { counterMatchesLabel(it, counterKey) }
                
                val counterId = matchingCounter?.counterId?.toIntOrNull() ?: 0
                val counterName = matchingCounter?.name ?: matchingCounter?.defaultName ?: counterKey
                
                // Extract called_time from payload (e.g., "$02026bCAL0K00071100030*")
                // In this protocol, the bytes after serial might contain time info, 
                // but if not explicit, we use the message receive time from the payload if possible, 
                // or just pass the raw "CAL0K" part as a placeholder if requested.
                // The user requested 'called_time' as a string.
                val calledTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

                tokenRecordRepo.saveRecord(
                    macAddress = macAddress,
                    counterId = counterId,
                    counterName = counterName,
                    tokenNumber = token,
                    keypadSerialNumber = serial,
                    calledTime = calledTime
                )
                // Same audit path as normal tokens: upload raw MQTT payload after record is stored.
                scheduleMqttPayloadSync()
            } catch (e: Exception) {
                android.util.Log.e("MqttViewModel", "Failed to save detailed token record", e)
            }
        }
    }

    private fun saveIncomingMqttPayload(payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mqttPayloadLogRepo.saveIncomingPayload(payload)
                scheduleMqttPayloadSync()
            } catch (e: Exception) {
                android.util.Log.e("MqttViewModel", "Failed to save MQTT payload log", e)
            }
        }
    }

    /**
     * Uploads pending MQTT payload logs to `api/external/token-report`.
     * [force] is used after [markPayloadDisplayed] so displayed timestamps reach the server.
     */
    private fun scheduleMqttPayloadSync(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPayloadSyncAtMs < MQTT_PAYLOAD_SYNC_DEBOUNCE_MS) return
        lastPayloadSyncAtMs = now
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mqttPayloadLogRepo.syncPendingPayloads()
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "MQTT payload sync failed: ${e.message}")
            }
        }
    }

    /**
     * If the subscribed topic receives no message (including broker "PING") for longer than
     * [MQTT_MESSAGE_LIVENESS_TIMEOUT_MS], assume BLUCON is dead and force a reconnect.
     */
    private fun startMessageLivenessWatchdog() {
        if (messageLivenessWatchdogJob?.isActive == true) return
        messageLivenessWatchdogJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val timeoutMs = getMqttLivenessTimeoutMs()
                val strikeThreshold = getMqttStaleStrikeThreshold()
                for ((serverUri, manager) in managers.toMap()) {
                    if (!manager.isConnected()) continue
                    val last = lastMessageAtByServerUri[serverUri] ?: 0L
                    if (last == 0L) continue
                    val silenceMs = now - last
                    if (silenceMs > timeoutMs) {
                        // Count stale "strikes" by full timeout windows, not every watchdog tick.
                        // Example with 30s timeout: 1 strike at >30s, 2 at >60s, 3 at >90s, 4 at >120s.
                        val strikes = (silenceMs / timeoutMs).toInt()
                        staleTrafficStrikeByServerUri[serverUri] = strikes
                        android.util.Log.d(
                            "MqttLivenessDebug",
                            "server=$serverUri silenceMs=$silenceMs strikeWindow=$strikes/" +
                                "$strikeThreshold connected=${manager.isConnected()} lowNetwork=${isLowBandwidthNetwork()}"
                        )
                        if (strikes >= strikeThreshold) {
                            forceReconnectDueToStaleTraffic(serverUri)
                        } else {
                            android.util.Log.d(
                                "MqttViewModel",
                                "Stale MQTT traffic for $serverUri (${silenceMs}ms). " +
                                    "Strike $strikes/$strikeThreshold; skipping reconnect."
                            )
                        }
                    } else {
                        staleTrafficStrikeByServerUri[serverUri] = 0
                    }
                }
            }
        }
    }

    private fun forceReconnectDueToStaleTraffic(serverUri: String) {
        val details = connectionDetailsMap[serverUri] ?: return
        if (!staleReconnectInFlight.add(serverUri)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timeoutMs = getMqttLivenessTimeoutMs()
                android.util.Log.w(
                    "MqttViewModel",
                    "No MQTT traffic for $serverUri for >${timeoutMs / 1000}s; forcing reconnect"
                )
                logMqttToFileThrottled(
                    key = "MqttLiveness:$serverUri",
                    tag = "MqttLiveness",
                    message = "No MQTT traffic for $serverUri for >${timeoutMs / 1000}s; forcing reconnect",
                    minIntervalMs = 30_000L
                )
                incrementMqttRetryAttemptCounter(serverUri)
                lastMessageAtByServerUri.remove(serverUri)
                staleTrafficStrikeByServerUri.remove(serverUri)
                stateMutex.withLock {
                    managers[serverUri]?.close()
                    managers.remove(serverUri)
                    reachabilityJobs[serverUri]?.cancel()
                    reachabilityJobs.remove(serverUri)
                    val current = _connectionStatusMap.value?.toMutableMap() ?: mutableMapOf()
                    current[serverUri] = false
                    _connectionStatusMap.postValue(current)
                    val now = System.currentTimeMillis()
                    val hasRecentTraffic = lastMessageAtByServerUri.values.any { ts ->
                        now - ts <= timeoutMs
                    }
                    _connectionStatus.postValue(current.values.any { it } || hasRecentTraffic)
                }
                startConnectTimer()
                initAndConnect(
                    details.serverUri,
                    details.clientId,
                    details.username,
                    details.password,
                    details.topic,
                    details.qos,
                    getApplication()
                )
            } finally {
                // initAndConnect returns before async connect finishes; debounce so we do not
                // stack multiple stale reconnects while the new socket is still handshaking.
                delay(2_000L)
                staleReconnectInFlight.remove(serverUri)
            }
        }
    }

    private fun logMqttToFileThrottled(
        key: String,
        tag: String,
        message: String,
        minIntervalMs: Long
    ) {
        val now = System.currentTimeMillis()
        val last = mqttLogThrottleByKey[key]
        if (last != null && now - last < minIntervalMs) return
        mqttLogThrottleByKey[key] = now
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileLogger.logError(getApplication(), tag, message)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildMqttPayloadPreview(payload: String): String {
        if (payload.isBlank()) return "<empty>"
        val singleLine = payload
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        return if (singleLine.length <= MAX_MQTT_LOG_PAYLOAD_CHARS) {
            singleLine
        } else {
            singleLine.take(MAX_MQTT_LOG_PAYLOAD_CHARS) + "...(truncated)"
        }
    }

    companion object {
        private const val MQTT_MESSAGE_LIVENESS_TIMEOUT_MS = 90_000L
        private const val MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT = 2
        private const val MQTT_MESSAGE_LIVENESS_TIMEOUT_MS_LOW_NETWORK = 120_000L
        private const val MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT_LOW_NETWORK = 3
        private const val CONFIG_REFRESH_DEBOUNCE_MS = 15_000L
        private const val INDICATOR_STALE_TIMEOUT_MS = 300_000L
        private const val MAX_MQTT_LOG_PAYLOAD_CHARS = 220
        private const val MQTT_PAYLOAD_SYNC_DEBOUNCE_MS = 15_000L
    }

    private fun isLowBandwidthNetwork(): Boolean {
        return try {
            val cm = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            val downKbps = caps.linkDownstreamBandwidthKbps
            val onCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val downIsLow = downKbps in 1..4_000
            onCellular || downIsLow
        } catch (_: Exception) {
            false
        }
    }

    private fun getMqttLivenessTimeoutMs(): Long {
        return if (isLowBandwidthNetwork()) {
            MQTT_MESSAGE_LIVENESS_TIMEOUT_MS_LOW_NETWORK
        } else {
            MQTT_MESSAGE_LIVENESS_TIMEOUT_MS
        }
    }

    private fun getMqttStaleStrikeThreshold(): Int {
        return if (isLowBandwidthNetwork()) {
            MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT_LOW_NETWORK
        } else {
            MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT
        }
    }

    private fun requestConfigRefresh(reason: String, forceImmediate: Boolean = false) {
        if (forceImmediate) {
            lastConfigRefreshAtMs = System.currentTimeMillis()
            android.util.Log.i("MqttViewModel", "$reason; requesting immediate TV config refresh (CLR)")
            configRefreshRequests.trySend(TvConfigRefreshSignal(reason, forceImmediate = true))
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastConfigRefreshAtMs >= CONFIG_REFRESH_DEBOUNCE_MS) {
            lastConfigRefreshAtMs = now
            android.util.Log.i("MqttViewModel", "$reason; requesting TV config refresh")
            configRefreshRequests.trySend(TvConfigRefreshSignal(reason, forceImmediate = false))
        }
    }

    /** Stops MQTT connections, timers, and publish loops when the app is backgrounded. */
    fun stopAllBackgroundWork() {
        messageLivenessWatchdogJob?.cancel()
        messageLivenessWatchdogJob = null
        indicatorWatchdogJob?.cancel()
        indicatorWatchdogJob = null
        timerJob?.cancel()
        timerJob = null
        continuousPublishJob?.cancel()
        continuousPublishJob = null
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
        reachabilityJobs.values.forEach { it.cancel() }
        reachabilityJobs.clear()
        staleReconnectInFlight.clear()
        val toClose = managers.values.toList()
        managers.clear()
        connectionDetailsMap.clear()
        _connectionStatus.postValue(false)
        _connectionStatusMap.postValue(emptyMap())
        _isConnectingToMqtt.postValue(false)
        announcementQueueSize.set(0)
        viewModelScope.launch(Dispatchers.IO) {
            toClose.forEach { runCatching { it.close() } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllBackgroundWork()
    }
}

/**
 * Drives [TokenDisplayViewModel.loadData]. [forceImmediate] is used for CLR so config refresh is not
 * debounced or dropped while another sync is active; cache/UI are not cleared before fetch.
 */
data class TvConfigRefreshSignal(
    val reason: String,
    val forceImmediate: Boolean = false,
)
