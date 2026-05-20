package com.miseservice.msmms.power

import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class PowerStatusResponse(
    val pin: Int,
    val state: String,
    val value: Int
)

data class PowerToggleResponse(
    val pin: Int,
    val state: String,
    val value: Int,
    val action: String?
)

interface PowerApiService {
    @POST
    suspend fun getStatus(
        @Url url: String,
        @Header("Authorization") token: String
    ): Response<PowerStatusResponse>

    @POST
    suspend fun togglePower(
        @Url url: String,
        @Header("Authorization") token: String
    ): Response<PowerToggleResponse>
}

