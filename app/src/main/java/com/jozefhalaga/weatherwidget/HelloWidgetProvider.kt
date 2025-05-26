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
          // 1) Resolve precise device location or fallback to IP
          var lat: Double? = null
          var lon: Double? = null
          var city: String = ""
          if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsLoc = kotlin.runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            val netLoc = kotlin.runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            val loc: Location? = gpsLoc ?: netLoc
            if (loc != null) {
              lat = loc.latitude
              lon = loc.longitude
            }
          }
          if (lat == null || lon == null) {
            val geoJson = URL("https://geolocation-db.com/json/").openConnection().run {
              inputStream.bufferedReader().readText()
            }
            val geoJo = JSONObject(geoJson)
            city = geoJo.optString("city", "")
            lat = geoJo.getDouble("latitude")
            lon = geoJo.getDouble("longitude")
          }

          // 2) Update location header
          val views = RemoteViews(context.packageName, R.layout.hello_widget)
          views.setTextViewText(R.id.location_info, "$city ($lat, $lon)")

          // 3) Fetch 16-day forecast
          val urlString = "https://api.open-meteo.com/v1/forecast" +
              "?latitude=$lat&longitude=$lon" +
              "&daily=temperature_2m_max,temperature_2m_min" +
              "&forecast_days=16" +
              "&timezone=auto"
          val raw = URL(urlString).openConnection().run {
            inputStream.bufferedReader().readText()
          }
          val daily    = JSONObject(raw).getJSONObject("daily")
          val dates    = daily.getJSONArray("time")
          val maxTemps = daily.getJSONArray("temperature_2m_max")
          val minTemps = daily.getJSONArray("temperature_2m_min")

          // 4) Populate 16 days
          val dfDate = DateTimeFormatter.ofPattern("d.M.")
          val dfDay  = DateTimeFormatter.ofPattern("EEE")
          for (i in 0 until minOf(16, dates.length())) {
            val ld      = LocalDate.parse(dates.getString(i), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val dateStr = ld.format(dfDate)
            val dayStr  = ld.format(dfDay)
            val maxStr  = maxTemps.getDouble(i).toInt().toString() + "°"
            val minStr  = minTemps.getDouble(i).toInt().toString() + "°"

            fun id(name: String) = context.resources.getIdentifier("$name$i", "id", context.packageName)
            views.setTextViewText(id("date"), dateStr)
            views.setTextViewText(id("day"),  dayStr)
            views.setTextViewText(id("temp"), maxStr)
            views.setTextViewText(id("min"),  minStr)
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
}