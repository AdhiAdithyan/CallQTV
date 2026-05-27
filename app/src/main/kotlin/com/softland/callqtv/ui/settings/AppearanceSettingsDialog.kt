package com.softland.callqtv.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import com.softland.callqtv.ui.ads.MediaEngine
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.softland.callqtv.R
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.ui.theme.CallQtvDimens
import com.softland.callqtv.ui.theme.CallQtvSettingsColors
import com.softland.callqtv.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Main settings surface for display customization, audio behavior, portal read-only values,
 * and system diagnostics actions.
 *
 * The dialog orchestrates child pickers/help overlays, persists user-facing toggles to local
 * preferences, and ensures TV-remote focus lands on the first actionable control per tab.
 */
fun AppearanceSettingsDialog(
    context: Context,
    tvConfig: TvConfigEntity? = null,
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
    val palette = CallQtvSettingsColors

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
    var licenseEndRaw by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val authPrefs = context.getSharedPreferences(
                AppSharedPreferences.AUTHENTICATION,
                Context.MODE_PRIVATE,
            )
            currentCounterHex = ThemeColorManager.getCounterBackgroundColor(context)
            currentTokenHex = ThemeColorManager.getTokenBackgroundColor(context)
            currentThemeHex = ThemeColorManager.getSelectedThemeColorHex(context)
            notificationSoundKey = ThemeColorManager.getNotificationSoundKey(context)
            customerId = authPrefs.getInt(PreferenceHelper.customer_id, 0)
            licenseEndRaw = authPrefs.getString(PreferenceHelper.product_license_end, null)
            is24Hour = ThemeColorManager.is24HourFormat(context)
            isAdSoundEnabled = ThemeColorManager.isAdSoundEnabled(context)
            isYouTubeAdsEnabled = ThemeColorManager.isYouTubeAdsEnabled(context)
            isYouTubeStrictAutoplay = ThemeColorManager.isYouTubeStrictAutoplay(context)
            isYouTubePlayUntilEnded = ThemeColorManager.isYouTubePlayUntilEnded(context)
            isOfflineAdsEnabled = PreferenceHelper.isOfflineAdsEnabled(context)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // Always open Settings with the first tab selected.
    LaunchedEffect(Unit) { selectedTabIndex = 0 }
    val settingsHelpFocusRequester = remember { FocusRequester() }
    TvDelayedFocusEffect(
        enabled = showSettingsHelpDialog,
        focusRequester = settingsHelpFocusRequester,
        delayMillis = 120L,
    )
    var showOfflineConfirmDialog by remember { mutableStateOf(false) }
    // Portal (server TV config) before System; System stays last.
    val tabs = listOf("Display", "Audios", "Other", "Portal", "System")
    val firstTabItemFocusRequesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    val showMainSettingsDialog = !showSoundPicker &&
        !showThemeColorPicker &&
        !showCounterColorPicker &&
        !showTokenColorPicker &&
        !showOfflineConfirmDialog &&
        !showSettingsHelpDialog
    LaunchedEffect(showMainSettingsDialog) {
        if (!showMainSettingsDialog) return@LaunchedEffect
        delay(120L)
        try {
            firstTabItemFocusRequesters[selectedTabIndex].requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

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
        SettingsHelpDialog(
            onDismiss = { showSettingsHelpDialog = false },
            focusRequester = settingsHelpFocusRequester,
        )
    } else {
        AlertDialog(
            modifier = Modifier.fillMaxWidth(CallQtvDimens.SettingsDialogWidthFraction),
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsDialogTabRow(
                        tabs = tabs,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(CallQtvDimens.SettingsTabContentHeight)) {
                        when (selectedTabIndex) {
                        0 -> { // Display Tab
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(CallQtvDimens.SettingsTabContentHeight),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                            ) {
                                item(span = { GridItemSpan(2) }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ColorPickerButton(
                                            label = "App Theme",
                                            hex = currentThemeHex,
                                            onClick = { showThemeColorPicker = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(firstTabItemFocusRequesters[0]),
                                        )
                                        ColorPickerButton(
                                            label = "Counter BG",
                                            hex = currentCounterHex,
                                            onClick = { showCounterColorPicker = true },
                                            modifier = Modifier.weight(1f),
                                        )
                                        ColorPickerButton(
                                            label = "Token BG",
                                            hex = currentTokenHex,
                                            onClick = { showTokenColorPicker = true },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                item(span = { GridItemSpan(2) }) {
                                    GridSettingsItem(
                                        title = "Token blink",
                                        onClick = null,
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        ThemeColorManager.setTokenBlinkMode(
                                                            context,
                                                            TokenBlinkMode.WHOLE_TILE,
                                                        )
                                                        onTokenBlinkModeChange(TokenBlinkMode.WHOLE_TILE)
                                                    },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = tokenBlinkMode == TokenBlinkMode.WHOLE_TILE,
                                                    onClick = {
                                                        ThemeColorManager.setTokenBlinkMode(
                                                            context,
                                                            TokenBlinkMode.WHOLE_TILE,
                                                        )
                                                        onTokenBlinkModeChange(TokenBlinkMode.WHOLE_TILE)
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = palette.Primary,
                                                        unselectedColor = palette.MutedText,
                                                    ),
                                                )
                                                Text(
                                                    "Whole tile blinks",
                                                    fontSize = 15.sp,
                                                    color = palette.Text,
                                                    modifier = Modifier.padding(start = 4.dp),
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        ThemeColorManager.setTokenBlinkMode(
                                                            context,
                                                            TokenBlinkMode.TEXT_ONLY,
                                                        )
                                                        onTokenBlinkModeChange(TokenBlinkMode.TEXT_ONLY)
                                                    },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = tokenBlinkMode == TokenBlinkMode.TEXT_ONLY,
                                                    onClick = {
                                                        ThemeColorManager.setTokenBlinkMode(
                                                            context,
                                                            TokenBlinkMode.TEXT_ONLY,
                                                        )
                                                        onTokenBlinkModeChange(TokenBlinkMode.TEXT_ONLY)
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = palette.Primary,
                                                        unselectedColor = palette.MutedText,
                                                    ),
                                                )
                                                Text(
                                                    "Text only blinks",
                                                    fontSize = 15.sp,
                                                    color = palette.Text,
                                                    modifier = Modifier.padding(start = 4.dp),
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
                                modifier = Modifier.fillMaxWidth().height(CallQtvDimens.SettingsTabContentHeight),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                            ) {
                                val currentSoundLabel =
                                    ThemeColorManager.notificationSoundLabel(notificationSoundKey)
                                
                                item {
                                    GridSettingsItem(
                                        title = "Notification Sound",
                                        onClick = { showSoundPicker = true },
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                        modifier = Modifier.focusRequester(firstTabItemFocusRequesters[1]),
                                    ) {
                                        Text(currentSoundLabel, fontSize = 16.sp, color = palette.Primary)
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "Advertisement Sound",
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                        onClick = {
                                            isAdSoundEnabled = !isAdSoundEnabled
                                            ThemeColorManager.setAdSoundEnabled(context, isAdSoundEnabled)
                                            MediaEngine.updateVolume(context)
                                        }
                                    ) {
                                        Checkbox(checked = isAdSoundEnabled, onCheckedChange = { 
                                            isAdSoundEnabled = it
                                            ThemeColorManager.setAdSoundEnabled(context, it)
                                            MediaEngine.updateVolume(context)
                                        }, modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = palette.Primary,
                                                uncheckedColor = palette.MutedText,
                                                checkmarkColor = Color.Black
                                            ))
                                    }
                                }
                            }
                        }
                        2 -> { // Other Tab
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(CallQtvDimens.SettingsTabContentHeight),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                            ) {
                                item {
                                    GridSettingsItem(
                                        title = "Help / Settings Guide",
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                        modifier = Modifier.focusRequester(firstTabItemFocusRequesters[2]),
                                        onClick = { showSettingsHelpDialog = true }
                                    ) {
                                        Text(
                                            "Understand what each setting does",
                                            fontSize = 15.sp,
                                            color = palette.Text
                                        )
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "24-Hour Format",
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                        onClick = {
                                            is24Hour = !is24Hour
                                            ThemeColorManager.set24HourFormat(context, is24Hour)
                                        }
                                    ) {
                                        Checkbox(checked = is24Hour, onCheckedChange = { 
                                            is24Hour = it
                                            ThemeColorManager.set24HourFormat(context, it)
                                        }, modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = palette.Primary,
                                                uncheckedColor = palette.MutedText,
                                                checkmarkColor = Color.Black
                                            ))
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "Offline Advertisements",
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
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
                                                checkedColor = palette.Primary,
                                                uncheckedColor = palette.MutedText,
                                                checkmarkColor = Color.Black
                                            ))
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "Allow YouTube Ads",
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                        onClick = {
                                            isYouTubeAdsEnabled = !isYouTubeAdsEnabled
                                            ThemeColorManager.setYouTubeAdsEnabled(context, isYouTubeAdsEnabled)
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isYouTubeAdsEnabled,
                                            onCheckedChange = {
                                                isYouTubeAdsEnabled = it
                                                ThemeColorManager.setYouTubeAdsEnabled(context, it)
                                            },
                                            modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = palette.Primary,
                                                uncheckedColor = palette.MutedText,
                                                checkmarkColor = Color.Black
                                            )
                                        )
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "YouTube Strict Autoplay",
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                        onClick = {
                                            isYouTubeStrictAutoplay = !isYouTubeStrictAutoplay
                                            ThemeColorManager.setYouTubeStrictAutoplay(context, isYouTubeStrictAutoplay)
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isYouTubeStrictAutoplay,
                                            onCheckedChange = {
                                                isYouTubeStrictAutoplay = it
                                                ThemeColorManager.setYouTubeStrictAutoplay(context, it)
                                            },
                                            modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = palette.Primary,
                                                uncheckedColor = palette.MutedText,
                                                checkmarkColor = Color.Black
                                            )
                                        )
                                    }
                                }

                                item {
                                    GridSettingsItem(
                                        title = "YouTube: Play Until Ended",
                                        titleColor = palette.Primary,
                                        cardColor = palette.Card,
                                        borderColor = palette.Border,
                                        onClick = {
                                            isYouTubePlayUntilEnded = !isYouTubePlayUntilEnded
                                            ThemeColorManager.setYouTubePlayUntilEnded(context, isYouTubePlayUntilEnded)
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isYouTubePlayUntilEnded,
                                            onCheckedChange = {
                                                isYouTubePlayUntilEnded = it
                                                ThemeColorManager.setYouTubePlayUntilEnded(context, it)
                                            },
                                            modifier = Modifier.scale(0.9f).offset(x = (-8).dp),
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = palette.Primary,
                                                uncheckedColor = palette.MutedText,
                                                checkmarkColor = Color.Black
                                            )
                                        )
                                    }
                                }

                                if (tvConfig != null) {
                                    item(span = { GridItemSpan(2) }) {
                                        GridSettingsItem(
                                            title = "Clear saved token history",
                                            titleColor = palette.Error,
                                            cardColor = palette.Card,
                                            borderColor = palette.Border,
                                            onClick = { showClearConfirmDialog = true }
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Action to reset active token list",
                                                    fontSize = 15.sp,
                                                    color = palette.Error.copy(alpha = 0.8f),
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Checkbox(
                                                    checked = false,
                                                    onCheckedChange = { if (it) showClearConfirmDialog = true },
                                                    modifier = Modifier.scale(0.9f).offset(x = (-6).dp),
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = palette.Error,
                                                        uncheckedColor = palette.Error,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> { // Portal Tab (read-only server TV configuration)
                            if (tvConfig != null) {
                                PortalConfigurationGrid(
                                    items = buildPortalConfigItems(
                                        config = tvConfig,
                                        isTokenAnnouncementEnabled = isTokenAnnouncementEnabled,
                                        isCounterAnnouncementEnabled = isCounterAnnouncementEnabled,
                                        isCounterPrefixEnabled = isCounterPrefixEnabled,
                                    ),
                                    firstItemFocusRequester = firstTabItemFocusRequesters[3],
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(CallQtvDimens.SettingsTabContentHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Portal configuration appears here after TV configuration is loaded from the server (open the main display or complete registration first).",
                                        fontSize = 12.sp,
                                        color = palette.MutedText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                        4 -> { // System Tab
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                            .padding(1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.callq_tv_logo),
                                            contentDescription = null,
                                            modifier = Modifier.size(35.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Column {
                                        Text(companyName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("System Information", fontSize = 14.sp, color = palette.Primary)
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
                                InfoRow("Company ID", String.format(Locale.ROOT, "%04d", customerId))
                                InfoRow("Device ID", macAddress)
                                InfoRow("App Version", appVersion)
                                SystemLicenseInfoRows(
                                    licenseEndRaw = licenseEndRaw,
                                    daysUntilExpiry = daysUntilExpiry,
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                OutlinedButton(
                                    modifier = Modifier
                                        .align(Alignment.Start)
                                        .focusRequester(firstTabItemFocusRequesters[4]),
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
                                    },
                                ) {
                                    Text(if (isExportingSnapshot) "Exporting..." else "Export Logs/Config Snapshot")
                                }
                                exportSnapshotStatus?.let { status ->
                                    Text(
                                        text = status,
                                        fontSize = 12.sp,
                                        color = palette.MutedText,
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    Text(
                                        "Developed by",
                                        fontSize = 14.sp,
                                        color = palette.Primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_softland_logo),
                                        contentDescription = "Softland India Ltd",
                                        modifier = Modifier.height(32.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
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
