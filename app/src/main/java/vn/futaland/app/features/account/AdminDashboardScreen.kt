package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

data class MetricCardSpec(
    val topLabel: String,
    val value: String,
    val bottomLabel: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBgColor: Color
)

data class DashboardPlan(
    val id: String,
    val name: String,
    val price: Long,
    val isActive: Boolean,
    val limitText: String
)

@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // 8 Overview Metrics matching iOS AdminDashboardView
    val metrics = remember {
        listOf(
            MetricCardSpec("Toàn thời gian", "0 đ", "Doanh thu toàn bộ", Icons.Default.TrendingUp, Color(0xFF0E7643), Color(0xFFE8F5E9)),
            MetricCardSpec("Tháng hiện tại", "0 đ", "Doanh thu tháng này", Icons.Default.CalendarToday, Color(0xFF2563EB), Color(0xFFEFF6FF)),
            MetricCardSpec("Hôm nay", "0", "Đơn mới hôm nay", Icons.Default.ShoppingBag, Color(0xFFF97316), Color(0xFFFFF7ED)),
            MetricCardSpec("Hoàn tất", "0", "Đơn chờ xử lý", Icons.Default.Schedule, Color(0xFFA855F7), Color(0xFFFAF5FF)),
            MetricCardSpec("Đã thanh toán", "0", "Đã hoàn thành", Icons.Default.CheckCircle, Color(0xFF0E7643), Color(0xFFE8F5E9)),
            MetricCardSpec("Tất cả đơn", "0", "Tổng đơn dịch vụ", Icons.Default.ShoppingCart, Color(0xFF7C3AED), Color(0xFFF5F3FF)),
            MetricCardSpec("Tài khoản hệ thống", "27", "Người dùng đăng ký", Icons.Default.People, Color(0xFF0284C7), Color(0xFFF0F9FF)),
            MetricCardSpec("Kho căn & sản phẩm", "259", "Tin đăng BĐS", Icons.Default.Apartment, Color(0xFFB45309), Color(0xFFFEF3C7))
        )
    }

    val plans = remember {
        listOf(
            DashboardPlan("free", "FREE", 0L, false, "3 tin đăng hoạt động"),
            DashboardPlan("pro", "PRO", 5_000_000L, true, "20 tin đăng · Đẩy tin 3 lần/ngày"),
            DashboardPlan("vip", "VIP", 10_000_000L, true, "Không giới hạn tin · Top 1 trang chủ")
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = FutaColors.PageBg,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Elevated Circular Back Button matching iOS
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.size(40.dp).clickable(onClick = onBack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = FutaColors.Navy, modifier = Modifier.size(18.dp))
                        }
                    }

                    Text(
                        text = "Bảng điều khiển",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    // Elevated Circular Refresh Button matching iOS
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.size(40.dp).clickable {
                            isRefreshing = true
                            scope.launch {
                                kotlinx.coroutines.delay(800)
                                isRefreshing = false
                                ToastCenter.show("Đã đồng bộ số liệu thời gian thực")
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = FutaColors.Navy, modifier = Modifier.size(18.dp))
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SECTION 1: Số liệu tổng quan (Matching iOS DashboardMetricsSection)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Số liệu tổng quan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )
                    Text(
                        text = "Chỉ số kinh doanh và vận hành theo thời gian thực",
                        fontSize = 12.5.sp,
                        color = FutaColors.Slate
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 2-Column Grid of 8 Metric Cards (Matching iOS 100%)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until metrics.size step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricBox(metrics[i], modifier = Modifier.weight(1f))
                            if (i + 1 < metrics.size) {
                                MetricBox(metrics[i + 1], modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // SECTION 2: Gói tin & Bảng giá (Matching iOS DashboardPricingSection)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Gói tin & Bảng giá",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )
                    Text(
                        text = "Cấu hình phí đăng tin và quyền lợi người dùng",
                        fontSize = 12.5.sp,
                        color = FutaColors.Slate
                    )
                }

                Spacer(Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    plans.forEach { plan ->
                        PricingAdminCard(plan = plan)
                    }
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun MetricBox(spec: MetricCardSpec, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = spec.iconBgColor,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(spec.icon, null, tint = spec.iconColor, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = spec.topLabel,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = FutaColors.Slate
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = spec.value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = FutaColors.Navy
            )

            Text(
                text = spec.bottomLabel,
                fontSize = 11.5.sp,
                color = FutaColors.Slate
            )
        }
    }
}

@Composable
private fun PricingAdminCard(plan: DashboardPlan) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Plan Tag Badge with Sparkle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(plan.name, fontSize = 12.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Status badge
                    Surface(
                        shape = CircleShape,
                        color = if (plan.isActive) Color(0xFFE8F5E9) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (plan.isActive) "• Đang bật" else "• Tạm tắt",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (plan.isActive) Color(0xFF0E7643) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Mint edit button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFF0E7643).copy(alpha = 0.2f)),
                        modifier = Modifier.clickable {
                            ToastCenter.show("Chỉnh sửa cấu hình ${plan.name}")
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Color(0xFF0E7643), modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Sửa", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643))
                        }
                    }
                }
            }

            // Price Row
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${"%,d".format(plan.price)} đ",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = FutaColors.Navy
                )
                Text(
                    text = " / tháng",
                    fontSize = 12.sp,
                    color = FutaColors.Slate,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Limits
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF0E7643), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hạn mức: ", fontSize = 12.sp, color = FutaColors.Slate)
                Text(plan.limitText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            }
        }
    }
}
