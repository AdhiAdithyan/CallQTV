package com.softland.callqtv.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.utils.TokenBlinkMode

/** Opens [AppearanceSettingsDialog] when [visible] is true (registration or main display). */
@Composable
fun AppearanceSettingsLauncher(
    visible: Boolean,
    context: Context,
    macAddress: String,
    appVersion: String,
    companyName: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit,
    onCounterBgChange: (String) -> Unit,
    onTokenBgChange: (String) -> Unit,
    onClearTokenHistoryAndRefresh: () -> Unit,
    tokenBlinkMode: TokenBlinkMode,
    onTokenBlinkModeChange: (TokenBlinkMode) -> Unit,
    tvConfig: TvConfigEntity? = null,
    daysUntilExpiry: Int? = null,
    isTokenAnnouncementEnabled: Boolean? = null,
    isCounterAnnouncementEnabled: Boolean? = null,
    isCounterPrefixEnabled: Boolean? = null,
) {
    if (!visible) return
    AppearanceSettingsDialog(
        context = context,
        tvConfig = tvConfig,
        onDismiss = onDismiss,
        onThemeSelected = onThemeSelected,
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
