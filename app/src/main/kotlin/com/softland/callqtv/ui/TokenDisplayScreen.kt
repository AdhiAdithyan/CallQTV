package com.softland.callqtv.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softland.callqtv.R
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.ui.ads.MediaEngine
import com.softland.callqtv.ui.ads.WebViewWarmup
import com.softland.callqtv.ui.display.TokenDisplayBlockingOverlays
import com.softland.callqtv.utils.TokenAnnouncer
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.estimatedChimeAudibleMs
import com.softland.callqtv.utils.playTokenChime
import com.softland.callqtv.viewmodel.MqttViewModel
import com.softland.callqtv.viewmodel.TokenDisplayViewModel
import com.softland.callqtv.viewmodel.findCounterEntityForMqttRoute
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

@Composable
/**
 * Main token display screen.
 *
 * Responsibilities:
 * - cold-start readiness gating (UI payload events + TTS prime + config load)
 * - token UI event handling (update/replace channels -> blink + announcements)
 * - display content rendering (content vs blocking overlays)
 */
fun TokenDisplayScreen(
    viewModel: TokenDisplayViewModel, 
    mqttViewModel: MqttViewModel, 
    counterBgHex: String,
    tokenBgHex: String,
    appThemeHex: String,
    onThemeChange: (String) -> Unit,
    onCounterBgChange: (String) -> Unit,
    onTokenBgChange: (String) -> Unit
) {
    val context = LocalContext.current

    // Warm up ExoPlayer instances/cache on a background dispatcher.
    // This reduces first-ad stutter/ANR during initial player/cache initialization.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                MediaEngine.warmUp(context)
            } catch (_: Exception) {
                // Never block UI for warm-up failures.
            }
        }
    }

    // Pre-warm WebView provider once, so first YouTube ad doesn't pay full Chromium init cost.
    LaunchedEffect(Unit) {
        val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        val isTv = uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        // TV: defer Chromium init until after config/UI settle (reduces cold-start jank).
        delay(if (isTv) 8000L else 2200L)
        WebViewWarmup.warmUp(context)
    }

    // Per-counter timestamps: blink only when a new MQTT call sets this (not on history restore / refresh).
    val blinkTriggers = remember { mutableStateMapOf<String, Long>() }

    val isLoading by viewModel.isLoading.observeAsState(false)
    val isStartupLoadInFlight by viewModel.isStartupLoadInFlight.observeAsState(false)
    LaunchedEffect(isLoading) {
        if (isLoading) blinkTriggers.clear()
    }

    var isColdStartSession by remember { mutableStateOf(true) }
    var coldStartMessage by remember { mutableStateOf<String?>("Preparing display.\nPlease wait...") }

    /**
     * Resets cold-start state and temporarily disables payload-to-UI event delivery.
     *
     * Called before manual refresh and license retry flows so the overlay + TTS warm-up
     * experience is consistent across subsequent reloads.
     */
    fun beginColdStartReadiness() {
        isColdStartSession = true
        coldStartMessage = "Preparing display.\nPlease wait..."
        mqttViewModel.setPayloadUiReady(false)
        blinkTriggers.clear()
    }

    val refreshConfigWithColdStart: () -> Unit = {
        beginColdStartReadiness()
        viewModel.loadData(
            mqttViewModel,
            forceShowOverlay = true,
            clearCacheBeforeFetch = true,
        )
    }

    val retryLicenseWithColdStart: () -> Unit = {
        beginColdStartReadiness()
        viewModel.refreshLicenseThenLoadData(mqttViewModel, clearCacheBeforeFetch = false)
    }

    DisposableEffect(Unit) {
        mqttViewModel.setPayloadUiReady(false)
        onDispose { }
    }
    val isPendingApproval by viewModel.isPendingApproval.observeAsState(false)
    val isLicenseExpired by viewModel.isLicenseExpired.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState(null)
    val isDisplayBlocked = isLicenseExpired || isPendingApproval

    val config by viewModel.config.observeAsState(null)
    val adAreaReloadToken by viewModel.adAreaReloadToken.observeAsState(0)
    val counters by viewModel.counters.observeAsState(emptyList())
    val daysUntilExpiry by viewModel.daysUntilExpiry.observeAsState(null)
    val currentDateTime by viewModel.currentDateTime.observeAsState("")

    val mqttConnected by mqttViewModel.getConnectionStatus().observeAsState(false)
    val mqttError by mqttViewModel.getErrorMessage().observeAsState("")
    val isAutoRetryExhausted by mqttViewModel.isAutoRetryExhausted().observeAsState(false)
    val tokensPerCounter by mqttViewModel.getTokensPerCounter().observeAsState(emptyMap())
    val lastPayloadForFooter by mqttViewModel.getLastPayload().observeAsState("")
    val isConnectingToMqtt by mqttViewModel.isConnectingToMqtt().observeAsState(false)
    val connectTimer by mqttViewModel.getConnectTimer().observeAsState(0)
    val mqttRetryAttempt by mqttViewModel.getMqttRetryAttempt().observeAsState(0)
    val isBrokerReachable by mqttViewModel.isBrokerReachable().observeAsState(true)
    val dispenseConnectedByButton by mqttViewModel.getDispenseConnectedByButton().observeAsState(emptyMap())
    val keypadConnectedByButton by mqttViewModel.getKeypadConnectedByButton().observeAsState(emptyMap())

    // Background Music Player (Loops tokenMusicUrl if provided)
    val musicUrl = config?.tokenMusicUrl
    DisposableEffect(musicUrl, isDisplayBlocked) {
        if (isDisplayBlocked || musicUrl.isNullOrBlank()) return@DisposableEffect onDispose {}

        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(context, Uri.parse(musicUrl))
            mediaPlayer.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0.3f, 0.3f) // Lower volume for background
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { it.start() }
        } catch (e: Exception) {
            Log.e("TokenDisplay", "Error initializing background music: $musicUrl", e)
        }

        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }
    var showMqttRetryDialog by remember { mutableStateOf(false) }
    LaunchedEffect(isLicenseExpired) {
        mqttViewModel.setLicenseExpired(isLicenseExpired)
        if (isLicenseExpired) {
            showMqttRetryDialog = false
        }
    }
    var pendingCallCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            // Show only queued/waiting calls; exclude the currently processing call.
            val next = (mqttViewModel.announcementQueueSize.get() - 1).coerceAtLeast(0)
            if (next != pendingCallCount) pendingCallCount = next
            delay(500)
        }
    }

    // Trust MQTT when it reports connected. If MQTT is connected and publishing,
    // we are connected - do NOT let the TCP ping override that.
    // (Some broker devices accept only one connection; the ping would fail even when MQTT works.)
    val brokerConnected = mqttConnected || isBrokerReachable
    var stableBrokerConnected by remember { mutableStateOf(false) }
    LaunchedEffect(brokerConnected) {
        if (brokerConnected) {
            stableBrokerConnected = true
        } else {
            // Prevent brief MQTT status flaps from showing a false red BLUCON indicator.
            delay(15000L)
            if (!brokerConnected) {
                stableBrokerConnected = false
            }
        }
    }
    var reconnectUiSeconds by remember { mutableIntStateOf(0) }
    var reconnectDisplayTry by remember { mutableIntStateOf(1) }
    val effectiveRetryAttempt = maxOf(reconnectDisplayTry, mqttRetryAttempt.coerceAtLeast(1))
    LaunchedEffect(mqttRetryAttempt, brokerConnected, showMqttRetryDialog) {
        if (!brokerConnected && !showMqttRetryDialog) {
            reconnectDisplayTry = maxOf(reconnectDisplayTry, mqttRetryAttempt.coerceAtLeast(1))
        } else {
            reconnectDisplayTry = 1
        }
    }
    LaunchedEffect(brokerConnected, showMqttRetryDialog) {
        if (!stableBrokerConnected && !showMqttRetryDialog) {
            reconnectUiSeconds = 0
            reconnectDisplayTry = maxOf(reconnectDisplayTry, mqttRetryAttempt.coerceAtLeast(1))
            while (!stableBrokerConnected && !showMqttRetryDialog) {
                delay(1000)
                reconnectUiSeconds += 1
                if (reconnectUiSeconds >= 15) {
                    // Trigger a fresh reconnect if still offline after 15s.
                    mqttViewModel.retryConnect()
                    reconnectDisplayTry += 1
                    reconnectUiSeconds = 0
                }
            }
        } else {
            reconnectUiSeconds = 0
        }
    }
    
    val macAddress = viewModel.macAddress
    val appVersion = remember { context.getString(R.string.app_version) }
    
    val networkViewModel = viewModel<com.softland.callqtv.viewmodel.NetworkViewModel>()
                val isNetworkAvailable by networkViewModel.getNetworkLiveData(context).observeAsState(initial = true)
    var showTtsLoading by remember { mutableStateOf(false) }
    var lastInitializedTtsLanguage by remember { mutableStateOf<String?>(null) }
    // Tracks when the BLUCON error dialog was shown (kept for potential timing / analytics)
    var mqttRetryShownAt by remember { mutableStateOf<Long?>(null) }
    val mqttRetryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isAutoRetryExhausted, mqttError) {
        if (isAutoRetryExhausted && mqttError.isNotBlank()) {
            showMqttRetryDialog = true
            mqttRetryShownAt = System.currentTimeMillis()
        }
    }
    // Auto-close BLUCON dialog when connection is successfully restored.
    // This does NOT depend on error text, only on actual MQTT connection status.
    LaunchedEffect(brokerConnected, showMqttRetryDialog) {
        if (brokerConnected && showMqttRetryDialog) {
            showMqttRetryDialog = false
            mqttRetryShownAt = null
        }
    }
    // Auto-focus Retry button when broker error dialog is shown (defer so dialog content is composed)
    LaunchedEffect(showMqttRetryDialog) {
        if (showMqttRetryDialog && mqttError.isNotBlank()) {
            delay(100)
            try {
                mqttRetryFocusRequester.requestFocus()
            } catch (_: IllegalStateException) { /* focus tree not ready */ }
        }
    }

    // Cold start: one progress overlay until config, token history, UI routing, and TTS are ready.
    LaunchedEffect(
        isColdStartSession,
        isStartupLoadInFlight,
        isLoading,
        config,
        counters,
        isPendingApproval,
        isLicenseExpired,
    ) {
        if (!isColdStartSession) return@LaunchedEffect

        if (isPendingApproval || isLicenseExpired) {
            coldStartMessage = null
            mqttViewModel.setPayloadUiReady(false)
            isColdStartSession = false
            return@LaunchedEffect
        }

        if (isStartupLoadInFlight || isLoading) {
            coldStartMessage = "Loading TV configuration.\nPlease wait..."
            mqttViewModel.setPayloadUiReady(false)
            return@LaunchedEffect
        }

        if (config == null) {
            coldStartMessage = "Loading TV configuration.\nPlease wait..."
            mqttViewModel.setPayloadUiReady(false)
            return@LaunchedEffect
        }

        if (counters.isEmpty()) {
            coldStartMessage = "Setting up token display.\nPlease wait..."
            mqttViewModel.setPayloadUiReady(false)
            return@LaunchedEffect
        }

        try {
            openTokenUiGateAfterReadiness(
                context = context,
                mqttViewModel = mqttViewModel,
                config = config!!,
                onMessage = { coldStartMessage = it },
                onLanguageInitialized = { lastInitializedTtsLanguage = it },
            )
        } catch (e: Exception) {
            Log.w("TokenDisplay", "Cold start readiness failed: ${e.message}")
            mqttViewModel.setPayloadUiReady(true)
            mqttViewModel.flushHeldTokenUiEvents()
        } finally {
            coldStartMessage = null
            isColdStartSession = false
        }
    }

    // Recover held MQTT tokens when cold start ended early (e.g. config was null before loadData ran).
    LaunchedEffect(
        isColdStartSession,
        isStartupLoadInFlight,
        isLoading,
        config,
        counters,
        isPendingApproval,
        isLicenseExpired,
    ) {
        if (isColdStartSession || isStartupLoadInFlight || isLoading) return@LaunchedEffect
        if (isPendingApproval || isLicenseExpired || config == null) return@LaunchedEffect
        if (mqttViewModel.isPayloadUiReady()) return@LaunchedEffect
        if (counters.isEmpty()) return@LaunchedEffect

        val held = mqttViewModel.heldTokenUiEventCount()
        if (held > 0) {
            Log.i("TokenDisplay", "Recovering $held held token UI event(s) after readiness gate was missed")
        }
        try {
            openTokenUiGateAfterReadiness(
                context = context,
                mqttViewModel = mqttViewModel,
                config = config!!,
                onMessage = { coldStartMessage = it },
                onLanguageInitialized = { lastInitializedTtsLanguage = it },
            )
        } catch (e: Exception) {
            Log.w("TokenDisplay", "Token UI gate recovery failed: ${e.message}")
            mqttViewModel.setPayloadUiReady(true)
            mqttViewModel.flushHeldTokenUiEvents()
        } finally {
            coldStartMessage = null
        }
    }

    // After cold start: warm TTS when announcement language changes during a later config refresh.
    LaunchedEffect(config?.enableTokenAnnouncement, config?.audioLanguage, isColdStartSession) {
        if (isColdStartSession) return@LaunchedEffect
        val cfg = config ?: return@LaunchedEffect

        val announcementEnabled = cfg.enableTokenAnnouncement == true
        TokenAnnouncer.setAnnouncementsEnabled(announcementEnabled)

        if (!announcementEnabled) {
            showTtsLoading = false
            lastInitializedTtsLanguage = null
            return@LaunchedEffect
        }

        val lang = cfg.audioLanguage
        val languageChanged = lastInitializedTtsLanguage != lang
        if (languageChanged && isLoading) {
            showTtsLoading = true
        }
        launch {
            try {
                val ready = TokenAnnouncer.awaitReady(
                    context,
                    lang,
                    performPoke = true,
                    primeSynthesis = true,
                )
                if (!ready) {
                    Log.w("TokenDisplay", "TTS awaitReady returned false in LaunchedEffect")
                }
            } finally {
                showTtsLoading = false
            }
        }
        lastInitializedTtsLanguage = lang
    }

    val latestConfigState = rememberUpdatedState(config)
    val latestCountersState = rememberUpdatedState(counters)
    val announcementMutex = remember { Mutex() }

    LaunchedEffect(Unit) {
        mqttViewModel.tokenUpdateChannel.receiveAsFlow().collect { pair ->
            val currentConfig = latestConfigState.value
            val currentCounters = latestCountersState.value

            launch(Dispatchers.Default) {
                try {
                    val counterIdOrName = pair.counter
                    val tokenLabel = pair.token
                    val sourcePayload = pair.payload

                    val buttonIndexKey = counterIdOrName.trim()
                    val actualCounter = findCounterEntityForMqttRoute(currentCounters, buttonIndexKey)
                    if (actualCounter == null) {
                        android.util.Log.d(
                            "TokenDisplay",
                            "Dropping token '$tokenLabel' â€” no counter with button_index '$buttonIndexKey'"
                        )
                        return@launch
                    }

                    val storageKey = actualCounter.buttonIndex?.toString()?.trim()?.takeIf { it.isNotBlank() }
                        ?: buttonIndexKey
                    val keypadFallback = actualCounter.keypadIndex?.trim()?.takeIf {
                        it.isNotBlank() && it != storageKey
                    }

                    announcementMutex.withLock {
                        TokenAnnouncer.enterTokenAnnouncementCycle()
                        try {
                            val announcementsOn = currentConfig?.enableTokenAnnouncement == true
                            val ttsWarmDeferred: Deferred<Boolean>? = if (announcementsOn) {
                                async {
                                    TokenAnnouncer.awaitReady(
                                        context,
                                        currentConfig!!.audioLanguage,
                                        performPoke = false,
                                        primeSynthesis = false,
                                    )
                                }
                            } else {
                                null
                            }

                            val tokenOutcome =
                                mqttViewModel.processTokenUpdateForKeys(
                                    storageKey,
                                    tokenLabel,
                                    fallbackKey = keypadFallback,
                                    publishImmediately = false,
                                    isVipEmergency = pair.isVipEmergency,
                                )
                            if (!tokenOutcome.playCueUi) return@withLock

                            val willAnnounce = announcementsOn && tokenOutcome.playCueUi

                            val publishedVisualState = AtomicBoolean(false)
                            val publishTokenTile = publish@{
                                if (!publishedVisualState.compareAndSet(false, true)) return@publish
                                mqttViewModel.publishTokensSnapshot()
                                mqttViewModel.markPayloadDisplayed(sourcePayload)
                                UiPerfProbe.markTokenUiUpdated(storageKey, tokenLabel)
                                Handler(Looper.getMainLooper()).post {
                                    blinkTriggers[storageKey] = System.currentTimeMillis()
                                }
                            }

                            val soundKey = ThemeColorManager.getNotificationSoundKey(context)
                            val hasCustomChime = !actualCounter.audioUrl.isNullOrBlank() ||
                                !currentConfig?.tokenAudioUrl.isNullOrBlank()
                            playTokenChime(
                                context = context,
                                soundKey = soundKey,
                                tokenAudioUrl = currentConfig?.tokenAudioUrl,
                                counterAudioUrl = actualCounter.audioUrl,
                                onAudioStart = publishTokenTile,
                            )
                            publishTokenTile()

                            if (willAnnounce) {
                                val cfg = currentConfig ?: return@withLock
                                delay(estimatedChimeAudibleMs(soundKey, hasCustomChime))
                                val ttsReady = awaitTtsWarmForTokenAnnounce(ttsWarmDeferred, "token")
                                val announced = withTimeoutOrNull(TOKEN_ANNOUNCE_CYCLE_TIMEOUT_MS) {
                                    suspendAnnounceTokenUpdate(
                                        context = context,
                                        mqttViewModel = mqttViewModel,
                                        storageKey = storageKey,
                                        cfg = cfg,
                                        actualCounter = actualCounter,
                                        tokenLabel = tokenLabel,
                                        isVipEmergency = pair.isVipEmergency,
                                        ttsReady = ttsReady,
                                    )
                                    true
                                }
                                if (announced != true) {
                                    Log.w(
                                        "TokenDisplay",
                                        "Token announcement timed out for '$tokenLabel'; releasing queue",
                                    )
                                }
                            }
                        } finally {
                            TokenAnnouncer.exitTokenAnnouncementCycle()
                        }
                    }
                } finally {
                    mqttViewModel.announcementQueueSize.decrementAndGet()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        mqttViewModel.tokenReplaceChannel.receiveAsFlow().collect { pair ->
            val currentConfig = latestConfigState.value
            val currentCounters = latestCountersState.value

            launch(Dispatchers.Default) {
                try {
                    val counterIdOrName = pair.counter
                    val tokenLabel = pair.token
                    val sourcePayload = pair.payload

                    val buttonIndexKey = counterIdOrName.trim()
                    val actualCounter = findCounterEntityForMqttRoute(currentCounters, buttonIndexKey)
                    if (actualCounter == null) {
                        android.util.Log.d(
                            "TokenDisplay",
                            "Special token '$tokenLabel' â€” no counter for button_index '$buttonIndexKey'; still reporting MQTT to server"
                        )
                        mqttViewModel.markPayloadDisplayed(sourcePayload)
                        return@launch
                    }

                    val storageKey = actualCounter.buttonIndex?.toString()?.trim()?.takeIf { it.isNotBlank() }
                        ?: buttonIndexKey
                    val keypadFallback = actualCounter.keypadIndex?.trim()?.takeIf {
                        it.isNotBlank() && it != storageKey
                    }

                    announcementMutex.withLock {
                        TokenAnnouncer.enterTokenAnnouncementCycle()
                        try {
                            val announcementsOn = currentConfig?.enableTokenAnnouncement == true
                            val ttsWarmDeferred: Deferred<Boolean>? = if (announcementsOn) {
                                async {
                                    TokenAnnouncer.awaitReady(
                                        context,
                                        currentConfig!!.audioLanguage,
                                        performPoke = false,
                                        primeSynthesis = false,
                                    )
                                }
                            } else {
                                null
                            }

                            val specialToken = encodeSpecialMessageToken(tokenLabel)
                            val replaced = mqttViewModel.replaceTokenForKeys(
                                storageKey,
                                specialToken,
                                fallbackKey = keypadFallback,
                                publishImmediately = false,
                            )
                            if (!replaced) return@withLock

                            val willAnnounce = announcementsOn

                            val publishedVisualState = AtomicBoolean(false)
                            val publishTokenTile = publish@{
                                if (!publishedVisualState.compareAndSet(false, true)) return@publish
                                mqttViewModel.publishTokensSnapshot()
                                mqttViewModel.markPayloadDisplayed(sourcePayload)
                                UiPerfProbe.markTokenUiUpdated(storageKey, tokenLabel)
                                Handler(Looper.getMainLooper()).post {
                                    blinkTriggers[storageKey] = System.currentTimeMillis()
                                }
                            }

                            val soundKey = ThemeColorManager.getNotificationSoundKey(context)
                            val hasCustomChime = !actualCounter.audioUrl.isNullOrBlank() ||
                                !currentConfig?.tokenAudioUrl.isNullOrBlank()
                            playTokenChime(
                                context = context,
                                soundKey = soundKey,
                                tokenAudioUrl = currentConfig?.tokenAudioUrl,
                                counterAudioUrl = actualCounter.audioUrl,
                                onAudioStart = publishTokenTile,
                            )
                            publishTokenTile()

                            if (willAnnounce) {
                                val cfg = currentConfig ?: return@withLock
                                delay(estimatedChimeAudibleMs(soundKey, hasCustomChime))
                                val ttsReady = awaitTtsWarmForTokenAnnounce(ttsWarmDeferred, "special")
                                val announced = withTimeoutOrNull(TOKEN_ANNOUNCE_CYCLE_TIMEOUT_MS) {
                                    suspendAnnounceSpecialToken(
                                        context = context,
                                        mqttViewModel = mqttViewModel,
                                        storageKey = storageKey,
                                        cfg = cfg,
                                        actualCounter = actualCounter,
                                        tokenLabel = tokenLabel,
                                        ttsReady = ttsReady,
                                    )
                                    true
                                }
                                if (announced != true) {
                                    Log.w(
                                        "TokenDisplay",
                                        "Special token announcement timed out for '$tokenLabel'; releasing queue",
                                    )
                                }
                            }
                        } finally {
                            TokenAnnouncer.exitTokenAnnouncementCycle()
                        }
                    }
                } finally {
                    mqttViewModel.announcementQueueSize.decrementAndGet()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Show inline broker error only when BLUCON is effectively offline.
        // If brokerConnected is true (MQTT session up and reachability considered OK),
        // suppress the "Connection lost" banner even if we have stale errors.
        if (!brokerConnected && !isBrokerReachable && !showMqttRetryDialog) {
            val mqttRetryAttemptInline by mqttViewModel.getMqttRetryAttempt().observeAsState(0)
//            MqttErrorBar(
//                error = "Connection lost: broker host unreachable (TCP ping failed)",
//                exhausted = isAutoRetryExhausted,
//                retryAttempt = mqttRetryAttemptInline
//            ) { mqttViewModel.retryConnect() }
        } else if (!brokerConnected && mqttError.isNotBlank() && !showMqttRetryDialog) {
            val mqttRetryAttemptInline by mqttViewModel.getMqttRetryAttempt().observeAsState(0)
//            MqttErrorBar(mqttError, isAutoRetryExhausted, mqttRetryAttemptInline) { mqttViewModel.retryConnect() }
        }

        if (config != null && !isDisplayBlocked) {
            val cfg = config!!

            Box(modifier = Modifier.fillMaxSize()) {
                TokenDisplayContent(
                    config = cfg,
                    adAreaReloadToken = adAreaReloadToken,
                    macAddress = macAddress,
                    appVersion = appVersion,
                    isMqttConnected = stableBrokerConnected,
                    isNetworkAvailable = isNetworkAvailable,
                    counters = counters,
                    adFiles = viewModel.localAdFiles.observeAsState(emptyList()).value,
                    tokensPerCounter = tokensPerCounter,
                    daysUntilLicenseExpiry = daysUntilExpiry,
                    dateTime = currentDateTime,
                    counterBgHex = counterBgHex,
                    tokenBgHex = tokenBgHex,
                    appThemeHex = appThemeHex,
                    onThemeChange = onThemeChange,
                    onCounterBgChange = onCounterBgChange,
                    onTokenBgChange = onTokenBgChange,
                    onRefresh = refreshConfigWithColdStart,
                    onClearTokenHistoryAndRefresh = {
                        mqttViewModel.clearTokenHistory()
                        refreshConfigWithColdStart()
                    },
                    blinkTriggers = blinkTriggers,
                    showReconnectBadge = !stableBrokerConnected && !showMqttRetryDialog,
                    reconnectRetryAttempt = reconnectDisplayTry,
                    reconnectUiSeconds = reconnectUiSeconds,
                    pendingCallCount = pendingCallCount
                )

                
            }
        }
    }

    val reloadConfig = refreshConfigWithColdStart
    TokenDisplayBlockingOverlays(
        coldStartMessage = coldStartMessage,
        showTtsLoading = showTtsLoading,
        isLoading = isLoading && coldStartMessage == null,
        isPendingApproval = isPendingApproval,
        isLicenseExpired = isLicenseExpired,
        showConfigUnavailable = config == null && !isDisplayBlocked,
        errorMessage = errorMessage,
        macAddress = macAddress,
        appVersion = appVersion,
        isNetworkAvailable = isNetworkAvailable,
        onReloadConfig = reloadConfig,
        onRetryLicense = retryLicenseWithColdStart,
        showMqttRetryDialog = showMqttRetryDialog,
        onDismissMqttDialog = {
            showMqttRetryDialog = false
            mqttRetryShownAt = null
        },
        mqttRetryAttempt = effectiveRetryAttempt,
        mqttRetryFocusRequester = mqttRetryFocusRequester,
        onMqttRetry = {
            showMqttRetryDialog = false
            mqttRetryShownAt = null
            reconnectDisplayTry = maxOf(reconnectDisplayTry + 1, mqttRetryAttempt.coerceAtLeast(1) + 1)
            mqttViewModel.retryConnect(resetAttempts = false)
        },
    )
}

/** Max wait per token for chime + TTS; must release [announcementMutex] if the engine never callbacks. */
private const val TOKEN_ANNOUNCE_CYCLE_TIMEOUT_MS = 50_000L

private suspend fun openTokenUiGateAfterReadiness(
    context: Context,
    mqttViewModel: MqttViewModel,
    config: TvConfigEntity,
    onMessage: (String?) -> Unit,
    onLanguageInitialized: (String?) -> Unit,
) {
    onMessage("Setting up token display.\nPlease wait...")
    mqttViewModel.setPayloadUiReady(false)
    mqttViewModel.awaitHistoryLoaded()
    mqttViewModel.invalidateKeypadSerialCache()

    val announcementEnabled = config.enableTokenAnnouncement == true
    TokenAnnouncer.setAnnouncementsEnabled(announcementEnabled)

    if (announcementEnabled) {
        onMessage("Preparing voice engine.\nPlease wait...")
        val ready = TokenAnnouncer.awaitReady(
            context,
            config.audioLanguage,
            performPoke = true,
            primeSynthesis = true,
        )
        if (!ready) {
            Log.w("TokenDisplay", "TTS awaitReady returned false during token UI gate open")
        }
        onLanguageInitialized(config.audioLanguage)
    } else {
        onLanguageInitialized(null)
    }

    delay(32L)
    mqttViewModel.setPayloadUiReady(true)
    mqttViewModel.flushHeldTokenUiEvents()
}

private suspend fun suspendAnnounceTokenUpdate(
    context: Context,
    mqttViewModel: MqttViewModel,
    storageKey: String,
    cfg: TvConfigEntity,
    actualCounter: CounterEntity,
    tokenLabel: String,
    isVipEmergency: Boolean,
    ttsReady: Boolean,
) {
    val displayName =
        (actualCounter.name?.takeIf { it.isNotBlank() }
            ?: actualCounter.defaultName?.takeIf { it.isNotBlank() }
            ?: "Counter ${actualCounter.buttonIndex}")

    val announcementCounterName =
        if (cfg.enableCounterAnnouncement == true) displayName else ""

    val counterCode = actualCounter.code.orEmpty().trim().ifBlank {
        actualCounter.defaultCode.orEmpty().trim()
    }
    val usePrefix = cfg.enableCounterPrefix != false
    val spokenPrefix = when {
        isVipEmergency ->
            VIP_EMERGENCY_COUNTER_PREFIX.toCharArray().joinToString(" ")
        usePrefix && counterCode.isNotBlank() ->
            counterCode.toCharArray().joinToString(" ")
        else -> ""
    }

    val isSpecial = isSpecialMessageToken(tokenLabel)
    val decodedSpecial = decodeSpecialMessageToken(tokenLabel)
    val skipPrime = ttsReady

    TokenAnnouncer.prepareForNextTokenAnnouncement()

    runWithAdvertisementAudioDuckedForSpeech(context) { skipPrimeFromDuck, restore ->
        val effectiveSkipPrime = skipPrime || skipPrimeFromDuck
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { restore() }
            if (isSpecial && decodedSpecial != null) {
                val spokenAnnouncement = buildString {
                    append(decodedSpecial)
                    if (announcementCounterName.isNotBlank()) {
                        append(' ')
                        append(announcementCounterName)
                    }
                }
                TokenAnnouncer.announceMessage(
                    context = context,
                    audioLanguage = cfg.audioLanguage,
                    message = spokenAnnouncement,
                    skipSynthesisPrime = effectiveSkipPrime,
                    pokeEngine = false,
                    onDone = {
                        restore()
                        mqttViewModel.markAsAnnounced(storageKey, tokenLabel)
                        if (continuation.isActive) continuation.resume(Unit)
                    },
                )
            } else {
                val formattedForTile = formatTokenByPattern(tokenLabel, cfg.tokenFormat) ?: tokenLabel
                val tokenText = TokenAnnouncer.sanitizeTokenLabelForSpeech(formattedForTile)
                TokenAnnouncer.announceTokenCall(
                    context = context,
                    audioLanguage = cfg.audioLanguage,
                    spelledCounterPrefix = spokenPrefix,
                    tokenText = tokenText,
                    counterDisplayName = announcementCounterName,
                    skipSynthesisPrime = effectiveSkipPrime,
                    pokeEngine = false,
                    onDone = {
                        restore()
                        mqttViewModel.markAsAnnounced(storageKey, tokenLabel)
                        if (continuation.isActive) continuation.resume(Unit)
                    },
                )
            }
        }
    }
}

private suspend fun suspendAnnounceSpecialToken(
    context: Context,
    mqttViewModel: MqttViewModel,
    storageKey: String,
    cfg: TvConfigEntity,
    actualCounter: CounterEntity,
    tokenLabel: String,
    ttsReady: Boolean,
) {
    val displayName =
        (actualCounter.name?.takeIf { it.isNotBlank() }
            ?: actualCounter.defaultName?.takeIf { it.isNotBlank() }
            ?: "Counter ${actualCounter.buttonIndex}")

    val announcementCounterName =
        if (cfg.enableCounterAnnouncement == true) displayName else ""

    val specialAnnouncement = buildString {
        append(tokenLabel)
        if (announcementCounterName.isNotBlank()) {
            append(' ')
            append(announcementCounterName)
        }
    }

    val skipPrime = ttsReady

    TokenAnnouncer.prepareForNextTokenAnnouncement()

    runWithAdvertisementAudioDuckedForSpeech(context) { skipPrimeFromDuck, restore ->
        val effectiveSkipPrime = skipPrime || skipPrimeFromDuck
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { restore() }
            TokenAnnouncer.announceMessage(
                context = context,
                audioLanguage = cfg.audioLanguage,
                message = specialAnnouncement,
                skipSynthesisPrime = effectiveSkipPrime,
                pokeEngine = false,
                onDone = {
                    restore()
                    mqttViewModel.markAsAnnounced(storageKey, tokenLabel)
                    if (continuation.isActive) continuation.resume(Unit)
                },
            )
        }
    }
}

private suspend fun awaitTtsWarmForTokenAnnounce(
    ttsWarmDeferred: Deferred<Boolean>?,
    pathLabel: String,
): Boolean {
    val warmed = withTimeoutOrNull(12_000L) {
        ttsWarmDeferred?.await() ?: false
    }
    if (warmed != true) {
        Log.w(
            "TokenDisplay",
            "TTS not ready before $pathLabel chime (timeout=$warmed); attempting speech anyway",
        )
    }
    return warmed == true
}

