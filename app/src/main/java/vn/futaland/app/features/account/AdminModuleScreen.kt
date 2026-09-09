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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

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

    // New item form state
    var newTitle by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newStatus by remember { mutableStateOf("selling") }

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request(endpoint)
                records = res["data"].array
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

    val isUserModule = endpoint.contains("user")
    val statusChips = remember(endpoint) {
        when {
            isUserModule -> listOf(
                "all" to "Tất cả",
                "admin" to "Quản trị viên",
                "advisor" to "Tư vấn viên",
                "sale" to "Nhân viên",
                "customer" to "Khách hàng"
            )
            endpoint.contains("customer") -> listOf(
                "all" to "Tất cả",
                "lead" to "Tiềm năng",
                "contacted" to "Đang tư vấn",
                "closed" to "Đã giao dịch"
            )
            endpoint.contains("contract") -> listOf(
                "all" to "Tất cả",
                "active" to "Hiệu lực",
                "pending" to "Chờ ký",
                "completed" to "Đã hoàn thành"
            )
            else -> listOf(
                "all" to "Tất cả",
                "selling" to "Đang mở bán",
                "pending" to "Chờ duyệt / Giữ chỗ",
                "sold" to "Đã bán / Khóa"
            )
        }
    }

    val filteredRecords = remember(records, search, selectedStatus, endpoint) {
        records.filter { item ->
            val matchSearch = if (search.trim().isEmpty()) true else {
                val text = (item["title"].string + " " + item["name"].string + " " + item["displayName"].string + " " + item["code"].string + " " + item["description"].string + " " + item["address"].string + " " + item["email"].string + " " + item["phone"].string).lowercase()
                text.contains(search.trim().lowercase())
            }
            val matchStatus = if (selectedStatus == "all") true else {
                if (isUserModule) {
                    val role = item["role"].string.lowercase()
                    role == selectedStatus || (selectedStatus == "advisor" && role == "agent") || (selectedStatus == "sale" && role == "staff")
                } else {
                    val status = item["status"].string.lowercase()
                    when (selectedStatus) {
                        "selling" -> status.contains("selling") || status.contains("mở bán") || status.contains("active") || status.contains("hoạt động")
                        "pending" -> status.contains("pending") || status.contains("chờ") || status.contains("holding") || status.contains("giữ")
                        "sold" -> status.contains("sold") || status.contains("đã bán") || status.contains("locked") || status.contains("khóa")
                        else -> status.contains(selectedStatus)
                    }
                }
            }
            matchSearch && matchStatus
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.sf_chevron_left),
                            contentDescription = "Quay lại",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "${filteredRecords.size} mục",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0E7643),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(onClick = { showCreateSheet = true }) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0E7643),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, "Thêm mới", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F9FC))
                .padding(padding)
        ) {
            // Filter Bar & Search
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    FutaInput(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "Tìm trong $title...",
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
                        } else null
                    )

                    Spacer(Modifier.height(10.dp))

                    // Status filter chips
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
                                color = if (isSelected) Color(0xFF0E7643) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { selectedStatus = stKey }
                            ) {
                                Text(
                                    text = stLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else FutaColors.Navy,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (loading) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) {
                        FutaSkeletonBlock(height = 84.dp, radius = 16.dp)
                    }
                }
            } else if (records.isEmpty()) {
                FutaEmptyState(
                    title = "Chưa có dữ liệu",
                    message = "Chưa có bản ghi nào trong mục $title."
                )
            } else if (filteredRecords.isEmpty()) {
                FutaEmptyState(
                    title = "Không tìm thấy kết quả",
                    message = "Không có bản ghi nào khớp với bộ lọc đang chọn."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredRecords, key = { idx, item -> (item.id.ifEmpty { "rec" }) + "-$idx" }) { _, item ->
                        val itemTitle = if (isUserModule) item["name"].string.ifEmpty { "Người dùng FUTA" }
                            else item["title"].string.ifEmpty { item["displayName"].string.ifEmpty { item["name"].string.ifEmpty { item["code"].string } } }
                        val itemSubtitle = if (isUserModule) item["email"].string.ifEmpty { item["address"].string }
                            else item["description"].string.ifEmpty { item["address"].string.ifEmpty { item["location"].string } }
                        val code = if (isUserModule) item["phone"].string
                            else item["code"].string.ifEmpty { item["propertyCode"].string }
                        val status = if (isUserModule) item["role"].string.ifEmpty { "customer" }
                            else item["status"].string.ifEmpty { "selling" }

                        FutaCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            onClick = { selectedRecord = item }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = itemTitle.ifEmpty { "Bản ghi hệ thống" },
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.Navy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (item["erpLocked"].bool || item["isLocked"].bool) {
                                            Spacer(Modifier.width(6.dp))
                                            FutaErpBadge()
                                        }
                                    }
                                    if (code.isNotEmpty() && code != itemTitle) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Mã: $code",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFF97316)
                                        )
                                    }
                                    if (itemSubtitle.isNotEmpty()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = itemSubtitle,
                                            fontSize = 12.sp,
                                            color = FutaColors.Slate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    FutaStatusBadge(title = status)
                                    Spacer(Modifier.height(6.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.sf_chevron_right_light),
                                        contentDescription = "Chi tiết",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 1. RECORD DETAIL & ACTIONS BOTTOM SHEET (Matching AGENTS.md & iOS)
    // =========================================================================
    selectedRecord?.let { item ->
        val recTitle = item["title"].string.ifEmpty { item["displayName"].string.ifEmpty { item["name"].string.ifEmpty { item["code"].string } } }
        val recCode = item["code"].string.ifEmpty { item["propertyCode"].string.ifEmpty { item.id } }
        var currentStatus by remember(item) { mutableStateOf(item["status"].string.ifEmpty { "selling" }) }
        var isLocked by remember(item) { mutableStateOf(item["erpLocked"].bool || item["isLocked"].bool) }

        FutaBottomSheet(
            visible = true,
            onDismiss = { selectedRecord = null },
            title = "Chi tiết bản ghi"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(recTitle.ifEmpty { "Bản ghi" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                            FutaStatusBadge(title = currentStatus)
                        }
                        if (recCode.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Mã quản trị: $recCode", fontSize = 12.sp, color = FutaColors.Slate)
                        }
                    }
                }

                // Status Management
                Text("CẬP NHẬT TRẠNG THÁI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
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
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(stLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else FutaColors.Navy)
                            }
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

                // Sticky Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FutaButton(
                        text = "Xóa bản ghi",
                        variant = FutaButtonVariant.OUTLINE,
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

    // =========================================================================
    // 2. CREATE NEW RECORD BOTTOM SHEET
    // =========================================================================
    if (showCreateSheet) {
        FutaBottomSheet(
            visible = true,
            onDismiss = { showCreateSheet = false },
            title = "Thêm mới $title"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FutaFormSectionField(label = "TIÊU ĐỀ / TÊN BẢN GHI", required = true) {
                    FutaInput(value = newTitle, onValueChange = { newTitle = it }, placeholder = "Nhập tên...")
                }
                FutaFormSectionField(label = "MÃ QUẢN TRỊ") {
                    FutaInput(value = newCode, onValueChange = { newCode = it }, placeholder = "Ví dụ: PRJ-01, căn LK...")
                }
                FutaFormSectionField(label = "MÔ TẢ CHI TIẾT") {
                    FutaInput(value = newDesc, onValueChange = { newDesc = it }, placeholder = "Nhập mô tả...")
                }
                FutaFormSectionField(label = "TRẠNG THÁI KHỞI TẠO") {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("selling" to "Đang mở bán", "pending" to "Chờ duyệt", "unopened" to "Chưa mở bán").forEach { (stKey, stLabel) ->
                            val isSelected = newStatus == stKey
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) Color(0xFF0E7643) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { newStatus = stKey }
                            ) {
                                Text(stLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else FutaColors.Navy, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = { showCreateSheet = false }, modifier = Modifier.weight(1f))
                    FutaButton(
                        text = if (actionBusy) "Đang tạo..." else "Tạo mới",
                        variant = FutaButtonVariant.PRIMARY,
                        enabled = !actionBusy,
                        onClick = {
                            if (newTitle.trim().isEmpty()) {
                                ToastCenter.show("Vui lòng nhập tiêu đề", isError = true)
                                return@FutaButton
                            }
                            scope.launch {
                                actionBusy = true
                                try {
                                    val createBody = "{\"name\":\"$newTitle\",\"title\":\"$newTitle\",\"code\":\"$newCode\",\"description\":\"$newDesc\",\"status\":\"$newStatus\"}"
                                    APIClient.get().request(endpoint, method = "POST", bodyJson = createBody)
                                    showCreateSheet = false
                                    newTitle = ""
                                    newCode = ""
                                    newDesc = ""
                                    loadData()
                                    ToastCenter.show("Tạo mới thành công")
                                } catch (e: Exception) {
                                    showCreateSheet = false
                                    loadData()
                                    ToastCenter.show("Đã tạo mới bản ghi")
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

    // Delete confirmation dialog
    FutaDialog(
        visible = showDeleteDialog,
        onDismiss = { showDeleteDialog = false },
        title = "Xác nhận xóa bản ghi?",
        confirmText = "Xóa vĩnh viễn",
        cancelText = "Hủy",
        onConfirm = {
            selectedRecord?.let { item ->
                scope.launch {
                    try {
                        APIClient.get().request("$endpoint/${item.id}", method = "DELETE")
                        selectedRecord = null
                        showDeleteDialog = false
                        loadData()
                        ToastCenter.show("Đã xóa bản ghi thành công")
                    } catch (_: Exception) {
                        selectedRecord = null
                        showDeleteDialog = false
                        loadData()
                        ToastCenter.show("Đã xóa bản ghi")
                    }
                }
            }
        }
    ) {
        Text("Thao tác này không thể hoàn tác. Bản ghi sẽ bị xóa khỏi cơ sở dữ liệu hệ thống.", fontSize = 13.5.sp, color = FutaColors.Slate, lineHeight = 18.sp)
    }
}
