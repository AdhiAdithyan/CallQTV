package com.softland.callqtv.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
/**
 * Generic registration/provisioning status dialog shown during onboarding flows.
 *
 * Presents status text with device identity and configurable confirm/dismiss actions.
 */
fun RegistrationStatusDialog(
    title: String,
    message: String,
    deviceId: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: () -> Unit = onDismiss ?: onConfirm,
) {
    AlertDialog(
        modifier = Modifier.widthIn(min = 280.dp, max = 900.dp),
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Device ID: $deviceId",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = if (dismissLabel != null && onDismiss != null) {
            { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
        } else {
            null
        },
    )
}
