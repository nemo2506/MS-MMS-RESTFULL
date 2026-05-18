package com.miseservice.msmms.di

import android.content.Context
import com.miseservice.msmms.data.datasource.LocalCredentialsDataSource
import com.miseservice.msmms.data.datasource.LocalCredentialsDataSourceImpl
import com.miseservice.msmms.domain.repository.CredentialsRepository
import com.miseservice.msmms.domain.usecase.ClearCredentialsUseCase
import com.miseservice.msmms.domain.usecase.GetLoginUseCase
import com.miseservice.msmms.domain.usecase.GetPasswordUseCase
import com.miseservice.msmms.domain.usecase.SaveCredentialsUseCase
import com.miseservice.msmms.util.RestServerEventManager
import com.miseservice.msmms.service.AndroidServiceControlManager
import com.miseservice.msmms.service.ServiceControlManager
import com.miseservice.msmms.service.SmsRestServer
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

