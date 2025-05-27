package com.jozefhalaga.weatherwidget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.appwidget.AppWidgetManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate

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
        val iconResource: Int
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
        
        if (isHourly) {
            populateHourlyDemo()
        } else {
            populateDailyDemo()
        }
    }

    private fun populateHourlyDemo() {
        val now = LocalDateTime.now()
        // 48 hours of data (6 rows × 8 hours)
        val demoTemps = listOf(
            22, 21, 20, 19, 18, 17, 16, 17, // Row 1: Hours 0-7
            18, 20, 23, 25, 27, 26, 24, 23, // Row 2: Hours 8-15  
            21, 19, 18, 17, 16, 15, 14, 15, // Row 3: Hours 16-23
            16, 18, 21, 24, 26, 28, 25, 22, // Row 4: Hours 24-31
            20, 18, 17, 16, 15, 14, 13, 14, // Row 5: Hours 32-39
            15, 17, 20, 23, 25, 27, 24, 21  // Row 6: Hours 40-47
        )
        val demoWeatherCodes = listOf(
            0, 1, 2, 0, 1, 3, 2, 0, // Row 1
            1, 0, 0, 1, 2, 1, 0, 1, // Row 2
            2, 0, 1, 3, 2, 0, 1, 0, // Row 3
            0, 1, 0, 1, 2, 0, 1, 2, // Row 4
            1, 3, 2, 0, 1, 0, 2, 1, // Row 5
            0, 1, 0, 1, 2, 0, 1, 0  // Row 6
        )
        
        // Create 6 rows, each with 8 hours
        for (row in 0 until 6) {
            val hoursInRow = mutableListOf<HourData>()
            
            for (hourInRow in 0 until 8) {
                val hourIndex = row * 8 + hourInRow
                val time = now.plusHours(hourIndex.toLong())
                val temp = demoTemps[hourIndex]
                val weatherCode = demoWeatherCodes[hourIndex]
                
                val dateStr = time.format(DateTimeFormatter.ofPattern("dd/MM"))
                val timeStr = if (hourIndex == 0) "Now" else time.format(DateTimeFormatter.ofPattern("HH:mm"))
                val tempStr = "${temp}°"
                
                hoursInRow.add(HourData(
                    date = dateStr,
                    time = timeStr,
                    temp = tempStr,
                    iconResource = getWeatherIcon(weatherCode)
                ))
            }
            
            rowData.add(RowData(isHourly = true, hours = hoursInRow))
        }
    }

    private fun populateDailyDemo() {
        val today = LocalDate.now()
        val demoMaxTemps = listOf(25, 23, 27, 24, 22, 26, 28, 25, 23, 21, 24, 26, 27, 25, 23, 22)
        val demoMinTemps = listOf(15, 13, 17, 14, 12, 16, 18, 15, 13, 11, 14, 16, 17, 15, 13, 12)
        val demoWeatherCodes = listOf(0, 1, 61, 0, 2, 1, 0, 3, 51, 0, 1, 0, 2, 1, 0, 3)
        
        // Create 2 rows, each with 8 days
        for (row in 0 until 2) {
            val daysInRow = mutableListOf<DayData>()
            
            for (dayInRow in 0 until 8) {
                val dayIndex = row * 8 + dayInRow
                if (dayIndex < 16) {
                    val date = today.plusDays(dayIndex.toLong())
                    val maxTemp = demoMaxTemps[dayIndex]
                    val minTemp = demoMinTemps[dayIndex]
                    val weatherCode = demoWeatherCodes[dayIndex]
                    
                    val dateStr = date.format(DateTimeFormatter.ofPattern("dd/MM"))
                    val dayStr = if (dayIndex == 0) "Today" else date.format(DateTimeFormatter.ofPattern("EEE"))
                    val tempStr = "${maxTemp}°"
                    val minTempStr = "${minTemp}°"
                    
                    daysInRow.add(DayData(
                        date = dateStr,
                        day = dayStr,
                        temp = tempStr,
                        minTemp = minTempStr,
                        iconResource = getWeatherIcon(weatherCode)
                    ))
                }
            }
            
            rowData.add(RowData(isHourly = false, days = daysInRow))
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

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rowData.size) {
            return RemoteViews(context.packageName, R.layout.forecast_item)
        }
        
        val row = rowData[position]
        val views = RemoteViews(context.packageName, R.layout.forecast_item)
        
        if (row.isHourly && row.hours != null) {
            populateHourlyRow(views, row.hours)
        } else if (!row.isHourly && row.days != null) {
            populateDailyRow(views, row.days)
        }
        
        return views
    }
    
    private fun populateHourlyRow(views: RemoteViews, hours: List<HourData>) {
        val hourIds = listOf(
            listOf(R.id.hour1_date, R.id.hour1_time, R.id.hour1_icon, R.id.hour1_temp),
            listOf(R.id.hour2_date, R.id.hour2_time, R.id.hour2_icon, R.id.hour2_temp),
            listOf(R.id.hour3_date, R.id.hour3_time, R.id.hour3_icon, R.id.hour3_temp),
            listOf(R.id.hour4_date, R.id.hour4_time, R.id.hour4_icon, R.id.hour4_temp),
            listOf(R.id.hour5_date, R.id.hour5_time, R.id.hour5_icon, R.id.hour5_temp),
            listOf(R.id.hour6_date, R.id.hour6_time, R.id.hour6_icon, R.id.hour6_temp),
            listOf(R.id.hour7_date, R.id.hour7_time, R.id.hour7_icon, R.id.hour7_temp),
            listOf(R.id.hour8_date, R.id.hour8_time, R.id.hour8_icon, R.id.hour8_temp)
        )
        
        for (i in hourIds.indices) {
            if (i < hours.size) {
                val hour = hours[i]
                val ids = hourIds[i]
                val (dateId, timeId, iconId, tempId) = ids
                
                views.setTextViewText(dateId, hour.date)
                views.setTextViewText(timeId, hour.time)
                views.setImageViewResource(iconId, hour.iconResource)
                views.setTextViewText(tempId, hour.temp)
            } else {
                // Hide unused hour slots
                val ids = hourIds[i]
                val (dateId, timeId, iconId, tempId) = ids
                views.setTextViewText(dateId, "")
                views.setTextViewText(timeId, "")
                views.setTextViewText(tempId, "")
            }
        }
    }
    
    private fun populateDailyRow(views: RemoteViews, days: List<DayData>) {
        val dayIds = listOf(
            listOf(R.id.hour1_date, R.id.hour1_time, R.id.hour1_icon, R.id.hour1_temp),
            listOf(R.id.hour2_date, R.id.hour2_time, R.id.hour2_icon, R.id.hour2_temp),
            listOf(R.id.hour3_date, R.id.hour3_time, R.id.hour3_icon, R.id.hour3_temp),
            listOf(R.id.hour4_date, R.id.hour4_time, R.id.hour4_icon, R.id.hour4_temp),
            listOf(R.id.hour5_date, R.id.hour5_time, R.id.hour5_icon, R.id.hour5_temp),
            listOf(R.id.hour6_date, R.id.hour6_time, R.id.hour6_icon, R.id.hour6_temp),
            listOf(R.id.hour7_date, R.id.hour7_time, R.id.hour7_icon, R.id.hour7_temp),
            listOf(R.id.hour8_date, R.id.hour8_time, R.id.hour8_icon, R.id.hour8_temp)
        )
        
        for (i in dayIds.indices) {
            if (i < days.size) {
                val day = days[i]
                val ids = dayIds[i]
                val (dateId, timeId, iconId, tempId) = ids
                
                views.setTextViewText(dateId, day.date)
                views.setTextViewText(timeId, day.day)
                views.setImageViewResource(iconId, day.iconResource)
                views.setTextViewText(tempId, "${day.temp}/${day.minTemp}")
            } else {
                // Hide unused day slots
                val ids = dayIds[i]
                val (dateId, timeId, iconId, tempId) = ids
                views.setTextViewText(dateId, "")
                views.setTextViewText(timeId, "")
                views.setTextViewText(tempId, "")
            }
        }
    }

    override fun getCount(): Int = rowData.size

    override fun getLoadingView(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.forecast_item)
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