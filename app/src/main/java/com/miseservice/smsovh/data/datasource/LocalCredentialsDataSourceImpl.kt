package com.miseservice.smsovh.data.datasource

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

class LocalCredentialsDataSourceImpl(context: Context) : LocalCredentialsDataSource {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ovh_sms_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveCredentials(login: String, password: String) {
        sharedPreferences.edit { putString("login", login).putString("password", password) }
    }

    override fun getLogin(): String? = sharedPreferences.getString("login", null)
    override fun getPassword(): String? = sharedPreferences.getString("password", null)
    override fun clearCredentials() {
        sharedPreferences.edit { remove("login").remove("password") }
    }
}
