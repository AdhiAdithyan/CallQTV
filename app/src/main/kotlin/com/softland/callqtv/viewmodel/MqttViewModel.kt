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
import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.repository.MqttClientManager
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

    private val managers = mutableMapOf<String, MqttClientManager>()
    private val connectionDetailsMap = mutableMapOf<String, MqttConnectionDetails>()
    private val stateMutex = Mutex()
    private val gson = Gson()

    init {
        loadPersistedHistory()
        performRecordsCleanup()
    }

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

    private val tokenRecordRepo: com.softland.callqtv.data.repository.TokenRecordRepository by lazy {
        com.softland.callqtv.data.repository.TokenRecordRepository(AppDatabase.getInstance(application))
    }

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
        val qos: Int
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
        kotlinx.coroutines.channels.Channel<Pair<String, String>>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)
    /**
     * Requests to refresh TV configuration based on a *specific* MQTT response.
     * (Used to remove FCM dependency and keep refresh driven by MQTT.)
     */
    val configRefreshRequests =
        kotlinx.coroutines.channels.Channel<Unit>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val queuedTokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Queue of ALL raw MQTT payloads received (no filtering). Business logic consumes and filters
    // from this queue so we never lose received data due to early drops.
    private val rawMessageQueue: java.util.Queue<String> =
        java.util.concurrent.ConcurrentLinkedQueue<String>()

    private val _tokensPerCounter = MutableLiveData<Map<String, List<String>>>(emptyMap())
    fun getTokensPerCounter(): LiveData<Map<String, List<String>>> = _tokensPerCounter

    // Thread-safe internal state to prevent race conditions during updates
    private val internalTokenMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val announcedTokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
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

    fun loadPersistedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                try {
                    val persisted = tokenHistoryRepo.loadAll()
                    // If MQTT updates arrived before we loaded from DB, merge them
                    // This is safer than internalTokenMap.clear()
                    persisted.forEach { (key, tokens) ->
                        val current = internalTokenMap[key] ?: emptyList()
                        // Combine: persisted tokens followed by current ones, then take top 15
                        val combined = (tokens + current).distinct().take(15)
                        internalTokenMap[key] = combined
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
            stateMutex.withLock {
                try {
                    tokenHistoryRepo.clearAll()
                    internalTokenMap.clear()
                    isHistoryLoaded = true // Resetting counts as loaded
                    _tokensPerCounter.postValue(emptyMap())
                    announcedTokenTimestamps.clear()
                } catch (e: Exception) {
                    android.util.Log.w(
                        "MqttViewModel",
                        "Failed to clear token history: ${e.message}"
                    )
                }
            }
        }
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

    fun initAndConnect(
        serverUri: String,
        clientId: String,
        username: String?,
        password: String?,
        topic: String,
        qos: Int,
        context: android.content.Context
    ) {
        connectionDetailsMap[serverUri] =
            MqttConnectionDetails(serverUri, clientId, username, password, topic, qos)

        val existing = managers[serverUri]
        if (existing != null) {
            if (existing.clientId == clientId) {
                existing.subscribe(topic, qos)
                if (existing.isConnected()) {
                    // Already connected: make sure UI does NOT show "connecting..."
                    stopConnectTimer()
                    updateStatus(serverUri, true)
                } else {
                    // Not connected yet: trigger a new connect attempt and timer
                    startConnectTimer()
                    existing.connect(username, password)
                }
                return
            } else {
                removeManager(serverUri)
            }
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
                    if (isLicenseExpired) return
                    // Enqueue ALL raw payloads first, without any filtering.
                    val trimmed = message.trim()
                    rawMessageQueue.add(trimmed)
                    // Simple bound to avoid unbounded growth in extreme cases.
                    while (rawMessageQueue.size > 2000) {
                        rawMessageQueue.poll()
                    }

                    // Offload validation/processing to background so MQTT callback stays fast.
                    viewModelScope.launch(Dispatchers.Default) {
                        // Business filters (unchanged) operate on the queued payload content.
                        // We re-use 'trimmed' here, but every raw message is already stored.
                        if (!trimmed.contains("CAL0K")) return@launch
                        if (!trimmed.startsWith("$")) return@launch
                        if (trimmed.length < 24) return@launch
                        val containsClr = trimmed.contains("CLR", ignoreCase = true)

                        // CLR-triggered config refresh path:
                        // e.g. "$02026bCAL0K0007001CLR0*"
                        // Only trigger when keypad serial is valid for this device/customer.
                        if (containsClr) {
                            if (!isValidKeypadMessage(trimmed)) {
                                android.util.Log.d(
                                    "MqttViewModel",
                                    "Ignored CLR refresh trigger: Keypad serial mismatch or device not found $trimmed"
                                )
                                return@launch
                            }
                            requestConfigRefresh("MQTT refresh trigger detected (payload contains CLR)")
                            return@launch
                        }

                        // Business rule: ignore responses where the 17th character is '0'
                        if (trimmed[16] == '0') {
                            requestConfigRefresh("MQTT refresh trigger detected (17th char '0')")
                            return@launch
                        }

                        // Validate against Keypad Serial Number (runs on IO dispatcher internally)
                        if (!isValidKeypadMessage(trimmed)) {
                            android.util.Log.d(
                                "MqttViewModel",
                                "Ignored message: Keypad serial mismatch or device not found $trimmed"
                            )
                            return@launch
                        }

                        android.util.Log.i(
                            "MqttViewModel",
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
                    }
                }

                override fun onError(error: String, code: Int?) {
                    // Log the error to a date-wise file instead of showing it in the UI.
                    val logTag = "MQTT_ERROR"
                    val logMessage = if (code != null) "[$serverUri] $error (Code: $code)" else "[$serverUri] $error"
                    FileLogger.logError(getApplication(), logTag, logMessage)

                    // Stop surfacing specific MQTT errors to the UI per user request.
                    // We keep the LiveData for state if needed, but we clear it or just don't update it.
                    _errorMessage.postValue("")

                    // Initial connection failures: backoff + increment inside scheduleInitialRetry.
                    // After first successful connect, scheduleInitialRetry is skipped — still bump the
                    // BLUCON retry counter so the UI shows each disconnect / connection-lost event.
                    if (everConnected[serverUri] == true) {
                        incrementMqttRetryAttemptCounter(serverUri)
                    } else {
                        scheduleInitialRetry(serverUri)
                    }
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
        val trimmed = message.trim()
        if (!trimmed.startsWith("$") || !trimmed.endsWith("*")) return null

        // Remove leading '$' and trailing '*'
        val body = trimmed.substring(1, trimmed.length - 1)
        if (body.length < 16) return null

        // Protocol: serial is characters 1..15 of body (index 1 inclusive, 15 exclusive)
        val serial = body.substring(1, 15).trim()
        if (serial.isEmpty()) return null

        return serial
    }

    /**
     * Checks if the serial extracted from the keypad message matches a connected device
     * of type "KEYPAD" for the current MAC and customer, using an in-memory cache to
     * avoid DB lookups on every MQTT message.
     */
    private suspend fun isValidKeypadMessage(message: String): Boolean {
        val serialInMessage = extractKeypadSerial(message) ?: return false
        val normalizedSerial = serialInMessage.trim().uppercase()

        return withContext(Dispatchers.IO) {
            ensureKeypadSerialCache()
            keypadSerials.contains(normalizedSerial)
        }
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

                keypadSerials.clear()
                devices.forEach { device ->
                    if (device.deviceType?.uppercase() == "KEYPAD") {
                        device.serialNumber
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.uppercase()
                            ?.let { keypadSerials.add(it) }

                        // Also inspect configJson once to capture any additional embedded serials.
                        val cfg = device.configJson
                        if (!cfg.isNullOrBlank()) {
                            // Very lightweight scan: look for CAL0K-style serials and cache them.
                            val regex = "20[0-9A-Za-z]{11}".toRegex()
                            regex.findAll(cfg).forEach { m ->
                                keypadSerials.add(m.value.uppercase())
                            }
                        }
                    }
                }

                keypadSerialsLoaded = true
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
            // Manual/Timeout retry: bump attempt counter for any disconnected broker
            // so the UI "Try X" count increments even if Paho is stuck in an internal loop.
            var incremented = false
            connectionDetailsMap.keys.forEach { serverUri ->
                if (managers[serverUri]?.isConnected() != true) {
                    val next = (retryAttempts[serverUri] ?: 0) + 1
                    retryAttempts[serverUri] = next
                    android.util.Log.d("MqttViewModel", "Manual retry trigger: incrementing attempt for $serverUri to $next")
                    incremented = true
                }
            }
            
            // If for some reason we missed the increment (e.g. state race), 
            // force a global increment so the user sees progress.
            if (!incremented) {
                val currentMax = _mqttRetryAttempt.value ?: 0
                _mqttRetryAttempt.postValue(currentMax + 1)
            } else {
                _mqttRetryAttempt.postValue(retryAttempts.values.maxOrNull() ?: 0)
            }
        }
        
        // Only show "connecting" if at least one broker is actually disconnected
        val anyDisconnected = connectionDetailsMap.keys.any { managers[it]?.isConnected() != true }
        if (anyDisconnected) {
            startConnectTimer()
        }
        connectionDetailsMap.values.forEach { details ->
            initAndConnect(
                details.serverUri,
                details.clientId,
                details.username,
                details.password,
                details.topic,
                details.qos,
                getApplication()
            )
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

    private fun scheduleInitialRetry(serverUri: String) {
        val details = connectionDetailsMap[serverUri] ?: return

        // If we've connected at least once, avoid our own retry loop and rely on Paho's
        // automatic reconnect instead. This prevents constant disconnect/reconnect flapping
        // once the broker is generally healthy.
        if (everConnected[serverUri] == true) return

        // If already connected, no need to retry
        if (managers[serverUri]?.isConnected() == true) return

        // Avoid overlapping retry jobs
        if (retryJobs[serverUri]?.isActive == true) return

        val attempt = retryAttempts[serverUri] ?: 0
        val lowNetwork = isLowBandwidthNetwork()
        // Aggressive first-time backoff for faster initial connect:
        // 0s, 1s, 2s, 4s, 8s, then max 12s.
        // Keeps startup responsive while still adding bounded spacing.
        val delayMs = if (lowNetwork) {
            when {
                attempt == 0 -> 0L
                attempt == 1 -> 3000L
                attempt == 2 -> 6000L
                attempt == 3 -> 12000L
                attempt == 4 -> 20000L
                else -> 30000L
            }
        } else {
            when {
                attempt == 0 -> 0L
                attempt == 1 -> 1000L
                attempt == 2 -> 2000L
                attempt == 3 -> 4000L
                attempt == 4 -> 8000L
                else -> 12000L
            }
        }

        retryJobs[serverUri] = viewModelScope.launch {
            val logMsg = "Retrying initial connection for $serverUri in ${delayMs / 1000}s (Attempt ${attempt + 1})"
            android.util.Log.d("MqttViewModel", logMsg)
            FileLogger.logError(getApplication(), "MQTT_RETRY", "[$serverUri] $logMsg")
            
            delay(delayMs)
            
            // Check if still disconnected before firing
            if (managers[serverUri]?.isConnected() == true) {
                android.util.Log.d("MqttViewModel", "Skipping scheduled retry for $serverUri: already connected.")
                return@launch
            }

            retryAttempts[serverUri] = attempt + 1
            _mqttRetryAttempt.postValue(retryAttempts.values.maxOrNull() ?: (attempt + 1))
            
            val details = connectionDetailsMap[serverUri]
            if (details != null) {
                initAndConnect(
                    details.serverUri,
                    details.clientId,
                    details.username,
                    details.password,
                    details.topic,
                    details.qos,
                    getApplication()
                )
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
                managers[serverUri]?.close()
                managers.remove(serverUri)
                reachabilityJobs[serverUri]?.cancel()
                reachabilityJobs.remove(serverUri)
                val current = _connectionStatusMap.value?.toMutableMap() ?: mutableMapOf()
                current.remove(serverUri)
                _connectionStatusMap.postValue(current)
            }
        }
    }

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
            val updatedList = list.take(15)
            internalTokenMap[trimmedCounter] = updatedList
            _tokensPerCounter.postValue(internalTokenMap.toMap())

            persistToken(trimmedCounter, trimmedToken)
            true // Announcement should happen
        }
    }

    /**
     * Atomically add token to both primary and fallback keys in one map update.
     * Prevents race where two processTokenUpdate calls overwrite each other.
     * Returns true if the token was new or moved for the primary key (should announce).
     */
    suspend fun processTokenUpdateForKeys(
        primaryKey: String,
        token: String,
        fallbackKey: String?
    ): Boolean {
        val trimmedPrimary = primaryKey.trim()
        val trimmedToken = token.trim()
        val trimmedFallback = fallbackKey?.trim()

        if (trimmedToken == "0" || trimmedToken.isBlank() || trimmedPrimary == "0" || trimmedPrimary.isBlank()) return false
        if (trimmedToken.contains("CAL", ignoreCase = true)) return false

        return stateMutex.withLock {

            val keysToUpdate = mutableListOf(trimmedPrimary)
            if (trimmedFallback != null && trimmedFallback != trimmedPrimary) keysToUpdate.add(
                trimmedFallback
            )

            var shouldAnnounce = false

            for (key in keysToUpdate) {
                val list = (internalTokenMap[key] ?: emptyList()).toMutableList()
                val existingPosition = list.indexOf(trimmedToken)

                // If it's already the most recent for this key
                if (existingPosition == 0) {
                    if (key == trimmedPrimary) {
                        val lastTime = announcedTokenTimestamps["${key}_$trimmedToken"] ?: 0L
                        if (System.currentTimeMillis() - lastTime > 10000L) {
                            shouldAnnounce = true
                        }
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
                internalTokenMap[key] = list.take(15)
                persistToken(key, trimmedToken)
            }

            if (shouldAnnounce) {
                _tokensPerCounter.postValue(internalTokenMap.toMap())
            }

            shouldAnnounce
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
            // Semantic parsing is pure/CPU-bound; keep it on Default.
            val result = SemanticMqttParser.parse(message, topic)
            if (result != null) {
                val (counter, token) = result
                val buttonKey = normalizeButtonKey(counter)
                if (buttonKey != null) {
                    markKeypadSeen(buttonKey)
                }
                if (token != "0" && token.isNotBlank() && counter != "0" && counter.isNotBlank()) {
                    if (buttonKey != null) {
                        markDispenseSeen(buttonKey)
                    }
                    val key = "${counter.trim()}_${token.trim()}"
                    val now = System.currentTimeMillis()
                    val lastQueued = queuedTokenTimestamps[key] ?: 0L
                    // Pre-enqueue dedup: drop immediate-next duplicates within 10s window.
                    if (now - lastQueued < 10000L) {
                        return
                    }
                    queuedTokenTimestamps[key] = now
                    if (queuedTokenTimestamps.size > 500) {
                        queuedTokenTimestamps.entries.removeIf { now - it.value > 60000L }
                    }

                    // Log to the new detailed token_records table
                    saveTokenRecord(message, counter, token)

                    // Channel has UNLIMITED capacity; enqueue without blocking the MQTT thread.
                    announcementQueueSize.incrementAndGet()
                    tokenUpdateChannel.send(counter to token)
                }
            }
        } catch (e: Exception) {
            // Swallow and log – a bad payload should never break the stream.
            android.util.Log.w(
                "MqttViewModel",
                "Failed to parse MQTT message '$message' on topic '$topic': ${e.message}"
            )
        }
    }

    private fun normalizeButtonKey(counter: String?): String? {
        val raw = counter?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val asInt = raw.toIntOrNull() ?: return null
        return asInt.toString()
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
                val matchingCounter = counters.find { 
                    it.code?.trim() == counterKey.trim() || it.defaultCode?.trim() == counterKey.trim() 
                }
                
                val counterId = matchingCounter?.counterId?.toIntOrNull() ?: 0
                val counterName = matchingCounter?.name ?: matchingCounter?.defaultName ?: counterKey
                val serial = extractKeypadSerial(message) ?: "UNKNOWN"
                
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
            } catch (e: Exception) {
                android.util.Log.e("MqttViewModel", "Failed to save detailed token record", e)
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
                FileLogger.logError(
                    getApplication(),
                    "MqttLiveness",
                    "No MQTT traffic for $serverUri for >${timeoutMs / 1000}s; forcing reconnect"
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
                delay(5_000L)
                staleReconnectInFlight.remove(serverUri)
            }
        }
    }

    companion object {
        private const val MQTT_MESSAGE_LIVENESS_TIMEOUT_MS = 120_000L
        private const val MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT = 4
        private const val MQTT_MESSAGE_LIVENESS_TIMEOUT_MS_LOW_NETWORK = 180_000L
        private const val MQTT_LIVENESS_STALE_STRIKES_BEFORE_RECONNECT_LOW_NETWORK = 6
        private const val CONFIG_REFRESH_DEBOUNCE_MS = 15_000L
        private const val INDICATOR_STALE_TIMEOUT_MS = 300_000L
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

    private fun requestConfigRefresh(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastConfigRefreshAtMs >= CONFIG_REFRESH_DEBOUNCE_MS) {
            lastConfigRefreshAtMs = now
            android.util.Log.i("MqttViewModel", "$reason; requesting TV config refresh")
            configRefreshRequests.trySend(Unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageLivenessWatchdogJob?.cancel()
        messageLivenessWatchdogJob = null
        indicatorWatchdogJob?.cancel()
        indicatorWatchdogJob = null
        val toClose = managers.values.toList()
        managers.clear()
        reachabilityJobs.values.forEach { it.cancel() }
        reachabilityJobs.clear()
        viewModelScope.launch(Dispatchers.IO) {
            toClose.forEach { it.close() }
        }
    }
}
