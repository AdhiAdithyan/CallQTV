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
import com.softland.callqtv.data.repository.MqttClientManager
import com.softland.callqtv.data.repository.MqttPayloadLogRepository
import com.softland.callqtv.data.repository.TokenHistoryRepository
import com.softland.callqtv.utils.FileLogger
import com.softland.callqtv.utils.SemanticMqttParser
import com.softland.callqtv.viewmodel.mqtt.MqttBrokerCallbacks
import com.softland.callqtv.viewmodel.mqtt.MqttBrokerConnector
import com.softland.callqtv.viewmodel.mqtt.MqttBrokerListenerFactory
import com.softland.callqtv.viewmodel.mqtt.MqttClrTokenOperations
import com.softland.callqtv.viewmodel.mqtt.mqttClrPayloadMatchesResolvedCounter
import com.softland.callqtv.viewmodel.mqtt.MqttConnectionDetails
import com.softland.callqtv.viewmodel.mqtt.MqttCounterIdentityResolver
import com.softland.callqtv.viewmodel.mqtt.MqttInboundPayloadRouter
import com.softland.callqtv.viewmodel.mqtt.MqttKeypadSerialRegistry
import com.softland.callqtv.viewmodel.mqtt.MqttRawPayloadGate
import com.softland.callqtv.viewmodel.mqtt.MqttReconnectPolicy
import com.softland.callqtv.viewmodel.mqtt.MqttVerifiedMessageParser
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

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
     * [speakTokenAnnouncement]: primary-key token should be spoken (each queued UI event).
     */
    data class TokenUiProcessResult(
        val playCueUi: Boolean,
        val speakTokenAnnouncement: Boolean,
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

    private val keypadSerialRegistry by lazy {
        MqttKeypadSerialRegistry(getApplication(), connectedDeviceDao, tvConfigDao, gson, stateMutex)
    }

    private val counterIdentityResolver by lazy {
        MqttCounterIdentityResolver(
            getApplication(),
            counterDao,
            tvConfigDao,
            gson,
            counterRouteCache,
        ) { routingCacheScope() }
    }

    private val inboundPayloadRouter by lazy {
        MqttInboundPayloadRouter(keypadSerialRegistry, inboundPayloadHost)
    }

    private val verifiedMessageParser by lazy {
        MqttVerifiedMessageParser(keypadSerialRegistry, counterIdentityResolver, verifiedMessageHost)
    }

    private val clrTokenOperations by lazy {
        MqttClrTokenOperations(counterIdentityResolver, clrTokenHost)
    }

    private val inboundPayloadHost = object : MqttInboundPayloadRouter.Host {
        override suspend fun isValidKeypadMessage(message: String): Boolean =
            keypadSerialRegistry.isValidMessage(message)

        override fun saveIncomingMqttPayload(trimmed: String) {
            this@MqttViewModel.saveIncomingMqttPayload(trimmed)
        }

        override fun onVerifiedPayload(topic: String, trimmed: String) {
            _receivedMessage.postValue(trimmed)
            _lastPayload.postValue(trimmed)
            viewModelScope.launch(Dispatchers.Default) {
                verifiedMessageParser.parse(topic, trimmed)
            }
        }

        override suspend fun clearTokensForClr(serial: String, routeIndex: String) {
            clrTokenOperations.clearTokensForResolvedCounter(serial, routeIndex)
        }

        override fun markPayloadDisplayed(payload: String) {
            this@MqttViewModel.markPayloadDisplayed(payload)
        }

        override fun requestConfigRefresh(reason: String, forceImmediate: Boolean) {
            this@MqttViewModel.requestConfigRefresh(reason, forceImmediate)
        }
    }

    private val verifiedMessageHost = object : MqttVerifiedMessageParser.Host {
        override fun shouldSuppressRepeatedPayload(payload: String): Boolean =
            this@MqttViewModel.shouldSuppressRepeatedPayload(payload)

        override fun enqueueTokenUpdate(event: TokenUiEvent) {
            enqueueTokenUiEvent(tokenUpdateChannel, event)
        }

        override fun enqueueTokenReplace(event: TokenUiEvent) {
            enqueueTokenUiEvent(tokenReplaceChannel, event)
        }

        override suspend fun saveTokenRecord(message: String, counterKey: String, token: String) {
            this@MqttViewModel.saveTokenRecord(message, counterKey, token)
        }

        override fun markKeypadSeen(buttonKey: String) {
            this@MqttViewModel.markKeypadSeen(buttonKey)
        }

        override fun markDispenseSeen(buttonKey: String) {
            this@MqttViewModel.markDispenseSeen(buttonKey)
        }

        override fun shouldSkipQueuedToken(key: String, now: Long): Boolean {
            val lastQueued = queuedTokenTimestamps[key] ?: 0L
            // Short debounce only; each distinct queued event is announced in order on the UI.
            return now - lastQueued < 1_500L
        }

        override fun recordQueuedToken(key: String, now: Long) {
            queuedTokenTimestamps[key] = now
            if (queuedTokenTimestamps.size > 500) {
                queuedTokenTimestamps.entries.removeIf { now - it.value > 60000L }
            }
        }
    }

    private val clrTokenHost = object : MqttClrTokenOperations.Host {
        override suspend fun resolveIdentity(serial: String, routeIndex: String) =
            counterIdentityResolver.resolve(serial, routeIndex)

        override suspend fun applyClear(keysToClear: Set<String>, serial: String, routeIndex: String) {
            stateMutex.withLock {
                var changed = false
                keysToClear.forEach { key ->
                    changed = internalTokenMap.remove(key) != null || changed
                    announcedTokenTimestamps.entries.removeIf { it.key.startsWith("${key}_") }
                    queuedTokenTimestamps.entries.removeIf { it.key.startsWith("${key}_") }
                    vipEmergencyTokensByKey.remove(key)
                }
                postVipEmergencyTokensSnapshot()
                tokenHistoryRepo.clearCounterKeys(keysToClear.toList())
                val route = routeIndex.trim()
                queuedPayloadTimestamps.entries.removeIf { entry ->
                    val payloadSerial = KeypadPayloadParser.extractKeypadSerial(entry.key)
                        ?: return@removeIf false
                    if (!payloadSerial.trim().equals(serial.trim(), ignoreCase = true)) return@removeIf false
                    if (route.isEmpty()) return@removeIf true
                    mqttClrPayloadMatchesResolvedCounter(entry.key, serial, route)
                }
                if (!changed) {
                    android.util.Log.w(
                        "MqttViewModel",
                        "CLR cleared DB/history for keys=$keysToClear but no in-memory token map entries matched " +
                            "(serial=$serial route='$routeIndex')",
                    )
                }
                _tokensPerCounter.postValue(internalTokenMap.toMap())
            }
        }

    }

    @Volatile
    private var lastPayloadSyncAtMs: Long = 0L

    /** Max tokens kept per counter key; from [com.softland.callqtv.data.local.TvConfigEntity.tokensPerCounter]. */
    @Volatile
    private var tokenHistoryLimit: Int = 15

    /**
     * Updates the in-memory token history limit based on TV config.
     *
     * If the limit changes, it trims existing token history lists and prunes VIP/emergency
     * tokens so both history and VIP sets remain consistent with the new retention window.
     */
    fun applyTokenHistoryLimitFromConfig(config: com.softland.callqtv.data.local.TvConfigEntity?) {
        val newLimit = config?.tokensPerCounter?.coerceAtLeast(1) ?: 15
        val limitChanged = newLimit != tokenHistoryLimit
        tokenHistoryLimit = newLimit
        if (!limitChanged) return
        viewModelScope.launch {
            stateMutex.withLock {
                var mapChanged = false
                for (key in internalTokenMap.keys.toList()) {
                    val prior = internalTokenMap[key] ?: continue
                    val trimmed = trimTokenHistory(prior)
                    if (trimmed != prior) {
                        internalTokenMap[key] = trimmed
                        mapChanged = true
                    }
                    pruneVipEmergencyTokens(key, trimmed)
                }
                if (mapChanged) {
                    _tokensPerCounter.postValue(internalTokenMap.toMap())
                }
                postVipEmergencyTokensSnapshot()
            }
        }
    }

    /**
     * Trims the given history list to the currently configured [tokenHistoryLimit].
     */
    private fun trimTokenHistory(list: List<String>): List<String> =
        list.take(tokenHistoryLimit.coerceAtLeast(1))

    private val counterRouteCache =
        CounterRouteLookupCache<ResolvedCounterIdentity>(MqttCounterRouteKeys.CACHE_TTL_MS)

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

    /**
     * Sets the license-expired flag and blocks MQTT/UI token processing when expired.
     *
     * When toggled to `true`, it clears queued token UI events, cancels retry/connect jobs,
     * stops background MQTT loops/watchdogs, and disconnects active broker managers.
     */
    fun setLicenseExpired(expired: Boolean) {
        if (isLicenseExpired == expired) return
        isLicenseExpired = expired
        if (expired) {
            android.util.Log.w("MqttViewModel", "License expired; MQTT message processing is BLOCKED.")
            setPayloadUiReady(false)
            pendingTokenUiEvents.clear()
            viewModelScope.launch {
                retryJobs.values.forEach { it.cancel() }
                retryJobs.clear()
                stopConnectTimer()
                stopContinuousPublishLoop()
                messageLivenessWatchdogJob?.cancel()
                messageLivenessWatchdogJob = null
                indicatorWatchdogJob?.cancel()
                indicatorWatchdogJob = null
                managers.values.forEach { manager ->
                    runCatching { manager.disconnect() }
                }
                _connectionStatus.postValue(false)
                _connectionStatusMap.postValue(emptyMap())
                _isConnectingToMqtt.postValue(false)
            }
        }
    }

    private val _receivedMessage = MutableLiveData<String>()
    /** Latest raw received MQTT message preview. */
    fun getReceivedMessage(): LiveData<String> = _receivedMessage

    private val _lastPayload = MutableLiveData<String>("")
    /** Most recent accepted MQTT payload string. */
    fun getLastPayload(): LiveData<String> = _lastPayload

    private val _connectionStatusMap = MutableLiveData<Map<String, Boolean>>(emptyMap())
    /** Map of broker server URI to connection status. */
    fun getConnectionStatusMap(): LiveData<Map<String, Boolean>> = _connectionStatusMap

    private val _connectionStatus = MutableLiveData<Boolean>(false)
    /** Aggregated connection status (true when all relevant brokers are connected). */
    fun getConnectionStatus(): LiveData<Boolean> = _connectionStatus

    private val _errorMessage = MutableLiveData<String>("")
    /** Last MQTT-related error message shown to the UI. */
    fun getErrorMessage(): LiveData<String> = _errorMessage

    private val _isConnectingToMqtt = MutableLiveData<Boolean>(false)
    /** True while MQTT connection attempts/timers are active. */
    fun isConnectingToMqtt(): LiveData<Boolean> = _isConnectingToMqtt

    private val _connectTimer = MutableLiveData<Int>(0)
    /** Seconds counter used by the MQTT retry dialog. */
    fun getConnectTimer(): LiveData<Int> = _connectTimer

    private var timerJob: kotlinx.coroutines.Job? = null
    private var connectTime = 0

    /** Starts a 1-second connect watchdog timer used for UI retry feedback. */
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

    /**
     * Stops the connect timer when all brokers are connected.
     * (Prevents the timer UI from lingering after stable connection.)
     */
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
    /** True once auto-retry budget has been exhausted for the current MQTT session. */
    fun isAutoRetryExhausted(): LiveData<Boolean> = _isAutoRetryExhausted

    // Exposes current MQTT retry attempt (for UI display)
    private val _mqttRetryAttempt = MutableLiveData<Int>(0)
    /** Current MQTT auto-retry attempt counter exposed for UI messaging. */
    fun getMqttRetryAttempt(): LiveData<Int> = _mqttRetryAttempt

    // Exposes whether the broker host/port is reachable via a short TCP ping.
    private val _isBrokerReachable = MutableLiveData<Boolean>(false)
    /** True when the broker endpoint is reachable (used as a UI hint). */
    fun isBrokerReachable(): LiveData<Boolean> = _isBrokerReachable

    private val _dispenseConnectedByButton = MutableLiveData<Map<String, Boolean>>(emptyMap())
    /** Connection status per dispense button route. */
    fun getDispenseConnectedByButton(): LiveData<Map<String, Boolean>> = _dispenseConnectedByButton

    private val _keypadConnectedByButton = MutableLiveData<Map<String, Boolean>>(emptyMap())
    /** Connection status per keypad button route. */
    fun getKeypadConnectedByButton(): LiveData<Map<String, Boolean>> = _keypadConnectedByButton

    private val lastDispenseSeenAtByButton = ConcurrentHashMap<String, Long>()
    private val lastKeypadSeenAtByButton = ConcurrentHashMap<String, Long>()
    private var indicatorWatchdogJob: kotlinx.coroutines.Job? = null

    val tokenUpdateChannel: Channel<TokenUiEvent> = createTokenUiChannel("tokenUpdate")
    val tokenReplaceChannel: Channel<TokenUiEvent> = createTokenUiChannel("tokenReplace")
    /**
     * Requests to refresh TV configuration based on a *specific* MQTT response.
     * (Used to remove FCM dependency and keep refresh driven by MQTT.)
     */
    val configRefreshRequests: Channel<TvConfigRefreshSignal> = Channel(
        capacity = MqttCounterRouteKeys.CONFIG_REFRESH_CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { dropped ->
            android.util.Log.w(
                "MqttViewModel",
                "configRefresh channel full; dropped oldest refresh signal: ${dropped.reason}",
            )
        },
    )
    private val queuedTokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val queuedPayloadTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Queue of ALL raw MQTT payloads received (no filtering). Business logic consumes and filters
    // from this queue so we never lose received data due to early drops.
    private val rawMessageQueue: java.util.Queue<String> =
        java.util.concurrent.ConcurrentLinkedQueue<String>()

    private val _tokensPerCounter = MutableLiveData<Map<String, List<String>>>(emptyMap())
    /** Current mapped token history per counter key for UI rendering. */
    fun getTokensPerCounter(): LiveData<Map<String, List<String>>> = _tokensPerCounter

    // Thread-safe internal state to prevent race conditions during updates
    private val internalTokenMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val announcedTokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Raw token values that keep VIP/emergency prefix (ER) on any history slot, per map key. */
    private val vipEmergencyTokensByKey = ConcurrentHashMap<String, MutableSet<String>>()
    private val _vipEmergencyTokensByKey = MutableLiveData<Map<String, Set<String>>>(emptyMap())

    /** VIP/emergency token sets per token-map key for UI/state reconciliation. */
    fun getVipEmergencyTokensByKey(): LiveData<Map<String, Set<String>>> = _vipEmergencyTokensByKey

    /** Publishes VIP/emergency token snapshots and persists them to shared preferences. */
    private fun postVipEmergencyTokensSnapshot() {
        _vipEmergencyTokensByKey.postValue(
            vipEmergencyTokensByKey.mapValues { (_, tokens) -> tokens.toSet() },
        )
        persistVipEmergencyTokensToPrefs()
    }

    /** Adds a VIP/emergency token to the in-memory set for the given map key. */
    private fun markVipEmergencyToken(key: String, token: String) {
        vipEmergencyTokensByKey.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(token)
    }

    /**
     * Prunes VIP/emergency token sets by removing tokens that are no longer present
     * in the active token list for that map key.
     */
    private fun pruneVipEmergencyTokens(key: String, activeTokens: List<String>) {
        val active = activeTokens.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val set = vipEmergencyTokensByKey[key] ?: return
        set.retainAll(active)
        if (set.isEmpty()) vipEmergencyTokensByKey.remove(key)
    }

    private val vipEmergencyPrefs by lazy {
        getApplication<Application>().getSharedPreferences(VIP_EMERGENCY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Builds the per-customer SharedPreferences key for VIP/emergency token snapshots. */
    private fun vipEmergencyPrefsStorageKey(customerId: String): String = "vip_tokens_$customerId"

    /** Returns the current customer id (zero-padded) used for VIP/emergency prefs scoping. */
    private fun currentCustomerIdForPrefs(): String {
        val authPrefs = getApplication<Application>().getSharedPreferences(
            com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
            Context.MODE_PRIVATE,
        )
        return String.format(
            java.util.Locale.ROOT,
            "%04d",
            authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0),
        )
    }

    /** Persists the in-memory VIP/emergency token sets into shared preferences. */
    private fun persistVipEmergencyTokensToPrefs() {
        val customerId = currentCustomerIdForPrefs()
        val snapshot = vipEmergencyTokensByKey.mapValues { (_, tokens) -> tokens.toList() }
        vipEmergencyPrefs.edit()
            .putString(vipEmergencyPrefsStorageKey(customerId), gson.toJson(snapshot))
            .apply()
    }

    /**
     * Restores VIP/emergency token sets from shared preferences for the given customer id.
     *
     * If parsing fails or the prefs entry is missing, this is a no-op.
     */
    private fun restoreVipEmergencyTokensFromPrefs(customerId: String) {
        val json = vipEmergencyPrefs.getString(vipEmergencyPrefsStorageKey(customerId), null) ?: return
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val loaded: Map<String, List<String>> = try {
            gson.fromJson(json, type) ?: return
        } catch (_: Exception) {
            return
        }
        loaded.forEach { (key, tokens) ->
            val trimmed = tokens.map { it.trim() }.filter { it.isNotEmpty() }
            if (trimmed.isNotEmpty()) {
                vipEmergencyTokensByKey[key] = ConcurrentHashMap.newKeySet<String>().apply { addAll(trimmed) }
            }
        }
    }

    /** Removes stored VIP/emergency token snapshot from shared preferences for the customer. */
    private fun clearVipEmergencyTokensPrefs(customerId: String) {
        vipEmergencyPrefs.edit().remove(vipEmergencyPrefsStorageKey(customerId)).apply()
    }

    @Volatile
    private var isHistoryLoaded = false

    @Volatile
    private var uiReadyForTokenUiEvents = false

    private data class PendingTokenUiEvent(
        val channel: Channel<TokenUiEvent>,
        val event: TokenUiEvent,
    )

    private val pendingTokenUiEvents = ConcurrentLinkedQueue<PendingTokenUiEvent>()

    /**
     * When false, token announcement/replace events are held until the UI opens the gate on cold start.
     * Opening the gate does not flush held events — call [flushHeldTokenUiEvents] once counter routing
     * is available so flushed tokens can be announced (important after APK upgrade / config reload).
     */
    fun setPayloadUiReady(ready: Boolean) {
        uiReadyForTokenUiEvents = ready
        if (ready) {
            publishTokensSnapshot()
        }
    }

    /** Delivers tokens that were held while [setPayloadUiReady] was false (FIFO). */
    fun flushHeldTokenUiEvents() {
        if (!uiReadyForTokenUiEvents) return
        flushPendingTokenUiEvents()
    }

    fun heldTokenUiEventCount(): Int = pendingTokenUiEvents.size

    /** Returns whether the UI is ready to receive/enqueue token UI events. */
    fun isPayloadUiReady(): Boolean = uiReadyForTokenUiEvents

    /** Suspends until persisted token history has been merged into the in-memory map (cold start). */
    suspend fun awaitHistoryLoaded(timeoutMs: Long = 30_000L) {
        if (isHistoryLoaded) return
        withTimeoutOrNull(timeoutMs) {
            while (!isHistoryLoaded) {
                delay(50L)
            }
        }
    }

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

    /** Updates the “announced recently” timestamp for [counterKey] + [token]. */
    fun markAsAnnounced(counterKey: String, token: String) {
        val key = "${counterKey.trim()}_${token.trim()}"
        announcedTokenTimestamps[key] = System.currentTimeMillis()

        // Cleanup old entries (simple way to prevent memory leak)
        if (announcedTokenTimestamps.size > 100) {
            val now = System.currentTimeMillis()
            announcedTokenTimestamps.entries.removeIf { now - it.value > 60000 }
        }
    }

    /** Publishes current token history map into the UI channel (`tokenUpdateChannel`). */
    fun publishTokensSnapshot() {
        _tokensPerCounter.postValue(internalTokenMap.toMap())
    }

    /**
     * Loads persisted token history from repositories and merges it into the in-memory map.
     *
     * Also restores VIP/emergency tokens from prefs and applies the currently configured
     * token history retention limit.
     */
    fun loadPersistedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val customerId = run {
                val app = getApplication<Application>()
                val authPrefs = app.getSharedPreferences(
                    com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
                    Context.MODE_PRIVATE,
                )
                String.format(
                    java.util.Locale.ROOT,
                    "%04d",
                    authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0),
                )
            }
            try {
                val app = getApplication<Application>()
                val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
                applyTokenHistoryLimitFromConfig(tvConfigDao.getByMacAndCustomer(macAddress, customerId))
            } catch (_: Exception) {
                // Keep default limit when config is unavailable.
            }
            stateMutex.withLock {
                try {
                    restoreVipEmergencyTokensFromPrefs(customerId)
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
                        pruneVipEmergencyTokens(key, combined)
                    }
                    isHistoryLoaded = true
                    postVipEmergencyTokensSnapshot()
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

    /** Kicks off async clearing of in-memory and persisted token history. */
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
                    vipEmergencyTokensByKey.clear()
                    clearVipEmergencyTokensPrefs(currentCustomerIdForPrefs())
                    _vipEmergencyTokensByKey.postValue(emptyMap())
                    queuedTokenTimestamps.clear()
                    queuedPayloadTimestamps.clear()
                    announcementQueueSize.set(0)
                    invalidateCounterRoutingCache()
                } catch (e: Exception) {
                    android.util.Log.w(
                        "MqttViewModel",
                        "Failed to clear token history: ${e.message}"
                    )
                }
            }
        }
    }

    /** Runs daily cleanup for persisted token record storage (best-effort). */
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

    /**
     * Initializes MQTT for the given broker and starts connecting/subscribing.
     *
     * Called after config sync/reconcile to ensure each broker uses the right
     * host/topic/credentials/clientId/qos, and to start the retry/reachability/watchdog logic.
     */
    fun initAndConnect(
        serverUri: String,
        clientId: String,
        username: String?,
        password: String?,
        topic: String,
        qos: Int,
        context: android.content.Context,
    ) {
        val newDetails = MqttConnectionDetails(serverUri, clientId, username, password, topic, qos)
        val previousDetails = connectionDetailsMap[serverUri]
        connectionDetailsMap[serverUri] = newDetails

        val reconnectHost = object : MqttBrokerConnector.ReconnectHost {
            override fun stopConnectTimer() = this@MqttViewModel.stopConnectTimer()
            override fun startConnectTimer() = this@MqttViewModel.startConnectTimer()
            override fun updateStatus(connected: Boolean) = updateStatus(serverUri, connected)
            override fun detachBroker() = detachBrokerSync(serverUri)
        }
        when (
            MqttBrokerConnector.handleExistingManager(
                existing = managers[serverUri],
                clientId = clientId,
                previousDetails = previousDetails,
                newDetails = newDetails,
                topic = topic,
                qos = qos,
                username = username,
                password = password,
                host = reconnectHost,
            )
        ) {
            MqttBrokerConnector.ExistingManagerResult.Reattached -> return
            MqttBrokerConnector.ExistingManagerResult.MustReplace -> Unit
        }

        val manager = MqttClientManager(context.applicationContext, serverUri, clientId).apply {
            setMqttListener(MqttBrokerListenerFactory.create(createBrokerCallbacks(serverUri)))
        }

        startReachabilityMonitor(serverUri)
        startConnectTimer()
        managers[serverUri] = manager
        manager.subscribe(topic, qos)
        manager.connect(username, password)
    }

    /**
     * Factory for per-broker callbacks that forward MQTT events into this ViewModel.
     *
     * These callbacks update connection status, enqueue raw payloads, and process inbound
     * payloads in the background pipeline.
     */
    private fun createBrokerCallbacks(serverUri: String): MqttBrokerCallbacks =
        object : MqttBrokerCallbacks {
            override val serverUri: String = serverUri

            override fun isLicenseExpired(): Boolean = this@MqttViewModel.isLicenseExpired

            override fun onAnyIncomingTraffic() {
                lastMessageAtByServerUri[serverUri] = System.currentTimeMillis()
                staleTrafficStrikeByServerUri[serverUri] = 0
            }

            override fun logPayloadIn(trimmed: String) {
                logMqttToFileThrottled(
                    key = "MQTT_PAYLOAD_IN:$serverUri",
                    tag = "MQTT_PAYLOAD_IN",
                    message = trimmed,
                    minIntervalMs = 0L,
                )
            }

            override fun enqueueRawPayload(trimmed: String) {
                rawMessageQueue.add(trimmed)
                while (rawMessageQueue.size > 2000) {
                    rawMessageQueue.poll()
                }
            }

            override fun launchBackground(block: suspend () -> Unit) {
                viewModelScope.launch(Dispatchers.Default) { block() }
            }

            override suspend fun processInboundPayload(topic: String, trimmed: String) {
                processInboundMqttPayload(topic, trimmed)
            }

            override fun onConnectionStatus(isConnected: Boolean) {
                updateStatus(serverUri, isConnected)
                if (isConnected) {
                    lastMessageAtByServerUri[serverUri] = System.currentTimeMillis()
                    staleReconnectInFlight.remove(serverUri)
                    startMessageLivenessWatchdog()
                    everConnected[serverUri] = true
                    stopConnectTimer()
                    _isAutoRetryExhausted.postValue(false)
                    _errorMessage.postValue("")
                    retryAttempts[serverUri] = 0
                    retryJobs[serverUri]?.cancel()
                    _mqttRetryAttempt.postValue(retryAttempts.values.maxOrNull() ?: 0)
                    startContinuousPublishLoop()
                } else {
                    startConnectTimer()
                    stopContinuousPublishLoop()
                    if (managers[serverUri]?.isConnectingNow() != true) {
                        scheduleReconnect(serverUri)
                    }
                }
            }

            override fun onBrokerError(error: String, code: Int?) {
                val logMessage = if (code != null) "[$serverUri] $error (Code: $code)" else "[$serverUri] $error"
                logMqttToFileThrottled(
                    key = "MQTT_ERROR:$serverUri",
                    tag = "MQTT_ERROR",
                    message = logMessage,
                    minIntervalMs = 15_000L,
                )
                _errorMessage.postValue("")
                incrementMqttRetryAttemptCounter(serverUri)
                if (managers[serverUri]?.isConnectingNow() != true) {
                    scheduleReconnect(serverUri)
                }
            }

            override fun onAutoRetryExhausted() {
                stopConnectTimer()
                _isAutoRetryExhausted.postValue(true)
            }
        }

    /** Routes an inbound MQTT payload (already verified by the MQTT layer) into the payload router. */
    private suspend fun processInboundMqttPayload(topic: String, trimmed: String) {
        inboundPayloadRouter.process(topic, trimmed)
    }

    /** Clears keypad serial registration cache so subsequent routing re-reads DB/config. */
    fun invalidateKeypadSerialCache() {
        invalidateCounterRoutingCache()
        keypadSerialRegistry.invalidate()
    }

    /** Clears serial+route → counter resolution cache (e.g. after TV config sync). */
    fun invalidateCounterRoutingCache() {
        counterRouteCache.invalidate()
    }

    /**
     * Triggers a manual MQTT reconnect attempt.
     *
     * If [resetAttempts] is true, it resets per-broker retry counters before reconnecting.
     */
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

    /**
     * Updates connection/liveness state for a specific broker.
     *
     * Besides the per-broker connection map, it derives:
     * - aggregated BLUCON connected state (connected OR recent traffic)
     * - reachability flag used by the UI header icon
     */
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
                // Header BLUCON icon: update immediately on connect/disconnect (not only on 5s poll).
                reachabilityStatusMap[serverUri] = isConnected
                _isBrokerReachable.postValue(reachabilityStatusMap.values.any { it })
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
        if (managers[serverUri]?.isConnectingNow() == true) {
            android.util.Log.d(
                "MqttViewModel",
                "Skipping reconnect schedule for $serverUri: connect already in progress.",
            )
            return
        }
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

    /**
     * Returns a reconnect backoff delay for the given retry stage.
     *
     * The value comes from [MqttReconnectPolicy] and is adjusted for the current network
     * bandwidth tier.
     */
    private fun reconnectDelayMs(attempt: Int, afterFirstSuccess: Boolean): Long =
        MqttReconnectPolicy.reconnectDelayMs(attempt, afterFirstSuccess, isLowBandwidthNetwork())

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
                    val serials = keypadSerialRegistry.registeredSerialsSnapshot()

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

    /**
     * Removes a broker manager from this ViewModel.
     *
     * This is a thin wrapper that detaches broker state while holding the internal
     * state mutex to keep connection/job maps consistent.
     */
    private fun removeManager(serverUri: String) {
        viewModelScope.launch {
            stateMutex.withLock {
                detachBrokerLocked(serverUri)
            }
        }
    }

    /** Detaches all MQTT state for the given broker and cancels scheduled jobs. */
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

    /** Detaches broker while holding stateMutex (safe internal helper). */
    private fun detachBrokerLocked(serverUri: String) {
        detachBrokerSync(serverUri)
    }

    /** Converts a broker endpoint into runtime MQTT connection details for the client. */
    private fun BrokerEndpoint.toMqttConnectionDetails(): MqttConnectionDetails =
        MqttConnectionDetails(serverUri, clientId, username, password, topic, qos)

    /**
     * Inserts/moves a token to the top of the history list for [counter].
     *
     * Returns `true` when the UI layer should announce/play cues (e.g. when the token
     * becomes the new top token and passes suppression rules).
     */
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
            val vipBefore = keysToUpdate.associateWith { k ->
                vipEmergencyTokensByKey[k]?.toSet() ?: emptySet<String>()
            }

            for (key in keysToUpdate) {
                val priorStored = internalTokenMap[key] ?: emptyList()
                val list = priorStored.toMutableList()
                // When normal tokens arrive after a special message, remove special-message placeholders.
                list.removeAll { it.contains("__MSG__", ignoreCase = false) }
                val existingPosition = list.indexOf(trimmedToken)

                // Already at top: still announce when this counter's UI event is processed
                // (sequential queue handles one full chime + speech cycle per MQTT event).
                if (existingPosition == 0) {
                    if (key == trimmedPrimary) {
                        shouldAnnounce = true
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

            if (isVipEmergency) {
                for (key in keysToUpdate) {
                    markVipEmergencyToken(key, trimmedToken)
                }
            }
            for (key in keysToUpdate) {
                pruneVipEmergencyTokens(key, internalTokenMap[key] ?: emptyList())
            }
            postVipEmergencyTokensSnapshot()

            val vipChanged = keysToUpdate.any { k ->
                vipBefore[k] != (vipEmergencyTokensByKey[k]?.toSet() ?: emptySet<String>())
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
            }
            postVipEmergencyTokensSnapshot()
            if (publishImmediately) {
                publishTokensSnapshot()
            }
            true
        }
    }

    /** Persists the given token to the token history repository asynchronously. */
    private fun persistToken(key: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tokenHistoryRepo.saveToken(key, token)
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "Persist failed: ${e.message}")
            }
        }
    }

    /** Normalizes counter identifiers into a stable numeric string key when possible. */
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

    /** Marks the given MQTT payload as displayed/handled (used for throttling). */
    fun markPayloadDisplayed(payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mqttPayloadLogRepo.markDisplayed(payload)
                scheduleMqttPayloadSync(force = true)
            } catch (_: Exception) {
            }
        }
    }

    /** Marks a dispense button route as seen/connected and posts per-button state. */
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

    /** Marks a keypad button route as seen/connected and posts per-button state. */
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

    /** Periodically updates BLUCON header indicator flags based on last-seen timestamps. */
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

    /**
     * Saves a detailed audit record for a token call to the local token-record repository.
     *
     * Extracts keypad serial from the payload and derives customer/counter fields needed for reports.
     * Also triggers MQTT payload log sync.
     */
    private fun saveTokenRecord(message: String, counterKey: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serial = KeypadPayloadParser.extractKeypadSerial(message)
                    ?.let { MqttCounterRouteKeys.normalizeKeypadSerial(it) }
                if (serial.isNullOrBlank() || !keypadSerialRegistry.isRegistered(serial)) {
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
                val matchingCounter = counters.find { counterIdentityResolver.counterMatchesLabel(it, counterKey) }
                
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

    /** Persists the raw incoming MQTT payload to local logs (for later sync). */
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

    /**
     * Forces broker reconnection when no MQTT messages have been received for too long.
     *
     * Uses liveness timeouts and a strike threshold to avoid aggressive reconnect loops.
     */
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

    /**
     * Writes MQTT logs with per-key throttling so repeated events don't spam the error log.
     *
     * [minIntervalMs] controls the minimum delay between two writes of the same [key].
     */
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

    /** Formats a raw MQTT payload into a short, log-safe preview string. */
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

    /**
     * Creates a buffered channel for token UI events.
     *
     * When the channel is full, it drops the oldest pending event and decrements the shared
     * announcement queue size counter.
     */
    private fun createTokenUiChannel(name: String): Channel<TokenUiEvent> =
        Channel(
            capacity = MqttCounterRouteKeys.TOKEN_UI_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = {
                val remaining = announcementQueueSize.decrementAndGet()
                if (remaining < 0) {
                    announcementQueueSize.set(0)
                }
                android.util.Log.w(
                    "MqttViewModel",
                    "$name channel full; dropped oldest pending token UI event",
                )
            },
        )

    /** Enqueues a token UI event, or buffers it in-memory until UI gating is enabled. */
    private fun enqueueTokenUiEvent(channel: Channel<TokenUiEvent>, event: TokenUiEvent) {
        if (!uiReadyForTokenUiEvents) {
            if (pendingTokenUiEvents.size >= MqttCounterRouteKeys.TOKEN_UI_CHANNEL_CAPACITY) {
                pendingTokenUiEvents.poll()
                android.util.Log.w(
                    "MqttViewModel",
                    "Pending token UI queue full; dropped oldest held event",
                )
            }
            pendingTokenUiEvents.add(PendingTokenUiEvent(channel, event))
            return
        }
        sendTokenUiEvent(channel, event)
    }

    /** Flushes all queued token UI events in FIFO order once the UI becomes ready. */
    private fun flushPendingTokenUiEvents() {
        while (true) {
            val pending = pendingTokenUiEvents.poll() ?: break
            sendTokenUiEvent(pending.channel, pending.event)
        }
    }

    /** Sends a token UI event into the channel and tracks announcement queue size. */
    private fun sendTokenUiEvent(channel: Channel<TokenUiEvent>, event: TokenUiEvent) {
        announcementQueueSize.incrementAndGet()
        if (!channel.trySend(event).isSuccess) {
            announcementQueueSize.decrementAndGet()
            android.util.Log.w("MqttViewModel", "Token UI channel closed; event not queued")
        }
    }

    /** Builds the per-device cache scope key for routing/identity operations. */
    private fun routingCacheScope(): String {
        val app = getApplication<Application>()
        val authPrefs = app.getSharedPreferences(
            com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION,
            Context.MODE_PRIVATE,
        )
        val customerId = String.format(
            java.util.Locale.ROOT,
            "%04d",
            authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0),
        )
        val macAddress = com.softland.callqtv.utils.Variables.getMacId(app)
        return MqttCounterRouteKeys.deviceScope(macAddress, customerId)
    }

    companion object {
        private const val VIP_EMERGENCY_PREFS_NAME = "vip_emergency_tokens"
        private const val MQTT_MESSAGE_LIVENESS_TIMEOUT_MS = 90_000L
        private const val MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT = 2
        private const val MQTT_MESSAGE_LIVENESS_TIMEOUT_MS_LOW_NETWORK = 120_000L
        private const val MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT_LOW_NETWORK = 3
        private const val CONFIG_REFRESH_DEBOUNCE_MS = 15_000L
        private const val INDICATOR_STALE_TIMEOUT_MS = 300_000L
        private const val MAX_MQTT_LOG_PAYLOAD_CHARS = 220
        private const val MQTT_PAYLOAD_SYNC_DEBOUNCE_MS = 15_000L
    }

    /** Returns whether the device is on a low-bandwidth tier (used by MQTT policies). */
    private fun isLowBandwidthNetwork(): Boolean =
        com.softland.callqtv.utils.NetworkCompat.isLowBandwidthNetwork(getApplication())

    /** Returns the silence timeout used by message liveness watchdog logic. */
    private fun getMqttLivenessTimeoutMs(): Long {
        return if (isLowBandwidthNetwork()) {
            MQTT_MESSAGE_LIVENESS_TIMEOUT_MS_LOW_NETWORK
        } else {
            MQTT_MESSAGE_LIVENESS_TIMEOUT_MS
        }
    }

    /** Returns the number of stale watchdog “strikes” required before forcing reconnect. */
    private fun getMqttStaleStrikeThreshold(): Int {
        return if (isLowBandwidthNetwork()) {
            MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT_LOW_NETWORK
        } else {
            MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT
        }
    }

    /**
     * Requests a TV config refresh, debounced unless [forceImmediate] is true.
     *
     * When immediate, it bypasses debounce and sends a CLR-trigger-like refresh signal.
     */
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
        uiReadyForTokenUiEvents = false
        pendingTokenUiEvents.clear()
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
