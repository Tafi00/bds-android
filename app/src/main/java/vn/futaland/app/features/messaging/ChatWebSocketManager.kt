package vn.futaland.app.features.messaging

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import java.util.concurrent.TimeUnit

class ChatWebSocketManager private constructor() {

    companion object {
        val shared = ChatWebSocketManager()
        private const val TAG = "ChatWS"
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingUsers = _typingUsers.asStateFlow()

    var onNewMessage: ((JSONValue) -> Unit)? = null
    var onMessagesRead: ((conversationId: String, userId: String) -> Unit)? = null
    var onNewConversation: ((JSONValue) -> Unit)? = null
    var onMessageTranslated: ((messageId: String, conversationId: String, translations: Map<String, String>) -> Unit)? = null

    private val productEventListeners = java.util.concurrent.ConcurrentHashMap<String, (JSONValue) -> Unit>()

    fun addProductEventListener(key: String, listener: (JSONValue) -> Unit) {
        productEventListeners[key] = listener
    }

    fun removeProductEventListener(key: String) {
        productEventListeners.remove(key)
    }

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    private val typingJobs = mutableMapOf<String, Job>()
    private val joinedConversations = mutableSetOf<String>()
    private var isManualDisconnect = false

    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WS
        .build()

    fun connect() {
        if (webSocket != null) return
        isManualDisconnect = false

        // No anonymous chat sessions — guests get the transient AI chat instead,
        // so a missing token means "not allowed to connect", not "connect blank".
        val token = APIClient.get().effectiveToken
        if (token.isNullOrEmpty()) return
        val wsUrl = "wss://bds.futaland.vn/ws/chat?token=$token"

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $token")
            .header("Sec-WebSocket-Protocol", "futaland-chat, auth.$token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to FUTA Chat WebSocket")
                _isConnected.value = true
                startHeartbeat()

                // Re-join active conversations
                synchronized(joinedConversations) {
                    for (convId in joinedConversations) {
                        join(convId)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code / $reason")
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code / $reason")
                _isConnected.value = false
                this@ChatWebSocketManager.webSocket = null
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure: ${t.message}")
                _isConnected.value = false
                this@ChatWebSocketManager.webSocket = null
                if (!isManualDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnect() {
        isManualDisconnect = true
        pingJob?.cancel()
        pingJob = null
        synchronized(typingJobs) {
            typingJobs.values.forEach { it.cancel() }
            typingJobs.clear()
        }
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _isConnected.value = false
        _typingUsers.value = emptyMap()
    }

    fun join(conversationId: String) {
        if (conversationId.isEmpty()) return
        synchronized(joinedConversations) {
            joinedConversations.add(conversationId)
        }
        send("{\"type\":\"join_conversation\",\"conversationId\":\"$conversationId\"}")
    }

    fun sendMessage(conversationId: String, content: String, metadataJson: String? = null) {
        val metaPart = if (!metadataJson.isNullOrEmpty()) ",\"metadata\":$metadataJson" else ""
        val escaped = content.replace("\"", "\\\"").replace("\n", "\\n")
        send("{\"type\":\"send_message\",\"conversationId\":\"$conversationId\",\"content\":\"$escaped\"$metaPart}")
    }

    fun sendTyping(conversationId: String) {
        send("{\"type\":\"typing\",\"conversationId\":\"$conversationId\"}")
    }

    fun markRead(conversationId: String) {
        send("{\"type\":\"mark_read\",\"conversationId\":\"$conversationId\"}")
    }

    private fun send(payload: String) {
        val ws = webSocket
        if (ws != null) {
            ws.send(payload)
        }
    }

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(25_000)
                send("{\"type\":\"ping\"}")
            }
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(3000)
            if (!isManualDisconnect && webSocket == null) {
                Log.d(TAG, "Attempting reconnect...")
                connect()
            }
        }
    }

    private fun handleIncomingMessage(rawText: String) {
        try {
            val json = JSONValue.parse(rawText)
            val type = json["type"].string

            when (type) {
                "new_message" -> {
                    val msg = json["message"]
                    val convId = msg["conversationId"].string
                    val isBot = msg["senderType"].string == "bot"
                    if (convId.isNotEmpty() && isBot) {
                        synchronized(typingJobs) {
                            typingJobs.remove(convId)?.cancel()
                        }
                        if (_typingUsers.value.containsKey(convId)) {
                            val updated = _typingUsers.value.toMutableMap()
                            updated.remove(convId)
                            _typingUsers.value = updated
                        }
                    }
                    scope.launch(Dispatchers.Main) {
                        onNewMessage?.invoke(msg)
                    }
                }
                "typing" -> {
                    val convId = json["conversationId"].string
                    val user = json["userName"].string.ifEmpty { "Tư vấn viên" }
                    if (convId.isNotEmpty()) {
                        val current = _typingUsers.value
                        if (current[convId] != user) {
                            val updated = current.toMutableMap()
                            updated[convId] = user
                            _typingUsers.value = updated
                        }

                        // Renew auto-clear timer per conversation (5 seconds)
                        synchronized(typingJobs) {
                            typingJobs[convId]?.cancel()
                            typingJobs[convId] = scope.launch {
                                delay(5000)
                                synchronized(typingJobs) {
                                    typingJobs.remove(convId)
                                }
                                val updated = _typingUsers.value.toMutableMap()
                                if (updated[convId] == user) {
                                    updated.remove(convId)
                                    _typingUsers.value = updated
                                }
                            }
                        }
                    }
                }
                "messages_read" -> {
                    val convId = json["conversationId"].string
                    val uId = json["userId"].string
                    scope.launch(Dispatchers.Main) {
                        onMessagesRead?.invoke(convId, uId)
                    }
                }
                "new_conversation" -> {
                    val conv = json["conversation"]
                    scope.launch(Dispatchers.Main) {
                        onNewConversation?.invoke(conv)
                    }
                }
                "message_translated" -> {
                    val msgId = json["messageId"].string
                    val convId = json["conversationId"].string
                    val trans = mutableMapOf<String, String>()
                    (json["translations"].element as? kotlinx.serialization.json.JsonObject)?.forEach { (k, v) ->
                        trans[k] = (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    }
                    scope.launch(Dispatchers.Main) {
                        onMessageTranslated?.invoke(msgId, convId, trans)
                    }
                }
                "product_holding_updated", "product_registration_updated", "product_inventory_updated" -> {
                    scope.launch(Dispatchers.Main) {
                        for (listener in productEventListeners.values) {
                            try {
                                listener(json)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error invoking product event listener: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }
}
