package com.miseservice.msmms.di

import com.miseservice.msmms.domain.repository.SmsRepository
import com.miseservice.msmms.data.repository.SmsRepositoryImpl
import com.miseservice.msmms.domain.usecase.SendMessageUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindModule {
    @Binds
    @Singleton
    abstract fun bindSmsRepository(impl: SmsRepositoryImpl): SmsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun provideSendMessageUseCase(repository: SmsRepository): SendMessageUseCase = SendMessageUseCase(repository)
}

