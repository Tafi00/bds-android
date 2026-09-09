package vn.futaland.app.features.messaging

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
    val icon: ImageVector,
    val iconColor: Color
)

@Composable
fun NotificationsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("all") }
    var notifications by remember { mutableStateOf<List<NotificationModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun loadNotifications() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/notifications")
                val array = res["data"].array.ifEmpty { res["notifications"].array }
                if (array.isNotEmpty()) {
                    notifications = array.map { item ->
                        val cat = item["category"].string.ifEmpty { item["type"].string.ifEmpty { "system" } }
                        val (icon, color) = when (cat.lowercase()) {
                            "transaction", "holding", "payment" -> Icons.Default.Receipt to Color(0xFF2563EB)
                            "promotion", "voucher", "marketing" -> Icons.Default.Star to FutaColors.BrandOrange
                            else -> Icons.Default.Notifications to FutaColors.BrandGreen
                        }
                        NotificationModel(
                            id = item["id"].string.ifEmpty { item["_id"].string },
                            title = item["title"].string.ifEmpty { "Thông báo hệ thống" },
                            body = item["body"].string.ifEmpty { item["message"].string.ifEmpty { item["content"].string } },
                            time = item["createdAt"].string.take(10).ifEmpty { "Vừa xong" },
                            isRead = item["isRead"].bool || item["read"].bool,
                            category = cat,
                            icon = icon,
                            iconColor = color
                        )
                    }
                } else {
                    // Fallback demo notifications matching iOS
                    notifications = listOf(
                        NotificationModel("1", "Cập nhật tiến độ dự án Times Square", "Dự án đã hoàn thành cất nóc phân khu A, chuẩn bị mở bán đợt 2.", "10 phút trước", false, "system", Icons.Default.Notifications, FutaColors.BrandGreen),
                        NotificationModel("2", "Xác nhận giữ chỗ căn hộ thành công", "Phiếu giữ chỗ mã LK4B-503 đã được hệ thống ERP ghi nhận.", "2 giờ trước", true, "transaction", Icons.Default.Receipt, Color(0xFF2563EB)),
                        NotificationModel("3", "Vòng quay may mắn FUTA", "Bạn nhận được thêm 1 lượt quay miễn phí khi đăng nhập hôm nay.", "Hôm qua", false, "promotion", Icons.Default.Star, FutaColors.BrandOrange)
                    )
                }
            } catch (_: Exception) {
                notifications = listOf(
                    NotificationModel("1", "Cập nhật tiến độ dự án Times Square", "Dự án đã hoàn thành cất nóc phân khu A, chuẩn bị mở bán đợt 2.", "10 phút trước", false, "system", Icons.Default.Notifications, FutaColors.BrandGreen),
                    NotificationModel("2", "Xác nhận giữ chỗ căn hộ thành công", "Phiếu giữ chỗ mã LK4B-503 đã được hệ thống ERP ghi nhận.", "2 giờ trước", true, "transaction", Icons.Default.Receipt, Color(0xFF2563EB)),
                    NotificationModel("3", "Vòng quay may mắn FUTA", "Bạn nhận được thêm 1 lượt quay miễn phí khi đăng nhập hôm nay.", "Hôm qua", false, "promotion", Icons.Default.Star, FutaColors.BrandOrange)
                )
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNotifications()
    }

    val filteredNotifications = remember(notifications, selectedCategory) {
        if (selectedCategory == "all") {
            notifications
        } else {
            notifications.filter { it.category.equals(selectedCategory, ignoreCase = true) }
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
                            Icon(Icons.Default.ArrowBack, null, tint = FutaColors.Navy)
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
                                    ToastCenter.show("Đã đánh dấu tất cả là đã đọc")
                                }
                            }
                        ) {
                            Text("Đã đọc tất cả", fontSize = 12.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 4 Category Tabs (Matching iOS NotificationsView)
                    val categories = listOf(
                        "all" to "Tất cả",
                        "transaction" to "Giao dịch",
                        "system" to "Hệ thống",
                        "promotion" to "Khuyến mãi"
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
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { selectedCategory = catKey }
                            ) {
                                Text(
                                    text = catLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else FutaColors.Navy,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
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
                    message = "Các cập nhật về dự án, tiến độ cọc và ưu đãi sẽ hiển thị tại đây."
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
                        borderColor = if (item.isRead) FutaColors.LightBlueBorder else FutaColors.BrandGreen.copy(alpha = 0.5f)
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
                                    Text(
                                        text = item.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.Bold,
                                        color = FutaColors.Navy,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Emerald Green Unread Dot matching iOS
                                    if (!item.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(FutaColors.BrandGreen, CircleShape)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.body,
                                    fontSize = 12.sp,
                                    color = FutaColors.Slate,
                                    lineHeight = 17.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = item.time,
                                    fontSize = 10.5.sp,
                                    color = FutaColors.Muted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
