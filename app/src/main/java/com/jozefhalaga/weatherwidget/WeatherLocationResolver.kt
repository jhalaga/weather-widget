package com.jozefhalaga.weatherwidget

import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine

data class LatLon(val latitude: Double, val longitude: Double)

class WeatherLocationResolver(private val ctx: Context) {

    suspend fun resolveForBackground(): LatLon {
        WeatherLocationManager.getCustomLocation(ctx)?.let {
            return LatLon(it.latitude, it.longitude)
        }

        val fused = LocationServices.getFusedLocationProviderClient(ctx)
        val last: Location? = suspendCancellableCoroutine { cont ->
            fused.lastLocation
                .addOnSuccessListener { cont.resume(it, onCancellation = null) }
                .addOnFailureListener { cont.resume(null, onCancellation = null) }
        }
        if (last != null) return LatLon(last.latitude, last.longitude)

        val ip = GeoIpService.lookupOrDefault(ctx)
        return LatLon(ip.latitude, ip.longitude)
    }
}


