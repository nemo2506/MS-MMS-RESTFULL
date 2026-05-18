package com.miseservice.msmms.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val senderId: String?,
    val recipient: String?,
    val message: String?,
    val ovhAppKey: String? = null,
    val ovhAppSecret: String? = null,
    val ovhConsumerKey: String? = null,
    val ovhServiceName: String? = null,
    val ovhEndpoint: String? = null,
    val ovhCountryPrefix: String? = null,
    val serviceActive: Boolean,
    val hostIp: String?,
    val restPort: Int = 8080,
    val token: String? = null,
    val blePin: String? = null,
    val bleMinBattery: Int = 20,
    val bleMaxBattery: Int = 80
)
