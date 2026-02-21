package com.softland.callqtv.data.network

import com.softland.callqtv.data.model.CheckDeviceStatusRequest
import com.softland.callqtv.data.model.CheckDeviceStatusResponse
import com.softland.callqtv.data.model.DeviceMappingRequest
import com.softland.callqtv.data.model.DeviceMappingResponse
import com.softland.callqtv.data.model.DeviceRegistrationRequest
import com.softland.callqtv.data.model.DeviceRegistrationResponse
import com.softland.callqtv.data.model.LoginRequest
import com.softland.callqtv.data.model.LoginResponse
import com.softland.callqtv.data.model.ProductAuthenticationReq
import com.softland.callqtv.data.model.ProductAuthenticationRes
import com.softland.callqtv.data.model.ServiceUrlResponse
import com.softland.callqtv.data.model.TvConfigRequest
import com.softland.callqtv.data.model.TvConfigResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {

    @POST
    suspend fun login(@Url url: String, @Body loginRequest: LoginRequest): LoginResponse

    @GET
    suspend fun getServiceUrl(
        @Url url: String,
        @Query("composite") encodedJson: String
    ): ServiceUrlResponse

    @POST
    suspend fun authenticateProduct(
        @Url url: String,
        @Body request: ProductAuthenticationReq
    ): ProductAuthenticationRes

    @POST
    suspend fun getDeviceRegistration(
        @Url url: String,
        @Body request: DeviceRegistrationRequest
    ): DeviceRegistrationResponse

    @POST
    suspend fun getCheckDeviceStatus(
        @Url url: String,
        @Body request: CheckDeviceStatusRequest
    ): CheckDeviceStatusResponse

    @POST
    suspend fun registerDevice(
        @Url url: String,
        @Body plainTextBody: DeviceMappingRequest
    ): DeviceMappingResponse

    /**
     * Fetch TV configuration for a device.
     *
     * Base URL example:
     * https://py.softlandindia.net/CallQ/config/api/android/config
     */
    @POST
    suspend fun fetchTvConfig(
        @Url url: String,
        @Body request: TvConfigRequest
    ): TvConfigResponse
}
