package com.miseservice.smsovh

import android.app.Application
import com.miseservice.smsovh.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.miseservice.smsovh.service.SmsRestServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmsRestServerEntryPoint {
    fun smsRestServer(): SmsRestServer
    fun settingsRepository(): SettingsRepository
}

@HiltAndroidApp
class SmsOvhApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        try {
            val entryPoint = EntryPointAccessors.fromApplication(this, SmsRestServerEntryPoint::class.java)
            val settingsRepository = entryPoint.settingsRepository()
            val smsRestServer = entryPoint.smsRestServer()

            val settings = runBlocking { settingsRepository.getSettings() }
            val initialPort = settings?.restPort ?: 8080
            com.miseservice.smsovh.util.NetworkInfoProvider.setApiPort(initialPort)
            // Initialisation réseau best-effort: ne jamais bloquer le démarrage de l'app.
            try {
                com.miseservice.smsovh.util.NetworkInfoProvider.init(this)
            } catch (_: Exception) {
            }

            smsRestServer.updatePort(initialPort)
            if (settings?.serviceActive == true) {
                smsRestServer.startServer()
            } else {
                smsRestServer.stopServer()
            }

            appScope.launch {
                settingsRepository.observeSettings().collect { observed ->
                    val current = observed ?: return@collect
                    val port = current.restPort.coerceIn(1, 65535)
                    com.miseservice.smsovh.util.NetworkInfoProvider.setApiPort(port)
                    smsRestServer.updatePort(port)
                    if (current.serviceActive) {
                        smsRestServer.startServer()
                    } else {
                        smsRestServer.stopServer()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
