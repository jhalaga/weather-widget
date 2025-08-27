package com.jozefhalaga.weatherwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val manager = AppWidgetManager.getInstance(ctx)
            val comp8 = ComponentName(ctx, ForecastWidgetProvider8::class.java)
            val comp7 = ComponentName(ctx, ForecastWidgetProvider7::class.java)
            val comp6 = ComponentName(ctx, ForecastWidgetProvider6::class.java)
            val ids8 = manager.getAppWidgetIds(comp8)
            val ids7 = manager.getAppWidgetIds(comp7)
            val ids6 = manager.getAppWidgetIds(comp6)
            val allIds = (ids8.toList() + ids7.toList() + ids6.toList()).toIntArray()

            if (allIds.isEmpty()) return Result.success()

            // Resolve background-appropriate location
            val location = WeatherLocationResolver(ctx).resolveForBackground()

            // Fetch weather (use cache when valid)
            val cached = WeatherService.getCachedWeatherData(ctx)
            val data = cached ?: WeatherService.fetchWeatherForecast(location.latitude, location.longitude)?.also {
                WeatherService.cacheWeatherData(ctx, it)
            }

            if (data == null) return Result.retry()

            // Push updates to all widgets
            allIds.forEach { id ->
                // Notify collection changes first
                manager.notifyAppWidgetViewDataChanged(id, R.id.forecast_list)
                // Trigger provider update to rebuild RemoteViews and header
                // Provider will read cached data via RemoteViewsFactory
                // Sending a standard update broadcast to reuse existing update logic
                // Alternatively, we could construct RemoteViews directly here
            }

            // Send a broadcast to trigger onUpdate for all ids
            val updateIntent8 = android.content.Intent(ctx, ForecastWidgetProvider8::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids8)
            }
            val updateIntent7 = android.content.Intent(ctx, ForecastWidgetProvider7::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids7)
            }
            val updateIntent6 = android.content.Intent(ctx, ForecastWidgetProvider6::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids6)
            }
            if (ids8.isNotEmpty()) ctx.sendBroadcast(updateIntent8)
            if (ids7.isNotEmpty()) ctx.sendBroadcast(updateIntent7)
            if (ids6.isNotEmpty()) ctx.sendBroadcast(updateIntent6)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

fun schedulePeriodicWidgetRefresh(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val work = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "weather_widget_periodic_refresh",
        ExistingPeriodicWorkPolicy.UPDATE,
        work
    )
}

fun enqueueImmediateRefresh(context: Context) {
    val oneShot = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    WorkManager.getInstance(context).enqueue(oneShot)
}


