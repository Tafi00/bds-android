package vn.futaland.app.features.messaging

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import coil3.compose.AsyncImage

data class ConversationItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = true,
    val isAi: Boolean = false,
    val badge: String = "",
    val contextProjectName: String = "",
    val contextCode: String = ""
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val isMe: Boolean,
    val time: String,
    val propertyCard: JSONValue? = null,
    val propertyCards: List<JSONValue> = emptyList()
)

/** Ids of bubbles rendered optimistically before the server confirms them. */
private const val LOCAL_MESSAGE_PREFIX = "local-"

private val localMessageCounter = java.util.concurrent.atomic.AtomicLong()

private fun nextLocalMessageId(): String =
    "$LOCAL_MESSAGE_PREFIX${System.currentTimeMillis()}-${localMessageCounter.incrementAndGet()}"

/** Extract every property card embedded in message metadata.
 *  Bot replies send `propertyCards` (array); older payloads use `propertyCard`
 *  or `apartmentCard` (single object). */
private fun parseChatPropertyCards(metadata: JSONValue): List<JSONValue> {
    val list = metadata["propertyCards"].array
    val singles = if (list.isNotEmpty()) list else listOf(metadata["propertyCard"], metadata["apartmentCard"])
    return singles.filter { card ->
        !card.isNull && (card["propertyCode"].string.isNotEmpty() || card["title"].string.isNotEmpty() || card.id.isNotEmpty())
    }
}

/**
 * Whether a message returned by the API/WebSocket was authored by the signed-in
 * account. Guests have no profile id on the device, so the side of the
 * conversation being viewed is used as a fallback.
 */
private fun isOwnChatMessage(
    senderId: String,
    senderType: String,
    currentUserId: String,
    viewerIsCustomer: Boolean
): Boolean {
    if (currentUserId.isNotEmpty() && senderId.isNotEmpty()) return senderId == currentUserId
    return if (viewerIsCustomer) senderType == "customer" else senderType == "staff"
}

/**
 * Adds an incoming message to the thread without creating a duplicate bubble.
 *
 * The chat gateway echoes the sender's own message back over the socket while the
 * composer already renders it optimistically, so the echo must replace the local
 * bubble instead of being appended as a message from the other party.
 */
private fun mergeIncomingChatMessage(list: MutableList<ChatMessage>, incoming: ChatMessage) {
    if (incoming.id.isNotEmpty()) {
        val sameId = list.indexOfFirst { it.id == incoming.id }
        if (sameId >= 0) {
            list[sameId] = incoming
            return
        }
    }
    if (incoming.isMe) {
        val localEcho = list.indexOfLast {
            it.isMe && it.id.startsWith(LOCAL_MESSAGE_PREFIX) && it.content == incoming.content
        }
        if (localEcho >= 0) {
            // Keep the "just sent" label the optimistic bubble was showing.
            list[localEcho] = incoming.copy(time = list[localEcho].time)
            return
        }
    }
    list.add(incoming)
}

@Composable
fun ChatScreen(
    productContext: vn.futaland.app.core.sales.ProductContext = vn.futaland.app.core.sales.ProductContext.CUSTOMER,
    onNavigate: (String) -> Unit = {},
    initialConversationId: String? = null,
    targetAdvisorId: String? = null,
    targetAdvisorName: String? = null,
    targetPropertyId: String? = null,
    isAiChat: Boolean = false,
    staffContext: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val accountIsStaff = AppSession.shared.role != "customer" && AppSession.shared.role != "guest"
    // Role follows the screen context: outside the advisor workspace the
    // account always talks as a customer, even when the account is staff.
    val isStaff = staffContext && accountIsStaff
    val buyerPhone = if (accountIsStaff) AppSession.shared.user?.get("phone")?.string.orEmpty() else ""

    fun conversationBody(vararg fields: Pair<String, String>): String {
        val parts = mutableListOf<String>()
        if (buyerPhone.isNotEmpty()) parts.add("\"customerPhone\":\"$buyerPhone\"")
        fields.forEach { (key, value) -> if (value.isNotEmpty()) parts.add("\"$key\":\"$value\"") }
        return if (parts.isEmpty()) "{}" else "{${parts.joinToString(",")}}"
    }

    // A null id shows the conversation list (the entry screen). Deep links and an
    // explicit advisor/AI target open the thread directly instead.
    var activeConversationId by remember { mutableStateOf(initialConversationId) }
    var activeConversationName by remember { mutableStateOf(targetAdvisorName ?: (if (isAiChat) "Trợ lý AI FUTA Land" else "Trợ lý AI FUTA Land")) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(activeConversationId) {
        delay(350)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    var search by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("all") }
    var showingNewChatDialog by remember { mutableStateOf(false) }
    var newCustomerPhone by remember { mutableStateOf("") }
    var isCreatingConv by remember { mutableStateOf(false) }
    var staffChatMode by remember { mutableStateOf("advisor") } // "advisor" vs "buyer"
    val availableAdvisors = remember { mutableStateListOf<JSONValue>() }
    val conversations = remember {
        mutableStateListOf<ConversationItem>()
    }

    suspend fun fetchConversationsList(mode: String) {
        try {
            val unreadMap = mutableMapOf<String, Int>()
            try {
                val unreadRes = APIClient.get().request("/chat/unread-counts")
                val perConv = unreadRes["data"]["perConversation"].element as? kotlinx.serialization.json.JsonObject
                perConv?.forEach { (k, v) ->
                    val count = (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    unreadMap[k] = count
                }
            } catch (_: Exception) {}

            val query = mutableMapOf("limit" to "50")
            if (isStaff) {
                query["mode"] = mode
            } else if (accountIsStaff) {
                // Staff outside the advisor workspace only sees own buyer chats.
                query["mode"] = "buyer"
            }
            val res = APIClient.get().request("/chat/conversations", query = query)
            val list = res["data"].array

            conversations.clear()
            for (c in list) {
                val advId = c["advisorId"].string
                val isAi = advId.isEmpty()
                val name = if (isStaff && mode == "advisor") {
                    val cName = c["customer"]["customerName"].string
                    cName.ifEmpty { c["customerPhone"].string.ifEmpty { "Khách hàng" } }
                } else {
                    if (isAi) "Trợ lý AI FUTA Land" else c["advisor"]["name"].string.ifEmpty { "Sale phụ trách điều phối" }
                }
                val badge = if (isStaff && mode == "advisor") "" else (if (isAi) "" else "Sale phụ trách")
                val lastMsg = c["lastMessageContent"].string.ifEmpty { "Bắt đầu cuộc trò chuyện..." }
                val time = formatChatDateTime(c["lastMessageAt"].string.ifEmpty { c["updatedAt"].string })
                val unread = unreadMap[c.id] ?: c["unreadCount"].int
                val projectName = c["property"]["projectName"].string
                val code = c["property"]["propertyCode"].string.ifEmpty { c["property"]["unitCode"].string }
                conversations.add(
                    ConversationItem(
                        id = c.id,
                        name = name,
                        lastMessage = lastMsg,
                        time = time,
                        unreadCount = unread,
                        isOnline = true,
                        isAi = isAi,
                        badge = badge,
                        contextProjectName = projectName,
                        contextCode = code
                    )
                )
            }

            if (isStaff && mode == "buyer") {
                try {
                    val advRes = APIClient.get().request("/sales/advisors")
                    availableAdvisors.clear()
                    availableAdvisors.addAll(advRes["data"].array)
                } catch (_: Exception) {}
            }

            ChatUnreadBadge.set(conversations.sumOf { it.unreadCount })
        } catch (_: Exception) {}
    }

    suspend fun refreshUnreadBadge() {
        try {
            val res = APIClient.get().request("/chat/unread-counts")
            val total = res["data"]["total"].int
            ChatUnreadBadge.set(total)
            val perConv = res["data"]["perConversation"].element as? kotlinx.serialization.json.JsonObject
            for (i in conversations.indices) {
                val conv = conversations[i]
                val count = (perConv?.get(conv.id) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                if (conv.unreadCount != count) {
                    conversations[i] = conv.copy(unreadCount = count)
                }
            }
        } catch (_: Exception) {}
    }

    // Ensure guest session & connect WebSocket on launch
    LaunchedEffect(Unit) {
        if (!AppSession.shared.isAuthenticated) {
            AppSession.shared.ensureGuest()
        }
        ChatWebSocketManager.shared.connect()
        fetchConversationsList(if (isStaff) staffChatMode else "buyer")

        try {
            if (!targetAdvisorId.isNullOrEmpty()) {
                val existing = conversations.find { !it.isAi && it.name.contains(targetAdvisorName ?: "") }
                if (existing != null) {
                    activeConversationId = existing.id
                    activeConversationName = targetAdvisorName ?: existing.name
                } else {
                    val body = conversationBody(
                        "advisorId" to targetAdvisorId,
                        "propertyId" to (targetPropertyId ?: "")
                    )
                    val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = body)
                    val newConv = createRes["data"]
                    if (!newConv.id.isEmpty()) {
                        val advName = targetAdvisorName ?: newConv["advisor"]["name"].string.ifEmpty { "Tư vấn viên" }
                        conversations.add(0, ConversationItem(newConv.id, advName, "Bắt đầu cuộc trò chuyện...", "Bây giờ", 0, true, false, "Sale phụ trách"))
                        activeConversationId = newConv.id
                        activeConversationName = advName
                    }
                }
            } else if (isAiChat) {
                val aiConv = conversations.find { it.isAi }
                if (aiConv != null) {
                    activeConversationId = aiConv.id
                    activeConversationName = aiConv.name
                } else {
                    val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = conversationBody())
                    val newConv = createRes["data"]
                    if (!newConv.id.isEmpty()) {
                        conversations.add(0, ConversationItem(newConv.id, "Trợ lý AI FUTA Land", "Bắt đầu cuộc trò chuyện...", "Bây giờ", 0, true, true, ""))
                        activeConversationId = newConv.id
                        activeConversationName = "Trợ lý AI FUTA Land"
                    }
                }
            } else if (!isStaff && activeConversationId.isNullOrEmpty()) {
                // Ensure the AI conversation always exists so it appears in the list.
                if (conversations.isEmpty()) {
                    val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = conversationBody())
                    val newConv = createRes["data"]
                    if (!newConv.id.isEmpty()) {
                        conversations.add(ConversationItem(newConv.id, "Trợ lý AI FUTA Land", "Bắt đầu cuộc trò chuyện...", "Bây giờ", 0, true, true, ""))
                    }
                }
                // Open the AI thread directly only when there is no advisor chat yet.
                // Once the customer has several conversations, show the list instead.
                val hasAdvisorConv = conversations.any { !it.isAi }
                if (!hasAdvisorConv) {
                    val aiConv = conversations.find { it.isAi } ?: conversations.firstOrNull()
                    if (aiConv != null) {
                        activeConversationId = aiConv.id
                        activeConversationName = aiConv.name
                    }
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(staffChatMode) {
        if (isStaff) {
            fetchConversationsList(staffChatMode)
        }
    }

    val isConnected by ChatWebSocketManager.shared.isConnected.collectAsState()
    val typingUsers by ChatWebSocketManager.shared.typingUsers.collectAsState()

    if (activeConversationId.isNullOrEmpty() && isStaff) {
        // VIEW 1: CONVERSATION LIST (Staff & Advisors only)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FutaColors.PageBg)
        ) {
            // Top Bar
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onBack.invoke() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trung tâm trò chuyện",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B), CircleShape)
                                )
                                Text(
                                    text = if (isConnected) "Đã kết nối trực tiếp" else "Đang kết nối lại...",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isConnected) Color(0xFF059669) else Color(0xFFD97706)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = FutaColors.BrandGreen,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { showingNewChatDialog = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, "Tạo hội thoại mới", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Staff Mode Switcher
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(3.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (staffChatMode == "advisor") Color.White else Color.Transparent,
                                shadowElevation = if (staffChatMode == "advisor") 1.dp else 0.dp,
                                modifier = Modifier.weight(1f).clickable { staffChatMode = "advisor" }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                    Text(
                                        text = "Khách hàng liên hệ",
                                        fontSize = 12.sp,
                                        fontWeight = if (staffChatMode == "advisor") FontWeight.Bold else FontWeight.Medium,
                                        color = if (staffChatMode == "advisor") FutaColors.BrandGreen else Color(0xFF64748B)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (staffChatMode == "buyer") Color.White else Color.Transparent,
                                shadowElevation = if (staffChatMode == "buyer") 1.dp else 0.dp,
                                modifier = Modifier.weight(1f).clickable { staffChatMode = "buyer" }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                    Text(
                                        text = "Tôi hỏi mua / TVV khác",
                                        fontSize = 12.sp,
                                        fontWeight = if (staffChatMode == "buyer") FontWeight.Bold else FontWeight.Medium,
                                        color = if (staffChatMode == "buyer") FutaColors.BrandGreen else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    FutaInput(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "Tìm theo tên, số điện thoại…",
                        leadingIcon = Icons.Default.Search
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val unreadTotal = conversations.sumOf { it.unreadCount }
                        listOf(
                            "all" to "Tất cả (${conversations.size})",
                            "unread" to (if (unreadTotal > 0) "Chưa đọc ($unreadTotal)" else "Chưa đọc")
                        ).forEach { (tab, label) ->
                            val isSelected = filterTab == tab
                            Surface(
                                onClick = { filterTab = tab },
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else FutaColors.Navy,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Conversations List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val filtered = conversations.filter {
                    (filterTab != "unread" || it.unreadCount > 0) &&
                    (search.isEmpty() || it.name.contains(search, ignoreCase = true) || it.lastMessage.contains(search, ignoreCase = true))
                }

                itemsIndexed(filtered, key = { _, conv -> conv.id }) { _, conv ->
                    FutaCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            activeConversationId = conv.id
                            activeConversationName = conv.name
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    shape = CircleShape,
                                    color = FutaColors.MintBg,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = conv.name.take(2).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.BrandGreen,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                                if (conv.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = conv.name,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.Navy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (conv.badge.isNotEmpty()) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (conv.isAi) FutaColors.MintBg else Color(0xFFEFF6FF)
                                            ) {
                                                Text(
                                                    text = conv.badge,
                                                    color = if (conv.isAi) FutaColors.BrandGreen else Color(0xFF1D4ED8),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = conv.time,
                                        fontSize = 11.sp,
                                        color = FutaColors.Muted
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conv.lastMessage,
                                        fontSize = 12.5.sp,
                                        color = if (conv.unreadCount > 0) FutaColors.Navy else FutaColors.Slate,
                                        fontWeight = if (conv.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (conv.unreadCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFFF8D28),
                                            modifier = Modifier.padding(start = 6.dp)
                                        ) {
                                            Text(
                                                text = conv.unreadCount.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!targetPropertyId.isNullOrEmpty()) {
                    item {
                        TextButton(onClick = { onNavigate(vn.futaland.app.navigation.FutaDestinations.propertyDetail(targetPropertyId, productContext)) }) {
                            Text("Xem căn")
                        }
                    }
                }

                if (staffChatMode == "buyer" && availableAdvisors.isNotEmpty()) {
                    item {
                        Text(
                            text = "TƯ VẤN VIÊN SẴN SÀNG HỖ TRỢ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(availableAdvisors.size) { idx ->
                        val item = availableAdvisors[idx]
                        val adv = item["advisor"]
                        val property = item["property"]
                        val advName = adv["name"].string.ifEmpty { "Tư vấn viên FUTA" }
                        val propCode = property["propertyCode"].string
                        val projectName = property["projectName"].string

                        FutaCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    try {
                                        val body = conversationBody(
                                            "advisorId" to adv["id"].string,
                                            "propertyId" to property.id
                                        )
                                        val res = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = body)
                                        val newConv = res["data"]
                                        if (!newConv.id.isEmpty()) {
                                            activeConversationId = newConv.id
                                            activeConversationName = advName
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = FutaColors.MintBg,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = advName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    if (propCode.isNotEmpty()) {
                                        Text(text = "$propCode • $projectName", fontSize = 11.5.sp, color = FutaColors.BrandGreen)
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = FutaColors.BrandGreen,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = "Nhắn tin",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (activeConversationId.isNullOrEmpty()) {
        // VIEW 3: CONVERSATION LIST (Customer & guest) — shown instead of the thread
        // so a customer with many chats can find them again without swiping pills.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FutaColors.PageBg)
        ) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onBack.invoke() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trò chuyện",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B), CircleShape)
                                )
                                Text(
                                    text = if (isConnected) "Đã kết nối trực tiếp" else "Đang kết nối lại...",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isConnected) Color(0xFF059669) else Color(0xFFD97706)
                                )
                            }
                        }

                        // Quick start button: open (or create) the AI conversation.
                        Surface(
                            shape = CircleShape,
                            color = FutaColors.BrandGreen,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    val aiConv = conversations.find { it.isAi }
                                    if (aiConv != null) {
                                        activeConversationId = aiConv.id
                                        activeConversationName = aiConv.name
                                    } else {
                                        scope.launch {
                                            try {
                                                val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = conversationBody())
                                                val newConv = createRes["data"]
                                                if (!newConv.id.isEmpty()) {
                                                    conversations.add(ConversationItem(newConv.id, "Trợ lý AI FUTA Land", "Bắt đầu cuộc trò chuyện...", "Bây giờ", 0, true, true, ""))
                                                    activeConversationId = newConv.id
                                                    activeConversationName = "Trợ lý AI FUTA Land"
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_lucide_bot), "Trợ lý AI", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    FutaInput(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "Tìm cuộc trò chuyện…",
                        leadingIcon = Icons.Default.Search
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val sorted = conversations.withIndex()
                    .sortedWith(compareBy({ if (it.value.isAi) 0 else 1 }, { it.index }))
                    .map { it.value }
                val filtered = sorted.filter {
                    search.isEmpty() || it.name.contains(search, ignoreCase = true) || it.lastMessage.contains(search, ignoreCase = true)
                }

                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Chưa có cuộc trò chuyện nào", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            Text("Bấm vào biểu tượng Trợ lý AI để bắt đầu.", fontSize = 12.sp, color = FutaColors.Muted)
                        }
                    }
                }

                itemsIndexed(filtered, key = { _, conv -> conv.id }) { _, conv ->
                    FutaCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            activeConversationId = conv.id
                            activeConversationName = conv.name
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (conv.isAi) FutaColors.BrandGreen else FutaColors.MintBg,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (conv.isAi) {
                                        Icon(painterResource(R.drawable.ic_lucide_bot), null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    } else {
                                        Text(
                                            text = conv.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.BrandGreen,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = conv.name,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.Navy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (conv.badge.isNotEmpty()) {
                                            Surface(shape = CircleShape, color = Color(0xFFEFF6FF)) {
                                                Text(
                                                    text = conv.badge,
                                                    color = Color(0xFF1D4ED8),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(text = conv.time, fontSize = 11.sp, color = FutaColors.Muted)
                                }

                                Spacer(Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conv.lastMessage,
                                        fontSize = 12.5.sp,
                                        color = if (conv.unreadCount > 0) FutaColors.Navy else FutaColors.Slate,
                                        fontWeight = if (conv.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (conv.unreadCount > 0) {
                                        Surface(shape = CircleShape, color = Color(0xFFFF8D28), modifier = Modifier.padding(start = 6.dp)) {
                                            Text(
                                                text = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // VIEW 2: ACTIVE CONVERSATION THREAD (Default for customer & guest, matching web)
        val listState = rememberLazyListState()
        var messageText by remember { mutableStateOf("") }
        val messages = remember {
            mutableStateListOf<ChatMessage>()
        }
        var isAiThinking by remember { mutableStateOf(false) }
        var aiPollJob by remember { mutableStateOf<Job?>(null) }
        val isCurrentConversationAi = isAiChat || activeConversationName.contains("AI") || targetAdvisorId.isNullOrEmpty()
        val isImeVisible = WindowInsets.isImeVisible
        LaunchedEffect(isImeVisible, messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        val activeTypingUser = activeConversationId?.let { typingUsers[it] }
        val showTypingIndicator = isAiThinking || !activeTypingUser.isNullOrEmpty()
        LaunchedEffect(showTypingIndicator, activeTypingUser) {
            if (showTypingIndicator && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size)
            }
        }

        LaunchedEffect(activeConversationId) {
            isAiThinking = false
            aiPollJob?.cancel()
            activeConversationId?.let { convId ->
                ChatWebSocketManager.shared.join(convId)
                ChatWebSocketManager.shared.markRead(convId)
                if (convId != "ai_agent") {
                    try {
                        val msgRes = APIClient.get().request("/chat/conversations/$convId/messages", query = mapOf("limit" to "50"))
                        val msgList = msgRes["data"].array
                        if (msgList.isNotEmpty()) {
                            messages.clear()
                            val currentUserId = AppSession.shared.user?.id.orEmpty()
                            val sorted = msgList.sortedBy { it["createdAt"].string }
                            for (m in sorted) {
                                val sType = m["senderType"].string
                                val senderId = m["senderId"].string
                                val isMe = isOwnChatMessage(senderId, sType, currentUserId, viewerIsCustomer = !isStaff)
                                val timeStr = formatChatTime(m["createdAt"].string)
                                val cards = parseChatPropertyCards(m["metadata"])
                                messages.add(
                                    ChatMessage(
                                        id = m.id,
                                        senderId = m["senderId"].string,
                                        senderName = if (isMe) "Tôi" else (if (sType == "bot") "Trợ lý AI FUTA Land" else "Tư vấn viên"),
                                        content = m["content"].string,
                                        isMe = isMe,
                                        time = timeStr,
                                        propertyCard = cards.firstOrNull(),
                                        propertyCards = cards
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            refreshUnreadBadge()
        }

        // Handle incoming WebSocket messages
        DisposableEffect(activeConversationId) {
            ChatWebSocketManager.shared.onNewMessage = { jsonMsg ->
                val convId = jsonMsg["conversationId"].string
                if (convId.isNotEmpty() && (convId == activeConversationId || activeConversationId == null || activeConversationId == "ai_agent")) {
                    val cards = parseChatPropertyCards(jsonMsg["metadata"])
                    // The gateway echoes our own messages back over the socket, so
                    // classify them as ours instead of attributing them to the other side.
                    val isMine = isOwnChatMessage(
                        senderId = jsonMsg["senderId"].string,
                        senderType = jsonMsg["senderType"].string,
                        currentUserId = AppSession.shared.user?.id.orEmpty(),
                        viewerIsCustomer = !isStaff
                    )
                    val newMsg = ChatMessage(
                        id = jsonMsg["id"].string.ifEmpty { nextLocalMessageId() },
                        senderId = jsonMsg["senderId"].string,
                        senderName = if (isMine) "Tôi" else jsonMsg["senderName"].string.ifEmpty { activeConversationName },
                        content = jsonMsg["content"].string,
                        isMe = isMine,
                        time = "Vừa xong",
                        propertyCard = cards.firstOrNull(),
                        propertyCards = cards
                    )
                    mergeIncomingChatMessage(messages, newMsg)
                    if (!isMine) {
                        isAiThinking = false
                        aiPollJob?.cancel()
                        scope.launch { refreshUnreadBadge() }
                    }
                    scope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            }
            onDispose {
                ChatWebSocketManager.shared.onNewMessage = null
            }
        }

        fun sendMessage(customText: String? = null) {
            val textToSend = (customText ?: messageText).trim()
            if (textToSend.isEmpty()) return
            val convId = activeConversationId ?: "ai_agent"
            val newMsg = ChatMessage(
                id = nextLocalMessageId(),
                senderId = "me",
                senderName = "Tôi",
                content = textToSend,
                isMe = true,
                time = "Bây giờ"
            )
            messages.add(newMsg)
            if (customText == null) messageText = ""
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }

            if (isCurrentConversationAi) {
                isAiThinking = true
                aiPollJob?.cancel()
                aiPollJob = scope.launch {
                    var attempts = 0
                    while (isAiThinking && attempts < 16) {
                        delay(2000)
                        attempts++
                        val currentConvId = activeConversationId
                        if (currentConvId != null && currentConvId != "ai_agent") {
                            try {
                                val msgRes = APIClient.get().request("/chat/conversations/$currentConvId/messages", query = mapOf("limit" to "10"))
                                val msgList = msgRes["data"].array
                                if (msgList.isNotEmpty()) {
                                    val currentUserId = AppSession.shared.user?.id.orEmpty()
                                    val latestBot = msgList.filter { m ->
                                        val sType = m["senderType"].string
                                        val sId = m["senderId"].string
                                        !isOwnChatMessage(sId, sType, currentUserId, viewerIsCustomer = !isStaff) && (sType == "bot" || sType == "staff")
                                    }.maxByOrNull { it["createdAt"].string }

                                    if (latestBot != null) {
                                        val cards = parseChatPropertyCards(latestBot["metadata"])
                                        val botMsg = ChatMessage(
                                            id = latestBot.id,
                                            senderId = latestBot["senderId"].string,
                                            senderName = if (latestBot["senderType"].string == "bot") "Trợ lý AI FUTA Land" else latestBot["senderName"].string.ifEmpty { activeConversationName },
                                            content = latestBot["content"].string,
                                            isMe = false,
                                            time = formatChatTime(latestBot["createdAt"].string),
                                            propertyCard = cards.firstOrNull(),
                                            propertyCards = cards
                                        )
                                        mergeIncomingChatMessage(messages, botMsg)
                                        isAiThinking = false
                                        refreshUnreadBadge()
                                        listState.animateScrollToItem(messages.size - 1)
                                        break
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    isAiThinking = false
                }
            }

            scope.launch {
                try {
                    var targetConvId = activeConversationId ?: "ai_agent"
                    if (targetConvId == "ai_agent") {
                        val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = conversationBody())
                        val newConv = createRes["data"]
                        if (!newConv.id.isEmpty()) {
                            targetConvId = newConv.id
                            activeConversationId = newConv.id
                            ChatWebSocketManager.shared.join(newConv.id)
                        }
                    }
                    if (ChatWebSocketManager.shared.isConnected.value) {
                        ChatWebSocketManager.shared.sendMessage(targetConvId, textToSend)
                    } else {
                        val escaped = textToSend.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        APIClient.get().request(
                            "/chat/conversations/$targetConvId/messages",
                            method = "POST",
                            bodyJson = "{\"content\":\"$escaped\"}"
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        Scaffold(
            topBar = {
                Surface(color = Color.White, shadowElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Always show back so a customer can return from the
                            // thread to the conversation list on the root tab.
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        if (onBack != null) {
                                            onBack.invoke()
                                        } else {
                                            activeConversationId = null
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (!isStaff && activeConversationName.contains("AI")) FutaColors.BrandGreen else FutaColors.MintBg,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!isStaff && activeConversationName.contains("AI")) {
                                        Icon(painterResource(R.drawable.ic_lucide_bot), null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text(activeConversationName.take(1).uppercase(), color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeConversationName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF22C55E), CircleShape))
                                    Text(
                                        text = if (isConnected) (if (!isStaff && activeConversationName.contains("AI")) "Trợ lý AI 24/7 • Sẵn sàng hỗ trợ" else "Tư vấn viên • Sẵn sàng hỗ trợ") else "Đang kết nối lại...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isConnected) Color(0xFF16A34A) else FutaColors.BrandOrange,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Switcher pills for customer to toggle between AI and all assigned advisors
                        val advisorConvs = conversations.filter { !it.isAi }
                        val aiConv = conversations.firstOrNull { it.isAi }
                        if (!isStaff && advisorConvs.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isAiSelected = activeConversationId == (aiConv?.id ?: "ai_agent") || activeConversationName.contains("AI")
                                Surface(
                                    shape = CircleShape,
                                    color = if (isAiSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                    modifier = Modifier.clickable {
                                        activeConversationId = aiConv?.id ?: "ai_agent"
                                        activeConversationName = "Trợ lý AI FUTA Land"
                                    }
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(painterResource(R.drawable.ic_lucide_bot), null, tint = if (isAiSelected) Color.White else FutaColors.Navy, modifier = Modifier.size(15.dp))
                                        Text("Trợ lý AI (24/7)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isAiSelected) Color.White else FutaColors.Navy)
                                    }
                                }

                                advisorConvs.forEach { advConv ->
                                    val isAdvisorSelected = activeConversationId == advConv.id
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isAdvisorSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                        modifier = Modifier.clickable {
                                            activeConversationId = advConv.id
                                            activeConversationName = advConv.name
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Person, null, tint = if (isAdvisorSelected) Color.White else FutaColors.Navy, modifier = Modifier.size(13.dp))
                                            Text("Sale: ${advConv.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isAdvisorSelected) Color.White else FutaColors.Navy)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The app is edge-to-edge on Android 15+, where `adjustResize`
                        // no longer shrinks the window, so the IME has to be padded
                        // manually — otherwise the keyboard covers the composer.
                        // `union` keeps the larger of the keyboard / navigation bar
                        // insets so the bar is not pushed up twice.
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .background(Color.White)
                ) {
                    // Quick Reply Suggestion Chips (Staff only, hidden for customer)
                    if (isStaff) {
                        val quickReplies = listOf(
                            "Tôi muốn xem căn này",
                            "Gửi bảng giá chi tiết",
                            "Tư vấn chính sách vay",
                            "Đặt lịch xem nhà thực tế"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickReplies.forEach { reply ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.clickable { sendMessage(reply) }
                                ) {
                                    Text(
                                        text = reply,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = FutaColors.Navy,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        color = Color.White,
                        border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable { ToastCenter.show("Tính năng gửi tệp/hình ảnh") }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, "Đính kèm", tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            FutaInput(
                                value = messageText,
                                onValueChange = {
                                    messageText = it
                                    activeConversationId?.let { convId ->
                                        ChatWebSocketManager.shared.sendTyping(convId)
                                    }
                                },
                                placeholder = if (isStaff) "Nhập tin nhắn tư vấn…" else "Nhập câu hỏi và nhu cầu",
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { sendMessage() })
                            )
                            Spacer(Modifier.width(8.dp))
                            val canSend = messageText.isNotBlank()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (canSend) FutaColors.BrandGreen else Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable(enabled = canSend) { sendMessage() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Gửi", tint = Color.White, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(FutaColors.PageBg)
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty() && !isStaff) {
                    val activeConv = conversations.find { it.id == activeConversationId }
                    val contextProjectName = activeConv?.contextProjectName.orEmpty()
                    val contextCode = activeConv?.contextCode.orEmpty()
                    val suggestions = buildList {
                        if (contextProjectName.isNotEmpty()) {
                            if (contextCode.isNotEmpty()) {
                                add("Căn $contextCode thuộc $contextProjectName còn chính sách ưu đãi nào?")
                            }
                            add("$contextProjectName còn những căn nào đang mở bán?")
                        } else {
                            add("Những dự án nào đang mở bán tại Đà Nẵng?")
                        }
                        add("Tư vấn bảng tính dòng tiền và lãi suất vay")
                        add("Chính sách ưu đãi và chiết khấu thanh toán")
                        add("Kết nối với tư vấn viên phụ trách")
                    }.take(4)
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lucide_bot),
                                    contentDescription = null,
                                    tint = FutaColors.BrandGreen,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = when {
                                        contextCode.isNotEmpty() -> "Trợ lý AI FUTA Land đang hỗ trợ căn $contextCode"
                                        contextProjectName.isNotEmpty() -> "Trợ lý AI FUTA Land đang hỗ trợ dự án $contextProjectName"
                                        else -> "Bạn đang trò chuyện với Trợ lý AI FUTA Land"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (contextProjectName.isNotEmpty()) {
                                        "Trợ lý AI sẵn sàng giải đáp 24/7 về $contextProjectName: bảng hàng, tiến độ mở bán, chính sách ưu đãi và dòng tiền."
                                    } else {
                                        "Trợ lý AI sẵn sàng giải đáp 24/7 về thông tin dự án, tiến độ mở bán và chính sách căn hộ."
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "CÂU HỎI GỢI Ý",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.BrandGreen
                                )
                                Spacer(Modifier.height(8.dp))
                                suggestions.forEach { prompt ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFFEAF8F1),
                                        border = BorderStroke(1.dp, Color(0xFF0E7643).copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { sendMessage(prompt) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = prompt,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = FutaColors.BrandGreen,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = FutaColors.BrandGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(messages, key = { _, msg -> msg.id }) { _, msg ->
                    MessageBubble(msg = msg, onOpenProperty = { id -> onNavigate(vn.futaland.app.navigation.FutaDestinations.propertyDetail(id, productContext)) })
                }

                if (showTypingIndicator) {
                    val displayName = when {
                        !activeTypingUser.isNullOrEmpty() -> activeTypingUser
                        isCurrentConversationAi -> "Trợ lý AI FUTA Land"
                        else -> activeConversationName
                    }
                    item(key = "typing_indicator_bubble") {
                        TypingIndicatorBubble(senderName = displayName)
                    }
                }
            }
        }
    }

    if (showingNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showingNewChatDialog = false },
            title = { Text("Tạo cuộc trò chuyện mới", fontWeight = FontWeight.Bold, color = FutaColors.Navy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập số điện thoại khách hàng để bắt đầu tư vấn:", fontSize = 13.sp, color = FutaColors.Slate)
                    FutaInput(
                        value = newCustomerPhone,
                        onValueChange = { newCustomerPhone = it },
                        placeholder = "Ví dụ: 0912345678",
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val phone = newCustomerPhone.trim()
                        if (phone.isNotEmpty()) {
                            scope.launch {
                                isCreatingConv = true
                                try {
                                    val res = APIClient.get().request(
                                        "/chat/conversations",
                                        method = "POST",
                                        bodyJson = """{"customerPhone":"$phone"}"""
                                    )
                                    val newConv = res["data"]
                                    if (!newConv.id.isEmpty()) {
                                        val cName = newConv["customer"]["customerName"].string.ifEmpty { phone }
                                        conversations.add(0, ConversationItem(newConv.id, cName, "Bắt đầu cuộc trò chuyện...", "Bây giờ", 0, true, false, ""))
                                        activeConversationId = newConv.id
                                        activeConversationName = cName
                                        showingNewChatDialog = false
                                        newCustomerPhone = ""
                                    }
                                } catch (e: Exception) {
                                    ToastCenter.show("Lỗi tạo hội thoại: ${e.message}")
                                } finally {
                                    isCreatingConv = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                    enabled = newCustomerPhone.isNotBlank() && !isCreatingConv
                ) {
                    Text(if (isCreatingConv) "Đang tạo…" else "Bắt đầu chat", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showingNewChatDialog = false }) {
                    Text("Hủy", color = FutaColors.Slate)
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onOpenProperty: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (msg.isMe) 14.dp else 2.dp,
                bottomEnd = if (msg.isMe) 2.dp else 14.dp
            ),
            color = if (msg.isMe) FutaColors.BrandGreen else Color.White,
            border = BorderStroke(1.dp, if (msg.isMe) Color.Transparent else FutaColors.LightBlueBorder),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!msg.isMe) {
                    Text(msg.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    Spacer(Modifier.height(3.dp))
                }
                val cards = msg.propertyCards.ifEmpty { listOfNotNull(msg.propertyCard) }
                for (card in cards) {
                    val title = card["title"].string.ifEmpty { "Căn hộ ${card["propertyCode"].string}" }
                    val projectName = card["zone"].string
                    val price = card["price"].double
                    val imgUrl = card["imageUrl"].string
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (msg.isMe) Color.White.copy(alpha = 0.15f) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (msg.isMe) Color.White.copy(alpha = 0.25f) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            TextButton(onClick = { onOpenProperty(card.id) }, enabled = card.id.isNotEmpty()) { Text("Xem căn") }
                            if (imgUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (msg.isMe) Color.White else FutaColors.Navy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (projectName.isNotEmpty()) {
                                Text(
                                    text = projectName,
                                    fontSize = 10.5.sp,
                                    color = if (msg.isMe) Color.White.copy(alpha = 0.8f) else Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (price > 0) {
                                val priceFormatted = "%,.0f đ".format(price).replace(",", ".")
                                Text(
                                    text = priceFormatted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.isMe) Color(0xFFFEF08A) else FutaColors.BrandGreen
                                )
                            }
                        }
                    }
                }
                Text(
                    text = msg.content,
                    fontSize = 13.5.sp,
                    color = if (msg.isMe) Color.White else FutaColors.Navy,
                    lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(msg.time, fontSize = 10.sp, color = FutaColors.Slate)
    }
}

@Composable
private fun TypingIndicatorBubble(senderName: String = "Trợ lý AI FUTA Land") {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 0),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 180),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = 2.dp,
                bottomEnd = 14.dp
            ),
            color = Color.White,
            border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.BrandGreen
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(FutaColors.BrandGreen.copy(alpha = dot1Alpha), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(FutaColors.BrandGreen.copy(alpha = dot2Alpha), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(FutaColors.BrandGreen.copy(alpha = dot3Alpha), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Đang soạn câu trả lời...",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}

private fun formatChatTime(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val instant = java.time.Instant.parse(dateStr)
        val zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh")
        val zonedDateTime = instant.atZone(zone)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        zonedDateTime.format(formatter)
    } catch (_: Exception) {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdf.parse(dateStr)
            val outSdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            outSdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            if (date != null) outSdf.format(date) else if (dateStr.length >= 16) dateStr.substring(11, 16) else dateStr
        } catch (_: Exception) {
            if (dateStr.length >= 16) dateStr.substring(11, 16) else dateStr
        }
    }
}

private fun formatChatDateTime(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val instant = java.time.Instant.parse(dateStr)
        val zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh")
        val zonedDateTime = instant.atZone(zone)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
        zonedDateTime.format(formatter)
    } catch (_: Exception) {
        dateStr.take(16).replace("T", " ")
    }
}

