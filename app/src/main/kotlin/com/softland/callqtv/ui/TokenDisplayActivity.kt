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
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.softland.callqtv.AppBackgroundCoordinator
import com.softland.callqtv.R
import com.softland.callqtv.utils.*
import com.softland.callqtv.viewmodel.MqttViewModel
import com.softland.callqtv.viewmodel.findCounterEntityForMqttRoute
import com.softland.callqtv.viewmodel.mqttRouteMatchesButtonIndex
import com.softland.callqtv.viewmodel.mqttRouteMatchesKeypadIndex
import com.softland.callqtv.viewmodel.TokenDisplayViewModel
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.utils.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
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
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val SPECIAL_MSG_PREFIX = "__MSG__:"
/** Multiline special counter messages: line height vs font size (reduces cramped / clipped lines). */
private const val SPECIAL_COUNTER_MSG_LINE_HEIGHT_EM = 1.42f
/** Fixed-protocol index-4 `D` (VIP / emergency): counter prefix when prefix mode is on. */
private const val VIP_EMERGENCY_COUNTER_PREFIX = "ER"

private fun encodeSpecialMessageToken(value: String): String = SPECIAL_MSG_PREFIX + value
private fun isSpecialMessageToken(value: String?): Boolean =
    value?.contains("__MSG__", ignoreCase = false) == true

private fun decodeSpecialMessageToken(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return when {
        raw.contains("__MSG__:") -> raw.substringAfter("__MSG__:").trim()
        raw.contains("__MSG__") -> raw.substringAfter("__MSG__").trimStart(':', '-', ' ')
        else -> raw
    }.ifBlank { null }
}

class TokenDisplayActivity : ComponentActivity() {

    private lateinit var viewModel: TokenDisplayViewModel
    private lateinit var mqttViewModel: MqttViewModel
    private var launchInstanceId: Long = 0L

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        StoragePermissionHelper.onRuntimePermissionResult(this)
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
        AppBackgroundCoordinator.registerTokenDisplaySession(mqttViewModel, viewModel)

        StoragePermissionHelper.ensureStorageAccess(this, storagePermissionLauncher)

        val refreshAfterApkUpgrade =
            com.softland.callqtv.utils.AppUpgradeCoordinator.consumePendingConfigRefresh(this)
        // Use cached config on startup; after an APK upgrade, refresh config once from the server.
        viewModel.loadData(
            mqttViewModel,
            forceShowOverlay = true,
            clearCacheBeforeFetch = refreshAfterApkUpgrade,
        )

        lifecycleScope.launch {
            val pendingUpdate = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.softland.callqtv.utils.AppUpdateChecker.checkForUpdate(this@TokenDisplayActivity)
            }
            if (pendingUpdate != null && !isFinishing && !isDestroyed) {
                startActivity(
                    Intent(this@TokenDisplayActivity, CustomerIdActivity::class.java).apply {
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_VERSION, pendingUpdate.apkVersion)
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_URL, pendingUpdate.downloadUrl)
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_MANDATORY, pendingUpdate.isMandatoryUpdate)
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_PROJECT, pendingUpdate.projectCode)
                    },
                )
            }
        }

        setContent {
            // Theme State - load async to avoid blocking main thread during composition
            val context = LocalContext.current

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
                ThemeColorManager.colorForMaterialPrimary(currentThemeHex)
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
                        appThemeHex = currentThemeHex,
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
        AppBackgroundCoordinator.unregisterTokenDisplaySession()
        android.util.Log.i(
            "TokenDisplayLaunch",
            "onDestroy instance=$launchInstanceId taskId=$taskId isFinishing=$isFinishing isDestroyed=$isDestroyed"
        )
        super.onDestroy()
        MediaEngine.shutdown()
        // Avoid cold TTS after transient activity teardown; full shutdown only when leaving the app.
        if (isFinishing) {
            TokenAnnouncer.shutdown()
        }
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

private const val MAX_CUSTOM_CHIME_STARTUP_MS = 1200L
private const val MAX_CUSTOM_CHIME_PLAYBACK_MS = 1500L

// Play a short built-in chime or a custom audio URL before announcing a token.
// Returns as soon as the cue starts; chime tail may overlap token TTS.
suspend fun playTokenChime(
    context: Context,
    soundKey: String,
    tokenAudioUrl: String? = null,
    counterAudioUrl: String? = null,
    onAudioStart: (() -> Unit)? = null
) {
    withContext(Dispatchers.Main.immediate) {
        // Use only one pre-announcement cue. Stacking multiple remote chimes can delay speech noticeably.
        val playedCustomCue = when {
            !counterAudioUrl.isNullOrBlank() -> playMediaUrl(context, counterAudioUrl, onAudioStart)
            !tokenAudioUrl.isNullOrBlank() -> playMediaUrl(context, tokenAudioUrl, onAudioStart)
            else -> false
        }

        if (!playedCustomCue) {
            playSystemTone(soundKey, onAudioStart)
        }
    }
}

private suspend fun playMediaUrl(
    context: Context,
    url: String,
    onAudioStart: (() -> Unit)? = null
): Boolean {
    return withContext(Dispatchers.IO) {
        val mediaPlayer = android.media.MediaPlayer()
        var playbackDelayMs = 250L
        var releaseAfterPlayback = false
        val releasePlayer: (android.media.MediaPlayer) -> Unit = { mp ->
            runCatching {
                mp.setOnPreparedListener(null)
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        try {
            mediaPlayer.setDataSource(context, Uri.parse(url))
            mediaPlayer.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )

            val started = withTimeoutOrNull(MAX_CUSTOM_CHIME_STARTUP_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    mediaPlayer.setOnPreparedListener { preparedPlayer ->
                        playbackDelayMs = preparedPlayer.duration
                            .takeIf { it > 0 }
                            ?.toLong()
                            ?.coerceAtMost(MAX_CUSTOM_CHIME_PLAYBACK_MS)
                            ?: 250L
                        try {
                            preparedPlayer.setOnCompletionListener { releasePlayer(it) }
                            preparedPlayer.setOnErrorListener { mp, _, _ ->
                                releasePlayer(mp)
                                true
                            }
                            preparedPlayer.start()
                            releaseAfterPlayback = true
                            onAudioStart?.invoke()
                            if (continuation.isActive) continuation.resume(true)
                        } catch (e: Exception) {
                            Log.e("TokenChime", "Failed starting chime URL: $url", e)
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                    mediaPlayer.setOnErrorListener { _, what, extra ->
                        Log.w("TokenChime", "Error preparing chime URL: $url what=$what extra=$extra")
                        if (continuation.isActive) continuation.resume(false)
                        true
                    }
                    mediaPlayer.prepareAsync()
                }
            } ?: false

            if (!started) {
                Log.w("TokenChime", "Timed out starting chime URL: $url")
                return@withContext false
            }

            true
        } catch (e: Exception) {
            Log.e("TokenChime", "Error playing chime URL: $url", e)
            false
        } finally {
            if (!releaseAfterPlayback) {
                releasePlayer(mediaPlayer)
            }
        }
    }
}

private fun playSystemTone(soundKey: String, onAudioStart: (() -> Unit)? = null) {
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
        onAudioStart?.invoke()
    } catch (_: Exception) {
        onAudioStart?.invoke()
        toneGen.release()
        return
    }
    Handler(Looper.getMainLooper()).postDelayed(
        { runCatching { toneGen.release() } },
        durationMs.toLong() + 50L,
    )
}

@Composable
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
    LaunchedEffect(isLoading) {
        if (isLoading) blinkTriggers.clear()
    }
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

    // TTS when token announcement is enabled — warm as soon as config is known (do not wait for
    // the config API overlay to finish; first MQTT token often arrives right after connect).
    LaunchedEffect(config?.enableTokenAnnouncement, config?.audioLanguage) {
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
        TokenAnnouncer.warmUp(context, lang, performPoke = true) {
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
                    val counterIdOrName = pair.counter
                    val tokenLabel = pair.token
                    val sourcePayload = pair.payload

                    val buttonIndexKey = counterIdOrName.trim()
                    val actualCounter = findCounterEntityForMqttRoute(currentCounters, buttonIndexKey)
                    if (actualCounter == null) {
                        android.util.Log.d(
                            "TokenDisplay",
                            "Dropping token '$tokenLabel' — no counter with button_index '$buttonIndexKey'"
                        )
                        return@launch
                    }

                    val storageKey = actualCounter.buttonIndex?.toString()?.trim()?.takeIf { it.isNotBlank() }
                        ?: buttonIndexKey
                    val keypadFallback = actualCounter.keypadIndex?.trim()?.takeIf {
                        it.isNotBlank() && it != storageKey
                    }

                    announcementMutex.withLock {
                        val tokenOutcome =
                            mqttViewModel.processTokenUpdateForKeys(
                                storageKey,
                                tokenLabel,
                                fallbackKey = keypadFallback,
                                publishImmediately = false,
                                isVipEmergency = pair.isVipEmergency,
                            )
                        if (!tokenOutcome.playCueUi) return@withLock

                        val willAnnounce =
                            currentConfig?.enableTokenAnnouncement == true &&
                                tokenOutcome.speakTokenAnnouncement
                        val ttsWarmDeferred = if (willAnnounce) {
                            async {
                                TokenAnnouncer.awaitReady(
                                    context,
                                    currentConfig!!.audioLanguage,
                                    performPoke = true,
                                )
                            }
                        } else {
                            null
                        }

                        val publishedAtCueStart = java.util.concurrent.atomic.AtomicBoolean(false)
                        val publishVisualStateAtCueStart = publish@{
                            if (!publishedAtCueStart.compareAndSet(false, true)) return@publish
                            mqttViewModel.publishTokensSnapshot()
                            mqttViewModel.markAsAnnounced(storageKey, tokenLabel)
                            mqttViewModel.markPayloadDisplayed(sourcePayload)
                            UiPerfProbe.markTokenUiUpdated(storageKey, tokenLabel)
                            Handler(Looper.getMainLooper()).post {
                                blinkTriggers[storageKey] = System.currentTimeMillis()
                            }
                        }

                        val soundKey = ThemeColorManager.getNotificationSoundKey(context)
                        launch(Dispatchers.Main.immediate) {
                            playTokenChime(
                                context = context,
                                soundKey = soundKey,
                                tokenAudioUrl = currentConfig?.tokenAudioUrl,
                                counterAudioUrl = actualCounter.audioUrl,
                                onAudioStart = publishVisualStateAtCueStart,
                            )
                        }
                        publishVisualStateAtCueStart()

                        if (willAnnounce) {
                            ttsWarmDeferred?.await()
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
                            val effectiveSpeechCounterCode = when {
                                pair.isVipEmergency && usePrefix -> VIP_EMERGENCY_COUNTER_PREFIX
                                else -> counterCode
                            }
                            // When prefix is enabled (same as on-screen "CODE-token"), spell the code for TTS.
                            val spokenPrefix =
                                if (usePrefix && effectiveSpeechCounterCode.isNotBlank()) {
                                    effectiveSpeechCounterCode.toCharArray().joinToString(" ")
                                } else {
                                    ""
                                }

                            val isSpecial = isSpecialMessageToken(tokenLabel)
                            val decodedSpecial = decodeSpecialMessageToken(tokenLabel)

                            runWithAdvertisementAudioDuckedForSpeech(context) { skipPrime, restore ->
                                withTimeoutOrNull(6000) {
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
                                                audioLanguage = currentConfig.audioLanguage,
                                                message = spokenAnnouncement,
                                                skipSynthesisPrime = skipPrime,
                                                onDone = {
                                                    restore()
                                                    if (continuation.isActive) continuation.resume(Unit)
                                                },
                                            )
                                        } else {
                                            val tokenText = TokenAnnouncer.sanitizeTokenLabelForSpeech(tokenLabel)
                                            TokenAnnouncer.announceTokenCall(
                                                context = context,
                                                audioLanguage = currentConfig.audioLanguage,
                                                spelledCounterPrefix = if (usePrefix) spokenPrefix else "",
                                                tokenText = tokenText,
                                                counterDisplayName = announcementCounterName,
                                                skipSynthesisPrime = skipPrime,
                                                onDone = {
                                                    restore()
                                                    if (continuation.isActive) continuation.resume(Unit)
                                                },
                                            )
                                        }
                                    }
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
                            "Special token '$tokenLabel' — no counter for button_index '$buttonIndexKey'; still reporting MQTT to server"
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
                        val specialToken = encodeSpecialMessageToken(tokenLabel)
                        val replaced = mqttViewModel.replaceTokenForKeys(
                            storageKey,
                            specialToken,
                            fallbackKey = keypadFallback,
                            publishImmediately = false
                        )
                        if (!replaced) return@withLock

                        val willAnnounce = currentConfig?.enableTokenAnnouncement == true
                        val ttsWarmDeferred = if (willAnnounce) {
                            async {
                                TokenAnnouncer.awaitReady(
                                    context,
                                    currentConfig!!.audioLanguage,
                                    performPoke = true,
                                )
                            }
                        } else {
                            null
                        }

                        val publishedAtCueStart = java.util.concurrent.atomic.AtomicBoolean(false)
                        val publishVisualStateAtCueStart = publish@{
                            if (!publishedAtCueStart.compareAndSet(false, true)) return@publish
                            mqttViewModel.publishTokensSnapshot()
                            mqttViewModel.markAsAnnounced(storageKey, tokenLabel)
                            mqttViewModel.markPayloadDisplayed(sourcePayload)
                            UiPerfProbe.markTokenUiUpdated(storageKey, tokenLabel)
                            Handler(Looper.getMainLooper()).post {
                                blinkTriggers[storageKey] = System.currentTimeMillis()
                            }
                        }

                        val soundKey = ThemeColorManager.getNotificationSoundKey(context)
                        launch(Dispatchers.Main.immediate) {
                            playTokenChime(
                                context = context,
                                soundKey = soundKey,
                                tokenAudioUrl = currentConfig?.tokenAudioUrl,
                                counterAudioUrl = actualCounter.audioUrl,
                                onAudioStart = publishVisualStateAtCueStart,
                            )
                        }
                        publishVisualStateAtCueStart()

                        if (willAnnounce) {
                            ttsWarmDeferred?.await()
                            val displayName =
                                (actualCounter.name?.takeIf { it.isNotBlank() }
                                    ?: actualCounter.defaultName?.takeIf { it.isNotBlank() }
                                    ?: "Counter ${actualCounter.buttonIndex}")

                            val announcementCounterName =
                                if (currentConfig.enableCounterAnnouncement == true) displayName else ""

                            // For index-4='C' special message, announce message without prefix.
                            val specialAnnouncement = buildString {
                                append(tokenLabel)
                                if (announcementCounterName.isNotBlank()) {
                                    append(' ')
                                    append(announcementCounterName)
                                }
                            }

                            runWithAdvertisementAudioDuckedForSpeech(context) { skipPrime, restore ->
                                withTimeoutOrNull(6000) {
                                    suspendCancellableCoroutine<Unit> { continuation ->
                                        continuation.invokeOnCancellation { restore() }
                                        TokenAnnouncer.announceMessage(
                                            context = context,
                                            audioLanguage = currentConfig.audioLanguage,
                                            message = specialAnnouncement,
                                            skipSynthesisPrime = skipPrime,
                                            onDone = {
                                                restore()
                                                if (continuation.isActive) continuation.resume(Unit)
                                            }
                                        )
                                    }
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
                    appThemeHex = appThemeHex,
                    onThemeChange = onThemeChange,
                    onCounterBgChange = onCounterBgChange,
                    onTokenBgChange = onTokenBgChange,
                    onRefresh = { viewModel.loadData(mqttViewModel, forceShowOverlay = true, clearCacheBeforeFetch = true) },
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
                Button(onClick = { viewModel.loadData(mqttViewModel, clearCacheBeforeFetch = true) }) {
                    Text("Retry")
                }
            }
        )
    } else if (isLicenseExpired) {
        LicenseExpiredDialog(
            macAddress = macAddress,
            errorMessage = errorMessage,
            onRefresh = { viewModel.loadData(mqttViewModel, forceShowOverlay = true, clearCacheBeforeFetch = true) }
        )
    } else if (config == null) {
        TvConfigurationUnavailableScreen(
            macAddress = macAddress,
            appVersion = appVersion,
            isNetworkAvailable = isNetworkAvailable,
            errorMessage = errorMessage,
            onRetry = { viewModel.loadData(mqttViewModel, forceShowOverlay = true, clearCacheBeforeFetch = true) },
        )
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
    appThemeHex: String,
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
    val vipTopTokenByKey by mqttViewModel.getVipEmergencyTopTokenByKey().observeAsState(emptyMap())
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

    var tokenBlinkMode by remember {
        mutableStateOf(ThemeColorManager.getTokenBlinkMode(context))
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
    
    val countersToDisplay = remember(
        counters,
        config.noOfCounters,
        config.layoutType,
        config.displayRows,
        config.displayColumns
    ) {
        resolveCountersToDisplay(counters, config)
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
                viewModel.loadData(mqttViewModel, forceShowOverlay = true, clearCacheBeforeFetch = true)
            },
            tokenBlinkMode = tokenBlinkMode,
            onTokenBlinkModeChange = { tokenBlinkMode = it },
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
                    tokenBlinkMode = tokenBlinkMode,
                    vipTopTokenByKey = vipTopTokenByKey,
                    showReconnectBadge = false,
                    reconnectRetryAttempt = reconnectRetryAttempt,
                    reconnectUiSeconds = reconnectUiSeconds,
                )

                TokenDisplayFooter(
                    config = config,
                    responsivePadding = responsivePadding,
                    scale = scale,
                    deviceIsPortrait = deviceIsPortrait,
                    appThemeHex = appThemeHex,
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
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    vipTopTokenByKey: Map<String, String> = emptyMap(),
    showReconnectBadge: Boolean,
    reconnectRetryAttempt: Int,
    reconnectUiSeconds: Int
) {
    val showAds = config.showAds?.equals("on", ignoreCase = true) == true
    val hasAds = showAds && adFiles.isNotEmpty()
    val baseLayoutType = when (config.layoutType?.trim()?.lowercase()) {
        null, "", "default" -> "1"
        else -> config.layoutType!!.trim()
    }
    val layoutType = if (usePortraitLayout) "2" else baseLayoutType
    val counterCount = countersToDisplay.size
    val configuredCounterLimit = if (config.layoutType.equals("full", ignoreCase = true)) {
        counterCount
    } else {
        config.noOfCounters ?: counterCount
    }
    val adWeight = remember(showAds, configuredCounterLimit) {
        if (!showAds) 0f else if (configuredCounterLimit <= 2) 0.5f else 0.4f
    }
    val countersWeight = remember(adWeight) { if (adWeight > 0f) 1f - adWeight else 1f }
    val adAreaContent: @Composable () -> Unit = {
        key(adAreaReloadToken) {
            AdArea(adFiles, config, counterBgHex)
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
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipTopTokenByKey = vipTopTokenByKey)
                            }
                        } else {
                            Box(modifier = Modifier.weight(countersWeight).fillMaxWidth()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipTopTokenByKey = vipTopTokenByKey)
                            }
                            Box(modifier = Modifier.weight(adWeight).fillMaxWidth().clipToBounds()) { adAreaContent() }
                        }
                    }
                } else {
                    // Default Left/Right (Row) for Portrait if not specified as Top/Bottom
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isRight) {
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipTopTokenByKey = vipTopTokenByKey)
                            }
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                        } else {
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipTopTokenByKey = vipTopTokenByKey)
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
                    blinkTriggers = blinkTriggers,
                    tokenBlinkMode = tokenBlinkMode,
                    vipTopTokenByKey = vipTopTokenByKey,
                )
            }
        } else {
            if (hasAds) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (adPlacement.equals("left", ignoreCase = true)) {
                        Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                        Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                            CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipTopTokenByKey = vipTopTokenByKey)
                        }
                    } else {
                        Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                            CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipTopTokenByKey = vipTopTokenByKey)
                        }
                        Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                    }
                }
            } else {
                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipTopTokenByKey = vipTopTokenByKey)
            }
        }

    }
}

@Composable
private fun TokenDisplayFooter(
    config: TvConfigEntity,
    responsivePadding: androidx.compose.ui.unit.Dp,
    scale: Float,
    deviceIsPortrait: Boolean,
    appThemeHex: String,
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
            isPortrait = deviceIsPortrait,
            appThemeHex = appThemeHex,
            scrollTextColorHex = config.scrollTextColor,
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
    onClearTokenHistoryAndRefresh: () -> Unit,
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    onTokenBlinkModeChange: (TokenBlinkMode) -> Unit = {},
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
            companyName = companyName,
            tokenBlinkMode = tokenBlinkMode,
            onTokenBlinkModeChange = onTokenBlinkModeChange,
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
fun AdArea(adFiles: List<AdFileEntity>, config: TvConfigEntity, counterBgHex: String) {
    val orderedAds = remember(adFiles) { adFiles.sortedBy { it.position } }
    val adAreaBgBrush = remember(counterBgHex) { ThemeColorManager.getBackgroundBrush(counterBgHex) }
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
    // Single ExoPlayer + shared TextureView (alternating two decoders caused frozen frames with audio).
    val exoPlayerSlot = 0
    val mediaTypeCache = remember { mutableStateMapOf<String, AdMediaType>() }
    val sharedYoutubeWebView = remember(context) {
        buildBaseYoutubeWebView(context)
    }
    val sharedAdTextureView = remember(context) {
        buildSharedAdTextureView(context)
    }

    // Off-screen Exo preload uses a second MediaCodec and often hangs on TV SoCs with HQ files.

    DisposableEffect(sharedYoutubeWebView) {
        TokenAnnouncementAdAudio.setYoutubeWebView(sharedYoutubeWebView)
        onDispose {
            TokenAnnouncementAdAudio.setYoutubeWebView(null)
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

    fun fastInferMediaType(path: String): AdMediaType {
        val lc = path.lowercase()
        return when {
            isValidYoutubeVideoIdForEmbed(path.trim()) ||
                lc.contains("youtube.com") ||
                lc.contains("youtu.be") ||
                lc.contains("youtube-nocookie.com") -> AdMediaType.YouTube
            isAdVideo(path) -> AdMediaType.Video
            isAdImage(path) -> AdMediaType.Image
            isServerHostedAdMediaPath(path) && !isLikelyHtmlWebPage(path) -> AdMediaType.Video
            lc.startsWith("http://") || lc.startsWith("https://") -> AdMediaType.Web
            else -> AdMediaType.Image
        }
    }

    fun visibleReadyTimeoutFor(ad: AdFileEntity): Long {
        val type = mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
        return when (type) {
            AdMediaType.Video, AdMediaType.YouTube -> 45_000L
            AdMediaType.Web -> if (isLikelyLiveStreamUrl(ad.filePath)) 90_000L else 30_000L
            else -> 25_000L
        }
    }

    fun applyVisibleAd(ad: AdFileEntity, idx: Int, reason: String) {
        Log.i(
            "AdLoop",
            "applyVisibleAd reason=$reason idx=$idx url=${ad.filePath.take(90)}"
        )
        visibleAd = ad
        visibleAdIdx = idx.coerceAtLeast(0)
        visibleAdReady = false
        visibleReplayToken += 1
        candidateAd = null
        candidateAdIdx = -1
        candidateLoadToken += 1
    }

    /** Immediately advance away from a failed or unsupported visible ad (does not wait for preload). */
    fun skipVisibleAd(failedAd: AdFileEntity?) {
        if (orderedAds.isEmpty()) return
        if (failedAd != null) {
            val stillSame = visibleAd?.id == failedAd.id &&
                visibleAd?.position == failedAd.position
            if (!stillSame) return
        }
        val fromIdx = failedAd?.let { ad ->
            orderedAds.indexOfFirst { it.id == ad.id && it.position == ad.position }
                .takeIf { it >= 0 }
        } ?: visibleAdIdx.coerceIn(0, orderedAds.lastIndex.coerceAtLeast(0))

        if (orderedAds.size == 1) {
            visibleAdReady = false
            candidateAd = null
            candidateAdIdx = -1
            candidateLoadToken += 1
            visibleReplayToken += 1
            Log.w(
                "AdLoop",
                "Single ad unplayable/failed; remounting player token=$visibleReplayToken path=${orderedAds[0].filePath.take(90)}"
            )
            return
        }

        val nextIdx = (fromIdx + 1) % orderedAds.size
        applyVisibleAd(orderedAds[nextIdx], nextIdx, "skip_failed")
    }

    fun promoteCandidateAfterHold(preloadTokenSnapshot: Int, nextAd: AdFileEntity, nextIdx: Int) {
        adSwitchScope.launch {
            delay(transitionHoldMs)
            if (preloadTokenSnapshot != candidateLoadToken) return@launch
            candidateAd = null
            candidateAdIdx = -1
            // Let preload composables dispose before the shared TextureView is reattached.
            delay(48)
            if (preloadTokenSnapshot != candidateLoadToken) return@launch
            applyVisibleAd(nextAd, nextIdx, "preload_ready")
        }
    }

    /** Hands off to the next ad after a short hold (keeps last frame visible during Exo/WebView teardown). */
    fun scheduleExoOrYoutubeHandoff(nextAd: AdFileEntity, nextIdx: Int, reason: String) {
        val token = candidateLoadToken
        adSwitchScope.launch {
            delay(transitionHoldMs)
            if (token != candidateLoadToken) return@launch
            if (candidateAd?.id != nextAd.id || candidateAd?.position != nextAd.position) return@launch
            candidateAd = null
            candidateAdIdx = -1
            delay(48)
            if (token != candidateLoadToken) return@launch
            applyVisibleAd(nextAd, nextIdx, reason)
        }
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
        candidateLoadToken += 1
        candidateAd = next
        candidateAdIdx = nextIdx
        val nextType = mediaTypeCache[next.filePath] ?: fastInferMediaType(next.filePath)
        when {
            nextType == AdMediaType.YouTube -> {
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_youtube")
            }
            usesExoTexturePlayback(nextType, next.filePath) -> {
                // Exo preload was disabled on TV SoCs; advance explicitly so the playlist never stalls.
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_exo")
            }
            // Image / static Web: off-screen preload composable calls promoteCandidateAfterHold on ready.
        }
    }

    fun triggerNextFrom(ad: AdFileEntity, expectedCurrentAd: AdFileEntity? = null) {
        if (orderedAds.isEmpty()) return
        if (expectedCurrentAd != null) {
            val stillSame = visibleAd?.id == expectedCurrentAd.id &&
                visibleAd?.position == expectedCurrentAd.position
            if (!stillSame) return
        }
        val fromIdx = orderedAds.indexOfFirst { it.id == ad.id && it.position == ad.position }
            .takeIf { it >= 0 } ?: visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
        val nextIdx = (fromIdx + 1) % orderedAds.size
        val next = orderedAds[nextIdx]
        candidateLoadToken += 1
        candidateAd = next
        candidateAdIdx = nextIdx
        val nextType = mediaTypeCache[next.filePath] ?: fastInferMediaType(next.filePath)
        when {
            nextType == AdMediaType.YouTube -> {
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_from_youtube")
            }
            usesExoTexturePlayback(nextType, next.filePath) -> {
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_from_exo")
            }
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

    // Watchdog: if a visible ad never becomes ready, skip to the next item.
    LaunchedEffect(visibleAd?.id, visibleAd?.position) {
        visibleAdReady = false
    }
    LaunchedEffect(visibleAd?.id, visibleAd?.position, visibleAdReady, visibleReplayToken) {
        val current = visibleAd ?: return@LaunchedEffect
        if (visibleAdReady) return@LaunchedEffect
        val timeoutMs = visibleReadyTimeoutFor(current)
        delay(timeoutMs)
        val stillSame = visibleAd?.id == current.id && visibleAd?.position == current.position
        if (stillSame && !visibleAdReady) {
            Log.w(
                "AdLoop",
                "Visible ad not ready within ${timeoutMs / 1000}s; skipping ${current.filePath.take(120)}"
            )
            skipVisibleAd(current)
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .focusProperties { canFocus = false }
            .background(adAreaBgBrush)
            .padding(4.dp),
    ) {
        val viewportPx = remember(maxWidth, maxHeight, density) {
            AdViewportSizing.fromConstraints(
                with(density) { maxWidth.roundToPx() },
                with(density) { maxHeight.roundToPx() },
                context,
            )
        }
        LaunchedEffect(viewportPx.width, viewportPx.height) {
            AdViewportSizing.lastViewport = viewportPx
            delay(120)
            MediaEngine.updateViewport(context, viewportPx.width, viewportPx.height)
        }
        LaunchedEffect(orderedAds, viewportPx) {
            if (!viewportPx.isValid()) return@LaunchedEffect
            val loader = context.imageLoader
            orderedAds.forEach { ad ->
                val type = mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
                if (type == AdMediaType.Image) {
                    loader.enqueue(
                        AdViewportSizing.coilImageRequestBuilder(context, ad.filePath, viewportPx).build()
                    )
                }
            }
        }
        LaunchedEffect(candidateAd?.id, candidateAd?.filePath, viewportPx) {
            val ad = candidateAd ?: return@LaunchedEffect
            if (!viewportPx.isValid()) return@LaunchedEffect
            val type = mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
            if (type == AdMediaType.Image) {
                context.imageLoader.enqueue(
                    AdViewportSizing.coilImageRequestBuilder(context, ad.filePath, viewportPx).build()
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        if (orderedAds.isEmpty()) {
            Text("No Ads", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val visibleAdSnapshot = visibleAd
            val preloadAd = candidateAd
            val visibleType = visibleAdSnapshot?.let { ad ->
                mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
            }
            val preloadType = preloadAd?.let { ad ->
                mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
            }
            val showVideoSurface =
                (visibleAdSnapshot != null &&
                    usesExoTexturePlayback(visibleType!!, visibleAdSnapshot.filePath)) ||
                (preloadAd != null && preloadType != null &&
                    usesExoTexturePlayback(preloadType, preloadAd.filePath))

            Box(Modifier.fillMaxSize()) {
                var sharedSurfaceGeneration by remember { mutableIntStateOf(0) }
                if (showVideoSurface) {
                    AdTextureSurfaceHost(
                        textureView = sharedAdTextureView,
                        onSurfaceReady = { sharedSurfaceGeneration += 1 },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (visibleAdSnapshot != null && visibleType != null) {
                    key(visibleAdSnapshot.id, visibleAdSnapshot.position, visibleReplayToken) {
                        Log.i(
                            "AdClassifier",
                            "VISIBLE type=$visibleType url=${visibleAdSnapshot.filePath.take(140)}"
                        )
                        AdUnifiedPlayer(
                            ad = visibleAdSnapshot,
                            mediaType = visibleType,
                            allowYoutubeAds = allowYoutubeAds,
                            viewport = viewportPx,
                            sharedYoutubeWebView = sharedYoutubeWebView,
                            sharedVideoTextureView = if (usesExoTexturePlayback(visibleType, visibleAdSnapshot.filePath)) {
                                sharedAdTextureView
                            } else {
                                null
                            },
                            activePlayerIdx = exoPlayerSlot,
                            videoAttachToTexture = true,
                            videoSurfaceReadySignal = sharedSurfaceGeneration,
                            onReady = {
                                visibleAdReady = true
                                UiPerfProbe.logAdEvent("READY", visibleAdSnapshot.filePath)
                            },
                            onEnded = {
                                UiPerfProbe.logAdEvent("ENDED", visibleAdSnapshot.filePath)
                                triggerNext(expectedCurrentAd = visibleAdSnapshot)
                                if (orderedAds.size <= 1) {
                                    visibleReplayToken += 1
                                }
                            },
                            onError = {
                                UiPerfProbe.logAdEvent("ERROR", visibleAdSnapshot.filePath)
                                skipVisibleAd(visibleAdSnapshot)
                            }
                        )
                    }
                }

                // Preload next item off-screen; video uses headless ExoPlayer (no second TextureView).
                if (preloadAd != null && preloadType != null &&
                    (visibleAd == null ||
                        preloadAd.filePath != visibleAd?.filePath ||
                        preloadAd.id != visibleAd?.id)
                ) {
                    val preloadTokenSnapshot = candidateLoadToken
                    val preloadUsesExo = usesExoTexturePlayback(preloadType, preloadAd.filePath)
                    if (preloadType == AdMediaType.YouTube) {
                        // Shared YouTube WebView cannot dual-buffer; triggerNext handles handoff.
                    } else if (preloadUsesExo) {
                        // Skip second Exo decoder preload — prevents hangs and grainy reconfigure on MTK TVs.
                        Log.d("AdLoop", "Skipping off-screen Exo preload for ${preloadAd.filePath.take(90)}")
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.01f)
                                .clipToBounds()
                        ) {
                            Log.i(
                                "AdClassifier",
                                "PRELOAD type=$preloadType url=${preloadAd.filePath.take(140)}"
                            )
                            AdUnifiedPlayer(
                                ad = preloadAd,
                                mediaType = preloadType,
                                allowYoutubeAds = allowYoutubeAds,
                                viewport = viewportPx,
                                sharedYoutubeWebView = sharedYoutubeWebView,
                                sharedVideoTextureView = null,
                                activePlayerIdx = exoPlayerSlot,
                                videoAttachToTexture = true,
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
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun AdTextureSurfaceHost(
    textureView: TextureView,
    onSurfaceReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = {
            detachViewFromParent(textureView)
            bindAdTextureSurfaceOnly(
                texture = textureView,
                onSurfaceReady = onSurfaceReady,
            )
            textureView
        },
        update = { view ->
            detachViewFromParent(view)
            bindAdTextureSurfaceOnly(texture = view, onSurfaceReady = onSurfaceReady)
        },
        modifier = modifier.focusProperties { canFocus = false }
    )
}

private fun usesExoTexturePlayback(type: AdMediaType, path: String): Boolean {
    return type == AdMediaType.Video ||
        (type == AdMediaType.Web && isLikelyLiveStreamUrl(path))
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
        isServerHostedAdMediaPath(path) && !isLikelyHtmlWebPage(path) -> AdMediaType.Video
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
                        view?.let { handleYouTubeRestrictionToken(it, token) }
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


/** Stops playback and clears the queue so the next ad can bind a fresh decoder (avoids MTK NO_MEMORY). */
private fun releaseExoVideoDecoder(player: ExoPlayer, texture: TextureView? = null) {
    try {
        player.playWhenReady = false
        player.stop()
        if (texture != null) {
            player.clearVideoTextureView(texture)
        }
        player.clearMediaItems()
    } catch (_: Exception) {
    }
}

private fun prepareAdVideoOnPlayer(
    player: ExoPlayer,
    texture: TextureView?,
    attachToTexture: Boolean,
    mediaItem: MediaItem,
    videoUrl: String,
    isLowBandwidth: Boolean,
    context: Context,
    viewport: AdViewportPx,
) {
    if (viewport.isValid()) {
        AdViewportSizing.lastViewport = viewport
    }
    if (attachToTexture && texture != null) {
        MediaEngine.detachTextureFromAllPlayers(texture)
        texture.setTransform(null)
        player.setVideoTextureView(texture)
    }
    applyAdaptiveVideoTrackCap(
        player = player,
        videoUrl = videoUrl,
        isLowBandwidth = isLowBandwidth,
        context = context,
        viewport = viewport,
    )
    player.setMediaItem(mediaItem)
    player.prepare()
    player.playWhenReady = true
}

@Composable
fun AdVideoPlayer(
    videoUrl: String,
    player: ExoPlayer,
    viewport: AdViewportPx = AdViewportSizing.lastViewport,
    sharedTextureView: TextureView? = null,
    attachToTexture: Boolean = true,
    videoSurfaceReadySignal: Int = 0,
    onVideoEnded: () -> Unit,
    onReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val externallyHosted = sharedTextureView != null && attachToTexture
    val videoTextureRef = remember(sharedTextureView, attachToTexture) {
        mutableStateOf(if (externallyHosted) sharedTextureView else null)
    }
    var textureSurfaceGeneration by remember { mutableIntStateOf(0) }
    var latestVideoSize by remember { mutableStateOf(androidx.media3.common.VideoSize.UNKNOWN) }

    var readyReported by remember(videoUrl) { mutableStateOf(false) }
    var terminalEventReported by remember(videoUrl) { mutableStateOf(false) }
    var loadStartedAtMs by remember(videoUrl) { mutableStateOf(0L) }
    var lastSurfaceFrameMs by remember(videoUrl) { mutableLongStateOf(0L) }
    var surfaceRecoveryAttempts by remember(videoUrl) { mutableIntStateOf(0) }
    val isLowBandwidth = remember(videoUrl) { isAdLowBandwidthNetwork(context) }

    DisposableEffect(videoUrl, player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        Log.i(
                            "AdVideoPerf",
                            "STATE_READY in ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${videoUrl.take(120)}"
                        )
                        if (!readyReported) {
                            readyReported = true
                            mainHandler.post { latestOnReady() }
                        }
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
                lastSurfaceFrameMs = System.currentTimeMillis()
                videoTextureRef.value?.post {
                    applyFitCenterTransform(videoTextureRef.value, videoSize)
                }
            }

            override fun onRenderedFirstFrame() {
                lastSurfaceFrameMs = System.currentTimeMillis()
                if (!readyReported) {
                    readyReported = true
                    Log.i(
                        "AdVideoPerf",
                        "FIRST_FRAME in ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${videoUrl.take(120)}"
                    )
                    mainHandler.post { latestOnReady() }
                }
            }
        }
        
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Pause only — clearing the decoder on every Compose dispose caused black frames
            // when the shared TextureView was rebound (prepare ran before surface was ready).
            try {
                val pauseOnly: () -> Unit = {
                    player.playWhenReady = false
                    player.pause()
                }
                val detachTexture: () -> Unit = {
                    videoTextureRef.value?.let { texture ->
                        player.clearVideoTextureView(texture)
                        if (!externallyHosted) {
                            detachViewFromParent(texture)
                            videoTextureRef.value = null
                        }
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    pauseOnly()
                    detachTexture()
                } else {
                    mainHandler.post {
                        try {
                            pauseOnly()
                            detachTexture()
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(viewport.width, viewport.height, latestVideoSize) {
        videoTextureRef.value?.post {
            applyFitCenterTransform(videoTextureRef.value, latestVideoSize)
        }
    }

    LaunchedEffect(videoUrl, player, textureSurfaceGeneration, attachToTexture, externallyHosted, videoSurfaceReadySignal) {
        readyReported = false
        terminalEventReported = false
        loadStartedAtMs = System.currentTimeMillis()
        lastSurfaceFrameMs = 0L
        surfaceRecoveryAttempts = 0
        val loadUrl = videoUrl
        val texture = when {
            !attachToTexture -> null
            externallyHosted -> sharedTextureView
            else -> videoTextureRef.value
        }
        if (attachToTexture && (texture == null || !texture.isAvailable)) {
            return@LaunchedEffect
        }
        val mediaItem = try {
            withContext(Dispatchers.Default) {
                buildAdVideoMediaItem(loadUrl)
            }
        } catch (e: Exception) {
            Log.e("AdVideoPlayer", "Failed to create media item: $loadUrl", e)
            null
        }
        if (mediaItem == null) {
            terminalEventReported = true
            latestOnError()
            return@LaunchedEffect
        }
        try {
            val canHandoffFromEnded =
                attachToTexture &&
                    texture != null &&
                    player.playbackState == Player.STATE_ENDED
            if (canHandoffFromEnded) {
                // Reuse the same decoder/surface for the next clip to reduce black frames between ads.
                if (viewport.isValid()) {
                    AdViewportSizing.lastViewport = viewport
                }
                MediaEngine.detachTextureFromAllPlayers(texture)
                texture.setTransform(null)
                player.setVideoTextureView(texture)
                applyAdaptiveVideoTrackCap(
                    player = player,
                    videoUrl = loadUrl,
                    isLowBandwidth = isLowBandwidth,
                    context = context,
                    viewport = viewport,
                )
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            } else {
                releaseExoVideoDecoder(player, texture)
                prepareAdVideoOnPlayer(
                    player = player,
                    texture = texture,
                    attachToTexture = attachToTexture,
                    mediaItem = mediaItem,
                    videoUrl = loadUrl,
                    isLowBandwidth = isLowBandwidth,
                    context = context,
                    viewport = viewport,
                )
            }
            Log.i(
                "AdVideoPerf",
                "Prepared ${if (attachToTexture) "with" else "without"} surface url=${loadUrl.take(120)}"
            )
        } catch (e: Exception) {
            Log.e("AdVideoPlayer", "Failed to prepare video: $loadUrl", e)
            terminalEventReported = true
            latestOnError()
        }
    }

    // Detect frozen TextureView (audio continues but last frame sticks).
    LaunchedEffect(videoUrl, player, attachToTexture) {
        if (!attachToTexture) return@LaunchedEffect
        while (true) {
            delay(2_000L)
            if (terminalEventReported) return@LaunchedEffect
            if (!player.isPlaying || player.playbackState != Player.STATE_READY) continue
            val lastFrame = lastSurfaceFrameMs
            if (lastFrame <= 0L) continue
            val staleMs = System.currentTimeMillis() - lastFrame
            if (staleMs < 3_500L) continue

            val texture = videoTextureRef.value ?: sharedTextureView
            if (surfaceRecoveryAttempts < 2) {
                surfaceRecoveryAttempts += 1
                Log.w(
                    "AdVideoPlayer",
                    "Video surface stale ${staleMs}ms; rebinding surface (attempt $surfaceRecoveryAttempts) url=${videoUrl.take(90)}",
                )
                mainHandler.post {
                    try {
                        texture?.setTransform(null)
                        if (texture != null) {
                            MediaEngine.detachTextureFromAllPlayers(texture)
                            player.setVideoTextureView(texture)
                        }
                        val pos = player.currentPosition.coerceAtLeast(1L)
                        player.seekTo(pos)
                        player.playWhenReady = true
                        lastSurfaceFrameMs = System.currentTimeMillis()
                    } catch (e: Exception) {
                        Log.w("AdVideoPlayer", "Surface recovery failed: ${e.message}")
                    }
                }
            } else {
                Log.e(
                    "AdVideoPlayer",
                    "Video surface frozen after recovery; skipping url=${videoUrl.take(90)}",
                )
                terminalEventReported = true
                latestOnError()
                return@LaunchedEffect
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
            isLowBandwidth -> 45_000L
            else -> 35_000L
        }
        delay(timeoutMs)
        if (!readyReported && !terminalEventReported) {
            Log.w("AdVideoPlayer", "Video not ready within timeout; skipping. url=$videoUrl")
            FileLogger.logError(context, "AdVideoPlayer", "Video not ready within timeout; skipping. url=$videoUrl")
            terminalEventReported = true
            latestOnError()
        }
    }

    if (externallyHosted || !attachToTexture) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false }
        )
    } else {
        AndroidView(
            factory = {
                val texture = buildSharedAdTextureView(context)
                detachViewFromParent(texture)
                bindAdVideoTextureView(
                    texture = texture,
                    player = player,
                    onSurfaceReady = { textureSurfaceGeneration += 1 },
                    onLayout = { applyFitCenterTransform(texture, latestVideoSize) },
                    onSurfaceFrame = { lastSurfaceFrameMs = System.currentTimeMillis() },
                )
                videoTextureRef.value = texture
                texture
            },
            update = { view ->
                detachViewFromParent(view)
                if (videoTextureRef.value !== view) {
                    videoTextureRef.value?.let { oldTexture ->
                        if (oldTexture !== view) player.clearVideoTextureView(oldTexture)
                    }
                    videoTextureRef.value = view
                }
                bindAdVideoTextureView(
                    texture = view,
                    player = player,
                    onSurfaceReady = { textureSurfaceGeneration += 1 },
                    onLayout = { applyFitCenterTransform(view, latestVideoSize) },
                    onSurfaceFrame = { lastSurfaceFrameMs = System.currentTimeMillis() },
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false }
        )
    }
}

private fun buildAdVideoMediaItem(loadUrl: String): MediaItem {
    val lower = loadUrl.lowercase()
    val mimeType = when {
        lower.contains(".m3u8") || lower.contains("format=m3u8") -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
        lower.contains(".mpd") -> androidx.media3.common.MimeTypes.APPLICATION_MPD
        else -> null
    }
    return if (mimeType != null) {
        MediaItem.Builder()
            .setUri(loadUrl)
            .setMimeType(mimeType)
            .build()
    } else {
        MediaItem.fromUri(loadUrl)
    }
}

/**
 * Binds ExoPlayer to [texture] when the surface exists. Many TV GPUs report decoder READY
 * while the TextureView surface is still unavailable — preparing earlier shows a black panel.
 */
private fun bindAdTextureSurfaceOnly(
    texture: TextureView,
    onSurfaceReady: () -> Unit,
) {
    fun notify() {
        if (texture.isAvailable) onSurfaceReady()
    }
    if (texture.isAvailable) {
        notify()
        return
    }
    texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            texture.surfaceTextureListener = null
            notify()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            texture.surfaceTextureListener = null
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
}

private class AdTextureCallbackState(
    var onLayout: () -> Unit,
    var onSurfaceFrame: () -> Unit,
    var onSurfaceReady: () -> Unit,
)

private fun bindAdVideoTextureView(
    texture: TextureView,
    player: ExoPlayer,
    onSurfaceReady: () -> Unit,
    onLayout: () -> Unit,
    onSurfaceFrame: () -> Unit = {},
) {
    @Suppress("UNCHECKED_CAST")
    val state = (texture.tag as? AdTextureCallbackState)
        ?: AdTextureCallbackState(onLayout, onSurfaceFrame, onSurfaceReady).also { created ->
            texture.tag = created
            val layoutHandler = Handler(Looper.getMainLooper())
            var layoutRunnable: Runnable? = null
            texture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                layoutRunnable?.let { layoutHandler.removeCallbacks(it) }
                layoutRunnable = Runnable { created.onLayout() }
                layoutHandler.postDelayed(layoutRunnable!!, 32L)
            }
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    created.onSurfaceReady()
                    created.onLayout()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    created.onLayout()
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    texture.surfaceTextureListener = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    created.onSurfaceFrame()
                }
            }
        }
    state.onLayout = onLayout
    state.onSurfaceFrame = onSurfaceFrame
    state.onSurfaceReady = onSurfaceReady

    if (texture.isAvailable) {
        onSurfaceReady()
        onLayout()
    }
}

private fun buildSharedAdTextureView(context: Context): TextureView {
    return TextureView(context).apply {
        setOpaque(true)
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
    isLowBandwidth: Boolean,
    context: Context,
    viewport: AdViewportPx = AdViewportSizing.lastViewport,
) {
    AdViewportSizing.applyVideoTrackConstraints(
        player = player,
        viewport = viewport,
        context = context,
        videoUrl = videoUrl,
        isLowBandwidth = isLowBandwidth,
    )
}

private fun applyFitCenterTransform(
    textureView: TextureView?,
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
        textureView.invalidate()
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

/**
 * Lowers ExoPlayer-based ad audio and the shared YouTube WebView video element while token TTS runs,
 * then restores prior levels. YouTube reference is registered from [AdArea].
 */
private object TokenAnnouncementAdAudio {
    @Volatile
    private var youtubeRef: WeakReference<WebView>? = null

    fun setYoutubeWebView(webView: WebView?) {
        youtubeRef = webView?.let { WeakReference(it) }
    }

    fun applyYoutubeDuck(duck: Boolean) {
        val wv = youtubeRef?.get() ?: return
        val js = if (duck) {
            """
            (function(){try{
              var v=document.querySelector('video');
              if(!v) return;
              if(!window._callqtv_tts_duck_saved){
                window._callqtv_tts_duck_saved={vol:v.volume,muted:v.muted};
              }
              v.muted=false;
              v.volume=0.1;
            }catch(e){}})();
            """.trimIndent()
        } else {
            """
            (function(){try{
              var v=document.querySelector('video');
              var s=window._callqtv_tts_duck_saved;
              window._callqtv_tts_duck_saved=null;
              if(!v||!s) return;
              v.volume=s.vol;
              v.muted=s.muted;
            }catch(e){}})();
            """.trimIndent()
        }
        try {
            wv.evaluateJavascript(js, null)
        } catch (_: Exception) {
        }
    }
}

private class AdAnnouncementRestoreOnce(private val appContext: Context) {
    private val finished = AtomicBoolean(false)

    fun run() {
        if (!finished.compareAndSet(false, true)) return
        Handler(Looper.getMainLooper()).post {
            MediaEngine.restoreAfterAnnouncement(appContext)
            TokenAnnouncementAdAudio.applyYoutubeDuck(false)
        }
    }
}

private suspend fun runWithAdvertisementAudioDuckedForSpeech(
    context: Context,
    block: suspend (skipSynthesisPrime: Boolean, restore: () -> Unit) -> Unit,
) {
    val prefs = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
    val adSoundEnabled = prefs.getBoolean("enable_ad_sound", false)
    if (!adSoundEnabled) {
        block(false) { }
        return
    }
    val restore = AdAnnouncementRestoreOnce(context.applicationContext)
    withContext(Dispatchers.Main.immediate) {
        MediaEngine.duckForAnnouncement()
        TokenAnnouncementAdAudio.applyYoutubeDuck(true)
    }
    TokenAnnouncer.awaitSynthesisPrimeIfNeeded()
    try {
        block(true) { restore.run() }
    } finally {
        restore.run()
    }
}

/**
 * On some MediaTek devices `c2.mtk.*` Codec2 components fail allocation with NO_MEMORY when
 * graphic/ION memory is already under pressure (WebView, multiple surfaces, another decoder).
 * Prefer other decoders first so ExoPlayer avoids a doomed init + noisy stack traces; MTK
 * remains in the list as a last resort when no alternative exists.
 */
private object DeprioritizeMtkCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ) = MediaCodecSelector.DEFAULT.getDecoderInfos(
        mimeType,
        requiresSecureDecoder,
        requiresTunnelingDecoder
    ).let { base ->
        if (mimeType != MimeTypes.VIDEO_H264 && mimeType != MimeTypes.VIDEO_H265) {
            return@let base
        }
        val nonMtk = base.filterNot { it.name.contains("mtk", ignoreCase = true) }
        val mtk = base.filter { it.name.contains("mtk", ignoreCase = true) }
        if (nonMtk.isNotEmpty() && mtk.isNotEmpty()) nonMtk + mtk else base
    }
}

@UnstableApi
object MediaEngine {
    private var player1: ExoPlayer? = null
    private var player2: ExoPlayer? = null
    private var simpleCache: androidx.media3.datasource.cache.Cache? = null
    private var cacheFactory: DataSource.Factory? = null

    /** Snapshot of Exo volumes before ducking for token speech (null = not ducked). */
    private var announcementDuckSavedVolumes: Pair<Float, Float>? = null

    private const val ANNOUNCEMENT_DUCK_VOLUME = 0.12f

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
            // Enough buffer for high-bitrate files; reduces rebuffer stutter / apparent "hangs".
            .setBufferDurationsMs(4_000, 45_000, 1_500, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Enable decoder fallback to improve compatibility on heterogeneous Android TV/SoC decoders.
        // Some devices fail MediaCodec.configure() for certain AVC streams; fallback lets ExoPlayer
        // try alternative initialization paths instead of getting stuck on a single codec.
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext)
            .setMediaCodecSelector(DeprioritizeMtkCodecSelector)
            .setEnableDecoderFallback(true)

        // Cap to the measured ad viewport (updated from [AdArea]); avoids decoding 4K into a narrow strip.
        val initialTarget = AdViewportSizing.decodeTarget(AdViewportSizing.lastViewport, context)
        val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
            val params: androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters =
                ParametersBuilder(context.applicationContext)
                    .setMaxVideoSize(initialTarget.width, initialTarget.height)
                    .setMaxVideoFrameRate(60)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
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
                videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
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
     * Pre-initialize the active ad player and cache to avoid heavy work on the first ad.
     * Only player index 0 is used in production (`AdArea`); avoid allocating a second decoder
     * on memory-constrained TV SoCs (MTK c2.mtk.avc.decoder NO_MEMORY).
     */
    fun warmUp(context: Context) {
        if (player1 == null) player1 = createPlayer(context)
    }

    /** Second player for off-screen preload (avoids black handoff between video ads). */
    fun ensureSecondPlayer(context: Context) {
        if (player2 == null) player2 = createPlayer(context)
    }

    /**
     * Called when the ad pane is laid out. Track caps are applied only while [ExoPlayer] is idle
     * so a resize mid-playback does not reconfigure the decoder (grain / crack / freeze).
     */
    fun detachTextureFromAllPlayers(texture: TextureView) {
        try {
            player1?.clearVideoTextureView(texture)
        } catch (_: Exception) {
        }
        try {
            player2?.clearVideoTextureView(texture)
        } catch (_: Exception) {
        }
    }

    fun updateViewport(context: Context, widthPx: Int, heightPx: Int) {
        val viewport = AdViewportSizing.fromConstraints(widthPx, heightPx, context)
        listOfNotNull(player1, player2).forEach { player ->
            if (player.playbackState != Player.STATE_IDLE) return@forEach
            AdViewportSizing.applyVideoTrackConstraints(
                player = player,
                viewport = viewport,
                context = context.applicationContext,
                videoUrl = "",
                isLowBandwidth = isAdLowBandwidthNetwork(context),
            )
        }
    }

    fun updateVolume(context: Context) {
        val isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getBoolean("enable_ad_sound", false)
        val vol = if (isAdSoundEnabled) 1f else 0f
        player1?.volume = vol
        player2?.volume = vol
    }

    fun duckForAnnouncement() {
        val a = player1?.volume ?: 1f
        val b = player2?.volume ?: 1f
        announcementDuckSavedVolumes = a to b
        player1?.volume = ANNOUNCEMENT_DUCK_VOLUME
        player2?.volume = ANNOUNCEMENT_DUCK_VOLUME
    }

    fun restoreAfterAnnouncement(context: Context) {
        val saved = announcementDuckSavedVolumes
        announcementDuckSavedVolumes = null
        val wantSound = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getBoolean("enable_ad_sound", false)
        val target = when {
            saved == null -> if (wantSound) 1f else 0f
            wantSound -> saved.first
            else -> 0f
        }
        player1?.volume = target
        player2?.volume = target
    }

    fun shutdown() {
        announcementDuckSavedVolumes = null
        player1?.release()
        player2?.release()
        player1 = null
        player2 = null
    }
}

private fun counterStorageLookupKey(counter: CounterEntity): String {
    val btn = counter.buttonIndex?.toString()?.trim()?.takeIf { it.isNotBlank() }
    if (!btn.isNullOrBlank()) return btn
    return counter.keypadIndex?.trim()?.takeIf { it.isNotBlank() }
        ?: counter.counterId?.trim()?.takeIf { it.isNotBlank() }
        ?: counter.name?.trim()?.takeIf { it.isNotBlank() }
        ?: counter.defaultName?.trim()?.takeIf { it.isNotBlank() }
        ?: ""
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
    blinkTriggers: Map<String, Long> = emptyMap(),
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    vipTopTokenByKey: Map<String, String> = emptyMap(),
) {
    Box(modifier = Modifier.fillMaxSize().padding(1.dp)) {
        val numCounters = counters.size

        // Use layoutType to influence splitting. 
        // type "1" (default) = automated split
        // type "2" = force no split (single grid/column)
        // type "3" = force split even for few counters
        val splitThreshold = when (layoutType.trim().lowercase()) {
            "2", "full" -> 999
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
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTok = vipTopTokenByKey[sk]?.takeIf { it.isNotBlank() }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipDisplayTopToken = vipTok)
                        }
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTok = vipTopTokenByKey[sk]?.takeIf { it.isNotBlank() }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipDisplayTopToken = vipTok)
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
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTok = vipTopTokenByKey[sk]?.takeIf { it.isNotBlank() }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipDisplayTopToken = vipTok)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTok = vipTopTokenByKey[sk]?.takeIf { it.isNotBlank() }
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipDisplayTopToken = vipTok)
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
                        val sk = counterStorageLookupKey(counter)
                        val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                        val vipTok = vipTopTokenByKey[sk]?.takeIf { it.isNotBlank() }
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipDisplayTopToken = vipTok)
                    }
                }
            } else {
                // Landscape: single horizontal row of counters
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter ->
                        val sk = counterStorageLookupKey(counter)
                        val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                        val vipTok = vipTopTokenByKey[sk]?.takeIf { it.isNotBlank() }
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipDisplayTopToken = vipTok)
                    }
                }
            }
        }
    }
}

/**
 * Fills the token area with a fixed grid: each cell gets equal width/height so no empty
 * space remains inside the counter token region (slots scale with [totalSlots] and [columns]).
 */
@Composable
private fun CounterTokenSlotsGrid(
    modifier: Modifier = Modifier,
    totalSlots: Int,
    columns: Int,
    tokens: List<String>,
    usePrefix: Boolean,
    counterCode: String,
    config: TvConfigEntity,
    scale: Float,
    tokenFontSize: Float,
    currentTokenTextColor: Color,
    previousTokenTextColor: Color,
    tokenBgBrush: Brush,
    shouldBlink: Boolean,
    isInverted: Boolean,
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    vipEmergencyTopRawToken: String? = null,
) {
    val cols = columns.coerceAtLeast(1)
    if (totalSlots <= 0) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    val rowCount = (totalSlots + cols - 1) / cols
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        for (r in 0 until rowCount) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (c in 0 until cols) {
                    val index = r * cols + c
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (index < totalSlots) {
                            CounterTokenSlot(
                                index = index,
                                tokens = tokens,
                                usePrefix = usePrefix,
                                counterCode = counterCode,
                                config = config,
                                scale = scale,
                                tokenFontSize = tokenFontSize,
                                currentTokenTextColor = currentTokenTextColor,
                                previousTokenTextColor = previousTokenTextColor,
                                tokenBgBrush = tokenBgBrush,
                                shouldBlink = shouldBlink,
                                isInverted = isInverted,
                                tokenBlinkMode = tokenBlinkMode,
                                vipEmergencyTopRawToken = vipEmergencyTopRawToken,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterTokenSlot(
    index: Int,
    tokens: List<String>,
    usePrefix: Boolean,
    counterCode: String,
    config: TvConfigEntity,
    scale: Float,
    tokenFontSize: Float,
    currentTokenTextColor: Color,
    previousTokenTextColor: Color,
    tokenBgBrush: Brush,
    shouldBlink: Boolean,
    isInverted: Boolean,
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    vipEmergencyTopRawToken: String? = null,
) {
    val token = tokens.getOrNull(index)
    val isFirst = index == 0
    val formattedToken = remember(token, config.tokenFormat) {
        formatTokenByPattern(token, config.tokenFormat)
    }
    val prefixForSlot = when {
        usePrefix && isFirst &&
            !vipEmergencyTopRawToken.isNullOrBlank() &&
            token?.trim().orEmpty() == vipEmergencyTopRawToken.trim() -> VIP_EMERGENCY_COUNTER_PREFIX
        else -> counterCode
    }
    val displayToken = when {
        formattedToken == null -> null
        isSpecialMessageToken(formattedToken) -> decodeSpecialMessageToken(formattedToken)
        usePrefix && prefixForSlot.isNotBlank() -> "$prefixForSlot-$formattedToken"
        else -> formattedToken
    }
    val textColorToUse = if (isFirst) currentTokenTextColor else previousTokenTextColor
    val isSpecialMsg = isSpecialMessageToken(token) || isSpecialMessageToken(formattedToken)
    TokenCard(
        token = displayToken,
        isPrimary = isFirst,
        scale = scale,
        textColor = textColorToUse,
        bgBrush = tokenBgBrush,
        fontSize = tokenFontSize,
        isInverted = if (isFirst && shouldBlink && token != null) isInverted else false,
        blinkMode = tokenBlinkMode,
        fullSize = true,
        multiline = isSpecialMsg,
        specialCounterMessage = isSpecialMsg,
    )
}

/** Counter header: centered when it fits; seamless horizontal marquee when the name is wider than the tile. */
@Composable
private fun CounterNameLabel(
    name: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textColorInt = color.toArgb()
    val fontSizeSp = fontSize.value
    AndroidView(
        factory = { CounterNameTickerView(it) },
        update = { view -> view.bind(name, textColorInt, fontSizeSp) },
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clipToBounds(),
    )
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
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    layoutType: String = "1",
    vipDisplayTopToken: String? = null,
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

    // Blink only after a live token call (blinkTrigger); not when tokens are restored from DB on refresh.
    var blinkActive by remember { mutableStateOf(false) }
    LaunchedEffect(shouldBlink, blinkSeconds, blinkTrigger) {
        if (!shouldBlink || blinkTrigger == 0L) {
            blinkActive = false
            return@LaunchedEffect
        }
        blinkActive = true
        if (blinkSeconds > 0) {
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
            val primaryRawToken = tokens.firstOrNull()
            val isSpecialMessage = isSpecialMessageToken(primaryRawToken)
            val specialMessage = decodeSpecialMessageToken(primaryRawToken)
            if (isPortrait) {
                Row(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                    // If ads exist, use 30% for name and 70% for tokens.
                    // If no ads, keep name compact and give 80% to tokens.
                    val nameWeight = if (hasAds) 0.30f else 0.2f
                    val tokenWeight = if (hasAds) 0.70f else 0.8f

                    // Counter name area (same layout for normal tokens and __MSG__ / protocol C)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(nameWeight)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        CounterNameLabel(
                            name = counterName,
                            fontSize = (counterFontSize * scale).sp,
                            color = counterColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = (counterFontSize * scale * 1.35f).dp.coerceAtLeast(20.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Token matrix area
                    Box(
                        modifier = Modifier
                            .weight(tokenWeight)
                            .fillMaxHeight()
                    ) {
                        if (isSpecialMessage) {
                            TokenCard(
                                modifier = Modifier.fillMaxSize(),
                                token = specialMessage,
                                isPrimary = true,
                                scale = scale,
                                textColor = currentTokenTextColor,
                                bgBrush = tokenBgBrush,
                                fontSize = tokenFontSize,
                                isInverted = shouldBlink && blinkActive && specialMessage != null && isInverted,
                                blinkMode = tokenBlinkMode,
                                fullSize = true,
                                multiline = true,
                                specialCounterMessage = true,
                            )
                        } else {
                            val totalSlots = config.tokensPerCounter ?: (rows * columns)
                            CounterTokenSlotsGrid(
                                modifier = Modifier.fillMaxSize(),
                                totalSlots = totalSlots,
                                columns = columns,
                                tokens = tokens,
                                usePrefix = usePrefix,
                                counterCode = counterCode,
                                config = config,
                                scale = scale,
                                tokenFontSize = tokenFontSize,
                                currentTokenTextColor = currentTokenTextColor,
                                previousTokenTextColor = previousTokenTextColor,
                                tokenBgBrush = tokenBgBrush,
                                shouldBlink = shouldBlink,
                                isInverted = isInverted,
                                tokenBlinkMode = tokenBlinkMode,
                                vipEmergencyTopRawToken = vipDisplayTopToken,
                            )
                        }
                    }
                }
            } else {
                // Cap header height: fillMaxHeight on the name row steals the whole column and hides tokens.
                val landscapeHeaderHeight = (counterFontSize * scale * 1.45f).dp.coerceIn(22.dp, 52.dp)
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(landscapeHeaderHeight)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CounterNameLabel(
                            name = counterName,
                            fontSize = (counterFontSize * scale).sp,
                            color = counterColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                        )
                    }
                    Spacer(modifier = Modifier.height(1.dp))

                    if (isSpecialMessage) {
                        TokenCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .fillMaxHeight(),
                            token = specialMessage,
                            isPrimary = true,
                            scale = scale,
                            textColor = currentTokenTextColor,
                            bgBrush = tokenBgBrush,
                            fontSize = tokenFontSize,
                            isInverted = shouldBlink && blinkActive && specialMessage != null && isInverted,
                            blinkMode = tokenBlinkMode,
                            fullSize = true,
                            multiline = true,
                            specialCounterMessage = true,
                        )
                    } else {
                        val totalSlots = config.tokensPerCounter ?: (rows * columns)
                        CounterTokenSlotsGrid(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            totalSlots = totalSlots,
                            columns = columns,
                            tokens = tokens,
                            usePrefix = usePrefix,
                            counterCode = counterCode,
                            config = config,
                            scale = scale,
                            tokenFontSize = tokenFontSize,
                            currentTokenTextColor = currentTokenTextColor,
                            previousTokenTextColor = previousTokenTextColor,
                            tokenBgBrush = tokenBgBrush,
                            shouldBlink = shouldBlink,
                            isInverted = isInverted,
                            tokenBlinkMode = tokenBlinkMode,
                            vipEmergencyTopRawToken = vipDisplayTopToken,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Largest font size in **sp** (given [maxSp] cap) so measured text fits in [maxW]×[maxH] px.
 */
private fun measureTokenAutoFitSp(
    textMeasurer: TextMeasurer,
    text: String,
    maxW: Float,
    maxH: Float,
    maxSp: Float,
    minSp: Float,
    multiline: Boolean,
    maxMultilineLines: Int = 4,
    /** When > 1, used for [TextStyle.lineHeight] so auto-fit height matches on-screen multiline text. */
    lineHeightEm: Float = 1f,
): Float {
    if (text.isEmpty()) return maxSp.coerceAtLeast(minSp)
    if (maxW <= 0f || maxH <= 0f) return minSp
    var lo = minSp
    var hi = maxSp.coerceAtLeast(minSp)
    if (hi <= lo) return lo
    var best = lo
    val maxWInt = maxW.toInt().coerceAtLeast(1)
    val maxHInt = maxH.toInt().coerceAtLeast(1)
    val measureConstraints = if (multiline) {
        Constraints(maxWidth = maxWInt, maxHeight = maxHInt)
    } else {
        Constraints(maxWidth = maxWInt)
    }
    fun styleFor(mid: Float): TextStyle {
        val base = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = mid.sp,
        )
        return if (lineHeightEm > 1.02f) {
            base.copy(lineHeight = (mid * lineHeightEm).sp)
        } else {
            base
        }
    }
    repeat(24) {
        val mid = (lo + hi) / 2f
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = styleFor(mid),
            overflow = TextOverflow.Clip,
            softWrap = multiline,
            maxLines = if (multiline) maxMultilineLines else 1,
            constraints = measureConstraints,
        )
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val fits = w <= maxW && h <= maxH
        if (fits) {
            best = mid
            lo = mid
        } else {
            hi = mid
        }
    }
    return best
}

/** Compose [Constraints] reject very large dimensions (e.g. Int.MAX_VALUE / 4). */
private fun composeMeasureMaxPx(px: Float): Int = px.toInt().coerceIn(1, 8192)

/** Largest single-line font (sp) that fits in [maxH] px (for horizontal marquee). */
private fun measureTokenAutoFitHeightSp(
    textMeasurer: TextMeasurer,
    text: String,
    maxH: Float,
    maxSp: Float,
    minSp: Float,
    maxWpx: Float = 8192f,
    lineHeightEm: Float = 1f,
): Float {
    if (text.isEmpty()) return maxSp.coerceAtLeast(minSp)
    if (maxH <= 0f) return minSp
    var lo = minSp
    var hi = maxSp.coerceAtLeast(minSp)
    if (hi <= lo) return lo
    var best = lo
    val maxHInt = composeMeasureMaxPx(maxH)
    val maxWInt = composeMeasureMaxPx(maxWpx)
    fun styleFor(mid: Float): TextStyle {
        val base = TextStyle(fontWeight = FontWeight.Bold, fontSize = mid.sp)
        return if (lineHeightEm > 1.02f) {
            base.copy(lineHeight = (mid * lineHeightEm).sp)
        } else {
            base
        }
    }
    repeat(24) {
        val mid = (lo + hi) / 2f
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = styleFor(mid),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWInt, maxHeight = maxHInt),
        )
        val fits = layout.size.height.toFloat() <= maxH
        if (fits) {
            best = mid
            lo = mid
        } else {
            hi = mid
        }
    }
    return best
}

/** Pack [text] onto lines at word boundaries so Compose never breaks mid-word. */
private fun buildWordWrappedMessage(
    textMeasurer: TextMeasurer,
    text: String,
    maxWpx: Float,
    fontSp: Float,
    lineHeightEm: Float,
): String {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size <= 1) return text.trim()
    val maxWInt = maxWpx.toInt().coerceAtLeast(1)
    val style = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = fontSp.sp,
        lineHeight = if (lineHeightEm > 1.02f) (fontSp * lineHeightEm).sp else TextUnit.Unspecified,
    )
    fun lineWidth(line: String): Float =
        textMeasurer.measure(
            text = AnnotatedString(line),
            style = style,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWInt),
        ).size.width.toFloat()

    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "${current} $word"
        if (lineWidth(candidate) <= maxWpx) {
            current = StringBuilder(candidate)
        } else {
            if (current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder()
            }
            if (lineWidth(word) <= maxWpx) {
                current = StringBuilder(word)
            } else {
                lines.add(word)
            }
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines.joinToString("\n")
}

@Composable
fun TokenCard(
    token: String?,
    isPrimary: Boolean,
    scale: Float,
    textColor: Color,
    bgBrush: Brush,
    fontSize: Float,
    isInverted: Boolean = false,
    blinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    fullSize: Boolean = false,
    multiline: Boolean = false,
    /** Full-bleed layout for protocol `C` / `__MSG__` counter messages (fills token region, no code prefix). */
    specialCounterMessage: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Dynamic height based on font size to prevent clipping
    // Reduced height multiplier to tighten padding around the text
    val cardHeight = (fontSize * 1.6f * scale).coerceIn(32f, 120f).dp
    val textMeasurer = rememberTextMeasurer()
    val effectiveMultiline = multiline || specialCounterMessage
    val maxMultilineLines = if (specialCounterMessage) 24 else 4
    val lineHeightEm =
        if (specialCounterMessage && effectiveMultiline) SPECIAL_COUNTER_MSG_LINE_HEIGHT_EM else 1f
    // Extra insets so multiline protocol C / __MSG__ text is not clipped and lines are not cramped.
    val outerPad = if (specialCounterMessage) (4f * scale).dp.coerceIn(3.dp, 8.dp) else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullSize) Modifier.fillMaxHeight() else Modifier.height(cardHeight))
            .padding(outerPad),
        shape = RoundedCornerShape(if (specialCounterMessage) 10.dp else 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        // Swap colors if inverted (whole tile), or pulse text only while keeping the tile background.
        val invertWholeTile = isInverted && blinkMode == TokenBlinkMode.WHOLE_TILE
        val invertTextOnly = isInverted && blinkMode == TokenBlinkMode.TEXT_ONLY
        val finalBg = if (invertWholeTile) {
            SolidColor(textColor)
        } else {
            bgBrush
        }

        val finalTextColor = when {
            invertWholeTile -> Color.White
            invertTextOnly -> textColor.copy(alpha = 0.4f)
            else -> textColor
        }

        Box(
            modifier = Modifier.fillMaxSize().background(finalBg),
            contentAlignment = Alignment.Center
        ) {
            if (fullSize) {
                val density = LocalDensity.current
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val maxWpx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val maxHpx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                    val minSidePx = minOf(maxWpx, maxHpx)
                    val padScale = if (specialCounterMessage) {
                        (minSidePx / (56f * density.density)).coerceIn(0.35f, 1f)
                    } else {
                        1f
                    }
                    val innerPadH = if (specialCounterMessage) {
                        (12f * scale * padScale).dp.coerceIn(2.dp, 20.dp)
                    } else {
                        4.dp
                    }
                    val innerPadV = if (specialCounterMessage) {
                        (10f * scale * padScale).dp.coerceIn(2.dp, 18.dp)
                    } else {
                        2.dp
                    }
                    val innerWpx = (maxWpx - with(density) { (innerPadH * 2).toPx() }).coerceAtLeast(1f)
                    val innerHpx = (maxHpx - with(density) { (innerPadV * 2).toPx() }).coerceAtLeast(1f)
                    val display = token ?: ""
                    val refTileAreaPx = 220f * 72f
                    val areaScale = kotlin.math.sqrt((innerWpx * innerHpx) / refTileAreaPx)
                        .coerceIn(0.32f, 1f)
                    val maxFontSp = if (specialCounterMessage) {
                        (fontSize * scale * areaScale).coerceAtLeast(8f)
                    } else {
                        fontSize * scale
                    }
                    val minFontSp = if (specialCounterMessage) 3.5f else 6f
                    // Narrow tiles: one scrolling line (avoids "NURS"/"E"/"CALLI" mid-word breaks).
                    val useSpecialMessageTicker =
                        specialCounterMessage && innerWpx < innerHpx * 0.85f
                    if (useSpecialMessageTicker) {
                        val tickerSp = remember(
                            display,
                            innerWpx,
                            innerHpx,
                            maxFontSp,
                            minFontSp,
                            lineHeightEm,
                        ) {
                            measureTokenAutoFitHeightSp(
                                textMeasurer,
                                display,
                                innerHpx,
                                maxFontSp,
                                minFontSp,
                                maxWpx = (innerWpx * 12f).coerceAtLeast(512f),
                                lineHeightEm = lineHeightEm,
                            )
                        }
                        val messageColorInt = finalTextColor.toArgb()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = innerPadH, vertical = innerPadV)
                                .clipToBounds(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AndroidView(
                                factory = { CounterNameTickerView(it) },
                                update = { view ->
                                    // finalTextColor changes every blink frame; bind updates color only.
                                    view.bind(display, messageColorInt, tickerSp)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        val displayForFit = remember(
                            display,
                            innerWpx,
                            maxFontSp,
                            specialCounterMessage,
                        ) {
                            if (specialCounterMessage) {
                                buildWordWrappedMessage(
                                    textMeasurer,
                                    display,
                                    innerWpx,
                                    maxFontSp,
                                    lineHeightEm,
                                )
                            } else {
                                display
                            }
                        }
                        val fittedSp = remember(
                            displayForFit,
                            innerWpx,
                            innerHpx,
                            maxFontSp,
                            minFontSp,
                            effectiveMultiline,
                            maxMultilineLines,
                            lineHeightEm,
                        ) {
                            measureTokenAutoFitSp(
                                textMeasurer,
                                displayForFit,
                                innerWpx,
                                innerHpx,
                                maxFontSp,
                                minFontSp,
                                effectiveMultiline,
                                maxMultilineLines,
                                lineHeightEm = lineHeightEm,
                            )
                        }
                        val messageStyle = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = fittedSp.sp,
                            color = finalTextColor,
                            textAlign = TextAlign.Center,
                            lineHeight = if (lineHeightEm > 1.02f) {
                                (fittedSp * lineHeightEm).sp
                            } else {
                                TextUnit.Unspecified
                            },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = innerPadH, vertical = innerPadV),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = displayForFit,
                                style = messageStyle,
                                maxLines = if (effectiveMultiline) maxMultilineLines else 1,
                                softWrap = effectiveMultiline,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = token ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSize * scale).sp,
                    color = finalTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = if (effectiveMultiline) maxMultilineLines else 1,
                    softWrap = effectiveMultiline,
                )
            }
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

private fun parseHexColorOrNull(colorString: String?): Int? {
    if (colorString.isNullOrBlank()) return null
    return try {
        AndroidColor.parseColor(colorString.trim())
    } catch (_: Exception) {
        null
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
    companyName: String,
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    onTokenBlinkModeChange: (TokenBlinkMode) -> Unit = {},
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
        PresetColorDialog(
            title = "App Theme",
            options = ThemeColorManager.themeColorOptions,
            selectedHex = currentThemeHex,
            selectedBorderWidth = 5.dp,
            onColorSelected = {
                onThemeSelected(it)
                currentThemeHex = it
                showThemeColorPicker = false
            },
            onDismiss = { showThemeColorPicker = false },
        )
    } else if (showCounterColorPicker) {
        PresetColorDialog(
            title = "Counter Background",
            options = ThemeColorManager.backgroundOptions,
            selectedHex = currentCounterHex,
            onColorSelected = {
                onCounterBgChange(it)
                currentCounterHex = it
                showCounterColorPicker = false
            },
            onDismiss = { showCounterColorPicker = false },
        )
    } else if (showTokenColorPicker) {
        PresetColorDialog(
            title = "Token Background",
            options = ThemeColorManager.backgroundOptions,
            selectedHex = currentTokenHex,
            onColorSelected = {
                onTokenBgChange(it)
                currentTokenHex = it
                showTokenColorPicker = false
            },
            onDismiss = { showTokenColorPicker = false },
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
                                item(span = { GridItemSpan(2) }) {
                                    GridSettingsItem(
                                        title = "Token blink",
                                        helpText = "When your TV configuration enables blinking on the current token, choose whether the entire token tile flashes or only the token text pulses.",
                                        onClick = null,
                                        titleColor = settingsPrimary,
                                        cardColor = settingsCard,
                                        borderColor = settingsBorder,
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        ThemeColorManager.setTokenBlinkMode(context, TokenBlinkMode.WHOLE_TILE)
                                                        onTokenBlinkModeChange(TokenBlinkMode.WHOLE_TILE)
                                                    },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = tokenBlinkMode == TokenBlinkMode.WHOLE_TILE,
                                                    onClick = {
                                                        ThemeColorManager.setTokenBlinkMode(context, TokenBlinkMode.WHOLE_TILE)
                                                        onTokenBlinkModeChange(TokenBlinkMode.WHOLE_TILE)
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = settingsPrimary,
                                                        unselectedColor = settingsMutedText
                                                    )
                                                )
                                                Text(
                                                    "Whole tile blinks",
                                                    fontSize = 15.sp,
                                                    color = settingsText,
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        ThemeColorManager.setTokenBlinkMode(context, TokenBlinkMode.TEXT_ONLY)
                                                        onTokenBlinkModeChange(TokenBlinkMode.TEXT_ONLY)
                                                    },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = tokenBlinkMode == TokenBlinkMode.TEXT_ONLY,
                                                    onClick = {
                                                        ThemeColorManager.setTokenBlinkMode(context, TokenBlinkMode.TEXT_ONLY)
                                                        onTokenBlinkModeChange(TokenBlinkMode.TEXT_ONLY)
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = settingsPrimary,
                                                        unselectedColor = settingsMutedText
                                                    )
                                                )
                                                Text(
                                                    "Text only blinks",
                                                    fontSize = 15.sp,
                                                    color = settingsText,
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    }
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
                                val currentSoundLabel =
                                    ThemeColorManager.notificationSoundLabel(notificationSoundKey)
                                
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
                                InfoRow("Company ID", String.format(java.util.Locale.ROOT, "%04d", customerId))
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
fun TvConfigurationUnavailableScreen(
    macAddress: String,
    appVersion: String,
    isNetworkAvailable: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    val guidance = when {
        !isNetworkAvailable ->
            "No internet connection detected.\n\nConnect this TV to Wi‑Fi or Ethernet, then tap Retry."
        !errorMessage.isNullOrBlank() ->
            errorMessage.trim()
        else ->
            "TV configuration could not be loaded from the server.\n\n" +
                "• Confirm the device is registered and approved in the portal\n" +
                "• Verify customer ID and MAC address assignment\n" +
                "• Check that the config API is reachable, then tap Retry"
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_network_unavailable),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (!isNetworkAvailable) "No network connection" else "TV configuration not loaded",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = guidance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0E0E0),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Device ID: $macAddress",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "App version: $appVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
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

private val PresetPickerFocusRing = Color(0xFFFFD600)
private val PresetPickerFocusOutline = Color(0xFF000000)
private val PresetPickerSelectedRing = Color.White

/**
 * TV-friendly color swatch: [clickable] + [focusable] on one node so D-pad focus updates reliably.
 * [focusedIndex] in the parent grid is updated from [onFocusChanged] for visible focus ring.
 */
@Composable
private fun PresetColorSwatchTile(
    brush: Brush,
    isSelected: Boolean,
    isFocused: Boolean,
    selectedBorderWidth: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val interactionFocused by interactionSource.collectIsFocusedAsState()
    val showFocus = isFocused || interactionFocused
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { state ->
                if (state.isFocused) onFocused()
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .scale(if (showFocus) 1.16f else 1f)
            .then(
                if (showFocus) {
                    Modifier.border(3.dp, PresetPickerFocusOutline, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .border(
                width = when {
                    showFocus -> 5.dp
                    isSelected -> selectedBorderWidth
                    else -> 1.dp
                },
                color = when {
                    showFocus -> PresetPickerFocusRing
                    isSelected -> PresetPickerSelectedRing
                    else -> Color.Gray.copy(alpha = 0.55f)
                },
                shape = if (showFocus) RoundedCornerShape(10.dp) else shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showFocus) 2.dp else 0.dp)
                .clip(shape)
                .background(brush),
        )
        if (showFocus) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), shape),
            )
        }
        if (isSelected && !showFocus) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .background(PresetPickerSelectedRing, CircleShape)
                    .border(1.dp, PresetPickerFocusOutline, CircleShape),
            )
        }
    }
}

@Composable
fun PresetColorDialog(
    title: String,
    options: List<ThemeOption>,
    selectedHex: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    selectedBorderWidth: androidx.compose.ui.unit.Dp = 3.dp,
) {
    val gridState = rememberLazyGridState()
    val selectedIndex = remember(options, selectedHex) {
        val trimmed = selectedHex.trim()
        val idx = options.indexOfFirst { it.hexCode.trim() == trimmed }
        if (idx >= 0) idx else 0
    }
    val selectedFocusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }

    var brushesReady by remember(options) { mutableStateOf(false) }
    LaunchedEffect(options) {
        // Warm brushes in a throttled way to avoid CPU starvation/ANR on low-end TVs.
        brushesReady = false

        val initialWarmCount = minOf(options.size, 35)
        withContext(Dispatchers.Default) {
            for (i in 0 until initialWarmCount) {
                ThemeColorManager.getBackgroundBrush(options[i].hexCode)
                // Give Compose/main thread a chance between batches.
                if (i % 7 == 6) kotlinx.coroutines.yield()
            }
        }

        brushesReady = true

        // Continue warming the remaining swatches in the background.
        // This prevents first-scroll stutter without blocking dialog open.
        if (initialWarmCount < options.size) {
            withContext(Dispatchers.Default) {
                for (i in initialWarmCount until options.size) {
                    ThemeColorManager.getBackgroundBrush(options[i].hexCode)
                    if (i % 7 == 6) {
                        kotlinx.coroutines.yield()
                        delay(1)
                    }
                }
            }
        }
    }

    LaunchedEffect(brushesReady, selectedIndex) {
        if (!brushesReady) return@LaunchedEffect
        delay(150)
        gridState.animateScrollToItem(selectedIndex)
        delay(80)
        focusedIndex = selectedIndex
        try {
            selectedFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.98f),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            if (!brushesReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .focusGroup(),
                ) {
                    itemsIndexed(
                        items = options,
                        key = { index, option -> "${option.name}_$index" },
                    ) { index, option ->
                        val swatchBrush = remember(option.hexCode) {
                            ThemeColorManager.getBackgroundBrush(option.hexCode)
                        }
                        val isSelected = index == selectedIndex
                        val isFocused = index == focusedIndex
                        PresetColorSwatchTile(
                            brush = swatchBrush,
                            isSelected = isSelected,
                            isFocused = isFocused,
                            selectedBorderWidth = selectedBorderWidth,
                            focusRequester = if (isSelected) selectedFocusRequester else null,
                            onClick = { onColorSelected(option.hexCode) },
                            onFocused = { focusedIndex = index },
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
        },
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
    val soundOptions = ThemeColorManager.notificationSoundOptions
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val selectedIndex = remember(soundOptions, selectedKey) {
        soundOptions.indexOfFirst { it.first == selectedKey }.let { if (it >= 0) it else 0 }
    }
    val selectedFocusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(selectedIndex) {
        delay(150)
        gridState.animateScrollToItem(selectedIndex)
        delay(80)
        focusedIndex = selectedIndex
        try {
            selectedFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.98f),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .focusGroup(),
            ) {
                itemsIndexed(soundOptions, key = { index, item -> "${item.first}_$index" }) { index, (key, label) ->
                    val isSelected = index == selectedIndex
                    val isFocused = index == focusedIndex
                    val interactionSource = remember { MutableInteractionSource() }
                    val interactionFocused by interactionSource.collectIsFocusedAsState()
                    val showFocus = isFocused || interactionFocused
                    OutlinedButton(
                        onClick = {
                            onSoundSelected(key)
                            scope.launch { playTokenChime(context, key) }
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier,
                            )
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedIndex = index
                            }
                            .scale(if (showFocus) 1.06f else 1f),
                        border = when {
                            showFocus -> BorderStroke(5.dp, PresetPickerFocusRing)
                            isSelected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        },
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = if (showFocus) PresetPickerFocusRing else MaterialTheme.colorScheme.onSurface,
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
        },
    )
}

@Composable
fun ScrollingFooter(
    textLines: List<String>,
    scale: Float,
    isPortrait: Boolean,
    appThemeHex: String,
    scrollTextColorHex: String? = null,
) {
    val scrollText = remember(textLines) {
        textLines.filter { it.isNotBlank() }.joinToString(separator = "  •  ")
    }
    if (scrollText.isEmpty()) return

    val textColor = remember(scrollTextColorHex) {
        parseHexColorOrNull(scrollTextColorHex) ?: android.graphics.Color.WHITE
    }
    val textSizeSp = if (isPortrait) 12f else 14f
    // Use a compact repeating unit so ticker flow feels continuous (no visible block reset).
    val marqueeText = remember(scrollText) { "$scrollText   \u2022   " }
    val footerHeight = if (isPortrait) 24.dp else 28.dp
    val footerBrush = remember(appThemeHex) {
        ThemeColorManager.getTickerStripBackgroundBrush(appThemeHex)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(footerBrush)
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

private fun configureAdTickerTextView(tv: TextView, color: Int, sizeSp: Float, isBold: Boolean) {
    tv.setTextColor(color)
    tv.textSize = sizeSp
    tv.setTypeface(tv.typeface, if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    tv.isSingleLine = true
    tv.includeFontPadding = false
    tv.gravity = android.view.Gravity.CENTER_VERTICAL
    tv.setHorizontallyScrolling(true)
    tv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
}

/** Pause at the start of each marquee loop so the label can be read before scrolling again. */
private const val MARQUEE_RESTART_PAUSE_MS = 3_000L

/** Counter header text must never ellipsize; width is set from paint measure and laid out in [CounterNameTickerView]. */
private fun configureCounterNameTextView(tv: TextView, color: Int, sizeSp: Float) {
    tv.setTextColor(color)
    tv.textSize = sizeSp
    tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
    tv.isSingleLine = true
    tv.maxLines = 1
    tv.includeFontPadding = false
    tv.gravity = android.view.Gravity.CENTER_VERTICAL
    tv.setHorizontallyScrolling(true)
    tv.ellipsize = null
    tv.maxWidth = Int.MAX_VALUE
    tv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
}

/**
 * Counter name header: static centered label when it fits; otherwise the same seamless
 * horizontal scroll used by the footer ticker so long names (e.g. CARDIOLOGY) stay readable.
 */
private class CounterNameTickerView(context: Context) : FrameLayout(context) {
    private val text1 = TextView(context)
    private val text2 = TextView(context)
    private var animator: ValueAnimator? = null
    private var cachedName: String = ""
    private var cachedTextColor: Int = Int.MIN_VALUE
    private var cachedTextSizeSp: Float = -1f
    private var currentOffset: Float = 0f
    private var marqueeActive: Boolean = false
    private var marqueePauseRunnable: Runnable? = null
    private var marqueeDistance: Float = 0f
    private var marqueeDurationMs: Long = 1L

    init {
        clipToPadding = true
        clipChildren = true
        setWillNotDraw(true)
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        text1.layoutParams = lp
        text2.layoutParams = lp
        addView(text1)
        addView(text2)
        text2.visibility = GONE
    }

    fun bind(name: String, textColor: Int, textSizeSp: Float) {
        val colorChanged = cachedTextColor != textColor
        val sizeChanged = cachedTextSizeSp != textSizeSp
        if (colorChanged || sizeChanged) {
            cachedTextColor = textColor
            cachedTextSizeSp = textSizeSp
            configureCounterNameTextView(text1, textColor, textSizeSp)
            configureCounterNameTextView(text2, textColor, textSizeSp)
        }
        val nameChanged = cachedName != name
        if (nameChanged) {
            cachedName = name
            restartLayout(preservePhase = false)
            return
        }
        if (sizeChanged) {
            restartLayout(preservePhase = true)
            return
        }
        if (marqueeActive && animator?.isRunning != true && marqueePauseRunnable == null) {
            restartLayout(preservePhase = true)
        }
    }

    private fun cancelMarqueeAnimation() {
        animator?.cancel()
        animator = null
        marqueePauseRunnable?.let { removeCallbacks(it) }
        marqueePauseRunnable = null
    }

    private fun applyMarqueeOffset(offset: Float) {
        currentOffset = offset
        text1.translationX = -offset
        text2.translationX = marqueeDistance - offset
    }

    private fun scheduleMarqueePause() {
        if (!marqueeActive) return
        applyMarqueeOffset(0f)
        marqueePauseRunnable = Runnable {
            if (marqueeActive) runMarqueeScroll(0f)
        }
        postDelayed(marqueePauseRunnable!!, MARQUEE_RESTART_PAUSE_MS)
    }

    private fun runMarqueeScroll(fromOffset: Float) {
        if (!marqueeActive) return
        val distance = marqueeDistance
        val clampedFrom = fromOffset.coerceIn(0f, distance)
        applyMarqueeOffset(clampedFrom)
        val remainingFraction =
            if (distance > 0f) 1f - (clampedFrom / distance) else 0f
        val animDuration = (marqueeDurationMs * remainingFraction).toLong().coerceAtLeast(1L)
        animator = ValueAnimator.ofFloat(clampedFrom, distance).apply {
            duration = animDuration
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                applyMarqueeOffset(va.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    applyMarqueeOffset(0f)
                    scheduleMarqueePause()
                }

                override fun onAnimationCancel(animation: Animator) {
                    marqueePauseRunnable?.let { removeCallbacks(it) }
                }
            })
            start()
        }
    }

    /** Measure children at full text width so Android does not ellipsize inside a narrow tile. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewportWidth = MeasureSpec.getSize(widthMeasureSpec)
        val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val childHeightSpec = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(parentHeight, MeasureSpec.EXACTLY)
            MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(parentHeight.coerceAtLeast(0), MeasureSpec.AT_MOST)
            else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        var maxChildHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.measure(childWidthSpec, childHeightSpec)
            maxChildHeight = maxOf(maxChildHeight, child.measuredHeight)
        }
        val resolvedHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> parentHeight
            MeasureSpec.AT_MOST -> minOf(maxChildHeight, parentHeight)
            else -> maxChildHeight
        }
        setMeasuredDimension(viewportWidth, resolvedHeight.coerceAtLeast(0))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val viewportHeight = bottom - top
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val childTop = ((viewportHeight - child.measuredHeight) / 2).coerceAtLeast(0)
            child.layout(0, childTop, child.measuredWidth, childTop + child.measuredHeight)
        }
    }

    private fun measureContentWidth(content: String): Float {
        return text1.paint.measureText(content).coerceAtLeast(1f)
    }

    private fun applyContent(tv: TextView, content: String) {
        tv.text = content
        val contentWidth = kotlin.math.ceil(measureContentWidth(content).toDouble()).toInt().coerceAtLeast(1)
        val lp = tv.layoutParams as LayoutParams
        if (lp.width != contentWidth) {
            lp.width = contentWidth
            tv.layoutParams = lp
        }
    }

    private fun restartLayout(preservePhase: Boolean) {
        post {
            val previousOffset = currentOffset
            cancelMarqueeAnimation()

            val viewWidth = width.toFloat()
            if (viewWidth <= 0f) return@post

            val label = cachedName
            applyContent(text1, label)
            applyContent(text2, "")
            text2.visibility = GONE
            requestLayout()
            post {
                applyMeasuredLayout(viewWidth, label, previousOffset, preservePhase)
            }
        }
    }

    /**
     * Decide static vs marquee from **measured** text width. Never center with negative translationX
     * (that clips the middle of long names, e.g. "RDIOLOG" instead of "CARDIOLOGY 1").
     */
    private fun applyMeasuredLayout(
        viewWidth: Float,
        label: String,
        previousOffset: Float,
        preservePhase: Boolean,
    ) {
        val horizontalPad = 4f * resources.displayMetrics.density
        val laidOutWidth = text1.measuredWidth.toFloat().coerceAtLeast(measureContentWidth(label))
        val needsMarquee = laidOutWidth > viewWidth - horizontalPad

        if (!needsMarquee) {
            marqueeActive = false
            text2.visibility = GONE
            text1.translationX = ((viewWidth - laidOutWidth) / 2f).coerceAtLeast(0f)
            text2.translationX = viewWidth
            currentOffset = 0f
            return
        }

        marqueeActive = true
        text2.visibility = VISIBLE
        val loopText = "$label   "
        applyContent(text1, loopText)
        applyContent(text2, loopText)
        requestLayout()
        post {
            startMarquee(viewWidth, loopText, previousOffset, preservePhase)
        }
    }

    private fun startMarquee(
        viewWidth: Float,
        loopText: String,
        previousOffset: Float,
        preservePhase: Boolean,
    ) {
        cancelMarqueeAnimation()

        val gap = 20f * resources.displayMetrics.density
        val loopWidth = text1.measuredWidth.toFloat().coerceAtLeast(measureContentWidth(loopText))
        val distance = loopWidth + gap
        val speedDpPerSec = 18f
        val speedPxPerSec = speedDpPerSec * resources.displayMetrics.density
        val durationMs = ((distance / speedPxPerSec) * 1000f).toLong().coerceAtLeast(1L)
        val startFraction = if (preservePhase && distance > 0f) {
            ((previousOffset % distance) / distance).coerceIn(0f, 1f)
        } else {
            0f
        }

        marqueeDistance = distance
        marqueeDurationMs = durationMs

        if (startFraction > 0f && preservePhase) {
            runMarqueeScroll(distance * startFraction)
        } else {
            scheduleMarqueePause()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            restartLayout(preservePhase = true)
        }
    }

    override fun onDetachedFromWindow() {
        cancelMarqueeAnimation()
        super.onDetachedFromWindow()
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
    private var marqueePauseRunnable: Runnable? = null
    private var marqueeDistance: Float = 0f
    private var marqueeDurationMs: Long = 1L

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
        val colorChanged = cachedTextColor != textColor
        val sizeChanged = cachedTextSizeSp != textSizeSp
        val boldChanged = cachedIsBold != isBold
        if (colorChanged || sizeChanged || boldChanged) {
            cachedTextColor = textColor
            cachedTextSizeSp = textSizeSp
            cachedIsBold = isBold
            configureAdTickerTextView(text1, textColor, textSizeSp, isBold)
            configureAdTickerTextView(text2, textColor, textSizeSp, isBold)
        }

        val textChanged = cachedText != text
        if (textChanged) {
            cachedText = text
            text1.text = text
            text2.text = text
        }
        val speedChanged = cachedSpeedDpPerSec != speedDpPerSec
        if (speedChanged) {
            cachedSpeedDpPerSec = speedDpPerSec
        }

        when {
            textChanged -> restartAnimation(preservePhase = false)
            sizeChanged || speedChanged -> restartAnimation(preservePhase = true)
            animator?.isRunning != true && marqueePauseRunnable == null ->
                restartAnimation(preservePhase = true)
        }
    }

    private fun cancelMarqueeAnimation() {
        animator?.cancel()
        animator = null
        marqueePauseRunnable?.let { removeCallbacks(it) }
        marqueePauseRunnable = null
    }

    private fun applyMarqueeOffset(offset: Float) {
        currentOffset = offset
        text1.translationX = -offset
        text2.translationX = marqueeDistance - offset
    }

    private fun scheduleMarqueePause() {
        applyMarqueeOffset(0f)
        marqueePauseRunnable = Runnable { runMarqueeScroll(0f) }
        postDelayed(marqueePauseRunnable!!, MARQUEE_RESTART_PAUSE_MS)
    }

    private fun runMarqueeScroll(fromOffset: Float) {
        val distance = marqueeDistance
        val clampedFrom = fromOffset.coerceIn(0f, distance)
        applyMarqueeOffset(clampedFrom)
        val remainingFraction =
            if (distance > 0f) 1f - (clampedFrom / distance) else 0f
        val animDuration = (marqueeDurationMs * remainingFraction).toLong().coerceAtLeast(1L)
        animator = ValueAnimator.ofFloat(clampedFrom, distance).apply {
            duration = animDuration
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                applyMarqueeOffset(va.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    applyMarqueeOffset(0f)
                    scheduleMarqueePause()
                }

                override fun onAnimationCancel(animation: Animator) {
                    marqueePauseRunnable?.let { removeCallbacks(it) }
                }
            })
            start()
        }
    }

    private fun restartAnimation(preservePhase: Boolean) {
        post {
            val previousOffset = currentOffset
            cancelMarqueeAnimation()
            val w = width.toFloat()
            if (w <= 0f) return@post

            val textWidth = text1.paint.measureText(text1.text?.toString().orEmpty()).coerceAtLeast(1f)
            val gap = 16f * resources.displayMetrics.density
            val distance = textWidth + gap
            lastDistance = distance
            marqueeDistance = distance
            val speedPxPerSec = (cachedSpeedDpPerSec.coerceAtLeast(8f)) * resources.displayMetrics.density
            marqueeDurationMs = ((distance / speedPxPerSec) * 1000f).toLong().coerceAtLeast(1L)
            val startFraction = if (preservePhase && distance > 0f) {
                ((previousOffset % distance) / distance).coerceIn(0f, 1f)
            } else {
                0f
            }

            if (startFraction > 0f && preservePhase) {
                runMarqueeScroll(distance * startFraction)
            } else {
                scheduleMarqueePause()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && oldw > 0 && w != oldw) {
            restartAnimation(preservePhase = true)
        }
    }

    override fun onDetachedFromWindow() {
        cancelMarqueeAnimation()
        super.onDetachedFromWindow()
    }
}

private fun getTokensForCounter(counter: CounterEntity, tokensPerCounter: Map<String, List<String>>): List<String> {
    // Match MQTT storage keys (button_index first). Do not scan the whole map or match by
    // keypad_index when button_index is set — that caused one counter's tokens to appear on all boards.
    val storageKey = counterStorageLookupKey(counter)
    val btnKey = counter.buttonIndex?.toString()?.trim().orEmpty()
    val lookupKeys = linkedSetOf<String>()
    if (storageKey.isNotBlank()) lookupKeys.add(storageKey)
    counter.counterId?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    counter.name?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    counter.defaultName?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    if (btnKey.isBlank()) {
        counter.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    }

    var rawList: List<String>? = null
    for (key in lookupKeys) {
        val candidate = tokensPerCounter[key]
        if (!candidate.isNullOrEmpty()) {
            rawList = candidate
            break
        }
    }
    if (rawList == null && storageKey == "__default__") {
        rawList = tokensPerCounter["__default__"]
    }
    rawList = rawList ?: emptyList()

    return rawList
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    // When a protocol-C / __MSG__ overlay is at index 0, [CounterBoard] shows it until a new
    // normal token arrives (MqttViewModel removes __MSG__ entries on the next token update).
}

private fun resolveCountersToDisplay(
    counters: List<CounterEntity>,
    config: TvConfigEntity
): List<CounterEntity> {
    val enabled = counters.filter { it.isEnabled != false }
    if (enabled.isEmpty()) return emptyList()
    return if (config.layoutType.equals("full", ignoreCase = true)) {
        val gridCap = (config.displayRows ?: 1).coerceAtLeast(1) *
            (config.displayColumns ?: 1).coerceAtLeast(1)
        enabled.take(gridCap.coerceAtMost(enabled.size))
    } else {
        val limit = (config.noOfCounters ?: enabled.size).coerceAtLeast(1)
        enabled.take(limit.coerceAtMost(enabled.size))
    }
}

private fun formatTokenByPattern(token: String?, pattern: String?): String? {
    if (token == null) return null
    if (pattern.isNullOrBlank()) return token
    
    return try {
        val trimmedPattern = pattern.trim()
        // e.g. "T2" → 01, 23 (digit width only; no literal "T" before the number on screen)
        Regex("^T(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(trimmedPattern)?.let { match ->
            val digitLen = match.groupValues[1].toIntOrNull() ?: return@let null
            val num = token.toIntOrNull() ?: return token
            return num.toString().padStart(digitLen, '0')
        }

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
