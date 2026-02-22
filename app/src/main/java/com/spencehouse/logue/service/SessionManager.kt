package com.spencehouse.logue.service

import android.content.Context
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
        set(value) = sharedPreferences.edit().putString("honda_username", value).apply()

    var password: String?
        get() = sharedPreferences.getString("honda_password", null)
        set(value) = sharedPreferences.edit().putString("honda_password", value).apply()

    var vin: String?
        get() = sharedPreferences.getString("honda_vin", null)
        set(value) = sharedPreferences.edit().putString("honda_vin", value).apply()

    var pin: String?
        get() = sharedPreferences.getString("honda_pin", null)
        set(value) = sharedPreferences.edit().putString("honda_pin", value).apply()

    var useMetric: Boolean
        get() = sharedPreferences.getBoolean("use_metric", false)
        set(value) = sharedPreferences.edit().putBoolean("use_metric", value).apply()

    var accessToken: String? = null
    var hidasIdent: String? = null

    fun logout() {
        sharedPreferences.edit().clear().apply()
        accessToken = null
        hidasIdent = null
    }
}
