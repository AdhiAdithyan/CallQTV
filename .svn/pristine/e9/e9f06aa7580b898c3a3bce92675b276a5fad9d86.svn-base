@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
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
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.HelpOutline
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
import androidx.compose.ui.focus.focusProperties
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.ParametersBuilder
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
import android.webkit.PermissionRequest
import java.io.File
import java.io.BufferedInputStream
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Matrix
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import java.net.URLConnection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TokenDisplayActivity : ComponentActivity() {

    private lateinit var viewModel: TokenDisplayViewModel
    private lateinit var mqttViewModel: MqttViewModel
    private var launchInstanceId: Long = 0L

    override fun onStart() {
        super.onStart()
        TokenAnnouncer.initialize(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Belt-and-suspenders guard: if a second instance is created in the same task,
        // close it immediately and keep the existing one.
        if (!isTaskRoot && savedInstanceState == null) {
            Log.w(
                "TokenDisplayLaunch",
                "Ignoring duplicate TokenDisplayActivity instance taskId=$taskId action=${intent?.action.orEmpty()}"
            )
            finish()
            return
        }
        launchInstanceId = nextLaunchInstanceId()
        logLaunchOrigin("onCreate", savedInstanceState != null)
        
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
        android.util.Log.i(
            "TokenDisplayLaunch",
            "onDestroy instance=$launchInstanceId taskId=$taskId isFinishing=$isFinishing isDestroyed=$isDestroyed"
        )
        super.onDestroy()
        MediaEngine.shutdown()
        TokenAnnouncer.shutdown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logLaunchOrigin("onNewIntent", savedInstanceStateAvailable = false)
    }

    private fun logLaunchOrigin(event: String, savedInstanceStateAvailable: Boolean) {
        val i = intent
        val extrasKeys = try {
            i?.extras?.keySet()?.joinToString(",").orEmpty()
        } catch (_: Exception) {
            ""
        }
        val referrerValue = try {
            referrer?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }
        val callerValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                callingPackage.orEmpty()
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
        android.util.Log.i(
            "TokenDisplayLaunch",
            "event=$event instance=$launchInstanceId taskId=$taskId " +
                "flags=0x${Integer.toHexString(i?.flags ?: 0)} action=${i?.action.orEmpty()} " +
                "hasExtras=${(i?.extras != null)} extrasKeys=$extrasKeys " +
                "referrer=$referrerValue caller=$callerValue restored=$savedInstanceStateAvailable"
        )
    }

    companion object {
        private val launchCounter = java.util.concurrent.atomic.AtomicLong(0L)

        private fun nextLaunchInstanceId(): Long = launchCounter.incrementAndGet()
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

private object UiPerfProbe {
    private val lastTokenUiUpdateAtMs = AtomicLong(0L)

    fun markTokenUiUpdated(counterKey: String, tokenLabel: String) {
        val now = System.currentTimeMillis()
        lastTokenUiUpdateAtMs.set(now)
        Log.i("UiPerfProbe", "TOKEN_UI_UPDATED counter=$counterKey token=$tokenLabel at=$now")
    }

    fun logAdEvent(event: String, adPath: String) {
        val now = System.currentTimeMillis()
        val lastTokenAt = lastTokenUiUpdateAtMs.get()
        val delta = if (lastTokenAt > 0L) now - lastTokenAt else -1L
        Log.i(
            "UiPerfProbe",
            "AD_EVENT event=$event deltaFromLastTokenMs=$delta at=$now ad=${adPath.take(120)}"
        )
    }
}

// Play a short built-in chime or a custom audio URL before announcing a token.
suspend fun playTokenChime(
    context: Context,
    soundKey: String,
    tokenAudioUrl: String? = null,
    counterAudioUrl: String? = null
) {
    withContext(Dispatchers.Default) {
        // 1. Play counter-specific audio if provided (highest priority)
        if (!counterAudioUrl.isNullOrBlank()) {
            playMediaUrl(context, counterAudioUrl)
        }

        // 2. Play token-specific chime if provided, otherwise system tone
        if (!tokenAudioUrl.isNullOrBlank()) {
            playMediaUrl(context, tokenAudioUrl)
        } else {
            playSystemTone(soundKey)
        }
    }
}

private suspend fun playMediaUrl(context: Context, url: String) {
    withContext(Dispatchers.IO) {
        val mediaPlayer = android.media.MediaPlayer()
        try {
            mediaPlayer.setDataSource(context, Uri.parse(url))
            mediaPlayer.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            mediaPlayer.prepare()
            mediaPlayer.start()
            val duration = mediaPlayer.duration.toLong()
            delay(duration.coerceAtMost(3000L)) 
        } catch (e: Exception) {
            Log.e("TokenChime", "Error playing chime URL: $url", e)
        } finally {
            mediaPlayer.release()
        }
    }
}

private suspend fun playSystemTone(soundKey: String) {
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
        delay(durationMs + 5L)
    } catch (_: Exception) {
    } finally {
        toneGen.release()
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

    // Warm up ExoPlayer instances/cache on a background dispatcher.
    // This reduces first-ad stutter/ANR during initial player/cache initialization.
    LaunchedEffect(Unit) {
        // Let first frame settle before expensive subsystem warm-up.
        delay(1200)
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
        // Stagger from Exo warm-up to avoid startup contention on UI thread.
        delay(2200)
        WebViewWarmup.warmUp(context)
    }

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
    DisposableEffect(musicUrl) {
        if (musicUrl.isNullOrBlank()) return@DisposableEffect onDispose {}

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
                if (reconnectUiSeconds >= 30) {
                    // Hard cap retry window at 30s; trigger fresh retry immediately.
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
    val announcementMutex = remember { Mutex() }

    LaunchedEffect(Unit) {
        mqttViewModel.tokenUpdateChannel.receiveAsFlow().collect { pair ->
            val currentConfig = latestConfigState.value
            val currentCounters = latestCountersState.value

            launch(Dispatchers.Default) {
                try {
                    val (counterIdOrName, tokenLabel) = pair

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
                        return@launch
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
                        return@launch
                    }

                    // Deduplicate: skip chime/TTS if we already announced this token call recently
                    if (mqttViewModel.isAlreadyAnnounced(storageKey, tokenLabel)) {
                        return@launch
                    }

                    // Mark before playing any sound so duplicate messages don't re-play
                    mqttViewModel.markAsAnnounced(storageKey, tokenLabel)
                    if (btnKey != null) mqttViewModel.markAsAnnounced(btnKey, tokenLabel)

                    // Token list update is independent from ad/player pipeline.
                    val isNewOrMoved =
                        mqttViewModel.processTokenUpdateForKeys(storageKey, tokenLabel, btnKey)
                    if (!isNewOrMoved) return@launch

                    withContext(Dispatchers.Main.immediate) {
                        // Signal re-call by updating timestamp (blink restart) at update time.
                        blinkTriggers[storageKey] = System.currentTimeMillis()
                    }
                    UiPerfProbe.markTokenUiUpdated(storageKey, tokenLabel)

                    // Keep audio announcements sequential, but do not block token population.
                    announcementMutex.withLock {
                        delay(50)

                        val soundKey = ThemeColorManager.getNotificationSoundKey(context)
                        withContext(Dispatchers.Main.immediate) {
                            playTokenChime(
                                context = context,
                                soundKey = soundKey,
                                tokenAudioUrl = currentConfig?.tokenAudioUrl,
                                counterAudioUrl = actualCounter.audioUrl
                            )
                        }

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

        if (config != null) {
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
                    onThemeChange = onThemeChange,
                    onCounterBgChange = onCounterBgChange,
                    onTokenBgChange = onTokenBgChange,
                    onRefresh = { viewModel.loadData(mqttViewModel, forceShowOverlay = true) },
                    blinkTriggers = blinkTriggers,
                    showReconnectBadge = !stableBrokerConnected && !showMqttRetryDialog,
                    reconnectRetryAttempt = reconnectDisplayTry,
                    reconnectUiSeconds = reconnectUiSeconds,
                    pendingCallCount = pendingCallCount
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
                        "The display could not connect to the messaging server (Attempt $effectiveRetryAttempt).\n" +
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
                        reconnectDisplayTry = maxOf(reconnectDisplayTry + 1, mqttRetryAttempt.coerceAtLeast(1) + 1)
                        mqttViewModel.retryConnect(resetAttempts = false)
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
        modifier = modifier,
        visible = pendingCallCount > 0,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        Surface(
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
        modifier = modifier,
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        val retryCount = retryAttempt.coerceAtLeast(1)
        Surface(
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
    adAreaReloadToken: Int,
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
    blinkTriggers: Map<String, Long>,
    showReconnectBadge: Boolean,
    reconnectRetryAttempt: Int,
    reconnectUiSeconds: Int,
    pendingCallCount: Int
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
    val hasScrollingFooter = remember(config.scrollEnabled, config.noOfTextFields, config.scrollTextLinesJson) {
        config.scrollEnabled?.equals("on", ignoreCase = true) == true &&
            (config.noOfTextFields ?: 0) > 0 &&
            !config.scrollTextLinesJson.isNullOrBlank()
    }

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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TokenDisplayBody(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    config = config,
                    adAreaReloadToken = adAreaReloadToken,
                    adFiles = adFiles,
                    countersToDisplay = countersToDisplay,
                    tokensPerCounter = tokensPerCounter,
                    rows = rows,
                    columns = columns,
                    scale = scale,
                    counterBgHex = counterBgHex,
                    tokenBgHex = tokenBgHex,
                    usePortraitLayout = usePortraitLayout,
                    adPlacement = adPlacement,
                    blinkTriggers = blinkTriggers,
                    showReconnectBadge = false,
                    reconnectRetryAttempt = reconnectRetryAttempt,
                    reconnectUiSeconds = reconnectUiSeconds,
                )

                TokenDisplayFooter(
                    config = config,
                    responsivePadding = responsivePadding,
                    scale = scale,
                    deviceIsPortrait = deviceIsPortrait
                )
            }

            ReconnectStatusBadge(
                visible = showReconnectBadge,
                retryAttempt = reconnectRetryAttempt,
                reconnectUiSeconds = reconnectUiSeconds,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = if (hasScrollingFooter) 0.dp else 8.dp)
            )

            PendingCallsBadge(
                pendingCallCount = pendingCallCount,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = if (hasScrollingFooter) 0.dp else 8.dp)
            )
        }
    }
}

@Composable
private fun TokenDisplayBody(
    modifier: Modifier,
    config: TvConfigEntity,
    adAreaReloadToken: Int,
    adFiles: List<AdFileEntity>,
    countersToDisplay: List<CounterEntity>,
    tokensPerCounter: Map<String, List<String>>,
    rows: Int,
    columns: Int,
    scale: Float,
    counterBgHex: String,
    tokenBgHex: String,
    usePortraitLayout: Boolean,
    adPlacement: String,
    blinkTriggers: Map<String, Long>,
    showReconnectBadge: Boolean,
    reconnectRetryAttempt: Int,
    reconnectUiSeconds: Int
) {
    val showAds = config.showAds?.equals("on", ignoreCase = true) == true
    val hasAds = showAds && adFiles.isNotEmpty()
    val baseLayoutType = config.layoutType ?: "1"
    val layoutType = if (usePortraitLayout) "2" else baseLayoutType
    val counterCount = countersToDisplay.size
    val configuredCounterLimit = config.noOfCounters ?: counterCount
    val adWeight = remember(showAds, configuredCounterLimit) {
        if (!showAds) 0f else if (configuredCounterLimit <= 2) 0.5f else 0.4f
    }
    val countersWeight = remember(adWeight) { if (adWeight > 0f) 1f - adWeight else 1f }
    val adAreaContent: @Composable () -> Unit = {
        key(adAreaReloadToken) {
            AdArea(adFiles, config)
        }
    }

    Box(modifier = modifier) {
        if (usePortraitLayout) {
            if (hasAds) {
                val isTop = adPlacement.equals("top", ignoreCase = true)
                val isBottom = adPlacement.equals("bottom", ignoreCase = true)
                val isRight = adPlacement.equals("right", ignoreCase = true)
                
                if (isTop || isBottom) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isTop) {
                            Box(modifier = Modifier.weight(adWeight).fillMaxWidth().clipToBounds()) { adAreaContent() }
                            Box(modifier = Modifier.weight(countersWeight).fillMaxWidth()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                            }
                        } else {
                            Box(modifier = Modifier.weight(countersWeight).fillMaxWidth()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                            }
                            Box(modifier = Modifier.weight(adWeight).fillMaxWidth().clipToBounds()) { adAreaContent() }
                        }
                    }
                } else {
                    // Default Left/Right (Row) for Portrait if not specified as Top/Bottom
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isRight) {
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                            }
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                        } else {
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                            }
                        }
                    }
                }
            } else {
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
            if (hasAds) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (adPlacement.equals("left", ignoreCase = true)) {
                        Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                        Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                            CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                        }
                    } else {
                        Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                            CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
                        }
                        Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                    }
                }
            } else {
                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers)
            }
        }

    }
}

@Composable
private fun TokenDisplayFooter(
    config: TvConfigEntity,
    responsivePadding: androidx.compose.ui.unit.Dp,
    scale: Float,
    deviceIsPortrait: Boolean
) {
    val scrollEnabled = config.scrollEnabled?.equals("on", ignoreCase = true) == true
    val noOfTextFields = config.noOfTextFields ?: 0
    val scrollTextLinesJson = config.scrollTextLinesJson

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
                } catch (_: Exception) {
                    emptyList()
                }
            }
        } else emptyList()
    }

    if (scrollEnabled && noOfTextFields > 0 && scrollLines.isNotEmpty()) {
        ScrollingFooter(
            textLines = scrollLines,
            scale = scale,
            isPortrait = deviceIsPortrait
        )
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

    // visibleAd stays on-screen until next ad is actually ready.
    var visibleAd by remember(orderedAds) { mutableStateOf(orderedAds.getOrNull(0)) }
    // Strict round-robin cursor for predictable 1->2->3->1 ordering.
    var visibleAdIdx by remember(orderedAds) { mutableIntStateOf(0) }
    // candidateAd is preloaded in background; we switch only after its onReady callback.
    var candidateAd by remember(orderedAds) { mutableStateOf<AdFileEntity?>(null) }
    var candidateAdIdx by remember(orderedAds) { mutableIntStateOf(-1) }
    var candidateLoadToken by remember(orderedAds) { mutableIntStateOf(0) }
    var visibleReplayToken by remember(orderedAds) { mutableIntStateOf(0) }
    val adSwitchScope = rememberCoroutineScope()
    val transitionHoldMs = 180L
    var visibleAdReady by remember(orderedAds) { mutableStateOf(false) }
    val visibleReadyTimeoutMs = 20_000L
    // Keep one stable player instance active; alternating players without true preloading
    // can add handoff delay on constrained TV devices.
    val activePlayerIdx = 0
    val mediaTypeCache = remember { mutableStateMapOf<String, AdMediaType>() }
    val sharedYoutubeWebView = remember(context) {
        buildBaseYoutubeWebView(context)
    }
    val sharedAdTextureView = remember(context) {
        buildSharedAdTextureView(context)
    }

    DisposableEffect(sharedYoutubeWebView) {
        onDispose {
            releaseYouTubeWebView(sharedYoutubeWebView, destroy = true)
        }
    }

    LaunchedEffect(orderedAds) {
        if (orderedAds.isEmpty()) {
            visibleAd = null
            visibleAdIdx = 0
            candidateAd = null
            candidateAdIdx = -1
        } else {
            // Keep current visible ad stable while DB rows refresh (e.g., remote -> local path swap).
            // Restarting the same slot mid-play causes repeated codec/surface churn.
            val current = visibleAd
            if (current != null) {
                val sameAdIdx = orderedAds.indexOfFirst {
                    // Prefer stable identity; falling back to slot-only can pin loop to first ad
                    // when multiple ads share the same position.
                    val idMatch = current.id == it.id && current.id != 0L
                    val pathMatch = current.filePath.equals(it.filePath, ignoreCase = true)
                    val positionMatch = current.position == it.position
                    idMatch || (pathMatch && positionMatch) || pathMatch
                }
                if (sameAdIdx >= 0) {
                    visibleAdIdx = sameAdIdx
                    visibleAd = orderedAds[sameAdIdx]
                } else {
                    visibleAdIdx = visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
                    visibleAd = orderedAds[visibleAdIdx]
                }
            } else {
                visibleAdIdx = visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
                visibleAd = orderedAds[visibleAdIdx]
            }
            candidateAd = null
            candidateAdIdx = -1
        }
    }

    // Resolve media types off-main and cache them so composition remains lightweight.
    LaunchedEffect(orderedAds) {
        orderedAds.forEach { ad ->
            val key = ad.filePath
            if (mediaTypeCache[key] == null) {
                val resolved = withContext(Dispatchers.Default) {
                    resolveAdMediaType(key, context)
                }
                mediaTypeCache[key] = resolved
            }
        }
    }

    fun isWmvUnsupportedVideo(path: String): Boolean {
        val lc = path.lowercase()
        // ExoPlayer may not have a decoder/extractor for WMV on all TV SoCs.
        // Your log shows: "Unsupported mime video/x-ms-wmv".
        return lc.contains(".wmv") || lc.contains("x-ms-wmv") || lc.endsWith("/wmv")
    }

    fun fastInferMediaType(path: String): AdMediaType {
        val lc = path.lowercase()
        return when {
            isValidYoutubeVideoIdForEmbed(path.trim()) ||
                lc.contains("youtube.com") ||
                lc.contains("youtu.be") ||
                lc.contains("youtube-nocookie.com") -> AdMediaType.YouTube
            isAdVideo(path) -> AdMediaType.Video
            isAdImage(path) -> AdMediaType.Image
            lc.startsWith("http://") || lc.startsWith("https://") -> AdMediaType.Web
            else -> AdMediaType.Image
        }
    }

    fun isUhdPortraitU25UnsupportedVideo(path: String): Boolean {
        val lc = path.lowercase()
        return lc.contains("uhd_2160_3840") ||
            lc.contains("uhd_2160x3840") ||
            (lc.contains("2160x3840") || lc.contains("2160_3840")) && lc.endsWith(".mp4")
    }

    fun triggerNext(expectedCurrentAd: AdFileEntity? = null) {
        if (orderedAds.isEmpty()) return
        if (expectedCurrentAd != null) {
            // Ignore stale callbacks (old ad ended after a new ad is already visible).
            val stillSame = visibleAd?.id == expectedCurrentAd.id &&
                visibleAd?.position == expectedCurrentAd.position
            if (!stillSame) return
        }

        val currentIdx = visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
        val nextIdx = (currentIdx + 1) % orderedAds.size
        val next = orderedAds[nextIdx]
        Log.i(
            "AdLoop",
            "triggerNext current[${currentIdx.coerceAtLeast(0)}]=${visibleAd?.filePath?.take(90)} -> next[$nextIdx]=${next.filePath.take(90)}"
        )
        // Preload the next ad first, keep current ad on screen meanwhile.
        candidateLoadToken += 1
        candidateAd = next
        candidateAdIdx = nextIdx
    }

    fun triggerNextFrom(ad: AdFileEntity, expectedCurrentAd: AdFileEntity? = null) {
        if (orderedAds.isEmpty()) return
        if (expectedCurrentAd != null) {
            val stillSame = visibleAd?.id == expectedCurrentAd.id &&
                visibleAd?.position == expectedCurrentAd.position
            if (!stillSame) return
        }
        val currentIdx = orderedAds.indexOfFirst { it.id == ad.id && it.position == ad.position }
            .takeIf { it >= 0 } ?: visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
        val nextIdx = (currentIdx + 1) % orderedAds.size
        candidateLoadToken += 1
        candidateAd = orderedAds[nextIdx]
        candidateAdIdx = nextIdx
    }

    fun promoteCandidateAfterHold(preloadTokenSnapshot: Int, nextAd: AdFileEntity, nextIdx: Int) {
        adSwitchScope.launch {
            delay(transitionHoldMs)
            if (preloadTokenSnapshot != candidateLoadToken) return@launch
            visibleAd = nextAd
            visibleAdIdx = nextIdx.coerceAtLeast(0)
            candidateAd = null
            candidateAdIdx = -1
            visibleReplayToken = 0
        }
    }

    // Ensure non-ended media types always advance by configured interval so the full ad list
    // loops continuously (videos/YouTube already advance via ended/error callbacks).
    // For remote URLs with ambiguous extensions, wait for resolved media type before applying
    // interval-based switching so video ads are not prematurely cut as "web".
    LaunchedEffect(visibleAd?.id, visibleAd?.position, orderedAds.size, intervalSeconds) {
        val current = visibleAd ?: return@LaunchedEffect
        if (orderedAds.isEmpty()) return@LaunchedEffect
        val cachedType = mediaTypeCache[current.filePath]
        val currentType = cachedType ?: fastInferMediaType(current.filePath)
        val hasAmbiguousRemotePath = cachedType == null &&
            current.filePath.startsWith("http", ignoreCase = true) &&
            !isAdVideo(current.filePath) &&
            !isAdImage(current.filePath) &&
            !current.filePath.contains("youtube.com", ignoreCase = true) &&
            !current.filePath.contains("youtu.be", ignoreCase = true) &&
            !isValidYoutubeVideoIdForEmbed(current.filePath.trim())
        if (hasAmbiguousRemotePath) return@LaunchedEffect
        if (currentType == AdMediaType.Video || currentType == AdMediaType.YouTube) return@LaunchedEffect

        delay(intervalSeconds * 1000L)
        triggerNext(expectedCurrentAd = current)
    }

    // Black-screen watchdog: if a visible ad never becomes ready, skip it quickly.
    LaunchedEffect(visibleAd?.id, visibleAd?.position) {
        visibleAdReady = false
    }
    LaunchedEffect(visibleAd?.id, visibleAd?.position, visibleAdReady) {
        val current = visibleAd ?: return@LaunchedEffect
        if (visibleAdReady) return@LaunchedEffect
        delay(visibleReadyTimeoutMs)
        val stillSame = visibleAd?.id == current.id && visibleAd?.position == current.position
        if (stillSame && !visibleAdReady) {
            Log.w(
                "AdLoop",
                "Visible ad not ready within ${visibleReadyTimeoutMs / 1000}s; skipping ${current.filePath.take(120)}"
            )
            triggerNext(expectedCurrentAd = current)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .focusProperties { canFocus = false }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (orderedAds.isEmpty()) {
            Text("No Ads", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Main display: keep currently visible ad until the next candidate is ready.
            Crossfade(
                targetState = Pair(visibleAd, visibleReplayToken),
                animationSpec = tween(0),
                modifier = Modifier.fillMaxSize().clipToBounds(),
                label = "ad_fade"
            ) { state ->
                val ad = state.first
                if (ad != null) {
                    val mediaType = mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
                    Log.i(
                        "AdClassifier",
                        "VISIBLE type=$mediaType url=${ad.filePath.take(140)}"
                    )
                    AdUnifiedPlayer(
                        ad = ad,
                        mediaType = mediaType,
                        allowYoutubeAds = allowYoutubeAds,
                        sharedYoutubeWebView = sharedYoutubeWebView,
                        sharedVideoTextureView = sharedAdTextureView,
                        activePlayerIdx = activePlayerIdx,
                        isWmvUnsupportedVideo = ::isWmvUnsupportedVideo,
                        onReady = {
                            visibleAdReady = true
                            UiPerfProbe.logAdEvent("READY", ad.filePath)
                        },
                        onEnded = {
                            UiPerfProbe.logAdEvent("ENDED", ad.filePath)
                            triggerNext(expectedCurrentAd = ad)
                            // Only replay when there is a single ad configured.
                            // For multiple ads, enforce round-robin without repeating same ad.
                            if (orderedAds.size <= 1) {
                                visibleReplayToken += 1
                            }
                        },
                        onError = {
                            UiPerfProbe.logAdEvent("ERROR", ad.filePath)
                            triggerNext(expectedCurrentAd = ad)
                        }
                    )
                }
            }

            // Hidden preloader: keep this image-only.
            // Offscreen WebView/Player surfaces can exhaust RTS IDs on some IMG GPUs.
            val preloadAd = candidateAd
            if (preloadAd != null && (visibleAd == null || preloadAd.filePath != visibleAd?.filePath || preloadAd.id != visibleAd?.id)) {
                val preloadTokenSnapshot = candidateLoadToken
                val preloadType = mediaTypeCache[preloadAd.filePath] ?: fastInferMediaType(preloadAd.filePath)
                Log.i(
                    "AdClassifier",
                    "PRELOAD type=$preloadType url=${preloadAd.filePath.take(140)}"
                )
                if (preloadType == AdMediaType.Image) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.01f)
                            .clipToBounds()
                    ) {
                        AdUnifiedPlayer(
                            ad = preloadAd,
                            mediaType = preloadType,
                            allowYoutubeAds = allowYoutubeAds,
                            sharedYoutubeWebView = sharedYoutubeWebView,
                            sharedVideoTextureView = sharedAdTextureView,
                            activePlayerIdx = activePlayerIdx,
                            isWmvUnsupportedVideo = ::isWmvUnsupportedVideo,
                            onReady = {
                                if (preloadTokenSnapshot == candidateLoadToken) {
                                    UiPerfProbe.logAdEvent("PRELOAD_READY", preloadAd.filePath)
                                    promoteCandidateAfterHold(
                                        preloadTokenSnapshot = preloadTokenSnapshot,
                                        nextAd = preloadAd,
                                        nextIdx = candidateAdIdx
                                    )
                                }
                            },
                            onEnded = { },
                            onError = {
                                if (preloadTokenSnapshot == candidateLoadToken) {
                                    UiPerfProbe.logAdEvent("PRELOAD_ERROR", preloadAd.filePath)
                                    triggerNextFrom(preloadAd, expectedCurrentAd = visibleAd)
                                }
                            }
                        )
                    }
                } else {
                    // Single-surface strategy for YouTube/video: avoid offscreen playback surfaces.
                    if (preloadTokenSnapshot == candidateLoadToken) {
                        promoteCandidateAfterHold(
                            preloadTokenSnapshot = preloadTokenSnapshot,
                            nextAd = preloadAd,
                            nextIdx = candidateAdIdx
                        )
                    }
                }
            }
            
            // Candidate preloader keeps current ad visible and avoids black-frame switches.
        }
    }
}

// YouTube components replaced by direct WebView implementation per user request to use exact links.

private fun isAdVideo(path: String): Boolean {
    val lowerFull = path.lowercase()
    val lc = lowerFull.substringBefore("?").substringBefore("#")
    return lc.endsWith(".mp4") || lc.endsWith(".mkv") || lc.endsWith(".mov") ||
           lc.endsWith(".3gp") || lc.endsWith(".webm") || lc.endsWith(".avi") ||
           lc.endsWith(".flv") || lc.endsWith(".ts") || lc.endsWith(".m4v") ||
           lc.endsWith(".mpg") || lc.endsWith(".mpeg") || lc.endsWith(".m2ts") ||
           lc.endsWith(".m3u8") || lc.endsWith(".mpd") || lc.endsWith(".ism") ||
           lc.endsWith(".isml") || lowerFull.contains(".m3u8") || lowerFull.contains(".mpd")
}

private fun isAdImage(path: String): Boolean {
    val lc = path.lowercase().substringBefore("?").substringBefore("#")
    return lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") ||
        lc.endsWith(".gif") || lc.endsWith(".webp") || lc.endsWith(".bmp") ||
        lc.endsWith(".svg")
}

enum class AdMediaType { YouTube, Video, Image, Web }

private const val PREF_YOUTUBE_AD_MAX_SECONDS = "youtube_ad_max_seconds"
private const val PREF_YOUTUBE_STRICT_AUTOPLAY = "youtube_strict_autoplay"
private const val PREF_YOUTUBE_PLAY_UNTIL_ENDED = "youtube_play_until_ended"
private const val DEFAULT_YOUTUBE_AD_MAX_SECONDS = 120
private const val MIN_TRUSTED_YOUTUBE_DURATION_MS = 5_000L
private val YOUTUBE_AD_MAX_OPTIONS_SECONDS = listOf(30, 60, 90, 120)

private fun resolveAdMediaType(path: String, context: Context): AdMediaType {
    val lc = path.lowercase()
    val res = when {
        isValidYoutubeVideoIdForEmbed(path.trim()) -> AdMediaType.YouTube
        lc.contains("youtube.com") || lc.contains("youtu.be") || lc.contains("youtube-nocookie.com") -> AdMediaType.YouTube
        isAdVideo(path) -> AdMediaType.Video
        isAdImage(path) -> AdMediaType.Image
        lc.startsWith("http://") || lc.startsWith("https://") -> AdMediaType.Web
        else -> {
            val mime = detectMimeType(path, context)
            when {
                mime?.startsWith("video/") == true -> AdMediaType.Video
                mime?.startsWith("image/") == true -> AdMediaType.Image
                lc.startsWith("http://") || lc.startsWith("https://") -> AdMediaType.Web
                else -> AdMediaType.Image
            }
        }
    }
    // Log for debugging (only to logcat, not FileLogger yet to avoid spam)
    Log.d("AdMediaType", "Resolved '$path' to $res")
    return res
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





@Composable
fun YouTubeAdPlayer(
    url: String,
    sharedWebView: WebView? = null,
    onVideoEnded: () -> Unit,
    onReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)

    val originalUrl = remember(url) { url.trim() }
    // Prefer original URL first. Some videos block embedded playback (Error 152/153)
    // but still work as regular watch/shorts pages inside WebView.
    // Normalize short/share links (e.g., youtu.be/...) to watch URLs for better TV WebView compatibility.
    val initialPlaybackUrl = remember(originalUrl) { normalizeYouTubePlaybackUrl(originalUrl) }
    val expectedVideoId = remember(originalUrl) { extractYoutubeIdForAnyUrl(originalUrl) }
    val expectedWatchUrl = remember(expectedVideoId) {
        expectedVideoId?.let { "https://www.youtube.com/watch?v=$it" }
    }
    var activeUrl by remember(initialPlaybackUrl) { mutableStateOf(initialPlaybackUrl) }
    var currentLoadToken by remember { mutableIntStateOf(0) }

    var readyReported by remember(activeUrl) { mutableStateOf(false) }
    var terminalEventReported by remember(activeUrl) { mutableStateOf(false) }
    var loadStartedAtMs by remember(activeUrl) { mutableStateOf(0L) }
    var reportedDurationMs by remember(activeUrl) { mutableStateOf<Long?>(null) }
    var dnsFallbackTried by remember(activeUrl) { mutableStateOf(false) }
    var embedFallbackTried by remember(activeUrl) { mutableStateOf(false) }
    var embedRestrictionFallbackTried by remember(activeUrl) { mutableStateOf(false) }
    var iframeFallbackTried by remember(originalUrl) { mutableStateOf(false) }
    var mismatchedVideoRetried by remember(activeUrl) { mutableStateOf(false) }
    val hardMaxPlaybackMs = remember(activeUrl) {
        // Shorts/embedded pages can sometimes miss ended callbacks on TV WebView.
        // Keep a configurable hard cap so ad rotation never gets stuck.
        val prefs = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val stored = prefs.getInt(PREF_YOUTUBE_AD_MAX_SECONDS, DEFAULT_YOUTUBE_AD_MAX_SECONDS)
        val safeSeconds = if (stored in YOUTUBE_AD_MAX_OPTIONS_SECONDS) {
            stored
        } else {
            DEFAULT_YOUTUBE_AD_MAX_SECONDS
        }
        safeSeconds * 1000L
    }
    val playUntilEnded = remember(activeUrl) {
        context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_YOUTUBE_PLAY_UNTIL_ENDED, true)
    }
    val isLowBandwidth = remember(activeUrl) { isAdLowBandwidthNetwork(context) }

    // Safety timeout: if ad doesn't report ready/ended within 45s, skip it.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(activeUrl) {
        loadStartedAtMs = System.currentTimeMillis()
        Log.i("YouTubeAdPerf", "Start load for url=${activeUrl.take(120)}")
    }
    LaunchedEffect(initialPlaybackUrl) {
        if (!initialPlaybackUrl.equals(originalUrl, ignoreCase = true)) {
            Log.i(
                "YouTubeAdPerf",
                "Normalized YouTube URL from=${originalUrl.take(120)} to=${initialPlaybackUrl.take(120)}"
            )
        }
    }

    // Early fallback: embed URLs can be flaky too, so do not force watch/shorts pages
    // into embed mode. Keep watch playback path stable on TVs where embeds are restricted.
    LaunchedEffect(activeUrl, readyReported, terminalEventReported) {
        if (readyReported || terminalEventReported || embedFallbackTried) return@LaunchedEffect
        val looksLikeEmbed = activeUrl.contains("/embed/", ignoreCase = true)
        if (!looksLikeEmbed) return@LaunchedEffect
        delay(12000)
        if (readyReported || terminalEventReported || embedFallbackTried) return@LaunchedEffect
        val embedUrl = nextYouTubeEmbedFallbackUrl(activeUrl)
        if (!embedUrl.isNullOrBlank() && !embedUrl.equals(activeUrl, ignoreCase = true)) {
            embedFallbackTried = true
            Log.w(
                "YouTubeAdPerf",
                "Early fallback to embed URL after slow ready: ${embedUrl.take(120)}"
            )
            activeUrl = embedUrl
            readyReported = false
            terminalEventReported = false
            reportedDurationMs = null
            loadStartedAtMs = System.currentTimeMillis()
        }
    }

    // If YouTube page never becomes visible, skip.
    // Use a more tolerant timeout for low-network scenarios.
    LaunchedEffect(activeUrl, readyReported, terminalEventReported) {
        if (readyReported || terminalEventReported) return@LaunchedEffect
        val readyTimeoutMs = if (isLowBandwidth) 90_000L else 70_000L
        delay(readyTimeoutMs)
        // Extra grace window to avoid boundary races where ready lands
        // right around timeout expiry.
        if (!readyReported && !terminalEventReported) {
            delay(1500)
        }
        if (!readyReported && !terminalEventReported) {
            Log.w("YouTubeAdPlayer", "Ready timeout (${readyTimeoutMs / 1000}s) for YouTube ad: $activeUrl")
            withContext(Dispatchers.IO) {
                FileLogger.logError(
                    context,
                    "YouTubeAdPlayer",
                    "Ready timeout (${readyTimeoutMs / 1000}s) for: $activeUrl"
                )
            }
            Log.w(
                "YouTubeAdPerf",
                "Ready timeout after ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${activeUrl.take(120)}"
            )
            terminalEventReported = true
            latestOnError()
        }
    }

    // Duration-based safety fallback:
    // For direct YouTube pages, JS-reported duration can be unreliable (often 10s previews),
    // so use only the configured hard cap to avoid premature ad switching.
    LaunchedEffect(activeUrl, readyReported, terminalEventReported, reportedDurationMs) {
        if (!readyReported || terminalEventReported) return@LaunchedEffect
        val fallbackDelayMs = if (playUntilEnded) {
            // Prefer natural "ended" event; keep a long hard-stop to avoid infinite hangs.
            hardMaxPlaybackMs.coerceAtLeast(10 * 60 * 1000L)
        } else {
            hardMaxPlaybackMs
        }
        delay(fallbackDelayMs)
        if (!terminalEventReported) {
            terminalEventReported = true
            Log.i(
                "YouTubeAdPerf",
                "Duration safety auto-advance after ${fallbackDelayMs} ms for url=${activeUrl.take(120)}"
            )
            latestOnVideoEnded()
        }
    }

    val webView = sharedWebView ?: remember(context) { buildBaseYoutubeWebView(context) }

    DisposableEffect(webView, sharedWebView) {
        onDispose {
            if (sharedWebView == null) {
                releaseYouTubeWebView(webView, destroy = true)
            } else {
                // Keep shared WebView alive between ad transitions to avoid recreate churn
                // and avoid transient black frames from forcing about:blank.
                try {
                    webView.onPause()
                    webView.pauseTimers()
                    webView.stopLoading()
                } catch (_: Exception) {
                }
                // Shared WebView may be reattached by a new AndroidView host.
                // Detach from any old parent to prevent "specified child already has a parent".
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            }
        }
    }

    AndroidView(
        factory = {
            // Defensive detach for shared instance reuse across Compose hosts.
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView
        },
        update = { view ->
            // Shared WebView can be paused during previous ad disposal.
            // Resume before loading next YouTube ad; otherwise page JS/timers may never run.
            try {
                view.onResume()
                view.resumeTimers()
            } catch (_: Exception) {
            }
            try {
                view.removeJavascriptInterface("CallQTVBridge")
            } catch (_: Exception) {
            }
            val jsBridge = object {
                @JavascriptInterface
                fun onAdEnded() {
                    mainHandler.post {
                        if (!terminalEventReported) {
                            terminalEventReported = true
                            latestOnVideoEnded()
                        }
                    }
                }

                @JavascriptInterface
                fun onAdReady() {
                    mainHandler.post {
                        if (!readyReported) {
                            readyReported = true
                            latestOnReady()
                        }
                    }
                }

                @JavascriptInterface
                fun onAdDuration(seconds: Double) {
                    if (!seconds.isFinite() || seconds <= 0.0) return
                    mainHandler.post {
                        val ms = (seconds * 1000.0).toLong()
                        // Shorts/watch pages can report transient preview duration (~10s).
                        // Ignore very short values so they do not influence ad timing decisions.
                        if (ms < MIN_TRUSTED_YOUTUBE_DURATION_MS) {
                            Log.i(
                                "YouTubeAdPerf",
                                "Ignoring short YouTube duration=${ms}ms for url=${activeUrl.take(120)}"
                            )
                            return@post
                        }
                        if (reportedDurationMs == null || kotlin.math.abs((reportedDurationMs ?: 0L) - ms) > 1000L) {
                            reportedDurationMs = ms
                            Log.i("YouTubeAdPerf", "Detected YouTube duration=${ms}ms for url=${activeUrl.take(120)}")
                        }
                    }
                }
            }
            view.addJavascriptInterface(jsBridge, "CallQTVBridge")

            fun handleYouTubeRestrictionToken(webView: WebView, token: String) {
                val normalized = token.lowercase()
                val hasEmbedConfigError = normalized == "152" || normalized == "153" || normalized == "config"
                if (!hasEmbedConfigError) return
                val watchFallbackUrl = nextYouTubeWatchFallbackUrl(activeUrl)
                if (!embedRestrictionFallbackTried &&
                    !watchFallbackUrl.isNullOrBlank() &&
                    !watchFallbackUrl.equals(activeUrl, ignoreCase = true)
                ) {
                    embedRestrictionFallbackTried = true
                    Log.w(
                        "YouTubeAdPerf",
                        "Embed restriction detected; retrying with watch URL: ${watchFallbackUrl.take(120)}"
                    )
                    activeUrl = watchFallbackUrl
                    readyReported = false
                    terminalEventReported = false
                    reportedDurationMs = null
                    loadStartedAtMs = System.currentTimeMillis()
                    webView.tag = null
                    loadYouTubeUrlWithHeaders(webView, watchFallbackUrl)
                    return
                }
                // Avoid repeated iframe/loadData retries on restricted videos.
                // On IMG GPUs this can trigger heavy surface churn and RTS exhaustion.
                if (!iframeFallbackTried && activeUrl.contains("/embed/", ignoreCase = true)) {
                    iframeFallbackTried = true
                    Log.w(
                        "YouTubeAdPerf",
                        "Embed restriction persists on url=${activeUrl.take(120)}; skipping without iframe fallback"
                    )
                }
                if (!terminalEventReported) {
                    terminalEventReported = true
                    latestOnError()
                }
            }

            view.webViewClient = object : WebViewClient() {
                private val readyPosted = AtomicBoolean(false)
                private var mainFrameFailed = false

                private fun reportReadyOnce(urlHint: String?) {
                    if (!readyPosted.compareAndSet(false, true)) return
                    if (mainFrameFailed) return
                    val finalUrl = (urlHint ?: activeUrl)
                    val u = finalUrl.lowercase()
                    if (u.startsWith("chrome-error://")) return
                    val loadedVideoId = extractYoutubeIdForAnyUrl(finalUrl)
                    if (!mismatchedVideoRetried &&
                        !expectedVideoId.isNullOrBlank() &&
                        !loadedVideoId.isNullOrBlank() &&
                        !loadedVideoId.equals(expectedVideoId, ignoreCase = true)
                    ) {
                        val canonicalTarget = expectedWatchUrl
                        if (!canonicalTarget.isNullOrBlank()) {
                            mismatchedVideoRetried = true
                            Log.w(
                                "YouTubeAdPerf",
                                "Detected mismatched YouTube video id loaded=$loadedVideoId expected=$expectedVideoId; retrying $canonicalTarget"
                            )
                            activeUrl = canonicalTarget
                            readyReported = false
                            terminalEventReported = false
                            reportedDurationMs = null
                            loadStartedAtMs = System.currentTimeMillis()
                            return
                        }
                    }
                    if (!readyReported) {
                        readyReported = true
                        Log.i(
                            "YouTubeAdPerf",
                            "Ready in ${System.currentTimeMillis() - loadStartedAtMs} ms, url=${(urlHint ?: activeUrl).take(120)}"
                        )
                        latestOnReady()
                    }
                }

                override fun onPageStarted(view: WebView?, urlStr: String?, favicon: android.graphics.Bitmap?) {
                    mainFrameFailed = false
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    val isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                        .getBoolean("enable_ad_sound", false)
                    view?.let { applyYouTubeKioskMode(it, isAdSoundEnabled) }
                    reportReadyOnce(url)
                }

                override fun onPageFinished(view: WebView?, urlStr: String?) {
                    val isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                        .getBoolean("enable_ad_sound", false)
                    view?.let { applyYouTubeKioskMode(it, isAdSoundEnabled) }
                    reportReadyOnce(urlStr)
                    // Detect embed restriction/player config errors (e.g., Error 152/153).
                    // If we are on an embed URL, fallback once to watch/shorts URL before skipping.
                    view?.evaluateJavascript(
                        """
                        (function() {
                          try {
                            var txt = (document.body && document.body.innerText ? document.body.innerText : '').toLowerCase();
                            var has152 = txt.indexOf('error 152') >= 0 || txt.indexOf('error code: 152') >= 0;
                            var has153 = txt.indexOf('error 153') >= 0 || txt.indexOf('error code: 153') >= 0;
                            var hasConfig = txt.indexOf('video player configuration error') >= 0;
                            if (has152) return '152';
                            if (has153) return '153';
                            if (hasConfig) return 'config';
                            return 'none';
                          } catch (e) {
                            return 'none';
                          }
                        })();
                        """.trimIndent()
                    ) { result ->
                        val token = result.trim().trim('"')
                        if (view != null) handleYouTubeRestrictionToken(view, token)
                    }
                    Log.i(
                        "YouTubeAdPerf",
                        "Page finished in ${System.currentTimeMillis() - loadStartedAtMs} ms, url=${(urlStr ?: activeUrl).take(120)}"
                    )
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    val description = error?.description?.toString() ?: "Unknown error"
                    val errorCode = error?.errorCode ?: 0
                    val requestUrl = request?.url?.toString().orEmpty()
                    // Only treat main-frame failures as terminal for ad switching.
                    // Subresource failures are common and should not interrupt active playback.
                    val shouldHandleAsPrimary = request == null || request.isForMainFrame
                    if (!shouldHandleAsPrimary) return
                    mainFrameFailed = true
                    Log.e("YouTubeAdPlayer", "WebView error ($errorCode) for ${activeUrl.take(60)}...: $description")
                    Log.e(
                        "YouTubeAdPerf",
                        "WebView error ($errorCode) after ${System.currentTimeMillis() - loadStartedAtMs} ms: $description"
                    )
                    
                    val isSslError = errorCode == ERROR_FAILED_SSL_HANDSHAKE || 
                                    errorCode == ERROR_PROXY_AUTHENTICATION ||
                                    description.contains("SSL", ignoreCase = true) ||
                                    description.contains("handshake", ignoreCase = true)
                                    
                    val isConnectionError = errorCode == ERROR_CONNECT ||
                                           errorCode == ERROR_TIMEOUT ||
                                           errorCode == ERROR_HOST_LOOKUP ||
                                           description.contains("ERR_NAME_NOT_RESOLV", ignoreCase = true) ||
                                           description.contains("ERR_FAILED", ignoreCase = true) ||
                                           description.contains("ERR_CONNECTION", ignoreCase = true) ||
                                           description.contains("ERR_SOCKET", ignoreCase = true) ||
                                           description.contains("ERR_TIMED_OUT", ignoreCase = true)

                    // One-shot fallback host retry for transient DNS/SSL path issues.
                    if ((isSslError || isConnectionError) && !dnsFallbackTried) {
                        val fallbackUrl = nextYouTubeDnsFallbackUrl(activeUrl)
                        if (!fallbackUrl.isNullOrBlank() && !fallbackUrl.equals(activeUrl, ignoreCase = true)) {
                            dnsFallbackTried = true
                            Log.w(
                                "YouTubeAdPerf",
                                "Retrying failed YouTube load with fallback URL: ${fallbackUrl.take(120)}"
                            )
                            activeUrl = fallbackUrl
                            readyReported = false
                            terminalEventReported = false
                            reportedDurationMs = null
                            loadStartedAtMs = System.currentTimeMillis()
                            view?.tag = null
                            view?.let { loadYouTubeUrlWithHeaders(it, fallbackUrl) }
                            return
                        }
                    }

                    // If fallback is exhausted/unavailable, skip this ad.
                    if (!terminalEventReported) {
                        terminalEventReported = true
                        latestOnError()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request == null || !request.isForMainFrame) return false
                    val requestedUrl = request.url?.toString().orEmpty()
                    val requestedId = extractYoutubeIdForAnyUrl(requestedUrl)
                    if (!expectedVideoId.isNullOrBlank() &&
                        !requestedId.isNullOrBlank() &&
                        !requestedId.equals(expectedVideoId, ignoreCase = true)
                    ) {
                        val canonicalTarget = expectedWatchUrl
                        if (!canonicalTarget.isNullOrBlank()) {
                            Log.w(
                                "YouTubeAdPerf",
                                "Blocked navigation to different YouTube id=$requestedId; keeping id=$expectedVideoId"
                            )
                            view?.tag = canonicalTarget
                            loadYouTubeUrlWithHeaders(view ?: return true, canonicalTarget)
                            return true
                        }
                    }
                    return false
                }
            }
            
            view.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.deny()
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (title == "AD_ENDED") {
                        Log.i("YouTubeAdPlayer", "Detected JS signal: AD_ENDED; triggering next ad.")
                        if (!terminalEventReported) {
                            terminalEventReported = true
                            latestOnVideoEnded()
                        }
                        return
                    }
                    val t = title.orEmpty().lowercase()
                    val restrictionFromTitle =
                        when {
                            t.contains("error 152") || t.contains("error code: 152") -> "152"
                            t.contains("error 153") || t.contains("error code: 153") -> "153"
                            t.contains("watch video on youtube") || t.contains("video player configuration error") -> "config"
                            else -> null
                        }
                    if (view != null && !restrictionFromTitle.isNullOrBlank()) {
                        Log.w(
                            "YouTubeAdPerf",
                            "Embed restriction inferred from title='$title' for url=${activeUrl.take(120)}"
                        )
                        handleYouTubeRestrictionToken(view, restrictionFromTitle)
                    }
                }
            }

            val preferredLoadUrl = activeUrl

            // Avoid reloading same page on recomposition.
            if (view.tag as? String != preferredLoadUrl) {
                view.tag = preferredLoadUrl
                currentLoadToken += 1
                val tokenForLoad = currentLoadToken
                mainHandler.post {
                    if (view.tag != preferredLoadUrl) return@post
                    if (tokenForLoad != currentLoadToken) return@post
                    loadYouTubeUrlWithHeaders(view, preferredLoadUrl)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .clipToBounds(),
    )
}

private fun buildBaseYoutubeWebView(context: Context): WebView {
    return WebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            // TV-like UA for better compatibility.
            userAgentString = "Mozilla/5.0 (Linux; Android 10; BRAVIA 4K VH2 Build/PG1.200115.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.7 Mobile Safari/537.36"
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false
        isHapticFeedbackEnabled = false
        setOnLongClickListener { true }
        // Ads are display-only; consume touch so WebView does not capture gestures/focus.
        setOnTouchListener { _, _ -> true }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                // Ads do not require mic/camera recording; deny capture requests to avoid
                // WebView/Chromium recording capability warnings.
                request?.deny()
            }
        }
    }
}

private fun releaseYouTubeWebView(webView: WebView, destroy: Boolean) {
    try {
        webView.stopLoading()
    } catch (_: Exception) {
    }
    try {
        webView.removeJavascriptInterface("CallQTVBridge")
    } catch (_: Exception) {
    }
    try {
        // Prevent late chromium callbacks from targeting stale handlers.
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    } catch (_: Exception) {
    }
    try {
        webView.loadUrl("about:blank")
    } catch (_: Exception) {
    }
    try {
        webView.onPause()
        webView.pauseTimers()
    } catch (_: Exception) {
    }
    try {
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
    } catch (_: Exception) {
    }
    if (!destroy) return
    try {
        // Delay actual destroy slightly so queued chromium callbacks drain first.
        webView.post {
            try {
                webView.destroy()
            } catch (_: Exception) {
            }
        }
    } catch (_: Exception) {
        try {
            webView.destroy()
        } catch (_: Exception) {
        }
    }
}

private fun applyYouTubeKioskMode(webView: WebView, isAdSoundEnabled: Boolean) {
    // Best-effort DOM cleanup for YouTube shorts/watch pages rendered directly in WebView.
    // Keeps video area while hiding surrounding page chrome/action panels.
    val strictAutoplay = webView.context
        .getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        .getBoolean(PREF_YOUTUBE_STRICT_AUTOPLAY, false)
    val effectiveMuted = strictAutoplay || !isAdSoundEnabled
    val mutedJs = if (effectiveMuted) "true" else "false"
    val volumeJs = if (effectiveMuted) "0.0" else "1.0"
    val restoreSoundAfterAutoplayJs = if (effectiveMuted) "false" else "true"
    val js = """
        (function() {
          try {
            var styleId = 'callqtv-kiosk-style';
            if (!document.getElementById(styleId)) {
              var s = document.createElement('style');
              s.id = styleId;
              s.textContent = `
                html, body { margin:0 !important; padding:0 !important; overflow:hidden !important; background:#000 !important; }
                body * { -webkit-user-select: none !important; user-select: none !important; }
                #page-manager, #content, #container, #player-container, #player-container-id,
                #player, #movie_player, .html5-video-player, .ytp-player-content, .player-api,
                ytd-app, ytd-watch-flexy, ytd-player, ytd-page-manager,
                ytm-app, ytm-watch, ytm-watch-page, ytm-player, ytm-single-column-watch-next-results-renderer {
                  width: 100vw !important;
                  height: 100vh !important;
                  max-width: 100vw !important;
                  max-height: 100vh !important;
                  margin: 0 !important;
                  padding: 0 !important;
                  position: relative !important;
                  inset: 0 !important;
                  transform: none !important;
                  background: #000 !important;
                  overflow: hidden !important;
                }
                ytm-header-renderer, ytd-masthead, #header, #topbar, #guide-button,
                ytm-sub-header-renderer, #ytm-navigation-bar,
                ytm-mobile-topbar-renderer, ytm-topbar-logo-renderer, ytm-topbar-menu-button-renderer,
                ytm-topbar-search-button-renderer, ytm-open-app-button-renderer, .mobile-topbar-header,
                .ytm-topbar-menu-button, .ytm-topbar-logo, .ytm-header-container,
                #comments, ytd-comments, ytm-comments-entry-point-header-renderer,
                ytd-reel-player-overlay-renderer, ytm-reel-player-overlay-renderer,
                #actions, #menu, #related, #secondary, #below, #info, .metadata-container,
                ytd-watch-next-secondary-results-renderer, ytd-watch-next-results-renderer,
                ytm-pivot-bar-renderer, ytm-item-section-renderer, ytm-browse,
                ytm-watch-metadata, ytm-slim-owner-renderer, ytm-expandable-video-description-body-renderer,
                ytm-watch-next-secondary-results-renderer, ytm-structured-description-content-renderer,
                .ytp-show-cards-title, .ytp-pause-overlay, .ytp-ce-element,
                .ytp-chrome-top, .ytp-chrome-bottom,
                .ytp-title, .ytp-title-channel, .ytp-title-link,
                .ytp-endscreen-content, .ytp-endscreen-element,
                .branding-img-container, .iv-branding,
                .ytp-unmute, .ytp-unmute-button, .ytp-unmute-inner, .ytp-mute-button,
                .ytp-controls, .ytp-progress-bar-container, .ytp-time-display,
                .ytp-button, .ytp-right-controls, .ytp-left-controls,
                .ytp-gradient-top, .ytp-gradient-bottom, .ytp-cued-thumbnail-overlay,
                .html5-endscreen, .ytp-player-content ytp-ce-element {
                  display:none !important;
                }
                video, .video-stream, .html5-main-video {
                  width: 100% !important;
                  height: 100% !important;
                  max-width: 100vw !important;
                  max-height: 100vh !important;
                  object-fit: cover !important;
                  background: #000 !important;
                  position: absolute !important;
                  top: 50% !important;
                  left: 50% !important;
                  transform: translate(-50%, -50%) !important;
                  z-index: 2147483646 !important;
                }
                video::-webkit-media-controls,
                video::-webkit-media-controls-panel,
                video::-webkit-media-controls-play-button,
                video::-webkit-media-controls-start-playback-button,
                video::-webkit-media-controls-timeline,
                video::-webkit-media-controls-current-time-display,
                video::-webkit-media-controls-time-remaining-display,
                video::-webkit-media-controls-mute-button,
                video::-webkit-media-controls-fullscreen-button {
                  display: none !important;
                  -webkit-appearance: none !important;
                }
              `;
              (document.head || document.documentElement).appendChild(s);
            }
            function removeMobileChrome() {
              try {
                var selectors = [
                  'ytm-mobile-topbar-renderer',
                  'ytm-topbar-logo-renderer',
                  'ytm-topbar-menu-button-renderer',
                  'ytm-topbar-search-button-renderer',
                  'ytm-open-app-button-renderer',
                  '.mobile-topbar-header',
                  '.ytm-header-container',
                  '#header',
                  '#topbar'
                ];
                selectors.forEach(function(sel) {
                  document.querySelectorAll(sel).forEach(function(node) {
                    try { node.remove(); } catch (e) {
                      try {
                        node.style.setProperty('display', 'none', 'important');
                        node.style.setProperty('visibility', 'hidden', 'important');
                        node.style.setProperty('opacity', '0', 'important');
                        node.style.setProperty('pointer-events', 'none', 'important');
                      } catch (e2) {}
                    }
                  });
                });
              } catch (e) {}
            }
            removeMobileChrome();
            
            // Auto-play + ended detection for watch/shorts pages without URL params
            var v = document.querySelector('video');
            if (v) {
              try {
                try {
                  v.controls = false;
                  v.removeAttribute('controls');
                  v.setAttribute('playsinline', 'true');
                  v.setAttribute('webkit-playsinline', 'true');
                  v.setAttribute('controlsList', 'nodownload nofullscreen noplaybackrate noremoteplayback');
                  v.disablePictureInPicture = true;
                } catch (e) {}
                // Start muted for autoplay reliability, then optionally restore sound.
                v.muted = true;
                v.volume = 0.0;
                var playPromise = v.play();
                if (playPromise && typeof playPromise.then === 'function') {
                  playPromise.then(function() {
                    if ($restoreSoundAfterAutoplayJs) {
                      try {
                        v.muted = false;
                        v.volume = 1.0;
                      } catch (e) {}
                    } else {
                      v.muted = $mutedJs;
                      v.volume = $volumeJs;
                    }
                  }).catch(function(e){ console.warn('Autoplay blocked:', e); });
                } else {
                  if ($restoreSoundAfterAutoplayJs) {
                    try {
                      v.muted = false;
                      v.volume = 1.0;
                    } catch (e) {}
                  } else {
                    v.muted = $mutedJs;
                    v.volume = $volumeJs;
                  }
                }
                // Best-effort user-gesture simulation for TVs where autoplay policy is stricter.
                try {
                  var clickEv = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                  v.dispatchEvent(clickEv);
                } catch (e) {}
                // Also poke known play buttons when present.
                try {
                  var playBtn = document.querySelector('.ytp-large-play-button, .ytp-play-button, button[aria-label*="Play"], button[aria-label*="play"]');
                  if (playBtn) {
                    playBtn.click();
                  }
                } catch (e) {}
                if (isFinite(v.duration) && v.duration > 0) {
                  if (window.CallQTVBridge && window.CallQTVBridge.onAdDuration) {
                    window.CallQTVBridge.onAdDuration(v.duration);
                  }
                } else {
                  v.addEventListener('loadedmetadata', function() {
                    try {
                      if (isFinite(v.duration) && v.duration > 0 &&
                          window.CallQTVBridge && window.CallQTVBridge.onAdDuration) {
                        window.CallQTVBridge.onAdDuration(v.duration);
                      }
                    } catch (e) {}
                  }, { once: true });
                }
                if (window.CallQTVBridge && window.CallQTVBridge.onAdReady) {
                  window.CallQTVBridge.onAdReady();
                }
              } catch (e) {}
              // Set up a one-time ended listener to trigger the next ad
              if (!v._callqtv_hooked) {
                v._callqtv_hooked = true;
                v.addEventListener('ended', function() {
                  console.log('CallQTV: Video ended');
                  if (window.CallQTVBridge && window.CallQTVBridge.onAdEnded) {
                    window.CallQTVBridge.onAdEnded();
                  }
                  document.title = 'AD_ENDED';
                });
              }
              // Keep re-applying autoplay and ended detection because YouTube DOM/video
              // can be recreated during shorts/watch transitions.
              if (!window._callqtv_autoplay_interval) {
                window._callqtv_autoplay_interval = setInterval(function() {
                  try {
                    var vv = document.querySelector('video');
                    if (!vv) return;
                    try {
                      vv.controls = false;
                      vv.removeAttribute('controls');
                      vv.setAttribute('playsinline', 'true');
                      vv.setAttribute('webkit-playsinline', 'true');
                      vv.setAttribute('controlsList', 'nodownload nofullscreen noplaybackrate noremoteplayback');
                      vv.disablePictureInPicture = true;
                    } catch (e) {}
                    if (vv.ended) {
                      if (window.CallQTVBridge && window.CallQTVBridge.onAdEnded) {
                        window.CallQTVBridge.onAdEnded();
                      }
                      document.title = 'AD_ENDED';
                      return;
                    }
                    if (vv.paused) {
                      // Re-attempt autoplay in muted mode first to satisfy policy.
                      vv.muted = true;
                      vv.volume = 0.0;
                      var resumePromise = vv.play();
                      if (resumePromise && typeof resumePromise.then === 'function') {
                        resumePromise.then(function() {
                          if ($restoreSoundAfterAutoplayJs) {
                            try {
                              vv.muted = false;
                              vv.volume = 1.0;
                            } catch (e) {}
                          } else {
                            vv.muted = $mutedJs;
                            vv.volume = $volumeJs;
                          }
                        }).catch(function(){});
                      }
                    }
                    if (isFinite(vv.duration) && vv.duration > 0 &&
                        window.CallQTVBridge && window.CallQTVBridge.onAdDuration) {
                      window.CallQTVBridge.onAdDuration(vv.duration);
                    }
                    // Re-assert current app sound setting in case YouTube DOM mutates.
                    if (!$restoreSoundAfterAutoplayJs) {
                      vv.muted = $mutedJs;
                      vv.volume = $volumeJs;
                    }
                    if (window.CallQTVBridge && window.CallQTVBridge.onAdReady) {
                      window.CallQTVBridge.onAdReady();
                    }
                    removeMobileChrome();
                  } catch (e) {}
                }, 1200);
              }
            }
          } catch (e) {}
        })();
    """.trimIndent()
    try {
        webView.evaluateJavascript(js, null)
    } catch (_: Exception) {
        // Ignore JS injection failures; playback should continue.
    }
}

private fun nextYouTubeDnsFallbackUrl(currentUrl: String): String? {
    return try {
        val uri = Uri.parse(currentUrl)
        val host = (uri.host ?: return null).lowercase()
        val id = extractYoutubeIdForFallback(currentUrl)
        when (host) {
            "youtube.com", "www.youtube.com" -> {
                // Prefer short-link fallback over host swap for /shorts and /watch URLs.
                // Swapping to youtube-nocookie with /shorts path is unreliable on some TVs.
                if (id != null) "https://youtu.be/$id" else uri.buildUpon().authority("m.youtube.com").build().toString()
            }
            "m.youtube.com" -> {
                if (id != null) "https://youtu.be/$id" else uri.buildUpon().authority("www.youtube.com").build().toString()
            }
            "youtu.be" -> {
                if (id != null) "https://www.youtube.com/watch?v=$id" else null
            }
            "www.youtube-nocookie.com", "youtube-nocookie.com" -> {
                // Move back to standard host for page mode.
                if (id != null) "https://www.youtube.com/watch?v=$id" else null
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun extractYoutubeIdForFallback(url: String): String? {
    val u = url.trim()
    Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    return null
}

private fun nextYouTubeEmbedFallbackUrl(currentUrl: String): String? {
    val id = extractYoutubeIdForFallback(currentUrl) ?: return null
    val lower = currentUrl.lowercase()
    if (lower.contains("/embed/$id")) return null
    return "https://www.youtube.com/embed/$id?autoplay=1&mute=1&playsinline=1&controls=0&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com&widget_referrer=https%3A%2F%2Fwww.youtube.com"
}

private fun nextYouTubeWatchFallbackUrl(currentUrl: String): String? {
    val id = extractYoutubeIdForAnyUrl(currentUrl) ?: return null
    val target = "https://www.youtube.com/watch?v=$id"
    return if (currentUrl.equals(target, ignoreCase = true)) null else target
}

private fun normalizeYouTubePlaybackUrl(url: String): String {
    val trimmed = url.trim()
    return nextYouTubeWatchFallbackUrl(trimmed) ?: trimmed
}

private fun extractYoutubeIdForAnyUrl(url: String): String? {
    val u = url.trim()
    Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    return extractYoutubeIdForFallback(u)
}

private fun loadYouTubeUrlWithHeaders(webView: WebView, url: String) {
    val headers = mapOf(
        "Referer" to "https://www.youtube.com/",
        "Origin" to "https://www.youtube.com",
        // Clearing this header improves compatibility with some embedded providers.
        "X-Requested-With" to ""
    )
    try {
        webView.loadUrl(url, headers)
    } catch (_: Exception) {
        webView.loadUrl(url)
    }
}

private fun loadYouTubeIframeFallback(webView: WebView, sourceUrl: String): Boolean {
    val id = extractYoutubeIdForAnyUrl(sourceUrl) ?: return false
    val embedUrl =
        "https://www.youtube.com/embed/$id?autoplay=1&mute=1&playsinline=1&controls=0&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com&widget_referrer=https%3A%2F%2Fwww.youtube.com"
    val html = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
          <style>
            html, body { margin: 0; padding: 0; background: #000; width: 100%; height: 100%; overflow: hidden; }
            iframe { border: 0; width: 100%; height: 100%; display: block; }
          </style>
        </head>
        <body>
          <iframe
            src="$embedUrl"
            allow="autoplay; encrypted-media; picture-in-picture"
            referrerpolicy="origin"
            allowfullscreen>
          </iframe>
        </body>
        </html>
    """.trimIndent()
    return try {
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        true
    } catch (_: Exception) {
        false
    }
}

internal fun isAdLowBandwidthNetwork(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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


@Composable
fun AdVideoPlayer(
    videoUrl: String,
    player: ExoPlayer,
    sharedTextureView: android.view.TextureView? = null,
    onVideoEnded: () -> Unit,
    onReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    val videoTextureRef = remember(sharedTextureView) { mutableStateOf<android.view.TextureView?>(sharedTextureView) }
    var latestVideoSize by remember { mutableStateOf(androidx.media3.common.VideoSize.UNKNOWN) }

    var readyReported by remember(videoUrl) { mutableStateOf(false) }
    var terminalEventReported by remember(videoUrl) { mutableStateOf(false) }
    var loadStartedAtMs by remember(videoUrl) { mutableStateOf(0L) }
    val isLowBandwidth = remember(videoUrl) { isAdLowBandwidthNetwork(context) }

    DisposableEffect(videoUrl, player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        readyReported = true
                        Log.i(
                            "AdVideoPerf",
                            "STATE_READY in ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${videoUrl.take(120)}"
                        )
                        mainHandler.post { latestOnReady() }
                    }
                    Player.STATE_ENDED -> {
                        terminalEventReported = true
                        mainHandler.post { latestOnVideoEnded() }
                    }
                }
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                Log.e("AdVideoPlayer", "ExoPlayer error playing $videoUrl: ${e.message}", e)
                Log.e(
                    "AdVideoPerf",
                    "Player error after ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${videoUrl.take(120)}"
                )
                // ExoPlayer/Media3 enforces main-thread access for player methods.
                mainHandler.post {
                    try {
                        player.stop()
                        player.clearMediaItems()
                    } catch (_: Exception) {
                        // Ignore cleanup failures; we still trigger the ad skip.
                    }
                    terminalEventReported = true
                    latestOnError()
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                latestVideoSize = videoSize
                videoTextureRef.value?.post {
                    applyFitCenterTransform(videoTextureRef.value, videoSize)
                }
            }
        }
        
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Detach texture host and pause playback, but keep decoder session warm.
            // Using TextureView avoids repeated SurfaceView lifecycle churn on IMG GPUs.
            try {
                // Keep player operations on main thread to avoid Media3 thread violations.
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    val activeTexture = videoTextureRef.value
                    if (sharedTextureView == null) {
                        activeTexture?.let { texture ->
                            player.clearVideoTextureView(texture)
                            (texture.parent as? android.view.ViewGroup)?.removeView(texture)
                        }
                    }
                    if (sharedTextureView == null) {
                        videoTextureRef.value = null
                    } else {
                        videoTextureRef.value = sharedTextureView
                    }
                    player.playWhenReady = false
                    player.pause()
                } else {
                    mainHandler.post {
                        try {
                            val activeTexture = videoTextureRef.value
                            if (sharedTextureView == null) {
                                activeTexture?.let { texture ->
                                    player.clearVideoTextureView(texture)
                                    (texture.parent as? android.view.ViewGroup)?.removeView(texture)
                                }
                            }
                            if (sharedTextureView == null) {
                                videoTextureRef.value = null
                            } else {
                                videoTextureRef.value = sharedTextureView
                            }
                            player.playWhenReady = false
                            player.pause()
                        } catch (_: Exception) {
                            // Ignore; pause may still throw on some TV device states.
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore; pause may still throw on some TV device states.
            }
        }
    }
    
    LaunchedEffect(videoUrl, player) {
        readyReported = false
        terminalEventReported = false
        loadStartedAtMs = System.currentTimeMillis()
        // Media3 requires player method calls on the main thread.
        // We only keep lightweight work off-main (MediaItem creation).
        scope.launch(Dispatchers.Default) {
            val mediaItem = try {
                val lower = videoUrl.lowercase()
                val mimeType = when {
                    lower.contains(".m3u8") || lower.contains("format=m3u8") -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
                    lower.contains(".mpd") -> androidx.media3.common.MimeTypes.APPLICATION_MPD
                    else -> null
                }
                if (mimeType != null) {
                    MediaItem.Builder()
                        .setUri(videoUrl)
                        .setMimeType(mimeType)
                        .build()
                } else {
                    MediaItem.fromUri(videoUrl)
                }
            } catch (e: Exception) {
                Log.e("AdVideoPlayer", "Failed to create media item: $videoUrl", e)
                null
            }
            if (mediaItem == null) {
                mainHandler.post {
                    terminalEventReported = true
                    latestOnError()
                }
                return@launch
            }

            mainHandler.post {
                try {
                    applyAdaptiveVideoTrackCap(
                        player = player,
                        videoUrl = videoUrl,
                        isLowBandwidth = isLowBandwidth
                    )
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                } catch (e: Exception) {
                    Log.e("AdVideoPlayer", "Failed to prepare video: $videoUrl", e)
                    terminalEventReported = true
                    latestOnError()
                }
            }
        }
    }

    // If ExoPlayer never reaches STATE_READY, treat it as a failed ad and skip.
    // Keep a longer timeout for low-bandwidth/live links.
    LaunchedEffect(videoUrl) {
        val isLikelyLive = videoUrl.lowercase().contains(".m3u8") ||
            videoUrl.lowercase().contains(".mpd") ||
            videoUrl.lowercase().contains("live")
        val timeoutMs = when {
            isLikelyLive && isLowBandwidth -> 120_000L
            isLikelyLive -> 90_000L
            isLowBandwidth -> 80_000L
            else -> 60_000L
        }
        delay(timeoutMs)
        if (!readyReported && !terminalEventReported) {
            Log.w("AdVideoPlayer", "Video not ready within timeout; skipping. url=$videoUrl")
            FileLogger.logError(context, "AdVideoPlayer", "Video not ready within timeout; skipping. url=$videoUrl")
            terminalEventReported = true
            latestOnError()
        }
    }

    AndroidView(
        factory = {
            val texture = sharedTextureView ?: buildSharedAdTextureView(context)
            (texture.parent as? android.view.ViewGroup)?.removeView(texture)
            if (texture.tag != "callqtv_ad_texture_bound") {
                texture.tag = "callqtv_ad_texture_bound"
                texture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    applyFitCenterTransform(texture, latestVideoSize)
                }
            }
            videoTextureRef.value = texture
            player.setVideoTextureView(texture)
            texture
        },
        update = { view ->
            if (videoTextureRef.value !== view) {
                videoTextureRef.value?.let { oldTexture ->
                    if (oldTexture !== view) player.clearVideoTextureView(oldTexture)
                }
                videoTextureRef.value = view
                player.setVideoTextureView(view)
            }
            applyFitCenterTransform(view, latestVideoSize)
        },
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
    )
}

private fun buildSharedAdTextureView(context: Context): android.view.TextureView {
    return android.view.TextureView(context).apply {
        setOpaque(false)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}

private fun applyAdaptiveVideoTrackCap(
    player: ExoPlayer,
    videoUrl: String,
    isLowBandwidth: Boolean
) {
    val lower = videoUrl.lowercase()
    val isLikelyLive = lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("live")
    val maxBitrate = when {
        // Keep adaptive/live streams conservative to avoid bitrate oscillation stutter.
        isLowBandwidth && isLikelyLive -> 900_000
        isLowBandwidth -> 1_800_000
        else -> 5_000_000
    }
    try {
        val current = player.trackSelectionParameters
        val updated = current.buildUpon()
            .setMaxVideoBitrate(maxBitrate)
            .build()
        player.trackSelectionParameters = updated
        Log.i(
            "AdVideoPerf",
            "Track cap applied bitrate=${maxBitrate}bps lowBandwidth=$isLowBandwidth live=$isLikelyLive url=${videoUrl.take(120)}"
        )
    } catch (e: Exception) {
        Log.w("AdVideoPerf", "Failed applying adaptive track cap for ${videoUrl.take(120)}: ${e.message}")
    }
}

private fun applyFitCenterTransform(
    textureView: android.view.TextureView?,
    videoSize: androidx.media3.common.VideoSize
) {
    if (textureView == null) return
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f) return

    val rawVideoWidth = videoSize.width
    val rawVideoHeight = videoSize.height
    if (rawVideoWidth <= 0 || rawVideoHeight <= 0) {
        textureView.setTransform(null)
        return
    }

    val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
    val videoWidth = rawVideoWidth.toFloat() * pixelRatio
    val videoHeight = rawVideoHeight.toFloat()
    if (videoWidth <= 0f || videoHeight <= 0f) {
        textureView.setTransform(null)
        return
    }

    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth / videoHeight
    val matrix = Matrix()

    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        // Wider than container: fit width, letterbox top/bottom.
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    } else {
        // Taller than container: fit height, letterbox left/right.
        scaleX = videoAspect / viewAspect
        scaleY = 1f
    }

    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    val current = Matrix()
    textureView.getTransform(current)
    if (!matrixAlmostEqual(current, matrix)) {
        textureView.setTransform(matrix)
        // Avoid forcing extra redraws every recomposition/layout pass.
    }
}

private fun matrixAlmostEqual(a: Matrix, b: Matrix, epsilon: Float = 0.001f): Boolean {
    val av = FloatArray(9)
    val bv = FloatArray(9)
    a.getValues(av)
    b.getValues(bv)
    for (i in 0 until 9) {
        if (kotlin.math.abs(av[i] - bv[i]) > epsilon) return false
    }
    return true
}

private object WebViewWarmup {
    private val warmed = AtomicBoolean(false)

    suspend fun warmUp(context: Context) {
        if (!warmed.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.Main) {
                val webView = WebView(context.applicationContext)
                try {
                    webView.settings.javaScriptEnabled = true
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        "<html><body style='background:#000'></body></html>",
                        "text/html",
                        "UTF-8",
                        null
                    )
                } finally {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
            Log.i("YouTubeAdPerf", "WebView warm-up completed")
        } catch (e: Exception) {
            Log.w("YouTubeAdPerf", "WebView warm-up failed: ${e.message}")
        }
    }
}

@UnstableApi
object MediaEngine {
    private var player1: ExoPlayer? = null
    private var player2: ExoPlayer? = null
    private var simpleCache: androidx.media3.datasource.cache.Cache? = null
    private var cacheFactory: DataSource.Factory? = null

    private fun createPlayer(context: Context): ExoPlayer {
        // Large/remote ad videos can be slow to establish connections on TV networks.
        // ExoPlayer's HTTP stack uses OkHttp underneath, so we must set longer timeouts here too.
        val httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
            .newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
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
            // Favor smoother playback over fastest possible start:
            // Min 8s, Max 40s, start at 1.5s, rebuffer at 3s.
            .setBufferDurationsMs(8_000, 40_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Enable decoder fallback to improve compatibility on heterogeneous Android TV/SoC decoders.
        // Some devices fail MediaCodec.configure() for certain AVC streams; fallback lets ExoPlayer
        // try alternative initialization paths instead of getting stuck on a single codec.
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true)

        // Strictly enforce safe decoder bounds.
        // Without strict enforcement, ExoPlayer may still pick the 2160x3840@25 track and crash with:
        // "NoSupport sizeAndRate ... 2160x3840@25.0"
        val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
            val params: androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters =
                ParametersBuilder(context.applicationContext)
                    .setMaxVideoSize(1080, 1920)
                    .setMaxVideoFrameRate(30)
                    .setExceedVideoConstraintsIfNecessary(false)
                    .build()
            this.parameters = params
        }

        return ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
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

    /**
     * Pre-initialize players/cache to avoid heavy work on the first ad composition.
     * Safe to call multiple times.
     */
    fun warmUp(context: Context) {
        if (simpleCache == null) {
            // Ensure cacheFactory is initialized as part of first player creation.
            // (createPlayer() lazily initializes cacheFactory.)
        }
        if (player1 == null) player1 = createPlayer(context)
        if (player2 == null) player2 = createPlayer(context)
        // No return; players are now ready to be used by AdVideoPlayer.
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

        // Use layoutType to influence splitting. 
        // type "1" (default) = automated split
        // type "2" = force no split (single grid/column)
        // type "3" = force split even for few counters
        val splitThreshold = when (layoutType) {
            "2" -> 999 
            "3" -> 0
            else -> if (isPortrait) 4 else 2
        }

        if (numCounters > splitThreshold && numCounters > 1) {
            val firstHalfCount = (numCounters + 1) / 2
            val firstHalf = counters.take(firstHalfCount)
            val secondHalf = counters.drop(firstHalfCount)

            if (isPortrait) {
                // Portrait: split horizontally (top/bottom)
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        firstHalf.forEach { counter ->
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, layoutType = layoutType)
                        }
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L, layoutType = layoutType)
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
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L, layoutType = layoutType)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L, layoutType = layoutType)
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
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L, layoutType = layoutType)
                    }
                }
            } else {
                // Landscape: single horizontal row of counters
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter ->
                        val sKey = counter.counterId?.trim() ?: counter.name?.trim() ?: counter.defaultName?.trim() ?: counter.buttonIndex?.toString() ?: ""
                        val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sKey] ?: 0L, layoutType = layoutType)
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
    blinkTrigger: Long = 0L,
    layoutType: String = "1"
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
            val usePrefix = config.enableCounterPrefix != false
            if (isPortrait) {
                Row(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                    // If ads exist, use 30% for name and 70% for tokens.
                    // If no ads, keep name compact and give 80% to tokens.
                    val nameWeight = if (hasAds) 0.30f else 0.2f
                    val tokenWeight = if (hasAds) 0.70f else 0.8f

                    // Counter name area
                    BoxWithConstraints(
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
                                val formattedToken = remember(token, config.tokenFormat) {
                                    formatTokenByPattern(token, config.tokenFormat)
                                }
                                val displayToken = when {
                                    formattedToken == null -> null
                                    usePrefix && counterCode.isNotBlank() -> "$counterCode-$formattedToken"
                                    else -> formattedToken
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
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
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
                            val formattedToken = remember(token, config.tokenFormat) {
                                formatTokenByPattern(token, config.tokenFormat)
                            }
                            val displayToken = when {
                                formattedToken == null -> null
                                usePrefix && counterCode.isNotBlank() -> "$counterCode-$formattedToken"
                                else -> formattedToken
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
    var showSettingsHelpDialog by remember { mutableStateOf(false) }

    var currentCounterHex by remember { mutableStateOf("#FFFFFF") }
    var currentTokenHex by remember { mutableStateOf("#FFFFFF") }
    var customerId by remember { mutableStateOf(0) }
    var currentThemeHex by remember { mutableStateOf("#2196F3") }
    var notificationSoundKey by remember { mutableStateOf("ding") }
    var is24Hour by remember { mutableStateOf(true) }
    var isAdSoundEnabled by remember { mutableStateOf(false) }
    var isYouTubeAdsEnabled by remember { mutableStateOf(true) }
    var isYouTubeStrictAutoplay by remember { mutableStateOf(false) }
    var isYouTubePlayUntilEnded by remember { mutableStateOf(false) }
    var isOfflineAdsEnabled by remember { mutableStateOf(true) }
    var isExportingSnapshot by remember { mutableStateOf(false) }
    var exportSnapshotStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
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
            isYouTubeStrictAutoplay = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_YOUTUBE_STRICT_AUTOPLAY, false)
            isYouTubePlayUntilEnded = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_YOUTUBE_PLAY_UNTIL_ENDED, false)
            isOfflineAdsEnabled = PreferenceHelper.isOfflineAdsEnabled(context)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // Always open Settings with the first tab selected.
    LaunchedEffect(Unit) { selectedTabIndex = 0 }
    val appThemeFocusRequester = remember { FocusRequester() }
    val settingsHelpFocusRequester = remember { FocusRequester() }
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
    LaunchedEffect(showSettingsHelpDialog) {
        if (showSettingsHelpDialog) {
            delay(120)
            try {
                settingsHelpFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
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
    } else if (showSettingsHelpDialog) {
        val displayHelp = listOf(
            "App Theme: changes overall app color theme.",
            "Counter BG: changes background color behind counters.",
            "Token BG: changes background color behind token values."
        )
        val audioHelp = listOf(
            "Notification Sound: selects the alert sound.",
            "Advertisement Sound: enables/disables ad audio."
        )
        val otherHelp = listOf(
            "24-Hour Format: toggles time display between 24h and 12h.",
            "Offline Advertisements: play ads from downloaded local files.",
            "Allow YouTube Ads: allows YouTube links in ad playlist.",
            "YouTube Strict Autoplay: forces muted autoplay for better TV compatibility.",
            "YouTube: Play Until Ended: waits for natural video end (long safety fallback).",
            "Clear saved token history: resets stored/queued token call history."
        )
        val systemHelp = listOf(
            "Shows device/app info and useful diagnostic details."
        )
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.9f),
            onDismissRequest = { showSettingsHelpDialog = false },
            title = { Text("Settings Help", style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .focusRequester(settingsHelpFocusRequester)
                        .focusable(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Display", fontWeight = FontWeight.Bold, color = settingsPrimary)
                    }
                    items(displayHelp) { text ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = settingsCard),
                            border = BorderStroke(1.dp, settingsBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "• $text",
                                color = settingsText,
                                fontSize = 14.sp,
                                softWrap = true,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    item {
                        Text("Audios", fontWeight = FontWeight.Bold, color = settingsPrimary)
                    }
                    items(audioHelp) { text ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = settingsCard),
                            border = BorderStroke(1.dp, settingsBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "• $text",
                                color = settingsText,
                                fontSize = 14.sp,
                                softWrap = true,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    item {
                        Text("Other", fontWeight = FontWeight.Bold, color = settingsPrimary)
                    }
                    items(otherHelp) { text ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = settingsCard),
                            border = BorderStroke(1.dp, settingsBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "• $text",
                                color = settingsText,
                                fontSize = 14.sp,
                                softWrap = true,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    item {
                        Text("System", fontWeight = FontWeight.Bold, color = settingsPrimary)
                    }
                    items(systemHelp) { text ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = settingsCard),
                            border = BorderStroke(1.dp, settingsBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "• $text",
                                color = settingsText,
                                fontSize = 14.sp,
                                softWrap = true,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsHelpDialog = false }) {
                    Text("Close")
                }
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
                                        helpText = "Choose the sound played for token/call notifications.",
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
                                        helpText = "Enable or mute audio from advertisement videos.",
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
                                        title = "Help / Settings Guide",
                                        helpText = "Open a full guide explaining all settings in this screen.",
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = { showSettingsHelpDialog = true }
                                    ) {
                                        Text(
                                            "Understand what each setting does",
                                            fontSize = 15.sp,
                                            color = settingsText
                                        )
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "24-Hour Format",
                                        helpText = "Show time in 24-hour format (13:00) instead of 12-hour (1:00 PM).",
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
                                        helpText = "When enabled, ads are played from local storage for better reliability in low internet conditions.",
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
                                        helpText = "Allow YouTube links in ad playlist. Disable to skip YouTube items automatically.",
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

                                item {
                                    GridSettingsItem(
                                        title = "YouTube Strict Autoplay",
                                        helpText = "Forces muted autoplay for YouTube ads, improving playback reliability on TV devices.",
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = {
                                            isYouTubeStrictAutoplay = !isYouTubeStrictAutoplay
                                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                                .edit()
                                                .putBoolean(PREF_YOUTUBE_STRICT_AUTOPLAY, isYouTubeStrictAutoplay)
                                                .apply()
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isYouTubeStrictAutoplay,
                                            onCheckedChange = {
                                                isYouTubeStrictAutoplay = it
                                                context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                                    .edit()
                                                    .putBoolean(PREF_YOUTUBE_STRICT_AUTOPLAY, it)
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

                                item {
                                    GridSettingsItem(
                                        title = "YouTube: Play Until Ended",
                                        helpText = "Wait for natural video end. If not detected, a long safety timeout will still move to next ad.",
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                        onClick = {
                                            isYouTubePlayUntilEnded = !isYouTubePlayUntilEnded
                                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                                .edit()
                                                .putBoolean(PREF_YOUTUBE_PLAY_UNTIL_ENDED, isYouTubePlayUntilEnded)
                                                .apply()
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isYouTubePlayUntilEnded,
                                            onCheckedChange = {
                                                isYouTubePlayUntilEnded = it
                                                context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                                    .edit()
                                                    .putBoolean(PREF_YOUTUBE_PLAY_UNTIL_ENDED, it)
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
                                        helpText = "Clears locally saved token/call history and refreshes current state.",
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
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        if (isExportingSnapshot) return@OutlinedButton
                                        isExportingSnapshot = true
                                        exportSnapshotStatus = "Exporting snapshot..."
                                        scope.launch(Dispatchers.IO) {
                                            val result = DiagnosticsExporter.exportConfigSnapshot(context)
                                            withContext(Dispatchers.Main) {
                                                isExportingSnapshot = false
                                                exportSnapshotStatus = result.message
                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (isExportingSnapshot) "Exporting..." else "Export Logs/Config Snapshot")
                                }
                                exportSnapshotStatus?.let { status ->
                                    Text(
                                        text = status,
                                        fontSize = 12.sp,
                                        color = settingsMutedText,
                                        maxLines = 2
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
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
    helpText: String? = null,
    titleColor: Color = Color(0xFF4FC3F7),
    cardColor: Color = Color(0xFF263238),
    borderColor: Color = Color(0xFF607D8B),
    content: @Composable () -> Unit
) {
    var showHelp by remember { mutableStateOf(false) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.weight(1f)
                )
                if (!helpText.isNullOrBlank()) {
                    IconButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = "Help for $title",
                            tint = titleColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            content()
        }
    }
    if (showHelp && !helpText.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(title) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text("OK")
                }
            }
        )
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
    val context = LocalContext.current
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
                            scope.launch { playTokenChime(context, key) }
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

    val textColor = android.graphics.Color.WHITE
    val textSizeSp = if (isPortrait) 12f else 14f
    // Use a compact repeating unit so ticker flow feels continuous (no visible block reset).
    val marqueeText = remember(scrollText) { "$scrollText   \u2022   " }
    val footerHeight = if (isPortrait) 24.dp else 28.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Keep footer readable even when app theme is very dark.
            .background(Color(0xFF1E4E79))
            .height(footerHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        AndroidView(
            factory = { ctx ->
                SeamlessTickerView(ctx)
            },
            update = { ticker ->
                ticker.bind(
                    text = marqueeText,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    isBold = true,
                    speedDpPerSec = if (isPortrait) 24f else 28f
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        )
    }
}

private class SeamlessTickerView(context: Context) : FrameLayout(context) {
    private val text1 = TextView(context)
    private val text2 = TextView(context)
    private var animator: ValueAnimator? = null
    private var cachedText: String = ""
    private var cachedSpeedDpPerSec: Float = -1f
    private var cachedTextColor: Int = Int.MIN_VALUE
    private var cachedTextSizeSp: Float = -1f
    private var cachedIsBold: Boolean = false
    private var currentOffset: Float = 0f
    private var lastDistance: Float = 1f

    init {
        clipToPadding = true
        clipChildren = true
        setWillNotDraw(true)

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        text1.layoutParams = lp
        text2.layoutParams = lp
        addView(text1)
        addView(text2)
    }

    fun bind(
        text: String,
        textColor: Int,
        textSizeSp: Float,
        isBold: Boolean,
        speedDpPerSec: Float
    ) {
        val styleChanged =
            cachedTextColor != textColor || cachedTextSizeSp != textSizeSp || cachedIsBold != isBold
        if (styleChanged) {
            cachedTextColor = textColor
            cachedTextSizeSp = textSizeSp
            cachedIsBold = isBold
            configureTextView(text1, textColor, textSizeSp, isBold)
            configureTextView(text2, textColor, textSizeSp, isBold)
        }

        val textChanged = cachedText != text
        val speedChanged = cachedSpeedDpPerSec != speedDpPerSec
        if (textChanged) {
            cachedText = text
            text1.text = text
            text2.text = text
        }
        if (speedChanged) {
            cachedSpeedDpPerSec = speedDpPerSec
        }
        if (textChanged || speedChanged || styleChanged) {
            restartAnimation(preservePhase = true)
        } else if (animator?.isRunning != true) {
            restartAnimation(preservePhase = true)
        }
    }

    private fun configureTextView(tv: TextView, color: Int, sizeSp: Float, isBold: Boolean) {
        tv.setTextColor(color)
        tv.textSize = sizeSp
        tv.setTypeface(tv.typeface, if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tv.isSingleLine = true
        tv.includeFontPadding = false
        tv.gravity = android.view.Gravity.CENTER_VERTICAL
        tv.setHorizontallyScrolling(true)
        tv.setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun restartAnimation(preservePhase: Boolean) {
        post {
            val previousOffset = currentOffset
            animator?.cancel()
            val w = width.toFloat()
            if (w <= 0f) return@post

            val textWidth = text1.paint.measureText(text1.text?.toString().orEmpty()).coerceAtLeast(1f)
            // Keep inter-copy gap small for a seamless continuous reading flow.
            val gap = 16f * resources.displayMetrics.density
            val distance = textWidth + gap
            lastDistance = distance
            val speedPxPerSec = (cachedSpeedDpPerSec.coerceAtLeast(8f)) * resources.displayMetrics.density
            val durationMs = ((distance / speedPxPerSec) * 1000f).toLong().coerceAtLeast(1L)
            val startFraction = if (preservePhase) {
                ((previousOffset % distance) / distance).coerceIn(0f, 1f)
            } else {
                0f
            }

            val updatePositions: (Float) -> Unit = { offset ->
                currentOffset = offset
                text1.translationX = -offset
                text2.translationX = distance - offset
            }
            updatePositions(distance * startFraction)

            animator = ValueAnimator.ofFloat(0f, distance).apply {
                duration = durationMs
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    updatePositions(va.animatedValue as Float)
                }
                start()
                currentPlayTime = (durationMs * startFraction).toLong().coerceIn(0L, durationMs)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && oldw > 0 && w != oldw) {
            // Width changes during startup/composition can otherwise cause visible jumps.
            restartAnimation(preservePhase = true)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
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

private fun formatTokenByPattern(token: String?, pattern: String?): String? {
    if (token == null) return null
    if (pattern.isNullOrBlank()) return token
    
    return try {
        // Simple numeric padding if pattern is like "000" or "00"
        if (pattern.all { it == '0' || it == '#' }) {
            val num = token.toIntOrNull()
            if (num != null) {
                val len = pattern.length
                return num.toString().padStart(len, '0')
            }
        }
        // If pattern contains prefix like "A-000", handle accordingly (e.g. up to 10 zeros)
        if (pattern.contains("0")) {
            val num = token.toIntOrNull()
            if (num != null) {
                val firstZeroIdx = pattern.indexOf('0')
                val lastZeroIdx = pattern.lastIndexOf('0')
                val prefix = pattern.substring(0, firstZeroIdx)
                val formatLen = lastZeroIdx - firstZeroIdx + 1
                return prefix + num.toString().padStart(formatLen, '0')
            }
        }
        token
    } catch (_: Exception) {
        token
    }
}
