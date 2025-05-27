package com.jozefhalaga.weatherwidget

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset

data class WeatherForecast(
    val hourlyTemperatures: List<Int>,
    val hourlyWeatherCodes: List<Int>,
    val dailyMaxTemperatures: List<Int>,
    val dailyMinTemperatures: List<Int>,
    val dailyWeatherCodes: List<Int>
)

object WeatherService {
    
    suspend fun fetchWeatherForecast(latitude: Double, longitude: Double): WeatherForecast? = withContext(Dispatchers.IO) {
        try {
            // Open-Meteo API URL for forecast data
            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$latitude&longitude=$longitude" +
                    "&hourly=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                    "&temperature_unit=celsius" +
                    "&windspeed_unit=kmh" +
                    "&precipitation_unit=mm" +
                    "&timezone=auto" +
                    "&forecast_days=16"
            
            val response = URL(url).openConnection().run {
                setRequestProperty("User-Agent", "WeatherWidget/1.0")
                connectTimeout = 10000
                readTimeout = 10000
                inputStream.bufferedReader().readText()
            }
            
            val json = JSONObject(response)
            
            // Parse hourly data
            val hourlyData = json.getJSONObject("hourly")
            val hourlyTemps = hourlyData.getJSONArray("temperature_2m")
            val hourlyWeatherCodes = hourlyData.getJSONArray("weather_code")
            
            val hourlyTemperatures = mutableListOf<Int>()
            val hourlyWeatherCodesList = mutableListOf<Int>()
            
            for (i in 0 until hourlyTemps.length()) {
                val temp = hourlyTemps.optDouble(i, 0.0)
                val code = hourlyWeatherCodes.optInt(i, 0)
                hourlyTemperatures.add(temp.toInt())
                hourlyWeatherCodesList.add(code)
            }
            
            // Parse daily data
            val dailyData = json.getJSONObject("daily")
            val dailyMaxTemps = dailyData.getJSONArray("temperature_2m_max")
            val dailyMinTemps = dailyData.getJSONArray("temperature_2m_min")
            val dailyWeatherCodes = dailyData.getJSONArray("weather_code")
            
            val dailyMaxTemperatures = mutableListOf<Int>()
            val dailyMinTemperatures = mutableListOf<Int>()
            val dailyWeatherCodesList = mutableListOf<Int>()
            
            for (i in 0 until dailyMaxTemps.length()) {
                val maxTemp = dailyMaxTemps.optDouble(i, 0.0)
                val minTemp = dailyMinTemps.optDouble(i, 0.0)
                val code = dailyWeatherCodes.optInt(i, 0)
                dailyMaxTemperatures.add(maxTemp.toInt())
                dailyMinTemperatures.add(minTemp.toInt())
                dailyWeatherCodesList.add(code)
            }
            
            WeatherForecast(
                hourlyTemperatures = hourlyTemperatures,
                hourlyWeatherCodes = hourlyWeatherCodesList,
                dailyMaxTemperatures = dailyMaxTemperatures,
                dailyMinTemperatures = dailyMinTemperatures,
                dailyWeatherCodes = dailyWeatherCodesList
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Cache weather data
    fun cacheWeatherData(context: Context, forecast: WeatherForecast) {
        val prefs = context.getSharedPreferences("weather_forecast_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            // Cache hourly data (convert lists to comma-separated strings)
            putString("hourly_temps", forecast.hourlyTemperatures.joinToString(","))
            putString("hourly_codes", forecast.hourlyWeatherCodes.joinToString(","))
            
            // Cache daily data
            putString("daily_max_temps", forecast.dailyMaxTemperatures.joinToString(","))
            putString("daily_min_temps", forecast.dailyMinTemperatures.joinToString(","))
            putString("daily_codes", forecast.dailyWeatherCodes.joinToString(","))
            
            // Cache timestamp
            putLong("cache_timestamp", System.currentTimeMillis())
            apply()
        }
    }
    
    fun getCachedWeatherData(context: Context): WeatherForecast? {
        val prefs = context.getSharedPreferences("weather_forecast_cache", Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("cache_timestamp", 0)
        
        // Check if cache is not too old (2 hours)
        if (System.currentTimeMillis() - timestamp > 2 * 60 * 60 * 1000) {
            return null
        }
        
        try {
            val hourlyTempsStr = prefs.getString("hourly_temps", null) ?: return null
            val hourlyCodesStr = prefs.getString("hourly_codes", null) ?: return null
            val dailyMaxTempsStr = prefs.getString("daily_max_temps", null) ?: return null
            val dailyMinTempsStr = prefs.getString("daily_min_temps", null) ?: return null
            val dailyCodesStr = prefs.getString("daily_codes", null) ?: return null
            
            val hourlyTemps = hourlyTempsStr.split(",").map { it.toInt() }
            val hourlyCodes = hourlyCodesStr.split(",").map { it.toInt() }
            val dailyMaxTemps = dailyMaxTempsStr.split(",").map { it.toInt() }
            val dailyMinTemps = dailyMinTempsStr.split(",").map { it.toInt() }
            val dailyCodes = dailyCodesStr.split(",").map { it.toInt() }
            
            return WeatherForecast(
                hourlyTemperatures = hourlyTemps,
                hourlyWeatherCodes = hourlyCodes,
                dailyMaxTemperatures = dailyMaxTemps,
                dailyMinTemperatures = dailyMinTemps,
                dailyWeatherCodes = dailyCodes
            )
        } catch (e: Exception) {
            return null
        }
    }
} 