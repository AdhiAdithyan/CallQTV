package com.softland.callqtv.data.repository

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

class ProjectRepository {

    private val api: ApiService = RetrofitClient.getApiLicenseService()

    suspend fun authenticateProduct(url: String, request: ProductAuthenticationReq): ProductAuthenticationRes =
        withContext(Dispatchers.IO) {
            api.authenticateProduct(url, request)
        }

    suspend fun getDeviceRegistration(url: String, request: DeviceRegistrationRequest): DeviceRegistrationResponse =
        withContext(Dispatchers.IO) {
            api.getDeviceRegistration(url, request)
        }

    suspend fun getCheckDeviceStatus(url: String, request: CheckDeviceStatusRequest): CheckDeviceStatusResponse =
        withContext(Dispatchers.IO) {
            api.getCheckDeviceStatus(url, request)
        }
}
