package com.medpriority.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences

    @Volatile
    private var inMemoryToken: String? = null

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        inMemoryToken = sharedPreferences.getString(KEY_JWT_TOKEN, null)
    }

    fun saveToken(token: String) {
        inMemoryToken = token
        sharedPreferences.edit().putString(KEY_JWT_TOKEN, token).commit()
    }

    fun getToken(): String? {
        return inMemoryToken ?: sharedPreferences.getString(KEY_JWT_TOKEN, null).also {
            inMemoryToken = it
        }
    }

    fun clearToken() {
        inMemoryToken = null
        sharedPreferences.edit().remove(KEY_JWT_TOKEN).commit()
    }

    companion object {
        private const val KEY_JWT_TOKEN = "JWT_TOKEN"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

