// di/BleModule.kt
package com.miseservice.msmms.di

import com.miseservice.msmms.data.ble.BleRepository
import com.miseservice.msmms.data.ble.BleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {
    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository
}