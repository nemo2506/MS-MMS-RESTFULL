package com.miseservice.msmms.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ServiceControlManager {
    fun start()
    fun stop()
}

@Singleton
class AndroidServiceControlManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsRestServer: SmsRestServer
) : ServiceControlManager {
    @Volatile
    private var started = false

    @Synchronized
    override fun start() {
        if (started || smsRestServer.isRunning()) {
            started = true
            return
        }

        ContextCompat.startForegroundService(
            context,
            Intent(context, SmsOvhForegroundService::class.java)
        )

        // On démarre le serveur. S'il échoue, on laisse le service tourner ou on gère
        // l'arrêt de manière asynchrone pour éviter l'ANR de transition.
        val serverStarted = smsRestServer.startServer()
        started = serverStarted

        if (!serverStarted) {
            // On ne stopService pas immédiatement pour éviter l'ANR (promesse non tenue).
            // L'état d'erreur est déjà géré par smsRestServer via des événements UI.
            started = false
        }
    }

    @Synchronized
    override fun stop() {
        // Best effort stop: le serveur REST peut avoir été démarré ailleurs (ex: Application).
        context.stopService(Intent(context, SmsOvhForegroundService::class.java))
        smsRestServer.stopServer()
        started = false
    }
}
