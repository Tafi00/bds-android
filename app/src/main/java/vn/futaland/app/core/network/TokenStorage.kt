package vn.futaland.app.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed Encrypted SharedPreferences with in-memory fallback.
 */
class TokenStorage(context: Context) {

    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "futaland_session_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("futaland_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val memoryStore = mutableMapOf<String, String>()

    fun set(key: String, value: String?) {
        if (value == null) {
            memoryStore.remove(key)
            prefs?.edit()?.remove(key)?.apply()
        } else {
            memoryStore[key] = value
            prefs?.edit()?.putString(key, value)?.apply()
        }
    }

    fun get(key: String): String? {
        return prefs?.getString(key, null) ?: memoryStore[key]
    }

    fun clear() {
        memoryStore.clear()
        prefs?.edit()?.clear()?.apply()
    }

    var accessToken: String?
        get() = get("access_token")
        set(value) = set("access_token", value)

    var refreshToken: String?
        get() = get("refresh_token")
        set(value) = set("refresh_token", value)

    var guestToken: String?
        get() = get("guest_token")
        set(value) = set("guest_token", value)
}
