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
    private const val KEY_ITEMS_PER_ROW_HOURLY = "items_per_row_hourly"
    private const val KEY_ITEMS_PER_ROW_DAILY = "items_per_row_daily"
    private const val KEY_WIDGET_FONT_SCALE = "widget_font_scale"
    private const val KEY_FAVORITES_JSON = "favorite_locations_json"

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

    // Widget customization settings
    fun setItemsPerRowHourly(context: Context, count: Int) {
        val safe = when (count) { 4, 6, 8 -> count; else -> 8 }
        getPrefs(context).edit().putInt(KEY_ITEMS_PER_ROW_HOURLY, safe).apply()
    }
    fun setItemsPerRowDaily(context: Context, count: Int) {
        val safe = when (count) { 4, 6, 8 -> count; else -> 8 }
        getPrefs(context).edit().putInt(KEY_ITEMS_PER_ROW_DAILY, safe).apply()
    }
    fun getItemsPerRowHourly(context: Context): Int {
        return getPrefs(context).getInt(KEY_ITEMS_PER_ROW_HOURLY, 8)
    }
    fun getItemsPerRowDaily(context: Context): Int {
        return getPrefs(context).getInt(KEY_ITEMS_PER_ROW_DAILY, 8)
    }
    fun setWidgetFontScale(context: Context, scale: Float) {
        val clamped = scale.coerceIn(1.0f, 2.0f)
        getPrefs(context).edit().putFloat(KEY_WIDGET_FONT_SCALE, clamped).apply()
    }
    fun getWidgetFontScale(context: Context): Float {
        val v = getPrefs(context).getFloat(KEY_WIDGET_FONT_SCALE, 1.0f)
        return if (v == 0f) 1.0f else v
    }

    // Favorite locations
    fun getFavoriteLocations(context: Context): List<LocationData> {
        val json = getPrefs(context).getString(KEY_FAVORITES_JSON, "") ?: ""
        if (json.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val out = mutableListOf<LocationData>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    LocationData(
                        latitude = o.optDouble("lat"),
                        longitude = o.optDouble("lon"),
                        city = o.optString("city"),
                        isCustom = true
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
    private fun saveFavoriteLocations(context: Context, favorites: List<LocationData>) {
        val arr = org.json.JSONArray()
        favorites.forEach { loc ->
            val o = org.json.JSONObject()
            o.put("lat", loc.latitude)
            o.put("lon", loc.longitude)
            o.put("city", loc.city)
            arr.put(o)
        }
        getPrefs(context).edit().putString(KEY_FAVORITES_JSON, arr.toString()).apply()
    }
    fun addFavoriteLocation(context: Context, locationData: LocationData) {
        val list = getFavoriteLocations(context).toMutableList()
        if (list.none { Math.abs(it.latitude - locationData.latitude) < 1e-6 && Math.abs(it.longitude - locationData.longitude) < 1e-6 }) {
            list.add(locationData.copy(isCustom = true))
            saveFavoriteLocations(context, list)
        }
    }
    fun removeFavoriteLocation(context: Context, locationData: LocationData) {
        val list = getFavoriteLocations(context).toMutableList()
        val filtered = list.filterNot { Math.abs(it.latitude - locationData.latitude) < 1e-6 && Math.abs(it.longitude - locationData.longitude) < 1e-6 }
        saveFavoriteLocations(context, filtered)
    }
    fun isFavorite(context: Context, locationData: LocationData): Boolean {
        return getFavoriteLocations(context).any { Math.abs(it.latitude - locationData.latitude) < 1e-6 && Math.abs(it.longitude - locationData.longitude) < 1e-6 }
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
                connectTimeout = 30000
                readTimeout = 30000
                inputStream.bufferedReader().readText()
            }
            
            val jsonArray = org.json.JSONArray(response)
            val results = mutableListOf<Pair<Double, SearchResult>>()
            
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
                    val importance = item.optDouble("importance", 0.0)
                    
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
                    val state = address?.optString("state") ?: address?.optString("region")
                    val county = address?.optString("county") ?: ""

                    val primary = if (city.isNotEmpty()) city else originalName
                    val secondary = listOfNotNull(state?.takeIf { it.isNotEmpty() } ?: county.takeIf { it.isNotEmpty() })
                        .firstOrNull()
                    val cleanDisplayName = buildString {
                        append(primary)
                        if (!secondary.isNullOrEmpty()) append(", ").append(secondary)
                        if (country.isNotEmpty()) append(", ").append(country)
                    }.ifBlank { displayName }
                    
                    results.add(importance to SearchResult(cleanDisplayName, lat, lon, country))
                }
            }
            
            results
                .sortedByDescending { it.first }
                .map { it.second }
                .distinctBy { "${it.latitude},${it.longitude}" }
                .take(8)
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