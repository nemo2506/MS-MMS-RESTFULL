package com.miseservice.msmms.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.os.Build
import com.miseservice.msmms.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire du presse-papiers.
 * Encapsule la logique de copie et l'affichage de notifs.
 */
@Singleton
class ClipboardProvider @Inject constructor(
    private val context: Context
) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * Copie une valeur dans le presse-papiers et affiche un message Toast si approprié.
     * @param label Label pour le presse-papiers
     * @param value Valeur à copier
     */
    fun copyToClipboard(label: String, value: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, value))

        // Afficher le Toast uniquement avant Android 13 (Tiramisu)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val messageRes = when (label) {
                "send-endpoint" -> R.string.endpoint_send_copied
                "logs-endpoint" -> R.string.endpoint_logs_copied
                "battery-endpoint" -> R.string.endpoint_battery_copied
                "connected-ip" -> R.string.connected_ip_copied
                "token" -> R.string.token_copied
                "location" -> R.string.location_copied
                "network" -> R.string.network_copied
                else -> R.string.endpoint_copied
            }
            Toast.makeText(
                context,
                context.getString(messageRes),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
