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


} 