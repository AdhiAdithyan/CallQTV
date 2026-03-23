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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectRepository(private val context: android.content.Context) {

    private val api: ApiService = RetrofitClient.getApiLicenseService(context)
    private val gson = Gson()

    suspend fun authenticateProduct(url: String, request: ProductAuthenticationReq): ProductAuthenticationRes =
        withContext(Dispatchers.IO) {
            try {
                Log.i("LicenseApi", "authenticateProduct REQUEST url=$url body=${gson.toJson(request)}")
            } catch (e: Exception) {e.printStackTrace()}
            try {
                val response = api.authenticateProduct(url, request)
                try {
                    Log.i("LicenseApi", "authenticateProduct RESPONSE url=$url body=${gson.toJson(response)}")
                } catch (e: Exception) {e.printStackTrace()}
                response
            } catch (e: Exception) {
                Log.e("LicenseApi", "authenticateProduct ERROR url=$url message=${e.message}", e)
                throw e
            }
        }

    suspend fun getDeviceRegistration(url: String, request: DeviceRegistrationRequest): DeviceRegistrationResponse =
        withContext(Dispatchers.IO) {
            try {
                Log.i("LicenseApi", "getDeviceRegistration REQUEST url=$url body=${gson.toJson(request)}")
            } catch (_: Exception) { }
            try {
                val response = api.getDeviceRegistration(url, request)
                try {
                    Log.i("LicenseApi", "getDeviceRegistration RESPONSE url=$url body=${gson.toJson(response)}")
                } catch (_: Exception) { }
                response
            } catch (e: Exception) {
                Log.e("LicenseApi", "getDeviceRegistration ERROR url=$url message=${e.message}", e)
                throw e
            }
        }

    suspend fun getCheckDeviceStatus(url: String, request: CheckDeviceStatusRequest): CheckDeviceStatusResponse =
        withContext(Dispatchers.IO) {
            try {
                Log.i("LicenseApi", "getCheckDeviceStatus REQUEST url=$url body=${gson.toJson(request)}")
            } catch (_: Exception) { }
            try {
                val response = api.getCheckDeviceStatus(url, request)
                try {
                    Log.i("LicenseApi", "getCheckDeviceStatus RESPONSE url=$url body=${gson.toJson(response)}")
                } catch (_: Exception) { }
                response
            } catch (e: Exception) {
                Log.e("LicenseApi", "getCheckDeviceStatus ERROR url=$url message=${e.message}", e)
                throw e
            }
        }
}
