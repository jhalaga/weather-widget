package com.jozefhalaga.weatherwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class HelloWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    appWidgetIds.forEach { widgetId ->
      thread {
        try {
          // 1) Get location using the improved location manager
          val locationData = kotlin.runCatching { 
            // Since we can't use suspend functions in this context, we need to use a blocking approach
            if (WeatherLocationManager.isUsingCustomLocation(context)) {
              WeatherLocationManager.getCustomLocation(context) ?: getFallbackIPLocation()
            } else {
              getPreciseLocationBlocking(context) ?: getFallbackIPLocation()
            }
          }.getOrElse { getFallbackIPLocation() }

          val lat = locationData.latitude
          val lon = locationData.longitude
          val city = locationData.city

          // 2) Update location header
          val views = RemoteViews(context.packageName, R.layout.hello_widget)
          views.setTextViewText(R.id.location_info, "$city ($lat, $lon)")

          // 3) Fetch 16-day forecast
          val urlString = "https://api.open-meteo.com/v1/forecast" +
              "?latitude=$lat&longitude=$lon" +
              "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
              "&forecast_days=16" +
              "&timezone=auto"
          val raw = URL(urlString).openConnection().run {
            inputStream.bufferedReader().readText()
          }
          val daily    = JSONObject(raw).getJSONObject("daily")
          val dates    = daily.getJSONArray("time")
          val maxTemps = daily.getJSONArray("temperature_2m_max")
          val minTemps = daily.getJSONArray("temperature_2m_min")
          val weatherCodes = daily.getJSONArray("weather_code")

          // 4) Populate 16 days
          val dfDate = DateTimeFormatter.ofPattern("d.M.")
          val dfDay  = DateTimeFormatter.ofPattern("EEE")
          for (i in 0 until minOf(16, dates.length())) {
            val ld      = LocalDate.parse(dates.getString(i), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val dateStr = ld.format(dfDate)
            val dayStr  = ld.format(dfDay)
            val maxStr  = maxTemps.getDouble(i).toInt().toString() + "°"
            val minStr  = minTemps.getDouble(i).toInt().toString() + "°"
            val weatherCode = weatherCodes.getInt(i)

            fun id(name: String) = context.resources.getIdentifier("$name$i", "id", context.packageName)
            views.setTextViewText(id("date"), dateStr)
            views.setTextViewText(id("day"),  dayStr)
            views.setTextViewText(id("temp"), maxStr)
            views.setTextViewText(id("min"),  minStr)
            views.setImageViewResource(id("icon"), getWeatherIcon(weatherCode))
          }

          appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
          val views = RemoteViews(context.packageName, R.layout.hello_widget)
          views.setTextViewText(R.id.date0, "Err")
          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }

  private fun getPreciseLocationBlocking(context: Context): LocationData? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      return null
    }

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    // First try to get a fresh location from GPS
    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      val gpsLoc = kotlin.runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
      if (gpsLoc != null && System.currentTimeMillis() - gpsLoc.time < 300000) { // 5 minutes fresh
        val city = getCityFromCoordinatesBlocking(gpsLoc.latitude, gpsLoc.longitude)
        return LocationData(gpsLoc.latitude, gpsLoc.longitude, city)
      }
    }
    
    // Fallback to network location
    if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
      val netLoc = kotlin.runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
      if (netLoc != null) {
        val city = getCityFromCoordinatesBlocking(netLoc.latitude, netLoc.longitude)
        return LocationData(netLoc.latitude, netLoc.longitude, city)
      }
    }
    
    return null
  }

  private fun getCityFromCoordinatesBlocking(lat: Double, lon: Double): String {
    return try {
      val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
      val response = URL(url).openConnection().run {
        connectTimeout = 5000
        readTimeout = 5000
        inputStream.bufferedReader().readText()
      }
      val json = JSONObject(response)
      json.optString("city", "") ?: json.optString("locality", "Unknown Location")
    } catch (e: Exception) {
      "Current Location"
    }
  }

  private fun getFallbackIPLocation(): LocationData {
    return try {
      val geoJson = URL("https://geolocation-db.com/json/").openConnection().run {
        connectTimeout = 5000
        readTimeout = 5000
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

  private fun getWeatherIcon(weatherCode: Int): Int {
    return when (weatherCode) {
      0 -> R.drawable.weather_sunny                    // Clear
      1, 2, 3 -> R.drawable.weather_partly_cloudy      // Partly Cloudy
      45, 48 -> R.drawable.weather_fog                 // Fog
      51, 53, 55 -> R.drawable.weather_drizzle         // Light Drizzle
      56, 57 -> R.drawable.weather_snowy_rainy         // Freezing Drizzle
      61, 63, 65 -> R.drawable.weather_rainy           // Rain
      66, 67 -> R.drawable.weather_snowy_rainy         // Freezing Rain
      71, 73, 75 -> R.drawable.weather_snowy           // Snow
      77 -> R.drawable.weather_hail                    // Snow Grains
      80, 81, 82 -> R.drawable.weather_pouring         // Rain Showers
      85, 86 -> R.drawable.weather_snowy_heavy         // Snow Showers
      95, 96, 99 -> R.drawable.weather_lightning_rainy // Thunderstorm
      else -> R.drawable.weather_partly_cloudy         // Default fallback
    }
  }
}