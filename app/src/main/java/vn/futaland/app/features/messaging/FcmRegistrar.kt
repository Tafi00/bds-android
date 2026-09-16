package vn.futaland.app.features.messaging

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient

/**
 * Registers the FCM token with the backend so the server can push chat and
 * business notifications to this device.
 */
object FcmRegistrar {

    private const val PREFS = "futaland_device"
    private const val KEY_DEVICE_ID = "device_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Stable per-install id, kept in a plain prefs file so it survives logout. */
    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun appVersion(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }

    fun ensureRegistered(context: Context) {
        // Skip until a session token exists — otherwise the register call is rejected.
        if (APIClient.get().tokenStorage.accessToken.isNullOrEmpty()) return
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrEmpty()) {
                    scope.launch { register(context, task.result) }
                }
            }
    }

    suspend fun register(context: Context, token: String) {
        if (token.isBlank()) return
        if (APIClient.get().tokenStorage.accessToken.isNullOrEmpty()) return
        try {
            val id = deviceId(context)
            val version = appVersion(context)
            val body = buildString {
                append("{\"deviceId\":\"")
                append(id)
                append("\",\"platform\":\"android\",\"pushToken\":\"")
                append(token)
                append("\",\"appVersion\":\"")
                append(version)
                append("\",\"preferences\":{\"chat\":true,\"leads\":true,\"contracts\":true,\"listings\":true}}")
            }
            APIClient.get().request("/devices/register", method = "POST", bodyJson = body)
        } catch (_: Exception) {
            // Token registration is best-effort; the FCM SDK refreshes the token.
        }
    }
}
