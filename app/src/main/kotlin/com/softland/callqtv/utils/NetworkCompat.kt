package com.softland.callqtv.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Network checks with API 21+ fallbacks (legacy [ConnectivityManager.activeNetworkInfo] pre-M).
 */
object NetworkCompat {

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            val hasInternet =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        @Suppress("DEPRECATION")
        val info = cm.activeNetworkInfo
        @Suppress("DEPRECATION")
        return info != null && info.isConnected
    }

    fun isNetworkEnabled(context: Context): Boolean = isNetworkAvailable(context)

    fun activeNetwork(cm: ConnectivityManager): Network? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return cm.activeNetwork
    }

    fun networkCapabilities(cm: ConnectivityManager, network: Network): NetworkCapabilities? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return cm.getNetworkCapabilities(network)
    }

    /**
     * Heuristic for MQTT/ad bitrate tuning; returns false on API &lt; 23 (no [NetworkCapabilities]).
     */
    fun isLowBandwidthNetwork(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            val downKbps = caps.linkDownstreamBandwidthKbps
            val onCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val downIsLow = downKbps in 1..4_000
            onCellular || downIsLow
        } catch (_: Exception) {
            false
        }
    }
}
