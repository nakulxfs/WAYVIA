package com.example.services.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.domain.models.PlaceLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DeviceLocationState(
    val hasPermission: Boolean = false,
    val label: String = "Connaught Place, New Delhi (Default)",
    val lat: Double = 28.6315,
    val lon: Double = 77.2167,
    val isGpsFix: Boolean = false
)

class LocationService(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(): DeviceLocationState {
        if (!hasLocationPermission()) {
            return DeviceLocationState(
                hasPermission = false,
                label = "Connaught Place, New Delhi (Default)",
                lat = 28.6315,
                lon = 77.2167,
                isGpsFix = false
            )
        }

        val gpsLocation = suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }

        return if (gpsLocation != null) {
            val inNcr = gpsLocation.latitude in 27.0..29.5 && gpsLocation.longitude in 76.4..78.3
            if (inNcr) {
                DeviceLocationState(
                    hasPermission = true,
                    label = "Current GPS (${"%.4f".format(gpsLocation.latitude)}, ${"%.4f".format(gpsLocation.longitude)})",
                    lat = gpsLocation.latitude,
                    lon = gpsLocation.longitude,
                    isGpsFix = true
                )
            } else {
                // Keep Delhi NCR active scope if emulator GPS is set to Mountain View / outside NCR
                DeviceLocationState(
                    hasPermission = true,
                    label = "Delhi NCR Scope · Rajiv Chowk (GPS outside NCR)",
                    lat = 28.6328,
                    lon = 77.2197,
                    isGpsFix = false
                )
            }
        } else {
            DeviceLocationState(
                hasPermission = true,
                label = "Connaught Place, New Delhi (GPS Ready)",
                lat = 28.6315,
                lon = 77.2167,
                isGpsFix = false
            )
        }
    }

    fun toPlaceLocation(state: DeviceLocationState): PlaceLocation = PlaceLocation(
        id = "current_gps",
        name = if (state.isGpsFix) "Current Location" else "Connaught Place, New Delhi",
        subtitle = state.label,
        lat = state.lat,
        lon = state.lon,
        stopId = if (state.isGpsFix) null else "METRO_RAJIV_CHOWK",
        source = if (state.isGpsFix) "GPS" else "Delhi NCR Default"
    )
}
