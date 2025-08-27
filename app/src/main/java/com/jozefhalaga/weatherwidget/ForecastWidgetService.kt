package com.jozefhalaga.weatherwidget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.view.View
import android.widget.RemoteViewsService
import android.appwidget.AppWidgetManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class ForecastWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ForecastRemoteViewsFactory(this.applicationContext, intent)
    }
}

class ForecastRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var rowData = mutableListOf<RowData>()
    private val numColumns: Int = intent.getIntExtra("COLUMNS", 8).let { c -> if (c in 6..8) c else 8 }
    private val rowLayoutResId: Int = when (numColumns) { 6 -> R.layout.forecast_item_6; 7 -> R.layout.forecast_item_7; else -> R.layout.forecast_item_8 }

    data class HourData(
        val date: String,
        val time: String,
        val temp: String,
        val iconResource: Int
    )
    
    data class DayData(
        val date: String,
        val day: String,
        val temp: String,
        val minTemp: String,
        val iconResource: Int,
        val isWeekend: Boolean
    )

    data class RowData(
        val isHourly: Boolean,
        val hours: List<HourData>? = null,
        val days: List<DayData>? = null
    )

    override fun onCreate() {
        // Initialize with demo data
        refreshData()
    }

    override fun onDataSetChanged() {
        // Refresh data when requested
        refreshData()
    }

    private fun refreshData() {
        rowData.clear()
        
        val isHourly = WeatherLocationManager.isHourlyMode(context)
        
        try {
            // Get location data
            val location = WeatherLocationManager.getCustomLocation(context) 
                ?: WeatherLocationManager.getCachedLocationData(context)
            
            if (location != null) {
                // Try to get cached weather data for this location first
                var forecast = WeatherService.getCachedWeatherDataForLocation(context, location.latitude, location.longitude)
                
                // If no cached data or cache is old, fetch fresh data
                if (forecast == null) {
                    runBlocking {
                        forecast = WeatherService.fetchWeatherForecast(location.latitude, location.longitude)
                        forecast?.let { WeatherService.cacheWeatherDataForLocation(context, location.latitude, location.longitude, it) }
                    }
                }
                
                if (forecast != null) {
                    if (isHourly) {
                        populateHourlyForecast(forecast)
                    } else {
                        populateDailyForecast(forecast)
                    }
                } else {
                    // No forecast data available - show loading/error state
                    populateErrorState("Unable to load weather data")
                }
            } else {
                // No location available
                populateErrorState("Location not set")
            }
        } catch (e: Exception) {
            // Error occurred
            populateErrorState("Error loading weather data")
        }
    }

    private fun populateHourlyForecast(forecast: WeatherForecast) {
        val now = LocalDateTime.now()
        val currentHour = now.hour
        val itemsPerRow = numColumns
        val rows = 48 / itemsPerRow
        
        for (row in 0 until rows) {
            val hoursInRow = mutableListOf<HourData>()
            
            for (hourInRow in 0 until itemsPerRow) {
                val displayIndex = row * itemsPerRow + hourInRow
                // Skip past hours and get data from the server array
                val serverDataIndex = currentHour + displayIndex
                
                // Make sure we don't go beyond our forecast data
                if (serverDataIndex < forecast.hourlyTemperatures.size) {
                    val temp = forecast.hourlyTemperatures[serverDataIndex]
                    val weatherCode = forecast.hourlyWeatherCodes[serverDataIndex]
                    
                    // Calculate the actual time this forecast represents
                    val forecastTime = now.withMinute(0).withSecond(0).withNano(0).plusHours(displayIndex.toLong())
                    
                    val dateStr = forecastTime.format(DateTimeFormatter.ofPattern("dd/MM"))
                    val timeStr = if (displayIndex == 0) "Now" else forecastTime.format(DateTimeFormatter.ofPattern("HH:00"))
                    val tempStr = "${temp}°"
                    
                    hoursInRow.add(HourData(
                        date = dateStr,
                        time = timeStr,
                        temp = tempStr,
                        iconResource = getWeatherIcon(weatherCode)
                    ))
                }
            }
            
            if (hoursInRow.isNotEmpty()) {
                rowData.add(RowData(isHourly = true, hours = hoursInRow))
            }
        }
    }

    private fun populateDailyForecast(forecast: WeatherForecast) {
        val today = LocalDate.now()
        val itemsPerRow = numColumns
        val rows = 16 / itemsPerRow
        
        for (row in 0 until rows) {
            val daysInRow = mutableListOf<DayData>()
            
            for (dayInRow in 0 until itemsPerRow) {
                val dayIndex = row * itemsPerRow + dayInRow
                if (dayIndex < forecast.dailyMaxTemperatures.size) {
                    val date = today.plusDays(dayIndex.toLong())
                    val maxTemp = forecast.dailyMaxTemperatures[dayIndex]
                    val minTemp = forecast.dailyMinTemperatures[dayIndex]
                    val weatherCode = forecast.dailyWeatherCodes[dayIndex]
                    
                    val dateStr = date.format(DateTimeFormatter.ofPattern("dd/MM"))
                    val dayStr = if (dayIndex == 0) "Today" else date.format(DateTimeFormatter.ofPattern("EEE"))
                    val tempStr = "${maxTemp}°"
                    val minTempStr = "${minTemp}°"
                    val isWeekend = date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7
                    
                    daysInRow.add(DayData(
                        date = dateStr,
                        day = dayStr,
                        temp = tempStr,
                        minTemp = minTempStr,
                        iconResource = getWeatherIcon(weatherCode),
                        isWeekend = isWeekend
                    ))
                }
            }
            
            if (daysInRow.isNotEmpty()) {
                rowData.add(RowData(isHourly = false, days = daysInRow))
            }
        }
    }

    private fun populateErrorState(errorMessage: String) {
        // Create a single row showing the error message
        val errorRow = mutableListOf<HourData>()
        errorRow.add(HourData(
            date = "",
            time = "Error",
            temp = errorMessage,
            iconResource = R.drawable.weather_sunny // Default icon
        ))
        rowData.add(RowData(isHourly = true, hours = errorRow))
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

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rowData.size) {
            return RemoteViews(context.packageName, rowLayoutResId)
        }
        
        val row = rowData[position]
        val views = RemoteViews(context.packageName, rowLayoutResId)
        
        if (row.isHourly && row.hours != null) {
            populateHourlyRow(views, row.hours)
        } else if (!row.isHourly && row.days != null) {
            populateDailyRow(views, row.days)
        }
        
        return views
    }
    
    private fun populateHourlyRow(views: RemoteViews, hours: List<HourData>) {
        val hourIds = listOf(
            listOf(R.id.hour1_date, R.id.hour1_time, R.id.hour1_icon, R.id.hour1_temp, R.id.hour1_container),
            listOf(R.id.hour2_date, R.id.hour2_time, R.id.hour2_icon, R.id.hour2_temp, R.id.hour2_container),
            listOf(R.id.hour3_date, R.id.hour3_time, R.id.hour3_icon, R.id.hour3_temp, R.id.hour3_container),
            listOf(R.id.hour4_date, R.id.hour4_time, R.id.hour4_icon, R.id.hour4_temp, R.id.hour4_container),
            listOf(R.id.hour5_date, R.id.hour5_time, R.id.hour5_icon, R.id.hour5_temp, R.id.hour5_container),
            listOf(R.id.hour6_date, R.id.hour6_time, R.id.hour6_icon, R.id.hour6_temp, R.id.hour6_container),
            listOf(R.id.hour7_date, R.id.hour7_time, R.id.hour7_icon, R.id.hour7_temp, R.id.hour7_container),
            listOf(R.id.hour8_date, R.id.hour8_time, R.id.hour8_icon, R.id.hour8_temp, R.id.hour8_container)
        ).take(numColumns)
        
        val scale = WeatherLocationManager.getWidgetFontScale(context)
        val dateSize = (8 * scale).toFloat()
        val timeSize = (10 * scale).toFloat()
        val tempSize = (11 * scale).toFloat()

        for (i in hourIds.indices) {
            if (i < hours.size) {
                val hour = hours[i]
                val ids = hourIds[i]
                val dateId = ids[0]
                val timeId = ids[1]
                val iconId = ids[2]
                val tempId = ids[3]
                val containerId = ids[4]
                
                views.setTextViewText(dateId, hour.date)
                views.setTextViewTextSize(dateId, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize)
                views.setTextViewText(timeId, hour.time)
                views.setTextViewTextSize(timeId, android.util.TypedValue.COMPLEX_UNIT_SP, timeSize)
                views.setImageViewResource(iconId, hour.iconResource)
                views.setTextViewText(tempId, hour.temp)
                views.setTextViewTextSize(tempId, android.util.TypedValue.COMPLEX_UNIT_SP, tempSize)
                views.setViewVisibility(containerId, View.VISIBLE)
            } else {
                val ids = hourIds[i]
                val dateId = ids[0]
                val timeId = ids[1]
                val tempId = ids[3]
                val containerId = ids[4]
                views.setTextViewText(dateId, "")
                views.setTextViewText(timeId, "")
                views.setTextViewText(tempId, "")
                views.setViewVisibility(containerId, View.GONE)
            }
        }
    }
    
    private fun populateDailyRow(views: RemoteViews, days: List<DayData>) {
        val dayIds = listOf(
            listOf(R.id.hour1_date, R.id.hour1_time, R.id.hour1_icon, R.id.hour1_temp, R.id.hour1_container),
            listOf(R.id.hour2_date, R.id.hour2_time, R.id.hour2_icon, R.id.hour2_temp, R.id.hour2_container),
            listOf(R.id.hour3_date, R.id.hour3_time, R.id.hour3_icon, R.id.hour3_temp, R.id.hour3_container),
            listOf(R.id.hour4_date, R.id.hour4_time, R.id.hour4_icon, R.id.hour4_temp, R.id.hour4_container),
            listOf(R.id.hour5_date, R.id.hour5_time, R.id.hour5_icon, R.id.hour5_temp, R.id.hour5_container),
            listOf(R.id.hour6_date, R.id.hour6_time, R.id.hour6_icon, R.id.hour6_temp, R.id.hour6_container),
            listOf(R.id.hour7_date, R.id.hour7_time, R.id.hour7_icon, R.id.hour7_temp, R.id.hour7_container),
            listOf(R.id.hour8_date, R.id.hour8_time, R.id.hour8_icon, R.id.hour8_temp, R.id.hour8_container)
        ).take(numColumns)
        val scale = WeatherLocationManager.getWidgetFontScale(context)
        val dateSize = (8 * scale).toFloat()
        val daySize = (10 * scale).toFloat()
        val tempSize = (11 * scale).toFloat()
        
        for (i in dayIds.indices) {
            if (i < days.size) {
                val day = days[i]
                val ids = dayIds[i]
                val dateId = ids[0]
                val timeId = ids[1]
                val iconId = ids[2]
                val tempId = ids[3]
                val containerId = ids[4]
                
                views.setTextViewText(dateId, day.date)
                views.setTextViewTextSize(dateId, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize)
                views.setTextViewText(timeId, day.day)
                views.setTextViewTextSize(timeId, android.util.TypedValue.COMPLEX_UNIT_SP, daySize)
                views.setImageViewResource(iconId, day.iconResource)
                views.setTextViewText(tempId, "${day.temp}/${day.minTemp}")
                views.setTextViewTextSize(tempId, android.util.TypedValue.COMPLEX_UNIT_SP, tempSize)
                if (day.isWeekend) {
                    views.setInt(containerId, "setBackgroundResource", R.drawable.weekend_bg)
                } else {
                    // Remove background
                    views.setInt(containerId, "setBackgroundResource", 0)
                }
                views.setViewVisibility(containerId, View.VISIBLE)
            } else {
                val ids = dayIds[i]
                val dateId = ids[0]
                val timeId = ids[1]
                val tempId = ids[3]
                val containerId = ids[4]
                views.setTextViewText(dateId, "")
                views.setTextViewText(timeId, "")
                views.setTextViewText(tempId, "")
                views.setViewVisibility(containerId, View.GONE)
            }
        }
    }

    override fun getCount(): Int = rowData.size

    override fun getLoadingView(): RemoteViews {
        val views = RemoteViews(context.packageName, rowLayoutResId)
        views.setTextViewText(R.id.hour1_temp, "...")
        return views
    }

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        rowData.clear()
    }
} 