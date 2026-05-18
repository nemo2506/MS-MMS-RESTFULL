package com.miseservice.smsovh.util

/**
 * Configuration OVH pour l'envoi de SMS et MMS
 * Contient tous les paramètres spécifiques à OVH
 */
object OvhSmsConfig {
    /**
     * Paramètres transport locaux (sans dépendance à une librairie externe).
     */
    data class TransportSettings(
        val useSystemSending: Boolean,
        val deliveryReports: Boolean,
        val sendLongAsMms: Boolean,
        val mmsc: String,
        val mmsProxy: String,
        val mmsPort: Int
    )

    /**
     * Paramètres MMSC (MMS Service Center) OVH
     */
    object Mmsc {
        const val URL = "http://mms.ovh.net"
        const val PROXY = "192.168.1.1"  // À adapter selon l'opérateur/APN
        const val PORT = 8080
    }

    /**
     * Paramètres APN (Access Point Name) OVH
     */
    @Suppress("unused")
    object Apn {
        const val NAME = "ovh"
        const val MMSC = "http://mms.ovh.net"
        const val MMS_PROXY = "192.168.1.1"
        const val MMS_PORT = "8080"
        const val APN = "ovh"
    }

    /**
     * Configuration pour les rapports de livraison
     */
    object DeliveryReports {
        const val ENABLED = true
        @Suppress("unused")
        const val TIMEOUT_MS = 30000
    }

    /**
     * Limites des messages
     */
    object Limits {
        const val SMS_CHAR_LIMIT = 160  // Limite SMS standard
        const val SMS_CHAR_LIMIT_WITH_UNICODE = 70  // Avec accents
        const val MMS_SIZE_LIMIT_MB = 3  // Limite MMS 3MB
    }

    /**
     * Configuration réseau
     */
    @Suppress("unused")
    object Network {
        const val CONNECT_TIMEOUT_MS = 15000
        const val READ_TIMEOUT_MS = 15000
        const val WRITE_TIMEOUT_MS = 15000
    }

    /**
     * Crée une configuration OVH pour SMS
     */
    fun createSmsSettings(): TransportSettings {
        return TransportSettings(
            useSystemSending = true,
            deliveryReports = DeliveryReports.ENABLED,
            sendLongAsMms = false,
            mmsc = Mmsc.URL,
            mmsProxy = Mmsc.PROXY,
            mmsPort = Mmsc.PORT
        )
    }

    /**
     * Crée une configuration OVH pour MMS
     */
    fun createMmsSettings(): TransportSettings {
        return TransportSettings(
            useSystemSending = true,
            deliveryReports = DeliveryReports.ENABLED,
            sendLongAsMms = false,
            mmsc = Mmsc.URL,
            mmsProxy = Mmsc.PROXY,
            mmsPort = Mmsc.PORT
        )
    }

    /**
     * Crée une configuration OVH pour messages longs
     */
    fun createLongMessageSettings(): TransportSettings {
        return TransportSettings(
            useSystemSending = true,
            deliveryReports = false,
            sendLongAsMms = true,
            mmsc = Mmsc.URL,
            mmsProxy = Mmsc.PROXY,
            mmsPort = Mmsc.PORT
        )
    }

    /**
     * Valide la taille d'une image MMS
     */
    fun isValidMmsImageSize(sizeBytes: Int): Boolean {
        val limitBytes = Limits.MMS_SIZE_LIMIT_MB * 1024 * 1024
        return sizeBytes in 1..limitBytes
    }

    /**
     * Obtient le message d'erreur pour une taille invalide
     */
    fun getImageSizeErrorMessage(sizeBytes: Int): String {
        val limitMB = Limits.MMS_SIZE_LIMIT_MB
        val sizeMB = sizeBytes / (1024 * 1024)
        return "Taille d'image invalide: ${sizeMB}MB (limite: ${limitMB}MB)"
    }

    /**
     * Valide si un message SMS tiendra en une partie
     */
    fun willFitInSingleSms(message: String, useUnicode: Boolean = false): Boolean {
        val limit = if (useUnicode) Limits.SMS_CHAR_LIMIT_WITH_UNICODE else Limits.SMS_CHAR_LIMIT
        return message.length <= limit
    }

    /**
     * Calcule le nombre de parties SMS
     */
    fun calculateSmsPartCount(message: String, useUnicode: Boolean = false): Int {
        val limit = if (useUnicode) Limits.SMS_CHAR_LIMIT_WITH_UNICODE else Limits.SMS_CHAR_LIMIT
        return kotlin.math.ceil(message.length.toDouble() / limit).toInt()
    }

    /**
     * Conserve l'API publique sans journalisation.
     */
    fun logConfiguration() {
    }
}
