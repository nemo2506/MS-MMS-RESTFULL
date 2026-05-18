package com.miseservice.smsovh.util

/**
 * Utilitaire pour la validation et le formatage des numéros de téléphone
 */
@Suppress("unused")
object PhoneNumberValidator {

    /**
     * Régex pour valider un numéro de téléphone international
     */
    private val PHONE_REGEX = Regex("^\\+?[0-9]{10,15}$")

    /**
     * Préfixes de pays courants (codes d'accès)
     */
    private val COUNTRY_PREFIXES = mapOf(
        "33" to "France",
        "32" to "Belgium",
        "41" to "Switzerland",
        "34" to "Spain",
        "39" to "Italy",
        "49" to "Germany",
        "44" to "United Kingdom",
        "1" to "USA/Canada",
        "212" to "Morocco",
        "213" to "Algeria",
        "216" to "Tunisia"
    )

    /**
     * Valide un numéro de téléphone
     */
    fun isValid(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        val cleaned = phoneNumber.trim()
        return PHONE_REGEX.matches(cleaned)
    }

    /**
     * Formate un numéro au format international (+33...)
     */
    fun formatToInternational(phoneNumber: String, defaultCountryCode: String = "33"): String? {
        if (!isValid(phoneNumber)) return null

        var formatted = phoneNumber.trim()

        // Supprime les caractères non numériques sauf le +
        formatted = formatted.replace(Regex("[^0-9+]"), "")

        // Gère les cas français 0XXXXXXXXX -> +33XXXXXXXXX
        if (formatted.startsWith("0")) {
            formatted = "+$defaultCountryCode${formatted.substring(1)}"
        } else if (!formatted.startsWith("+")) {
            formatted = "+$formatted"
        }

        return if (isValid(formatted)) formatted else null
    }

    /**
     * Extrait le code pays du numéro
     */
    fun getCountryCode(phoneNumber: String): String? {
        val formatted = formatToInternational(phoneNumber) ?: return null
        for ((code, _) in COUNTRY_PREFIXES) {
            if (formatted.startsWith("+$code")) {
                return code
            }
        }
        return null
    }

    /**
     * Obtient le nom du pays
     */
    fun getCountryName(phoneNumber: String): String? {
        val code = getCountryCode(phoneNumber) ?: return null
        return COUNTRY_PREFIXES[code]
    }

    /**
     * Valide une liste de numéros
     */
    fun validateBatch(phoneNumbers: List<String>): Pair<List<String>, List<String>> {
        val valid = mutableListOf<String>()
        val invalid = mutableListOf<String>()

        for (number in phoneNumbers) {
            if (isValid(number)) {
                valid.add(number)
            } else {
                invalid.add(number)
            }
        }

        return Pair(valid, invalid)
    }


    /**
     * Normalise un numéro (valide + formate)
     */
    fun normalize(phoneNumber: String, defaultCountryCode: String = "33"): String? {
        return if (isValid(phoneNumber)) {
            formatToInternational(phoneNumber, defaultCountryCode)
        } else {
            null
        }
    }
}

