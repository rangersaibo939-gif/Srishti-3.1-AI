package com.opendroid.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.opendroid.app.core.logging.RedactedLogger

/**
 * Hardware-backed Android Keystore Secret Provider
 * Prevents plaintext credential storage on disk.
 */
object KeystoreSecretProvider {

    private const val PREFS_NAME = "opendroid_secure_keystore"
    private const val TAG = "KeystoreProvider"
    private var encryptedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            RedactedLogger.i(TAG, "Hardware Keystore MasterKey initialized successfully.")
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to initialize Hardware Keystore MasterKey", e)
        }
    }

    fun putSecureSecret(key: String, value: String) {
        encryptedPrefs?.edit()?.putString(key, value)?.apply()
    }

    fun getSecureSecret(key: String): String? {
        return encryptedPrefs?.getString(key, null)
    }

    fun clear() {
        encryptedPrefs?.edit()?.clear()?.apply()
    }
}
