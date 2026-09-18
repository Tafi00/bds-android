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

    fun with(key: String, value: JSONValue): JSONValue {
        val map = (element as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        map[key] = value.element
        return JSONValue(JsonObject(map))
    }

    fun with(key: String, value: String?): JSONValue {
        return with(key, if (value == null) Null else JSONValue(JsonPrimitive(value)))
    }

    fun withUpdates(updates: Map<String, Any?>): JSONValue {
        val map = (element as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        for ((k, v) in updates) {
            when (v) {
                null -> map[k] = JsonNull
                is JSONValue -> map[k] = v.element
                is JsonElement -> map[k] = v
                is String -> map[k] = JsonPrimitive(v)
                is Number -> map[k] = JsonPrimitive(v)
                is Boolean -> map[k] = JsonPrimitive(v)
                else -> map[k] = JsonPrimitive(v.toString())
            }
        }
        return JSONValue(JsonObject(map))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JSONValue) return false
        return element == other.element
    }

    override fun hashCode(): Int {
        return element.hashCode()
    }
}
