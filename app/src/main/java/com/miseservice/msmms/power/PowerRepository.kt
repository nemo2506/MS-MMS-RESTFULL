package com.miseservice.msmms.power

import android.content.Context
import com.miseservice.msmms.BuildConfig
import com.miseservice.msmms.util.ApiTokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerRepository @Inject constructor(
    private val api: PowerApiService,
    @ApplicationContext private val context: Context
) {
    private fun getAuthHeader(): String {
        val token = ApiTokenManager.getPowerToken()
        return "Bearer $token"
    }

    suspend fun fetchStatus(baseUrl: String, switchNumber: String): Result<PowerStatusResponse> {
        return runCatching {
            val pin = switchNumber.trim().toInt()
            val url = buildAbsoluteUrl(baseUrl, BuildConfig.API_ENDPOINT_STATUS, pin)
            val response = api.getStatus(url, getAuthHeader())
            if (!response.isSuccessful || response.body() == null) {
                error("Status HTTP ${response.code()}")
            }
            response.body()!!
        }
    }

    suspend fun togglePower(baseUrl: String, switchNumber: String): Result<PowerToggleResponse> {
        return runCatching {
            val pin = switchNumber.trim().toInt()
            val url = buildAbsoluteUrl(baseUrl, BuildConfig.API_ENDPOINT_POWER, pin)
            val response = api.togglePower(url, getAuthHeader())
            if (!response.isSuccessful || response.body() == null) {
                error("Power HTTP ${response.code()}")
            }
            response.body()!!
        }
    }

    private fun buildAbsoluteUrl(baseUrl: String, endpointTemplate: String, pin: Int): String {
        val fallbackBase = BuildConfig.API_BASE_URL.trim().ifBlank { "http://127.0.0.1" }
        val rawBase = baseUrl.trim().ifBlank { fallbackBase }
        val baseWithScheme = if (rawBase.startsWith("http://") || rawBase.startsWith("https://")) {
            rawBase
        } else {
            "http://$rawBase"
        }
        val normalizedBase = baseWithScheme.trimEnd('/')
        val endpoint = endpointTemplate.replace("[PIN]", pin.toString())
        val normalizedEndpoint = if (endpoint.startsWith('/')) endpoint else "/$endpoint"
        return "$normalizedBase$normalizedEndpoint"
    }
}
