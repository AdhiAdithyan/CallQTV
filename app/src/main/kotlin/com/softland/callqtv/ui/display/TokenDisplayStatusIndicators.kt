package com.softland.callqtv.ui.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.softland.callqtv.R
import com.softland.callqtv.ui.theme.CallQtvStatusColors

@Composable
/** Bluetooth/Blucon connectivity status icon for the top bar. */
fun BluconStatusIndicator(isOnline: Boolean, isPortrait: Boolean, scale: Float) {
    val color = if (isOnline) CallQtvStatusColors.Online else CallQtvStatusColors.Offline
    val iconSize = ((if (isPortrait) 20 else 28) * scale).dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            imageVector = if (isOnline) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
            contentDescription = "BLUCON",
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
/** Internet/network connectivity status icon for the top bar. */
fun NetworkStatusIndicator(isOnline: Boolean, isPortrait: Boolean, scale: Float) {
    val networkIconRes = if (isOnline) R.drawable.ic_network_available else R.drawable.ic_network_unavailable
    val networkIconColor = if (isOnline) CallQtvStatusColors.Online else CallQtvStatusColors.Offline
    val iconSize = ((if (isPortrait) 20 else 28) * scale).dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painterResource(id = networkIconRes),
            contentDescription = "Network Status",
            tint = networkIconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
