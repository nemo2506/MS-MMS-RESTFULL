package com.miseservice.msmms.data.local

import com.miseservice.msmms.BuildConfig
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val senderId: String? = null,
    val recipient: String? = null,
    val message: String? = null,
    val ovhAppKey: String? = null,
    val ovhAppSecret: String? = null,
    val ovhConsumerKey: String? = null,
    val ovhServiceName: String? = null,
    val ovhEndpoint: String? = "ovh-eu",
    val ovhCountryPrefix: String? = "+33",
    val serviceActive: Boolean = false,
    val hostIp: String? = null,
    val restPort: Int = 8080,
    val token: String? = null,
    val powerToken: String? = null,
    val powerBaseUrl: String = BuildConfig.API_BASE_URL,
    val powerSwitchNumber: String = BuildConfig.API_CHARGE_PIN,
    val powerBatteryMin: Int = 20,
    val powerBatteryMax: Int = 80,
    val powerResolvedIp: String? = null
)
