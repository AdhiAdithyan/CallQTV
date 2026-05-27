package com.softland.callqtv.ui.display

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.softland.callqtv.ui.settings.DeviceMessageDialog
import com.softland.callqtv.ui.settings.LicenseExpiredDialog
import com.softland.callqtv.ui.settings.TvConfigurationUnavailableScreen
import com.softland.callqtv.utils.AnimatedLoadingOverlay
import com.softland.callqtv.utils.VoiceInitializationDialog

/**
 * Blocking overlays for [com.softland.callqtv.ui.TokenDisplayScreen]:
 * cold-start readiness, TTS prep, config load, approval, license, config unavailable, MQTT retry.
 */
@Composable
fun TokenDisplayBlockingOverlays(
    coldStartMessage: String? = null,
    showTtsLoading: Boolean,
    isLoading: Boolean,
    isPendingApproval: Boolean,
    isLicenseExpired: Boolean,
    showConfigUnavailable: Boolean,
    errorMessage: String?,
    macAddress: String,
    appVersion: String,
    isNetworkAvailable: Boolean,
    onReloadConfig: () -> Unit,
    onRetryLicense: () -> Unit,
    showMqttRetryDialog: Boolean,
    onDismissMqttDialog: () -> Unit,
    mqttRetryAttempt: Int,
    mqttRetryFocusRequester: FocusRequester,
    onMqttRetry: () -> Unit,
) {
    val loadingMessage = when {
        !coldStartMessage.isNullOrBlank() -> coldStartMessage
        isLoading -> "Loading TV configuration.\nPlease wait..."
        else -> null
    }

    if (isLicenseExpired) {
        if (!loadingMessage.isNullOrBlank()) {
            AnimatedLoadingOverlay(message = loadingMessage, isVisible = true)
        }
        LicenseExpiredDialog(
            macAddress = macAddress,
            errorMessage = errorMessage,
            onRefresh = onRetryLicense,
        )
        return
    }

    if (isPendingApproval) {
        if (!loadingMessage.isNullOrBlank()) {
            AnimatedLoadingOverlay(message = loadingMessage, isVisible = true)
        }
        DeviceMessageDialog(
            title = "Device Awaiting Approval",
            message = errorMessage
                ?: "This display is awaiting approval from the administrator.\n" +
                "Please contact support or tap Retry after approval.",
            deviceId = macAddress,
            confirmLabel = "Retry",
            onConfirm = onReloadConfig,
            onDismissRequest = { /* Prevent dismiss */ },
        )
        return
    }

    if (!coldStartMessage.isNullOrBlank()) {
        AnimatedLoadingOverlay(message = coldStartMessage, isVisible = true)
        return
    }

    if (showTtsLoading && !isLoading) {
        VoiceInitializationDialog(isVisible = true)
    }

    if (showConfigUnavailable && !isLoading) {
        TvConfigurationUnavailableScreen(
            macAddress = macAddress,
            appVersion = appVersion,
            isNetworkAvailable = isNetworkAvailable,
            errorMessage = errorMessage,
            onRetry = onReloadConfig,
        )
    }

    if (isLoading) {
        AnimatedLoadingOverlay(
            message = "Loading TV configuration.\nPlease wait...",
            isVisible = true,
        )
    }

    if (showMqttRetryDialog) {
        DeviceMessageDialog(
            title = "BLUCON Connection Failed",
            message = "The display could not connect to the messaging server (Attempt $mqttRetryAttempt).\n" +
                "Please check your network or broker settings, then tap Retry.",
            deviceId = macAddress,
            confirmLabel = "Retry Connection",
            onConfirm = onMqttRetry,
            dismissLabel = "Close",
            onDismiss = onDismissMqttDialog,
            onDismissRequest = onDismissMqttDialog,
            showErrorIconInTitle = true,
            dialogWidthFraction = 0.9f,
            confirmFocusRequester = mqttRetryFocusRequester,
        )
    }
}
