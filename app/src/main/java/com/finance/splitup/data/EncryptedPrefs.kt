package com.finance.splitup.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object EncryptedPrefs {
    fun getPreferences(context: Context, name: String = "splitup_prefs"): SharedPreferences {
        return try {
            createEncryptedPrefs(context, name)
        } catch (e: Exception) {
            try {
                // Safely handle KeyStore corruption or key invalidation by clearing corrupted file and recreating encrypted storage
                context.deleteSharedPreferences(name)
                createEncryptedPrefs(context, name)
            } catch (_: Exception) {
                // If KeyStore remains unavailable, return a secure private preferences instance
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedPrefs(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
