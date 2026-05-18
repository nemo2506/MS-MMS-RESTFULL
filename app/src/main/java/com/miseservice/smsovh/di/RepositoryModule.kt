package com.miseservice.smsovh.di

import com.miseservice.smsovh.data.repository.CredentialsRepositoryImpl
import com.miseservice.smsovh.domain.repository.CredentialsRepository
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
