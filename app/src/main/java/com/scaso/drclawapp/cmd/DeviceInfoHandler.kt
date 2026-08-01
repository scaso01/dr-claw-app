package com.scaso.drclawapp.cmd

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Returns device info: battery, storage, connectivity, model, Android version.
 * Tool: device.info
 */
class DeviceInfoHandler : CmdFrameHandler {
    override val tool: String = "device.info"

    override suspend fun execute(params: JsonObject, context: Context): JsonElement {
        return buildJsonObject {
            put("battery", getBatteryLevel(context))
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android_version", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("connectivity", getConnectivity(context))

            val stat = StatFs(Environment.getDataDirectory().path)
            val freeBytes = stat.availableBytes
            val totalBytes = stat.totalBytes
            put("storage_free_mb", freeBytes / (1024 * 1024))
            put("storage_total_mb", totalBytes / (1024 * 1024))
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (scale > 0) (level * 100) / scale else -1
    }

    private fun getConnectivity(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "unknown"
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }
}
