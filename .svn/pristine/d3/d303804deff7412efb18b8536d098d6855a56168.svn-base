package com.softland.callqtv.data.repository

import com.softland.callqtv.data.model.ServiceUrlResponse
import com.softland.callqtv.data.network.RetrofitClient
import com.softland.callqtv.data.network.ApiService

class ServiceUrlRepository(private val context: android.content.Context) {

    private val apiService: ApiService = RetrofitClient.getApiService(context)

    suspend fun getServiceUrl(projectCode: String, baseUrl: String): ServiceUrlResponse? {
        val json = "{\"ProjectCode\":\"$projectCode\"}"
        RetrofitClient.BASE_URL = baseUrl
        
        return try {
            apiService.getServiceUrl(baseUrl, json)
        } catch (e: Exception) {
            null
        }
    }
}

