package com.miseservice.smsovh.util

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

object ApiTokenManager {
    private const val PREF_NAME = "api_token_prefs"
    private const val KEY_TOKEN = "api_token"

    fun getToken(context: Context): String {
        val prefs = getPrefs(context)
        var token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            token = UUID.randomUUID().toString().replace("-", "")
            prefs.edit {
                putString(KEY_TOKEN, token)
            }
        }
        return token
    }

    fun setToken(context: Context, token: String) {
        getPrefs(context).edit {
            putString(KEY_TOKEN, token)
        }
    }

    private fun getPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
}
