package com.spencehouse.logue.service

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(@ApplicationContext context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "logue_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var username: String?
        get() = sharedPreferences.getString("honda_username", null)
        set(value) = sharedPreferences.edit { putString("honda_username", value) }

    var password: String?
        get() = sharedPreferences.getString("honda_password", null)
        set(value) = sharedPreferences.edit { putString("honda_password", value) }

    var vin: String?
        get() = sharedPreferences.getString("honda_vin", null)
        set(value) = sharedPreferences.edit { putString("honda_vin", value) }

    var pin: String?
        get() = sharedPreferences.getString("honda_pin", null)
        set(value) = sharedPreferences.edit { putString("honda_pin", value) }

    var useCelsius: Boolean
        get() = sharedPreferences.getBoolean("use_celsius", false)
        set(value) = sharedPreferences.edit { putBoolean("use_celsius", value) }

    var useKilometers: Boolean
        get() = sharedPreferences.getBoolean("use_kilometers", false)
        set(value) = sharedPreferences.edit { putBoolean("use_kilometers", value) }

    var useKpa: Boolean
        get() = sharedPreferences.getBoolean("use_kpa", false)
        set(value) = sharedPreferences.edit { putBoolean("use_kpa", value) }

    var accessToken: String? = null
    var hidasIdent: String? = null

    fun logout() {
        sharedPreferences.edit { clear() }
        accessToken = null
        hidasIdent = null
    }
}
