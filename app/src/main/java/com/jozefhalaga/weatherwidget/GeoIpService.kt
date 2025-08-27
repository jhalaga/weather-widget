package com.jozefhalaga.weatherwidget

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object GeoIpService {
    suspend fun lookupOrDefault(context: Context): LocationData = withContext(Dispatchers.IO) {
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
            LocationData(51.5074, -0.1278, "London")
        }
    }
}


