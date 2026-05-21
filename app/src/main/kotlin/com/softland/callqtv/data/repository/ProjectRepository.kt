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
import com.softland.callqtv.utils.LicenseEndpointResolver
import com.softland.callqtv.utils.NetworkUtil
import com.softland.callqtv.utils.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class ProjectRepository(private val context: android.content.Context) {

    private val api: ApiService = RetrofitClient.getApiLicenseService(context)
    private val gson = Gson()

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
        var lastError: Exception? = null
        for ((index, base) in bases.withIndex()) {
            val url = LicenseEndpointResolver.endpointUrl(base, endpoint)
            try {
                Log.i(
                    "LicenseApi",
                    "$label ${LicenseEndpointResolver.environmentLabel()} url=$url",
                )
                val result = block(url)
                PreferenceHelper.setLastSuccessfulLicenseBase(context.applicationContext, base)
                return@withContext result
            } catch (e: HttpException) {
                Log.e("LicenseApi", "$label HTTP ${e.code()} url=$url", e)
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LicenseApi", "$label ERROR url=$url message=${e.message}", e)
                lastError = e as? Exception ?: IOException(e.message, e)
                val canRetryScheme =
                    index < bases.lastIndex &&
                        LicenseEndpointResolver.shouldRetryWithAlternateLicenseScheme(e)
                if (canRetryScheme) {
                    Log.w("LicenseApi", "$label: retrying with alternate transport (http/https)")
                    continue
                }
                throw lastError
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
