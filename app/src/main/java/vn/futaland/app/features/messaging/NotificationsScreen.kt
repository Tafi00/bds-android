package vn.futaland.app.features.messaging

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("all") }
    var notifications by remember { mutableStateOf<List<NotificationModel>>(emptyList()) }
    var serverUnreadCount by remember { mutableIntStateOf(0) }
    var serverUnreadByCategory by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selectedNotification by remember { mutableStateOf<NotificationModel?>(null) }

    fun loadNotifications() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/notifications?limit=50")
                val dataObj = res["data"]
                val array = dataObj["items"].array.ifEmpty { dataObj.array.ifEmpty { res["notifications"].array } }

                serverUnreadCount = dataObj["unreadCount"].int
                val categoryMap = mutableMapOf<String, Int>()
                val catCountsObj = dataObj["unreadCountByCategory"]
                listOf("chat", "leads", "contracts", "holding", "listings", "system").forEach { cKey ->
                    val cnt = catCountsObj[cKey].int
                    if (cnt > 0) categoryMap[cKey] = cnt
                }
                serverUnreadByCategory = categoryMap

                if (array.isNotEmpty()) {
                    notifications = array.map { item ->
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

                        NotificationModel(
                            id = item["id"].string.ifEmpty { item["_id"].string },
                            title = itemTitle,
                            body = itemBody,
                            time = formatRelativeTimeString(item["createdAt"].string),
                            isRead = item["isRead"].bool || item["read"].bool,
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
                } else {
                    notifications = listOf(
                        NotificationModel(
                            id = "1",
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
                            id = "2",
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
                            id = "3",
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
                            id = "4",
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
                }
            } catch (_: Exception) {
                notifications = listOf(
                    NotificationModel(
                        id = "1",
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
                        id = "2",
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
                        id = "3",
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
                    )
                )
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNotifications()
    }

    fun getCategoryUnreadCount(catKey: String): Int {
        if (catKey == "unread") {
            val count = if (serverUnreadCount > 0) serverUnreadCount else notifications.count { !it.isRead }
            return count
        }
        return serverUnreadByCategory[catKey] ?: notifications.count { !it.isRead && it.category == catKey }
    }

    val filteredNotifications = remember(notifications, selectedCategory) {
        when (selectedCategory) {
            "all" -> notifications
            "unread" -> notifications.filter { !it.isRead }
            else -> notifications.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

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
                        TextButton(
                            onClick = {
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
                        ) {
                            Text("Đã đọc tất cả", fontSize = 12.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold)
                        }
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
        } else if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                FutaEmptyState(
                    title = "Chưa có thông báo nào",
                    message = "Các cập nhật về dự án, tin nhắn, hợp đồng và ưu đãi sẽ hiển thị tại đây."
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
                itemsIndexed(filteredNotifications, key = { idx, item -> "$idx-${item.id}" }) { _, item ->
                    FutaCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (item.isRead) FutaColors.LightBlueBorder else FutaColors.BrandGreen.copy(alpha = 0.5f),
                        onClick = {
                            if (!item.isRead) {
                                scope.launch {
                                    try {
                                        APIClient.get().request("/notifications/${item.id}/read", method = "PATCH")
                                    } catch (_: Exception) {}
                                }
                                notifications = notifications.map { if (it.id == item.id) it.copy(isRead = true) else it }
                                if (serverUnreadCount > 0) serverUnreadCount--
                                val currentCatCount = serverUnreadByCategory[item.category] ?: 0
                                if (currentCatCount > 0) {
                                    serverUnreadByCategory = serverUnreadByCategory + (item.category to currentCatCount - 1)
                                }
                            }
                            val r = item.route
                            val isSelfOrNoRoute = r.isNullOrEmpty() || r == "/notifications" || r == "notifications"
                            if (!isSelfOrNoRoute) {
                                onNavigate(r)
                            } else {
                                selectedNotification = item
                            }
                        }
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
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Xem chi tiết",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FutaColors.BrandGreen
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = FutaColors.BrandGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    NotificationDetailDialog(
        notification = selectedNotification,
        onDismiss = { selectedNotification = null },
        onNavigate = onNavigate
    )
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
