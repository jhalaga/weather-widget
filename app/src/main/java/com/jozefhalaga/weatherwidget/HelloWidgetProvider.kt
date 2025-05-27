package com.jozefhalaga.weatherwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews


class HelloWidgetProvider : AppWidgetProvider() {
  
  companion object {
    const val ACTION_TOGGLE_FORECAST = "com.jozefhalaga.weatherwidget.TOGGLE_FORECAST"
  }

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    for (appWidgetId in appWidgetIds) {
      updateAppWidget(context, appWidgetManager, appWidgetId)
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    
    if (intent.action == ACTION_TOGGLE_FORECAST) {
      val appWidgetManager = AppWidgetManager.getInstance(context)
      val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 
        AppWidgetManager.INVALID_APPWIDGET_ID)
      
      if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        // Toggle forecast mode
        val isCurrentlyHourly = WeatherLocationManager.isHourlyMode(context)
        WeatherLocationManager.saveForecastMode(context, !isCurrentlyHourly)
        
        // Update the widget
        updateAppWidget(context, appWidgetManager, appWidgetId)
        
        // Notify the ListView to refresh its data
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.forecast_list)
      }
    }
  }

  private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    try {
      val views = RemoteViews(context.packageName, R.layout.hello_widget)
      
      // Update toggle button text
      val isHourly = WeatherLocationManager.isHourlyMode(context)
      views.setTextViewText(R.id.toggle_forecast, if (isHourly) "Hourly" else "Daily")
      
      // Set up toggle button click
      val toggleIntent = Intent(context, HelloWidgetProvider::class.java).apply {
        action = ACTION_TOGGLE_FORECAST
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
      }
      val togglePendingIntent = PendingIntent.getBroadcast(
        context, appWidgetId, toggleIntent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      views.setOnClickPendingIntent(R.id.toggle_forecast, togglePendingIntent)
      
      // Setup forecast list with proper adapter
      setupForecastList(context, views, appWidgetId)
      
      // Set location info with coordinates from app settings
      val locationText = getLocationDisplayText(context)
      views.setTextViewText(R.id.location_info, locationText)
      views.setViewVisibility(R.id.status_message, android.view.View.GONE)
      
      appWidgetManager.updateAppWidget(appWidgetId, views)
      
    } catch (e: Exception) {
      e.printStackTrace()
      // Show error state
      val views = RemoteViews(context.packageName, R.layout.hello_widget)
      views.setTextViewText(R.id.location_info, "Error loading widget")
      views.setViewVisibility(R.id.status_message, android.view.View.VISIBLE)
      views.setTextViewText(R.id.status_message, "Widget error")
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }
  
  private fun setupForecastList(context: Context, views: RemoteViews, appWidgetId: Int) {
    // Set up the intent that points to the RemoteViewsService
    val intent = Intent(context, ForecastWidgetService::class.java).apply {
      putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    
    // Set the RemoteViewsService on the ListView
    views.setRemoteAdapter(R.id.forecast_list, intent)
    
    // Set the empty view
    views.setEmptyView(R.id.forecast_list, R.id.status_message)
  }

  private fun getLocationDisplayText(context: Context): String {
    return try {
      // Check if using custom location first
      val customLocation = WeatherLocationManager.getCustomLocation(context)
      if (customLocation != null) {
        // Format custom location with 4 decimal places
        val lat = String.format("%.4f", customLocation.latitude)
        val lon = String.format("%.4f", customLocation.longitude)
        "${customLocation.city} ($lat, $lon)"
      } else {
        // Try to get cached location data
        val cachedLocation = WeatherLocationManager.getCachedLocationData(context)
        if (cachedLocation != null) {
          val lat = String.format("%.4f", cachedLocation.latitude)
          val lon = String.format("%.4f", cachedLocation.longitude)
          "${cachedLocation.city} ($lat, $lon)"
        } else {
          // Fallback to default message
          "Location not set"
        }
      }
    } catch (e: Exception) {
      "Weather Widget"
    }
  }

  private fun getWeatherIcon(code: Int): Int {
    return when (code) {
      0 -> R.drawable.weather_sunny // Clear sky
      1 -> R.drawable.weather_partly_cloudy // Mainly clear
      2 -> R.drawable.weather_partly_cloudy // Partly cloudy
      3 -> R.drawable.weather_partly_cloudy // Overcast
      45, 48 -> R.drawable.weather_fog // Fog and depositing rime fog
      51, 53, 55 -> R.drawable.weather_drizzle // Drizzle: Light, moderate, and dense intensity
      56, 57 -> R.drawable.weather_snowy_rainy // Freezing Drizzle: Light and dense intensity
      61, 63 -> R.drawable.weather_rainy // Rain: Slight and moderate intensity
      65 -> R.drawable.weather_pouring // Rain: Heavy intensity
      66, 67 -> R.drawable.weather_snowy_rainy // Freezing Rain: Light and heavy intensity
      71, 73 -> R.drawable.weather_snowy // Snow fall: Slight and moderate intensity
      75 -> R.drawable.weather_snowy_heavy // Snow fall: Heavy intensity
      77 -> R.drawable.weather_hail // Snow grains
      80, 81, 82 -> R.drawable.weather_pouring // Rain showers: Slight, moderate, and violent
      85, 86 -> R.drawable.weather_snowy_heavy // Snow showers: Slight and heavy
      95 -> R.drawable.weather_lightning_rainy // Thunderstorm: Slight or moderate
      96, 99 -> R.drawable.weather_lightning_rainy // Thunderstorm with slight and heavy hail
      else -> R.drawable.weather_sunny // Default to sunny
    }
  }

}