package vn.futaland.app.features.zalo

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import java.util.concurrent.TimeUnit

/** Realtime events of the messaging module (iOS posts them as `Notification.Name.zalo*`). */
sealed interface ZaloEvent {
    data class NewMessage(val message: ZaloMessageModel) : ZaloEvent
    data class MessageUndone(val msgId: String) : ZaloEvent
    data class QR(val event: String, val data: JSONValue) : ZaloEvent
    data object AccountsRefresh : ZaloEvent
    data class AccountStatus(val accountId: String, val isOnline: Boolean) : ZaloEvent
    data class CampaignProgress(val campaignId: String, val data: JSONValue) : ZaloEvent
    data class CampaignLog(val log: ZaloCampaignLogModel) : ZaloEvent
    data class Typing(val provider: String, val accountId: String, val threadId: String, val isTyping: Boolean) : ZaloEvent
    data class AliasesSynced(val accountId: String) : ZaloEvent
    data class AccountsInitFailed(val accountIds: List<String>, val message: String) : ZaloEvent
}

/**
 * Connection to the backend's `/zalo-ws` socket (iOS ZaloWebSocketManager.swift): token in the
 * `auth.` subprotocol (and query, like iOS), text `ping` heartbeat, `sync` replay by sequence
 * number after a reconnect, and `register_qr` for QR login sessions started by this client.
 */
object ZaloWebSocketManager {
    private const val TAG = "ZaloWS"

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _events = MutableSharedFlow<ZaloEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<ZaloEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var isManualDisconnect = false
    private val registeredQRLoginIds = mutableSetOf<String>()
    private var lastSeq = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect() {
        synchronized(lock) {
            if (webSocket != null) return
            val token = APIClient.get().effectiveToken?.trim()
            if (token.isNullOrEmpty()) return
            isManualDisconnect = false
            reconnectJob?.cancel()
            val url = "${APIClient.webSocketUrl("/zalo-ws")}?token=${android.net.Uri.encode(token)}"
            val request = Request.Builder()
                .url(url)
                .header("Sec-WebSocket-Protocol", "futaland-zalo, auth.$token")
                .build()
            webSocket = client.newWebSocket(request, listener)
        }
    }

    fun disconnect() {
        synchronized(lock) {
            isManualDisconnect = true
            reconnectJob?.cancel()
            reconnectJob = null
            pingJob?.cancel()
            pingJob = null
            webSocket?.close(1000, null)
            webSocket = null
        }
        _isConnected.value = false
    }

    fun registerQRLogin(loginId: String) {
        synchronized(lock) { registeredQRLoginIds += loginId }
        if (_isConnected.value) sendRegisterQR(loginId) else connect()
    }

    fun unregisterQRLogin(loginId: String) {
        synchronized(lock) { registeredQRLoginIds -= loginId }
    }

    private fun sendRegisterQR(loginId: String) {
        send(buildJsonObject {
            put("type", "register_qr")
            put("loginId", loginId)
        }.toString())
    }

    private fun sendSync(seq: Int) {
        send(buildJsonObject {
            put("type", "sync")
            put("lastSeq", seq)
        }.toString())
    }

    private fun send(text: String) {
        webSocket?.send(text)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _isConnected.value = true
            reconnectAttempt = 0
            startHeartbeat()
            // register_qr / sync are sent on `ws:connected`: the server only listens for client
            // messages once the token is verified, so anything sent earlier would be dropped.
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleIncomingText(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleDisconnection(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Zalo socket failure: ${t.message}")
            handleDisconnection(webSocket)
        }
    }

    private fun handleDisconnection(socket: WebSocket) {
        synchronized(lock) {
            if (webSocket !== socket) return
            webSocket = null
            pingJob?.cancel()
            pingJob = null
            _isConnected.value = false
            if (isManualDisconnect || reconnectJob?.isActive == true) return
            // Exponential backoff capped at 30 s (iOS: 1, 2, 4 … 30).
            val delayMs = minOf(1L shl minOf(reconnectAttempt, 5), 30L) * 1000
            reconnectAttempt++
            reconnectJob = scope.launch {
                delay(delayMs)
                connect()
            }
        }
    }

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(25_000)
                send("ping")
            }
        }
    }

    private fun handleIncomingText(text: String) {
        if (text == "pong") return
        val json = JSONValue.parse(text)
        if (json.isNull) return
        val data = json["data"]
        val event = json["event"].string
        val seq = data["seq"].int
        if (seq > 0 && event != "ws:connected") lastSeq = maxOf(lastSeq, seq)
        dispatch(event, data)
    }

    private fun emit(event: ZaloEvent) {
        _events.tryEmit(event)
    }

    private fun dispatch(event: String, data: JSONValue) {
        when (event) {
            "message:new" -> emit(ZaloEvent.NewMessage(ZaloMessageModel(data)))
            "message:undone" -> data["msgId"].string.takeIf { it.isNotEmpty() }?.let { emit(ZaloEvent.MessageUndone(it)) }
            "qr:generated", "qr:scanned", "qr:success", "qr:expired", "qr:declined" -> emit(ZaloEvent.QR(event, data))
            "accounts:refresh" -> emit(ZaloEvent.AccountsRefresh)
            "account:status" -> emit(ZaloEvent.AccountStatus(data["accountId"].string, data["isOnline"].bool))
            "campaign:progress", "campaign:status" -> emit(ZaloEvent.CampaignProgress(data["campaignId"].string, data))
            "campaign:log" -> {
                val log = data["log"]
                if (!log.isNull) emit(ZaloEvent.CampaignLog(ZaloCampaignLogModel(log)))
            }
            "ws:connected" -> {
                val pending = synchronized(lock) { registeredQRLoginIds.toList() }
                pending.forEach { sendRegisterQR(it) }
                val seq = data["seq"].int
                if (seq > 0) {
                    // Replay what was broadcast while this client was offline.
                    if (lastSeq in 1 until seq) sendSync(lastSeq)
                    lastSeq = seq
                }
            }
            "sync:response" -> data["messages"].array.forEach { dispatch(it["event"].string, it["data"]) }
            "typing" -> emit(
                ZaloEvent.Typing(
                    data["provider"].string, data["accountId"].string, data["threadId"].string, data["isTyping"].bool
                )
            )
            "aliases:synced" -> emit(ZaloEvent.AliasesSynced(data["accountId"].string))
            "accounts:init_failed" -> emit(
                ZaloEvent.AccountsInitFailed(data["accountIds"].array.map { it.string }, data["message"].string)
            )
        }
    }
}
