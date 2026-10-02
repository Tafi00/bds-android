package vn.futaland.app.features.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.futaland.app.core.network.APIClient

/**
 * Global unread notification counter for the home bell (iOS NotificationInboxStore.unreadCount).
 * Refreshed after sign-in, on app start and when a push arrives; cleared on sign-out.
 */
object NotificationUnreadBadge {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun set(value: Int) {
        _count.value = value.coerceAtLeast(0)
    }

    fun clear() {
        _count.value = 0
    }

    /** GET /notifications/unread-count. Safe to call from any coroutine. */
    suspend fun refresh() {
        if (APIClient.get().effectiveToken.isNullOrEmpty()) {
            clear()
            return
        }
        try {
            val res = APIClient.get().request("/notifications/unread-count")
            set(res["data"]["unreadCount"].int)
        } catch (_: Exception) {
            // The badge is supplementary; keep the last known value on transient failures.
        }
    }
}
