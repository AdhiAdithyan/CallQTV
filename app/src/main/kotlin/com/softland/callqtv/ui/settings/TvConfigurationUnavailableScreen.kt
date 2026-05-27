package com.softland.callqtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.softland.callqtv.R
import com.softland.callqtv.ui.theme.CallQtvDimens
import com.softland.callqtv.ui.theme.CallQtvOverlayColors

@Composable
/**
 * Full-screen blocking fallback shown when TV configuration cannot be loaded.
 *
 * Displays network-aware guidance, device/app identifiers for support, and a retry action.
 */
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

    val overlay = CallQtvOverlayColors
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
                .background(overlay.ScreenBackground),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(CallQtvDimens.ConfigUnavailableContentWidthFraction)
                    .padding(
                        horizontal = CallQtvDimens.ConfigUnavailableHorizontalPadding,
                        vertical = CallQtvDimens.ConfigUnavailableVerticalPadding,
                    ),
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
                    color = overlay.BodyText,
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
                    color = overlay.MutedLabel,
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
