package com.scaso.drclawapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.websocket.GatewayClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Debug-only BroadcastReceiver for ADB-driven configuration.
 *
 * Usage:
 *   # Set gateway URL
 *   adb shell am broadcast -a com.scaso.drclawapp.DEBUG_CONFIG --es gateway_url "ws://localhost:18794"
 *
 *   # Switch model (sends model.switch RPC)
 *   adb shell am broadcast -a com.scaso.drclawapp.DEBUG_CONFIG --es model "qwen3-coder-abliterated"
 *
 *   # Switch role (sends role.switch RPC)
 *   adb shell am broadcast -a com.scaso.drclawapp.DEBUG_CONFIG --es role "brain"
 *
 *   # Combine: set URL + switch model in one broadcast
 *   adb shell am broadcast -a com.scaso.drclawapp.DEBUG_CONFIG --es gateway_url "ws://localhost:18794" --es model "claude-sonnet-4-6"
 */
class DebugConfigReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DebugEntryPoint {
        fun gatewayClient(): GatewayClient
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppPreferences(context)

        // Handle gateway URL change
        val url = intent.getStringExtra("gateway_url")
        if (url != null) {
            Log.d("DebugConfig", "Setting gateway URL: $url")
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DebugEntryPoint::class.java,
            )
            val client = entryPoint.gatewayClient()
            CoroutineScope(Dispatchers.IO).launch {
                prefs.setGatewayUrl(url)
                // Trigger immediate reconnect with the new URL
                Log.d("DebugConfig", "Reconnecting gateway to: $url")
                client.reconnectWith(url)
            }
        }

        // Handle model switch via gateway RPC
        val model = intent.getStringExtra("model")
        if (model != null) {
            Log.d("DebugConfig", "Switching model: $model")
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DebugEntryPoint::class.java,
            )
            val client = entryPoint.gatewayClient()
            CoroutineScope(Dispatchers.IO).launch {
                val params = JsonObject(mapOf("model" to JsonPrimitive(model)))
                val res = client.sendGenericRequest("model.switch", params)
                Log.d("DebugConfig", "model.switch result: ok=${res.ok}")
            }
        }

        // Handle thinking level change (persisted to DataStore)
        intent.getStringExtra("thinking_level")?.let { level ->
            if (level in listOf("none", "low", "medium", "high")) {
                CoroutineScope(Dispatchers.IO).launch {
                    prefs.setThinkingLevel(level)
                }
                Log.d("DebugConfig", "Thinking level set to: $level")
            } else {
                Log.w("DebugConfig", "Invalid thinking level: $level (must be none/low/medium/high)")
            }
        }

        // Handle role switch via gateway RPC
        val role = intent.getStringExtra("role")
        if (role != null) {
            Log.d("DebugConfig", "Switching role: $role")
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DebugEntryPoint::class.java,
            )
            val client = entryPoint.gatewayClient()
            CoroutineScope(Dispatchers.IO).launch {
                val params = JsonObject(mapOf("name" to JsonPrimitive(role)))
                val res = client.sendGenericRequest("role.switch", params)
                Log.d("DebugConfig", "role.switch result: ok=${res.ok}")
            }
        }
    }
}
