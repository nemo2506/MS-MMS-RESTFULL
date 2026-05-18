package com.miseservice.smsovh.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.miseservice.smsovh.data.repository.NetworkRepository

object NetworkInfoProvider {
    @Volatile private var apiPort: Int = 8080

    @Volatile
    private var hostIp: String? = null

    fun setApiPort(port: Int) {
        apiPort = port.coerceIn(1, 65535)
    }

    fun getApiPort(): Int = apiPort

    fun getHostIp(): String {
        return hostIp ?: "127.0.0.1"
    }

    fun init(context: Context) {
        // La détection initiale ne doit jamais faire planter l'app.
        updateIp(context)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            return
        }

        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateIp(context)
                }

                override fun onLost(network: Network) {
                    updateIp(context)
                }
            })
        } catch (_: Exception) {
            // Sur certains devices/policies, l'enregistrement callback peut échouer.
        }
    }

    private fun updateIp(context: Context) {
        try {
            val snapshot = NetworkRepository(context).detect(apiPort)
            val ip = snapshot.localIpAddress
            hostIp = ip ?: "127.0.0.1"
        } catch (_: Exception) {
            hostIp = "127.0.0.1"
        }
    }
}
