package com.softland.callqtv.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

object Variables {
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

    fun getMacId(context: Context): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addr = networkInterface.hardwareAddress
                if (addr != null && addr.isNotEmpty()) {
                    val buf = StringBuilder()
                    for (b in addr) buf.append(String.format("%02X:", b))
                    if (buf.isNotEmpty()) buf.deleteCharAt(buf.length - 1)
                    return buf.toString()
                }
            }
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "00:00:00:00:00:00"
        } catch (e: Exception) {
            "00:00:00:00:00:00"
        }
    }

    fun ShowToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun getCurentDate(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun isNetworkEnabled(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @JvmStatic
    var LISENSE_DEMO_URL: String = "http://202.88.237.210:8093/LicenceMgmt/public/api/"

    @JvmStatic
    var LISENSE_DEMO_URL_LIVE: String = "http://licencemanagement.softlandindia.net/public/api/"

    fun getLicenseBaseUrl(): String {
        return if (IS_LIVE) LISENSE_DEMO_URL_LIVE else LISENSE_DEMO_URL
    }

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
