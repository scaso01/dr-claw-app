package com.scaso.drclawapp.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores auth tokens in EncryptedSharedPreferences backed by Android Keystore.
 * On first launch, migrates the BuildConfig token into secure storage.
 * Subsequent reads come from encrypted storage only.
 */
class SecureTokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "drclaw_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * Returns the gateway token from secure storage.
     * If not yet stored, writes [buildConfigFallback] to secure storage first.
     */
    fun getGatewayToken(buildConfigFallback: String): String {
        val stored = prefs.getString(KEY_GATEWAY_TOKEN, null)
        if (stored != null) return stored
        // First launch: migrate BuildConfig value into encrypted storage
        prefs.edit().putString(KEY_GATEWAY_TOKEN, buildConfigFallback).apply()
        return buildConfigFallback
    }

    /**
     * Returns the ntfy topic from secure storage, or null if not stored.
     */
    fun getNtfyTopic(): String? = prefs.getString(KEY_NTFY_TOPIC, null)

    fun setNtfyTopic(topic: String) {
        prefs.edit().putString(KEY_NTFY_TOPIC, topic).apply()
    }

    companion object {
        private const val KEY_GATEWAY_TOKEN = "gateway_token"
        private const val KEY_NTFY_TOPIC = "ntfy_topic"
    }
}
