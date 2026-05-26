package com.softland.callqtv.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.softland.callqtv.data.local.AppSharedPreferences
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.*

object Variables {
    private const val PREF_STABLE_DEVICE_ID = "stable_device_identifier"
    /** Set from [com.softland.callqtv.BuildConfig.IS_LIVE] in [com.softland.callqtv.MyApplication]. */
    var IS_LIVE: Boolean = true
    @JvmStatic
    var ServiceCounter: Int = 0
    @JvmStatic
    var GetServicelink: String = ""
    
    // API Endpoints
    const val GET_SERVICE_LINK = "v1/product/link"
    const val GET_SERVICE_URL = "v1/product/url"
    const val PRODUCT_AUTH_URL_HEADER = "license/authenticate"
    const val DEVICE_REG_URL_HEADER = "license/register"
    const val CHECK_DEVICE_LICENSE_URL_HEADER = "license/check"

    /**
     * Stable device id for license registration. Android TV often exposes several interfaces;
     * the first from [NetworkInterface.getNetworkInterfaces] iteration is not reliable.
     * Prefer ethernet/Wi‑Fi names, then persist the resolved value so reboots keep the same id.
     */
    fun getMacId(context: Context): String {
        val prefs = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        val cached = prefs.getString(PREF_STABLE_DEVICE_ID, null)?.trim().orEmpty()
        if (cached.isNotBlank() && cached != "00:00:00:00:00:00") {
            return cached
        }

        val resolved = resolveHardwareIdentifier(context)
        if (resolved.isNotBlank() && resolved != "00:00:00:00:00:00") {
            prefs.edit().putString(PREF_STABLE_DEVICE_ID, resolved).apply()
        }
        return resolved
    }

    private fun resolveHardwareIdentifier(context: Context): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val preferredNames = listOf("eth0", "wlan0", "en0", "eth1", "wlan1", "ap0")
            for (name in preferredNames) {
                val ni = interfaces.find { it.name.equals(name, ignoreCase = true) }
                if (ni != null) {
                    formatMacAddress(ni)?.let { return it }
                }
            }
            interfaces
                .filter { ni ->
                    ni.isUp && !ni.isLoopback && ni.hardwareAddress?.size == 6
                }
                .sortedBy { it.name }
                .firstNotNullOfOrNull { formatMacAddress(it) }
                ?: fallbackAndroidId(context)
        } catch (_: Exception) {
            fallbackAndroidId(context)
        }
    }

    private fun formatMacAddress(networkInterface: NetworkInterface): String? {
        val addr = networkInterface.hardwareAddress ?: return null
        if (addr.size != 6) return null
        val buf = StringBuilder()
        for (b in addr) {
            buf.append(String.format(Locale.ROOT, "%02X:", b))
        }
        if (buf.isEmpty()) return null
        buf.deleteCharAt(buf.length - 1)
        val mac = buf.toString()
        return if (mac == "00:00:00:00:00:00") null else mac
    }

    private fun fallbackAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "00:00:00:00:00:00"
    }

    fun ShowToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun getCurentDate(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun isNetworkEnabled(context: Context): Boolean = NetworkCompat.isNetworkEnabled(context)

    @JvmStatic
    var LISENSE_DEMO_URL: String = "http://202.88.237.210:8093/LicenceMgmt/public/api/"

    @JvmStatic
    var LISENSE_DEMO_URL_LIVE: String = "http://licencemanagement.softlandindia.net/public/api/"

    /** Production when [IS_LIVE]; QA/demo licence host when not live. */
    fun getLicenseBaseUrl(): String {
        return if (IS_LIVE) LISENSE_DEMO_URL_LIVE else LISENSE_DEMO_URL
    }

    fun getLicenseEnvironmentLabel(): String = if (IS_LIVE) "LIVE" else "QA"

    @JvmStatic
    fun LoadURL(): String {
        when (ServiceCounter) {
            0 -> GetServicelink = "https://androservice.softlandindia.co.in/SILService.svc/"
            1 -> GetServicelink = "http://androservice.palmtecandro.com/SILService.svc/"
            2 -> GetServicelink = "http://androservice.softlandindiasalesforce.com/SILService.svc/"
            3 -> GetServicelink = "http://androservice.salesforceautomation.in/SILService.svc/"
            4 -> GetServicelink = "http://androservice.softlandsalesforce.com/SILService.svc/"
            else -> {
                ServiceCounter = 0
                GetServicelink = ""
            }
        }
        return GetServicelink
    }
}
