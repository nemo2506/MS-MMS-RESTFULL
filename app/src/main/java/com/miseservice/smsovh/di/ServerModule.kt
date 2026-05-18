package com.miseservice.smsovh.di

import com.miseservice.smsovh.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {
    @Provides
    @Named("restPort")
    fun provideRestPort(settingsRepository: SettingsRepository): Int = runBlocking {
        settingsRepository.getSettings()?.restPort ?: 8080
    }
}
