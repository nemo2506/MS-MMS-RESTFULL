package com.miseservice.msmms.data.repository

import com.miseservice.msmms.data.local.AppSettingsEntity
import com.miseservice.msmms.data.local.SettingsDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    suspend fun getSettings(): AppSettingsEntity? = settingsDao.getSettings()
    fun observeSettings(): Flow<AppSettingsEntity?> = settingsDao.observeSettings()
    suspend fun saveSettings(settings: AppSettingsEntity) = settingsDao.saveSettings(settings)
    suspend fun updateToken(token: String) = settingsDao.updateToken(token)
    suspend fun updateRestPort(restPort: Int) = settingsDao.updateRestPort(restPort)
    suspend fun updateBleConnectionActive(active: Boolean) = settingsDao.updateBleConnectionActive(active)
}
