package vn.futaland.app.features.messaging

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import vn.futaland.app.core.network.APIClient

/**
 * Registers the FCM token with the backend so the server can push chat and
 * business notifications to this device.
 */
object FcmRegistrar {

    private const val PREFS = "futaland_device"
    private const val KEY_DEVICE_ID = "device_id"
    // Credential returned by /devices/register that authorizes revoking only this
    // installation's registration, even after the session token is gone (iOS DeviceManager).
    private const val KEY_REVOCATION = "revocation_current"
    private const val KEY_PENDING_REVOCATIONS = "revocation_pending"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Stable per-install id, kept in a plain prefs file so it survives logout. */
    fun deviceId(context: Context): String {
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
        if (APIClient.get().effectiveToken.isNullOrEmpty()) return
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrEmpty()) {
                    scope.launch { register(context, task.result) }
                }
            }
    }

    suspend fun register(context: Context, token: String) {
        if (token.isBlank()) return
        if (APIClient.get().effectiveToken.isNullOrEmpty()) return
        try {
            val id = deviceId(context)
            val version = appVersion(context)
            val body = buildJsonObject {
                put("deviceId", id)
                put("platform", "android")
                put("pushToken", token)
                put("appVersion", version)
                putJsonObject("preferences") {
                    put("chat", true)
                    put("leads", true)
                    put("contracts", true)
                    put("listings", true)
                }
            }.toString()
            val res = APIClient.get().request("/devices/register", method = "POST", bodyJson = body)
            val revocation = res["data"]["revocationToken"].string
            if (revocation.isNotEmpty()) {
                prefs(context).edit().putString(KEY_REVOCATION, revocation).apply()
            }
        } catch (_: Exception) {
            // Token registration is best-effort; the FCM SDK refreshes the token.
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Moves the current registration's revocation credential to the pending queue.
     * Called whenever the local identity is cleared (sign-out, session expiry, account deletion).
     */
    fun queueCurrentRevocation(context: Context) {
        val p = prefs(context)
        val current = p.getString(KEY_REVOCATION, null) ?: return
        val pending = (p.getStringSet(KEY_PENDING_REVOCATIONS, emptySet()) ?: emptySet()) + current
        p.edit().putStringSet(KEY_PENDING_REVOCATIONS, pending).remove(KEY_REVOCATION).apply()
    }

    /**
     * POST /devices/revoke for every queued credential so this phone stops receiving the
     * previous account's pushes. Failures stay queued and are retried on the next launch.
     */
    suspend fun retryPendingRevocations(context: Context) {
        val p = prefs(context)
        val pending = p.getStringSet(KEY_PENDING_REVOCATIONS, emptySet()).orEmpty()
        if (pending.isEmpty()) return
        val id = deviceId(context)
        val remaining = pending.toMutableSet()
        for (token in pending) {
            try {
                val body = buildJsonObject {
                    put("deviceId", id)
                    put("revocationToken", token)
                }.toString()
                // The route is unauthenticated: the revocation token is the authorization.
                APIClient.get().request("/devices/revoke", method = "POST", bodyJson = body)
                remaining.remove(token)
            } catch (e: vn.futaland.app.core.network.APIError) {
                // A rejected credential (4xx) will never succeed; drop it.
                if (e.statusCode in 400..499) remaining.remove(token) else break
            } catch (_: Exception) {
                break
            }
        }
        p.edit().putStringSet(KEY_PENDING_REVOCATIONS, remaining).apply()
    }
}
