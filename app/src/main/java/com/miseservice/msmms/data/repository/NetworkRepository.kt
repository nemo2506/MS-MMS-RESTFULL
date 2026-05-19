package com.miseservice.msmms.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Snapshot réseau utile pour l'UI et les endpoints API.
 */
data class NetworkSnapshot(
    val isWifiConnected: Boolean,
    val wifiSsid: String?,
    val hasLocationPermission: Boolean,
    val isLocationEnabled: Boolean,
    val localIpAddress: String?,
    val batteryApiUrl: String?,
    val cameraFormatsApiUrl: String?,
    val streamUrl: String?,
    val viewerUrl: String?
)

class NetworkRepository(private val context: Context) {

    fun detect(port: Int): NetworkSnapshot {
        val wifiConnected = isWifiConnected()
        val hasLocationPermission = hasLocationPermission()
        val locationEnabled = isLocationEnabled()
        val ip = localIpAddress()
        return NetworkSnapshot(
            isWifiConnected = wifiConnected,
            wifiSsid = if (wifiConnected) wifiSsid(hasLocationPermission, locationEnabled) else null,
            hasLocationPermission = hasLocationPermission,
            isLocationEnabled = locationEnabled,
            localIpAddress = ip,
            batteryApiUrl = ip?.let { "http://$it:$port/api/battery" },
            cameraFormatsApiUrl = ip?.let { "http://$it:$port/api/camera/formats" },
            streamUrl = ip?.let { "http://$it:$port/stream.mjpeg" },
            viewerUrl = ip?.let { "http://$it:$port/" }
        )
    }

    private fun isWifiConnected(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        return runCatching {
            val activeNetwork = manager.activeNetwork
            if (activeNetwork != null) {
                val capabilities = manager.getNetworkCapabilities(activeNetwork)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    return true
                }
            }

            manager.allNetworks.any { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }.getOrDefault(false)
    }

    private fun localIpAddress(): String? {
        // On privilégie NetworkInterface car LinkProperties.getLinkAddresses() crash sur certains devices
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            
            // 1. Priorité aux interfaces Wi-Fi (wlan, eth)
            val wifiIp = interfaces
                .filter { it.isUp && (it.name.contains("wlan", true) || it.name.contains("eth", true)) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
            
            if (wifiIp != null) return wifiIp

            // 2. Fallback sur n'importe quelle interface UP (non loopback, non p2p)
            interfaces
                .filter { it.isUp && !it.isLoopback && !it.name.contains("p2p", true) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun wifiSsid(hasLocationPermission: Boolean, locationEnabled: Boolean): String? {
        if (!hasLocationPermission || !locationEnabled) {
            return null
        }

        val ssidFromCapabilities = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = manager?.activeNetwork
                val capabilities = network?.let { manager.getNetworkCapabilities(it) }
                (capabilities?.transportInfo as? WifiInfo)?.ssid?.sanitizeSsid()
            } else {
                null
            }
        }.getOrNull()

        if (!ssidFromCapabilities.isNullOrBlank()) return ssidFromCapabilities

        return runCatching {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo?.ssid?.sanitizeSsid()
        }.getOrNull()
    }

    private fun String.sanitizeSsid(): String? {
        val clean = trim().trim('"')
        if (clean.isBlank()) return null
        if (clean.equals("<unknown ssid>", ignoreCase = true)) return null
        if (clean.equals("unknown ssid", ignoreCase = true)) return null
        return clean
    }
}
