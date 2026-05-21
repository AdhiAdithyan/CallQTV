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
import com.softland.callqtv.utils.LicenseDateUtils
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.TokenAnnouncer
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
    private data class CachedUiSnapshot(
        val config: TvConfigEntity?,
        val counters: List<CounterEntity>,
        val adFiles: List<AdFileEntity>,
        val connectedDevices: List<ConnectedDeviceEntity>
    )

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

    private fun computeDaysUntilExpiry(): Int? = LicenseDateUtils.daysUntilExpiry(
        authPrefs.getString(PreferenceHelper.product_license_end, null),
    )

    fun loadData(
        mqttViewModel: MqttViewModel,
        forceShowOverlay: Boolean = false,
        clearCacheBeforeFetch: Boolean = false,
        bypassActiveLoadGuard: Boolean = false,
    ) {
        // Prevent overlapping config reloads unless manual/startup or CLR (immediate MQTT refresh).
        if (!forceShowOverlay && !bypassActiveLoadGuard && currentConfigLoadJob?.isActive == true) return

        // Start a single collector that triggers config refresh based on MQTT responses.
        if (!mqttConfigRefreshListenerStarted) {
            mqttConfigRefreshListenerStarted = true
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                mqttViewModel.configRefreshRequests
                    .receiveAsFlow()
                    .collect { signal ->
                        val now = System.currentTimeMillis()
                        if (!signal.forceImmediate &&
                            now - lastMqttRefreshHandledAtMs < MQTT_REFRESH_MIN_INTERVAL_MS
                        ) {
                            return@collect
                        }
                        lastMqttRefreshHandledAtMs = now
                        // CLR: refresh config in background; token clear is handled in MqttViewModel before this runs.
                        loadData(
                            mqttViewModel,
                            forceShowOverlay = false,
                            clearCacheBeforeFetch = false,
                            bypassActiveLoadGuard = signal.forceImmediate,
                        )
                    }
            }
        }

        currentConfigLoadJob?.cancel()
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

            // Only app launch and explicit manual refresh wipe cache up front so the UI never blanks
            // during background/MQTT config sync. On success, fetchAndCacheTvConfig replaces DB rows.
            if (clearCacheBeforeFetch) {
                // Bind TTS using the previous session's cached language while the config API runs.
                warmTokenAnnouncerIfEnabled(loadCachedSnapshot(_macAddress, customerId).config)
                offlineAdSyncJob?.cancel()
                offlineAdSyncJob = null
                lastOfflineSyncedAds = emptyList()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repository.clearCachedConfiguration(_macAddress, customerId)
                    AdDownloader.clearOfflineAdStorage(getApplication())
                }
                _config.value = null
                _counters.value = emptyList()
                _adFiles.value = emptyList()
                _localAdFiles.value = emptyList()
                _connectedDevices.value = emptyList()
                mqttViewModel.invalidateKeypadSerialCache()
            }

            var showedCacheFirst = false
            try {
                // Stale-while-revalidate: paint Room cache immediately, then sync from API and refresh UI.
                if (!clearCacheBeforeFetch) {
                    val cachedSnapshot = loadCachedSnapshot(_macAddress, customerId)
                    if (cachedSnapshot.config != null) {
                        applyUiFromSnapshot(cachedSnapshot, offlineEnabled, bumpAdAreaReload = false)
                        showedCacheFirst = true
                        if (forceShowOverlay) {
                            _isLoading.value = false
                        }
                        mqttViewModel.applyTokenHistoryLimitFromConfig(cachedSnapshot.config)
                        launch { initMqttIfNeeded(mqttViewModel) }
                        android.util.Log.i(
                            "TokenDisplayVMPerf",
                            "Applied cached TV config before API sync"
                        )
                    }
                }

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
                            _isLicenseExpired.value = false
                            _errorMessage.value = null
                            com.softland.callqtv.utils.DatabaseBackup.backupDatabase(getApplication())
                        }
                        is TvConfigResult.Pending -> {
                            _isPendingApproval.value = true
                            _errorMessage.value = result.message
                        }
                        is TvConfigResult.Error -> {
                            val msg = if (result.licenceStatus != null) {
                                "${result.message}\n\n${result.licenceStatus}"
                            } else {
                                result.message
                            }
                            if (msg.contains("license expired", ignoreCase = true) ||
                                result.licenceStatus?.contains("expire", ignoreCase = true) == true
                            ) {
                                _isLicenseExpired.value = true
                            }
                            _errorMessage.value = msg
                            FileLogger.logError(getApplication(), "TVConfig", "Error: $msg")
                        }
                    }
                    // After API (success or handled error/pending), replace UI with latest DB snapshot.
                    val refreshedSnapshot = loadCachedSnapshot(_macAddress, customerId)
                    val uiSnapshot = when (result) {
                        is TvConfigResult.Success ->
                            refreshedSnapshot.copy(config = result.entity)
                        else -> refreshedSnapshot
                    }
                    applyUiFromSnapshot(uiSnapshot, offlineEnabled, bumpAdAreaReload = true)
                    startOfflineAdSyncIfNeeded(remoteFiles = uiSnapshot.adFiles, offlineEnabled = offlineEnabled)
                    mqttViewModel.applyTokenHistoryLimitFromConfig(uiSnapshot.config)
                    if (uiSnapshot.config != null) {
                        launch { initMqttIfNeeded(mqttViewModel) }
                    }
                    android.util.Log.i(
                        "TokenDisplayVMPerf",
                        "UI updated from post-API snapshot (cacheFirst=$showedCacheFirst)"
                    )
                } ?: run {
                    // Timeout: log and fall back to cache. User can retry via Refresh button or MQTT.
                    FileLogger.logError(getApplication(), "TVConfig", "Config API timed out; using cached data")
                    if (_config.value == null) {
                        val fallbackSnapshot = loadCachedSnapshot(_macAddress, customerId)
                        if (fallbackSnapshot.config != null) {
                            applyUiFromSnapshot(fallbackSnapshot, offlineEnabled, bumpAdAreaReload = false)
                        }
                    }
                    if (_config.value != null) {
                        launch { initMqttIfNeeded(mqttViewModel) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // New loadData, CLR refresh, or stopBackgroundWork cancelled this run — ignore.
                throw e
            } catch (e: Exception) {
                val fallbackSnapshot = loadCachedSnapshot(_macAddress, customerId)
                applyUiFromSnapshot(fallbackSnapshot, offlineEnabled, bumpAdAreaReload = false)
                val isTimeoutLike = e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("timed out", ignoreCase = true) == true
                if (_config.value == null) {
                    _errorMessage.value = "Failed to load TV configuration: ${e.message}"
                    FileLogger.logError(getApplication(), "TVConfig", "Exception: ${e.message}", e)
                } else if (isTimeoutLike) {
                    // Cache available; silently log and wait for MQTT or manual refresh.
                    FileLogger.logError(getApplication(), "TVConfig", "Config API exception (timeout, cached data in use): ${e.message}", e)
                }
                if (_config.value != null) {
                    launch { initMqttIfNeeded(mqttViewModel) }
                }
            } finally {
                _isLoading.value = false
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

    private suspend fun initMqttIfNeeded(mqttViewModel: MqttViewModel) {
        val endpoints = loadBrokerEndpoints()
        mqttViewModel.reconcileBrokersAfterConfigSync(endpoints, getApplication())
    }

    private suspend fun loadBrokerEndpoints(): List<MqttViewModel.BrokerEndpoint> {
        val customerIdInt = authPrefs.getInt(PreferenceHelper.customer_id, 0)
        val customerId = String.format(Locale.ROOT, "%04d", customerIdInt)
        val brokers = repository.getMappedBrokerEntities(_macAddress, customerId)
        val rows = if (brokers.isEmpty()) {
            listOfNotNull(repository.getMappedBrokerEntity(_macAddress, customerId))
        } else {
            brokers
        }
        return rows.mapNotNull { broker -> broker.toBrokerEndpoint(_macAddress) }
    }

    private fun com.softland.callqtv.data.local.MappedBrokerEntity.toBrokerEndpoint(
        macAddress: String,
    ): MqttViewModel.BrokerEndpoint? {
        val host = host?.trim().orEmpty()
        val topic = topic?.trim().orEmpty()
        if (host.isEmpty() || topic.isEmpty()) return null

        val mqttPort = port?.trim().orEmpty().ifEmpty { "1883" }
        val serverUri = "tcp://$host:$mqttPort"
        val baseMac = macAddress.replace(":", "")
        val brokerSuffix = brokerId.toString()
        val clientId = "callqtv_${baseMac}_$brokerSuffix"

        return MqttViewModel.BrokerEndpoint(
            serverUri = serverUri,
            clientId = clientId,
            username = ssid,
            password = password,
            topic = topic,
            qos = 1,
        )
    }

    companion object {
        // Prevent UI churn/jank from too-frequent refresh-trigger bursts.
        private const val MQTT_REFRESH_MIN_INTERVAL_MS = 30_000L
    }

    private suspend fun loadCachedSnapshot(macAddress: String, customerId: String): CachedUiSnapshot {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CachedUiSnapshot(
                config = repository.getCachedConfig(macAddress, customerId),
                counters = repository.getCounters(macAddress, customerId),
                adFiles = repository.getAdFiles(macAddress, customerId),
                connectedDevices = repository.getConnectedDevices(macAddress, customerId)
            )
        }
    }

    /** Pushes config, counters, ads, and connected devices into LiveData. */
    private fun applyUiFromSnapshot(
        snapshot: CachedUiSnapshot,
        offlineEnabled: Boolean,
        bumpAdAreaReload: Boolean,
    ) {
        _config.value = snapshot.config
        warmTokenAnnouncerIfEnabled(snapshot.config)
        _counters.value = snapshot.counters
        val remoteFiles = snapshot.adFiles
        _adFiles.value = remoteFiles
        _localAdFiles.value = buildEffectiveAdList(
            remoteAds = remoteFiles,
            offlineEnabled = offlineEnabled,
            syncedAds = lastOfflineSyncedAds,
        )
        _connectedDevices.value = snapshot.connectedDevices
        if (bumpAdAreaReload) {
            _adAreaReloadToken.value = (_adAreaReloadToken.value ?: 0) + 1
        }
    }

    private fun startOfflineAdSyncIfNeeded(
        remoteFiles: List<AdFileEntity>,
        offlineEnabled: Boolean,
    ) {
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
                                syncedAds = synced,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    FileLogger.logError(
                        getApplication(),
                        "OfflineAds",
                        "Background offline ad sync failed",
                        e,
                    )
                }
            }
        } else {
            offlineAdSyncJob?.cancel()
            lastOfflineSyncedAds = emptyList()
        }
    }

    /** Start TTS bind/warm as soon as TV config is known so the first MQTT token is not delayed. */
    private fun warmTokenAnnouncerIfEnabled(cfg: TvConfigEntity?) {
        if (cfg?.enableTokenAnnouncement != true) return
        val app = getApplication<Application>()
        TokenAnnouncer.setAnnouncementsEnabled(true)
        TokenAnnouncer.warmUp(app, cfg.audioLanguage, performPoke = true)
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

    /** Cancels in-flight config sync and ad downloads when the app is backgrounded. */
    fun stopBackgroundWork() {
        offlineAdSyncJob?.cancel()
        offlineAdSyncJob = null
        currentConfigLoadJob?.cancel()
        currentConfigLoadJob = null
    }
}
