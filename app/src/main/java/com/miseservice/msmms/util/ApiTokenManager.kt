package com.miseservice.msmms.util

import com.miseservice.msmms.BuildConfig

/**
 * Gère les tokens d'authentification en mémoire.
 * Les tokens sont synchronisés depuis la base de données par le MainViewModel.
 */
object ApiTokenManager {
    @Volatile
    private var serverToken: String? = null

    @Volatile
    private var powerToken: String? = null

    /**
     * Token pour le serveur REST entrant (MMS).
     * Fallback: BuildConfig.SERVER_TOKEN
     */
    fun getServerToken(): String {
        return serverToken ?: BuildConfig.SERVER_TOKEN.trim()
    }

    /**
     * Token pour le client Power API sortant.
     * Fallback: BuildConfig.API_BEARER
     */
    fun getPowerToken(): String {
        return powerToken ?: BuildConfig.API_BEARER.trim()
    }

    /**
     * Met à jour le token du serveur (entrant).
     */
    fun setServerToken(token: String?) {
        serverToken = token?.trim()?.ifBlank { null }
    }

    /**
     * Met à jour le token Power (sortant).
     */
    fun setPowerToken(token: String?) {
        powerToken = token?.trim()?.ifBlank { null }
    }

    // Compatibilité temporaire si nécessaire, à migrer vers getServerToken/getPowerToken
    @Deprecated("Utiliser getPowerToken() ou getServerToken()", ReplaceWith("getPowerToken()"))
    fun getToken(): String = getPowerToken()
}
