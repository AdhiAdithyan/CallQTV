package com.softland.callqtv.data.repository

import android.util.Log
import com.google.gson.Gson
import com.softland.callqtv.data.model.CheckDeviceStatusRequest
import com.softland.callqtv.data.model.CheckDeviceStatusResponse
import com.softland.callqtv.data.model.DeviceRegistrationRequest
import com.softland.callqtv.data.model.DeviceRegistrationResponse
import com.softland.callqtv.data.model.ProductAuthenticationReq
import com.softland.callqtv.data.model.ProductAuthenticationRes
import com.softland.callqtv.data.network.RetrofitClient
import com.softland.callqtv.data.network.ApiService
import com.softland.callqtv.utils.FileLogger
import com.softland.callqtv.utils.LicenseEndpointResolver
import com.softland.callqtv.utils.NetworkCompat
import com.softland.callqtv.utils.NetworkUtil
import com.softland.callqtv.utils.PreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ProjectRepository(private val context: android.content.Context) {

    private val api: ApiService = RetrofitClient.getApiLicenseService(context)
    private val gson = Gson()

    private fun shouldRetryLicenseCall(error: Exception): Boolean {
        return when (error) {
            is HttpException -> {
                val code = error.code()
                code == 408 || code == 429 || code in 500..599
            }
            is IOException -> true
            else -> {
                val msg = error.message.orEmpty()
                msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("timed out", ignoreCase = true) ||
                    msg.contains("connection reset", ignoreCase = true) ||
                    msg.contains("connection abort", ignoreCase = true) ||
                    msg.contains("unable to resolve host", ignoreCase = true) ||
                    msg.contains("failed to connect", ignoreCase = true)
            }
        }
    }

    suspend fun authenticateProduct(endpoint: String, request: ProductAuthenticationReq): ProductAuthenticationRes =
        withLicenseFailover(endpoint, "authenticateProduct") { url ->
            logRequest("authenticateProduct", url, request)
            val response = api.authenticateProduct(url, request)
            logResponse("authenticateProduct", url, response)
            response
        }

    suspend fun getDeviceRegistration(
        endpoint: String,
        request: DeviceRegistrationRequest,
    ): DeviceRegistrationResponse =
        withLicenseFailover(endpoint, "getDeviceRegistration") { url ->
            logRequest("getDeviceRegistration", url, request)
            val response = api.getDeviceRegistration(url, request)
            logResponse("getDeviceRegistration", url, response)
            response
        }

    suspend fun getCheckDeviceStatus(
        endpoint: String,
        request: CheckDeviceStatusRequest,
    ): CheckDeviceStatusResponse =
        withLicenseFailover(endpoint, "getCheckDeviceStatus") { url ->
            logRequest("getCheckDeviceStatus", url, request)
            val response = api.getCheckDeviceStatus(url, request)
            logResponse("getCheckDeviceStatus", url, response)
            response
        }

    private suspend fun <T> withLicenseFailover(
        endpoint: String,
        label: String,
        block: suspend (url: String) -> T,
    ): T = withContext(Dispatchers.IO) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            Log.w("LicenseApi", "Skipping $label: network unavailable")
            throw IOException("No internet connection. Please check network and retry.")
        }

        val bases = LicenseEndpointResolver.orderedLicenseBaseUrls(context.applicationContext)
        val lowNetwork = NetworkCompat.isLowBandwidthNetwork(context)
        val attemptsPerBase = if (lowNetwork) 3 else 2
        var retriesUsed = 0
        var lastErrorType = "none"
        var lastError: Exception? = null
        for ((index, base) in bases.withIndex()) {
            val url = LicenseEndpointResolver.endpointUrl(base, endpoint)
            for (attempt in 1..attemptsPerBase) {
                try {
                    Log.i(
                        "LicenseApi",
                        "$label ${LicenseEndpointResolver.environmentLabel()} url=$url attempt=$attempt/$attemptsPerBase",
                    )
                    val result = block(url)
                    Log.i(
                        "LicenseApi",
                        "$label telemetry lowNetwork=$lowNetwork retriesUsed=$retriesUsed attemptsPerBase=$attemptsPerBase lastErrorType=$lastErrorType",
                    )
                    FileLogger.logResponse(
                        context,
                        "LicenseApiNet",
                        "$label telemetry lowNetwork=$lowNetwork retriesUsed=$retriesUsed attemptsPerBase=$attemptsPerBase lastErrorType=$lastErrorType",
                    )
                    PreferenceHelper.setLastSuccessfulLicenseBase(context.applicationContext, base)
                    return@withContext result
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("LicenseApi", "$label ERROR url=$url message=${e.message}", e)
                    lastError = e as? Exception ?: IOException(e.message, e)
                    lastErrorType = lastError::class.java.simpleName
                    val retrySameBase = attempt < attemptsPerBase && shouldRetryLicenseCall(lastError)
                    if (retrySameBase) {
                        retriesUsed++
                        val backoffMs = if (lowNetwork) 4_000L * attempt else 2_000L * attempt
                        delay(backoffMs)
                        continue
                    }
                    val canRetryScheme =
                        index < bases.lastIndex &&
                            LicenseEndpointResolver.shouldRetryWithAlternateLicenseScheme(lastError)
                    if (canRetryScheme) {
                        Log.w("LicenseApi", "$label: retrying with alternate transport (http/https)")
                        break
                    }
                    throw lastError
                }
            }
        }
        throw lastError ?: IOException("License server unreachable")
    }

    private fun logRequest(label: String, url: String, body: Any) {
        try {
            Log.i("LicenseApi", "$label REQUEST url=$url body=${gson.toJson(body)}")
        } catch (_: Exception) {
        }
    }

    private fun logResponse(label: String, url: String, body: Any) {
        try {
            Log.i("LicenseApi", "$label RESPONSE url=$url body=${gson.toJson(body)}")
        } catch (_: Exception) {
        }
    }
}
