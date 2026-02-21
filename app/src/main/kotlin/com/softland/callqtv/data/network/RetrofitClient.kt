package com.softland.callqtv.data.network

import com.softland.callqtv.utils.UnsafeOkHttpClient
import com.softland.callqtv.utils.Variables
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    @JvmStatic
    var BASE_URL: String = "https://webtest.softlandindia.co.in/"

    private var retrofit: Retrofit? = null
    private var licenseRetrofit: Retrofit? = null

    private val sharedClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        
        UnsafeOkHttpClient.getUnsafeOkHttpClient()
            .newBuilder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    // Use a more robust browser-like User-Agent to bypass stricter WAF filters
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Connection", "keep-alive")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @JvmStatic
    fun getApiService(): ApiService {
        var instance = retrofit
        if (instance == null || instance.baseUrl().toString() != BASE_URL) {
            instance = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(sharedClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit = instance
        }
        return instance!!.create(ApiService::class.java)
    }

    @JvmStatic
    fun getApiLicenseService(): ApiService {
        var instance = licenseRetrofit
        val licenseUrl = Variables.getLicenseBaseUrl()
        if (instance == null /*|| instance.baseUrl().toString() != licenseUrl*/) {
            instance = Retrofit.Builder()
                .baseUrl(licenseUrl)
                .client(sharedClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            licenseRetrofit = instance
        }
        return instance!!.create(ApiService::class.java)
    }
}
