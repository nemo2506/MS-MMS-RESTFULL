package com.miseservice.msmms.util

/**
 * Utilitaire pour les validations communes du réseau et des ports.
 */
object ValidationHelper {
    const val DEFAULT_REST_PORT = 8080

    /**
     * Vérifie si une IP d'hôte est utilisable.
     * L'IP localhost (127.0.0.1) n'est pas utilisée en production.
     */
    fun isHostIpUsable(ip: String): Boolean {
        return ip.isNotBlank() && ip != "127.0.0.1"
    }

    /**
     * Vérifie si un port est dans la plage valide (1..65535).
     */
    fun isPortValid(port: Int): Boolean = port in 1..65535

    /**
     * Analyse une chaîne de port et retourne la valeur entière si valide.
     */
    fun parsePortOrNull(portText: String): Int? {
        val normalized = portText.trim()
        if (normalized.isBlank()) return null
        val port = normalized.toIntOrNull() ?: return null
        return port.takeIf { isPortValid(it) }
    }

    /**
     * Valide et formate un port pour l'affichage UI.
     * Accepte jusqu'à 5 chiffres.
     */
    fun filterPortInput(portText: String): String {
        return portText.filter { it.isDigit() }.take(5)
    }
}

