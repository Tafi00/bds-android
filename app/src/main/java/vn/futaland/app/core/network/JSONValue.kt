package vn.futaland.app.core.network

import kotlinx.serialization.json.*

/**
 * Universal dynamic JSON wrapper replicating the iOS JSONValue architecture.
 * Allows safe traversal, fallback defaults, and zero crashes on missing fields.
 */
class JSONValue(val element: JsonElement) {

    companion object {
        val Null = JSONValue(JsonNull)
        val EmptyObject = JSONValue(JsonObject(emptyMap()))
        val EmptyArray = JSONValue(JsonArray(emptyList()))

        fun parse(jsonString: String): JSONValue {
            return try {
                JSONValue(Json.parseToJsonElement(jsonString))
            } catch (_: Exception) {
                Null
            }
        }
    }

    val id: String get() = this["id"].string.ifEmpty { this["_id"].string.ifEmpty { this["recordId"].string } }

    operator fun get(key: String): JSONValue {
        val obj = element as? JsonObject ?: return Null
        val child = obj[key] ?: return Null
        return JSONValue(child)
    }

    operator fun get(index: Int): JSONValue {
        val arr = element as? JsonArray ?: return Null
        if (index in arr.indices) {
            return JSONValue(arr[index])
        }
        return Null
    }

    val string: String
        get() = when (element) {
            is JsonNull -> ""
            is JsonPrimitive -> {
                val content = element.content
                if (content == "null") "" else content
            }
            else -> ""
        }

    val double: Double
        get() = when (element) {
            is JsonPrimitive -> element.doubleOrNull ?: element.content.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    val int: Int
        get() = when (element) {
            is JsonPrimitive -> element.intOrNull ?: element.content.toIntOrNull() ?: 0
            else -> 0
        }

    val bool: Boolean
        get() = when (element) {
            is JsonPrimitive -> element.booleanOrNull ?: element.content.toBooleanStrictOrNull() ?: false
            else -> false
        }

    val array: List<JSONValue>
        get() = when (element) {
            is JsonArray -> element.map { JSONValue(it) }
            else -> emptyList()
        }

    val isNull: Boolean
        get() = element is JsonNull

    fun valueAt(path: String): JSONValue {
        val parts = path.split(".")
        var current = this
        for (part in parts) {
            current = current[part]
            if (current.isNull) return Null
        }
        return current
    }
}
