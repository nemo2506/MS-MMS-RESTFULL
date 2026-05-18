package com.miseservice.msmms.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Utilitaire pour accéder à l'état de la batterie du système.
 */
object BatteryHelper {
    /**
     * Récupère le pourcentage de batterie actuel du système.
     * Retourne null si les données ne sont pas disponibles.
     */
    fun getCurrentBatteryPercent(context: Context): Int? {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }
}

