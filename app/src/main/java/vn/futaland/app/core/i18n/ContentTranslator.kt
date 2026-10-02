package vn.futaland.app.core.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import java.io.File

/**
 * Admin-entered content (CMS pages, policies, news, project and property texts…) in the in-app
 * language. Port of iOS `ContentTranslator` / the web's `useTranslatedContent`: the backend
 * translates each Vietnamese text once per language (`POST /api/translations/content`) and the app
 * caches the answers on disk for 12 hours.
 *
 * [tc] returns the cached server translation, else the static dictionary entry, else the text, and
 * queues the text for translation. Composables calling it re-render when answers arrive because it
 * reads [revision]. Vietnamese never hits the network.
 *
 * Groups match the web: `project`, `property`, `policy`, `news`, `cms`, `pricing`.
 */
object ContentTranslator {
    private var revision by mutableIntStateOf(0)

    private data class Item(val language: AppLanguage, val group: String, val text: String)

    private val lock = Any()
    private val cache = HashMap<String, String>()
    private val requested = HashSet<String>()
    private val queue = ArrayList<Item>()
    private var flushScheduled = false
    private var retryAfter = 0L
    private var cacheFile: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val MAX_BATCH = 50
    private const val MAX_TEXT_LENGTH = 8000
    private const val STORAGE_VERSION = 1
    private const val STORAGE_TTL_MS = 12 * 60 * 60 * 1000L

    fun init(context: Context) {
        cacheFile = File(context.cacheDir, "content-translations.json")
        scope.launch { loadPersisted() }
    }

    fun tc(group: String, text: String): String {
        @Suppress("UNUSED_VARIABLE") val observed = revision // re-render when translations arrive
        val language = I18n.language
        if (language == AppLanguage.VI) return text
        val trimmed = text.trim()
        if (trimmed.length < 2 || trimmed.length > MAX_TEXT_LENGTH || trimmed.none { it.isLetter() }) return text
        // Codes such as "CT7-05.07" are not sentences: nothing to translate.
        if (trimmed.none { it.isWhitespace() } && trimmed.any { it.isDigit() }) return text

        val key = cacheKey(language, group, trimmed)
        var shouldFlush = false
        val hit = synchronized(lock) {
            cache[key] ?: run {
                if (System.currentTimeMillis() >= retryAfter && requested.add(key)) {
                    queue.add(Item(language, group, trimmed))
                    shouldFlush = true
                }
                null
            }
        }
        if (hit != null) return hit
        if (shouldFlush) scheduleFlush()
        return I18n.translate(text)
    }

    private fun cacheKey(language: AppLanguage, group: String, text: String) = "${language.code}\u0000$group\u0000$text"

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        scope.launch {
            delay(80) // gather the texts of one composition pass
            flush()
        }
    }

    private suspend fun flush() {
        val items = synchronized(lock) {
            flushScheduled = false
            ArrayList(queue).also { queue.clear() }
        }
        if (items.isEmpty()) return
        var changed = false
        for ((language, languageItems) in items.groupBy { it.language }) {
            for (chunk in languageItems.chunked(MAX_BATCH)) {
                if (send(language, chunk)) changed = true
            }
        }
        if (changed) {
            withContext(Dispatchers.Main) { revision++ }
            persist()
        }
    }

    private suspend fun send(language: AppLanguage, items: List<Item>): Boolean {
        val body = buildJsonObject {
            put("language", language.code)
            put("items", buildJsonArray {
                items.forEach { item -> add(buildJsonObject { put("group", item.group); put("text", item.text) }) }
            })
        }
        return try {
            val response = APIClient.get().request("/translations/content", method = "POST", bodyJson = body.toString())
            var stored = false
            synchronized(lock) {
                for (result in response["data"].array) {
                    val group = result["group"].string
                    val text = result["text"].string
                    val value = result["value"].string.trim()
                    if (group.isEmpty() || text.isEmpty() || value.isEmpty()) continue
                    cache[cacheKey(language, group, text)] = value
                    stored = true
                }
            }
            stored
        } catch (_: Exception) {
            // Allow a later render to ask again, but not in a tight loop while offline.
            synchronized(lock) {
                items.forEach { requested.remove(cacheKey(it.language, it.group, it.text)) }
                retryAfter = System.currentTimeMillis() + 30_000
            }
            false
        }
    }

    private fun loadPersisted() {
        val file = cacheFile ?: return
        try {
            if (!file.exists()) return
            val saved = Json.parseToJsonElement(file.readText()) as JsonObject
            val version = saved["version"]?.jsonPrimitive?.longOrNull ?: return
            val savedAt = saved["savedAt"]?.jsonPrimitive?.longOrNull ?: return
            if (version != STORAGE_VERSION.toLong() || System.currentTimeMillis() - savedAt > STORAGE_TTL_MS) return
            val content = saved["content"] as? JsonObject ?: return
            synchronized(lock) {
                for ((key, value) in content) cache.putIfAbsent(key, (value as JsonPrimitive).content)
            }
            scope.launch(Dispatchers.Main) { revision++ }
        } catch (_: Exception) {
        }
    }

    private fun persist() {
        val file = cacheFile ?: return
        val snapshot = synchronized(lock) { HashMap(cache) }
        scope.launch {
            try {
                val payload = buildJsonObject {
                    put("version", STORAGE_VERSION)
                    put("savedAt", System.currentTimeMillis())
                    put("content", buildJsonObject { snapshot.forEach { (key, value) -> put(key, value) } })
                }
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(payload.toString())
                temp.renameTo(file)
            } catch (_: Exception) {
            }
        }
    }
}

/** This admin-entered text in the in-app language via the backend translation (see [ContentTranslator]). */
fun String.translated(group: String): String = ContentTranslator.tc(group, this)
