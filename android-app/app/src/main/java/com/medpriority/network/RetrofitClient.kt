package com.medpriority.network

import android.content.Context
import android.os.Build
import com.medpriority.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {
    @Volatile
    private var apiService: ApiService? = null

    @Volatile
    var activeHost: String? = null

    fun isRunningOnEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    fun getCandidateHosts(context: Context): List<String> {
        val tokenManager = TokenManager.getInstance(context)
        val customHost = tokenManager.getServerUrl()
        val defaultCandidates = if (isRunningOnEmulator()) {
            listOf("10.0.2.2:8000", "127.0.0.1:8000", "192.168.29.14:8000")
        } else {
            listOf("192.168.29.14:8000", "127.0.0.1:8000", "10.0.2.2:8000")
        }

        val candidates = mutableListOf<String>()
        if (!customHost.isNullOrBlank()) {
            val clean = customHost.removePrefix("http://").removePrefix("https://").trimEnd('/')
            candidates.add(clean)
        }
        activeHost?.let {
            if (!candidates.contains(it)) candidates.add(it)
        }
        for (c in defaultCandidates) {
            if (!candidates.contains(c)) candidates.add(c)
        }
        return candidates
    }

    fun getApiService(context: Context): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildApiService(context.applicationContext).also { apiService = it }
        }
    }

    private fun buildApiService(context: Context): ApiService {
        val tokenManager = TokenManager.getInstance(context)

        val hostInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url
            val candidates = getCandidateHosts(context)

            var lastException: IOException? = null
            var response: Response? = null

            for (candidate in candidates) {
                val parts = candidate.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 8000

                val newUrl = originalUrl.newBuilder()
                    .host(host)
                    .port(port)
                    .build()

                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .build()

                try {
                    response = chain.proceed(newRequest)
                    activeHost = candidate
                    break
                } catch (e: IOException) {
                    lastException = e
                }
            }

            response ?: throw (lastException ?: IOException("Unable to reach backend server"))
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val token = tokenManager.getToken()
            val requestBuilder = originalRequest.newBuilder()
            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val response = chain.proceed(requestBuilder.build())

            // Only clear token if an authenticated request was sent and returned 401
            // Do NOT clear token for auth endpoints like login/register
            val hadAuthHeader = originalRequest.header("Authorization") != null || !token.isNullOrEmpty()
            val isAuthEndpoint = originalRequest.url.encodedPath.contains("/auth/login") ||
                    originalRequest.url.encodedPath.contains("/users/")
            if (response.code == 401 && hadAuthHeader && !isAuthEndpoint) {
                tokenManager.clearToken()
            }
            response
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE // SECURITY: Never log body or auth headers
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(hostInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val initialBase = if (isRunningOnEmulator()) "http://10.0.2.2:8000/" else "http://192.168.29.14:8000/"

        val retrofit = Retrofit.Builder()
            .baseUrl(initialBase)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
