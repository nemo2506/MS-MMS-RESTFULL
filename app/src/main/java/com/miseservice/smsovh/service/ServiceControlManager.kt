package com.miseservice.smsovh.service

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

        val serverStarted = smsRestServer.startServer()
        started = serverStarted

        if (!serverStarted) {
            // Si le bind du port echoue, on evite de garder un foreground service incoherent.
            context.stopService(Intent(context, SmsOvhForegroundService::class.java))
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
