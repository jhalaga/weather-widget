package com.jozefhalaga.weatherwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ForecastWidgetProvider7 : AppWidgetProvider() {
  companion object {
    const val ACTION_TOGGLE_FORECAST = "com.jozefhalaga.weatherwidget.TOGGLE_FORECAST"
  }

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    schedulePeriodicWidgetRefresh(context)
    for (appWidgetId in appWidgetIds) {
      updateAppWidget(context, appWidgetManager, appWidgetId)
    }
  }

  override fun onEnabled(context: Context) {
    schedulePeriodicWidgetRefresh(context)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_TOGGLE_FORECAST) {
      val appWidgetManager = AppWidgetManager.getInstance(context)
      val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
      if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
        val isCurrentlyHourly = WeatherLocationManager.isHourlyMode(context)
        WeatherLocationManager.saveForecastMode(context, !isCurrentlyHourly)
        updateAppWidget(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.forecast_list)
        enqueueImmediateRefresh(context)
      }
    }
  }

  private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    try {
      val views = RemoteViews(context.packageName, R.layout.widget_forecast)

      val isHourly = WeatherLocationManager.isHourlyMode(context)
      views.setTextViewText(R.id.toggle_forecast, if (isHourly) "Hourly" else "Daily")

      val toggleIntent = Intent(context, ForecastWidgetProvider7::class.java).apply {
        action = ACTION_TOGGLE_FORECAST
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
      }
      val togglePendingIntent = PendingIntent.getBroadcast(
        context, appWidgetId, toggleIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      views.setOnClickPendingIntent(R.id.toggle_forecast, togglePendingIntent)

      setupForecastList(context, views, appWidgetId)

      val locationText = getLocationDisplayText(context)
      views.setTextViewText(R.id.location_info, locationText)
      val updated = DateTimeFormatter.ofPattern("HH:mm").format(LocalDateTime.now())
      views.setTextViewText(R.id.as_of, "Updated $updated")

      val ts = WeatherService.getCacheTimestampMillis(context)
      val isStale = System.currentTimeMillis() - ts > 2 * 60 * 60 * 1000
      if (isStale) {
        views.setViewVisibility(R.id.status_message, View.VISIBLE)
        views.setTextViewText(R.id.status_message, "Data may be stale")
      } else {
        views.setViewVisibility(R.id.status_message, View.GONE)
      }

      appWidgetManager.updateAppWidget(appWidgetId, views)
      appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.forecast_list)
    } catch (e: Exception) {
      val views = RemoteViews(context.packageName, R.layout.widget_forecast)
      views.setTextViewText(R.id.location_info, "Error loading widget")
      views.setViewVisibility(R.id.status_message, View.VISIBLE)
      views.setTextViewText(R.id.status_message, "Widget error")
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }

  private fun setupForecastList(context: Context, views: RemoteViews, appWidgetId: Int) {
    val intent = Intent(context, ForecastWidgetService::class.java).apply {
      putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      putExtra("COLUMNS", 7)
      data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    views.setRemoteAdapter(R.id.forecast_list, intent)
    views.setEmptyView(R.id.forecast_list, R.id.status_message)
  }

  private fun getLocationDisplayText(context: Context): String {
    return try {
      val customLocation = WeatherLocationManager.getCustomLocation(context)
      if (customLocation != null) {
        val lat = String.format("%.4f", customLocation.latitude)
        val lon = String.format("%.4f", customLocation.longitude)
        "${customLocation.city} ($lat, $lon)"
      } else {
        val cachedLocation = WeatherLocationManager.getCachedLocationData(context)
        if (cachedLocation != null) {
          val lat = String.format("%.4f", cachedLocation.latitude)
          val lon = String.format("%.4f", cachedLocation.longitude)
          "${cachedLocation.city} ($lat, $lon)"
        } else {
          "Location not set"
        }
      }
    } catch (e: Exception) {
      "Weather Widget"
    }
  }
}

