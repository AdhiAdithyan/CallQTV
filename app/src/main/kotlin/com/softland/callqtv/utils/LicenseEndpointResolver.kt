package com.softland.callqtv.utils

import android.content.Context
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * License API base URLs for the active build ([Variables.IS_LIVE]).
 * Tries **https** and **http** variants of the same host/path so QA (HTTP-only) and
 * production (HTTPS) both work without manual edits. Optionally prefers the last
 * scheme that succeeded ([PreferenceHelper.getLastSuccessfulLicenseBase]).
 */
object LicenseEndpointResolver {

    fun environmentLabel(): String = if (Variables.IS_LIVE) "LIVE" else "QA"

    /**
     * Ordered base URLs (with trailing path segment as in [Variables.getLicenseBaseUrl]) to try
     * for license calls. Includes http/https alternates and last-known-good ordering.
     */
    fun orderedLicenseBaseUrls(context: Context): List<String> {
        val canonical = Variables.getLicenseBaseUrl().trim()
        if (canonical.isBlank()) return emptyList()

        val alternate = toggleHttpHttps(canonical)
        val variants = buildList {
            add(canonical)
            if (alternate != canonical && alternate.isNotBlank()) add(alternate)
        }.distinct()

        val lastOk = PreferenceHelper.getLastSuccessfulLicenseBase(context)?.trim()
        if (lastOk.isNullOrBlank()) return variants

        val normalizedLast = lastOk.trimEnd('/')
        val match = variants.find { it.trimEnd('/') == normalizedLast }
        return if (match != null) {
            listOf(match) + variants.filter { it.trimEnd('/') != normalizedLast }
        } else {
            variants
        }
    }

    fun endpointUrl(baseUrl: String, path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return base + path.trimStart('/')
    }

    fun isFailoverCandidate(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is SocketTimeoutException,
                is UnknownHostException,
                is IOException -> {
                    val msg = current.message.orEmpty()
                    if (msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("failed to connect", ignoreCase = true) ||
                        msg.contains("Unable to resolve host", ignoreCase = true) ||
                        msg.contains("ENETUNREACH", ignoreCase = true) ||
                        msg.contains("ECONNREFUSED", ignoreCase = true)
                    ) {
                        return true
                    }
                }
            }
            current = current.cause
        }
        return error is SocketTimeoutException || error is UnknownHostException
    }

    /**
     * Whether to try the next http/https base URL after this failure.
     */
    fun shouldRetryWithAlternateLicenseScheme(error: Throwable): Boolean {
        if (isFailoverCandidate(error)) return true
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is SSLException -> return true
            }
            val name = current.javaClass.name
            if (name.startsWith("javax.net.ssl.") || name.startsWith("java.security.cert.")) {
                return true
            }
            val msg = current.message.orEmpty()
            if (msg.contains("Cleartext HTTP traffic not permitted", ignoreCase = true) ||
                msg.contains("CLEARTEXT communication", ignoreCase = true) ||
                msg.contains("ERR_CLEARTEXT_NOT_PERMITTED", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun toggleHttpHttps(url: String): String {
        val u = url.trim()
        val lower = u.lowercase()
        return when {
            lower.startsWith("https://") -> {
                val rest = u.substring(u.indexOf("://") + 3)
                "http://$rest"
            }
            lower.startsWith("http://") -> {
                val rest = u.substring(u.indexOf("://") + 3)
                "https://$rest"
            }
            else -> u
        }
    }
}
