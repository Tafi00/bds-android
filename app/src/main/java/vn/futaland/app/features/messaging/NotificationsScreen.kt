package vn.futaland.app.features.messaging

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.designsystem.FutaCard
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.FutaEmptyState

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val isRead: Boolean,
    val icon: ImageVector,
    val iconColor: Color
)

@Composable
fun NotificationsScreen(
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("all") }

    val notifications = remember {
        listOf(
            NotificationItem("1", "Cập nhật tiến độ dự án Times Square", "Dự án đã hoàn thành cất nóc phân khu A, chuẩn bị mở bán đợt 2.", "10 phút trước", false, Icons.Default.Notifications, FutaColors.BrandGreen),
            NotificationItem("2", "Xác nhận giữ chỗ căn hộ thành công", "Phiếu giữ chỗ mã LK4B-503 đã được hệ thống ERP ghi nhận.", "2 giờ trước", true, Icons.Default.Receipt, Color(0xFF2563EB)),
            NotificationItem("3", "Vòng quay may mắn FUTA", "Bạn nhận được thêm 1 lượt quay miễn phí khi đăng nhập hôm nay.", "Hôm qua", true, Icons.Default.Star, FutaColors.BrandOrange)
        )
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Thông báo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
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
            itemsIndexed(notifications, key = { idx, item -> "$idx-${item.id}" }) { _, item ->
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
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(item.icon, null, tint = item.iconColor, modifier = Modifier.size(20.dp))
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
                                if (!item.isRead) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.Red, CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.message,
                                fontSize = 12.sp,
                                color = FutaColors.Slate,
                                lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.time,
                                fontSize = 11.sp,
                                color = FutaColors.Muted
                            )
                        }
                    }
                }
            }
        }
    }
}
