package com.miseservice.msmms.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fournisseur de données de localisation.
 * Externalise la logique d'obtention de la dernière position connue.
 */
@Singleton
class LocationDataProvider @Inject constructor(
    private val context: Context
) {
    /**
     * Récupère la meilleure localisation disponible parmi les providers actifs.
     * @return Pair(latitude, longitude) ou null si aucune localisation disponible
     */
    fun getLastKnownLocation(): Pair<Double, Double>? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null

        for (provider in providers) {
            val location = try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.getLastKnownLocation(provider)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }

            if (location != null && (bestLocation == null || location.accuracy < bestLocation.accuracy)) {
                bestLocation = location
            }
        }

        return bestLocation?.let { Pair(it.latitude, it.longitude) }
    }
}

