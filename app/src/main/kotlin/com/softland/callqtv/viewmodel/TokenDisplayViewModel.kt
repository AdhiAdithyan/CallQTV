package com.softland.callqtv.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.data.local.ConnectedDeviceEntity
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.model.CheckDeviceStatusRequest
import com.softland.callqtv.data.repository.ProjectRepository
import com.softland.callqtv.data.repository.TvConfigRepository
import com.softland.callqtv.data.repository.TvConfigResult
import com.softland.callqtv.utils.LicenseApiMessages
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class TokenDisplayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TvConfigRepository(application)
    private val projectRepository = ProjectRepository(application)
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** True while the initial/manual overlay load is in flight (survives cache-first isLoading=false). */
    private val _isStartupLoadInFlight = MutableLiveData(false)
    val isStartupLoadInFlight: LiveData<Boolean> = _isStartupLoadInFlight
    
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
    private val mqttInitMutex = Mutex()
    private var currentConfigLoadJob: Job? = null
    /** Monotonic id so a cancelled load's `finally` cannot clear loading for a newer retry. */
    private var activeConfigLoadId = 0
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
            applyLicenseExpiryFromPrefs()
        }
        startDateTimeUpdates()
        startExpiryCheck()
    }

    sealed class LicenseRefreshResult {
        data object Valid : LicenseRefreshResult()
        data class StillExpired(val message: String) : LicenseRefreshResult()
        data class Failed(val message: String) : LicenseRefreshResult()
    }

    private var is24HourFormat = true

    /** Updates the in-memory time format flag used by the live clock ticker. */
    fun setTimeFormat(is24Hour: Boolean) {
        is24HourFormat = is24Hour
    }

    /** Clears the configuration error so the Configuration Error dialog is not shown. */
    fun clearConfigurationError() {
        _errorMessage.value = null
    }

    /** Starts a 1-second ticker that publishes date-time text for the display header. */
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

    /** Re-evaluates license expiry from preferences on a fixed background interval. */
    private fun startExpiryCheck() {
        viewModelScope.launch {
            while (true) {
                applyLicenseExpiryFromPrefs()
                delay(15 * 60 * 1000L)
            }
        }
    }

    /** Computes days remaining from the persisted license end-date string. */
    private fun computeDaysUntilExpiry(): Int? = LicenseDateUtils.daysUntilExpiry(
        authPrefs.getString(PreferenceHelper.product_license_end, null),
    )

    /** Syncs license expiry from stored end date (e.g. after midnight rollover). */
    fun applyLicenseExpiryFromPrefs() {
        val days = computeDaysUntilExpiry()
        _daysUntilExpiry.postValue(days)
        when {
            days != null && days < 0 -> {
                _isLicenseExpired.postValue(true)
                if (_errorMessage.value.isNullOrBlank()) {
                    _errorMessage.postValue(
                        "Your Call-Q TV license has expired.\n" +
                            "Tap Retry to fetch updated license details from the server.",
                    )
                }
            }
            days != null && days >= 0 -> {
                _isLicenseExpired.postValue(false)
            }
        }
    }

    /** Calls CheckDeviceStatus and persists refreshed license end date when available. */
    private suspend fun refreshLicenseFromServer(): LicenseRefreshResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!com.softland.callqtv.utils.NetworkUtil.isNetworkAvailable(getApplication())) {
                return@withContext LicenseRefreshResult.Failed(
                    "No internet connection. Please check network and retry.",
                )
            }
            val customerId = authPrefs.getInt(PreferenceHelper.customer_id, 0)
            val deviceRegId = authPrefs.getInt(PreferenceHelper.device_registration_id, 0)
            val productRegId = authPrefs.getInt(PreferenceHelper.product_registration_id, 0)
            if (customerId == 0 || deviceRegId == 0) {
                return@withContext LicenseRefreshResult.Failed(
                    "Device registration details are missing. Please contact support.",
                )
            }
            val statusReq = CheckDeviceStatusRequest().apply {
                uniqueIDentifier = authPrefs.getString(PreferenceHelper.device_unique_id, null)
                this.customerId = customerId.toString()
                productRegistrationId = productRegId.toString()
                deviceRegistrationId = deviceRegId
                projectNAme = authPrefs.getString(PreferenceHelper.project_name, null)
            }
            try {
                val statusRes = projectRepository.getCheckDeviceStatus("CheckDeviceStatus", statusReq)
                when (statusRes.status) {
                    1, 3 -> {
                        statusRes.licenceActiveTo?.trim()?.takeIf { it.isNotBlank() }?.let { endDate ->
                            authPrefs.edit()
                                .putString(PreferenceHelper.product_license_end, endDate)
                                .apply()
                        }
                        applyLicenseExpiryFromPrefs()
                        val daysLeft = computeDaysUntilExpiry()
                        if (daysLeft != null && daysLeft < 0) {
                            LicenseRefreshResult.StillExpired(
                                statusRes.message?.trim().orEmpty().ifBlank {
                                    "Your license is still expired. Please renew with your administrator."
                                },
                            )
                        } else {
                            LicenseRefreshResult.Valid
                        }
                    }
                    7 -> LicenseRefreshResult.StillExpired(
                        statusRes.message?.trim().orEmpty().ifBlank { "License expired" },
                    )
                    else -> LicenseRefreshResult.Failed(
                        statusRes.message?.trim().orEmpty().ifBlank { "Could not verify license status." },
                    )
                }
            } catch (e: Exception) {
                FileLogger.logError(getApplication(), "TokenDisplayVM", "License refresh failed", e)
                LicenseRefreshResult.Failed(
                    LicenseApiMessages.userMessageFor(e, "License check"),
                )
            }
        }
    }

    /**
     * Fetches license from the server, then reloads TV config. Used from the license-expired dialog.
     */
    fun refreshLicenseThenLoadData(
        mqttViewModel: MqttViewModel,
        clearCacheBeforeFetch: Boolean = false,
    ) {
        viewModelScope.launch {
            _isStartupLoadInFlight.value = true
            _isLoading.value = true
            when (val licenseResult = refreshLicenseFromServer()) {
                is LicenseRefreshResult.Valid -> {
                    _isLicenseExpired.value = false
                    _errorMessage.value = null
                    loadData(
                        mqttViewModel,
                        forceShowOverlay = true,
                        clearCacheBeforeFetch = clearCacheBeforeFetch,
                        bypassActiveLoadGuard = true,
                    )
                }
                is LicenseRefreshResult.StillExpired -> {
                    _isLicenseExpired.value = true
                    _errorMessage.value = licenseResult.message
                    _isLoading.value = false
                    _isStartupLoadInFlight.value = false
                }
                is LicenseRefreshResult.Failed -> {
                    applyLicenseExpiryFromPrefs()
                    _errorMessage.value = licenseResult.message
                    if (_isLicenseExpired.value != true) {
                        loadData(
                            mqttViewModel,
                            forceShowOverlay = true,
                            clearCacheBeforeFetch = clearCacheBeforeFetch,
                            bypassActiveLoadGuard = true,
                        )
                    } else {
                        _isLoading.value = false
                        _isStartupLoadInFlight.value = false
                    }
                }
            }
        }
    }

    /**
     * Loads TV configuration, counters, ads, and devices using stale-while-revalidate.
     *
     * Supports both cache-first background refresh and force-overlay manual/cold-start reload modes.
     */
    /**
     * Arms loading flags before the first Compose frame so cold-start gating does not treat a
     * transient `config == null` as a finished load (common right after APK upgrade).
     */
    fun armInitialStartupLoad() {
        _isLoading.value = true
        _isStartupLoadInFlight.value = true
    }

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

        val loadId = ++activeConfigLoadId
        if (forceShowOverlay) {
            // Set before cancelling any in-flight job so a stale `finally` cannot hide the overlay.
            _isLoading.value = true
            _isStartupLoadInFlight.value = true
            hasShownInitialLoadingOverlay = true
        }

        currentConfigLoadJob?.cancel()
        currentConfigLoadJob = viewModelScope.launch {
            val loadStartMs = System.currentTimeMillis()
            if (!forceShowOverlay) {
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

            // Broker host/credentials live in mapped_broker (Room), not tv_config — connect in parallel with API.
            launch(kotlinx.coroutines.Dispatchers.IO) {
                initMqttIfNeeded(mqttViewModel)
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
                if (loadId == activeConfigLoadId) {
                    _isLoading.value = false
                    _isStartupLoadInFlight.value = false
                }
                // Connected devices may have changed; invalidate keypad serial cache so that
                // subsequent keypad validation uses the latest mapping.
                mqttViewModel.invalidateKeypadSerialCache()
                applyLicenseExpiryFromPrefs()
                android.util.Log.i(
                    "TokenDisplayVMPerf",
                    "loadData total time: ${System.currentTimeMillis() - loadStartMs} ms"
                )
            }
        }
    }

    /** Initializes/updates MQTT broker connections from mapped broker rows after config sync. */
    private suspend fun initMqttIfNeeded(mqttViewModel: MqttViewModel) {
        mqttInitMutex.withLock {
            val endpoints = loadBrokerEndpoints()
            mqttViewModel.reconcileBrokersAfterConfigSync(endpoints, getApplication())
        }
    }

    /** Reads broker endpoints for this device/customer from Room cache. */
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

    /** Maps a DB broker entity into runtime MQTT connection details. */
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

    /** Loads cached config/counters/ads/devices snapshot from local Room tables. */
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

    /** Starts or cancels offline ad download/sync depending on current offline mode toggle. */
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

    /** Bind TTS as soon as TV config is known; prime runs in the background without blocking token UI. */
    private fun warmTokenAnnouncerIfEnabled(cfg: TvConfigEntity?) {
        if (cfg?.enableTokenAnnouncement != true) return
        val app = getApplication<Application>()
        TokenAnnouncer.setAnnouncementsEnabled(true)
        TokenAnnouncer.warmUp(app, cfg.audioLanguage, performPoke = true)
    }

    /** Merges remote ad rows with downloaded local file paths when offline ads are enabled. */
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

    /** Returns true when an ad URL/path points to YouTube-hosted content. */
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
