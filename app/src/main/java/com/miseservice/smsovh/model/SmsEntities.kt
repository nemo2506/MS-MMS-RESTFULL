package com.miseservice.smsovh.model

/**
 * Entité représentant un SMS à envoyer
 */
data class SmsMessage(
    val from: String,
    val to: String,
    val message: String
)

/**
 * Entité représentant un MMS à envoyer
 */
data class MmsMessage(
    val from: String,
    val to: String,
    val message: String,
    val imageBytes: ByteArray? = null,
    val attachments: List<ByteArray>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MmsMessage

        if (from != other.from) return false
        if (to != other.to) return false
        if (message != other.message) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        if (attachments != other.attachments) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (attachments?.hashCode() ?: 0)
        return result
    }
}

/**
 * Types de messages supportés
 */
enum class MessageType {
    SMS,           // Message court texte
    MMS,           // Message multimédia
    LONG_SMS,      // SMS long (concaténé)
    NOTIFICATION   // Notification système
}

/**
 * Résultat de l'envoi
 */
sealed class SendResult {
    object Success : SendResult() {
        override fun toString(): String = "SendResult.Success"
    }
    data class Error(val code: Int, val message: String) : SendResult() {
        override fun toString(): String = "SendResult.Error($code: $message)"
    }
}

