package vn.futaland.app.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.features.messaging.ChatUnreadBadge
import vn.futaland.app.features.messaging.FcmRegistrar
import vn.futaland.app.features.messaging.NotificationUnreadBadge

class AppSession private constructor() {

    companion object {
        val shared = AppSession()

        fun defaultPermissions(role: String): Set<String> {
            return when (role) {
                "admin" -> setOf(
                    "admin:access", "apartments:view", "apartments:create", "apartments:edit", "apartments:delete",
                    "customers:view", "customers:create", "customers:edit", "contracts:view", "contracts:create",
                    "contracts:edit", "reports:view", "folders:view", "folders:manage", "chat:view", "chat:send",
                    "zalo:view", "zalo:manage", "projects:view", "projects:create", "projects:edit"
                )
                "sale" -> setOf(
                    "apartments:view", "apartments:create", "apartments:edit", "apartments:owner_contacts",
                    "apartments:owner_pricing", "apartments:internal_notes", "apartments:property_code",
                    "apartments:exact_unit", "customers:view", "customers:create", "customers:edit",
                    "contracts:view", "contracts:create", "contracts:edit", "reports:view", "folders:view",
                    "folders:manage", "chat:view", "chat:send", "zalo:view", "zalo:accounts", "zalo:campaigns", "projects:view"
                )
                "telesale" -> setOf(
                    "apartments:view", "apartments:owner_contacts", "apartments:property_code", "apartments:exact_unit",
                    "customers:view", "customers:create", "customers:edit", "contracts:view", "folders:view", "zalo:view"
                )
                "customer" -> setOf(
                    "apartments:view", "folders:view", "chat:view", "chat:send"
                )
                else -> emptySet()
            }
        }
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentUser = MutableStateFlow<JSONValue?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _permissions = MutableStateFlow<Set<String>>(emptySet())
    val permissions = _permissions.asStateFlow()

    val isAuthenticated: Boolean
        get() = _currentUser.value != null && !APIClient.get().tokenStorage.accessToken.isNullOrEmpty()

    val user: JSONValue?
        get() = _currentUser.value

    val role: String
        get() = _currentUser.value?.get("role")?.string ?: "guest"

    val isInternalStaff: Boolean
        get() = isAuthenticated && role != "customer" && role != "guest" && role.isNotEmpty() && (
            role == "admin" ||
            hasPermission("customers:view") ||
            hasPermission("contracts:view") ||
            hasPermission("apartments:owner_contacts") ||
            hasPermission("admin:access")
        )

    val canManageListings: Boolean
        get() = hasPermission("apartments:create") || hasPermission("apartments:edit")

    fun hasPermission(permission: String): Boolean {
        if (role == "admin") return true
        if (_permissions.value.isEmpty()) {
            return defaultPermissions(role).contains(permission)
        }
        return _permissions.value.contains(permission)
    }

    suspend fun restore() {
        // Revocations queued by an earlier sign-out that could not reach the server.
        FcmRegistrar.retryPendingRevocations(APIClient.get().appContext)
        val token = APIClient.get().tokenStorage.accessToken
        if (token.isNullOrEmpty()) {
            _currentUser.value = null
            _permissions.value = emptySet()
            return
        }

        try {
            val meRes = APIClient.get().request("/auth/me")
            val userData = meRes["data"]
            if (!userData.isNull) {
                _currentUser.value = userData
                fetchPermissions()
                FcmRegistrar.ensureRegistered(APIClient.get().appContext)
                NotificationUnreadBadge.refresh()
                FavoritesStore.load(force = true)
                vn.futaland.app.features.messaging.ChatWebSocketManager.shared.connect()
            } else {
                logout()
            }
        } catch (_: Exception) {
            // Keep recoverable offline state if access token exists
        }
    }

    suspend fun fetchPermissions() {
        try {
            val custom = _currentUser.value?.get("customPermissions")?.array?.map { it.string }?.filter { it.isNotEmpty() }
            if (!custom.isNullOrEmpty()) {
                _permissions.value = custom.toSet()
                return
            }
            val permRes = APIClient.get().request("/users/role-permissions")
            val userRole = role
            val list = permRes["data"][userRole].array.map { it.string }.filter { it.isNotEmpty() }
            if (list.isNotEmpty()) {
                _permissions.value = list.toSet()
            } else {
                _permissions.value = defaultPermissions(userRole)
            }
        } catch (_: Exception) {
            if (_permissions.value.isEmpty()) {
                _permissions.value = defaultPermissions(role)
            }
        }
    }

    fun login(accessToken: String, refreshToken: String, userData: JSONValue) {
        APIClient.get().tokenStorage.accessToken = accessToken
        APIClient.get().tokenStorage.refreshToken = refreshToken
        _currentUser.value = userData
        FcmRegistrar.ensureRegistered(APIClient.get().appContext)
        vn.futaland.app.features.messaging.ChatWebSocketManager.shared.connect()
        backgroundScope.launch {
            NotificationUnreadBadge.refresh()
            ChatUnreadBadge.refresh()
            FavoritesStore.load(force = true)
        }
    }

    /**
     * Full sign-out (iOS AppSession.signOut): clears the local identity immediately,
     * revokes this device's push registration and invalidates the refresh token server-side.
     * Both network calls are best effort and never block the sign-out.
     */
    suspend fun signOut() {
        val refreshToken = APIClient.get().tokenStorage.refreshToken
        // logout() also starts the device revocation (POST /devices/revoke).
        logout()
        if (!refreshToken.isNullOrEmpty()) {
            try {
                // Only `refreshToken` is accepted: the backend logout schema is strict.
                val body = buildJsonObject { put("refreshToken", refreshToken) }.toString()
                APIClient.get().request("/auth/logout", method = "POST", bodyJson = body)
            } catch (_: Exception) {
                // The refresh token expires on its own; local sign-out already happened.
            }
        }
    }

    /** Permanently deletes the signed-in account (DELETE /auth/me), then clears the local identity. */
    suspend fun deleteAccount() {
        APIClient.get().request("/auth/me", method = "DELETE")
        logout()
    }

    /**
     * Clears the local identity synchronously (also used when the session expires):
     * tokens, user, permissions, badges, OS notifications, and queues the device revocation.
     */
    fun logout() {
        val context = APIClient.get().appContext
        FcmRegistrar.queueCurrentRevocation(context)
        APIClient.get().tokenStorage.clear()
        _currentUser.value = null
        _permissions.value = emptySet()
        ChatUnreadBadge.clear()
        NotificationUnreadBadge.clear()
        FavoritesStore.reset()
        try {
            androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
        } catch (_: Exception) {}
        vn.futaland.app.features.messaging.ChatWebSocketManager.shared.disconnect()
        backgroundScope.launch { FcmRegistrar.retryPendingRevocations(context) }
    }
}
