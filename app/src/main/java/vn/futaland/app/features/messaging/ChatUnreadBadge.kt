package vn.futaland.app.features.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.futaland.app.core.network.APIClient

/**
 * Global unread chat counter, observed by the bottom navigation so the floating
 * chat button can render a badge without owning the chat screen state.
 */
object ChatUnreadBadge {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun set(value: Int) {
        _count.value = value.coerceAtLeast(0)
    }

    fun clear() {
        _count.value = 0
    }

    /** Re-fetch the unread total from the server. Safe to call from any coroutine. */
    suspend fun refresh() {
        try {
            val res = APIClient.get().request("/chat/unread-counts")
            set(res["data"]["total"].int)
        } catch (_: Exception) {
            // Unread badge is supplementary; ignore transient failures.
        }
    }
}
