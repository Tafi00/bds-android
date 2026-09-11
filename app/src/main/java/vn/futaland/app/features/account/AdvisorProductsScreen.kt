package vn.futaland.app.features.account
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

@Composable
fun AdvisorProductsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("all") }

    val tabs = listOf(
        "all" to "Tất cả",
        "registered" to "Đã đăng ký bán",
        "online_holding" to "Đang giữ chỗ",
        "pending" to "Chờ xác nhận",
        "holding" to "ERP đã khóa căn",
        "completed" to "GD thành công"
    )

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/sales/registrations")
                items = res["data"].array
            } catch (_: Exception) {
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val filteredItems = remember(items, search, selectedTab) {
        items.filter { reg ->
            val unitCode = reg["unitCode"].string.ifEmpty { reg["apartment"]["propertyCode"].string }
            val projectName = reg["projectName"].string.ifEmpty { reg["apartment"]["zone"].string }
            val block = reg["block"].string.ifEmpty { reg["apartment"]["building"].string }

            val q = search.trim().lowercase()
            val matchSearch = q.isEmpty() || unitCode.lowercase().contains(q)
                || projectName.lowercase().contains(q)
                || block.lowercase().contains(q)

            val bStatus = reg["bookingStatus"].string.lowercase()
            val status = reg["status"].string.lowercase()

            val matchTab = when (selectedTab) {
                "registered" -> (status == "active" || status == "approved") && (bStatus.isEmpty() || bStatus == "none" || bStatus == "available")
                "online_holding" -> bStatus == "online_holding"
                "pending" -> bStatus == "pending_booking" || bStatus == "cancel_requested"
                "holding" -> bStatus == "holding_success"
                "completed" -> listOf("deposited", "commission_pending", "commission_paid", "purchased").contains(bStatus)
                else -> true
            }

            matchSearch && matchTab
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Rổ hàng của tôi",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onNavigate(FutaDestinations.MY_LISTINGS) }) {
                        Icon(Icons.Default.Add, "Đăng ký bán", tint = FutaColors.BrandGreen)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
        ) {
            // Search Input
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, "Tìm kiếm", tint = FutaColors.Slate, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (search.isEmpty()) {
                                Text("Mã căn, toà nhà, dự án...", color = Color.Gray, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    if (search.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            "Xóa tìm kiếm",
                            tint = FutaColors.Slate,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { search = "" }
                        )
                    }
                }
            }

            // Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { (key, label) ->
                    val isSelected = selectedTab == key
                    Surface(
                        color = if (isSelected) FutaColors.BrandGreen else Color.White,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { selectedTab = key }
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else FutaColors.Navy,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (loading && items.isEmpty()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) {
                        FutaSkeletonBlock(height = 120.dp, radius = 16.dp)
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Chưa có sản phẩm trong rổ hàng",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        Text(
                            text = "Bạn chỉ thấy các căn đã đăng ký bán và được duyệt quyền bán tại đây.",
                            fontSize = 13.5.sp,
                            color = FutaColors.Slate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onNavigate(FutaDestinations.MY_LISTINGS) },
                            colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đăng ký bán sản phẩm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems, key = { it["id"].string.ifEmpty { it.id } }) { reg ->
                        AdvisorCartItemCard(
                            reg = reg,
                            onHold = {
                                val targetId = reg["apartmentId"].string.ifEmpty { reg["apartment"]["recordId"].string.ifEmpty { reg.id } }
                                onNavigate(FutaDestinations.propertyDetail(targetId))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvisorCartItemCard(
    reg: JSONValue,
    onHold: () -> Unit
) {
    val unitCode = reg["unitCode"].string.ifEmpty { reg["apartment"]["propertyCode"].string }
    val projectName = reg["projectName"].string.ifEmpty { reg["apartment"]["zone"].string }
    val block = reg["block"].string.ifEmpty { reg["apartment"]["building"].string }
    val floor = reg["floor"].string.ifEmpty {
        if (reg["apartment"]["floor"].int > 0) "${reg["apartment"]["floor"].int}" else ""
    }
    val rawPrice = if (reg["price"].double > 0) reg["price"].double
    else if (reg["apartment"]["sellPrice"].double > 0) reg["apartment"]["sellPrice"].double
    else reg["apartment"]["price"].double

    val area = if (reg["area"].double > 0) reg["area"].double else reg["apartment"]["size_m2"].double
    val bStatus = reg["bookingStatus"].string
    val status = reg["status"].string

    FutaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = unitCode,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )
                    Text(
                        text = projectName + (if (block.isNotEmpty()) " · Toà $block" else "") + (if (floor.isNotEmpty()) " · Tầng $floor" else ""),
                        fontSize = 13.sp,
                        color = FutaColors.Slate
                    )
                }

                // Status badge
                when {
                    bStatus == "online_holding" -> StatusBadge("Đang giữ chỗ (15p)", Color(0xFFF97316), Color(0xFFFFF7ED))
                    bStatus == "pending_booking" || bStatus == "cancel_requested" -> StatusBadge("Chờ xác nhận cọc", Color(0xFFD97706), Color(0xFFFEF3C7))
                    bStatus == "holding_success" -> StatusBadge("ERP đã khóa căn", FutaColors.BrandGreen, Color(0xFFECFDF5))
                    listOf("deposited", "commission_pending", "commission_paid", "purchased").contains(bStatus) -> StatusBadge("GD thành công", Color(0xFF2563EB), Color(0xFFEFF6FF))
                    status == "active" || status == "approved" -> StatusBadge("Đang mở quyền bán", FutaColors.BrandGreen, Color(0xFFECFDF5))
                    status == "pending" -> StatusBadge("Chờ duyệt quyền bán", Color(0xFFD97706), Color(0xFFFEF3C7))
                    else -> StatusBadge("Đã hết hạn", Color.Gray, Color(0xFFF1F5F9))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMoney(rawPrice),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.BrandGreen
                )
                if (area > 0) {
                    Text(
                        text = "${area.toInt()} m²",
                        fontSize = 13.sp,
                        color = FutaColors.Slate
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val exp = reg["expiresAt"].string
                if (exp.isNotEmpty() && (status == "active" || status == "approved")) {
                    Text(
                        text = "Hạn quyền bán: ${exp.take(10)}",
                        fontSize = 11.5.sp,
                        color = FutaColors.Slate
                    )
                } else {
                    Text(
                        text = "Đăng ký: ${reg["registeredAt"].string.take(10)}",
                        fontSize = 11.5.sp,
                        color = FutaColors.Slate
                    )
                }

                if (bStatus == "online_holding") {
                    Text(
                        text = "Đang giữ cọc 15p",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF97316)
                    )
                } else if (bStatus == "holding_success") {
                    Text(
                        text = "ERP đã khóa căn",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.BrandGreen
                    )
                } else {
                    Button(
                        onClick = onHold,
                        colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đăng ký giữ chỗ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color, bg: Color) {
    Surface(
        color = bg,
        shape = CircleShape
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun formatMoney(value: Double): String {
    if (value <= 0) return "Liên hệ"
    if (value >= 1_000_000_000) {
        return String.format("%.2f tỷ", value / 1_000_000_000).replace(".00", "").replace(".", ",")
    }
    return String.format("%,.0f đ", value)
}
