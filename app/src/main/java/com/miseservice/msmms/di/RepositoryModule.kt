package com.miseservice.msmms.di

import com.miseservice.msmms.data.repository.CredentialsRepositoryImpl
import com.miseservice.msmms.domain.repository.CredentialsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCredentialsRepository(
        impl: CredentialsRepositoryImpl
    ): CredentialsRepository
}
