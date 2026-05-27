@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.softland.callqtv.R
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.ui.display.BluconStatusIndicator
import com.softland.callqtv.ui.display.NetworkStatusIndicator
import com.softland.callqtv.ui.settings.AppearanceSettingsLauncher
import com.softland.callqtv.ui.settings.SettingsIconButton
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.TokenBlinkMode
import com.softland.callqtv.viewmodel.MqttViewModel
import com.softland.callqtv.viewmodel.TokenDisplayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
/** Shows a small badge when there are queued/pending token calls to process. */
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
/** Shows a “connecting” badge with retry attempt + elapsed seconds while reconnecting BLUCON. */
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
/**
 * Inline error banner for broker connectivity.
 *
 * Displays a message derived from `error` and `exhausted`, and exposes a D-pad focusable
 * `Retry` button when a manual retry is needed.
 */
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
/**
 * Top-level content layout for the token display screen (header + body + footer).
 *
 * Handles sizing/portrait layout, counter rendering, and ties UI callbacks to
 * refresh/clear token history actions.
 */
internal fun TokenDisplayContent(
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
    onClearTokenHistoryAndRefresh: () -> Unit,
    blinkTriggers: Map<String, Long>,
    showReconnectBadge: Boolean,
    reconnectRetryAttempt: Int,
    reconnectUiSeconds: Int,
    pendingCallCount: Int
) {
    val viewModel = viewModel<com.softland.callqtv.viewmodel.TokenDisplayViewModel>()
    val mqttViewModel = viewModel<MqttViewModel>()
    val vipEmergencyTokensByKey by mqttViewModel.getVipEmergencyTokensByKey().observeAsState(emptyMap())
    val context = LocalContext.current
    var is24HourPref by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        is24HourPref = withContext(Dispatchers.Default) {
            ThemeColorManager.is24HourFormat(context)
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
            tvConfig = config,
            onRefresh = onRefresh,
            onClearTokenHistoryAndRefresh = onClearTokenHistoryAndRefresh,
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
                    vipEmergencyTokensByKey = vipEmergencyTokensByKey,
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
/** Renders the main token area (counter grid + optional ad area + footer area). */
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
    vipEmergencyTokensByKey: Map<String, Set<String>> = emptyMap(),
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
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipEmergencyTokensByKey = vipEmergencyTokensByKey)
                            }
                        } else {
                            Box(modifier = Modifier.weight(countersWeight).fillMaxWidth()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipEmergencyTokensByKey = vipEmergencyTokensByKey)
                            }
                            Box(modifier = Modifier.weight(adWeight).fillMaxWidth().clipToBounds()) { adAreaContent() }
                        }
                    }
                } else {
                    // Default Left/Right (Row) for Portrait if not specified as Top/Bottom
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isRight) {
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipEmergencyTokensByKey = vipEmergencyTokensByKey)
                            }
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                        } else {
                            Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                            Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipEmergencyTokensByKey = vipEmergencyTokensByKey)
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
                    vipEmergencyTokensByKey = vipEmergencyTokensByKey,
                )
            }
        } else {
            if (hasAds) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (adPlacement.equals("left", ignoreCase = true)) {
                        Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                        Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                            CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipEmergencyTokensByKey = vipEmergencyTokensByKey)
                        }
                    } else {
                        Box(modifier = Modifier.weight(countersWeight).fillMaxHeight()) {
                            CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipEmergencyTokensByKey = vipEmergencyTokensByKey)
                        }
                        Box(modifier = Modifier.weight(adWeight).fillMaxHeight().clipToBounds()) { adAreaContent() }
                    }
                }
            } else {
                CountersArea(countersToDisplay, tokensPerCounter, config, rows, columns, layoutType, scale, counterBgHex, tokenBgHex, isPortrait = usePortraitLayout, hasAds = hasAds, blinkTriggers = blinkTriggers, tokenBlinkMode = tokenBlinkMode, vipEmergencyTokensByKey = vipEmergencyTokensByKey)
            }
        }

    }
}

@Composable
/** Renders the bottom/footer bar, including optional scrolling ticker text and time/date. */
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
/**
 * Header area showing company/date/network/broker status + refresh CTA controls.
 *
 * Also embeds theme and background color selections via callbacks.
 */
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
    tvConfig: TvConfigEntity,
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

    val context = LocalContext.current
    AppearanceSettingsLauncher(
        visible = showThemeDialog,
        context = context,
        macAddress = macAddress,
        appVersion = appVersion,
        companyName = companyName,
        onDismiss = { showThemeDialog = false },
        onThemeSelected = onThemeChange,
        onCounterBgChange = onCounterBgChange,
        onTokenBgChange = onTokenBgChange,
        onClearTokenHistoryAndRefresh = onClearTokenHistoryAndRefresh,
        tokenBlinkMode = tokenBlinkMode,
        onTokenBlinkModeChange = onTokenBlinkModeChange,
        tvConfig = tvConfig,
        daysUntilExpiry = daysUntilExpiry,
        isTokenAnnouncementEnabled = isTokenAnnouncementEnabled,
        isCounterAnnouncementEnabled = isCounterAnnouncementEnabled,
        isCounterPrefixEnabled = isCounterPrefixEnabled,
    )

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

                SettingsIconButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier.padding(4.dp),
                    size = (36 * scale).dp,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

