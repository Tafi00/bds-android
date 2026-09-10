package vn.futaland.app.features.properties

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun MyListingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("all") }
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingApartment by remember { mutableStateOf<JSONValue?>(null) }
    var deletingApartment by remember { mutableStateOf<JSONValue?>(null) }

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/apartments", query = mapOf("mine" to "true", "limit" to "50"))
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

    val filteredItems = remember(items, selectedTab) {
        when (selectedTab) {
            "visible" -> items.filter { it["status"].string != "sold" && it["status"].string != "pending" && it["status"].string != "expired" }
            "pending" -> items.filter { it["status"].string == "pending" }
            "expired" -> items.filter { it["status"].string == "expired" || it["status"].string == "sold" }
            else -> items
        }
    }

    val tabs = listOf(
        "all" to "Tất cả (${items.size})",
        "visible" to "Đang hiển thị (${items.count { it["status"].string != "sold" && it["status"].string != "pending" && it["status"].string != "expired" }})",
        "pending" to "Chờ duyệt (${items.count { it["status"].string == "pending" }})",
        "expired" to "Hết hạn (${items.count { it["status"].string == "expired" || it["status"].string == "sold" }})"
    )
    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                        }
                        Text(
                            text = "Tin đăng của tôi",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showCreateSheet = true }) {
                            Surface(shape = CircleShape, color = FutaColors.BrandGreen, modifier = Modifier.size(28.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, "Đăng tin mới", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // 4 Tabs matching iOS MyListingsView
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEach { (tabKey, tabLabel) ->
                            val isSelected = selectedTab == tabKey
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { selectedTab = tabKey }
                            ) {
                                Text(
                                    text = tabLabel,
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
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { FutaSkeletonBlock(height = 130.dp, radius = 16.dp) }
            }
        } else if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                FutaEmptyState(
                    title = "Chưa có tin đăng",
                    message = if (selectedTab == "all") "Bạn chưa đăng bất động sản nào. Bấm nút '+' góc trên để đăng tin mới." else "Không có tin đăng nào thuộc mục này."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(filteredItems, key = { idx, item -> item.id.ifEmpty { "my-apt-$idx" } }) { _, apt ->
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(apt["title"].string, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                Surface(shape = CircleShape, color = Color(0xFFE8F5E9)) {
                                    Text("Đang hiển thị", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            Text("Mã: ${apt["code"].string.ifEmpty { apt["propertyCode"].string }} · ${apt["zone"].string}", fontSize = 12.sp, color = FutaColors.Slate)
                            Text(PropertyFormatters.formatPrice(apt["price"].double), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFFF97316))

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Sửa tin
                                    OutlinedButton(
                                        onClick = { editingApartment = apt },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, null, tint = FutaColors.Navy, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sửa", fontSize = 11.5.sp, color = FutaColors.Navy, fontWeight = FontWeight.Bold)
                                    }
                                    // Xóa tin
                                    OutlinedButton(
                                        onClick = { deletingApartment = apt },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(13.dp))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    APIClient.get().request("/apartments/${apt.id}/boost", method = "POST")
                                                    ToastCenter.show("Đã đẩy tin đăng lên Top!")
                                                    loadData()
                                                } catch (e: Exception) {
                                                    ToastCenter.show("Lỗi đẩy tin: ${e.message}", isError = true)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.RocketLaunch, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Đẩy tin Top", fontSize = 11.5.sp, color = FutaColors.BrandOrange, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { onNavigate(FutaDestinations.propertyDetail(apt.id)) },
                                        colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Xem chi tiết", fontSize = 11.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog xác nhận xóa tin đăng
    if (deletingApartment != null) {
        val aptToDelete = deletingApartment!!
        FutaDialog(
            visible = true,
            title = "Xóa tin đăng?",
            confirmText = "Xóa ngay",
            confirmVariant = FutaButtonVariant.DANGER,
            cancelText = "Hủy",
            onConfirm = {
                scope.launch {
                    try {
                        APIClient.get().request("/apartments/${aptToDelete.id}", method = "DELETE")
                        ToastCenter.show("Đã xóa tin đăng thành công")
                        deletingApartment = null
                        loadData()
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi xóa tin: ${e.message}", isError = true)
                    }
                }
            },
            onCancel = { deletingApartment = null },
            onDismiss = { deletingApartment = null }
        ) {
            Text(
                text = "Bạn có chắc chắn muốn xóa tin '${aptToDelete["title"].string}'? Hành động này không thể hoàn tác.",
                fontSize = 13.5.sp,
                color = FutaColors.Slate
            )
        }
    }

    // Sheet Tạo / Chỉnh sửa tin đăng
    if (showCreateSheet || editingApartment != null) {
        ApartmentEditorSheet(
            apartment = editingApartment,
            onDismiss = {
                showCreateSheet = false
                editingApartment = null
            },
            onSaved = {
                showCreateSheet = false
                editingApartment = null
                loadData()
            }
        )
    }
}

@Composable
private fun ApartmentEditorSheet(
    apartment: JSONValue?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val isEdit = apartment != null
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf(apartment?.get("title")?.string.orEmpty()) }
    var propertyCode by remember { mutableStateOf(apartment?.get("propertyCode")?.string.orEmpty().ifEmpty { apartment?.get("code")?.string.orEmpty() }) }
    var listingType by remember { mutableStateOf(apartment?.get("listingType")?.string.orEmpty().ifEmpty { "sell" }) }
    var propertyType by remember { mutableStateOf(apartment?.get("propertyType")?.string.orEmpty().ifEmpty { "can-ho-chung-cu" }) }
    var furniture by remember { mutableStateOf(apartment?.get("furniture")?.string.orEmpty().ifEmpty { "Cơ bản cao cấp" }) }
    var price by remember { mutableStateOf(if (apartment != null && apartment["price"].double > 0) String.format(java.util.Locale.US, "%.0f", apartment["price"].double) else "") }
    var sizeM2 by remember { mutableStateOf(if (apartment != null && apartment["area_m2"].double > 0) "${apartment["area_m2"].double}" else "") }
    var zone by remember { mutableStateOf(apartment?.get("zone")?.string.orEmpty().ifEmpty { "Times Square" }) }
    var address by remember { mutableStateOf(apartment?.get("address")?.string.orEmpty()) }
    var bedrooms by remember { mutableStateOf("${apartment?.get("bedrooms")?.int ?: 2}") }
    var bathrooms by remember { mutableStateOf("${apartment?.get("bathrooms")?.int ?: 2}") }
    var description by remember { mutableStateOf(apartment?.get("description")?.string.orEmpty()) }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = if (isEdit) "Chỉnh sửa tin đăng" else "Tạo tin đăng BĐS mới"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tiêu đề
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tiêu đề tin đăng *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                FutaInput(value = title, onValueChange = { title = it }, placeholder = "Nhập tiêu đề hấp dẫn...")
            }

            // Mã tin
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mã căn hộ / Mã tin *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                FutaInput(value = propertyCode, onValueChange = { propertyCode = it }, placeholder = "Ví dụ: TS-12.08, CT7...")
            }

            // Loại giao dịch
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Loại giao dịch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sell" to "Mua bán", "rent" to "Cho thuê").forEach { (key, label) ->
                        val isSelected = listingType == key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) FutaColors.MintBg else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { listingType = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) FutaColors.BrandGreen else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Giá & Diện tích
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Giá (VNĐ) *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(value = price, onValueChange = { price = it.filter { c -> c.isDigit() } }, placeholder = "Ví dụ: 3500000000")
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Diện tích (m²) *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(value = sizeM2, onValueChange = { sizeM2 = it }, placeholder = "Ví dụ: 68.5")
                }
            }

            // Dự án & Địa chỉ
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dự án / Phân khu *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                FutaInput(value = zone, onValueChange = { zone = it }, placeholder = "Tên dự án hoặc phân khu...")
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Địa chỉ chi tiết *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                FutaInput(value = address, onValueChange = { address = it }, placeholder = "Đường, Phường, Quận, Tỉnh/TP...")
            }

            // Phòng ngủ & Phòng tắm
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Số phòng ngủ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(value = bedrooms, onValueChange = { bedrooms = it }, placeholder = "2")
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Số phòng tắm/WC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(value = bathrooms, onValueChange = { bathrooms = it }, placeholder = "2")
                }
            }

            // Nội thất
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tình trạng nội thất", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                FutaInput(value = furniture, onValueChange = { furniture = it }, placeholder = "Ví dụ: Đầy đủ, Cơ bản cao cấp...")
            }

            // Mô tả
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mô tả chi tiết", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                FutaInput(value = description, onValueChange = { description = it }, placeholder = "Tiện ích xung quanh, view, ưu đãi...", singleLine = false)
            }

            Spacer(Modifier.height(10.dp))

            // Submit Action
            FutaButton(
                text = if (isSaving) "Đang lưu..." else if (isEdit) "Cập nhật tin đăng" else "Đăng tin ngay",
                variant = FutaButtonVariant.PRIMARY,
                enabled = !isSaving && title.isNotEmpty() && price.isNotEmpty(),
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val pVal = price.toDoubleOrNull() ?: 0.0
                            val sVal = sizeM2.toDoubleOrNull() ?: 0.0
                            val bedVal = bedrooms.toIntOrNull() ?: 2
                            val bathVal = bathrooms.toIntOrNull() ?: 2

                            val body = "{\"title\":\"$title\",\"propertyCode\":\"$propertyCode\",\"listingType\":\"$listingType\",\"propertyType\":\"$propertyType\",\"furniture\":\"$furniture\",\"price\":$pVal,\"area_m2\":$sVal,\"zone\":\"$zone\",\"address\":\"$address\",\"bedrooms\":$bedVal,\"bathrooms\":$bathVal,\"description\":\"$description\"}"
                            if (isEdit) {
                                APIClient.get().request("/apartments/${apartment!!.id}", method = "PUT", bodyJson = body)
                                ToastCenter.show("Đã cập nhật tin đăng thành công!")
                            } else {
                                APIClient.get().request("/apartments", method = "POST", bodyJson = body)
                                ToastCenter.show("Đã tạo tin đăng thành công!")
                            }
                            onSaved()
                        } catch (e: Exception) {
                            ToastCenter.show("Lỗi lưu tin: ${e.message}", isError = true)
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
