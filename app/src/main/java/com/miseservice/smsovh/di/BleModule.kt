// di/BleModule.kt
package com.miseservice.smsovh.di

import com.miseservice.smsovh.data.ble.BleRepository
import com.miseservice.smsovh.data.ble.BleRepositoryImpl
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