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
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
data class ConversationItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = true,
    val isAi: Boolean = false,
    val badge: String = ""
)

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val isMe: Boolean,
    val time: String
)

@Composable
fun ChatScreen(
    initialConversationId: String? = null,
    targetAdvisorId: String? = null,
    targetAdvisorName: String? = null,
    targetApartmentId: String? = null,
    isAiChat: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val isStaff = AppSession.shared.role != "customer" && AppSession.shared.role != "guest"

    var activeConversationId by remember { mutableStateOf(if (!isStaff) (initialConversationId ?: "ai_agent") else initialConversationId) }
    var activeConversationName by remember { mutableStateOf(targetAdvisorName ?: (if (isAiChat) "Trợ lý AI FUTA Land" else "Trợ lý AI FUTA Land")) }

    var search by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("all") }

    val conversations = remember {
        mutableStateListOf<ConversationItem>()
    }

    // Ensure guest session & connect WebSocket on launch
    LaunchedEffect(Unit) {
        if (!AppSession.shared.isAuthenticated) {
            AppSession.shared.ensureGuest()
        }
        ChatWebSocketManager.shared.connect()
        try {
            val res = APIClient.get().request("/chat/conversations", query = mapOf("limit" to "50"))
            val list = res["data"].array
            val isStaff = AppSession.shared.role != "customer" && AppSession.shared.role != "guest"

            conversations.clear()
            for (c in list) {
                val advId = c["advisorId"].string
                val isAi = !isStaff && advId.isEmpty()
                val name = if (isStaff) {
                    val cName = c["customer"]["customerName"].string
                    cName.ifEmpty { c["customerPhone"].string.ifEmpty { "Khách hàng" } }
                } else {
                    if (isAi) "Trợ lý AI FUTA Land" else c["advisor"]["name"].string.ifEmpty { "Sale phụ trách điều phối" }
                }
                val badge = if (isStaff) "" else (if (isAi) "Trợ lý AI" else "Sale phụ trách")
                val lastMsg = c["lastMessageContent"].string.ifEmpty { "Bắt đầu cuộc trò chuyện..." }
                val time = c["lastMessageAt"].string.take(16).replace("T", " ")
                conversations.add(ConversationItem(c.id, name, lastMsg, time, 0, true, isAi, badge))
            }

            if (!targetAdvisorId.isNullOrEmpty()) {
                val existing = list.find { it["advisorId"].string == targetAdvisorId }
                if (existing != null) {
                    activeConversationId = existing.id
                    activeConversationName = targetAdvisorName ?: existing["advisor"]["name"].string.ifEmpty { "Tư vấn viên" }
                } else {
                    val body = """{"advisorId":"$targetAdvisorId"${if (!targetApartmentId.isNullOrEmpty()) ""","apartmentId":"$targetApartmentId"""" else ""}}"""
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
                    val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = "{}")
                    val newConv = createRes["data"]
                    if (!newConv.id.isEmpty()) {
                        conversations.add(0, ConversationItem(newConv.id, "Trợ lý AI FUTA Land", "Bắt đầu cuộc trò chuyện...", "Bây giờ", 0, true, true, "Trợ lý AI"))
                        activeConversationId = newConv.id
                        activeConversationName = "Trợ lý AI FUTA Land"
                    }
                }
            } else if (!isStaff && conversations.isEmpty()) {
                val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = "{}")
                val newConv = createRes["data"]
                if (!newConv.id.isEmpty()) {
                    conversations.add(ConversationItem(newConv.id, "Trợ lý AI FUTA Land", "Bắt đầu cuộc trò chuyện...", "Bây giờ", 0, true, true, "Trợ lý AI"))
                    activeConversationId = newConv.id
                    activeConversationName = "Trợ lý AI FUTA Land"
                }
            } else if (!isStaff && (activeConversationId.isNullOrEmpty() || activeConversationId == "ai_agent")) {
                val aiConv = conversations.find { it.isAi } ?: conversations.firstOrNull()
                if (aiConv != null) {
                    activeConversationId = aiConv.id
                    activeConversationName = aiConv.name
                }
            }
        } catch (_: Exception) {}
    }

    val isConnected by ChatWebSocketManager.shared.isConnected.collectAsState()
    val typingUsers by ChatWebSocketManager.shared.typingUsers.collectAsState()

    if (isStaff && activeConversationId.isNullOrEmpty()) {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hộp thư & Trò chuyện",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
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
                        listOf("all" to "Tất cả", "unread" to "Chưa đọc").forEach { (tab, label) ->
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
                                            color = FutaColors.BrandGreen,
                                            modifier = Modifier.padding(start = 6.dp)
                                        ) {
                                            Text(
                                                text = conv.unreadCount.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
        val isImeVisible = WindowInsets.isImeVisible
        LaunchedEffect(isImeVisible, messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        LaunchedEffect(activeConversationId) {
            activeConversationId?.let { convId ->
                ChatWebSocketManager.shared.join(convId)
                ChatWebSocketManager.shared.markRead(convId)
                if (convId != "ai_agent") {
                    try {
                        val msgRes = APIClient.get().request("/chat/conversations/$convId/messages", query = mapOf("limit" to "50"))
                        val msgList = msgRes["data"].array
                        if (msgList.isNotEmpty()) {
                            messages.clear()
                            for (m in msgList.reversed()) {
                                val sType = m["senderType"].string
                                val isMe = sType == "customer"
                                val timeStr = m["createdAt"].string.take(16).replace("T", " ")
                                messages.add(
                                    ChatMessage(
                                        id = m.id,
                                        senderId = m["senderId"].string,
                                        senderName = if (isMe) "Tôi" else (if (sType == "bot") "Trợ lý AI FUTA Land" else "Tư vấn viên"),
                                        content = m["content"].string,
                                        isMe = isMe,
                                        time = timeStr
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Handle incoming WebSocket messages
        DisposableEffect(activeConversationId) {
            ChatWebSocketManager.shared.onNewMessage = { jsonMsg ->
                val convId = jsonMsg["conversationId"].string
                if (convId == activeConversationId || activeConversationId == null || activeConversationId == "ai_agent") {
                    val newMsg = ChatMessage(
                        id = jsonMsg["id"].string.ifEmpty { System.currentTimeMillis().toString() },
                        senderId = jsonMsg["senderId"].string,
                        senderName = jsonMsg["senderName"].string.ifEmpty { activeConversationName },
                        content = jsonMsg["content"].string,
                        isMe = false,
                        time = "Vừa xong"
                    )
                    messages.add(newMsg)
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
                id = System.currentTimeMillis().toString(),
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

            scope.launch {
                try {
                    var targetConvId = activeConversationId ?: "ai_agent"
                    if (targetConvId == "ai_agent") {
                        val createRes = APIClient.get().request("/chat/conversations", method = "POST", bodyJson = "{}")
                        val newConv = createRes["data"]
                        if (!newConv.id.isEmpty()) {
                            targetConvId = newConv.id
                            activeConversationId = newConv.id
                            ChatWebSocketManager.shared.join(newConv.id)
                        }
                    }
                    ChatWebSocketManager.shared.sendMessage(targetConvId, textToSend)
                    val escaped = textToSend.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    APIClient.get().request(
                        "/chat/conversations/$targetConvId/messages",
                        method = "POST",
                        bodyJson = "{\"content\":\"$escaped\"}"
                    )
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
                            if (isStaff || onBack != null) {
                                IconButton(onClick = {
                                    activeConversationId = null
                                    onBack?.invoke()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = FutaColors.Navy)
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = FutaColors.MintBg,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!isStaff && activeConversationName.contains("AI")) {
                                        Icon(painterResource(R.drawable.ic_lucide_bot), null, tint = FutaColors.BrandGreen, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text(activeConversationName.take(1).uppercase(), color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeConversationName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                Text(
                                    text = if (isConnected) (if (!isStaff && activeConversationName.contains("AI")) "Trực tuyến 24/7 · Tư vấn tự động" else "Đang trực tuyến") else "Đang kết nối lại...",
                                    fontSize = 11.5.sp,
                                    color = if (isConnected) FutaColors.BrandGreen else FutaColors.BrandOrange
                                )
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
                        .navigationBarsPadding()
                        .background(Color.White)
                ) {
                    // Typing Indicator if active
                    val typingUser = activeConversationId?.let { typingUsers[it] }
                    if (!typingUser.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$typingUser đang soạn tin nhắn...",
                                fontSize = 11.5.sp,
                                color = FutaColors.BrandGreen,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }

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
                            IconButton(
                                onClick = {
                                    ToastCenter.show("Tính năng gửi tệp/hình ảnh")
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, "Đính kèm", tint = FutaColors.Navy)
                            }
                            Spacer(Modifier.width(4.dp))
                            FutaInput(
                                value = messageText,
                                onValueChange = {
                                    messageText = it
                                    activeConversationId?.let { convId ->
                                        ChatWebSocketManager.shared.sendTyping(convId)
                                    }
                                },
                                placeholder = "Nhập tin nhắn tư vấn...",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { sendMessage() })
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { sendMessage() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(FutaColors.BrandGreen, CircleShape)
                            ) {
                                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
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
                if (messages.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = FutaColors.MintBg,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_lucide_bot),
                                            contentDescription = null,
                                            tint = FutaColors.BrandGreen,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Xin chào quý khách!",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Tôi là Trợ lý AI của FUTA Land. Tôi có thể giải đáp thông tin các dự án, căn hộ đang mở bán, chính sách chiết khấu và tính lãi suất vay 24/7.",
                                    fontSize = 13.sp,
                                    color = FutaColors.Slate,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "CÂU HỎI GỢI Ý",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.BrandGreen
                                )
                                Spacer(Modifier.height(8.dp))
                                val suggestions = listOf(
                                    "Dự án Times Square Đà Nẵng có những căn nào?",
                                    "Tư vấn bảng tính dòng tiền và lãi suất vay",
                                    "Chính sách ưu đãi và chiết khấu thanh toán",
                                    "Kết nối với tư vấn viên phụ trách"
                                )
                                suggestions.forEach { prompt ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { sendMessage(prompt) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = prompt,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = FutaColors.Navy,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = FutaColors.BrandGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(messages, key = { _, msg -> msg.id }) { _, msg ->
                    MessageBubble(msg = msg)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
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
