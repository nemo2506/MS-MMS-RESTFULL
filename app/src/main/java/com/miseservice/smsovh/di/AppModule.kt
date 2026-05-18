package com.miseservice.smsovh.di

import android.content.Context
import com.miseservice.smsovh.data.datasource.LocalCredentialsDataSource
import com.miseservice.smsovh.data.datasource.LocalCredentialsDataSourceImpl
import com.miseservice.smsovh.domain.repository.CredentialsRepository
import com.miseservice.smsovh.domain.usecase.ClearCredentialsUseCase
import com.miseservice.smsovh.domain.usecase.GetLoginUseCase
import com.miseservice.smsovh.domain.usecase.GetPasswordUseCase
import com.miseservice.smsovh.domain.usecase.SaveCredentialsUseCase
import com.miseservice.smsovh.util.RestServerEventManager
import com.miseservice.smsovh.service.AndroidServiceControlManager
import com.miseservice.smsovh.service.ServiceControlManager
import com.miseservice.smsovh.service.SmsRestServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideLocalCredentialsDataSource(@ApplicationContext context: Context): LocalCredentialsDataSource =
        LocalCredentialsDataSourceImpl(context)

    @Provides
    @Singleton
    fun provideSaveCredentialsUseCase(repo: CredentialsRepository) = SaveCredentialsUseCase(repo)

    @Provides
    @Singleton
    fun provideGetLoginUseCase(repo: CredentialsRepository) = GetLoginUseCase(repo)

    @Provides
    @Singleton
    fun provideGetPasswordUseCase(repo: CredentialsRepository) = GetPasswordUseCase(repo)

    @Provides
    @Singleton
    fun provideClearCredentialsUseCase(repo: CredentialsRepository) = ClearCredentialsUseCase(repo)

    @Provides
    @Singleton
    fun provideRestServerEventManager(): RestServerEventManager = RestServerEventManager()

    @Provides
    @Singleton
    fun provideServiceControlManager(
        @ApplicationContext context: Context,
        smsRestServer: SmsRestServer
    ): ServiceControlManager = AndroidServiceControlManager(context, smsRestServer)
}

