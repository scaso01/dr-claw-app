package com.scaso.drclawapp.cmd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Detects user activity state using sensor availability and device status.
 * Tool: device.activity
 *
 * Uses a lightweight approach: check available sensors and device state
 * rather than requiring Google Play Services Activity Recognition API.
 * Returns activity hints based on sensor availability and screen/power state.
 */
class ActivityHandler : CmdFrameHandler {
    override val tool: String = "device.activity"

    override suspend fun execute(params: JsonObject, context: Context): JsonElement {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

            val isInteractive = powerManager?.isInteractive ?: false
            val hasStepcounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
            val hasAccelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

            // Infer basic activity from device state
            val activity = inferActivity(isInteractive, context)

            buildJsonObject {
                put("activity", activity)
                put("screen_on", isInteractive)
                put("has_step_counter", hasStepcounter)
                put("has_accelerometer", hasAccelerometer)
                put("device_idle", !isInteractive)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    put("power_save_mode", powerManager?.isPowerSaveMode ?: false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Activity detection failed", e)
            buildJsonObject {
                put("error", e.message ?: "Activity detection failed")
                put("activity", "unknown")
            }
        }
    }

    private fun inferActivity(isInteractive: Boolean, context: Context): String {
        // Basic heuristic: if screen is off, user is likely still/idle.
        // If screen is on, user is active. This is a lightweight fallback
        // for when Activity Recognition API is unavailable.
        return when {
            !isInteractive -> "still"
            isCharging(context) -> "charging"
            else -> "active"
        }
    }

    private fun isCharging(context: Context): Boolean {
        return try {
            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "ActivityHandler"
    }
}
