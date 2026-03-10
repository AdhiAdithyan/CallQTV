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

    private val tokenHistoryRepo: TokenHistoryRepository by lazy {
        TokenHistoryRepository(AppDatabase.getInstance(application))
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

    private val _isAutoRetryExhausted = MutableLiveData<Boolean>(false)
    fun isAutoRetryExhausted(): LiveData<Boolean> = _isAutoRetryExhausted

    val tokenUpdateChannel = kotlinx.coroutines.channels.Channel<Pair<String, String>>(capacity = kotlinx.coroutines.channels.Channel.UNLIMITED)

    private val _tokensPerCounter = MutableLiveData<Map<String, List<String>>>(emptyMap())
    fun getTokensPerCounter(): LiveData<Map<String, List<String>>> = _tokensPerCounter

    private val announcedTokenCalls = mutableSetOf<String>()

    fun isAlreadyAnnounced(counterKey: String, token: String): Boolean {
        val key = "${counterKey}_$token"
        return announcedTokenCalls.contains(key)
    }

    fun markAsAnnounced(counterKey: String, token: String) {
        announcedTokenCalls.add("${counterKey}_$token")
    }

    fun loadPersistedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val persisted = tokenHistoryRepo.loadAll()
                if (persisted.isNotEmpty()) {
                    _tokensPerCounter.postValue(persisted)
                }
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "Failed to load token history: ${e.message}")
            }
        }
    }

    fun clearTokenHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tokenHistoryRepo.clearAll()
                _tokensPerCounter.postValue(emptyMap())
                synchronized(this@MqttViewModel) {
                    announcedTokenCalls.clear()
                }
            } catch (e: Exception) {
                android.util.Log.w("MqttViewModel", "Failed to clear token history: ${e.message}")
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
                        _isAutoRetryExhausted.postValue(false)
                        _errorMessage.postValue("")
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

    /**
     * Checks if the character sequence after "$0" matches "keypad_sl_no_1"
     * for any connected device of type "KEYPAD".
     */
    private suspend fun isValidKeypadMessage(message: String): Boolean {
        // Extract the potential serial from the message (characters after "$0" up to where counter info starts)
        // In the fixed protocol "$0<SERIAL>C<TOKEN>*", if index 16 is Counter, then 2..15 is Serial (14 chars).
        val serialInMessage = try {
            message.substring(2, 16).trim()
        } catch (e: Exception) {
            return false
        }

        return withContext(Dispatchers.IO) {
            val authPrefs = getApplication<Application>().getSharedPreferences(com.softland.callqtv.data.local.AppSharedPreferences.AUTHENTICATION, android.content.Context.MODE_PRIVATE)
            val customerIdInt = authPrefs.getInt(com.softland.callqtv.utils.PreferenceHelper.customer_id, 0)
            val customerId = String.format(java.util.Locale.ROOT, "%04d", customerIdInt)
            val macAddress = com.softland.callqtv.utils.Variables.getMacId(getApplication())

            val devices = connectedDeviceDao.getByMacAndCustomer(macAddress, customerId)

            var matched = false

            devices.forEach { device ->
                if (device.deviceType?.uppercase() == "KEYPAD" && !device.configJson.isNullOrBlank()) {
                    try {
                        val configMap = gson.fromJson(device.configJson, Map::class.java)
                        val keypadSlNo = configMap["keypad_sl_no_1"]?.toString()
                        if (keypadSlNo != null && keypadSlNo == serialInMessage) {
                            matched = true
                        } else {
                            android.util.Log.d(
                                "MqttViewModel",
                                "Keypad serial mismatch: fromMessage='$serialInMessage', keypad_sl_no_1='${keypadSlNo ?: "null"}', deviceSerial='${device.serialNumber}'"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.d(
                            "MqttViewModel",
                            "Failed to parse keypad configJson for deviceSerial='${device.serialNumber}': ${e.message}"
                        )
                    }
                }
            }

            matched
        }
    }

    fun retryConnect() {
        _errorMessage.postValue("")
        _isAutoRetryExhausted.postValue(false)
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

    init {
        viewModelScope.launch {
            while (true) {
                val status = _connectionStatusMap.value ?: emptyMap()
                if (status.isNotEmpty()) {
                    if (status.any { !it.value }) retryConnect()
                }
                delay(10000L)
            }
        }
    }

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

    fun processTokenUpdate(counter: String, token: String): Boolean {
        if (token == "0" || token.isBlank() || counter == "0" || counter.isBlank()) return false
        if (token.contains("CAL", ignoreCase = true)) return false

        val currentMap = _tokensPerCounter.value?.toMutableMap() ?: mutableMapOf()
        val list = (currentMap[counter] ?: emptyList()).toMutableList()

        val existingPosition = list.indexOf(token)
        if (existingPosition == 0) return false // Already at top

        if (existingPosition > 0) {
            list.removeAt(existingPosition)
        }

        list.add(0, token)
        currentMap[counter] = list.take(15)
        _tokensPerCounter.postValue(currentMap)

        if (existingPosition > 0) {
            synchronized(this) {
                announcedTokenCalls.remove("${counter}_$token")
            }
        }

        persistToken(counter, token)
        return true
    }

    /**
     * Atomically add token to both primary and fallback keys in one map update.
     * Prevents race where two processTokenUpdate calls overwrite each other.
     * Returns true if the token was new or moved for the primary key (should announce).
     */
    fun processTokenUpdateForKeys(primaryKey: String, token: String, fallbackKey: String?): Boolean {
        if (token == "0" || token.isBlank() || primaryKey == "0" || primaryKey.isBlank()) return false
        if (token.contains("CAL", ignoreCase = true)) return false

        val currentMap = _tokensPerCounter.value?.toMutableMap() ?: mutableMapOf()
        val keysToUpdate = mutableListOf(primaryKey)
        if (fallbackKey != null && fallbackKey != primaryKey) keysToUpdate.add(fallbackKey)

        var shouldAnnounce = false
        for (key in keysToUpdate) {
            val list = (currentMap[key] ?: emptyList()).toMutableList()
            val existingPosition = list.indexOf(token)
            if (existingPosition == 0 && key == primaryKey) {
                shouldAnnounce = false
                continue
            }
            if (existingPosition > 0) list.removeAt(existingPosition)
            list.add(0, token)
            currentMap[key] = list.take(15)
            if (key == primaryKey) shouldAnnounce = true
            persistToken(key, token)
            if (existingPosition > 0) {
                synchronized(this) {
                    announcedTokenCalls.remove("${key}_$token")
                }
            }
        }
        _tokensPerCounter.postValue(currentMap)
        return shouldAnnounce
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
