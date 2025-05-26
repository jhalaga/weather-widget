package com.jozefhalaga.weatherwidget

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as SystemLocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val isCustom: Boolean = false
)

object WeatherLocationManager {
    private const val PREFS_NAME = "weather_location_prefs"
    private const val KEY_USE_CUSTOM_LOCATION = "use_custom_location"
    private const val KEY_CUSTOM_LAT = "custom_lat"
    private const val KEY_CUSTOM_LON = "custom_lon"
    private const val KEY_CUSTOM_CITY = "custom_city"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLocationPreference(context: Context, useCustom: Boolean, locationData: LocationData? = null) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putBoolean(KEY_USE_CUSTOM_LOCATION, useCustom)
            if (useCustom && locationData != null) {
                putFloat(KEY_CUSTOM_LAT, locationData.latitude.toFloat())
                putFloat(KEY_CUSTOM_LON, locationData.longitude.toFloat())
                putString(KEY_CUSTOM_CITY, locationData.city)
            }
            apply()
        }
    }

    fun isUsingCustomLocation(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_CUSTOM_LOCATION, false)
    }

    fun getCustomLocation(context: Context): LocationData? {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_USE_CUSTOM_LOCATION, false)) return null
        
        val lat = prefs.getFloat(KEY_CUSTOM_LAT, 0f).toDouble()
        val lon = prefs.getFloat(KEY_CUSTOM_LON, 0f).toDouble()
        val city = prefs.getString(KEY_CUSTOM_CITY, "") ?: ""
        
        if (lat == 0.0 && lon == 0.0) return null
        return LocationData(lat, lon, city, true)
    }

    suspend fun getCurrentLocation(context: Context): LocationData = withContext(Dispatchers.IO) {
        // Check if using custom location
        getCustomLocation(context)?.let { return@withContext it }
        
        // Try to get precise GPS location
        val preciseLocation = getPreciseGPSLocation(context)
        if (preciseLocation != null) {
            val city = getCityFromCoordinates(preciseLocation.latitude, preciseLocation.longitude)
            return@withContext LocationData(
                preciseLocation.latitude, 
                preciseLocation.longitude, 
                city
            )
        }
        
        // Fallback to IP-based location
        getIPBasedLocation()
    }

    private suspend fun getPreciseGPSLocation(context: Context): Location? = withContext(Dispatchers.Main) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as SystemLocationManager
        
        // Check if GPS is enabled
        if (!locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) && 
            !locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)) {
            return@withContext null
        }

        return@withContext suspendCancellableCoroutine { continuation ->
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(location))
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            try {
                // Try GPS first for highest accuracy
                if (locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        SystemLocationManager.GPS_PROVIDER,
                        0L, 0f, locationListener
                    )
                } else if (locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        SystemLocationManager.NETWORK_PROVIDER,
                        0L, 0f, locationListener
                    )
                }

                // Set timeout for location request
                continuation.invokeOnCancellation { 
                    locationManager.removeUpdates(locationListener)
                }

                // Fallback to last known location after 5 seconds
                CoroutineScope(Dispatchers.Main).launch {
                    delay(5000)
                    if (continuation.isActive) {
                        val lastKnown = locationManager.getLastKnownLocation(SystemLocationManager.GPS_PROVIDER)
                            ?: locationManager.getLastKnownLocation(SystemLocationManager.NETWORK_PROVIDER)
                        locationManager.removeUpdates(locationListener)
                        continuation.resumeWith(Result.success(lastKnown))
                    }
                }
            } catch (e: SecurityException) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(null))
                }
            }
        }
    }

    private suspend fun getCityFromCoordinates(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            // Use a reverse geocoding service
            val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
            val response = URL(url).openConnection().run {
                inputStream.bufferedReader().readText()
            }
            val json = JSONObject(response)
            json.optString("city", "") ?: json.optString("locality", "Unknown Location")
        } catch (e: Exception) {
            "Unknown Location"
        }
    }

    private suspend fun getIPBasedLocation(): LocationData = withContext(Dispatchers.IO) {
        try {
            val geoJson = URL("https://geolocation-db.com/json/").openConnection().run {
                inputStream.bufferedReader().readText()
            }
            val geoJo = JSONObject(geoJson)
            val city = geoJo.optString("city", "Unknown Location")
            val lat = geoJo.getDouble("latitude")
            val lon = geoJo.getDouble("longitude")
            LocationData(lat, lon, city)
        } catch (e: Exception) {
            // Default fallback location (London)
            LocationData(51.5074, -0.1278, "London")
        }
    }

    // Predefined cities for location picker
    val popularCities = listOf(
        LocationData(51.5074, -0.1278, "London, UK"),
        LocationData(40.7128, -74.0060, "New York, USA"),
        LocationData(48.8566, 2.3522, "Paris, France"),
        LocationData(35.6762, 139.6503, "Tokyo, Japan"),
        LocationData(37.7749, -122.4194, "San Francisco, USA"),
        LocationData(52.5200, 13.4050, "Berlin, Germany"),
        LocationData(41.9028, 12.4964, "Rome, Italy"),
        LocationData(55.7558, 37.6176, "Moscow, Russia"),
        LocationData(-33.8688, 151.2093, "Sydney, Australia"),
        LocationData(48.2082, 16.3738, "Vienna, Austria"),
        LocationData(50.0755, 14.4378, "Prague, Czech Republic"),
        LocationData(47.4979, 19.0402, "Budapest, Hungary"),
        LocationData(52.2297, 21.0122, "Warsaw, Poland"),
        LocationData(59.3293, 18.0686, "Stockholm, Sweden"),
        LocationData(45.4642, 9.1900, "Milan, Italy"),
        LocationData(41.3851, 2.1734, "Barcelona, Spain"),
        LocationData(52.3676, 4.9041, "Amsterdam, Netherlands"),
        LocationData(50.8503, 4.3517, "Brussels, Belgium"),
        LocationData(47.3769, 8.5417, "Zurich, Switzerland")
    )
} 