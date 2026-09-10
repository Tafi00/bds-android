package vn.futaland.app.features.account

import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminReportsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedPreset by remember { mutableStateOf("30d") }
    var loading by remember { mutableStateOf(true) }

    var overviewData by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var callKpi by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var callChartData by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var salesCalls by remember { mutableStateOf<List<JSONValue>>(emptyList()) }

    val presets = listOf(
        "7d" to "7 ngày",
        "30d" to "30 ngày",
        "month" to "Tháng này",
        "quarter" to "Quý này",
        "year" to "Năm nay"
    )

    fun calculateDateRange(preset: String): Pair<String, String> {
        val cal = Calendar.getInstance()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val endStr = iso.format(cal.time)

        when (preset) {
            "7d" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "30d" -> cal.add(Calendar.DAY_OF_YEAR, -30)
            "month" -> cal.set(Calendar.DAY_OF_MONTH, 1)
            "quarter" -> cal.add(Calendar.MONTH, -3)
            "year" -> cal.set(Calendar.DAY_OF_YEAR, 1)
            else -> cal.add(Calendar.DAY_OF_YEAR, -30)
        }
        val startStr = iso.format(cal.time)
        return startStr to endStr
    }

    fun loadReports() {
        scope.launch {
            loading = true
            try {
                val (startDate, endDate) = calculateDateRange(selectedPreset)
                val q = "startDate=${java.net.URLEncoder.encode(startDate, "UTF-8")}&endDate=${java.net.URLEncoder.encode(endDate, "UTF-8")}"

                val ov = APIClient.get().request("/reports/business/overview?$q")
                overviewData = ov["data"]

                val kpi = APIClient.get().request("/reports/calls/kpi?$q")
                callKpi = kpi["data"]

                val ch = APIClient.get().request("/reports/calls/chart?$q")
                callChartData = ch["data"].array

                val sl = APIClient.get().request("/reports/calls/by-sales?$q")
                salesCalls = sl["data"].array
            } catch (_: Exception) {}
            finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedPreset) {
        loadReports()
    }

    val totals = overviewData["totals"]
    val views = totals["views"].int
    val identified = totals["identifiedVisitors"].int
    val consultations = totals["consultations"].int
    val sales = totals["successfulSales"].int

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
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
                    FutaHeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        onClick = onBack
                    )

                    Text(
                        text = "Báo cáo kinh doanh",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    Spacer(Modifier.width(40.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Clean Time Preset Filter Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (pKey, pLabel) ->
                        val isSel = selectedPreset == pKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { selectedPreset = pKey }
                        ) {
                            Text(
                                text = pLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // 2. Segmented Tab Selector
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Tổng quan", "Cuộc gọi", "Hiệu suất Sales").forEachIndexed { idx, label ->
                            val isSel = selectedTab == idx
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSel) Color.White else Color.Transparent,
                                shadowElevation = if (isSel) 1.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = idx }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) FutaColors.Navy else FutaColors.Slate
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                items(3) {
                    FutaSkeletonBlock(height = 110.dp, radius = 16.dp)
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        // Section: Chỉ số tổng hợp 2x2
                        item {
                            Text("Chỉ số tổng hợp", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }

                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ReportCardMetric(
                                        title = "Lượt xem sản phẩm",
                                        value = "$views",
                                        icon = Icons.Default.Visibility,
                                        color = Color(0xFF2563EB),
                                        bg = Color(0xFFEFF6FF),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportCardMetric(
                                        title = "Khách xác định",
                                        value = "$identified",
                                        icon = Icons.Default.PersonSearch,
                                        color = FutaColors.BrandGreen,
                                        bg = Color(0xFFEAF5EF),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ReportCardMetric(
                                        title = "Lượt tư vấn",
                                        value = "$consultations",
                                        icon = Icons.Default.HeadsetMic,
                                        color = Color(0xFFF97316),
                                        bg = Color(0xFFFFF7ED),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportCardMetric(
                                        title = "Giao dịch thành công",
                                        value = "$sales",
                                        icon = Icons.Default.Verified,
                                        color = Color(0xFF7C3AED),
                                        bg = Color(0xFFF5F3FF),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Daily Interaction Trend Chart
                        val trend = overviewData["trend"].array
                        if (trend.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    shadowElevation = 1.dp,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("Xu hướng tương tác theo ngày", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                        val maxV = maxOf(1, trend.maxOfOrNull { it["views"].int } ?: 1)
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            trend.takeLast(7).forEach { p ->
                                                val rawD = p["date"].string
                                                val dateLabel = if (rawD.length >= 10) rawD.substring(5, 10).replace('-', '/') else rawD
                                                val vCount = p["views"].int
                                                val ratio = (vCount.toFloat() / maxV.toFloat()).coerceIn(0.04f, 1.0f)

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(dateLabel, fontSize = 11.5.sp, color = FutaColors.Slate, modifier = Modifier.width(42.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(14.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFFF1F5F9))
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .fillMaxWidth(ratio)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(FutaColors.BrandGreen)
                                                        )
                                                    }
                                                    Text("$vCount", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.width(32.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Conversion Funnel
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 1.dp,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                                        Text("Phễu chuyển đổi bán hàng", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FunnelStepRow("1. Lượt xem sản phẩm", "$views lượt", 1.0f, Color(0xFF2563EB))
                                        val interestRatio = if (views > 0) (identified.toFloat() / views.toFloat()).coerceIn(0.05f, 1.0f) else 0.25f
                                        FunnelStepRow("2. Khách quan tâm", "$identified khách", interestRatio, FutaColors.BrandGreen)
                                        val consultRatio = if (views > 0) (consultations.toFloat() / views.toFloat()).coerceIn(0.05f, 1.0f) else 0.12f
                                        FunnelStepRow("3. Yêu cầu tư vấn", "$consultations lượt", consultRatio, Color(0xFFF97316))
                                        val saleRatio = if (views > 0) (sales.toFloat() / views.toFloat()).coerceIn(0.03f, 1.0f) else 0.05f
                                        FunnelStepRow("4. Chốt giao dịch", "$sales đơn", saleRatio, Color(0xFF7C3AED))
                                    }
                                }
                            }
                        }

                        // Top Products
                        val topProducts = overviewData["topProducts"].array
                        if (topProducts.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    shadowElevation = 1.dp,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Top sản phẩm quan tâm nhiều nhất", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                        topProducts.take(5).forEachIndexed { idx, prod ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(prod["title"].string.ifEmpty { "Căn hộ" }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(prod["projectName"].string, fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEAF5EF)) {
                                                    Text("${prod["viewCount"].int} lượt xem", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp))
                                                }
                                            }
                                            if (idx < minOf(4, topProducts.size - 1)) HorizontalDivider(color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 2: CALLS
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ReportCardMetric(
                                        title = "Tổng cuộc gọi",
                                        value = "${callKpi["totalCalls"].int}",
                                        icon = Icons.Default.Phone,
                                        color = Color(0xFF2563EB),
                                        bg = Color(0xFFEFF6FF),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportCardMetric(
                                        title = "Đã nghe máy",
                                        value = "${callKpi["answered"].int}",
                                        icon = Icons.Default.PhoneCallback,
                                        color = FutaColors.BrandGreen,
                                        bg = Color(0xFFEAF5EF),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ReportCardMetric(
                                        title = "Cuộc gọi nhỡ",
                                        value = "${callKpi["missed"].int}",
                                        icon = Icons.Default.PhoneMissed,
                                        color = Color(0xFFF97316),
                                        bg = Color(0xFFFFF7ED),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ReportCardMetric(
                                        title = "Bị từ chối",
                                        value = "${callKpi["rejected"].int}",
                                        icon = Icons.Default.PhoneDisabled,
                                        color = Color(0xFFEF4444),
                                        bg = Color(0xFFFEF2F2),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (callChartData.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    shadowElevation = 1.dp,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Biểu đồ cuộc gọi theo ngày", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                        callChartData.takeLast(7).forEach { day ->
                                            val dLabel = day["date"].string.takeLast(5).replace('-', '/')
                                            val ans = day["answered"].int
                                            val total = maxOf(1, day["totalCalls"].int)
                                            val r = (ans.toFloat() / total.toFloat()).coerceIn(0.04f, 1.0f)

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(dLabel, fontSize = 11.5.sp, color = FutaColors.Slate, modifier = Modifier.width(42.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(14.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFFF1F5F9))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(r)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(FutaColors.BrandGreen)
                                                    )
                                                }
                                                Text("$ans/$total", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.width(42.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // TAB 3: SALES PERFORMANCE
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 1.dp,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Bảng xếp hạng hiệu suất tư vấn viên", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                    if (salesCalls.isEmpty()) {
                                        FutaEmptyState(
                                            title = "Chưa có dữ liệu",
                                            message = "Không có dữ liệu cuộc gọi của sales trong khoảng thời gian này."
                                        )
                                    } else {
                                        salesCalls.forEachIndexed { idx, s ->
                                            val uName = s["userInfo"]["name"].string.ifEmpty { "Tư vấn viên" }
                                            val callCount = s["callCount"].int
                                            val answered = s["answered"].int
                                            val rate = if (callCount > 0) (answered * 100 / callCount) else 0

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = when (idx) {
                                                            0 -> Color(0xFFFEF3C7)
                                                            1 -> Color(0xFFE2E8F0)
                                                            else -> Color(0xFFF1F5F9)
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "#${idx + 1}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = when (idx) {
                                                                    0 -> Color(0xFFD97706)
                                                                    1 -> Color(0xFF475569)
                                                                    else -> FutaColors.Slate
                                                                }
                                                            )
                                                        }
                                                    }
                                                    Column {
                                                        Text(uName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                                        Text("Tổng: $callCount cuộc gọi", fontSize = 11.sp, color = FutaColors.Slate)
                                                    }
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("$answered nghe máy", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                                    Text("Tỷ lệ: $rate%", fontSize = 11.sp, color = FutaColors.Slate)
                                                }
                                            }
                                            if (idx < salesCalls.size - 1) HorizontalDivider(color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                        }
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
private fun FunnelStepRow(
    label: String,
    value: String,
    ratio: Float,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = FutaColors.Navy, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun ReportCardMetric(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = bg, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
                Text(title, fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
