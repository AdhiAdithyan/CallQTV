package com.softland.callqtv.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.softland.callqtv.R

/**
 * Standard message + device ID dialog used on main display (approval pending, MQTT retry, etc.).
 */
@Composable
fun DeviceMessageDialog(
    title: String,
    message: String,
    deviceId: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: () -> Unit = {},
    showErrorIconInTitle: Boolean = false,
    dialogWidthFraction: Float? = null,
    confirmFocusRequester: FocusRequester? = null,
) {
    AlertDialog(
        modifier = if (dialogWidthFraction != null) {
            Modifier.fillMaxWidth(dialogWidthFraction)
        } else {
            Modifier
        },
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
        title = {
            if (showErrorIconInTitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_network_unavailable),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title)
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = if (showErrorIconInTitle) TextAlign.Start else TextAlign.Start,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Device ID: $deviceId",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = confirmFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = if (dismissLabel != null && onDismiss != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel)
                }
            }
        } else {
            null
        },
    )
}
