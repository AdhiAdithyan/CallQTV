package com.softland.callqtv.data.repository

import com.softland.callqtv.data.model.ServiceUrlResponse
import com.softland.callqtv.data.network.RetrofitClient
import com.softland.callqtv.data.network.ApiService
import com.softland.callqtv.utils.FileLogger
import com.softland.callqtv.utils.NetworkCompat
import com.softland.callqtv.utils.NetworkUtil
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

class ServiceUrlRepository(private val context: android.content.Context) {

    private val apiService: ApiService = RetrofitClient.getApiService(context)

    private fun shouldRetry(error: Exception): Boolean {
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

    suspend fun getServiceUrl(projectCode: String, baseUrl: String): ServiceUrlResponse? {
        if (!NetworkUtil.isNetworkAvailable(context)) return null
        val json = "{\"ProjectCode\":\"$projectCode\"}"
        RetrofitClient.BASE_URL = baseUrl
        val lowNetwork = NetworkCompat.isLowBandwidthNetwork(context)
        val maxAttempts = if (lowNetwork) 4 else 3
        var retriesUsed = 0
        var lastErrorType = "none"
        
        for (attempt in 1..maxAttempts) {
            try {
                val response = apiService.getServiceUrl(baseUrl, json)
                android.util.Log.i(
                    "ServiceUrlRepoNet",
                    "getServiceUrl telemetry projectCode=$projectCode lowNetwork=$lowNetwork retriesUsed=$retriesUsed maxAttempts=$maxAttempts lastErrorType=$lastErrorType",
                )
                FileLogger.logResponse(
                    context,
                    "ServiceUrlRepoNet",
                    "getServiceUrl telemetry projectCode=$projectCode lowNetwork=$lowNetwork retriesUsed=$retriesUsed maxAttempts=$maxAttempts lastErrorType=$lastErrorType",
                )
                return response
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErrorType = e::class.java.simpleName
                val canRetry = attempt < maxAttempts && shouldRetry(e)
                if (!canRetry) return null
                retriesUsed++
                val backoffMs = if (lowNetwork) 4_000L * attempt else 2_000L * attempt
                delay(backoffMs)
            }
        }
        return null
    }
}

