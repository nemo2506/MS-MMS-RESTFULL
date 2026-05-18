package com.miseservice.smsovh.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object SmsPermissionsManager {

    /**
     * Permissions réellement demandables à l'utilisateur.
     * - CHANGE_NETWORK_STATE : permission système, ne jamais la demander (toujours refusée)
     * - RECEIVE_MMS : réservée à l'app SMS par défaut, exclue sauf cas spécifique
     */
    val REQUIRED_PERMISSIONS: List<String> = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)

        // INTERNET et ACCESS_NETWORK_STATE sont des "normal permissions" :
        // accordées automatiquement si déclarées dans le manifest, inutile de les demander
        // au runtime — mais on les vérifie quand même par sécurité.
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.ACCESS_NETWORK_STATE)

        // POST_NOTIFICATIONS requis depuis Android 13 pour les notifications de statut
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Permissions normales (accordées automatiquement via le manifest).
     * Ne doivent PAS être demandées au runtime, mais peuvent être vérifiées.
     */
    val NORMAL_PERMISSIONS = listOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        // CHANGE_NETWORK_STATE : déclarée dans le manifest uniquement pour MMS,
        // Android l'accorde automatiquement aux apps non-système en mode "best effort"
        Manifest.permission.CHANGE_NETWORK_STATE
    )

    /**
     * Permissions à demander au runtime uniquement.
     * Exclut les normal permissions inutiles à demander explicitement.
     */
    val RUNTIME_PERMISSIONS: List<String> = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { hasPermission(context, it) }
    }

    /**
     * Retourne uniquement les permissions runtime manquantes.
     * Les normal permissions (INTERNET, etc.) sont exclues car elles ne peuvent
     * pas être demandées via requestPermissions().
     */
    fun getMissingRuntimePermissions(context: Context): List<String> {
        return RUNTIME_PERMISSIONS.filter { !hasPermission(context, it) }
    }

    fun getMissingPermissionsArray(context: Context): Array<String> {
        return getMissingRuntimePermissions(context).toTypedArray()
    }

    fun canSendSms(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.SEND_SMS)
    }

    fun canSendMms(context: Context): Boolean {
        // CHANGE_NETWORK_STATE et ACCESS_NETWORK_STATE sont des normal permissions :
        // si elles sont dans le manifest, elles sont accordées. Pas besoin de les tester
        // comme bloquant l'envoi MMS.
        return canSendSms(context) &&
                hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)
    }
}