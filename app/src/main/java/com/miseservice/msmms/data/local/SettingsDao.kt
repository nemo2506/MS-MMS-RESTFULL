package com.miseservice.msmms.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("UPDATE app_settings SET token = :token WHERE id = 1")
    suspend fun updateToken(token: String?)

    @Query("UPDATE app_settings SET powerToken = :powerToken WHERE id = 1")
    suspend fun updatePowerToken(powerToken: String?)

    @Query("UPDATE app_settings SET restPort = :restPort WHERE id = 1")
    suspend fun updateRestPort(restPort: Int)
}
