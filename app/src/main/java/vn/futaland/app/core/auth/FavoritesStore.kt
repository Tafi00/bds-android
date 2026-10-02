package vn.futaland.app.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.ToastCenter

/**
 * Shared set of the signed-in user's favorite property ids (iOS AppSession.favoritePropertyIds),
 * observed by property cards and the property detail heart so they always agree.
 *
 * Backend: `GET /favorites` returns the id list, `POST /favorites/{id}` toggles and returns
 * `{ isFavorited }`. There is no DELETE route — removal is the same toggle.
 */
object FavoritesStore {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private var loaded = false

    fun isFavorited(propertyId: String): Boolean = propertyId.isNotEmpty() && _ids.value.contains(propertyId)

    /** Loads GET /favorites. Keeps the current set on transient failures. */
    suspend fun load(force: Boolean = false) {
        if (!AppSession.shared.isAuthenticated) {
            reset()
            return
        }
        if (loaded && !force) return
        try {
            val res = APIClient.get().request("/favorites")
            _ids.value = res["data"].array.mapNotNull { item ->
                // Ids are plain strings; tolerate `{ propertyId }` objects too.
                item.string.ifEmpty { item["propertyId"].string.ifEmpty { item.id } }.takeIf { it.isNotEmpty() }
            }.toSet()
            loaded = true
        } catch (_: Exception) {
        }
    }

    /**
     * Optimistically toggles a favorite and syncs with POST /favorites/{id}.
     * Returns the resulting state, or null when the user must sign in first.
     */
    suspend fun toggle(propertyId: String): Boolean? {
        if (propertyId.isEmpty()) return null
        if (!AppSession.shared.isAuthenticated) {
            ToastCenter.show(tr("Vui lòng đăng nhập để lưu bất động sản yêu thích."))
            return null
        }
        val wasFavorited = _ids.value.contains(propertyId)
        _ids.value = if (wasFavorited) _ids.value - propertyId else _ids.value + propertyId
        return try {
            val res = APIClient.get().request("/favorites/${android.net.Uri.encode(propertyId)}", method = "POST")
            val data = res["data"]
            val serverState = if (data["isFavorited"].isNull) !wasFavorited else data["isFavorited"].bool
            _ids.value = if (serverState) _ids.value + propertyId else _ids.value - propertyId
            serverState
        } catch (e: Exception) {
            // Roll back the optimistic change.
            _ids.value = if (wasFavorited) _ids.value + propertyId else _ids.value - propertyId
            ToastCenter.show(e.message ?: tr("Không thể cập nhật danh sách yêu thích"), isError = true)
            wasFavorited
        }
    }

    fun reset() {
        loaded = false
        _ids.value = emptySet()
    }
}
