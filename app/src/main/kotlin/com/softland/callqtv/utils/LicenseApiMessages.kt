package com.softland.callqtv.utils

import java.net.SocketTimeoutException

object LicenseApiMessages {

    fun userMessageFor(error: Throwable, action: String): String {
        if (error.message?.contains("No internet", ignoreCase = true) == true) {
            return error.message ?: "No internet connection. Please check network and retry."
        }
        if (error is SocketTimeoutException ||
            LicenseEndpointResolver.isFailoverCandidate(error) ||
            LicenseEndpointResolver.shouldRetryWithAlternateLicenseScheme(error)
        ) {
            val env = Variables.getLicenseEnvironmentLabel()
            return "License server ($env) is slow or unreachable. Check network and tap Retry."
        }
        return "$action failed. Please retry."
    }
}
