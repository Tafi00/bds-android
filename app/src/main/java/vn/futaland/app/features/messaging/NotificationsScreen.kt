package vn.futaland.app.features.messaging

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

data class NotificationModel(
    val id: String,
    val title: String,
    val body: String,
    val time: String,
    val isRead: Boolean,
    val category: String,
    val categoryLabel: String,
    val categoryTextColor: Color,
    val categoryBgColor: Color,
    val icon: ImageVector,
    val iconColor: Color,
    val route: String? = null,
    val fullContent: String? = null,
    val imageUrl: String? = null,
    val externalLink: String? = null
)

private fun normalizeNotificationCategory(raw: String, route: String = "", title: String = "", body: String = ""): String {
    val low = raw.lowercase().trim()
    val rLow = route.lowercase().trim()
    val textLow = "$title $body".lowercase()

    if (low == "chat" || rLow.startsWith("/chat") || rLow.startsWith("chat.")) return "chat"
    if (low in listOf("leads", "crm", "customer", "customers") || rLow.startsWith("/crm") || rLow.startsWith("/customers") || textLow.contains("khách hàng") || textLow.contains("tư vấn")) return "leads"
    if (low in listOf("contracts", "contract") || rLow.startsWith("/contracts") || textLow.contains("hợp đồng")) return "contracts"
    if (low in listOf("holding", "transaction", "payment", "booking") || rLow.startsWith("/booking") || textLow.contains("giữ chỗ") || textLow.contains("đặt cọc") || textLow.contains("tiền cọc")) return "holding"
    if (low in listOf("listings", "listing") || rLow.startsWith("/listing") || rLow.startsWith("/my-listings") || textLow.contains("tin đăng") || textLow.contains("bất động sản")) return "listings"
    if (low.isNotEmpty() && low != "system" && low != "promotion") {
        return low
    }
    return "system"
}

private fun getCategoryVisuals(category: String): Pair<ImageVector, Pair<Color, Color>> {
    return when (category) {
        "chat" -> Icons.Default.Email to (Color(0xFF2563EB) to Color(0xFFEFF6FF))
        "leads" -> Icons.Default.Person to (Color(0xFF059669) to Color(0xFFECFDF5))
        "contracts" -> Icons.Default.Receipt to (Color(0xFF9333EA) to Color(0xFFFAF5FF))
        "holding" -> Icons.Default.Lock to (Color(0xFFD97706) to Color(0xFFFFFBEB))
        "listings" -> Icons.Default.Home to (Color(0xFF0D9488) to Color(0xFFF0FDFA))
        else -> Icons.Default.Notifications to (Color(0xFF475569) to Color(0xFFF1F5F9))
    }
}

private fun getCategoryDisplayLabel(category: String): String {
    return when (category) {
        "chat" -> "TIN NHẮN"
        "leads" -> "KHÁCH HÀNG"
        "contracts" -> "HỢP ĐỒNG"
        "holding" -> "GIỮ CHỖ & CỌC"
        "listings" -> "TIN ĐĂNG"
        else -> "HỆ THỐNG"
    }
}

private fun formatRelativeTimeString(raw: String): String {
    if (raw.isEmpty()) return "Vừa xong"
    return try {
        if (raw.length >= 16 && raw.contains("T")) {
            val datePart = raw.substring(0, 10)
            val timePart = raw.substring(11, 16)
            "$timePart $datePart"
        } else {
            raw.take(16)
        }
    } catch (_: Exception) {
        raw.take(16).ifEmpty { "Vừa xong" }
    }
}

private fun demoNotifications(): List<NotificationModel> = listOf(
    NotificationModel(
        id = "demo-1",
        title = "Cập nhật tiến độ dự án Times Square",
        body = "Dự án đã hoàn thành cất nóc phân khu A, chuẩn bị mở bán đợt 2.",
        time = "10 phút trước",
        isRead = false,
        category = "system",
        categoryLabel = "HỆ THỐNG",
        categoryTextColor = Color(0xFF475569),
        categoryBgColor = Color(0xFFF1F5F9),
        icon = Icons.Default.Notifications,
        iconColor = Color(0xFF475569)
    ),
    NotificationModel(
        id = "demo-2",
        title = "Xác nhận giữ chỗ căn hộ thành công",
        body = "Phiếu giữ chỗ mã LK4B-503 đã được hệ thống ERP ghi nhận.",
        time = "2 giờ trước",
        isRead = true,
        category = "holding",
        categoryLabel = "GIỮ CHỖ & CỌC",
        categoryTextColor = Color(0xFFD97706),
        categoryBgColor = Color(0xFFFFFBEB),
        icon = Icons.Default.Lock,
        iconColor = Color(0xFFD97706)
    ),
    NotificationModel(
        id = "demo-3",
        title = "Tin nhắn mới từ chuyên viên tư vấn",
        body = "Chào anh/chị, em đã gửi thông tin bảng hàng mới nhất cho anh/chị.",
        time = "Hôm qua",
        isRead = false,
        category = "chat",
        categoryLabel = "TIN NHẮN",
        categoryTextColor = Color(0xFF2563EB),
        categoryBgColor = Color(0xFFEFF6FF),
        icon = Icons.Default.Email,
        iconColor = Color(0xFF2563EB)
    ),
    NotificationModel(
        id = "demo-4",
        title = "Hợp đồng mua bán đã sẵn sàng ký",
        body = "Hợp đồng điện tử số HĐMB-2026/09 đã hoàn tất thẩm định và sẵn sàng ký duyệt.",
        time = "3 ngày trước",
        isRead = true,
        category = "contracts",
        categoryLabel = "HỢP ĐỒNG",
        categoryTextColor = Color(0xFF9333EA),
        categoryBgColor = Color(0xFFFAF5FF),
        icon = Icons.Default.Receipt,
        iconColor = Color(0xFF9333EA)
    )
)

private fun parseNotification(item: JSONValue): NotificationModel {
    val targetRoute = item["route"].string.ifEmpty { item["link"].string.ifEmpty { item["data"]["route"].string } }.takeIf { it.isNotEmpty() }
    val rawCat = item["category"].string.ifEmpty { item["type"].string.ifEmpty { "system" } }
    val itemTitle = item["title"].string.ifEmpty { "Thông báo hệ thống" }
    val itemBody = item["body"].string.ifEmpty { item["message"].string.ifEmpty { item["content"].string } }

    val normalizedCat = normalizeNotificationCategory(
        raw = rawCat,
        route = targetRoute ?: "",
        title = itemTitle,
        body = itemBody
    )
    val (icon, colors) = getCategoryVisuals(normalizedCat)
    val catLabel = getCategoryDisplayLabel(normalizedCat)

    return NotificationModel(
        id = item["id"].string.ifEmpty { item["_id"].string },
        title = itemTitle,
        body = itemBody,
        time = formatRelativeTimeString(item["createdAt"].string),
        isRead = item["isRead"].bool || item["read"].bool || !item["readAt"].isNull,
        category = normalizedCat,
        categoryLabel = catLabel,
        categoryTextColor = colors.first,
        categoryBgColor = colors.second,
        icon = icon,
        iconColor = colors.first,
        route = targetRoute,
        fullContent = item["fullContent"].string.takeIf { it.isNotEmpty() },
        imageUrl = item["imageUrl"].string.takeIf { it.isNotEmpty() },
        externalLink = item["externalLink"].string.takeIf { it.isNotEmpty() }
    )
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("all") }
    var notifications by remember { mutableStateOf<List<NotificationModel>>(emptyList()) }
    var serverUnreadCount by remember { mutableIntStateOf(0) }
    var serverUnreadByCategory by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selectedNotification by remember { mutableStateOf<NotificationModel?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var mutating by remember { mutableStateOf(false) }

    // iOS parity: search, read filter, sort — all server-side.
    var searchQuery by remember { mutableStateOf("") }
    var readFilter by remember { mutableStateOf("all") }
    var sortOrder by remember { mutableStateOf("newest") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<NotificationModel?>(null) }
    var menuForItemId by remember { mutableStateOf<String?>(null) }

    val pageSize = 20
    val hasMore = notifications.size < total

    suspend fun fetchPage(requestedPage: Int, append: Boolean) {
        try {
            val category = if (selectedCategory == "all" || selectedCategory == "unread") "all" else selectedCategory
            val read = when {
                selectedCategory == "unread" -> "unread"
                else -> readFilter
            }
            val query = mutableMapOf(
                "page" to requestedPage.toString(),
                "limit" to pageSize.toString(),
                "category" to category,
                "read" to read,
                "sort" to sortOrder
            )
            if (searchQuery.isNotBlank()) query["q"] = searchQuery.trim()
            val res = APIClient.get().request("/notifications", query = query)
            val dataObj = res["data"]
            val array = dataObj["items"].array.ifEmpty { dataObj.array.ifEmpty { res["notifications"].array } }

            serverUnreadCount = dataObj["unreadCount"].int
            total = dataObj["total"].int
            val categoryMap = mutableMapOf<String, Int>()
            val catCountsObj = dataObj["unreadCountByCategory"]
            listOf("chat", "leads", "contracts", "holding", "listings", "system").forEach { cKey ->
                val cnt = catCountsObj[cKey].int
                if (cnt > 0) categoryMap[cKey] = cnt
            }
            serverUnreadByCategory = categoryMap

            val parsed = array.map { parseNotification(it) }
            notifications = if (append) {
                val existing = notifications.map { it.id }.toSet()
                notifications + parsed.filter { it.id !in existing }
            } else {
                parsed.ifEmpty { if (requestedPage == 1 && searchQuery.isBlank() && selectedCategory == "all" && readFilter == "all") demoNotifications() else emptyList() }
            }
        } catch (_: Exception) {
            if (!append) notifications = demoNotifications()
        }
    }

    fun loadNotifications() {
        scope.launch {
            loading = true
            page = 1
            fetchPage(1, append = false)
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        scope.launch {
            loadingMore = true
            page += 1
            fetchPage(page, append = true)
            loadingMore = false
        }
    }

    fun setRead(item: NotificationModel, isRead: Boolean) {
        notifications = notifications.map { if (it.id == item.id) it.copy(isRead = isRead) else it }
        if (isRead) {
            if (serverUnreadCount > 0) serverUnreadCount--
            val currentCatCount = serverUnreadByCategory[item.category] ?: 0
            if (currentCatCount > 0) {
                serverUnreadByCategory = serverUnreadByCategory + (item.category to currentCatCount - 1)
            }
        } else {
            serverUnreadCount++
            serverUnreadByCategory = serverUnreadByCategory + (item.category to (serverUnreadByCategory[item.category] ?: 0) + 1)
        }
        if (item.id.startsWith("demo-")) return
        scope.launch {
            try {
                APIClient.get().request("/notifications/${item.id}/read", method = "PATCH", bodyJson = "{\"isRead\":$isRead}")
            } catch (_: Exception) {}
        }
    }

    fun openItem(item: NotificationModel) {
        if (!item.isRead) setRead(item, true)
        val route = item.route
        if (route.isNullOrEmpty() || route == "/notifications" || route == "notifications") {
            selectedNotification = item
        } else {
            onNavigate(route)
        }
    }

    fun deleteItem(item: NotificationModel) {
        mutating = true
        notifications = notifications.filter { it.id != item.id }
        if (!item.isRead) {
            if (serverUnreadCount > 0) serverUnreadCount--
            val currentCatCount = serverUnreadByCategory[item.category] ?: 0
            if (currentCatCount > 0) {
                serverUnreadByCategory = serverUnreadByCategory + (item.category to currentCatCount - 1)
            }
        }
        total = maxOf(0, total - 1)
        scope.launch {
            if (!item.id.startsWith("demo-")) {
                try {
                    APIClient.get().request("/notifications/${item.id}", method = "DELETE")
                    ToastCenter.show("Đã xóa thông báo")
                } catch (e: Exception) {
                    ToastCenter.show("Không xóa được thông báo: ${e.message}", isError = true)
                }
            } else {
                ToastCenter.show("Đã xóa thông báo")
            }
            mutating = false
        }
    }

    fun clearAll() {
        mutating = true
        scope.launch {
            try {
                APIClient.get().request("/notifications", method = "DELETE")
                notifications = emptyList()
                serverUnreadCount = 0
                serverUnreadByCategory = emptyMap()
                total = 0
                ToastCenter.show("Đã xóa toàn bộ thông báo")
            } catch (e: Exception) {
                ToastCenter.show("Không xóa được: ${e.message}", isError = true)
            }
            mutating = false
        }
    }

    fun markAllRead() {
        scope.launch {
            try {
                APIClient.get().request("/notifications/read-all", method = "POST")
            } catch (_: Exception) {}
            notifications = notifications.map { it.copy(isRead = true) }
            serverUnreadCount = 0
            serverUnreadByCategory = emptyMap()
            ToastCenter.show("Đã đánh dấu tất cả là đã đọc")
        }
    }

    // Debounced server-side reload on any filter/search change (iOS parity: q param).
    LaunchedEffect(searchQuery, selectedCategory, readFilter, sortOrder) {
        delay(300)
        loadNotifications()
    }
    LaunchedEffect(Unit) {
        FutaMessagingService.inboxRevision.drop(1).collect { loadNotifications() }
    }

    // iOS parity: reload when the app returns to foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) loadNotifications()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    fun getCategoryUnreadCount(catKey: String): Int {
        if (catKey == "unread") {
            val count = if (serverUnreadCount > 0) serverUnreadCount else notifications.count { !it.isRead }
            return count
        }
        return serverUnreadByCategory[catKey] ?: notifications.count { !it.isRead && it.category == catKey }
    }

    val activeFilterCount = (if (readFilter != "all") 1 else 0) + (if (sortOrder != "newest") 1 else 0)

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = FutaColors.Navy)
                        }
                        Text(
                            text = "Thông báo",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                        // Filter (iOS parity: slider sheet)
                        IconButton(onClick = { showFilterSheet = true }) {
                            Box {
                                Icon(Icons.Default.FilterList, "Bộ lọc", tint = FutaColors.Navy, modifier = Modifier.size(20.dp))
                                if (activeFilterCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .background(FutaColors.BrandGreen, CircleShape)
                                    )
                                }
                            }
                        }
                        // Settings (iOS parity: per-category push preferences)
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, "Cài đặt thông báo", tint = FutaColors.Navy, modifier = Modifier.size(20.dp))
                        }
                        // Overflow menu (iOS parity: mark-all-read / clear-all)
                        Box {
                            IconButton(onClick = { menuForItemId = "topbar" }) {
                                Icon(Icons.Default.MoreVert, "Tùy chọn", tint = FutaColors.Navy, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = menuForItemId == "topbar",
                                onDismissRequest = { menuForItemId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Đánh dấu tất cả đã đọc") },
                                    enabled = serverUnreadCount > 0 && !mutating,
                                    onClick = {
                                        menuForItemId = null
                                        markAllRead()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Xóa toàn bộ", color = Color(0xFFDC2626)) },
                                    enabled = notifications.isNotEmpty() && !mutating,
                                    onClick = {
                                        menuForItemId = null
                                        showClearConfirm = true
                                    }
                                )
                            }
                        }
                    }

                    // Search bar (iOS parity: server-side q)
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FutaInput(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Tìm theo tiêu đề hoặc nội dung…",
                            leadingIcon = Icons.Default.Search,
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Xóa",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { searchQuery = "" },
                                        tint = FutaColors.Slate
                                    )
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 8 Standardized Category Tabs matching Web & iOS
                    val categories = listOf(
                        "all" to "Tất cả",
                        "unread" to "Chưa đọc",
                        "chat" to "Tin nhắn",
                        "leads" to "Khách hàng",
                        "contracts" to "Hợp đồng",
                        "holding" to "Giữ chỗ & Cọc",
                        "listings" to "Tin đăng",
                        "system" to "Hệ thống"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { (catKey, catLabel) ->
                            val isSelected = selectedCategory == catKey
                            val badgeCount = if (catKey == "all") 0 else getCategoryUnreadCount(catKey)

                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { selectedCategory = catKey }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = catLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else FutaColors.Navy
                                    )
                                    if (badgeCount > 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) Color.White.copy(alpha = 0.25f) else if (catKey == "unread") Color(0xFFFEF3C7) else Color(0xFFD1FAE5)
                                        ) {
                                            Text(
                                                text = "$badgeCount",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else if (catKey == "unread") Color(0xFF92400E) else Color(0xFF065F46),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
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
    ) { padding ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) {
                    FutaSkeletonBlock(height = 90.dp, radius = 14.dp)
                }
            }
        } else if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                FutaEmptyState(
                    title = if (searchQuery.isNotBlank() || selectedCategory != "all" || readFilter != "all") "Không tìm thấy thông báo phù hợp" else "Chưa có thông báo nào",
                    message = if (searchQuery.isNotBlank() || selectedCategory != "all" || readFilter != "all") "Thử đổi từ khóa hoặc chọn tab danh mục khác." else "Các cập nhật về dự án, tin nhắn, hợp đồng và ưu đãi sẽ hiển thị tại đây."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FutaColors.PageBg)
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(notifications, key = { idx, item -> "$idx-${item.id}" }) { _, item ->
                    Box {
                        FutaCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (item.isRead) FutaColors.LightBlueBorder else FutaColors.BrandGreen.copy(alpha = 0.5f),
                            onClick = { openItem(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = item.iconColor.copy(alpha = 0.12f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(item.icon, null, tint = item.iconColor, modifier = Modifier.size(19.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Category Pill Tag
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = item.categoryBgColor,
                                            border = BorderStroke(0.5.dp, item.categoryTextColor.copy(alpha = 0.25f))
                                        ) {
                                            Text(
                                                text = item.categoryLabel,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = item.categoryTextColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.time,
                                                fontSize = 11.sp,
                                                color = FutaColors.Muted
                                            )
                                            if (!item.isRead) {
                                                Spacer(Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(FutaColors.BrandGreen, CircleShape)
                                                )
                                            }
                                            Spacer(Modifier.width(2.dp))
                                            IconButton(
                                                onClick = { menuForItemId = item.id },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.MoreVert, "Tùy chọn", tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = item.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.Bold,
                                        color = FutaColors.Navy
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = item.body,
                                        fontSize = 12.sp,
                                        color = FutaColors.Slate,
                                        lineHeight = 17.sp
                                    )
                                    if (item.route != null || item.fullContent != null || item.imageUrl != null || item.externalLink != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Xem chi tiết",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = FutaColors.BrandGreen
                                            )
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                null,
                                                tint = FutaColors.BrandGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = menuForItemId == item.id,
                            onDismissRequest = { menuForItemId = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (item.isRead) "Đánh dấu chưa đọc" else "Đánh dấu đã đọc") },
                                onClick = {
                                    menuForItemId = null
                                    setRead(item, !item.isRead)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa thông báo", color = Color(0xFFDC2626)) },
                                onClick = {
                                    menuForItemId = null
                                    deletingItem = item
                                }
                            )
                        }
                    }
                }

                // Pagination (iOS parity: loadMore)
                if (hasMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            FutaButton(
                                text = if (loadingMore) "Đang tải…" else "Xem thêm (${notifications.size}/$total)",
                                variant = FutaButtonVariant.OUTLINE,
                                enabled = !loadingMore,
                                onClick = { loadMore() }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Filter sheet (iOS parity: read state + sort order)
    if (showFilterSheet) {
        var draftRead by remember { mutableStateOf(readFilter) }
        var draftSort by remember { mutableStateOf(sortOrder) }
        FutaBottomSheet(
            visible = true,
            onDismiss = { showFilterSheet = false },
            title = "Bộ lọc thông báo",
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FutaButton(
                        text = "Đặt lại",
                        variant = FutaButtonVariant.OUTLINE,
                        onClick = {
                            draftRead = "all"
                            draftSort = "newest"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FutaButton(
                        text = "Áp dụng",
                        variant = FutaButtonVariant.PRIMARY,
                        onClick = {
                            readFilter = draftRead
                            sortOrder = draftSort
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text("TRẠNG THÁI ĐỌC", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "Tất cả", "unread" to "Chưa đọc", "read" to "Đã đọc").forEach { (key, label) ->
                        val sel = draftRead == key
                        Surface(
                            shape = CircleShape,
                            color = if (sel) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { draftRead = key }
                        ) {
                            Text(label, fontSize = 12.5.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) Color.White else FutaColors.Navy, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
                Text("SẮP XẾP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("newest" to "Mới nhất", "oldest" to "Cũ nhất").forEach { (key, label) ->
                        val sel = draftSort == key
                        Surface(
                            shape = CircleShape,
                            color = if (sel) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { draftSort = key }
                        ) {
                            Text(label, fontSize = 12.5.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) Color.White else FutaColors.Navy, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }

    // Settings sheet (iOS parity: per-category push preferences on this device)
    if (showSettingsSheet) {
        NotificationSettingsSheet(
            deviceId = FcmRegistrar.deviceId(context),
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Delete single notification confirm (iOS parity)
    deletingItem?.let { item ->
        FutaDialog(
            visible = true,
            onDismiss = { deletingItem = null },
            title = "Xóa thông báo?",
            confirmText = "Xóa",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                deletingItem = null
                deleteItem(item)
            },
            cancelText = "Hủy",
            onCancel = { deletingItem = null }
        ) {
            Text("Thông báo \"${item.title}\" sẽ bị xóa vĩnh viễn. Thao tác này không thể hoàn tác.", fontSize = 13.sp, color = FutaColors.Slate)
        }
    }

    // Clear all confirm (iOS parity)
    if (showClearConfirm) {
        FutaDialog(
            visible = true,
            onDismiss = { showClearConfirm = false },
            title = "Xóa toàn bộ thông báo?",
            confirmText = "Xóa tất cả",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                showClearConfirm = false
                clearAll()
            },
            cancelText = "Hủy",
            onCancel = { showClearConfirm = false }
        ) {
            Text("Toàn bộ thông báo trong hộp thư sẽ bị xóa vĩnh viễn, kể cả các thông báo ngoài bộ lọc hiện tại.", fontSize = 13.sp, color = FutaColors.Slate)
        }
    }
    NotificationDetailDialog(
        notification = selectedNotification,
        onDismiss = { selectedNotification = null },
        onNavigate = onNavigate
    )
}

@Composable
private fun NotificationSettingsSheet(
    deviceId: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) {
        try {
            val res = APIClient.get().request("/devices/$deviceId/preferences")
            val p = res["data"]["preferences"]
            prefs = mapOf(
                "chat" to p["chat"].bool,
                "leads" to p["leads"].bool,
                "contracts" to p["contracts"].bool,
                "listings" to p["listings"].bool
            )
        } catch (_: Exception) {
            loadError = true
        }
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Cài đặt thông báo"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (prefs == null && !loadError) {
                FutaSkeletonBlock(height = 44.dp, radius = 10.dp)
                FutaSkeletonBlock(height = 44.dp, radius = 10.dp)
                FutaSkeletonBlock(height = 44.dp, radius = 10.dp)
                FutaSkeletonBlock(height = 44.dp, radius = 10.dp)
            } else if (loadError) {
                Text(
                    "Không tải được cài đặt thiết bị. Thiết bị có thể chưa đăng ký push.",
                    fontSize = 13.sp,
                    color = FutaColors.Slate
                )
            } else {
                listOf(
                    "chat" to "Tin nhắn trò chuyện",
                    "leads" to "Khách hàng & Lead",
                    "contracts" to "Hợp đồng",
                    "listings" to "Tin đăng & Sản phẩm"
                ).forEach { (key, label) ->
                    val enabled = prefs?.get(key) ?: true
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy)
                        Switch(
                            checked = enabled,
                            enabled = !saving,
                            onCheckedChange = { checked ->
                                val current = prefs ?: return@Switch
                                prefs = current + (key to checked)
                                saving = true
                                scope.launch {
                                    try {
                                        val body = buildString {
                                            append("{\"preferences\":{")
                                            append(current.entries.joinToString(",") { (k, v) -> "\"$k\":${if (k == key) checked else v}" })
                                            append("}}")
                                        }
                                        APIClient.get().request("/devices/$deviceId/preferences", method = "PATCH", bodyJson = body)
                                    } catch (e: Exception) {
                                        prefs = current
                                        ToastCenter.show("Không lưu được cài đặt: ${e.message}", isError = true)
                                    }
                                    saving = false
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = FutaColors.BrandGreen)
                        )
                    }
                }
                Text(
                    "Tắt một danh mục sẽ ngừng nhận push cho danh mục đó trên thiết bị này.",
                    fontSize = 11.5.sp,
                    color = FutaColors.Slate
                )
            }
        }
    }
}

@Composable
fun NotificationDetailDialog(
    notification: NotificationModel?,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    if (notification == null) return
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Category icon & Badge + Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = notification.iconColor.copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(notification.icon, null, tint = notification.iconColor, modifier = Modifier.size(17.dp))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = notification.categoryBgColor,
                            border = BorderStroke(0.5.dp, notification.categoryTextColor.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = notification.categoryLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = notification.categoryTextColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = FutaColors.Slate, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Title
                Text(
                    text = notification.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = notification.time,
                    fontSize = 11.5.sp,
                    color = FutaColors.Muted
                )

                Spacer(Modifier.height(12.dp))

                // Optional Image
                if (!notification.imageUrl.isNullOrEmpty()) {
                    coil3.compose.AsyncImage(
                        model = notification.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Body / Full content Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notification.fullContent ?: notification.body,
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Actions: External link / Navigate / Dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!notification.externalLink.isNullOrEmpty()) {
                        FutaButton(
                            text = "Mở liên kết",
                            variant = FutaButtonVariant.OUTLINE,
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notification.externalLink)))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    val r = notification.route
                    if (!r.isNullOrEmpty() && r != "/notifications" && r != "notifications") {
                        FutaButton(
                            text = "Đi đến mục này",
                            variant = FutaButtonVariant.PRIMARY,
                            onClick = {
                                onDismiss()
                                onNavigate(r)
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    FutaButton(
                        text = "Đóng",
                        variant = FutaButtonVariant.OUTLINE,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
