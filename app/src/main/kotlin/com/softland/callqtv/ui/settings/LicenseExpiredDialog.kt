package com.softland.callqtv.ui.settings

import androidx.compose.runtime.Composable

@Composable
/**
 * Blocking license-expired dialog used by the main display flow.
 *
 * The dialog intentionally has no dismiss path and requires users to retry license refresh.
 */
fun LicenseExpiredDialog(
    macAddress: String,
    errorMessage: String?,
    onRefresh: () -> Unit,
) {
    DeviceMessageDialog(
        title = "License Expired",
        message = errorMessage
            ?: "Your Call-Q TV license has expired.\n" +
            "Token calls, announcements, and queue updates are paused until the license is renewed.\n" +
            "Tap Retry to fetch the latest license details from the server.",
        deviceId = macAddress,
        confirmLabel = "Retry",
        onConfirm = onRefresh,
        onDismissRequest = { /* Block all activity until license is valid */ },
        showErrorIconInTitle = true,
        dialogWidthFraction = 0.88f,
    )
}
