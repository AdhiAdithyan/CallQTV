package com.softland.callqtv

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Set up global exception handler to suppress MIUI/Xiaomi SDK errors
        setupGlobalExceptionHandler()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // Log crash to file first
            writeCrashLog(exception)

            // Check if this is a MIUI/Xiaomi SDK related error that we can safely ignore
            if (isMiuiSdkException(exception)) {
                Log.w("MyApplication", "Suppressed MIUI SDK exception: ${exception.message}")
                // Don't crash the app for these non-critical errors
                return@setDefaultUncaughtExceptionHandler
            }
            
            // For all other exceptions, use the default handler
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    private fun writeCrashLog(exception: Throwable) {
        try {
            com.softland.callqtv.utils.FileLogger.logCrash(this, exception)
            Log.e("MyApplication", "Crash logged to: ${com.softland.callqtv.utils.FileLogger.getLogFilePath(this)}")
        } catch (e: Exception) {
            // Never let crash logging cause a secondary crash (critical on Android 15)
            Log.e("MyApplication", "Failed to write crash log: ${e.message}", e)
        }
    }

    private fun isMiuiSdkException(exception: Throwable?): Boolean {
        if (exception == null) return false
        
        val stackTrace = exception.stackTraceToString()
        val message = exception.message ?: ""

        // Google Play Express Integrity warm-up can fail transiently on some networks
        // (Cronet ERR_CONNECTION_CLOSED / ERR_INTERNET_DISCONNECTED, etc.).
        // These are external service failures and should not crash the app.
        val isExpressIntegrityNetworkFailure =
            stackTrace.contains("ExpressIntegrityException", ignoreCase = true) ||
            stackTrace.contains("expressintegrityservice", ignoreCase = true) ||
            stackTrace.contains("CronetUrlRequest", ignoreCase = true) ||
            stackTrace.contains("NetworkExceptionImpl", ignoreCase = true) ||
            message.contains("ERR_CONNECTION_CLOSED", ignoreCase = true) ||
            message.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) ||
            message.contains("ERR_NETWORK_CHANGED", ignoreCase = true) ||
            message.contains("ERR_TIMED_OUT", ignoreCase = true)

        if (isExpressIntegrityNetworkFailure) {
            return true
        }
        
        // Check for NameNotFoundException related to com.miui.miwallpaper
        if (exception is PackageManager.NameNotFoundException || 
            exception.cause is PackageManager.NameNotFoundException) {
            if (message.contains("com.miui.miwallpaper", ignoreCase = true)) {
                return true
            }
        }

        // Check for NoSuchFieldException related to MIUI/Xiaomi SDK
        if (exception is NoSuchFieldException) {
            if (message.contains("IS_MIUI_LITE_VERSION", ignoreCase = true) ||
                message.contains("miui.os.Build", ignoreCase = true)
            ) {
                return true
            }
        }
        
        // Check stack trace for MIUI/Xiaomi SDK or Google Play Integrity errors
        val isVendorOrSystemError = stackTrace.contains("miuix.device.DeviceUtils", ignoreCase = true) ||
                stackTrace.contains("com.xiaomi.market", ignoreCase = true) ||
                stackTrace.contains("ExpressIntegrityException", ignoreCase = true) ||
                stackTrace.contains("com.google.android.finsky", ignoreCase = true) ||
                stackTrace.contains("ExpressIntegrityService", ignoreCase = true)
        
        // Only suppress if it's from vendor/system SDK AND not from our application package
        if (isVendorOrSystemError && !stackTrace.contains("com.softland.callqtv", ignoreCase = true)) {
            return true
        }
        
        // Check nested exceptions
        var cause: Throwable? = exception.cause
        while (cause != null) {
            if (isMiuiSdkException(cause)) {
                return true
            }
            cause = cause.cause
        }
        
        return false
    }
}
