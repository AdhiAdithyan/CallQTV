package com.softland.callqtv.utils

import android.os.Build
import java.util.concurrent.TimeUnit

object ProcessCompat {
    /** [Process.waitFor] with timeout on API 26+; blocking wait on older releases. */
    fun waitFor(process: Process, timeoutMs: Long): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            try {
                process.waitFor()
                true
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    fun exitValue(process: Process): Int = process.exitValue()
}
