package com.freepakistancall.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted storage (AES-256) for the auth token and profile. */
object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) {
        if (::sp.isInitialized) return
        val key = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        sp = EncryptedSharedPreferences.create(
            c, "fpc_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    var token: String?
        get() = sp.getString("token", null)
        set(v) { sp.edit().putString("token", v).apply() }
    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }
    var phone: String
        get() = sp.getString("phone", "") ?: ""
        set(v) { sp.edit().putString("phone", v).apply() }
    var lang: String
        get() = sp.getString("lang", "ur") ?: "ur"   // Urdu is the default
        set(v) { sp.edit().putString("lang", v).apply() }
    var fcm: String?
        get() = sp.getString("fcm", null)
        set(v) { sp.edit().putString("fcm", v).apply() }
    fun clearSession() { sp.edit().remove("token").remove("name").remove("phone").apply() }
}
