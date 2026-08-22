package com.ambientcompanion

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ambientcompanion.data.location.LocationProvider
import com.ambientcompanion.data.preferences.AppPreferences
import com.ambientcompanion.data.weather.WeatherApi
import com.ambientcompanion.data.weather.WeatherRepository
import com.ambientcompanion.domain.repository.ContextRepository
import com.ambientcompanion.worker.ContextRefreshWorker
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class AmbientApplication : Application() {
    lateinit var preferences: AppPreferences
        private set
    lateinit var contextRepository: ContextRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WeatherApi::class.java)
        contextRepository = ContextRepository(preferences, LocationProvider(this), WeatherRepository(api, preferences))
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        val request = PeriodicWorkRequestBuilder<ContextRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ContextRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
