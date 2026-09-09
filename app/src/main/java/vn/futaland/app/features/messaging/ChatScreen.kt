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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

data class ConversationItem(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = true
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
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    var activeConversationId by remember { mutableStateOf(initialConversationId) }
    var activeConversationName by remember { mutableStateOf("Trợ lý FUTA AI") }

    var search by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("all") }

    // Connect WebSocket on launch
    LaunchedEffect(Unit) {
        ChatWebSocketManager.shared.connect()
    }

    val isConnected by ChatWebSocketManager.shared.isConnected.collectAsState()
    val typingUsers by ChatWebSocketManager.shared.typingUsers.collectAsState()

    val conversations = remember {
        mutableStateListOf(
            ConversationItem("1", "Trợ lý ảo FUTA AI", "Dự án Times Square Đà Nẵng hiện đang mở bán các căn 2PN view biển.", "10:03", 1, true),
            ConversationItem("2", "Nguyễn Văn Tuấn (Môi giới FUTA)", "Em đã gửi bảng tính dòng tiền ngân hàng qua email cho anh rồi ạ.", "Hôm qua", 0, false),
            ConversationItem("3", "CSKH FUTA Land", "Hệ thống đã ghi nhận phiếu đặt cọc của quý khách.", "15/05", 0, true)
        )
    }
    if (activeConversationId.isNullOrEmpty()) {
        // VIEW 1: CONVERSATION LIST (Matching iOS ChatView.swift)
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
                                    Text(
                                        text = conv.name,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Navy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
        // VIEW 2: ACTIVE CONVERSATION THREAD (Matching iOS ChatConversationView)
        val listState = rememberLazyListState()
        var messageText by remember { mutableStateOf("") }
        val messages = remember {
            mutableStateListOf(
                ChatMessage("1", "bot", activeConversationName, "Xin chào quý khách! FUTA Land có thể hỗ trợ quý khách tìm căn hộ hoặc tính dòng tiền vay dự án nào ạ?", false, "10:00"),
                ChatMessage("2", "me", "Tôi", "Tôi đang quan tâm căn hộ 2 phòng ngủ dự án Times Square Đà Nẵng", true, "10:02"),
                ChatMessage("3", "bot", activeConversationName, "Dự án Times Square Đà Nẵng hiện đang mở bán các căn 2PN diện tích từ 68m² đến 74m², view biển Mỹ Khê với mức giá từ 3.2 Tỷ. Bạn có muốn nhận bảng tính lãi suất vay ngân hàng không?", false, "10:03")
            )
        }
        LaunchedEffect(activeConversationId) {
            activeConversationId?.let { convId ->
                ChatWebSocketManager.shared.join(convId)
                ChatWebSocketManager.shared.markRead(convId)
            }
        }

        // Handle incoming WebSocket messages
        DisposableEffect(activeConversationId) {
            ChatWebSocketManager.shared.onNewMessage = { jsonMsg ->
                val convId = jsonMsg["conversationId"].string
                if (convId == activeConversationId || activeConversationId == null) {
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
            val convId = activeConversationId ?: "1"
            ChatWebSocketManager.shared.sendMessage(convId, textToSend)
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
        }

        Scaffold(
            topBar = {
                Surface(color = Color.White, shadowElevation = 1.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            activeConversationId = null
                            onBack?.invoke()
                        }) {
                            Icon(Icons.Default.ArrowBack, null, tint = FutaColors.Navy)
                        }
                        Surface(
                            shape = CircleShape,
                            color = FutaColors.MintBg,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(activeConversationName.take(1), color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = activeConversationName,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy
                            )
                            Text(
                                text = if (isConnected) "Đang trực tuyến 24/7" else "Đang kết nối lại...",
                                fontSize = 11.sp,
                                color = if (isConnected) FutaColors.BrandGreen else FutaColors.BrandOrange
                            )
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

                    // Quick Reply Suggestion Chips
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
                                modifier = Modifier.weight(1f)
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(FutaColors.PageBg)
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
