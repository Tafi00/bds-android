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

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
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

        val token = APIClient.get().effectiveToken ?: ""
        val wsUrl = if (token.isNotEmpty()) {
            "wss://bds.futaland.vn/ws/chat?token=$token"
        } else {
            "wss://bds.futaland.vn/ws/chat"
        }

        val requestBuilder = Request.Builder()
            .url(wsUrl)

        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
            requestBuilder.header("Sec-WebSocket-Protocol", "futaland-chat, auth.$token")
        } else {
            requestBuilder.header("Sec-WebSocket-Protocol", "futaland-chat")
        }

        val request = requestBuilder.build()

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
                    scope.launch(Dispatchers.Main) {
                        onNewMessage?.invoke(msg)
                    }
                }
                "typing" -> {
                    val convId = json["conversationId"].string
                    val user = json["userName"].string.ifEmpty { "Tư vấn viên" }
                    val current = _typingUsers.value.toMutableMap()
                    current[convId] = user
                    _typingUsers.value = current

                    // Auto-clear typing after 3.5 seconds
                    scope.launch {
                        delay(3500)
                        val updated = _typingUsers.value.toMutableMap()
                        if (updated[convId] == user) {
                            updated.remove(convId)
                            _typingUsers.value = updated
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }
}
