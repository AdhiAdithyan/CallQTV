@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.softland.callqtv.R
import com.softland.callqtv.utils.*
import com.softland.callqtv.viewmodel.MqttViewModel
import com.softland.callqtv.viewmodel.TokenDisplayViewModel
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.utils.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.BufferedInputStream
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.net.URLConnection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TokenDisplayActivity : ComponentActivity() {

    private lateinit var viewModel: TokenDisplayViewModel
    private lateinit var mqttViewModel: MqttViewModel

    override fun onStart() {
        super.onStart()
        TokenAnnouncer.initialize(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screen from sleeping while this activity is in the foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        

        
        viewModel = ViewModelProvider(this)[TokenDisplayViewModel::class.java]
        mqttViewModel = ViewModelProvider(this)[MqttViewModel::class.java]
        
        // First launch: show the full-screen loading overlay once
        viewModel.loadData(mqttViewModel, forceShowOverlay = true)

        setContent {
            // Theme State - load async to avoid blocking main thread during composition
            val context = LocalContext.current
            
            // Check & Request Storage Permissions
            LaunchedEffect(Unit) {
                checkAndRequestStoragePermissions()
            }

            var currentThemeHex by remember { mutableStateOf("#2196F3") }
            var counterBgHex by remember { mutableStateOf("#FFFFFF") }
            var tokenBgHex by remember { mutableStateOf("#FFFFFF") }
            LaunchedEffect(Unit) {
                try {
                    withContext(Dispatchers.Default) {
                        currentThemeHex = ThemeColorManager.getSelectedThemeColorHex(context)
                        counterBgHex = ThemeColorManager.getCounterBackgroundColor(context)
                        tokenBgHex = ThemeColorManager.getTokenBackgroundColor(context)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Keep defaults on error
                }
            }
            
            val themeColor = remember(currentThemeHex) { 
                try { Color(AndroidColor.parseColor(currentThemeHex)) } catch (e: Exception) { Color(0xFF2196F3) }
            }
            val colorScheme = remember(themeColor) { ThemeColorManager.createDarkColorScheme(themeColor) }
            
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TokenDisplayScreen(
                        viewModel, 
                        mqttViewModel,
                        counterBgHex = counterBgHex,
                        tokenBgHex = tokenBgHex,
                        onThemeChange = { newHex ->
                            ThemeColorManager.setThemeColor(this, newHex)
                            currentThemeHex = newHex
                        },
                        onCounterBgChange = { newHex ->
                            ThemeColorManager.setCounterBackgroundColor(this, newHex)
                            counterBgHex = newHex
                        },
                        onTokenBgChange = { newHex ->
                            ThemeColorManager.setTokenBackgroundColor(this, newHex)
                            tokenBgHex = newHex
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaEngine.shutdown()
        TokenAnnouncer.shutdown()
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needsRequest = permissions.any {
                ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (needsRequest) {
                ActivityCompat.requestPermissions(this, permissions, 1001)
            }
        }
    }
}

// Play a short built-in chime before announcing a token.
// Uses ToneGenerator so it does not depend on network or external URLs.
suspend fun playTokenChime(soundKey: String) {
    withContext(Dispatchers.Default) {
        val (tone, durationMs) = when (soundKey) {
            // Dings
            "ding"   -> ToneGenerator.TONE_PROP_BEEP2 to 120
            "ding2"  -> ToneGenerator.TONE_PROP_BEEP2 to 280
            "ding3"  -> ToneGenerator.TONE_PROP_BEEP2 to 350
            "ding4"  -> ToneGenerator.TONE_PROP_BEEP2 to 420
            "ding5"  -> ToneGenerator.TONE_PROP_BEEP2 to 500
            // Doubles
            "double"  -> ToneGenerator.TONE_PROP_ACK to 240
            "double2" -> ToneGenerator.TONE_PROP_ACK to 320
            "double3" -> ToneGenerator.TONE_PROP_ACK to 400
            "double4" -> ToneGenerator.TONE_PROP_ACK to 480
            // Soft beeps
            "soft"  -> ToneGenerator.TONE_PROP_BEEP to 150
            "soft2" -> ToneGenerator.TONE_PROP_BEEP to 240
            "soft3" -> ToneGenerator.TONE_PROP_BEEP to 320
            "soft4" -> ToneGenerator.TONE_PROP_BEEP to 400
            // Alerts
            "alert"  -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 260
            "alert2" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 360
            "alert3" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 450
            "alert4" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 520
            // Bells
            "bell"  -> ToneGenerator.TONE_SUP_PIP to 240
            "bell2" -> ToneGenerator.TONE_SUP_PIP to 320
            "bell3" -> ToneGenerator.TONE_SUP_PIP to 400
            "bell4" -> ToneGenerator.TONE_SUP_PIP to 480
            // Church bells
            "church1" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL to 360
            "church2" -> ToneGenerator.TONE_CDMA_HIGH_L to 390
            "church3" -> ToneGenerator.TONE_CDMA_HIGH_PBX_L to 450
            // Pings
            "ping"  -> ToneGenerator.TONE_PROP_PROMPT to 180
            "ping2" -> ToneGenerator.TONE_PROP_PROMPT to 260
            "ping3" -> ToneGenerator.TONE_PROP_PROMPT to 340
            "ping4" -> ToneGenerator.TONE_PROP_PROMPT to 420
            // Long tones
            "long"  -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 420
            "long2" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 540
            "long3" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 660
            "long4" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 780
            // Chimes
            "chime1" -> ToneGenerator.TONE_SUP_RINGTONE to 360
            "chime2" -> ToneGenerator.TONE_SUP_RINGTONE to 480
            "chime3" -> ToneGenerator.TONE_SUP_RINGTONE to 600
            "chime4" -> ToneGenerator.TONE_SUP_RINGTONE to 720
            // High beeps
            "hi1" -> ToneGenerator.TONE_SUP_RADIO_ACK to 180
            "hi2" -> ToneGenerator.TONE_SUP_RADIO_ACK to 260
            "hi3" -> ToneGenerator.TONE_SUP_RADIO_ACK to 340
            "hi4" -> ToneGenerator.TONE_SUP_RADIO_ACK to 420
            // Low beeps
            "low1" -> ToneGenerator.TONE_SUP_CONGESTION to 180
            "low2" -> ToneGenerator.TONE_SUP_CONGESTION to 260
            "low3" -> ToneGenerator.TONE_SUP_CONGESTION to 340
            "low4" -> ToneGenerator.TONE_SUP_CONGESTION to 420
            // Misc tones
            "tone1" -> ToneGenerator.TONE_SUP_DIAL to 210
            "tone2" -> ToneGenerator.TONE_SUP_BUSY to 210
            "tone3" -> ToneGenerator.TONE_SUP_CALL_WAITING to 300
            "tone4" -> ToneGenerator.TONE_SUP_CONFIRM to 300
            "tone5" -> ToneGenerator.TONE_SUP_ERROR to 300
            "tone6" -> ToneGenerator.TONE_SUP_INTERCEPT to 300
            // use another valid system tone for tone7 (reorder is not available on all devices)
            "tone7" -> ToneGenerator.TONE_SUP_CALL_WAITING to 390
            else    -> ToneGenerator.TONE_PROP_BEEP2 to 240 // default ding
        }

        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        try {
            toneGen.startTone(tone, durationMs)
            // Keep total chime time very short
            delay(durationMs + 5L)
        } catch (_: Exception) {
        } finally {
            toneGen.release()
        }
    }
}

@Composable
fun TokenDisplayScreen(
    viewModel: TokenDisplayViewModel, 
    mqttViewModel: MqttViewModel, 
    counterBgHex: String,
    tokenBgHex: String,
    onThemeChange: (String) -> Unit,
    onCounterBgChange: (String) -> Unit,
    onTokenBgChange: (String) -> Unit
) {
    val context = LocalContext.current

    // State map to track the last call timestamp per counter, used to restart blinking on re-call.
    val blinkTriggers = remember { mutableStateMapOf<String, Long>() }
    
    val isLoading by viewModel.isLoading.observeAsState(false)
    val isPendingApproval by viewModel.isPendingApproval.observeAsState(false)
    val isLicenseExpired by viewModel.isLicenseExpired.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState(null)

    LaunchedEffect(isLicenseExpired) {
        mqttViewModel.setLicenseExpired(isLicenseExpired)
    }
    val config by viewModel.config.observeAsState(null)
    val counters by viewModel.counters.observeAsState(emptyList())
    val adFiles by viewModel.adFiles.observeAsState(emptyList())
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
    var showMqttRetryDialog by remember { mutableStateOf(false) }
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
    var reconnectUiSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(brokerConnected, showMqttRetryDialog) {
        if (!brokerConnected && !showMqttRetryDialog) {
            reconnectUiSeconds = 0
            while (!brokerConnected && !showMqttRetryDialog) {
                delay(1000)
                reconnectUiSeconds += 1
                if (reconnectUiSeconds >= 30) {
                    // Hard cap retry window at 30s; trigger fresh retry immediately.
                    mqttViewModel.retryConnect()
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

    // Initialize TTS in a separate phase after config loading completes, so it does not
    // appear as "API/config loading" time to users.
    LaunchedEffect(config?.audioLanguage, isLoading) {
        val cfg = config ?: return@LaunchedEffect
        if (isLoading) return@LaunchedEffect

        val lang = cfg.audioLanguage
        if (lastInitializedTtsLanguage == lang) return@LaunchedEffect

        showTtsLoading = true
        TokenAnnouncer.initialize(context, lang) {
            Handler(Looper.getMainLooper()).post {
                showTtsLoading = false
            }
        }
        lastInitializedTtsLanguage = lang
    }

    val latestConfigState = rememberUpdatedState(config)
    val latestCountersState = rememberUpdatedState(counters)

    LaunchedEffect(Unit) {
        mqttViewModel.tokenUpdateChannel.receiveAsFlow().collect { pair ->
            try {
                val (counterIdOrName, tokenLabel) = pair

                val currentConfig = latestConfigState.value
                val currentCounters = latestCountersState.value

                // 1. Drop any tokens whose counter does NOT match a buttonIndex
                val mqttCounterIdx = counterIdOrName.toIntOrNull()
                val actualCounter = currentCounters.find {
                    it.buttonIndex != null && it.buttonIndex == mqttCounterIdx
                }

                if (actualCounter == null) {
                    android.util.Log.d(
                        "TokenDisplay",
                        "Dropping token '$tokenLabel' for unknown counter '$counterIdOrName'"
                    )
                    return@collect
                }

                // 2. Use a canonical storage key so the same physical counter always shares history,
                //    even if MQTT uses different identifiers (id, name, button index).
                val storageKey =
                    actualCounter.counterId?.trim()?.takeIf { it.isNotBlank() }
                        ?: actualCounter.name?.trim()?.takeIf { it.isNotBlank() }
                        ?: actualCounter.defaultName?.trim()?.takeIf { it.isNotBlank() }
                        ?: actualCounter.buttonIndex?.toString()
                        ?: counterIdOrName.trim()
                val btnKey = actualCounter.buttonIndex?.toString()?.trim()

                // Rule: If this exact token is already at first position and the last same
                // announcement is within 10s, skip BOTH announcement and UI update.
                if (mqttViewModel.shouldSkipTopTokenWithin10s(storageKey, tokenLabel)) {
                    return@collect
                }

                // Deduplicate: skip chime/TTS if we already announced this token call recently
                if (mqttViewModel.isAlreadyAnnounced(storageKey, tokenLabel)) {
                    return@collect
                }

                // Mark before playing any sound so duplicate messages don't re-play
                mqttViewModel.markAsAnnounced(storageKey, tokenLabel)
                if (btnKey != null) mqttViewModel.markAsAnnounced(btnKey, tokenLabel)

                // Show token in UI at announcement start (not after completion).
                val isNewOrMoved = mqttViewModel.processTokenUpdateForKeys(storageKey, tokenLabel, btnKey)
                if (!isNewOrMoved) return@collect

                // Signal re-call by updating timestamp (blink restart) at announcement start.
                blinkTriggers[storageKey] = System.currentTimeMillis()

                // Keep sequential announcement handling and bounded timeout so idle-state
                // TTS does not stall the pipeline for long periods.

                // Brief pause to keep speech/chime pacing natural.
                delay(50)

                // Always play notification chime first.
                val soundKey = ThemeColorManager.getNotificationSoundKey(context)
                playTokenChime(soundKey)

                if (currentConfig?.enableTokenAnnouncement == true) {
                    val displayName =
                        (actualCounter.name?.takeIf { it.isNotBlank() }
                            ?: actualCounter.defaultName?.takeIf { it.isNotBlank() }
                            ?: "Counter ${actualCounter.buttonIndex}")

                    val announcementCounterName =
                        if (currentConfig.enableCounterAnnouncement == true) displayName else ""

                    val counterCode = actualCounter.code.orEmpty().trim().ifBlank {
                        actualCounter.defaultCode.orEmpty().trim()
                    }
                    val usePrefix = currentConfig.enableCounterPrefix != false
                    val tokenLabelWithCode =
                        if (usePrefix && counterCode.isNotBlank()) "$counterCode-$tokenLabel" else tokenLabel

                    // Cap announcement wait to avoid long idle wake-up delays.
                    withTimeoutOrNull(6000) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            TokenAnnouncer.announceToken(
                                context = context,
                                audioLanguage = currentConfig.audioLanguage,
                                counterName = announcementCounterName,
                                tokenLabel = tokenLabelWithCode,
                                onDone = {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            )
                        }
                    }
                }
            } finally {
                mqttViewModel.announcementQueueSize.decrementAndGet()
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

        if (config != null) {
            val cfg = config!!

            Box(modifier = Modifier.fillMaxSize()) {
                TokenDisplayContent(
                    config = cfg,
                    macAddress = macAddress,
                    appVersion = appVersion,
                    isMqttConnected = brokerConnected,
                    isNetworkAvailable = isNetworkAvailable,
                    counters = counters,
                    adFiles = viewModel.localAdFiles.observeAsState(emptyList()).value,
                    tokensPerCounter = tokensPerCounter,
                    daysUntilLicenseExpiry = daysUntilExpiry,
                    dateTime = currentDateTime,
                    counterBgHex = counterBgHex,
                    tokenBgHex = tokenBgHex,
                    onThemeChange = onThemeChange,
                    onCounterBgChange = onCounterBgChange,
                    onTokenBgChange = onTokenBgChange,
                    onRefresh = { viewModel.loadData(mqttViewModel, forceShowOverlay = true) },
                    blinkTriggers = blinkTriggers
                )

                PendingCallsBadge(
                    pendingCallCount = pendingCallCount,
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                ReconnectStatusBadge(
                    visible = !brokerConnected && !showMqttRetryDialog,
                    retryAttempt = mqttRetryAttempt,
                    reconnectUiSeconds = reconnectUiSeconds,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }
    }

    // Overlays and Dialogs - Placed at the end to ensure they draw on top
    if (showTtsLoading && !isLoading) {
        VoiceInitializationDialog(isVisible = true)
    }

    if (isLoading) {
        AnimatedLoadingOverlay(
            message = "Loading TV configuration.\nPlease wait...",
            isVisible = true
        )
    } else if (isPendingApproval) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            title = {
                Text(
                    "Device Awaiting Approval",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        errorMessage
                            ?: "This display is awaiting approval from the administrator.\n" +
                               "Please contact support or tap Retry after approval.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Device ID: $macAddress",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.loadData(mqttViewModel) }) {
                    Text("Retry")
                }
            }
        )
    } else if (isLicenseExpired) {
        LicenseExpiredDialog(
            macAddress = macAddress,
            errorMessage = errorMessage,
            onRefresh = { viewModel.loadData(mqttViewModel, forceShowOverlay = true) }
        )
    } else if (!errorMessage.isNullOrBlank() && !isAutoRetryExhausted) {
        // Do not show any Configuration Error dialog; just log the error once per value.
        LaunchedEffect(errorMessage) {
            android.util.Log.w("TokenDisplay", "Configuration error (UI suppressed): $errorMessage")
            viewModel.clearConfigurationError()
        }
    }

    // MQTT Retry Dialog - Move to the end so it always appears on top
    // Show dialog purely based on showMqttRetryDialog so it does not auto-close
    // when background retries clear the mqttError string.
    if (showMqttRetryDialog) {
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.9f),
            onDismissRequest = { 
                showMqttRetryDialog = false
                mqttRetryShownAt = null
            },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = com.softland.callqtv.R.drawable.ic_network_unavailable), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BLUCON Connection Failed")
                }
            },
            text = { 
                Column {
                    Text(
                        "The display could not connect to the messaging server (Attempt $mqttRetryAttempt).\n" +
                        "Please check your network or broker settings, then tap Retry.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Device ID: $macAddress",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.focusRequester(mqttRetryFocusRequester),
                    onClick = {
                        // User-initiated retry: close dialog and trigger reconnect
                        showMqttRetryDialog = false
                        mqttRetryShownAt = null
                        mqttViewModel.retryConnect(resetAttempts = true)
                    }
                ) {
                    Text("Retry Connection")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showMqttRetryDialog = false
                    mqttRetryShownAt = null
                }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PendingCallsBadge(
    pendingCallCount: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = pendingCallCount > 0,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        Surface(
            modifier = modifier.padding(top = 8.dp, end = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xCC1E1E1E),
            border = BorderStroke(1.dp, Color(0xFF64B5F6))
        ) {
            Text(
                text = "Pending calls: $pendingCallCount",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ReconnectStatusBadge(
    visible: Boolean,
    retryAttempt: Int,
    reconnectUiSeconds: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        val retryCount = retryAttempt.coerceAtLeast(1)
        Surface(
            modifier = modifier.padding(top = 8.dp, start = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xCC2E2E2E),
            border = BorderStroke(1.dp, Color(0xFFFFA726))
        ) {
            Text(
                text = "Connecting to BLUCON... Try $retryCount | ${reconnectUiSeconds}s",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun MqttErrorBar(error: String, exhausted: Boolean, retryAttempt: Int, onRetry: () -> Unit) {
    val retryFocusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val message = when {
            exhausted -> "BROKER: Connection timeout"
            error.contains("Connection lost", ignoreCase = true) && retryAttempt > 0 ->
                "BROKER: Connection lost (Retrying $retryAttempt)"
            error.contains("Connection lost", ignoreCase = true) ->
                "BROKER: Connection lost (Retrying)"
            retryAttempt > 0 ->
                "BROKER : Connecting... (Retrying $retryAttempt)"
            else ->
                "BROKER : Connecting... (Retrying)"
        }

        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 13.sp
        )
        if (exhausted) {
            LaunchedEffect(exhausted) {
                delay(100)
                try {
                    retryFocusRequester.requestFocus()
                } catch (_: IllegalStateException) { /* focus tree not ready */ }
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .height(32.dp)
                    .focusRequester(retryFocusRequester)
            ) {
                Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TokenDisplayContent(
    config: TvConfigEntity,
    macAddress: String,
    appVersion: String,
    isMqttConnected: Boolean,
    isNetworkAvailable: Boolean,
    counters: List<CounterEntity>,
    adFiles: List<AdFileEntity>,
    tokensPerCounter: Map<String, List<String>>,
    daysUntilLicenseExpiry: Int? = null,
    dateTime: String,
    counterBgHex: String,
    tokenBgHex: String,
    onThemeChange: (String) -> Unit,
    onCounterBgChange: (String) -> Unit,
    onTokenBgChange: (String) -> Unit,
    onRefresh: () -> Unit,
    blinkTriggers: Map<String, Long>
) {
    val viewModel = viewModel<com.softland.callqtv.viewmodel.TokenDisplayViewModel>()
    val mqttViewModel = viewModel<MqttViewModel>()
    val context = LocalContext.current
    var is24HourPref by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        is24HourPref = withContext(Dispatchers.Default) {
            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .getBoolean("use_24_hour_format", true)
        }
    }
    
    LaunchedEffect(is24HourPref) {
        viewModel.setTimeFormat(is24HourPref)
    }

    // Removed blinkTriggers definition from here as it is now passed down from TokenDisplayScreen

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val deviceIsPortrait = screenHeight > screenWidth

    // Use config.orientation when set; otherwise fall back to device orientation.
    // Be tolerant of common variants/misspellings (e.g., "potrait", "P", "L").
    val usePortraitLayout = remember(config.orientation, deviceIsPortrait) {
        val raw = config.orientation?.trim()
        val o = raw?.lowercase()
        when {
            o == null -> deviceIsPortrait
            o == "portrait" || o == "potrait" ||
                o == "p" || o.startsWith("port") -> true
            o == "landscape" || o == "l" || o.startsWith("land") -> false
            else -> deviceIsPortrait
        }
    }

    // IMPORTANT: scale (and thus font sizes) should follow the *physical* screen
    // orientation, not tv_config.orientation, so text size doesn't jump when
    // only the layout mode changes.
    val scale = remember(screenWidth, screenHeight, deviceIsPortrait) {
        if (deviceIsPortrait) {
            (screenWidth.value / 360f).coerceIn(0.6f, 1.2f)
        } else {
            (screenWidth.value / 1280f).coerceIn(0.5f, 1.6f)
        }
    }

    val responsivePadding = remember(scale) { (8.dp * scale).coerceAtLeast(2.dp) }
    
    val countersToDisplay = remember(counters, config.noOfCounters) {
        val limit = config.noOfCounters ?: counters.size
        counters.take(limit)
    }

    val showAds = config.showAds?.equals("on", ignoreCase = true) == true
    val adPlacement = config.adPlacement ?: "right"
    // Token grid shape comes directly from config (no swapping), so backend fully controls
    // how many tokens are shown per row/column.
    val rows = remember(config.displayRows) { (config.displayRows ?: 3).coerceAtLeast(1) }
    val columns = remember(config.displayColumns) { (config.displayColumns ?: 4).coerceAtLeast(1) }
    val companyName = if (config.companyName.isNotBlank()) config.companyName else "CALL-Q"

    val primary = MaterialTheme.colorScheme.primary
    val bgIntensity = remember { 0.15f }
    val backgroundBrush = remember(primary, bgIntensity) { 
        Brush.verticalGradient(colors = listOf(Color.White, primary.copy(alpha = bgIntensity))) 
    }

    Column(modifier = Modifier.fillMaxSize().background(backgroundBrush).padding(responsivePadding)) {
        HeaderArea(
            companyName = companyName,
            dateTime = dateTime,
            isMqttConnected = isMqttConnected,
            isNetworkAvailable = isNetworkAvailable,
            // Header should follow physical screen shape, not config.orientation
            isPortrait = deviceIsPortrait,
            scale = scale,
            responsivePadding = responsivePadding,
            onThemeChange = onThemeChange,
            onCounterBgChange = onCounterBgChange,
            onTokenBgChange = onTokenBgChange,
            macAddress = macAddress,
            appVersion = appVersion,
            daysUntilExpiry = daysUntilLicenseExpiry,
            isTokenAnnouncementEnabled = config.enableTokenAnnouncement,
            isCounterAnnouncementEnabled = config.enableCounterAnnouncement,
            isCounterPrefixEnabled = config.enableCounterPrefix,
            onRefresh = onRefresh,
            onClearTokenHistoryAndRefresh = {
                mqttViewModel.clearTokenHistory()
                viewModel.loadData(mqttViewModel, forceShowOverlay = true)
            }
        )

        Spacer(modifier = Modifier.height(responsivePadding))

        val hasAds = showAds && adFiles.isNotEmpty()
        val baseLayoutType = config.layoutType ?: "1"
        // In portrait mode: treat each counter as a row, tokens as columns (type 2 behavior).
        // When counters increase, CountersArea(type 2) already arranges them into a grid of rows/columns.
        val layoutType = if (usePortraitLayout) "2" else baseLayoutType

        // Decide how much horizontal space ads vs counters should take when ads are visible.
        // Use configuration (no_of_counters) to choose between 40% and 50% for ads:
        // - If ads are enabled AND there are 2 or fewer counters, ads take 50% (bigger ad area).
        // - Otherwise, ads take 40%. Counters take the remaining width.
        val counterCount = countersToDisplay.size
        val configuredCounterLimit = config.noOfCounters ?: counterCount
        val adWeight = remember(showAds, configuredCounterLimit) {
            if (!showAds) 0f
            else if (configuredCounterLimit <= 2) 0.5f
            else 0.4f
        }
        val countersWeight = remember(adWeight) { if (adWeight > 0f) 1f - adWeight else 1f }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (usePortraitLayout) {
                if (hasAds) {
                    // Portrait + ads: Ad area on the left, vertical counters on the right
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(adWeight).fillMaxHeight()) {
                            AdArea(adFiles, config)
                        }
                        Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                            CountersArea(
                                countersToDisplay,
                                tokensPerCounter,
                                config,
                                rows,
                                columns,
                                layoutType,
                                scale,
                                counterBgHex,
                                tokenBgHex,
                                isPortrait = usePortraitLayout,
                                hasAds = hasAds,
                                blinkTriggers = blinkTriggers
                            )
                        }
                    }
                } else {
                    // Portrait + no ads: full-width vertical counters
                    CountersArea(
                        countersToDisplay,
                        tokensPerCounter,
                        config,
                        rows,
                        columns,
                        layoutType,
                        scale,
                        counterBgHex,
                        tokenBgHex,
                        isPortrait = usePortraitLayout,
                        hasAds = hasAds,
                        blinkTriggers = blinkTriggers
                    )
                }
            } else {
                // Landscape: preserve existing behavior with layout_type and ad_placement
                if (hasAds) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (adPlacement.equals("left", ignoreCase = true)) {
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight()) { AdArea(adFiles, config) }
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                            }
                        } else {
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                            }
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight()) { AdArea(adFiles, config) }
                        }
                    }
                } else {
                    CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                }
            }
        }

        // Footer: restore configured scrolling section based on TV config
        val scrollEnabled = config.scrollEnabled?.equals("on", ignoreCase = true) == true
        val noOfTextFields = config.noOfTextFields ?: 0
        val scrollTextLinesJson = config.scrollTextLinesJson

        // Parse JSON on background to avoid blocking main thread during composition
        val gson = remember { Gson() }
        val scrollLines by produceState<List<String>>(
            initialValue = emptyList(),
            scrollTextLinesJson
        ) {
            value = if (!scrollTextLinesJson.isNullOrBlank()) {
                withContext(Dispatchers.Default) {
                    try {
                        val type = object : TypeToken<List<String>>() {}.type
                        gson.fromJson<List<String>>(scrollTextLinesJson, type) ?: emptyList()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }
            } else emptyList()
        }

        if (scrollEnabled && noOfTextFields > 0 && scrollLines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(responsivePadding))
            ScrollingFooter(
                textLines = scrollLines,
                scale = scale,
                // Footer should also follow physical screen shape only
                isPortrait = deviceIsPortrait
            )
        }
    }
}

@Composable
fun HeaderArea(
    companyName: String,
    dateTime: String,
    isMqttConnected: Boolean,
    isNetworkAvailable: Boolean,
    isPortrait: Boolean,
    scale: Float,
    responsivePadding: androidx.compose.ui.unit.Dp,
    onThemeChange: (String) -> Unit,
    onCounterBgChange: (String) -> Unit,
    onTokenBgChange: (String) -> Unit,
    macAddress: String,
    appVersion: String,
    daysUntilExpiry: Int?,
    isTokenAnnouncementEnabled: Boolean?,
    isCounterAnnouncementEnabled: Boolean?,
    isCounterPrefixEnabled: Boolean?,
    onRefresh: () -> Unit,
    onClearTokenHistoryAndRefresh: () -> Unit
) {
    // Fallback local clock so DateTime display stays live even if upstream updates stall.
    val use24Hour = remember(dateTime) { !dateTime.contains("AM") && !dateTime.contains("PM") }
    var displayDateTime by remember(dateTime) { mutableStateOf(dateTime) }
    LaunchedEffect(dateTime) {
        if (dateTime.isNotBlank()) displayDateTime = dateTime
    }
    LaunchedEffect(use24Hour) {
        while (true) {
            val pattern = if (use24Hour) "dd-MM-yyyy HH:mm:ss" else "dd-MM-yyyy hh:mm:ss a"
            val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            displayDateTime = LocalDateTime.now().format(formatter)
            delay(1000)
        }
    }

    var showThemeDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        val context = LocalContext.current
        AppearanceSettingsDialog(
            context = context,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { newHex ->
                onThemeChange(newHex)
            },
            onCounterBgChange = onCounterBgChange,
            onTokenBgChange = onTokenBgChange,
            onClearTokenHistoryAndRefresh = onClearTokenHistoryAndRefresh,
            macAddress = macAddress,
            appVersion = appVersion,
            daysUntilExpiry = daysUntilExpiry,
            isTokenAnnouncementEnabled = isTokenAnnouncementEnabled,
            isCounterAnnouncementEnabled = isCounterAnnouncementEnabled,
            isCounterPrefixEnabled = isCounterPrefixEnabled,
            companyName = companyName
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(horizontal = responsivePadding, vertical = (responsivePadding / 2).coerceAtLeast(2.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: App Name
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height((if (isPortrait) 50 else 65).dp * scale)
                    .width((if (isPortrait) 88 else 120).dp * scale)
                    .clip(RoundedCornerShape(8.dp))
                    // Slightly soften the logo background to reduce harsh contrast.
                    .background(Color(0xFF0D1B2A))
                    .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.callq_tv_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Center: Company Name
        Box(modifier = Modifier.weight(2f)) {
            Text(
                text = companyName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold, 
                    fontSize = (if (isPortrait) 26 else 30).sp * scale
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        // Right: Status & Settings
        Box(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = displayDateTime,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (if (isPortrait) 16 else 24).sp * scale),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BluconStatusIndicator(
                            isOnline = isMqttConnected,
                            isPortrait = isPortrait,
                            scale = scale
                        )
                        NetworkStatusIndicator(
                            isOnline = isNetworkAvailable,
                            isPortrait = isPortrait,
                            scale = scale
                        )
                    }
                }
                
                // Refresh Button
                Box(
                    modifier = Modifier
                        .clickable { onRefresh() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Configuration",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size((36 * scale).dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Theme Settings Button
                Box(
                    modifier = Modifier
                        .clickable { showThemeDialog = true }
                        .padding(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Change Theme",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size((36 * scale).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BluconStatusIndicator(isOnline: Boolean, isPortrait: Boolean, scale: Float) {
    val color = if (isOnline) Color(0xFF4AFF52) else Color(0xFFFF0000)
    val iconSize = ((if (isPortrait) 20 else 28) * scale).dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            imageVector = if (isOnline) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
            contentDescription = "BLUCON",
            tint = color,
            modifier = Modifier.size(iconSize)
        )
/*        Text(
//            text = "BLUCON: ${if (isOnline) "Online" else "Offline"}",
            text = ": ${if (isOnline) "Online" else "Offline"}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = (if (isPortrait) 12 else 18).sp * scale),
            color = MaterialTheme.colorScheme.onBackground
        )*/
    }
}

@Composable
fun NetworkStatusIndicator(isOnline: Boolean, isPortrait: Boolean, scale: Float) {
    val networkIconRes = if (isOnline) R.drawable.ic_network_available else R.drawable.ic_network_unavailable
    val networkIconColor = if (isOnline) Color(0xFF4AFF52) else Color(0xFFFF0000)
    val iconSize = ((if (isPortrait) 20 else 28) * scale).dp
    val labelFontSize = (if (isPortrait) 12 else 18).sp * scale
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            painter = painterResource(id = networkIconRes),
            contentDescription = "Network Status",
            tint = networkIconColor,
            modifier = Modifier.size(iconSize)
        )
      /*  Text(
//            text = "NETWORK: ${if (isOnline) "Online" else "Offline"}",
            text = ": ${if (isOnline) "Online" else "Offline"}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = labelFontSize),
            color = MaterialTheme.colorScheme.onBackground
        )*/
    }
}

@Composable
fun AdArea(adFiles: List<AdFileEntity>, config: TvConfigEntity) {
    val orderedAds = remember(adFiles) { adFiles.sortedBy { it.position } }
    val context = LocalContext.current
    val allowYoutubeAds = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        .getBoolean("allow_youtube_ads", true)
    val intervalSeconds = (config.adInterval ?: 5).coerceAtLeast(1)

    // visibleAd: Ad currently on screen
    var visibleAd by remember { mutableStateOf(orderedAds.getOrNull(0)) }
    // preparingAd: Ad currently being loaded in background
    var preparingAd by remember { mutableStateOf<AdFileEntity?>(null) }
    // nextAdIndex: Sequence counter
    var nextAdIndex by remember { mutableStateOf(0) }
    
    // Player indexing for dual-player setup
    var activePlayerIdx by remember { mutableStateOf(0) }
    var isNextReady by remember { mutableStateOf(false) }

    val triggerNext = {
        if (orderedAds.isNotEmpty()) {
            nextAdIndex = (nextAdIndex + 1) % orderedAds.size
            isNextReady = false
        }
    }

    // Logic to start preloading the next ad
    LaunchedEffect(nextAdIndex, orderedAds.size) {
        if (orderedAds.isEmpty()) return@LaunchedEffect
        
        val targetAd = orderedAds[nextAdIndex]
        preparingAd = targetAd
        isNextReady = false

        val mediaType = resolveAdMediaType(targetAd.filePath, context)
        val isVideo = mediaType == AdMediaType.Video
        val isYouTube = mediaType == AdMediaType.YouTube

        if (isVideo || isYouTube) {
            if (isYouTube && !NetworkUtil.isNetworkAvailable(context)) {
                // Some TV networks are not marked "validated" by Android despite working internet.
                // Do not hard-skip YouTube ads here; allow WebView load and handle real failures via callbacks/timeouts.
                Log.w("YouTubeAdFlow", "Network appears unvalidated; still attempting YouTube ad load")
                FileLogger.logError(context, "YouTubeAdFlow", "Network appears unvalidated; still attempting YouTube ad load")
            }
            // Video or YouTube: wait for onReady or timeout
            withTimeoutOrNull(30000) { // 30s timeout for high-res/remote buffering
                while (!isNextReady) { delay(200) }
            }
            if (!isNextReady) {
                if (isYouTube) {
                    Log.w("YouTubeAdFlow", "Skipping YouTube ad: ready timeout (30s)")
                    FileLogger.logError(context, "YouTubeAdFlow", "Skipping YouTube ad: ready timeout (30s)")
                }
                triggerNext() 
            } else {
                activePlayerIdx = 1 - activePlayerIdx
                visibleAd = targetAd
            }
        } else {
            // Image logic: delay then switch
            if (visibleAd != null) {
                delay(intervalSeconds * 1000L)
            }
            visibleAd = targetAd
            isNextReady = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), contentAlignment = Alignment.Center) {
        if (orderedAds.isEmpty()) {
            Text("No Ads", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Main Display
            Crossfade(targetState = visibleAd, animationSpec = tween(0), label = "ad_fade") { ad ->
                if (ad != null) {
                    val mediaType = remember(ad.filePath) { resolveAdMediaType(ad.filePath, context) }
                    val adIsVideo = mediaType == AdMediaType.Video
                    val adIsYouTube = mediaType == AdMediaType.YouTube
                    
                    if (adIsYouTube) {
                        if (allowYoutubeAds) {
                            val videoId = extractYoutubeVideoId(ad.filePath)
                            if (videoId != null) {
                                YouTubeAdPlayer(
                                    videoId = videoId,
                                    onVideoEnded = { triggerNext() },
                                    onReady = { if (ad == preparingAd) isNextReady = true },
                                    onError = { triggerNext() }
                                )
                            } else triggerNext()
                        } else {
                            // Embedded YouTube WebView playback can cause global UI jank on TV devices.
                            // Keep disabled by default unless explicitly enabled from settings.
                            Log.i("YouTubeAdFlow", "Skipping YouTube ad: disabled by setting allow_youtube_ads=false")
                            FileLogger.logError(context, "YouTubeAdFlow", "Skipping YouTube ad: disabled by setting allow_youtube_ads=false")
                            LaunchedEffect(ad.filePath) {
                                delay(300)
                                triggerNext()
                            }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Skipping YouTube ad (disabled in settings)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (adIsVideo) {
                        AdVideoPlayer(
                            videoUrl = ad.filePath,
                            player = MediaEngine.get(context, activePlayerIdx),
                            onVideoEnded = { triggerNext() },
                            onReady = { if (ad == preparingAd) isNextReady = true },
                            onError = { triggerNext() }
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ad.filePath)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Ad",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onSuccess = { if (ad == preparingAd) isNextReady = true },
                            onError = { triggerNext() }
                        )
                    }
                }
            }
            
            // Background Preloader (uses the OTHER player)
            if (!isNextReady && preparingAd != visibleAd && preparingAd != null) {
                val nextMediaType = remember(preparingAd!!.filePath) {
                    resolveAdMediaType(preparingAd!!.filePath, context)
                }
                val nextIsVideo = nextMediaType == AdMediaType.Video
                val nextIsYouTube = nextMediaType == AdMediaType.YouTube
                
                if (nextIsYouTube) {
                    // Avoid hidden YouTube player preloading.
                    // Creating offscreen WebView-backed players can stall low-power TV devices.
                    isNextReady = true
                } else if (nextIsVideo) {
                    Box(modifier = Modifier.size(1.dp).alpha(0.01f)) {
                        AdVideoPlayer(
                            videoUrl = preparingAd!!.filePath,
                            player = MediaEngine.get(context, 1 - activePlayerIdx),
                            onVideoEnded = { },
                            onReady = { isNextReady = true },
                            onError = { triggerNext() }
                        )
                    }
                }
            }
        }
    }
}

private fun extractYoutubeVideoId(url: String): String? {
    val u = url.trim()
    Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    val regex = "^.*((youtu.be\\/)|(v\\/)|(\\/u\\/\\w\\/)|(embed\\/)|(watch\\?))\\??v?=?([^#&?]*).*".toRegex()
    val matchResult = regex.find(u)
    return matchResult?.groupValues?.get(7)?.takeIf { it.length == 11 }
}

private fun isAdVideo(path: String): Boolean {
    val lc = path.lowercase().substringBefore("?").substringBefore("#")
    return lc.endsWith(".mp4") || lc.endsWith(".mkv") || lc.endsWith(".mov") || 
           lc.endsWith(".3gp") || lc.endsWith(".webm") || lc.endsWith(".avi") || 
           lc.endsWith(".flv") || lc.endsWith(".ts") || lc.endsWith(".m4v") || 
           lc.endsWith(".mpg") || lc.endsWith(".mpeg") || lc.endsWith(".m2ts")
}

private fun isAdImage(path: String): Boolean {
    val lc = path.lowercase().substringBefore("?").substringBefore("#")
    return lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") ||
        lc.endsWith(".gif") || lc.endsWith(".webp") || lc.endsWith(".bmp") ||
        lc.endsWith(".svg")
}

private enum class AdMediaType { YouTube, Video, Image }

private fun resolveAdMediaType(path: String, context: Context): AdMediaType {
    val lc = path.lowercase()
    if (lc.contains("youtube.com") || lc.contains("youtu.be") || lc.contains("youtube-nocookie.com")) {
        return AdMediaType.YouTube
    }
    if (isAdVideo(path)) return AdMediaType.Video
    if (isAdImage(path)) return AdMediaType.Image

    // Fallback MIME sniffing for extension-less/signed URLs and local files.
    val mime = detectMimeType(path, context)
    return when {
        mime?.startsWith("video/") == true -> AdMediaType.Video
        else -> AdMediaType.Image
    }
}

private fun detectMimeType(path: String, context: Context): String? {
    return try {
        val cleaned = path.substringBefore("?").substringBefore("#")

        if (cleaned.startsWith("content://")) {
            return context.contentResolver.getType(Uri.parse(cleaned))
        }

        val filePath = when {
            cleaned.startsWith("file://") -> Uri.parse(cleaned).path
            else -> cleaned
        }

        // 1) Guess from name first
        URLConnection.guessContentTypeFromName(cleaned)?.let { return it.lowercase() }

        // 2) If local file exists, sniff first bytes
        val localFile = filePath?.let { File(it) }
        if (localFile != null && localFile.exists() && localFile.isFile) {
            BufferedInputStream(localFile.inputStream()).use { input ->
                URLConnection.guessContentTypeFromStream(input)?.let { return it.lowercase() }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun isValidYoutubeVideoIdForEmbed(id: String): Boolean =
    id.length == 11 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

private fun buildYoutubeIframeEmbedHtml(videoId: String): String {
    require(isValidYoutubeVideoIdForEmbed(videoId))
    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
  <style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}#player{width:100%;height:100vh}</style>
</head>
<body>
  <div id="player"></div>
  <script>
    function onYouTubeIframeAPIReady() {
      new YT.Player('player', {
        height: '100%',
        width: '100%',
        videoId: '$videoId',
        playerVars: {
          autoplay: 1,
          mute: 1,
          playsinline: 1,
          rel: 0,
          modestbranding: 1,
          origin: 'https://www.youtube.com'
        },
        events: {
          onReady: function(event) {
            Android.onPlayerReady();
            setTimeout(function() {
              try { event.target.unMute(); } catch (e) {}
            }, 400);
          },
          onStateChange: function(event) {
            if (event.data === YT.PlayerState.ENDED) {
              Android.onPlayerEnded();
            }
          },
          onError: function(event) { Android.onPlayerError(String(event.data)); }
        }
      });
    }
    (function() {
      var tag = document.createElement('script');
      tag.src = 'https://www.youtube.com/iframe_api';
      document.head.appendChild(tag);
    })();
  </script>
</body>
</html>
""".trimIndent()
}

private class YoutubeWebJsBridge(
    private val mainHandler: Handler,
    private val onReady: () -> Unit,
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit
) {
    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onPlayerEnded() {
        mainHandler.post { onEnded() }
    }

    @JavascriptInterface
    fun onPlayerError(code: String) {
        mainHandler.post { onError(code) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
private fun createYoutubeAdWebView(
    context: Context,
    videoId: String,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onLoadOrPlayerError: (String?) -> Unit
): WebView {
    val handler = Handler(Looper.getMainLooper())
    val bridge = YoutubeWebJsBridge(
        mainHandler = handler,
        onReady = onReady,
        onEnded = onEnded,
        onError = { code -> onLoadOrPlayerError(code) }
    )
    val readyFallbackPosted = java.util.concurrent.atomic.AtomicBoolean(false)
    fun postReadyFallback() {
        if (readyFallbackPosted.compareAndSet(false, true)) {
            handler.post { onReady() }
        }
    }
    return WebView(context).apply {
        setBackgroundColor(AndroidColor.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            settings.databaseEnabled = true
        }
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
        // Chrome-like UA improves YouTube embed compatibility on Android TV WebView.
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // If IFrame API is slow or blocked, still allow ad pipeline to proceed after load.
                handler.postDelayed({ postReadyFallback() }, 3500L)
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    handler.post { onLoadOrPlayerError(description) }
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame) {
                    handler.post { onLoadOrPlayerError(error.description?.toString()) }
                }
            }
        }
        addJavascriptInterface(bridge, "Android")
        loadDataWithBaseURL(
            "https://www.youtube.com",
            buildYoutubeIframeEmbedHtml(videoId),
            "text/html",
            "UTF-8",
            "https://www.youtube.com"
        )
    }
}

@Composable
fun YouTubeAdPlayer(
    videoId: String,
    onVideoEnded: () -> Unit,
    onReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current

    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)
    var readyReported by remember(videoId) { mutableStateOf(false) }
    var terminalEventReported by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        if (!isValidYoutubeVideoIdForEmbed(videoId)) {
            Log.e("YouTubeAdFlow", "Invalid YouTube video id for WebView embed")
            FileLogger.logError(context, "YouTubeAdFlow", "Invalid YouTube video id for WebView embed")
            latestOnError()
            return@LaunchedEffect
        }
    }

    LaunchedEffect(videoId) {
        delay(25000)
        if (!readyReported && !terminalEventReported) {
            Log.w("YouTubeAdFlow", "Skipping YouTube ad: WebView ready timeout (25s)")
            FileLogger.logError(context, "YouTubeAdFlow", "Skipping YouTube ad: WebView ready timeout (25s)")
            terminalEventReported = true
            latestOnError()
        }
    }

    key(videoId) {
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            AndroidView(
                factory = { ctx ->
                    createYoutubeAdWebView(
                        context = ctx,
                        videoId = videoId,
                        onReady = {
                            if (!readyReported) {
                                readyReported = true
                                latestOnReady()
                            }
                        },
                        onEnded = {
                            if (!terminalEventReported) {
                                terminalEventReported = true
                                latestOnVideoEnded()
                            }
                        },
                        onLoadOrPlayerError = { detail ->
                            if (!terminalEventReported) {
                                val msg = detail ?: "web_error"
                                Log.e("YouTubeAdFlow", "YouTube WebView load/player error: $msg")
                                FileLogger.logError(context, "YouTubeAdFlow", "YouTube WebView load/player error: $msg")
                                terminalEventReported = true
                                latestOnError()
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxSize().clipToBounds(),
                onRelease = { it.destroy() }
            )
        }
    }
}

@Composable
fun AdVideoPlayer(videoUrl: String, player: ExoPlayer, onVideoEnded: () -> Unit, onReady: () -> Unit = {}, onError: () -> Unit = {}) {
    val context = LocalContext.current
    
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(videoUrl, player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        mainHandler.post { latestOnReady() }
                    }
                    Player.STATE_ENDED -> {
                        mainHandler.post { latestOnVideoEnded() }
                    }
                }
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                Log.e("AdVideoPlayer", "ExoPlayer error playing $videoUrl: ${e.message}", e)
                mainHandler.post { latestOnError() }
            }
        }
        
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    
    LaunchedEffect(videoUrl, player) {
        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@UnstableApi
object MediaEngine {
    private var player1: ExoPlayer? = null
    private var player2: ExoPlayer? = null
    private var simpleCache: androidx.media3.datasource.cache.Cache? = null
    private var cacheFactory: DataSource.Factory? = null

    private fun createPlayer(context: Context): ExoPlayer {
        val httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
        val okHttpFactory = OkHttpDataSource.Factory(httpClient)
        
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "ad_video_cache")
            // Increase cache size to 500MB for higher resolution videos
            val evictor = androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024)
            simpleCache = androidx.media3.datasource.cache.SimpleCache(cacheDir, evictor, androidx.media3.database.StandaloneDatabaseProvider(context))
        }
        
        if (cacheFactory == null) {
            val upstreamFactory = DefaultDataSource.Factory(context, okHttpFactory)
            cacheFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(simpleCache!!)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

        val loadControl = DefaultLoadControl.Builder()
            // Standard buffers (in ms): Min 15s, Max 50s, For Playback 2.5s, After Rebuffer 5s
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .build()

        return ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory!!))
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                val isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                    .getBoolean("enable_ad_sound", false)
                volume = if (isAdSoundEnabled) 1f else 0f
            }
    }

    fun get(context: Context, index: Int = 0): ExoPlayer {
        return if (index == 0) {
            if (player1 == null) player1 = createPlayer(context)
            player1!!
        } else {
            if (player2 == null) player2 = createPlayer(context)
            player2!!
        }
    }

    fun updateVolume(context: Context) {
        val isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getBoolean("enable_ad_sound", false)
        val vol = if (isAdSoundEnabled) 1f else 0f
        player1?.volume = vol
        player2?.volume = vol
    }

    fun shutdown() {
        player1?.release()
        player2?.release()
        player1 = null
        player2 = null
    }
}

@Composable
fun CountersArea(
    counters: List<CounterEntity>,
    tokensPerCounter: Map<String, List<String>>,
    config: TvConfigEntity,
    rows: Int,
    columns: Int,
    layoutType: String,
    scale: Float,
    counterBgHex: String,
    tokenBgHex: String,
    isPortrait: Boolean = false,
    hasAds: Boolean = false,
    blinkTriggers: Map<String, Long> = emptyMap()
) {
    Box(modifier = Modifier.fillMaxSize().padding(1.dp)) {
        val numCounters = counters.size

        // When there are many counters, split the UI into two parts.
        // For landscape layouts (isPortrait = false) use a lower threshold (2),
        // otherwise use 4. Orientation still controls only direction of split.
        val splitThreshold = if (isPortrait) 4 else 2

        if (numCounters > splitThreshold) {
            val firstHalfCount = (numCounters + 1) / 2
            val firstHalf = counters.take(firstHalfCount)
            val secondHalf = counters.drop(firstHalfCount)

            if (isPortrait) {
                // Portrait: split horizontally (top/bottom)
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        firstHalf.forEach { counter ->
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds)
                        }
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L)
                        }
                        if (secondHalf.size < firstHalf.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // Landscape: split vertically (left/right)
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        firstHalf.forEach { counter ->
                            val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L)
                        }
                        if (secondHalf.size < firstHalf.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            if (isPortrait) {
                // Portrait: single vertical stack of counters
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter ->
                        val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                        val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L)
                    }
                }
            } else {
                // Landscape: single horizontal row of counters
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter ->
                        val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                        val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L)
                    }
                }
            }
        }
    }
}

@Composable
fun CounterBoard(
    counter: CounterEntity,
    tokens: List<String>,
    config: TvConfigEntity,
    rows: Int,
    columns: Int,
    modifier: Modifier,
    scale: Float,
    counterBgHex: String,
    tokenBgHex: String,
    isPortrait: Boolean,
    hasAds: Boolean,
    blinkTrigger: Long = 0L
) {
    val counterName = remember(counter.name, counter.defaultName) { 
        (counter.name.orEmpty().ifBlank { counter.defaultName.orEmpty().ifBlank { "Counter" } }).uppercase()
    }
    

    // Counter code from CounterConfig - used to prefix token for display (e.g. "A-36")
    val counterCode = remember(counter.code, counter.defaultCode) {
        counter.code.orEmpty().trim().ifBlank { counter.defaultCode.orEmpty().trim() }
    }

    val counterColor = remember(config.counterTextColor) { 
        parseColorOrDefault(config.counterTextColor, Color.Black) 
    }
    val currentTokenTextColor = remember(config.currentTokenColor, config.tokenTextColor) { 
        parseColorOrDefault(config.currentTokenColor, parseColorOrDefault(config.tokenTextColor, Color.Black)) 
    }
    val previousTokenTextColor = remember(config.previousTokenColor, config.tokenTextColor) { 
        parseColorOrDefault(config.previousTokenColor, parseColorOrDefault(config.tokenTextColor, Color.Gray)) 
    }
    
    // Parse Custom BGs
    val counterBgBrush = remember(counterBgHex) { ThemeColorManager.getBackgroundBrush(counterBgHex) }
    val tokenBgBrush = remember(tokenBgHex) { ThemeColorManager.getBackgroundBrush(tokenBgHex) }

    val counterFontSize = (config.counterFontSize ?: config.fontSize ?: 20).toFloat()
    val tokenFontSize = (config.tokenFontSize ?: config.fontSize ?: 24).toFloat()
    val shouldBlink = config.blinkCurrentToken ?: false
    val blinkSeconds = config.blinkSeconds ?: 0

    // Blink only for blinkSeconds; if blinkSeconds <= 0, blink indefinitely (backward compat).
    // Restart blink timer whenever the current token changes.
    val currentTokenForBlink = tokens.firstOrNull()
    var blinkActive by remember { mutableStateOf(true) }
    LaunchedEffect(shouldBlink, blinkSeconds, currentTokenForBlink, blinkTrigger) {
        blinkActive = true
        if (shouldBlink && blinkSeconds > 0 && currentTokenForBlink != null) {
            delay(blinkSeconds * 1000L)
            blinkActive = false
        }
    }

    val isInverted by if (shouldBlink && blinkActive) {
        val transition = rememberInfiniteTransition(label = "counter_invert")
        val step by transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                // Reduce inversion phase duration by 1/3 (1000ms -> ~667ms)
                animation = tween(667, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "invert_step"
        )
        // Step 0..1 = Inverted, 1..2 = Normal
        remember(step) { mutableStateOf(step < 1f) }
    } else {
        remember { mutableStateOf(false) }
    }

    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(counterBgBrush)) {
            if (isPortrait) {
                Row(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                    // If ads exist, use 30% for name and 70% for tokens.
                    // If no ads, keep name compact and give 80% to tokens.
                    val nameWeight = if (hasAds) 0.30f else 0.2f
                    val tokenWeight = if (hasAds) 0.70f else 0.8f

                    // Counter name area
                    Box(
                        modifier = Modifier
                            .weight(nameWeight)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = counterName,
                            fontWeight = FontWeight.Bold,
                            fontSize = (counterFontSize * scale).sp,
                            color = counterColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Token matrix area
                    Box(
                        modifier = Modifier
                            .weight(tokenWeight)
                            .fillMaxHeight()
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            userScrollEnabled = false
                        ) {
                            val totalSlots = config.tokensPerCounter ?: (rows * columns)
                            items(totalSlots) { index ->
                                val token = tokens.getOrNull(index)
                                val isFirst = index == 0
                                val usePrefix = config.enableCounterPrefix != false
                                val displayToken = when {
                                    token == null -> null
                                    usePrefix && counterCode.isNotBlank() -> "$counterCode-$token"
                                    else -> token
                                }
                                val currentFontSize = if (isFirst) tokenFontSize else tokenFontSize
                                val textColorToUse = if (isFirst) currentTokenTextColor else previousTokenTextColor
                                TokenCard(
                                    token = displayToken,
                                    isPrimary = isFirst,
                                    scale = scale,
                                    textColor = textColorToUse,
                                    bgBrush = tokenBgBrush,
                                    fontSize = currentFontSize,
                                    isInverted = if (isFirst && shouldBlink && token != null) isInverted else false
                                )
                            }
                        }
                    }

                    // No extra spacer needed; nameWeight + tokenWeight should be <= 1f
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = counterName,
                        fontWeight = FontWeight.Bold,
                        fontSize = (counterFontSize * scale).sp,
                        color = counterColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(1.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        userScrollEnabled = false
                    ) {
                        val totalSlots = config.tokensPerCounter ?: (rows * columns)
                        items(totalSlots) { index ->
                            val token = tokens.getOrNull(index)
                            val isFirst = index == 0
                            val usePrefix = config.enableCounterPrefix != false
                            val displayToken = when {
                                token == null -> null
                                usePrefix && counterCode.isNotBlank() -> "$counterCode-$token"
                                else -> token
                            }
                            val currentFontSize = if (isFirst) tokenFontSize else tokenFontSize
                            val textColorToUse = if (isFirst) currentTokenTextColor else previousTokenTextColor
                                TokenCard(
                                    token = displayToken,
                                    isPrimary = isFirst,
                                    scale = scale,
                                    textColor = textColorToUse,
                                    bgBrush = tokenBgBrush,
                                    fontSize = currentFontSize,
                                    isInverted = if (isFirst && shouldBlink && token != null) isInverted else false
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TokenCard(
    token: String?, 
    isPrimary: Boolean, 
    scale: Float, 
    textColor: Color,
    bgBrush: Brush,
    fontSize: Float,
    isInverted: Boolean = false
) {
    // Dynamic height based on font size to prevent clipping
    // Reduced height multiplier to tighten padding around the text
    val cardHeight = (fontSize * 1.6f * scale).coerceIn(32f, 120f).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .padding(1.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        // Swap colors if inverted
        val finalBg = if (isInverted) {
            // If we swap, text color becomes background. 
            // Since bg is Brush, we'll use SolidColor for simplicity when inverted
            SolidColor(textColor)
        } else {
            bgBrush
        }
        
        // Attempt to find a suitable text color when inverted (the old background).
        // If the background is a brush, we might need a default or extract a primary color.
        // For now, if inverted, we'll try to pick a fallback (e.g. white or black) or 
        // if we have a counterBgHex, use that.
        // A simple approach: Inverted mode = White text on original TextColor background
        val finalTextColor = if (isInverted) Color.White else textColor

        Box(
            modifier = Modifier.fillMaxSize().background(finalBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = token ?: "",
                fontWeight = FontWeight.Bold,
                fontSize = (fontSize * scale).sp,
                color = finalTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}


@Composable
fun FooterArea(macAddress: String, appVersion: String, daysExpiry: Int?, padding: androidx.compose.ui.unit.Dp, isPortrait: Boolean, scale: Float) {
    val licenseText = when {
        daysExpiry == null -> "License Valid"
        daysExpiry < 0 -> "License Expired"
        else -> "License expires in $daysExpiry days"
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "license_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, 
        targetValue = 0.5f, 
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = padding, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val footerFontSize = (if (isPortrait) 10 else 12).sp * scale
            
            Text(
                text = "Device: $macAddress", 
                fontSize = footerFontSize, 
                color = Color.Blue,
                textAlign = TextAlign.Center,
                lineHeight = footerFontSize // Tighten line height
            )
            Text(
                text = "v$appVersion", 
                fontSize = footerFontSize, 
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = footerFontSize
            )

            // Only show license text if it's expired or expires soon (< 10 days) as the third line
            if (daysExpiry != null && daysExpiry <= 10) {
                Text(
                    text = licenseText, 
                    fontSize = footerFontSize, 
                    color = Color.Red.copy(alpha = alpha), 
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = footerFontSize
                )
            }
        }
    }
}

private fun parseColorOrDefault(colorString: String?, default: Color): Color {
    return try {
        if (colorString.isNullOrBlank()) default else Color(AndroidColor.parseColor(colorString))
    } catch (_: Exception) {
        default
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsDialog(
    context: Context,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit,
    onCounterBgChange: (String) -> Unit,
    onTokenBgChange: (String) -> Unit,
    onClearTokenHistoryAndRefresh: () -> Unit,
    macAddress: String,
    appVersion: String,
    daysUntilExpiry: Int?,
    isTokenAnnouncementEnabled: Boolean?,
    isCounterAnnouncementEnabled: Boolean?,
    isCounterPrefixEnabled: Boolean?,
    companyName: String
) {
    // Fixed Settings palette (independent from app theme)
    val settingsPrimary = Color(0xFF4FC3F7)
    val settingsText = Color(0xFFECEFF1)
    val settingsMutedText = Color(0xFFB0BEC5)
    val settingsCard = Color(0xFF263238)
    val settingsBorder = Color(0xFF607D8B)
    val settingsError = Color(0xFFEF5350)

    var showThemeColorPicker by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var showCounterColorPicker by remember { mutableStateOf(false) }
    var showTokenColorPicker by remember { mutableStateOf(false) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var currentCounterHex by remember { mutableStateOf("#FFFFFF") }
    var currentTokenHex by remember { mutableStateOf("#FFFFFF") }
    var customerId by remember { mutableStateOf(0) }
    var currentThemeHex by remember { mutableStateOf("#2196F3") }
    var notificationSoundKey by remember { mutableStateOf("ding") }
    var is24Hour by remember { mutableStateOf(true) }
    var isAdSoundEnabled by remember { mutableStateOf(false) }
    var isYouTubeAdsEnabled by remember { mutableStateOf(true) }
    var isOfflineAdsEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            currentCounterHex = ThemeColorManager.getCounterBackgroundColor(context)
            currentTokenHex = ThemeColorManager.getTokenBackgroundColor(context)
            currentThemeHex = ThemeColorManager.getSelectedThemeColorHex(context)
            notificationSoundKey = ThemeColorManager.getNotificationSoundKey(context)
            customerId = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
                .getInt(PreferenceHelper.customer_id, 0)
            is24Hour = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                    .getBoolean("use_24_hour_format", true)
            isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .getBoolean("enable_ad_sound", false)
            isYouTubeAdsEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .getBoolean("allow_youtube_ads", true)
            isOfflineAdsEnabled = PreferenceHelper.isOfflineAdsEnabled(context)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // Always open Settings with the first tab selected.
    LaunchedEffect(Unit) { selectedTabIndex = 0 }
    val appThemeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 0) {
            // Defer focus request until dialog/tab content is composed.
            delay(100)
            try {
                appThemeFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus tree may not be ready yet.
            }
        }
    }
    var showOfflineConfirmDialog by remember { mutableStateOf(false) }
    // Move "System" tab to the last position as requested.
    val tabs = listOf("Display", "Audios", "Other", "System")

    if (showSoundPicker) {
        NotificationSoundDialog(
            title = "Notification sound",
            selectedKey = notificationSoundKey,
            onSoundSelected = { key ->
                notificationSoundKey = key
                ThemeColorManager.setNotificationSoundKey(context, key)
            },
            onDismiss = { showSoundPicker = false }
        )
    } else if (showThemeColorPicker) {
        PresetThemeColorDialog(
            title = "App Theme",
            onColorSelected = {
                onThemeSelected(it)
                currentThemeHex = it
                showThemeColorPicker = false
            },
            onDismiss = { showThemeColorPicker = false }
        )
    } else if (showCounterColorPicker) {
        PresetColorDialog(
            title = "Counter Background",
            onColorSelected = {
                onCounterBgChange(it)
                currentCounterHex = it
                showCounterColorPicker = false
            },
            onDismiss = { showCounterColorPicker = false }
        )
    } else if (showTokenColorPicker) {
        PresetColorDialog(
            title = "Token Background",
            onColorSelected = {
                onTokenBgChange(it)
                currentTokenHex = it
                showTokenColorPicker = false
            },
            onDismiss = { showTokenColorPicker = false }
        )
    } else if (showOfflineConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineConfirmDialog = false },
            title = { Text("Confirm Switch", style = MaterialTheme.typography.titleSmall) },
            text = {
                Text(
                    "Online streaming requires a reliable high-speed internet connection for smooth playback. Switching from offline mode will stop using locally saved videos. Do you want to proceed with online streaming?",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isOfflineAdsEnabled = false
                        PreferenceHelper.setOfflineAdsEnabled(context, false)
                        showOfflineConfirmDialog = false
                    }
                ) { Text("Proceed") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOfflineConfirmDialog = false
                    }
                ) { Text("Cancel") }
            }
        )
    } else {
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.55f),
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Column {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = settingsPrimary,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = settingsPrimary
                                )
                            }
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                selectedContentColor = settingsPrimary,
                                unselectedContentColor = settingsMutedText,
                                text = { Text(title, fontSize = 16.sp) }
                            )
                        }
                    }
                }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    when (selectedTabIndex) {
                        0 -> { // Display Tab
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                            ) {
                                item {
                                    ColorPickerButton(
                                        label = "App Theme",
                                        hex = currentThemeHex,
                                        onClick = { showThemeColorPicker = true },
                                        modifier = Modifier.focusRequester(appThemeFocusRequester)
                                    )
                                }
                                item {
                                    ColorPickerButton(
                                        label = "Counter BG",
                                        hex = currentCounterHex,
                                        onClick = { showCounterColorPicker = true }
                                    )
                                }
                                item {
                                    ColorPickerButton(
                                        label = "Token BG",
                                        hex = currentTokenHex,
                                        onClick = { showTokenColorPicker = true }
                                    )
                                }
                            }
                        }
                        1 -> { // Audios Tab
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                            ) {
                                val soundOptions = listOf("ding" to "Ding", "soft" to "Soft", "alert" to "Alert", "bell" to "Bell", "ping" to "Ping")
                                val currentSoundLabel = soundOptions.firstOrNull { it.first == notificationSoundKey }?.second ?: notificationSoundKey.replaceFirstChar { it.uppercase() }
                                
                                item {
                                    GridSettingsItem(
                                        title = "Notification Sound",
                                        onClick = { showSoundPicker = true },
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder
                                    ) {
                                        Text(currentSoundLabel, fontSize = 16.sp, color = settingsPrimary)
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "Advertisement Sound",
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = {
                                            isAdSoundEnabled = !isAdSoundEnabled
                                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE).edit().putBoolean("enable_ad_sound", isAdSoundEnabled).apply()
                                            MediaEngine.updateVolume(context)
                                        }
                                    ) {
                                        Checkbox(checked = isAdSoundEnabled, onCheckedChange = { 
                                            isAdSoundEnabled = it
                                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE).edit().putBoolean("enable_ad_sound", it).apply()
                                            MediaEngine.updateVolume(context)
                                        }, modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = settingsPrimary,
                                                uncheckedColor = settingsMutedText,
                                                checkmarkColor = Color.Black
                                            ))
                                    }
                                }
                            }
                        }
                        2 -> { // Other Tab
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                            ) {
                                item {
                                    GridSettingsItem(
                                        title = "24-Hour Format",
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = {
                                            is24Hour = !is24Hour
                                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE).edit().putBoolean("use_24_hour_format", is24Hour).apply()
                                        }
                                    ) {
                                        Checkbox(checked = is24Hour, onCheckedChange = { 
                                            is24Hour = it
                                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE).edit().putBoolean("use_24_hour_format", it).apply()
                                        }, modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = settingsPrimary,
                                                uncheckedColor = settingsMutedText,
                                                checkmarkColor = Color.Black
                                            ))
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "Offline Advertisements",
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = {
                                            if (isOfflineAdsEnabled) showOfflineConfirmDialog = true
                                            else { isOfflineAdsEnabled = true; com.softland.callqtv.utils.PreferenceHelper.setOfflineAdsEnabled(context, true) }
                                        }
                                    ) {
                                        Checkbox(checked = isOfflineAdsEnabled, onCheckedChange = { 
                                            if (!it) showOfflineConfirmDialog = true 
                                            else { isOfflineAdsEnabled = true; com.softland.callqtv.utils.PreferenceHelper.setOfflineAdsEnabled(context, true) }
                                        }, modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = settingsPrimary,
                                                uncheckedColor = settingsMutedText,
                                                checkmarkColor = Color.Black
                                            ))
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "Allow YouTube Ads",
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = {
                                            isYouTubeAdsEnabled = !isYouTubeAdsEnabled
                                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                                .edit()
                                                .putBoolean("allow_youtube_ads", isYouTubeAdsEnabled)
                                                .apply()
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isYouTubeAdsEnabled,
                                            onCheckedChange = {
                                                isYouTubeAdsEnabled = it
                                                context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                                    .edit()
                                                    .putBoolean("allow_youtube_ads", it)
                                                    .apply()
                                            },
                                            modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = settingsPrimary,
                                                uncheckedColor = settingsMutedText,
                                                checkmarkColor = Color.Black
                                            )
                                        )
                                    }
                                }

                                item(span = { GridItemSpan(2) }) {
                                    GridSettingsItem(
                                        title = "Clear saved token history",
                                        titleColor = settingsError,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = { showClearConfirmDialog = true }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Action to reset active token list", fontSize = 15.sp, color = settingsError.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                                            Checkbox(
                                                checked = false, 
                                                onCheckedChange = { if (it) showClearConfirmDialog = true },
                                                modifier = Modifier.scale(0.9f).offset(x = (-6).dp),
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = settingsError,
                                                    uncheckedColor = settingsError
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        3 -> { // System Tab
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                            .padding(1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Image(
                                            painter = painterResource(id = R.drawable.callq_tv_logo),
                                            contentDescription = null,
                                            modifier = Modifier.size(35.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Column {
                                        Text(companyName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("System Information", fontSize = 14.sp, color = settingsPrimary)
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
                                InfoRow("Company ID", String.format("%04d", customerId))
                                InfoRow("Device ID", macAddress)
                                InfoRow("App Version", appVersion)
                                if (daysUntilExpiry != null) {
                                    val expiryText = if (daysUntilExpiry <= 0) "Expired" else "Expires in $daysUntilExpiry days"
                                    val color = if (daysUntilExpiry <= 10) settingsError else settingsText
                                    if (daysUntilExpiry <= 10) 
                                    {
                                        InfoRow("License", expiryText, color)
                                    }
                                }
                                InfoRow("Token Announcement", if (isTokenAnnouncementEnabled == true) "Enabled" else "Disabled")
                                InfoRow("Counter Announcement", if (isCounterAnnouncementEnabled == true) "Enabled" else "Disabled")
                                InfoRow("Show Counter Prefix", if (isCounterPrefixEnabled == true) "Enabled" else "Disabled")
                                
                                Spacer(modifier = Modifier.height(1.dp))
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Developed by", fontSize = 16.sp, color = settingsPrimary, fontWeight = FontWeight.Bold)
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = R.drawable.ic_softland_logo),
                                        contentDescription = "Softland India Ltd",
                                        modifier = Modifier.fillMaxWidth(0.5f).height(35.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear token details?") },
            text = {
                Text("This will clear all saved token details for all counters and fetch the latest configuration from the server. Continue?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearTokenHistoryAndRefresh()
                        showClearConfirmDialog = false
                        onDismiss()
                    }
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(190.dp)
        )
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = valueColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}

@Composable
fun LicenseExpiredDialog(
    macAddress: String,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss */ },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = com.softland.callqtv.R.drawable.ic_network_unavailable),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("License Expired", color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Column {
                Text(
                    errorMessage ?: "Your application license has expired.\nPlease contact support to renew your license and resume operations.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Device ID: $macAddress",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Refresh License")
            }
        }
    )
}

@Composable
fun ColorPickerButton(
    label: String,
    hex: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, Color(0xFF607D8B)),
        modifier = modifier.width(180.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(ThemeColorManager.getBackgroundBrush(hex), RoundedCornerShape(4.dp))
                    .border(2.dp, Color.Gray, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 16.sp, color = Color(0xFFECEFF1))
        }
    }
}

@Composable
fun GridSettingsItem(
    title: String,
    onClick: (() -> Unit)? = null,
    titleColor: Color = Color(0xFF4FC3F7),
    cardColor: Color = Color(0xFF263238),
    borderColor: Color = Color(0xFF607D8B),
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = titleColor)
            content()
        }
    }
}

@Composable
fun PresetColorDialog(
    title: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.98f),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(1.dp), // Minimal spacing
                verticalArrangement = Arrangement.spacedBy(1.dp), // Minimal spacing
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(ThemeColorManager.backgroundOptions) { option ->
                    var focused by remember { mutableStateOf(false) }
                    Surface(
                         onClick = { onColorSelected(option.hexCode) },
                         modifier = Modifier
                             .aspectRatio(1f)
                             .onFocusChanged { focused = it.isFocused },
                         shape = RoundedCornerShape(8.dp),
                         border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray)
                     ) {
                         Box(
                            modifier = Modifier.fillMaxSize().background(ThemeColorManager.getBackgroundBrush(option.hexCode))
                         )
                     }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NotificationSoundDialog(
    title: String,
    selectedKey: String,
    onSoundSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val soundOptions = listOf(
        "ding"      to "Ding",
        "ding2"     to "Ding 2",
        "ding3"     to "Ding 3",
        "ding4"     to "Ding 4",
        "ding5"     to "Ding 5",
        "double"    to "Double beep",
        "double2"   to "Double beep 2",
        "double3"   to "Double beep 3",
        "double4"   to "Double beep 4",
        "soft"      to "Soft beep",
        "soft2"     to "Soft beep 2",
        "soft3"     to "Soft beep 3",
        "soft4"     to "Soft beep 4",
        "alert"     to "Alert",
        "alert2"    to "Alert 2",
        "alert3"    to "Alert 3",
        "alert4"    to "Alert 4",
        "bell"      to "Bell",
        "bell2"     to "Bell 2",
        "bell3"     to "Bell 3",
        "bell4"     to "Bell 4",
        "church1"   to "Church bell 1",
        "church2"   to "Church bell 2",
        "church3"   to "Church bell 3",
        "ping"      to "Ping",
        "ping2"     to "Ping 2",
        "ping3"     to "Ping 3",
        "ping4"     to "Ping 4",
        "long"      to "Long tone",
        "long2"     to "Long tone 2",
        "long3"     to "Long tone 3",
        "long4"     to "Long tone 4",
        "chime1"    to "Chime 1",
        "chime2"    to "Chime 2",
        "chime3"    to "Chime 3",
        "chime4"    to "Chime 4",
        "hi1"       to "High beep 1",
        "hi2"       to "High beep 2",
        "hi3"       to "High beep 3",
        "hi4"       to "High beep 4",
        "low1"      to "Low beep 1",
        "low2"      to "Low beep 2",
        "low3"      to "Low beep 3",
        "low4"      to "Low beep 4",
        "tone1"     to "Tone 1",
        "tone2"     to "Tone 2",
        "tone3"     to "Tone 3",
        "tone4"     to "Tone 4",
        "tone5"     to "Tone 5",
        "tone6"     to "Tone 6",
        "tone7"     to "Tone 7"
    )
    val scope = rememberCoroutineScope()

    AlertDialog(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 340.dp)
            ) {
                items(soundOptions.size) { index ->
                    val (key, label) = soundOptions[index]
                    val isSelected = key == selectedKey
                    OutlinedButton(
                        onClick = {
                            onSoundSelected(key)
                            scope.launch { playTokenChime(key) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = if (isSelected)
                            BorderStroke(5.dp, MaterialTheme.colorScheme.primary)
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PresetThemeColorDialog(
    title: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.98f),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(ThemeColorManager.themeColorOptions) { option ->
                    var focused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { onColorSelected(option.hexCode) },
                        modifier = Modifier
                            .aspectRatio(1f)
                            .onFocusChanged { focused = it.isFocused },
                        shape = RoundedCornerShape(8.dp),
                        border = if (focused)
                            BorderStroke(5.dp, MaterialTheme.colorScheme.primary)
                        else
                            BorderStroke(1.dp, Color.Gray)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ThemeColorManager.getBackgroundBrush(option.hexCode))
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private const val MARQUEE_VELOCITY_DP_PER_SEC = 42f
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollingFooter(
    textLines: List<String>,
    scale: Float,
    isPortrait: Boolean
) {
    val scrollText = remember(textLines) {
        textLines.filter { it.isNotBlank() }.joinToString(separator = "  •  ")
    }
    if (scrollText.isEmpty()) return

    val density = LocalDensity.current
    var travelPx by remember { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()
    // Keep footer typography stable to reduce per-frame glyph/raster churn on TVs.
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = if (isPortrait) 12.sp else 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    val textLayoutResult = remember(scrollText, textStyle, textMeasurer) {
        textMeasurer.measure(
            text = scrollText,
            style = textStyle,
            maxLines = 1
        )
    }
    val footerHeight = with(density) {
        // Match container to measured text height with light breathing room.
        textLayoutResult.size.height.toDp() + 8.dp
    }
    val gapPx = with(density) { 24.dp.toPx() }
    val totalDistance = (textLayoutResult.size.width + gapPx).coerceAtLeast(1f)
    val pxPerSec = with(density) { MARQUEE_VELOCITY_DP_PER_SEC.dp.toPx() }
    LaunchedEffect(totalDistance, pxPerSec) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val deltaSec = (frameNanos - lastFrameNanos) / 1_000_000_000f
                    // Continuous modulo-based motion avoids the restart "jerk".
                    travelPx = (travelPx + (pxPerSec * deltaSec)) % totalDistance
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Keep footer readable even when app theme is very dark.
            .background(Color(0xFF1E4E79))
            .height(footerHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val baselineY = (size.height - textLayoutResult.size.height) / 2f
            var x = -travelPx
            while (x < size.width + totalDistance) {
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(x, baselineY)
                )
                x += totalDistance
            }
            if (x < size.width) {
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(x, baselineY)
                )
            }
        }
    }
}

private fun getTokensForCounter(counter: CounterEntity, tokensPerCounter: Map<String, List<String>>): List<String> {
    val cid = counter.counterId.orEmpty().trim()
    val cname = counter.name.orEmpty().trim()
    val dname = counter.defaultName.orEmpty().trim()
    val btnKey = counter.buttonIndex?.toString()?.trim().orEmpty()

    val rawList = (if (cid.isNotBlank()) tokensPerCounter[cid] else null)
        ?: (if (cname.isNotBlank()) tokensPerCounter[cname] else null)
        ?: (if (dname.isNotBlank()) tokensPerCounter[dname] else null)
        ?: (if (btnKey.isNotBlank()) tokensPerCounter[btnKey] else null)
        ?: tokensPerCounter.entries.find {
            val keyTrimmed = it.key.trim()
            val keyInt = keyTrimmed.toIntOrNull()
            val cidInt = cid.toIntOrNull()
            keyInt != null && (keyInt == cidInt || keyInt == counter.buttonIndex)
        }?.value
        ?: (if (tokensPerCounter.containsKey("__default__")) tokensPerCounter["__default__"] else null)
        ?: emptyList()
        
    return rawList.filter { it.trim() != "0" && !it.contains("CAL", ignoreCase = true) }.map { it.trim() }.distinct()
}
