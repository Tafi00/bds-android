package vn.futaland.app.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue

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
        get() = isAuthenticated && role != "customer" && role != "guest" && role.isNotEmpty()

    val canManageListings: Boolean
        get() = hasPermission("apartments:create") || hasPermission("apartments:edit") || (isInternalStaff && role != "telesale")

    fun hasPermission(permission: String): Boolean {
        if (role == "admin") return true
        if (_permissions.value.isEmpty()) {
            return defaultPermissions(role).contains(permission)
        }
        return _permissions.value.contains(permission)
    }

    suspend fun restore() {
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
    }

    fun logout() {
        APIClient.get().tokenStorage.clear()
        _currentUser.value = null
        _permissions.value = emptySet()
    }
    suspend fun ensureGuest(): Boolean {
        if (isAuthenticated) return true
        val currentToken = APIClient.get().tokenStorage.accessToken
        if (!currentToken.isNullOrEmpty()) return true
        return try {
            val res = APIClient.get().request("/auth/guest", method = "POST", bodyJson = "{}")
            val data = res["data"]
            val token = data["accessToken"].string
            if (token.isNotEmpty()) {
                APIClient.get().tokenStorage.accessToken = token
                APIClient.get().tokenStorage.guestToken = token
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
