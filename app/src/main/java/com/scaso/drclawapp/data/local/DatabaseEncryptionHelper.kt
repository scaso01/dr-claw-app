package com.scaso.drclawapp.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates and retrieves a database encryption key from Android Keystore.
 * The key is AES-256, hardware-backed where available, and never leaves
 * the Keystore — only the encrypted passphrase is stored in SharedPreferences.
 *
 * Flow:
 * 1. On first launch, generate a random 32-byte passphrase.
 * 2. Encrypt it with an AES-GCM key stored in Android Keystore.
 * 3. Store the encrypted passphrase + IV in SharedPreferences.
 * 4. On subsequent launches, decrypt the passphrase from SharedPreferences.
 */
object DatabaseEncryptionHelper {

    private const val KEYSTORE_ALIAS = "drclaw_db_encryption_key"
    private const val PREFS_NAME = "drclaw_db_encryption"
    private const val PREF_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val PREF_IV = "encryption_iv"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128

    /**
     * Returns the SQLCipher passphrase as a ByteArray.
     * Creates and persists one on first call.
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingEncrypted = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val existingIv = prefs.getString(PREF_IV, null)

        if (existingEncrypted != null && existingIv != null) {
            return decryptPassphrase(
                android.util.Base64.decode(existingEncrypted, android.util.Base64.NO_WRAP),
                android.util.Base64.decode(existingIv, android.util.Base64.NO_WRAP),
            )
        }

        // Generate a fresh random passphrase
        val passphrase = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

        // Encrypt with Keystore key and persist
        val keystoreKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)
        val encrypted = cipher.doFinal(passphrase)
        val iv = cipher.iv

        prefs.edit()
            .putString(PREF_ENCRYPTED_PASSPHRASE, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .putString(PREF_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            .apply()

        return passphrase
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun decryptPassphrase(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }
}
