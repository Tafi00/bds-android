package vn.futaland.app.features.properties

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

data class SellingItem(
    val id: String,
    val aptId: String,
    val unitCode: String,
    val projectName: String,
    val block: String,
    val price: Double,
    val area: Double,
    val imgUrl: String,
    val status: String, // available, pending, active, expired, rejected
    val expiresAt: String
)

/**
 * Đăng ký bán sản phẩm (Matching Web advisor-registrations.tsx).
 */
@Composable
fun MyListingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SellingItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var registeringApartment by remember { mutableStateOf<SellingItem?>(null) }

    val tabs = listOf(
        "all" to "Tất cả",
        "available" to "Chưa đăng ký",
        "pending" to "Chờ duyệt",
        "active" to "Đang bán",
        "expired" to "Đã hết hạn"
    )

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val invList = try {
                    val invRes = APIClient.get().request("/sales/inventory")
                    invRes["data"].array.ifEmpty { invRes.array }
                } catch (_: Exception) {
                    try {
                        val aptRes = APIClient.get().request("/apartments?limit=50")
                        aptRes["data"].array
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                val regList = try {
                    val regRes = APIClient.get().request("/sales/registrations")
                    regRes["data"].array.ifEmpty { regRes.array }
                } catch (_: Exception) {
                    emptyList()
                }

                val merged = mutableListOf<SellingItem>()
                val registeredIds = mutableSetOf<String>()

                for (reg in regList) {
                    val aptId = reg["apartmentId"].string.ifEmpty {
                        reg["apartment"]["id"].string.ifEmpty { reg["apartment"]["recordId"].string }
                    }
                    if (aptId.isNotEmpty()) registeredIds.add(aptId)
                    val unitCode = reg["unitCode"].string.ifEmpty { reg["apartment"]["propertyCode"].string }
                    if (unitCode.isNotEmpty()) registeredIds.add(unitCode)

                    val projectName = reg["projectName"].string.ifEmpty { reg["apartment"]["zone"].string }
                    val block = reg["block"].string.ifEmpty { reg["apartment"]["building"].string }
                    val rawPrice = if (reg["price"].double > 0) reg["price"].double
                    else if (reg["apartment"]["sellPrice"].double > 0) reg["apartment"]["sellPrice"].double
                    else reg["apartment"]["price"].double
                    val area = if (reg["area"].double > 0) reg["area"].double else reg["apartment"]["size_m2"].double
                    val imgUrl = PropertyFormatters.resolveImage(reg)
                    val status = reg["status"].string.lowercase()

                    merged.add(
                        SellingItem(
                            id = reg.id.ifEmpty { "reg-$aptId" },
                            aptId = aptId,
                            unitCode = unitCode,
                            projectName = projectName,
                            block = block,
                            price = rawPrice,
                            area = area,
                            imgUrl = imgUrl,
                            status = status,
                            expiresAt = reg["expiresAt"].string
                        )
                    )
                }

                for (apt in invList) {
                    val recId = apt["recordId"].string.ifEmpty { apt["id"].string }
                    val pCode = apt["propertyCode"].string
                    if (!registeredIds.contains(recId) && !registeredIds.contains(pCode)) {
                        val rawPrice = if (apt["sellPrice"].double > 0) apt["sellPrice"].double else apt["price"].double
                        val area = apt["size_m2"].double
                        val imgUrl = PropertyFormatters.resolveImage(apt)
                        merged.add(
                            SellingItem(
                                id = "avail-$recId",
                                aptId = recId,
                                unitCode = pCode,
                                projectName = apt["zone"].string,
                                block = apt["building"].string,
                                price = rawPrice,
                                area = area,
                                imgUrl = imgUrl,
                                status = "available",
                                expiresAt = ""
                            )
                        )
                    }
                }

                items = merged
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

    val filteredItems = remember(items, selectedTab, search) {
        items.filter { item ->
            val q = search.trim().lowercase()
            val matchSearch = q.isEmpty() || item.unitCode.lowercase().contains(q) || item.projectName.lowercase().contains(q)

            val matchTab = when (selectedTab) {
                "available" -> item.status == "available"
                "pending" -> item.status == "pending"
                "active" -> item.status == "active" || item.status == "approved"
                "expired" -> item.status == "expired" || item.status == "rejected" || item.status == "revoked"
                else -> true
            }

            matchSearch && matchTab
        }
    }

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
                        text = "Đăng ký bán sản phẩm",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    FutaHeaderIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Làm mới",
                        tint = FutaColors.BrandGreen,
                        onClick = { loadData() }
                    )
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
            // Search Bar
            item {
                FutaInput(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Tìm theo mã căn, toà nhà, dự án…",
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Xóa",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { search = "" },
                                tint = FutaColors.Slate
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Status Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { (tabKey, tabLabel) ->
                        val isSelected = selectedTab == tabKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { selectedTab = tabKey }
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Cards List
            if (loading && items.isEmpty()) {
                items(4) {
                    FutaSkeletonBlock(height = 140.dp, radius = 16.dp)
                }
            } else if (filteredItems.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không tìm thấy sản phẩm",
                        message = "Thử tìm kiếm với từ khoá khác hoặc chuyển bộ lọc."
                    )
                }
            } else {
                itemsIndexed(filteredItems, key = { _, item -> item.id }) { _, item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (item.aptId.isNotEmpty()) {
                                onNavigate(FutaDestinations.propertyDetail(item.aptId))
                            }
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Thumbnail Image
                                Box(
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 76.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE2E8F0))
                                ) {
                                    if (item.imgUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = item.imgUrl,
                                            contentDescription = item.unitCode,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Apartment,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                // Details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = item.unitCode.ifEmpty { "Căn hộ" },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Navy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatMoney(item.price),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.BrandGreen
                                    )
                                    Text(
                                        text = item.projectName + if (item.block.isNotEmpty()) " · Toà ${item.block}" else "",
                                        fontSize = 11.5.sp,
                                        color = FutaColors.Slate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.area > 0) {
                                        Text(
                                            text = "${item.area.toInt()} m²",
                                            fontSize = 11.sp,
                                            color = FutaColors.Slate
                                        )
                                    }
                                }

                                // Badge
                                when (item.status) {
                                    "available" -> RegistrationBadge("Chưa đăng ký", Color(0xFF2563EB), Color(0xFFEFF6FF))
                                    "pending" -> RegistrationBadge("Chờ duyệt", Color(0xFFD97706), Color(0xFFFEF3C7))
                                    "active", "approved" -> RegistrationBadge("Đang bán", FutaColors.BrandGreen, Color(0xFFECFDF5))
                                    "rejected" -> RegistrationBadge("Từ chối", Color(0xFFDC2626), Color(0xFFFEE2E2))
                                    else -> RegistrationBadge("Hết hạn", Color.Gray, Color(0xFFF1F5F9))
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (item.status) {
                                    "active", "approved" -> {
                                        Text(
                                            text = if (item.expiresAt.isNotEmpty()) "Hạn: ${item.expiresAt.take(10)}" else "Đang mở bán",
                                            fontSize = 11.5.sp,
                                            color = FutaColors.Slate
                                        )
                                        Button(
                                            onClick = { onNavigate(FutaDestinations.ADVISOR_PRODUCTS) },
                                            colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Vào rổ hàng", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    "pending" -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Schedule, null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                                            Text(
                                                text = "Chờ Admin duyệt",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                        Text(
                                            text = "Đang xét duyệt",
                                            fontSize = 12.sp,
                                            color = FutaColors.Slate
                                        )
                                    }
                                    else -> {
                                        // Available or Expired: allow Registering to sell!
                                        Spacer(Modifier.weight(1f))
                                        Button(
                                            onClick = { registeringApartment = item },
                                            colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Đăng ký bán", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

    // Sales Policy Confirmation Dialog
    registeringApartment?.let { apt ->
        var agreed by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) registeringApartment = null
            },
            icon = {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = FutaColors.BrandOrange,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text("Xác nhận đăng ký bán", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Trước khi đăng ký bán căn ${apt.unitCode} thuộc ${apt.projectName}, bạn cần đọc và đồng ý với chính sách bán hàng và quy định của FUTA Land.",
                        fontSize = 13.5.sp,
                        color = FutaColors.Slate
                    )

                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Nguyên tắc ưu tiên khi có tranh chấp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Trùng khách và thứ tự giữ chỗ được đối soát theo hồ sơ hợp lệ được hệ thống ghi nhận trước, không theo thỏa thuận miệng.",
                                fontSize = 11.5.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { agreed = !agreed }
                    ) {
                        Checkbox(
                            checked = agreed,
                            onCheckedChange = { agreed = it },
                            colors = CheckboxDefaults.colors(checkedColor = FutaColors.BrandGreen)
                        )
                        Text(
                            text = "Tôi đồng ý với chính sách bán hàng và quy chế phân phối (Phiên bản 1.0)",
                            fontSize = 12.sp,
                            color = FutaColors.Navy
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            try {
                                val body = """{"apartmentId":"${apt.aptId}","customerName":"Tư vấn viên FUTA Land","notes":"Đăng ký bán từ ứng dụng Android","salesPolicyAccepted":true,"salesPolicyVersion":"1.0"}"""
                                APIClient.get().request("/sales/registrations", method = "POST", bodyJson = body)
                                ToastCenter.show("Đã gửi yêu cầu đăng ký bán căn ${apt.unitCode}! Đang chờ Admin duyệt.")
                                registeringApartment = null
                                loadData()
                            } catch (e: Exception) {
                                ToastCenter.show("Lỗi: ${e.message}", isError = true)
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = agreed && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Xác nhận đăng ký")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { registeringApartment = null },
                    enabled = !isSubmitting
                ) {
                    Text("Hủy bỏ", color = FutaColors.Slate)
                }
            }
        )
    }
}

@Composable
private fun RegistrationBadge(text: String, color: Color, bg: Color) {
    Surface(
        color = bg,
        shape = CircleShape
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
