package com.example.runpodmanager.data.api

import com.example.runpodmanager.data.local.ApiKeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val apiKeyManager: ApiKeyManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = runBlocking {
            apiKeyManager.apiKey.first()
        }

        val request = chain.request().newBuilder()
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .addHeader("Content-Type", "application/json")
            .build()

        return chain.proceed(request)
    }
}
