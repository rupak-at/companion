package com.ambientcompanion.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ambientcompanion.domain.context.AudioOutputType
import com.ambientcompanion.domain.context.DeviceContext
import com.ambientcompanion.domain.context.NetworkState
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DeviceContextSource(private val context: Context, private val weekendDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
    private val battery = context.getSystemService(BatteryManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val mutable = MutableStateFlow(snapshot())
    private val handler = Handler(Looper.getMainLooper())
    private val networkRefresh = Runnable(::refresh)
    val state: StateFlow<DeviceContext> = mutable

    private val batteryReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { mutable.value = snapshot(i) } }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = debounceNetwork()
        override fun onLost(network: Network) = debounceNetwork()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = debounceNetwork()
    }
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    fun start() {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        connectivity.registerDefaultNetworkCallback(networkCallback)
        audio.registerAudioDeviceCallback(audioCallback, null)
        refresh()
    }
    fun stop() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        runCatching { audio.unregisterAudioDeviceCallback(audioCallback) }
        handler.removeCallbacks(networkRefresh)
    }
    fun refresh() { mutable.value = snapshot() }
    private fun debounceNetwork() { handler.removeCallbacks(networkRefresh); handler.postDelayed(networkRefresh, 4_000) }

    private fun snapshot(intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))): DeviceContext {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val outputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrDefault(emptyArray())
        val output = outputs.map(::audioType).firstOrNull { it != AudioOutputType.SPEAKER && it != AudioOutputType.UNKNOWN } ?: AudioOutputType.SPEAKER
        val active = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val today = LocalDate.now().dayOfWeek
        return DeviceContext(
            batteryPercent = if (level < 0) battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100) else level * 100 / scale.coerceAtLeast(1),
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING,
            isBatteryFull = status == BatteryManager.BATTERY_STATUS_FULL,
            isPowerSaveMode = power.isPowerSaveMode,
            networkState = when {
                active == null -> NetworkState.UNKNOWN
                active.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.ONLINE
                else -> NetworkState.OFFLINE
            },
            isWifiConnected = active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            isHeadphonesConnected = output in setOf(AudioOutputType.WIRED, AudioOutputType.USB, AudioOutputType.BLUETOOTH),
            audioOutputType = output,
            dayOfWeek = today,
            isWeekend = today in weekendDays,
        )
    }

    private fun audioType(device: AudioDeviceInfo) = when (device.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioOutputType.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioOutputType.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER ->
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            ) AudioOutputType.BLUETOOTH else AudioOutputType.UNKNOWN
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputType.SPEAKER
        else -> AudioOutputType.UNKNOWN
    }
}
