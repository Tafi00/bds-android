package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.i18n.translated
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

/**
 * "Gói & Thanh toán" — the customer's subscription, usage quota and order
 * history from `GET /pricing/me` (iOS BillingView). Tapping a pending order
 * reopens its VietQR transfer details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var accountData by remember { mutableStateOf<JSONValue?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedOrder by remember { mutableStateOf<JSONValue?>(null) }

    fun loadData() {
        scope.launch {
            loading = true
            error = null
            try {
                val res = APIClient.get().request("/pricing/me")
                accountData = if (!res["data"].isNull) res["data"] else res
            } catch (e: Exception) {
                error = e.message ?: tr("Không thể tải dữ liệu")
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Gói & Thanh toán",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onUpgradeClick) {
                        Text("Nâng cấp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                loadData()
            },
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding)
        ) {
            val acc = accountData
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when {
                    loading && acc == null -> {
                        item { FutaSkeletonBlock(height = 130.dp, radius = 18.dp) }
                        item { FutaSkeletonBlock(height = 140.dp, radius = 14.dp) }
                        items(2) { FutaSkeletonBlock(height = 64.dp, radius = 12.dp) }
                    }
                    acc == null -> item {
                        FutaEmptyState(
                            title = "Không thể tải dữ liệu",
                            message = error ?: tr("Vui lòng thử lại sau."),
                            icon = Icons.Default.ErrorOutline,
                            actionButton = {
                                FutaButton(text = "Thử lại", onClick = { loadData() }, variant = FutaButtonVariant.OUTLINE)
                            }
                        )
                    }
                    else -> {
                        item { ActivePlanCard(acc, onUpgradeClick) }
                        item { QuotaUsageSection(acc["quota"]) }
                        val orders = acc["orders"].array
                        item {
                            Text(
                                tr("Lịch sử giao dịch ({0})", orders.size),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy
                            )
                        }
                        if (orders.isEmpty()) {
                            item {
                                Text("Chưa có lịch sử giao dịch nào.", fontSize = 12.5.sp, color = FutaColors.Slate)
                            }
                        } else {
                            items(orders, key = { it.id }) { order ->
                                OrderRow(order) {
                                    if (order["status"].string == "pending") selectedOrder = order
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    PricingPaymentSheet(order = selectedOrder, onDismiss = { selectedOrder = null })
}

@Composable
private fun ActivePlanCard(acc: JSONValue, onUpgradeClick: () -> Unit) {
    val sub = acc["subscription"]
    val planId = sub["planId"].string.ifEmpty { acc["quota"]["planId"].string }
    val planName = when {
        sub["planName"].string.isNotEmpty() -> sub["planName"].string.translated("pricing")
        sub.isNull -> tr("Gói FREE")
        else -> tr("Gói {0}", planId.uppercase())
    }
    val status = when (sub["status"].string) {
        "", "active" -> if (sub.isNull) tr("Chưa đăng ký") else tr("Đang hoạt động")
        "expired" -> tr("Đã hết hạn")
        "cancelled" -> tr("Đã hủy")
        else -> sub["status"].string
    }
    val expiresAt = sub["expiresAt"].string

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF14854D), Color(0xFF0A522E))),
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("GÓI DỊCH VỤ HIỆN TẠI", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                VerbatimText(planName, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Surface(shape = CircleShape, color = Color.White) {
                VerbatimText(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (expiresAt.isNotEmpty()) tr("Hết hạn: {0}", formatOrderDate(expiresAt)) else tr("Gói duy trì vĩnh viễn"),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUpgradeClick, contentPadding = PaddingValues(0.dp)) {
                Text("Đổi gói ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun QuotaUsageSection(quota: JSONValue) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Hạn mức tính năng", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
        FutaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuotaRow("Tin đăng đang hiển thị", quota["activeListings"].int, quota["activeListingLimit"], Icons.Default.Home)
                QuotaRow("Tin đăng miễn phí hôm nay", quota["freeListingsToday"].int, quota["dailyListingLimit"], Icons.Default.CalendarToday)
                QuotaRow("Lượt đẩy tin hôm nay", quota["boostsToday"].int, quota["dailyBoostLimit"], Icons.Default.Bolt)
            }
        }
    }
}

@Composable
private fun QuotaRow(title: String, used: Int, limitValue: JSONValue, icon: ImageVector) {
    // A null limit means unlimited (backend returns null for unlimited plans).
    val limit = if (limitValue.isNull) null else limitValue.int
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 13.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            if (limit != null) {
                VerbatimText(
                    "$used / $limit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (used >= limit) Color(0xFFDC2626) else FutaColors.Navy
                )
            } else {
                Text(tr("{0} (Không giới hạn)", used), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
            }
        }
        if (limit != null && limit > 0) {
            LinearProgressIndicator(
                progress = { (used.toFloat() / limit).coerceIn(0f, 1f) },
                color = if (used >= limit) Color(0xFFDC2626) else FutaColors.BrandGreen,
                trackColor = Color(0xFFE2E8F0),
                modifier = Modifier.fillMaxWidth().height(5.dp)
            )
        }
    }
}

@Composable
private fun OrderRow(order: JSONValue, onClick: () -> Unit) {
    val status = order["status"].string
    val isPending = status == "pending"
    FutaCard(modifier = Modifier.fillMaxWidth(), onClick = if (isPending) onClick else null) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Đơn: {0}", order.id.take(8)), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    OrderStatusBadge(status)
                }
                Text(
                    tr("Gói: {0} • {1}", order["planId"].string.uppercase(), tr(if (order["billingCycle"].string == "six_months") "6 tháng" else "1 tháng")),
                    fontSize = 11.5.sp,
                    color = FutaColors.Slate
                )
                if (order["createdAt"].string.isNotEmpty()) {
                    VerbatimText(formatOrderDate(order["createdAt"].string), fontSize = 11.sp, color = FutaColors.Slate)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VerbatimText(LocalizedPrice.full(order["amount"].double), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                if (isPending) {
                    Text("Chạm xem QR ›", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                }
            }
        }
    }
}

@Composable
private fun OrderStatusBadge(status: String) {
    val (title, color) = when (status) {
        "paid" -> "ĐÃ THANH TOÁN" to FutaColors.BrandGreen
        "pending" -> "CHỜ CHUYỂN KHOẢN" to FutaColors.BrandOrange
        "cancelled" -> "ĐÃ HỦY" to FutaColors.Slate
        "expired" -> "ĐÃ HẾT HẠN" to FutaColors.Slate
        else -> status.uppercase() to Color(0xFF2563EB)
    }
    Text(
        title,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** ISO timestamp → dd/MM/yyyy (falls back to the date part). */
private fun formatOrderDate(iso: String): String {
    val date = iso.take(10).split("-")
    return if (date.size == 3) "${date[2]}/${date[1]}/${date[0]}" else iso.take(10)
}
