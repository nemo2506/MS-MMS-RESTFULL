package com.miseservice.msmms.util

import android.content.Context
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fournisseur de l'état du réseau SIM.
 * Permet de surveiller l'état de la carte SIM de manière réactive.
 */
@Singleton
class SimNetworkStatusProvider @Inject constructor(
    private val context: Context
) {
    /**
     * Crée un Flow qui émet l'état de la SIM à intervalle régulier.
     * @param intervalMs Intervalle de vérification en millisecondes (défaut: 5000ms)
     * @return Flow<Boolean> émettant true si la SIM est prête, false sinon
     */
    fun observeSimNetworkStatus(intervalMs: Long = 5000L): Flow<Boolean> = flow {
        while (true) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simReady = tm?.simState == TelephonyManager.SIM_STATE_READY
            emit(simReady)
            kotlinx.coroutines.delay(intervalMs)
        }
    }

    /**
     * Récupère l'état actuel de la SIM de manière synchrone.
     */
    fun isSimNetworkReady(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm?.simState == TelephonyManager.SIM_STATE_READY
    }
}

