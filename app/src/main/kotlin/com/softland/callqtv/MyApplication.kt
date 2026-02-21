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
            Log.e("MyApplication", "Failed to write crash log: ${e.message}")
        }
    }

    private fun isMiuiSdkException(exception: Throwable?): Boolean {
        if (exception == null) return false
        
        val stackTrace = exception.stackTraceToString()
        val message = exception.message ?: ""
        
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
        
        // Check stack trace for MIUI/Xiaomi SDK classes
        val isXiaomiSdk = stackTrace.contains("miuix.device.DeviceUtils", ignoreCase = true) ||
                stackTrace.contains("com.xiaomi.mipicks", ignoreCase = true) ||
                stackTrace.contains("com.xiaomi.market", ignoreCase = true) ||
                stackTrace.contains("com.xiaomi.task", ignoreCase = true) ||
                stackTrace.contains("FG_LOG", ignoreCase = true)
        
        // Only suppress if it's from Xiaomi SDK AND not from our application package
        if (isXiaomiSdk && !stackTrace.contains("com.softland.callqtv", ignoreCase = true)) {
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
