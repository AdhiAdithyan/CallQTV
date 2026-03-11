package com.softland.callqtv.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.data.local.ConnectedDeviceEntity
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.repository.TvConfigRepository
import com.softland.callqtv.data.repository.TvConfigResult
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.Variables
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class TokenDisplayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TvConfigRepository(application)
    
    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _config = MutableLiveData<TvConfigEntity?>(null)
    val config: LiveData<TvConfigEntity?> = _config
    
    private val _counters = MutableLiveData<List<CounterEntity>>(emptyList())
    val counters: LiveData<List<CounterEntity>> = _counters
    
    private val _adFiles = MutableLiveData<List<AdFileEntity>>(emptyList())
    val adFiles: LiveData<List<AdFileEntity>> = _adFiles

    private val _connectedDevices = MutableLiveData<List<ConnectedDeviceEntity>>(emptyList())
    val connectedDevices: LiveData<List<ConnectedDeviceEntity>> = _connectedDevices
    
    private val _daysUntilExpiry = MutableLiveData<Int?>(null)
    val daysUntilExpiry: LiveData<Int?> = _daysUntilExpiry
    
    private val _currentDateTime = MutableLiveData("")
    val currentDateTime: LiveData<String> = _currentDateTime

    private var _macAddress = ""
    val macAddress: String get() = _macAddress
    private val _isPendingApproval = MutableLiveData(false)
    val isPendingApproval: LiveData<Boolean> = _isPendingApproval

    private val authPrefs = application.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)

    init {
        // Initialize macAddress on a background thread to avoid blocking main thread at startup
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _macAddress = Variables.getMacId(getApplication())
        }
        startDateTimeUpdates()
        startExpiryCheck()
    }

    private var is24HourFormat = true

    fun setTimeFormat(is24Hour: Boolean) {
        is24HourFormat = is24Hour
    }

    private fun startDateTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val pattern = if (is24HourFormat) "dd-MM-yyyy HH:mm:ss" else "dd-MM-yyyy hh:mm:ss a"
                val formatter = DateTimeFormatter.ofPattern(pattern)
                _currentDateTime.postValue(now.format(formatter))
                delay(1000L)
            }
        }
    }

    private fun startExpiryCheck() {
        viewModelScope.launch {
            while (true) {
                _daysUntilExpiry.value = computeDaysUntilExpiry()
                delay(15 * 60 * 1000L)
            }
        }
    }

    private fun computeDaysUntilExpiry(): Int? {
        val rawEnd = authPrefs.getString(PreferenceHelper.product_license_end, null).orEmpty()
        if (rawEnd.isBlank()) return null
        return try {
            val datePart = rawEnd.trim().split(" ").getOrElse(0) { "" }
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            val endDate = LocalDate.parse(datePart, formatter)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, endDate).toInt()
        } catch (_: Exception) {
            null
        }
    }

    fun loadData(mqttViewModel: MqttViewModel) {
        viewModelScope.launch {
            _isLoading.value = true
            _isPendingApproval.value = false

            // Seed the UI immediately with the last known token state from local DB
            mqttViewModel.loadPersistedHistory()
            
            // Ensure macAddress is fetched before making API calls
            if (_macAddress.isEmpty()) {
                _macAddress = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { 
                    Variables.getMacId(getApplication()) 
                }
            }

            val customerIdInt = authPrefs.getInt(PreferenceHelper.customer_id, 0)
            val customerId = String.format(Locale.ROOT, "%04d", customerIdInt)

            try {
                // Outer safety timeout of 30 seconds to ensure the dialog ALWAYS closes
                kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    val result = repository.fetchAndCacheTvConfig(_macAddress, customerId)
                    when (result) {
                        is TvConfigResult.Success -> {
                            _config.value = result.entity
                            _errorMessage.value = null
                        }
                        is TvConfigResult.Pending -> {
                            _config.value = repository.getCachedConfig(_macAddress, customerId)
                            _isPendingApproval.value = true
                            _errorMessage.value = result.message
                        }
                        is TvConfigResult.Error -> {
                            _config.value = repository.getCachedConfig(_macAddress, customerId)
                            _errorMessage.value = if (result.licenceStatus != null) {
                                "${result.message}\n\n${result.licenceStatus}"
                            } else {
                                result.message
                            }
                        }
                    }
                    _counters.value = repository.getCounters(_macAddress, customerId)
                    _adFiles.value = repository.getAdFiles(_macAddress, customerId)
                    _connectedDevices.value = repository.getConnectedDevices(_macAddress, customerId)

                    _config.value?.let { cfg ->
                        // Launch concurrently so loading spinner dismisses immediately
                        launch { initMqttIfNeeded(cfg, mqttViewModel) }
                    }
                } ?: run {
                    // Timeout occurred
                    _errorMessage.value = "Connection timeout. Please check your network and try again."
                }
            } catch (e: Exception) {
                _config.value = repository.getCachedConfig(_macAddress, customerId)
                _counters.value = repository.getCounters(_macAddress, customerId)
                _adFiles.value = repository.getAdFiles(_macAddress, customerId)
                _connectedDevices.value = repository.getConnectedDevices(_macAddress, customerId)
                if (_config.value == null) {
                    _errorMessage.value = "Failed to load TV configuration: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun initMqttIfNeeded(cfg: TvConfigEntity, mqttViewModel: MqttViewModel) {
        val customerIdInt = authPrefs.getInt(PreferenceHelper.customer_id, 0)
        val customerId = String.format(Locale.ROOT, "%04d", customerIdInt)
        val brokers = repository.getMappedBrokerEntities(_macAddress, customerId)

        if (brokers.isEmpty()) {
            // Fallback to single broker if dedicated table is empty (unlikely with recent code)
            repository.getMappedBrokerEntity(_macAddress, customerId)?.let { broker ->
                connectToBroker(broker, mqttViewModel)
            }
        } else {
            brokers.forEach { broker ->
                connectToBroker(broker, mqttViewModel)
            }
        }
    }

    private fun connectToBroker(broker: com.softland.callqtv.data.local.MappedBrokerEntity, mqttViewModel: MqttViewModel) {
        val host = broker.host?.trim().orEmpty()
        val topic = broker.topic?.trim().orEmpty()

        if (host.isNotEmpty() && topic.isNotEmpty()) {
            val port = broker.port?.trim().orEmpty()
            val mqttPort = port.ifEmpty { "1883" }
            val serverUri = "tcp://$host:$mqttPort"
            val clientId = "callqtv_${_macAddress.replace(":", "")}_${broker.brokerId ?: Random().nextInt(1000)}"

            mqttViewModel.initAndConnect(
                serverUri = serverUri,
                clientId = clientId,
                username = broker.ssid, // SSID is used as username in certain setups
                password = broker.password,
                topic = topic,
                qos = 1,
                context = getApplication()
            )
        }
    }
}
