package vn.futaland.app.navigation

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class RouteIntent(
    val id: String = UUID.randomUUID().toString(),
    val route: String,
    val userId: String? = null
)

object RouteCoordinator {
    private val _pendingIntent = MutableStateFlow<RouteIntent?>(null)
    val pendingIntent = _pendingIntent.asStateFlow()

    fun normalizeRoute(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return null
        }

        var path = uri.path.orEmpty()
        when (uri.scheme?.lowercase()) {
            null -> {
                if (!trimmed.startsWith("/")) return null
            }
            "https", "http" -> {
                val host = uri.host?.lowercase().orEmpty()
                if (host != "futaland.vn" && host != "bds.futaland.vn") return null
            }
            "futaland" -> {
                val host = uri.host.orEmpty()
                if (host.isNotEmpty() && host.lowercase() != "app") {
                    path = "/$host$path"
                }
            }
            else -> return null
        }

        if (path.isEmpty()) path = "/"
        return path
    }

    fun enqueue(route: String, userId: String? = null) {
        val normalized = normalizeRoute(route) ?: return
        _pendingIntent.value = RouteIntent(route = normalized, userId = userId)
    }

    fun consume(intent: RouteIntent) {
        if (_pendingIntent.value?.id == intent.id) {
            _pendingIntent.value = null
        }
    }
}
