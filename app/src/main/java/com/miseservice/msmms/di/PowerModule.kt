package com.miseservice.msmms.di

import com.miseservice.msmms.BuildConfig
import com.miseservice.msmms.data.repository.SettingsRepository
import com.miseservice.msmms.power.PowerApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PowerModule {

    @Provides
    @Singleton
    fun providePowerOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun providePowerRetrofit(okHttpClient: OkHttpClient, settingsRepository: SettingsRepository): Retrofit {
        val settings = runBlocking { settingsRepository.getSettings() }
        val base = settings?.powerBaseUrl?.trim()?.ifBlank { null } 
            ?: BuildConfig.API_BASE_URL.trim().ifBlank { "http://127.0.0.1" }

        val normalized = if (base.endsWith('/')) base else "$base/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePowerApiService(retrofit: Retrofit): PowerApiService =
        retrofit.create(PowerApiService::class.java)
}
