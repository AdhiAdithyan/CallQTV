package com.softland.callqtv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.repository.MqttClientManager
import com.softland.callqtv.data.repository.TokenHistoryRepository
import com.softland.callqtv.utils.SemanticMqttParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Random

class MqttViewModel(application: Application) : AndroidViewModel(application) {

    private val managers = mutableMapOf<String, MqttClientManager>()
    private val connectionDetailsMap = mutableMapOf<String, MqttConnectionDetails>()
    private val stateMutex = Mutex()
    private val gson = Gson()
    
    private val retryAttempts = mutableMapOf<String, Int>()
    private val retryJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    private val tokenHistoryRepo: TokenHistoryRepository by lazy {
        TokenHistoryRepository(AppDatabase.getInstance(application), application)
    }

    private val connectedDeviceDao by lazy {
        AppDatabase.getInstance(application).connectedDeviceDao()
    }

    private data class MqttConnectionDetails(
        val serverUri: String,
        val clientId: String,
        val username: String?,
        val password: String?,
        val topic: String,
        val qos: Int
    )

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

    private fun startConnectTimer() {
        _isConnectingToMqtt.postValue(true)
        _connectTimer.postValue(0)
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            var time = 0
            while(true) {
                delay(1000)
                time++
                _connectTimer.postValue(time)
            }
        }
    }

    private fun stopConnectTimer() {
        _isConnectingToMqtt.postValue(false)
        timerJob?.cancel()
        timerJob = null
    }

    private val _isAutoRetryExhausted = MutableLiveData<Boolean>(false)
    fun isAutoRetryExhausted(): LiveData<Boolean> = _isAutoRetryExhausted

    val tokenUpdateChannel = kotlinx.coroutines.channels.Channel<Pair<String, String>>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)

    private val _tokensPerCounter = MutableLiveData<Map<String, List<String>>>(emptyMap())
    fun getTokensPerCounter(): LiveData<Map<String, List<String>>> = _tokensPerCounter

    // Thread-safe internal state to prevent race conditions during updates
    private val internalTokenMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val announcedTokenTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var isHistoryLoaded = false

    /**
     * Deduplicate rapid MQTT messages. Returns true if announced within the last 2 seconds.
     */
    fun isAlreadyAnnounced(counterKey: String, token: String): Boolean {
        val key = "${counterKey.trim()}_${token.trim()}"
        val lastTime = announcedTokenTimestamps[key] ?: 0L
        return (System.currentTimeMillis() - lastTime) < 2000L
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
                    android.util.Log.w("MqttViewModel", "Failed to load token history: ${e.message}")
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
                    android.util.Log.w("MqttViewModel", "Failed to clear token history: ${e.message}")
                }
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
        connectionDetailsMap[serverUri] = MqttConnectionDetails(serverUri, clientId, username, password, topic, qos)

        val existing = managers[serverUri]
        if (existing != null) {
            if (existing.clientId == clientId) {
                existing.subscribe(topic, qos)
                if (!existing.isConnected()) existing.connect(username, password)
                return
            } else {
                removeManager(serverUri)
            }
        }

        val manager = MqttClientManager(context.applicationContext, serverUri, clientId).apply {
            setMqttListener(object : MqttClientManager.MqttListener {
                override fun onMessageReceived(topic: String, message: String) {
                    viewModelScope.launch {
                        val trimmed = message.trim()

                        if (!trimmed.contains("CAL0K")) return@launch
                        if (!trimmed.startsWith("$")) return@launch
                        if (trimmed.length < 24) return@launch

                        // Validate against Keypad Serial Number
                        if (!isValidKeypadMessage(trimmed)) {
                            // Detailed logging is done inside isValidKeypadMessage; keep this as a high-level marker
                            android.util.Log.d("MqttViewModel", "Ignored message: Keypad serial mismatch or device not found")
                            return@launch
                        }

                        _receivedMessage.postValue(trimmed)
                        _lastPayload.postValue(trimmed)
                        parseMqttMessage(topic, trimmed)
                    }
                }

                override fun onConnectionStatus(isConnected: Boolean) {
                    updateStatus(serverUri, isConnected)
                    if (isConnected) {
                        stopConnectTimer()
                        _isAutoRetryExhausted.postValue(false)
                        _errorMessage.postValue("")
                        // Connection success: reset backoff
                        retryAttempts[serverUri] = 0
                        retryJobs[serverUri]?.cancel()
                    }
                }

                override fun onError(error: String) {
                    stopConnectTimer()
                    _errorMessage.postValue("[$serverUri] $error")
                    // Initial connection failed or lost before Paho could handle auto-reconnect
                    scheduleInitialRetry(serverUri)
                }

                override fun onAutoRetryExhausted() {
                    _isAutoRetryExhausted.postValue(true)
                }
            })
        }
        
        startConnectTimer()
        managers[serverUri] = manager
        manager.subscribe(topic, qos)
        manager.connect(username, password)
    }

    /**
     * Checks if the character sequence after "$0" matches a connected device of type "KEYPAD".
     */
    private suspend fun isValidKeypadMessage(message: String): Boolean {
        if (message.length < 16) return false
        val serialInMessage = message.substring(2, 16).trim()
        if (serialInMessage.isEmpty()) return false

        return withContext(Dispatchers.IO) {
            val authPrefs = getApplication<Application>().getSharedPreferences(com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION, android.content.Context.MODE_PRIVATE)
            val customerId = String.format(java.util.Locale.ROOT, "%04d", authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0))
            val macAddress = com.softland.callqtv.utils.Variables.getMacId(getApplication())

            connectedDeviceDao.getByMacAndCustomer(macAddress, customerId).any { device ->
                device.deviceType?.uppercase() == "KEYPAD" && device.configJson?.contains(serialInMessage) == true
            }
        }
    }

    fun retryConnect() {
        _errorMessage.postValue("")
        _isAutoRetryExhausted.postValue(false)
        startConnectTimer()
        connectionDetailsMap.values.forEach { details ->
            initAndConnect(details.serverUri, details.clientId, details.username, details.password, details.topic, details.qos, getApplication())
        }
    }

    private fun updateStatus(serverUri: String, isConnected: Boolean) {
        viewModelScope.launch {
            stateMutex.withLock {
                val current = _connectionStatusMap.value?.toMutableMap() ?: mutableMapOf()
                current[serverUri] = isConnected
                _connectionStatusMap.postValue(current)
                _connectionStatus.postValue(current.values.all { it })
            }
        }
    }

    private fun scheduleInitialRetry(serverUri: String) {
        val details = connectionDetailsMap[serverUri] ?: return
        
        // If already connected, no need to retry
        if (managers[serverUri]?.isConnected() == true) return
        
        // Avoid overlapping retry jobs
        if (retryJobs[serverUri]?.isActive == true) return

        val attempt = retryAttempts[serverUri] ?: 0
        // Exponential backoff: 5s, 10s, 30s, then capped at 1 min
        val delayMs = when (attempt) {
            0 -> 5000L
            1 -> 10000L
            2 -> 30000L
            else -> 60000L
        }

        retryJobs[serverUri] = viewModelScope.launch {
            android.util.Log.d("MqttViewModel", "Retrying initial connection for $serverUri in ${delayMs/1000}s (Attempt ${attempt + 1})")
            delay(delayMs)
            retryAttempts[serverUri] = attempt + 1
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

// Redundant retry loop removed. Relying on Paho's isAutomaticReconnect = true 
// for optimized and conflict-free reconnection management.

    private fun removeManager(serverUri: String) {
        viewModelScope.launch {
            stateMutex.withLock {
                managers[serverUri]?.close()
                managers.remove(serverUri)
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
            // If history isn't loaded yet, try to load it first
            if (!isHistoryLoaded) {
                val persisted = withContext(Dispatchers.IO) { tokenHistoryRepo.loadAll() }
                persisted.forEach { (k, v) -> 
                    if (!internalTokenMap.containsKey(k)) internalTokenMap[k] = v 
                }
                isHistoryLoaded = true
            }

            val list = (internalTokenMap[trimmedCounter] ?: emptyList()).toMutableList()
            val existingPosition = list.indexOf(trimmedToken)

            // If already at top, per user request: DO NOT announce
            if (existingPosition == 0) {
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
    suspend fun processTokenUpdateForKeys(primaryKey: String, token: String, fallbackKey: String?): Boolean {
        val trimmedPrimary = primaryKey.trim()
        val trimmedToken = token.trim()
        val trimmedFallback = fallbackKey?.trim()

        if (trimmedToken == "0" || trimmedToken.isBlank() || trimmedPrimary == "0" || trimmedPrimary.isBlank()) return false
        if (trimmedToken.contains("CAL", ignoreCase = true)) return false

        return stateMutex.withLock {
            if (!isHistoryLoaded) {
                val persisted = withContext(Dispatchers.IO) { tokenHistoryRepo.loadAll() }
                persisted.forEach { (k, v) -> 
                    if (!internalTokenMap.containsKey(k)) internalTokenMap[k] = v 
                }
                isHistoryLoaded = true
            }

            val keysToUpdate = mutableListOf(trimmedPrimary)
            if (trimmedFallback != null && trimmedFallback != trimmedPrimary) keysToUpdate.add(trimmedFallback)

            var shouldAnnounce = false

            for (key in keysToUpdate) {
                val list = (internalTokenMap[key] ?: emptyList()).toMutableList()
                val existingPosition = list.indexOf(trimmedToken)
                
                // If it's already the most recent for this key, keep moving but don't set shouldAnnounce
                if (existingPosition == 0) {
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

    private fun parseMqttMessage(topic: String, message: String) {
        viewModelScope.launch(Dispatchers.Default) {
             try {
                 val result = SemanticMqttParser.parse(message, topic)
                 if (result != null) {
                     val (counter, token) = result
                     if (token != "0" && token.isNotBlank() && counter != "0" && counter.isNotBlank()) {
                         tokenUpdateChannel.send(counter to token)
                     }
                 }
             } catch (e: Exception) {
                 // Log error if needed
             }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val toClose = managers.values.toList()
        managers.clear()
        viewModelScope.launch(Dispatchers.IO) {
            toClose.forEach { it.close() }
        }
    }
}
