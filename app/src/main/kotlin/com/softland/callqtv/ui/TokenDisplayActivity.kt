package com.softland.callqtv.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import android.graphics.Color as AndroidColor
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
        
        viewModel.loadData(mqttViewModel)

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
    
    val isLoading by viewModel.isLoading.observeAsState(true)
    val errorMessage by viewModel.errorMessage.observeAsState(null)
    val isPendingApproval by viewModel.isPendingApproval.observeAsState(false)
    val config by viewModel.config.observeAsState(null)
    val counters by viewModel.counters.observeAsState(emptyList())
    val adFiles by viewModel.adFiles.observeAsState(emptyList())
    val daysUntilExpiry by viewModel.daysUntilExpiry.observeAsState(null)
    val currentDateTime by viewModel.currentDateTime.observeAsState("")

    val mqttConnected by mqttViewModel.getConnectionStatus().observeAsState(false)
    val mqttError by mqttViewModel.getErrorMessage().observeAsState("")
    val isAutoRetryExhausted by mqttViewModel.isAutoRetryExhausted().observeAsState(false)
    val tokensPerCounter by mqttViewModel.getTokensPerCounter().observeAsState(emptyMap())
    
    val macAddress = viewModel.macAddress
    val appVersion = remember { context.getString(R.string.app_version) }
    
    val networkViewModel = viewModel<com.softland.callqtv.viewmodel.NetworkViewModel>()
                val isNetworkAvailable by networkViewModel.getNetworkLiveData(context).observeAsState(initial = true)
    var showMqttRetryDialog by remember { mutableStateOf(false) }
    var showTtsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isAutoRetryExhausted, mqttError) {
        if (isAutoRetryExhausted && mqttError.isNotBlank()) {
            showMqttRetryDialog = true
        }
    }

    // Pre-initialize announcement engine to avoid delay on first call
    LaunchedEffect(config) {
        config?.let { cfg ->
            showTtsLoading = true
            TokenAnnouncer.initialize(context, cfg.audioLanguage) { success ->
                showTtsLoading = false
            }
        }
    }

    val latestConfigState = rememberUpdatedState(config)
    val latestCountersState = rememberUpdatedState(counters)

    LaunchedEffect(Unit) {
        mqttViewModel.tokenUpdateChannel.receiveAsFlow().collect { pair ->
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

            // 2. Update in‑memory history & UI for valid counters only
            val shouldAnnounce = mqttViewModel.processTokenUpdate(counterIdOrName, tokenLabel)
            if (!shouldAnnounce) {
                return@collect
            }

            // 3. Announce ONLY when enabled in configuration
            if (currentConfig?.enableTokenAnnouncement == true) {
                val displayName =
                    (actualCounter.name?.takeIf { it.isNotBlank() }
                        ?: actualCounter.defaultName?.takeIf { it.isNotBlank() }
                        ?: "Counter ${actualCounter.buttonIndex}")

                val announcementCounterName =
                    if (currentConfig.enableCounterAnnouncement == true) {
                        displayName
                    } else {
                        ""
                    }

                // Announce and suspend until TTS callback completes
                suspendCancellableCoroutine<Unit> { continuation ->
                    TokenAnnouncer.announceToken(
                        context = context,
                        audioLanguage = currentConfig.audioLanguage,
                        counterName = announcementCounterName,
                        tokenLabel = tokenLabel,
                        onDone = {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    )
                }
                mqttViewModel.markAsAnnounced(counterIdOrName, tokenLabel)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (mqttError.isNotBlank() && !showMqttRetryDialog) {
            MqttErrorBar(mqttError, isAutoRetryExhausted) { mqttViewModel.retryConnect() }
        }

        if (config != null) {
            val cfg = config!!
            
            TokenDisplayContent(
                config = cfg,
                macAddress = macAddress,
                appVersion = appVersion,
                isMqttConnected = mqttConnected,
                isNetworkAvailable = isNetworkAvailable,
                counters = counters,
                adFiles = adFiles,
                tokensPerCounter = tokensPerCounter,
                daysUntilLicenseExpiry = daysUntilExpiry,
                dateTime = currentDateTime,
                counterBgHex = counterBgHex,
                tokenBgHex = tokenBgHex,
                onThemeChange = onThemeChange,
                onCounterBgChange = onCounterBgChange,
                onTokenBgChange = onTokenBgChange,
                onRefresh = { viewModel.loadData(mqttViewModel) }
            )
        }
    }

    // Overlays and Dialogs - Placed at the end to ensure they draw on top
    if (showTtsLoading) {
        AnimatedLoadingOverlay(message = "Setting up voice announcement...", isVisible = true)
    }

    if (isLoading) {
        AnimatedLoadingOverlay(
            message = "Loading TV configuration.\nPlease wait...",
            isVisible = true
        )
    } else if (isPendingApproval) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = {
                Text(
                    "Device Awaiting Approval",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    errorMessage
                        ?: "This display is awaiting approval from the administrator.\n" +
                           "Please contact support or tap Retry after approval.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.loadData(mqttViewModel) }) {
                    Text("Retry")
                }
            }
        )
    } else if (!errorMessage.isNullOrBlank() && !isAutoRetryExhausted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.loadData(mqttViewModel) }) {
                Text("Retry Loading")
            }
        }
    }

    // MQTT Retry Dialog - Move to the end so it always appears on top
    if (showMqttRetryDialog && mqttError.isNotBlank()) {
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.8f),
            onDismissRequest = { showMqttRetryDialog = false },
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
                        "The display could not connect to the messaging server.\n" +
                        "Please check your network or broker settings, then tap Retry.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: $mqttError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showMqttRetryDialog = false
                    mqttViewModel.retryConnect()
                }) {
                    Text("Retry Connection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMqttRetryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun MqttErrorBar(error: String, exhausted: Boolean, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (exhausted) "MQTT Error: $error" else "MQTT: Connecting... (Retrying)",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 13.sp
        )
        if (exhausted) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.height(32.dp)
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
    onRefresh: () -> Unit
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isPortrait = screenHeight > screenWidth
    
    val scale = remember(screenWidth, screenHeight, isPortrait) {
        if (isPortrait) {
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

    val showAds = config.showAds == true
    val adPlacement = config.adPlacement ?: "right"
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
            isPortrait = isPortrait,
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
            onRefresh = onRefresh,
            onClearTokenHistoryAndRefresh = {
                mqttViewModel.clearTokenHistory()
                viewModel.loadData(mqttViewModel)
            }
        )

        Spacer(modifier = Modifier.height(responsivePadding))

        val hasAds = showAds && adFiles.isNotEmpty()
        val layoutType = config.layoutType ?: "1"

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (hasAds) {
                if (isPortrait) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (adPlacement.equals("top", ignoreCase = true) || adPlacement.equals("left", ignoreCase = true)) {
                            Box(modifier = Modifier.weight(0.30f).fillMaxWidth()) { AdArea(adFiles, config) }
                            Box(modifier = Modifier.weight(0.70f).fillMaxWidth()) { 
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex) 
                            }
                        } else {
                            Box(modifier = Modifier.weight(0.70f).fillMaxWidth()) { 
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex) 
                            }
                            Box(modifier = Modifier.weight(0.30f).fillMaxWidth()) { AdArea(adFiles, config) }
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (adPlacement.equals("left", ignoreCase = true)) {
                            Box(modifier = Modifier.weight(0.30f).fillMaxHeight()) { AdArea(adFiles, config) }
                            Box(modifier = Modifier.weight(0.70f).fillMaxHeight()) { 
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex) 
                            }
                        } else {
                            Box(modifier = Modifier.weight(0.70f).fillMaxHeight()) { 
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex) 
                            }
                            Box(modifier = Modifier.weight(0.30f).fillMaxHeight()) { AdArea(adFiles, config) }
                        }
                    }
                }
            } else {
                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex)
            }
        }

        val scrollEnabled = config.scrollEnabled.equals("on", ignoreCase = true)
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
                 isPortrait = isPortrait
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
    onRefresh: () -> Unit,
    onClearTokenHistoryAndRefresh: () -> Unit
) {
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
             androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.callq_tv_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height((if (isPortrait) 48 else 68).dp * scale),
                contentScale = ContentScale.Fit
            )
        }

        // Center: Company Name
        Box(modifier = Modifier.weight(2f)) {
            Text(
                text = companyName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold, 
                    fontSize = (if (isPortrait) 26 else 36).sp * scale
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
                        text = dateTime, 
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (if (isPortrait) 12 else 18).sp * scale),
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
                        .padding(8.dp)
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
                        .padding(8.dp)
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
    val color = if (isOnline) Color(0xFF056009) else MaterialTheme.colorScheme.error
    val iconSize = ((if (isPortrait) 16 else 20) * scale).dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            imageVector = if (isOnline) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
            contentDescription = "BLUCON",
            tint = color,
            modifier = Modifier.size(iconSize)
        )
        Text(
//            text = "BLUCON: ${if (isOnline) "Online" else "Offline"}",
            text = ": ${if (isOnline) "Online" else "Offline"}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = (if (isPortrait) 12 else 18).sp * scale),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun NetworkStatusIndicator(isOnline: Boolean, isPortrait: Boolean, scale: Float) {
    val networkIconRes = if (isOnline) R.drawable.ic_network_available else R.drawable.ic_network_unavailable
    val networkIconColor = if (isOnline) Color(0xFF2E7D32) else Color(0xFFB71C1C)
    val iconSize = ((if (isPortrait) 16 else 20) * scale).dp
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
        Text(
//            text = "NETWORK: ${if (isOnline) "Online" else "Offline"}",
            text = ": ${if (isOnline) "Online" else "Offline"}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = labelFontSize),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun AdArea(adFiles: List<AdFileEntity>, config: TvConfigEntity) {
    // Sort and remember ads stably
    val orderedAds = remember(adFiles) { adFiles.sortedBy { it.position } }
    var currentAdIndex by remember { mutableStateOf(0) }
    val intervalSeconds = (config.adInterval ?: 5).coerceAtLeast(1)
    
    // Safety check: ensure index is within current list bounds
    val safeIndex = if (orderedAds.isNotEmpty()) currentAdIndex % orderedAds.size else 0
    val currentAd = orderedAds.getOrNull(safeIndex)
    
    val isVideo = remember(currentAd) { 
        val path = currentAd?.filePath?.lowercase() ?: ""
        path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".mov") || path.endsWith(".3gp") || path.endsWith(".webm")
    }

    // Single source of truth for moving to the next ad
    val moveToNext = {
        if (orderedAds.isNotEmpty()) {
            currentAdIndex = (currentAdIndex + 1) % orderedAds.size
        }
    }

    // Timer logic for IMAGES only
    LaunchedEffect(safeIndex, isVideo, orderedAds.size) {
        if (orderedAds.isEmpty()) return@LaunchedEffect
        
        if (!isVideo) {
            // Delay for images, then move to next
            delay(intervalSeconds * 1000L)
            moveToNext()
        }
        // For videos, we rely strictly on the player listener
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), contentAlignment = Alignment.Center) {
        if (orderedAds.isEmpty()) {
            Text("No Ads", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Crossfade(targetState = currentAd, animationSpec = tween(600), label = "ad_fade") { ad ->
                if (ad != null) {
                    val path = ad.filePath.lowercase()
                    val adIsVideo = path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".mov") || path.endsWith(".3gp") || path.endsWith(".webm")
                    
                    if (adIsVideo) {
                        AdVideoPlayer(
                            videoUrl = ad.filePath,
                            onVideoEnded = { moveToNext() }
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(ad.filePath)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Ad",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdVideoPlayer(videoUrl: String, onVideoEnded: () -> Unit) {
    val context = LocalContext.current
    val player = remember(context) { MediaEngine.get(context) }
    
    // Use a stable reference to the callback to avoid listener leaks and multiple calls
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(videoUrl) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    mainHandler.post { latestOnVideoEnded() }
                }
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                mainHandler.post { latestOnVideoEnded() }
            }
        }
        
        player.addListener(listener)
        
        onDispose {
            player.removeListener(listener)
        }
    }
    
    // Load and play the video
    LaunchedEffect(videoUrl) {
        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    // Shared player: never stop() on dispose of a single view, as it might kill the next video's start
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

/**
 * Global Media Engine to reuse ExoPlayer instance and reduce loading overhead
 */
object MediaEngine {
    private var player: ExoPlayer? = null
    
    fun get(context: Context): ExoPlayer {
        if (player == null) {
            val httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
            val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
            
            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = 0f // Mute advertisement audio
                }
        }
        return player!!
    }
    
    fun prepareNext(context: Context, url: String) {
        // Optional: Pre-fill buffers for next video
        // ExoPlayer 2/Media3 handles some of this if items are added to a playlist,
        // but for now we reuse the instance to save initialization time (the biggest delay).
    }

    fun shutdown() {
        player?.release()
        player = null
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
    tokenBgHex: String
) {
    Box(modifier = Modifier.fillMaxSize().padding(1.dp)) {
        val numCounters = counters.size

        if (numCounters > 4) {
            if (layoutType == "2") {
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    val firstHalfCount = (numCounters + 1) / 2
                    val firstHalf = counters.take(firstHalfCount)
                    val secondHalf = counters.drop(firstHalfCount)

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        firstHalf.forEach { CounterBoard(it, tokensPerCounter, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex) }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { CounterBoard(it, tokensPerCounter, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex) }
                        if (secondHalf.size < firstHalf.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    val firstHalfCount = (numCounters + 1) / 2
                    val firstHalf = counters.take(firstHalfCount)
                    val secondHalf = counters.drop(firstHalfCount)

                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        firstHalf.forEach { CounterBoard(it, tokensPerCounter, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex) }
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { CounterBoard(it, tokensPerCounter, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex) }
                        if (secondHalf.size < firstHalf.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            if (layoutType == "2") {
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter -> CounterBoard(counter, tokensPerCounter, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex) }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter -> CounterBoard(counter, tokensPerCounter, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex) }
                }
            }
        }
    }
}

@Composable
fun CounterBoard(
    counter: CounterEntity,
    tokensPerCounter: Map<String, List<String>>,
    config: TvConfigEntity,
    rows: Int,
    columns: Int,
    modifier: Modifier,
    scale: Float,
    counterBgHex: String,
    tokenBgHex: String
) {
    val counterName = remember(counter.name, counter.defaultName) { 
        (counter.name.orEmpty().ifBlank { counter.defaultName.orEmpty().ifBlank { "Counter" } }).uppercase()
    }
    
    val tokens = remember(tokensPerCounter, counter, counterName) {
        val cid = counter.counterId.orEmpty().trim()
        val cname = counter.name.orEmpty().trim()
        val dname = counter.defaultName.orEmpty().trim()
        
        val rawList = tokensPerCounter[cid] 
            ?: tokensPerCounter[cname] 
            ?: tokensPerCounter[dname]
            ?: tokensPerCounter.entries.find { 
                val keyInt = it.key.toIntOrNull()
                val cidInt = cid.toIntOrNull()
                keyInt != null && (keyInt == cidInt || keyInt == counter.buttonIndex)
            }?.value
            ?: (if (tokensPerCounter.containsKey("__default__")) tokensPerCounter["__default__"] else null)
            ?: emptyList()
            
        // Filter out "0", handles duplicates, and maintain order (first is latest)
        rawList.filter { it != "0" }.distinct()
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

    // Blink only for blinkSeconds; if blinkSeconds <= 0, blink indefinitely (backward compat)
    var blinkActive by remember { mutableStateOf(true) }
    LaunchedEffect(shouldBlink, blinkSeconds) {
        if (shouldBlink && blinkSeconds > 0) {
            delay(blinkSeconds * 1000L)
            blinkActive = false
        }
    }

    val blinkAlpha by if (shouldBlink && blinkActive) {
        rememberInfiniteTransition(label = "counter_blink").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)), 
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent) // Use Transparent to show Brush
    ) {
        Box(modifier = Modifier.fillMaxSize().background(counterBgBrush)) {
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
                    val totalSlots = config.tokensPerCounter ?: (rows * columns) // Default to standard grid size
                    items(totalSlots) { index ->
                        val token = tokens.getOrNull(index)
                        
                        // Logic: First item is current token.
                        // If token list is empty, index 0 is empty current token slot.
                        val isFirst = index == 0
                        
                        // Scale font size for historical tokens
                        val currentFontSize = if (isFirst) tokenFontSize else tokenFontSize * 0.85f
                        
                        val textColorToUse = if (isFirst) currentTokenTextColor else previousTokenTextColor
                        
                        TokenCard(
                            token = token, 
                            isPrimary = isFirst, 
                            scale = scale, 
                            textColor = textColorToUse,
                            bgBrush = tokenBgBrush,
                            fontSize = currentFontSize,
                            blinkAlpha = if (isFirst && shouldBlink && token != null) blinkAlpha else 1f
                        )
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
    blinkAlpha: Float = 1f
) {
    // Dynamic height based on font size to prevent clipping
    // Reduced height multiplier to tighten padding around the text
    val cardHeight = (fontSize * 1.6f * scale).coerceIn(32f, 120f).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .padding(1.dp)
            .graphicsLayer { this.alpha = blinkAlpha },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(bgBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = token ?: "",
                fontWeight = FontWeight.Bold,
                fontSize = (fontSize * scale).sp,
                color = textColor,
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
    companyName: String
) {
    var showCounterColorPicker by remember { mutableStateOf(false) }
    var showTokenColorPicker by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var currentCounterHex by remember { mutableStateOf("#FFFFFF") }
    var currentTokenHex by remember { mutableStateOf("#FFFFFF") }
    var customerId by remember { mutableStateOf(0) }
    var currentThemeHex by remember { mutableStateOf("#2196F3") }
    var is24Hour by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            currentCounterHex = ThemeColorManager.getCounterBackgroundColor(context)
            currentTokenHex = ThemeColorManager.getTokenBackgroundColor(context)
            currentThemeHex = ThemeColorManager.getSelectedThemeColorHex(context)
            customerId = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
                .getInt(PreferenceHelper.customer_id, 0)
            is24Hour = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                .getBoolean("use_24_hour_format", true)
        }
    }

    // Theme Dropdown
    var expanded by remember { mutableStateOf(false) }
    val currentThemeName = ThemeColorManager.themeColorOptions.find { it.hexCode == currentThemeHex }?.name ?: "Custom"

    if (showCounterColorPicker) {
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
    } else {
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.9f),
            onDismissRequest = onDismiss,
            title = { Text("Settings", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // 1. Device Info
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                            // Header: Icon + Company Name
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = R.drawable.callq_tv_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = companyName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "System Information",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            
                            InfoRow("Company ID", String.format("%04d", customerId))
                            InfoRow("Device ID", macAddress)
                            InfoRow("App Version", appVersion)
                            if (daysUntilExpiry != null) {
                                val expiryText = if (daysUntilExpiry <= 0) "Expired" else "Expires in $daysUntilExpiry days"
                                val color = if (daysUntilExpiry <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                InfoRow("License", expiryText, color)
                            }
                            val tokenAnnText = if (isTokenAnnouncementEnabled == true) "Enabled" else "Disabled"
                            val counterAnnText = if (isCounterAnnouncementEnabled == true) "Enabled" else "Disabled"
                            InfoRow("Token announcement", tokenAnnText)
                            InfoRow("Counter announcement", counterAnnText)
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // App theme + counter/token background in a single horizontal row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.4f)) {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = currentThemeName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("App Theme") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded }
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    ThemeColorManager.themeColorOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .background(
                                                                ThemeColorManager.getBackgroundBrush(option.hexCode),
                                                                CircleShape
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        option.name,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onThemeSelected(option.hexCode)
                                                currentThemeHex = option.hexCode
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(0.8f)) {
                            ColorPickerButton("Counter BG", currentCounterHex) { showCounterColorPicker = true }
                        }

                        Box(modifier = Modifier.weight(0.8f)) {
                            ColorPickerButton("Token BG", currentTokenHex) { showTokenColorPicker = true }
                        }
                    }

                    // Clear saved token details (with confirmation)
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Clear saved token details",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    HorizontalDivider()

                    // Time Format
                    val viewModel = viewModel<com.softland.callqtv.viewmodel.TokenDisplayViewModel>()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            val newState = !is24Hour
                            is24Hour = newState
                            context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("use_24_hour_format", newState).apply()
                            viewModel.setTimeFormat(newState)
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Time Format", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (is24Hour) "24-Hour (14:30)" else "12-Hour (2:30 PM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = is24Hour,
                            onCheckedChange = { 
                                is24Hour = it 
                                context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("use_24_hour_format", it).apply()
                                viewModel.setTimeFormat(it)
                            }
                        )
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
fun ColorPickerButton(label: String, hex: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(130.dp)
    ) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(ThemeColorManager.getBackgroundBrush(hex), RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 11.sp)
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
        modifier = Modifier.fillMaxWidth(0.9f),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
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

private const val MARQUEE_VELOCITY_DP_PER_SEC = 50f
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(vertical = (8 * scale).dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = scrollText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (if (isPortrait) 16 else 24).sp * scale,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = MARQUEE_VELOCITY_DP_PER_SEC.dp
                )
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.Start,
            maxLines = 1
        )
    }
}
