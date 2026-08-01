package com.scaso.drclawapp.cmd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gets last known location via LocationManager (no Google Play dependency).
 * Tool: device.location
 *
 * Returns lat, lon, accuracy, provider, and age in seconds.
 */
class LocationHandler : CmdFrameHandler {
    override val tool: String = "device.location"

    override suspend fun execute(params: JsonObject, context: Context): JsonElement {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
            && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return buildJsonObject {
                put("error", "Location permission not granted")
            }
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return buildJsonObject { put("error", "LocationManager unavailable") }

        return try {
            val location = getBestLastLocation(locationManager)
            if (location != null) {
                val ageSecs = (System.currentTimeMillis() - location.time) / 1000
                buildJsonObject {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("accuracy_meters", location.accuracy.toDouble())
                    put("provider", location.provider ?: "unknown")
                    put("age_seconds", ageSecs)
                    put("altitude", if (location.hasAltitude()) location.altitude else null)
                }
            } else {
                buildJsonObject {
                    put("error", "No last known location available")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location query failed", e)
            buildJsonObject {
                put("error", e.message ?: "Location query failed")
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun getBestLastLocation(locationManager: LocationManager): Location? {
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || location.time > bestLocation.time) {
                    bestLocation = location
                }
            } catch (_: SecurityException) {
                // Skip providers we can't access
            }
        }
        return bestLocation
    }

    companion object {
        private const val TAG = "LocationHandler"
    }
}
