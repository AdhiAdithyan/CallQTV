package com.softland.callqtv.data.network

import android.content.Context

import com.softland.callqtv.utils.UnsafeOkHttpClient
import com.softland.callqtv.utils.Variables
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var response: Response? = null
            var exception: IOException? = null
            
            for (i in 0..maxRetries) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful) return response
                    // If not successful, we might still want to retry on certain status codes (e.g. 503)
                    if (i < maxRetries && response.code in 500..599) {
                        response.close()
                        continue
                    }
                    return response
                } catch (e: IOException) {
                    exception = e
                    if (i >= maxRetries) throw e
                    // Sleep brief duration before retry to give network/server a moment
                    try { Thread.sleep(2000L * (i + 1)) } catch (_: Exception) {}
                }
            }
            throw exception ?: IOException("Request failed after $maxRetries retries")
        }
    }

    @JvmStatic
    var BASE_URL: String = "https://webtest.softlandindia.co.in/"

    private var appContext: Context? = null

    private val responseLoggingInterceptor by lazy {
        Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            appContext?.let { ctx ->
                try {
                    val responseBody = response.peekBody(1024 * 1024) // Peek up to 1MB
                    val apiName = request.url.encodedPath
                    com.softland.callqtv.utils.FileLogger.logResponse(ctx, apiName, responseBody.string())
                } catch (e: Exception) {
                    android.util.Log.e("RetrofitClient", "Response logging failed", e)
                }
            }
            response
        }
    }

    private var retrofit: Retrofit? = null
    private var licenseRetrofit: Retrofit? = null

    private val sharedClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        
        UnsafeOkHttpClient.getUnsafeOkHttpClient()
            .newBuilder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(responseLoggingInterceptor)
            .addInterceptor(RetryInterceptor(maxRetries = 2))
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
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // License APIs can be slower / occasionally stall; use a separate client with longer timeouts
    // so we don't fail fast while the license server is still processing.
    private val licenseClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        UnsafeOkHttpClient.getUnsafeOkHttpClient()
            .newBuilder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(responseLoggingInterceptor)
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                    )
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Connection", "keep-alive")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private var cachedApiService: ApiService? = null
    private var cachedApiLicenseService: ApiService? = null

    @JvmStatic
    fun getApiService(context: Context): ApiService {
        appContext = context.applicationContext
        val instance = retrofit
        if (instance == null || instance.baseUrl().toString() != BASE_URL) {
            val newRetrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(sharedClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit = newRetrofit
            cachedApiService = newRetrofit.create(ApiService::class.java)
        } else if (cachedApiService == null) {
            cachedApiService = instance.create(ApiService::class.java)
        }
        return cachedApiService!!
    }

    @JvmStatic
    fun getApiLicenseService(context: Context): ApiService {
        appContext = context.applicationContext
        val licenseUrl = Variables.getLicenseBaseUrl()
        var instance = licenseRetrofit
        
        if (instance == null || instance.baseUrl().toString() != licenseUrl) {
            val newRetrofit = Retrofit.Builder()
                .baseUrl(licenseUrl)
                .client(licenseClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            licenseRetrofit = newRetrofit
            cachedApiLicenseService = newRetrofit.create(ApiService::class.java)
        } else if (cachedApiLicenseService == null) {
            cachedApiLicenseService = instance.create(ApiService::class.java)
        }
        return cachedApiLicenseService!!
    }
}
