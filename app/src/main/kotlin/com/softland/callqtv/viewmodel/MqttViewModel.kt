package com.softland.callqtv.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.repository.MqttClientManager
import com.softland.callqtv.data.repository.TokenHistoryRepository
import com.softland.callqtv.utils.SemanticMqttParser
import com.softland.callqtv.utils.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MqttViewModel(application: Application) : AndroidViewModel(application) {

    // Map of serverUri to MqttClientManager
    private val managers = mutableMapOf<String, MqttClientManager>()
    private val connectionDetailsMap = mutableMapOf<String, MqttConnectionDetails>()

    // Delayed auto-retry when broker disconnects; cancelled when any broker reconnects
    private var reconnectJob: Job? = null

    // Token history persistence
    private val tokenHistoryRepo: TokenHistoryRepository by lazy {
        TokenHistoryRepository(AppDatabase.getInstance(application))
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

    // Last raw MQTT payload for display (e.g. footer)
    private val _lastPayload = MutableLiveData<String>("")
    fun getLastPayload(): LiveData<String> = _lastPayload

    private val _connectionStatusMap = MutableLiveData<Map<String, Boolean>>(emptyMap())
    fun getConnectionStatusMap(): LiveData<Map<String, Boolean>> = _connectionStatusMap

    // For backward compatibility with single status display
    private val _connectionStatus = MutableLiveData<Boolean>(false)
    fun getConnectionStatus(): LiveData<Boolean> = _connectionStatus

    private val _errorMessage = MutableLiveData<String>("")
    fun getErrorMessage(): LiveData<String> = _errorMessage

    private val _isAutoRetryExhausted = MutableLiveData<Boolean>(false)
    fun isAutoRetryExhausted(): LiveData<Boolean> = _isAutoRetryExhausted

    // Unbounded channel so every MQTT token update is queued and processed in order,
    // even if messages arrive only a few milliseconds apart.
    val tokenUpdateChannel = kotlinx.coroutines.channels.Channel<Pair<String, String>>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)
    // val latestTokenFlow removed in favor of Channel for sequential processing

    private val _tokensPerCounter = MutableLiveData<Map<String, List<String>>>(emptyMap())
    fun getTokensPerCounter(): LiveData<Map<String, List<String>>> = _tokensPerCounter

    // Persistent set to track announced calls (Counter+Token) during the session
    private val announcedTokenCalls = mutableSetOf<String>()

    fun isAlreadyAnnounced(counterKey: String, token: String): Boolean {
        val key = "${counterKey}_$token"
        return announcedTokenCalls.contains(key)
    }

    fun markAsAnnounced(counterKey: String, token: String) {
        announcedTokenCalls.add("${counterKey}_$token")
    }

    /**
     * Load persisted token history from Room and seed the in-memory map.
     * Called once at startup so the UI shows the last known state immediately.
     */
    fun loadPersistedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val persisted = tokenHistoryRepo.loadAll()
                if (persisted.isNotEmpty()) {
                    _tokensPerCounter.postValue(persisted)
                    android.util.Log.d("MqttViewModel", "Loaded persisted token history: ${persisted.keys}")
                }
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "Failed to load token history: ${e.message}")
            }
        }
    }

    /**
     * Clear all persisted token history (e.g. from Settings). UI will show empty tokens until new ones arrive.
     */
    fun clearTokenHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tokenHistoryRepo.clearAll()
                _tokensPerCounter.postValue(emptyMap())
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "Failed to clear token history: ${e.message}")
            }
        }
    }

    /**
     * Initialize or update an MQTT client for a specific broker.
     */
    fun initAndConnect(
        serverUri: String,
        clientId: String,
        username: String?,
        password: String?,
        topic: String,
        qos: Int,
        context: android.content.Context
    ) {
        android.util.Log.i("MqttViewModel", "INIT CONNECT: server=$serverUri, clientId=$clientId, topic=$topic")
        connectionDetailsMap[serverUri] = MqttConnectionDetails(serverUri, clientId, username, password, topic, qos)

        val existing = managers[serverUri]
        if (existing != null) {
            if (existing.clientId == clientId) {
                // Already exists, just update subscription and connect if needed
                existing.subscribe(topic, qos)
                if (!existing.isConnected()) {
                    existing.connect(username, password)
                }
                return
            } else {
                // Different client ID for same URI? Close and recreate
                removeManager(serverUri)
            }
        }

        // Use applicationContext to avoid memory leaks
        val manager = MqttClientManager(context.applicationContext, serverUri, clientId).apply {
            setMqttListener(object : MqttClientManager.MqttListener {
                override fun onMessageReceived(topic: String, message: String) {
                    _receivedMessage.postValue(message)
                    _lastPayload.postValue(message)
                    parseMqttMessage(topic, message)
                }

                override fun onConnectionStatus(isConnected: Boolean) {
                    updateStatus(serverUri, isConnected)
                    if (isConnected) {
                        _isAutoRetryExhausted.postValue(false)
                        _errorMessage.postValue("")
                        viewModelScope.launch {
                            reconnectJob?.cancel()
                            reconnectJob = null
                        }
                    } else {
                        // Schedule app-level auto retry after delay (dialog still shows for manual retry)
                        viewModelScope.launch {
                            reconnectJob?.cancel()
                            reconnectJob = launch {
                                delay(5000L)
                                retryConnect()
                            }
                        }
                    }
                }

                override fun onError(error: String) {
                    _errorMessage.postValue("[$serverUri] $error")
                }

                override fun onAutoRetryExhausted() {
                    _isAutoRetryExhausted.postValue(true)
                }
            })
        }
        managers[serverUri] = manager
        manager.subscribe(topic, qos)
        manager.connect(username, password)
    }

    fun retryConnect() {
        _errorMessage.postValue("")
        _isAutoRetryExhausted.postValue(false)
        connectionDetailsMap.values.forEach { details ->
            initAndConnect(
                details.serverUri,
                details.clientId,
                details.username,
                details.password,
                details.topic,
                details.qos,
                getApplication() // Use application context
            )
        }
    }

    private fun updateStatus(serverUri: String, isConnected: Boolean) {
        synchronized(this) {
            val current = _connectionStatusMap.value?.toMutableMap() ?: mutableMapOf()
            current[serverUri] = isConnected
            _connectionStatusMap.postValue(current)

            // Single status: true if ALL are connected
            _connectionStatus.postValue(current.values.all { it })
            
            android.util.Log.d("MqttViewModel", "STATUS UPDATE: $serverUri is ${if (isConnected) "CONNECTED" else "OFFLINE"}")
        }
    }

    init {
        // Start a watcher to periodically log status and ensure the VM is alive
        viewModelScope.launch {
            while (true) {
                val status = _connectionStatusMap.value ?: emptyMap()
                if (status.isEmpty()) {
                    android.util.Log.v("MqttViewModel", "Watcher: No active brokers configured yet.")
                } else {
                    status.forEach { (uri, connected) ->
                        android.util.Log.i("MqttViewModel", "Watcher Check: [$uri] -> ${if (connected) "ACTIVE" else "DISCONNECTED"}")
                    }
                }
                kotlinx.coroutines.delay(15000L)
            }
        }
    }

    private fun removeManager(serverUri: String) {
        synchronized(this) {
            managers[serverUri]?.close()
            managers.remove(serverUri)
            val current = _connectionStatusMap.value?.toMutableMap() ?: mutableMapOf()
            current.remove(serverUri)
            _connectionStatusMap.postValue(current)
        }
    }

    /**
     * Called by Activity to apply update and trigger UI change just before announcement.
     * Returns true if the token is new or re-called (and should be announced), false otherwise.
     */
    fun processTokenUpdate(counter: String, token: String): Boolean {
        if (token == "0") return false
        if (token.contains("CAL", ignoreCase = true)) {
            android.util.Log.d("MqttViewModel", "Token '$token' contains CAL; skipping display and announcement.")
            return false
        }

        synchronized(this) {
             val currentMap = _tokensPerCounter.value?.toMutableMap() ?: mutableMapOf()
             val key = if (counter.isEmpty()) "__default__" else counter
             val list = (currentMap[key] ?: emptyList()).toMutableList()

             val existingPosition = list.indexOf(token)
             val isAlreadyCurrent  = existingPosition == 0
             val isRecallFromHistory = existingPosition > 0

             return when {
                 isAlreadyCurrent -> {
                     android.util.Log.d("MqttViewModel", "Token '$token' is already current for '$key'. No list change.")
                     false 
                 }
                 isRecallFromHistory -> {
                     list.removeAt(existingPosition)
                     list.add(0, token)
                     currentMap[key] = list.take(15)
                     _tokensPerCounter.postValue(currentMap) // Trigger UI Update
                     
                     // Allow re-announcement
                     announcedTokenCalls.remove("${key}_$token")
                     
                     android.util.Log.d("MqttViewModel", "Re-call: Token '$token' promoted to front for '$key'.")
                     persistToken(key, token)
                     true
                 }
                 else -> {
                     list.add(0, token)
                     currentMap[key] = list.take(15)
                     _tokensPerCounter.postValue(currentMap) // Trigger UI Update
                     
                     android.util.Log.d("MqttViewModel", "New token '$token' added to front for '$key'.")
                     persistToken(key, token)
                     true
                 }
             }
        }
    }

    private fun persistToken(key: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tokenHistoryRepo.saveToken(key, token)
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "Failed to persist token: ${e.message}")
            }
        }
    }

    private fun parseMqttMessage(topic: String, message: String) {
        android.util.Log.i("MqttViewModel", "RAW MQTT MESSAGE RECEIVED: topic=$topic, payload=$message")
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
             try {
                 val result = SemanticMqttParser.parse(message, topic)
                 if (result != null) {
                     val (counter, token) = result
                     android.util.Log.d("MqttViewModel", "PARSED: Counter='$counter', Token='$token' -> Queueing")
                     if (token != "0") {
                         // Suspend until this token is enqueued, preserving strict ordering
                         tokenUpdateChannel.send(counter to token)
                     }
                 } else {
                     android.util.Log.w("MqttViewModel", "PARSE FAILED: '$message'")
                 }
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val toClose = managers.values.toList()
        managers.clear()
        connectionDetailsMap.clear()
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            toClose.forEach { it.close() }
        }
    }
}
