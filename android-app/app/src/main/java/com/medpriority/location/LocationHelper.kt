package com.medpriority.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * LocationHelper wraps Google Fused Location Provider.
 *
 * Design decisions:
 *  - Updates every 5 seconds / min 3 seconds during active emergency (tighter than default).
 *  - Validates lat/lon range before emitting to avoid corrupt readings.
 *  - stopAndClear() resets [currentLocation] to null so stale state is never
 *    re-sent to the backend after a session ends.
 *  - stopLocationUpdates() stops callbacks but preserves last known value
 *    (useful for UI display).
 */
class LocationHelper(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                // Validate coordinates before emitting — reject degenerate readings
                if (location.latitude in -90.0..90.0 &&
                    location.longitude in -180.0..180.0 &&
                    (location.latitude != 0.0 || location.longitude != 0.0)
                ) {
                    _currentLocation.value = location
                }
            }
        }
    }

    /**
     * Start receiving GPS updates with high-accuracy, 5-second cadence.
     * Must only be called after location permission has been granted.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L  // 5-second interval
        )
            .setMinUpdateIntervalMillis(3_000L)  // min 3 seconds between updates
            .setMaxUpdateAgeMillis(10_000L)       // don't deliver updates older than 10s
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Stop receiving GPS callbacks but retain the last known location in the
     * StateFlow for UI display purposes.
     */
    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Stop callbacks AND clear the last location.
     * Call this when an emergency ends so no stale coordinates are
     * accidentally re-sent on the next session.
     */
    fun stopAndClear() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _currentLocation.value = null
    }
}
