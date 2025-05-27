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
import java.net.URLEncoder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val isCustom: Boolean = false
)

data class SearchResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val country: String
)

object WeatherLocationManager {
    private const val PREFS_NAME = "weather_location_prefs"
    private const val KEY_USE_CUSTOM_LOCATION = "use_custom_location"
    private const val KEY_CUSTOM_LAT = "custom_lat"
    private const val KEY_CUSTOM_LON = "custom_lon"
    private const val KEY_CUSTOM_CITY = "custom_city"
    private const val KEY_FORECAST_MODE = "forecast_mode"

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

    fun saveForecastMode(context: Context, isHourly: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putBoolean(KEY_FORECAST_MODE, isHourly)
            apply()
        }
    }

    fun isHourlyMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FORECAST_MODE, false) // Default to daily
    }

    // Cache location data for widget use
    fun cacheLocationData(context: Context, locationData: LocationData) {
        val prefs = context.getSharedPreferences("weather_location_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("cached_city", locationData.city)
            putFloat("cached_lat", locationData.latitude.toFloat())
            putFloat("cached_lon", locationData.longitude.toFloat())
            putLong("cached_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    fun getCachedLocationData(context: Context): LocationData? {
        val prefs = context.getSharedPreferences("weather_location_cache", Context.MODE_PRIVATE)
        val city = prefs.getString("cached_city", null) ?: return null
        val lat = prefs.getFloat("cached_lat", 0f)
        val lon = prefs.getFloat("cached_lon", 0f)
        val timestamp = prefs.getLong("cached_timestamp", 0)
        
        // Check if cache is not too old (24 hours)
        if (System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000) {
            return null
        }
        
        if (lat == 0f && lon == 0f) return null
        return LocationData(lat.toDouble(), lon.toDouble(), city)
    }

    suspend fun searchCities(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=10&addressdetails=1"
            
            val response = URL(url).openConnection().run {
                setRequestProperty("User-Agent", "WeatherWidget/1.0")
                connectTimeout = 10000
                readTimeout = 10000
                inputStream.bufferedReader().readText()
            }
            
            val jsonArray = org.json.JSONArray(response)
            val results = mutableListOf<SearchResult>()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val address = item.optJSONObject("address")
                
                // Filter for cities, towns, villages
                val placeType = item.optString("type", "")
                val osmType = item.optString("osm_type", "")
                
                if (placeType in listOf("city", "town", "village", "municipality") || 
                    osmType == "relation" || osmType == "way") {
                    
                    val displayName = item.getString("display_name")
                    val lat = item.getDouble("lat")
                    val lon = item.getDouble("lon")
                    
                    // Extract city and country for cleaner display
                    // Try multiple fields to find the actual place name
                    val originalName = item.optString("name", "")
                    val city = address?.optString("city") 
                        ?: address?.optString("town") 
                        ?: address?.optString("village")
                        ?: address?.optString("municipality")
                        ?: address?.optString("hamlet")
                        ?: address?.optString("suburb")
                        ?: originalName
                    
                    val country = address?.optString("country", "") ?: ""
                    
                    val cleanDisplayName = when {
                        // Prioritize the original name if it's meaningful and not just a country
                        originalName.isNotEmpty() && originalName != country && originalName.length > 1 -> {
                            if (country.isNotEmpty()) "$originalName, $country" else originalName
                        }
                        // If we have a valid city name from address fields that's different from original
                        city.isNotEmpty() && city != originalName -> {
                            if (country.isNotEmpty()) "$city, $country" else city
                        }
                        // If no good name found, try to extract from the display name
                        else -> {
                            val parts = displayName.split(",").map { it.trim() }
                            // Get the first non-empty part that's not just a country
                            val placeName = parts.firstOrNull { part -> 
                                part.isNotEmpty() && part != country && part.length > 1
                            } ?: parts.firstOrNull { it.isNotEmpty() }
                            
                            if (placeName != null && placeName.isNotEmpty()) {
                                if (country.isNotEmpty() && placeName != country) {
                                    "$placeName, $country"
                                } else {
                                    placeName
                                }
                            } else {
                                "Unknown Location"
                            }
                        }
                    }
                    
                    results.add(SearchResult(cleanDisplayName, lat, lon, country ?: ""))
                }
            }
            
            results.distinctBy { "${it.latitude},${it.longitude}" }.take(8)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCurrentLocation(context: Context): LocationData = withContext(Dispatchers.IO) {
        // Check if using custom location first
        getCustomLocation(context)?.let { return@withContext it }
        
        // Try to get location with improved error handling
        try {
            // First try to get last known location (fastest)
            val lastKnownLocation = getLastKnownLocation(context)
            if (lastKnownLocation != null) {
                val city = getCityFromCoordinates(lastKnownLocation.latitude, lastKnownLocation.longitude)
                return@withContext LocationData(
                    lastKnownLocation.latitude, 
                    lastKnownLocation.longitude, 
                    city
                )
            }
            
            // If no last known location, try to get fresh GPS location
            val preciseLocation = getPreciseGPSLocation(context)
            if (preciseLocation != null) {
                val city = getCityFromCoordinates(preciseLocation.latitude, preciseLocation.longitude)
                return@withContext LocationData(
                    preciseLocation.latitude, 
                    preciseLocation.longitude, 
                    city
                )
            }
            
            // If GPS fails, fallback to IP-based location
            return@withContext getIPBasedLocation()
            
        } catch (e: Exception) {
            // If everything fails, return IP-based location
            try {
                return@withContext getIPBasedLocation()
            } catch (ipError: Exception) {
                // Absolute fallback
                return@withContext LocationData(51.5074, -0.1278, "London (Fallback)")
            }
        }
    }

    private fun getLastKnownLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as SystemLocationManager
        
        try {
            // Try GPS first, then network
            val gpsLocation = if (locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(SystemLocationManager.GPS_PROVIDER)
            } else null
            
            val networkLocation = if (locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(SystemLocationManager.NETWORK_PROVIDER)
            } else null
            
            // Return the most recent/accurate location
            return when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } catch (e: SecurityException) {
            return null
        }
    }

    private suspend fun getPreciseGPSLocation(context: Context): Location? = withContext(Dispatchers.Main) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as SystemLocationManager
        
        // Check if any location provider is enabled
        if (!locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) && 
            !locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)) {
            return@withContext null
        }

        return@withContext suspendCancellableCoroutine { continuation ->
            var isLocationReceived = false
            
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!isLocationReceived && continuation.isActive) {
                        isLocationReceived = true
                        locationManager.removeUpdates(this)
                        continuation.resumeWith(Result.success(location))
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            try {
                // Start location updates on both GPS and Network for better chances
                var hasStartedLocationRequest = false
                
                if (locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        SystemLocationManager.GPS_PROVIDER,
                        0L, 0f, locationListener
                    )
                    hasStartedLocationRequest = true
                }
                
                if (locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        SystemLocationManager.NETWORK_PROVIDER,
                        0L, 0f, locationListener
                    )
                    hasStartedLocationRequest = true
                }
                
                if (!hasStartedLocationRequest) {
                    continuation.resumeWith(Result.success(null))
                    return@suspendCancellableCoroutine
                }

                // Set up cleanup on cancellation
                continuation.invokeOnCancellation { 
                    locationManager.removeUpdates(locationListener)
                }

                // Set timeout - give it more time (10 seconds instead of 5)
                CoroutineScope(Dispatchers.Main).launch {
                    delay(10000)
                    if (!isLocationReceived && continuation.isActive) {
                        locationManager.removeUpdates(locationListener)
                        continuation.resumeWith(Result.success(null))
                    }
                }
            } catch (e: SecurityException) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(null))
                }
            } catch (e: Exception) {
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


} 