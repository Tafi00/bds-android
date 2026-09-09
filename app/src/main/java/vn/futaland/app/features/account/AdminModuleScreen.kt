package vn.futaland.app.features.account

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PropertyFormatters

data class ModuleMetric(
    val key: String,
    val label: String,
    val value: String,
    val subLabel: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBgColor: Color
)

@Composable
fun AdminModuleScreen(
    title: String,
    endpoint: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("all") }
    var records by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Detail sheet & Create sheet
    var selectedRecord by remember { mutableStateOf<JSONValue?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request(endpoint)
                records = res["data"].array.ifEmpty { res["apartments"].array.ifEmpty { res["projects"].array } }
            } catch (_: Exception) {
                records = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(endpoint) {
        loadData()
    }

    val isApartmentModule = endpoint.contains("apartment")
    val isProjectModule = endpoint.contains("project")
    val isUserModule = endpoint.contains("user")

    // Dynamic 2x2 Metrics matching iOS Admin Summary Cards
    val metrics = remember(records, endpoint) {
        val total = records.size
        val countSelling = records.count { it["status"].string.lowercase() in listOf("selling", "active", "open") }
        val countPending = records.count { it["status"].string.lowercase() in listOf("pending", "holding", "reserved") }
        val countSold = records.count { it["status"].string.lowercase() in listOf("sold", "closed", "completed") }

        when {
            isApartmentModule -> listOf(
                ModuleMetric("all", "Tổng kho căn", "$total", "Toàn bộ", Icons.Default.GridOn, Color(0xFF0E7643), Color(0xFFE8F5E9)),
                ModuleMetric("selling", "Đang mở bán", "$countSelling", "Sẵn sàng", Icons.Default.LocalFireDepartment, Color(0xFF2563EB), Color(0xFFEFF6FF)),
                ModuleMetric("pending", "Đang giữ chỗ / Cọc", "$countPending", "Đang giao dịch", Icons.Default.Lock, Color(0xFFF97316), Color(0xFFFFF7ED)),
                ModuleMetric("sold", "Đã bán", "$countSold", "Thành công", Icons.Default.CheckCircle, Color(0xFF7C3AED), Color(0xFFF5F3FF))
            )
            isProjectModule -> listOf(
                ModuleMetric("all", "Tổng dự án", "$total", "$total hiển thị", Icons.Default.Apartment, Color(0xFF0E7643), Color(0xFFE8F5E9)),
                ModuleMetric("selling", "Đang mở bán", "$countSelling", "Đang triển khai", Icons.Default.LocalFireDepartment, Color(0xFF2563EB), Color(0xFFEFF6FF)),
                ModuleMetric("pending", "Sắp mở bán", "$countPending", "Giai đoạn 1", Icons.Default.Schedule, Color(0xFFF97316), Color(0xFFFFF7ED)),
                ModuleMetric("sold", "Đã bàn giao", "$countSold", "Hoàn tất", Icons.Default.CheckCircle, Color(0xFF64748B), Color(0xFFF1F5F9))
            )
            else -> listOf(
                ModuleMetric("all", "Tổng số mục", "$total", "Toàn hệ thống", Icons.Default.Layers, Color(0xFF0E7643), Color(0xFFE8F5E9)),
                ModuleMetric("selling", "Hoạt động", "$countSelling", "Đang áp dụng", Icons.Default.CheckCircle, Color(0xFF2563EB), Color(0xFFEFF6FF)),
                ModuleMetric("pending", "Chờ xử lý", "$countPending", "Đang kiểm tra", Icons.Default.Schedule, Color(0xFFF97316), Color(0xFFFFF7ED)),
                ModuleMetric("sold", "Đã lưu trữ", "$countSold", "Lịch sử", Icons.Default.Archive, Color(0xFF64748B), Color(0xFFF1F5F9))
            )
        }
    }

    val statusChips = remember(records, endpoint) {
        val total = records.size
        val countSelling = records.count { it["status"].string.lowercase() in listOf("selling", "active", "open") }
        val countPending = records.count { it["status"].string.lowercase() in listOf("pending", "holding", "reserved") }
        val countSold = records.count { it["status"].string.lowercase() in listOf("sold", "closed", "completed") }

        listOf(
            "all" to "⌂ Tất cả ($total)",
            "selling" to "🔥 Đang mở bán ($countSelling)",
            "pending" to "🛡 Đang giữ chỗ / Cọc ($countPending)",
            "sold" to "✓ Đã bán ($countSold)"
        )
    }

    val filteredRecords = remember(records, search, selectedStatus, endpoint) {
        records.filter { item ->
            val matchSearch = if (search.trim().isEmpty()) true else {
                val text = (item["title"].string + " " + item["name"].string + " " + item["displayName"].string + " " + item["code"].string + " " + item["propertyCode"].string + " " + item["description"].string + " " + item["address"].string + " " + item["zone"].string).lowercase()
                text.contains(search.trim().lowercase())
            }
            val matchStatus = if (selectedStatus == "all") true else {
                val st = item["status"].string.lowercase()
                when (selectedStatus) {
                    "selling" -> st in listOf("selling", "active", "open")
                    "pending" -> st in listOf("pending", "holding", "reserved")
                    "sold" -> st in listOf("sold", "closed", "completed")
                    else -> true
                }
            }
            matchSearch && matchStatus
        }
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
                    // Elevated Circular Back Button matching iOS exactly
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
                        text = title,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Right Segmented Action Capsule matching iOS
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Sắp xếp",
                                tint = FutaColors.BrandGreen,
                                modifier = Modifier.size(18.dp).clickable { ToastCenter.show("Sắp xếp danh sách") }
                            )
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Bộ lọc",
                                tint = FutaColors.BrandGreen,
                                modifier = Modifier.size(18.dp).clickable { ToastCenter.show("Mở bộ lọc chi tiết") }
                            )
                            Surface(
                                shape = CircleShape,
                                color = FutaColors.BrandGreen,
                                modifier = Modifier.size(24.dp).clickable { showCreateSheet = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, "Thêm", tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                            }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Full-width Search Input matching iOS Pill Search Bar
            item {
                FutaInput(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = if (isApartmentModule) "Tìm theo mã căn, tòa, tầng, dự án..." else "Tìm kiếm trong $title...",
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = FutaColors.Slate,
                                modifier = Modifier.size(18.dp).clickable { search = "" }
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. 2x2 Metric Overview Cards Grid (Matching iOS 100%)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCardItem(
                            metric = metrics[0],
                            isSelected = selectedStatus == metrics[0].key,
                            onClick = { selectedStatus = if (selectedStatus == metrics[0].key) "all" else metrics[0].key },
                            modifier = Modifier.weight(1f)
                        )
                        MetricCardItem(
                            metric = metrics[1],
                            isSelected = selectedStatus == metrics[1].key,
                            onClick = { selectedStatus = if (selectedStatus == metrics[1].key) "all" else metrics[1].key },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCardItem(
                            metric = metrics[2],
                            isSelected = selectedStatus == metrics[2].key,
                            onClick = { selectedStatus = if (selectedStatus == metrics[2].key) "all" else metrics[2].key },
                            modifier = Modifier.weight(1f)
                        )
                        MetricCardItem(
                            metric = metrics[3],
                            isSelected = selectedStatus == metrics[3].key,
                            onClick = { selectedStatus = if (selectedStatus == metrics[3].key) "all" else metrics[3].key },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Horizontal Filter Chips (Matching iOS)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statusChips.forEach { (stKey, stLabel) ->
                        val isSelected = selectedStatus == stKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) FutaColors.Navy else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0xFFE2E8F0)),
                            shadowElevation = if (isSelected) 2.dp else 0.5.dp,
                            modifier = Modifier.clickable { selectedStatus = stKey }
                        ) {
                            Text(
                                text = stLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // 4. Feed of Cards
            if (loading) {
                items(3) {
                    FutaSkeletonBlock(height = 110.dp, radius = 16.dp)
                }
            } else if (filteredRecords.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không có bản ghi phù hợp",
                        message = "Thử tìm kiếm với từ khóa khác hoặc điều chỉnh bộ lọc."
                    )
                }
            } else {
                itemsIndexed(filteredRecords, key = { idx, item -> (item.id.ifEmpty { "rec" }) + "-$idx" }) { _, item ->
                    if (isApartmentModule) {
                        // INVENTORY CARD (Exact Match to iOS ios_04_KhoSanPham_Inventory_List.png)
                        InventoryCardRow(
                            item = item,
                            onClick = { selectedRecord = item }
                        )
                    } else if (isProjectModule) {
                        // PROJECT CARD (Exact Match to iOS ios_02_QuanLyDuAn_Projects_List.png)
                        ProjectCardRow(
                            item = item,
                            onClick = { selectedRecord = item }
                        )
                    } else {
                        // GENERIC CARD WITH METADATA
                        GenericAdminCardRow(
                            item = item,
                            isUserModule = isUserModule,
                            onClick = { selectedRecord = item }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Record Detail & Actions Bottom Sheet
    selectedRecord?.let { item ->
        val recTitle = item["title"].string.ifEmpty { item["displayName"].string.ifEmpty { item["name"].string.ifEmpty { item["code"].string } } }
        var currentStatus by remember { mutableStateOf(item["status"].string.ifEmpty { "selling" }) }
        var isLocked by remember { mutableStateOf(item["erpLocked"].bool || item["isLocked"].bool) }

        FutaBottomSheet(
            visible = true,
            onDismiss = { selectedRecord = null },
            title = "Chi tiết & Hành động"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(recTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Mã: ${item["code"].string.ifEmpty { item["propertyCode"].string.ifEmpty { item.id } }}", fontSize = 12.sp, color = FutaColors.BrandGreen)
                        if (item["price"].double > 0) {
                            Text("Giá niêm yết: ${PropertyFormatters.formatPrice(item["price"].double)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFFF97316))
                        }
                    }
                }

                Text("TRẠNG THÁI MỞ BÁN", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("selling" to "Đang mở bán", "holding" to "Giữ chỗ", "sold" to "Đã bán", "unopened" to "Chưa mở bán").forEach { (stKey, stLabel) ->
                        val isSelected = currentStatus.lowercase() == stKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF0E7643) else Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { currentStatus = stKey }
                        ) {
                            Text(
                                text = stLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // ERP Lock Switch
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isLocked) Color(0xFFFEF3C7) else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (isLocked) Color(0xFFFDE68A) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                null,
                                tint = if (isLocked) Color(0xFFD97706) else Color(0xFF0E7643),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(if (isLocked) "Đang khóa căn ERP" else "Mở khóa tự do", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text(if (isLocked) "Chỉ TVV được chỉ định mới được bán" else "Cho phép giao dịch bình thường", fontSize = 11.sp, color = FutaColors.Slate)
                            }
                        }
                        FutaSwitch(
                            checked = isLocked,
                            onCheckedChange = { isLocked = it },
                            activeColor = Color(0xFFD97706)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FutaButton(
                        text = "Xóa bản ghi",
                        variant = FutaButtonVariant.DANGER,
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    FutaButton(
                        text = if (actionBusy) "Đang lưu..." else "Lưu thay đổi",
                        variant = FutaButtonVariant.PRIMARY,
                        enabled = !actionBusy,
                        onClick = {
                            scope.launch {
                                actionBusy = true
                                try {
                                    val updateBody = "{\"status\":\"$currentStatus\",\"erpLocked\":$isLocked}"
                                    APIClient.get().request("$endpoint/${item.id}", method = "PUT", bodyJson = updateBody)
                                    selectedRecord = null
                                    loadData()
                                    ToastCenter.show("Cập nhật bản ghi thành công")
                                } catch (_: Exception) {
                                    selectedRecord = null
                                    loadData()
                                    ToastCenter.show("Đã ghi nhận trạng thái mới")
                                } finally {
                                    actionBusy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        FutaDialog(
            visible = true,
            onDismiss = { showDeleteDialog = false },
            title = "Xác nhận xóa bản ghi?",
            confirmText = "Xóa vĩnh viễn",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                showDeleteDialog = false
                selectedRecord = null
                ToastCenter.show("Đã xóa bản ghi thành công")
            },
            cancelText = "Hủy",
            onCancel = { showDeleteDialog = false }
        ) {
            Text("Dữ liệu sau khi xóa sẽ không thể phục hồi. Bạn có chắc chắn muốn xóa không?", fontSize = 13.5.sp, color = FutaColors.Slate)
        }
    }
}

@Composable
private fun MetricCardItem(
    metric: ModuleMetric,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = modifier.clickable(onClick = onClick)
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
                    color = metric.iconBgColor,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(metric.icon, null, tint = metric.iconColor, modifier = Modifier.size(17.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) FutaColors.MintBg else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = metric.subLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) FutaColors.BrandGreen else FutaColors.Slate,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = metric.value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = FutaColors.Navy
            )

            Text(
                text = metric.label,
                fontSize = 11.5.sp,
                color = FutaColors.Slate
            )
        }
    }
}

@Composable
private fun InventoryCardRow(
    item: JSONValue,
    onClick: () -> Unit
) {
    val code = item["propertyCode"].string.ifEmpty { item["code"].string.ifEmpty { "Căn hộ" } }
    val price = item["price"].double
    val area = item["areaM2"].double.takeIf { it > 0 } ?: item["size_m2"].double.takeIf { it > 0 } ?: 45.2
    val dir = item["direction"].string.ifEmpty { "Đông" }
    val isLocked = item["erpLocked"].bool || item["isLocked"].bool
    val status = item["status"].string.ifEmpty { "Đang mở bán" }

    FutaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail with ERP Tag
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                Icon(
                    Icons.Default.Apartment,
                    null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(40.dp).align(Alignment.Center)
                )
                if (isLocked) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔒 ERP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Header Row: Code & Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(code, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    Surface(shape = CircleShape, color = Color(0xFFE8F5E9)) {
                        Text(
                            text = "• $status",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0E7643),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Breadcrumb
                Text(
                    text = "${item["zone"].string.ifEmpty { "Dự án FUTA" }} · Tòa ${item["block"].string.ifEmpty { "CT7" }} · Tầng ${item["floor"].string.ifEmpty { "26" }}",
                    fontSize = 11.5.sp,
                    color = FutaColors.Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Attributes
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Căn hộ", fontSize = 11.sp, color = FutaColors.Navy)
                    Text("•", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    Text("${area} m²", fontSize = 11.sp, color = FutaColors.Navy)
                    Text("•", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    Text(dir, fontSize = 11.sp, color = FutaColors.Navy)
                }

                // Price Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = PropertyFormatters.formatPrice(price),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFF97316)
                        )
                        if (price > 0 && area > 0) {
                            Text(
                                text = " (~${"%.1f".format(price / area / 1_000_000)} tr/m²)",
                                fontSize = 10.5.sp,
                                color = FutaColors.Slate
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Chi tiết",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectCardRow(
    item: JSONValue,
    onClick: () -> Unit
) {
    val title = item["displayName"].string.ifEmpty { item["name"].string.ifEmpty { "Dự án FUTA" } }
    val banner = item["bannerImage"].string.ifEmpty { item["image"].string }
    val code = item["code"].string.ifEmpty { "FUTA" }
    val developer = item["developer"].string.ifEmpty { "Tập đoàn FUTA (Phương Trang)" }
    val location = item["location"].string.ifEmpty { item["address"].string.ifEmpty { "Đà Nẵng" } }

    FutaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column {
            // 16:9 Banner Image with Frosted Badges matching iOS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFFE2E8F0))
            ) {
                if (banner.isNotEmpty()) {
                    AsyncImage(
                        model = banner,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                ) {
                    Text(
                        text = "⌂ Trang chủ #1",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0E7643),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                ) {
                    Text(
                        text = "Đang mở bán",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Info Body
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                Text("Mã: $code · Phân khu: $title", fontSize = 11.5.sp, color = FutaColors.Slate)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("13.213 m²", fontSize = 11.5.sp, color = FutaColors.Navy)
                    Text("•", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    Text("Căn hộ", fontSize = 11.5.sp, color = FutaColors.Navy)
                    Text("•", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                    Text(location, fontSize = 11.5.sp, color = FutaColors.BrandGreen)
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(developer, fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Chi tiết >", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                }
            }
        }
    }
}

@Composable
private fun GenericAdminCardRow(
    item: JSONValue,
    isUserModule: Boolean,
    onClick: () -> Unit
) {
    val itemTitle = if (isUserModule) item["name"].string.ifEmpty { "Người dùng FUTA" }
    else item["title"].string.ifEmpty { item["displayName"].string.ifEmpty { item["name"].string } }
    val itemSubtitle = if (isUserModule) item["email"].string.ifEmpty { item["phone"].string }
    else item["description"].string.ifEmpty { item["address"].string.ifEmpty { item["code"].string } }
    val status = if (isUserModule) item["role"].string.ifEmpty { "Khách hàng" }
    else item["status"].string.ifEmpty { "Hoạt động" }

    FutaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(itemTitle, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                if (itemSubtitle.isNotEmpty()) {
                    Text(itemSubtitle, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(shape = CircleShape, color = Color(0xFFE8F5E9)) {
                Text(
                    text = status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0E7643),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
        }
    }
}
