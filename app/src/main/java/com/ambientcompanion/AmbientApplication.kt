package com.ambientcompanion

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ambientcompanion.data.location.LocationProvider
import com.ambientcompanion.data.device.DeviceContextSource
import com.ambientcompanion.data.preferences.AppPreferences
import com.ambientcompanion.data.weather.WeatherApi
import com.ambientcompanion.data.weather.WeatherRepository
import com.ambientcompanion.domain.repository.ContextRepository
import com.ambientcompanion.worker.ContextRefreshWorker
import com.ambientcompanion.data.preferences.UserSettings
import com.ambientcompanion.data.profile.AppCategoryResolver
import com.ambientcompanion.data.profile.AppProfileRepository
import com.ambientcompanion.data.screen.ScreenContextSource
import com.ambientcompanion.data.wellbeing.WellbeingRepository
import com.ambientcompanion.domain.context.ResourceMode
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class AmbientApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var preferences: AppPreferences
        private set
    lateinit var contextRepository: ContextRepository
        private set
    lateinit var deviceContextSource: DeviceContextSource
        private set
    lateinit var appProfileRepository: AppProfileRepository
        private set
    lateinit var screenContextSource: ScreenContextSource
        private set
    lateinit var wellbeingRepository: WellbeingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        appProfileRepository = AppProfileRepository(this)
        screenContextSource = ScreenContextSource(AppCategoryResolver(), appProfileRepository)
        wellbeingRepository = WellbeingRepository(this)
        deviceContextSource = DeviceContextSource(this).also { it.start() }
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
        observeResourcePolicy()
    }

    private fun observeResourcePolicy() {
        applicationScope.launch {
            preferences.settings.combine(deviceContextSource.state) { settings, device ->
                settings to device.isPowerSaveMode
            }.distinctUntilChanged { old, new ->
                old.first.resourceMode == new.first.resourceMode &&
                    old.first.weatherEnabled == new.first.weatherEnabled && old.second == new.second
            }.collect { (settings, powerSaver) -> scheduleRefresh(settings, powerSaver) }
        }
    }

    private fun scheduleRefresh(settings: UserSettings, powerSaver: Boolean) {
        if (!settings.weatherEnabled) {
            WorkManager.getInstance(this).cancelUniqueWork(ContextRefreshWorker.WORK_NAME)
            return
        }
        val minutes = when {
            powerSaver -> 180L
            settings.resourceMode == ResourceMode.MINIMAL -> 360L
            settings.resourceMode == ResourceMode.BATTERY_SAVER -> 180L
            else -> 75L
        }
        val request = PeriodicWorkRequestBuilder<ContextRefreshWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ContextRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
