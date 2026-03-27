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
import com.softland.callqtv.utils.AdDownloader
import com.softland.callqtv.utils.FileLogger
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.Variables
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class TokenDisplayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TvConfigRepository(application)
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _config = MutableLiveData<TvConfigEntity?>(null)
    val config: LiveData<TvConfigEntity?> = _config
    
    private val _counters = MutableLiveData<List<CounterEntity>>(emptyList())
    val counters: LiveData<List<CounterEntity>> = _counters
    
    private val _adFiles = MutableLiveData<List<AdFileEntity>>(emptyList())
    val adFiles: LiveData<List<AdFileEntity>> = _adFiles

    private val _localAdFiles = MutableLiveData<List<AdFileEntity>>(emptyList())
    val localAdFiles: LiveData<List<AdFileEntity>> = _localAdFiles
    // Monotonic token to force AdArea remount after each config API cycle.
    private val _adAreaReloadToken = MutableLiveData(0)
    val adAreaReloadToken: LiveData<Int> = _adAreaReloadToken

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

    private val _isLicenseExpired = MutableLiveData(false)
    val isLicenseExpired: LiveData<Boolean> = _isLicenseExpired

    private val authPrefs = application.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)

    // Ensures the full-screen "Loading TV configuration" overlay is shown only once
    // on the very first manual load trigger after app install/launch.
    private var hasShownInitialLoadingOverlay = false
    private var tokenHistorySeeded = false
    private var offlineAdSyncJob: Job? = null
    private var mqttConfigRefreshListenerStarted = false
    private var currentConfigLoadJob: Job? = null
    private var lastMqttRefreshHandledAtMs: Long = 0L
    private var lastOfflineSyncedAds: List<AdFileEntity> = emptyList()

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

    /** Clears the configuration error so the Configuration Error dialog is not shown. */
    fun clearConfigurationError() {
        _errorMessage.value = null
    }

    private fun startDateTimeUpdates() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            while (isActive) {
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

    fun loadData(mqttViewModel: MqttViewModel, forceShowOverlay: Boolean = false) {
        // Prevent overlapping config reloads unless the caller explicitly wants overlay UX.
        if (!forceShowOverlay && currentConfigLoadJob?.isActive == true) return

        // Start a single collector that triggers config refresh based on MQTT responses.
        if (!mqttConfigRefreshListenerStarted) {
            mqttConfigRefreshListenerStarted = true
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                mqttViewModel.configRefreshRequests
                    .receiveAsFlow()
                    .collect {
                        val now = System.currentTimeMillis()
                        if (now - lastMqttRefreshHandledAtMs < MQTT_REFRESH_MIN_INTERVAL_MS) {
                            return@collect
                        }
                        lastMqttRefreshHandledAtMs = now
                        // Non-UI (no overlay) reload triggered by MQTT.
                        loadData(mqttViewModel, forceShowOverlay = false)
                    }
            }
        }

        currentConfigLoadJob = viewModelScope.launch {
            val loadStartMs = System.currentTimeMillis()
            if (forceShowOverlay) {
                _isLoading.value = true
                hasShownInitialLoadingOverlay = true
            } else {
                // For automatic retries or subsequent hidden loads, keep the overlay hidden.
                _isLoading.value = false
            }
            _isPendingApproval.value = false

            // Seed token history only once to avoid repeated disk work on every refresh/retry.
            if (!tokenHistorySeeded) {
                tokenHistorySeeded = true
                mqttViewModel.loadPersistedHistory()
            }
            
            // Ensure macAddress is fetched before making API calls
            if (_macAddress.isEmpty()) {
                _macAddress = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { 
                    Variables.getMacId(getApplication()) 
                }
            }

            val customerIdInt = authPrefs.getInt(PreferenceHelper.customer_id, 0)
            val customerId = String.format(Locale.ROOT, "%04d", customerIdInt)
            val offlineEnabled = PreferenceHelper.isOfflineAdsEnabled(getApplication())

            // Cached-first strategy: render local data immediately while API is in flight.
            val cacheStartMs = System.currentTimeMillis()
            _config.value = repository.getCachedConfig(_macAddress, customerId)
            _counters.value = repository.getCounters(_macAddress, customerId)
            val cachedAds = repository.getAdFiles(_macAddress, customerId)
            _adFiles.value = cachedAds
            _localAdFiles.value = buildEffectiveAdList(
                remoteAds = cachedAds,
                offlineEnabled = offlineEnabled,
                syncedAds = lastOfflineSyncedAds
            )
            _connectedDevices.value = repository.getConnectedDevices(_macAddress, customerId)
            val hasRenderableCache = _config.value != null
            android.util.Log.i(
                "TokenDisplayVMPerf",
                "Cache pre-load took ${System.currentTimeMillis() - cacheStartMs} ms"
            )

            // UX optimization: if we already have cache, stop blocking UI with the
            // full-screen "Loading TV configuration" overlay and continue API sync
            // in the background.
            if (forceShowOverlay && hasRenderableCache) {
                _isLoading.value = false
            }

            try {
                // Safety timeout: allow the network layer (60s x retries) to exhaust itself first
                kotlinx.coroutines.withTimeoutOrNull(300_000L) {
                    val apiStartMs = System.currentTimeMillis()
                    val result = repository.fetchAndCacheTvConfig(_macAddress, customerId)
                    android.util.Log.i(
                        "TokenDisplayVMPerf",
                        "fetchAndCacheTvConfig completed in ${System.currentTimeMillis() - apiStartMs} ms"
                    )
                    when (result) {
                        is TvConfigResult.Success -> {
                            _config.value = result.entity
                            _isLicenseExpired.value = false
                            _errorMessage.value = null
                            
                            // Successful sync: back up the database
                            com.softland.callqtv.utils.DatabaseBackup.backupDatabase(getApplication())
                        }
                        is TvConfigResult.Pending -> {
                            _config.value = repository.getCachedConfig(_macAddress, customerId)
                            _isPendingApproval.value = true
                            _errorMessage.value = result.message
                        }
                        is TvConfigResult.Error -> {
                            _config.value = repository.getCachedConfig(_macAddress, customerId)
                            val msg = if (result.licenceStatus != null) {
                                "${result.message}\n\n${result.licenceStatus}"
                            } else {
                                result.message
                            }

                            if (msg.contains("license expired", ignoreCase = true) || result.licenceStatus?.contains("expire", ignoreCase = true) == true) {
                                _isLicenseExpired.value = true
                            }
                            // Log error and show dialog; user can manually retry with the Refresh button.
                            _errorMessage.value = msg
                            FileLogger.logError(getApplication(), "TVConfig", "Error: $msg")
                        }
                    }
                    _counters.value = repository.getCounters(_macAddress, customerId)
                    val remoteFiles = repository.getAdFiles(_macAddress, customerId)
                    _adFiles.value = remoteFiles
                    // Resolve what the ad area should play right now based on offline mode.
                    _localAdFiles.value = buildEffectiveAdList(
                        remoteAds = remoteFiles,
                        offlineEnabled = offlineEnabled,
                        syncedAds = lastOfflineSyncedAds
                    )

                    // If offline mode is enabled, download in background and switch to local
                    // paths only after sync is complete.
                    if (offlineEnabled) {
                        offlineAdSyncJob?.cancel()
                        offlineAdSyncJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val synced = AdDownloader.syncAds(getApplication(), remoteFiles)
                                if (isActive) {
                                    lastOfflineSyncedAds = synced
                                    _localAdFiles.postValue(
                                        buildEffectiveAdList(
                                            remoteAds = remoteFiles,
                                            offlineEnabled = true,
                                            syncedAds = synced
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                FileLogger.logError(
                                    getApplication(),
                                    "OfflineAds",
                                    "Background offline ad sync failed",
                                    e
                                )
                            }
                        }
                    } else {
                        offlineAdSyncJob?.cancel()
                        lastOfflineSyncedAds = emptyList()
                    }

                    _connectedDevices.value = repository.getConnectedDevices(_macAddress, customerId)

                    _config.value?.let { cfg ->
                        // Launch concurrently so loading spinner dismisses immediately
                        launch { initMqttIfNeeded(cfg, mqttViewModel) }
                    }
                } ?: run {
                    // Timeout: log and fall back to cache. User can retry via Refresh button or MQTT.
                    FileLogger.logError(getApplication(), "TVConfig", "Config API timed out; using cached data")
                }
            } catch (e: Exception) {
                _config.value = repository.getCachedConfig(_macAddress, customerId)
                _counters.value = repository.getCounters(_macAddress, customerId)
                val cachedAds = repository.getAdFiles(_macAddress, customerId)
                _adFiles.value = cachedAds
                _localAdFiles.value = buildEffectiveAdList(
                    remoteAds = cachedAds,
                    offlineEnabled = offlineEnabled,
                    syncedAds = lastOfflineSyncedAds
                )
                _connectedDevices.value = repository.getConnectedDevices(_macAddress, customerId)
                val isTimeoutLike = e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("timed out", ignoreCase = true) == true
                if (_config.value == null) {
                    _errorMessage.value = "Failed to load TV configuration: ${e.message}"
                    FileLogger.logError(getApplication(), "TVConfig", "Exception: ${e.message}", e)
                } else if (isTimeoutLike) {
                    // Cache available; silently log and wait for MQTT or manual refresh.
                    FileLogger.logError(getApplication(), "TVConfig", "Config API exception (timeout, cached data in use): ${e.message}", e)
                }
            } finally {
                _isLoading.value = false
                _adAreaReloadToken.value = (_adAreaReloadToken.value ?: 0) + 1
                // Connected devices may have changed; invalidate keypad serial cache so that
                // subsequent keypad validation uses the latest mapping.
                mqttViewModel.invalidateKeypadSerialCache()
                android.util.Log.i(
                    "TokenDisplayVMPerf",
                    "loadData total time: ${System.currentTimeMillis() - loadStartMs} ms"
                )
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

            // Use a stable clientId so we do NOT force-disconnect and recreate the MQTT client
            // on every retry. If broker.brokerId is null, derive a deterministic suffix from
            // the broker connection parameters instead of a random value.
            val baseMac = _macAddress.replace(":", "")
            val brokerSuffix = broker.brokerId?.toString()
                ?: "${host}_${mqttPort}_${topic}".replace(Regex("[^A-Za-z0-9]"), "")
            val clientId = "callqtv_${baseMac}_$brokerSuffix"

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

    companion object {
        // Prevent UI churn/jank from too-frequent refresh-trigger bursts.
        private const val MQTT_REFRESH_MIN_INTERVAL_MS = 30_000L
    }

    private fun buildEffectiveAdList(
        remoteAds: List<AdFileEntity>,
        offlineEnabled: Boolean,
        syncedAds: List<AdFileEntity>
    ): List<AdFileEntity> {
        if (!offlineEnabled) return remoteAds
        if (syncedAds.isEmpty()) return remoteAds

        val byStableKey = syncedAds.associateBy { "${it.id}|${it.position}" }
        return remoteAds.map { remote ->
            // YouTube should stream even when offline mode is enabled.
            if (isYouTubePath(remote.filePath)) return@map remote
            val key = "${remote.id}|${remote.position}"
            val synced = byStableKey[key] ?: return@map remote
            // If sync failed for this item, downloader keeps original URL; preserve remote entity.
            if (synced.filePath == remote.filePath) remote else remote.copy(filePath = synced.filePath)
        }
    }

    private fun isYouTubePath(path: String): Boolean {
        val lc = path.lowercase()
        return lc.contains("youtube.com") || lc.contains("youtu.be") || lc.contains("youtube-nocookie.com")
    }
}
