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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

@Composable
fun AdminCustomersScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }

    var selectedStatus by remember { mutableStateOf("all") }
    var selectedSegment by remember { mutableStateOf("all") }
    var showFilterSheet by remember { mutableStateOf(false) }

    var selectedCustomer by remember { mutableStateOf<JSONValue?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val statusFilters = listOf(
        "all" to "Tất cả",
        "Khách mới" to "Khách mới",
        "Đang thương lượng" to "Đang thương lượng",
        "Đã cọc chờ ký HĐ" to "Đã cọc",
        "Đã liên hệ" to "Đã liên hệ",
        "Đợi xem nhà" to "Đợi xem nhà",
        "Chờ chăm lại" to "Chờ chăm lại"
    )

    fun loadCustomers(searchQuery: String = search) {
        scope.launch {
            loading = true
            try {
                val q = searchQuery.trim()
                var url = "/customers?page=$page&limit=20"
                if (q.isNotEmpty()) {
                    url += "&search=${java.net.URLEncoder.encode(q, "UTF-8")}"
                }
                if (selectedStatus != "all") {
                    url += "&customerStatus=${java.net.URLEncoder.encode(selectedStatus, "UTF-8")}"
                }
                if (selectedSegment != "all") {
                    url += "&customerSegment=${java.net.URLEncoder.encode(selectedSegment, "UTF-8")}"
                }
                val res = APIClient.get().request(url)
                val list = res["data"].array.ifEmpty { res["customers"].array }
                customers = list
                val total = res["pagination"]["total"].int
                totalCount = if (total > 0) total else list.size
                totalPages = maxOf(1, res["pagination"]["totalPages"].int.takeIf { it > 0 } ?: 1)
            } catch (_: Exception) {
                customers = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(search, page, selectedStatus, selectedSegment) {
        kotlinx.coroutines.delay(250)
        loadCustomers(search)
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
                        text = "Khách hàng",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )
                    FutaHeaderIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Thêm mới",
                        tint = FutaColors.BrandGreen,
                        onClick = { showCreateDialog = true }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Filter Trigger Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        FutaInput(
                            value = search,
                            onValueChange = {
                                search = it
                                page = 1
                            },
                            placeholder = "Tìm tên, SĐT, email...",
                            leadingIcon = Icons.Default.Search,
                            trailingIcon = if (search.isNotEmpty()) {
                                {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Xóa",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                search = ""
                                                page = 1
                                            },
                                        tint = FutaColors.Slate
                                    )
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedStatus != "all" || selectedSegment != "all") Color(0xFFEAF5EF) else Color.White,
                        border = BorderStroke(1.dp, if (selectedStatus != "all" || selectedSegment != "all") FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { showFilterSheet = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Bộ lọc",
                                tint = if (selectedStatus != "all" || selectedSegment != "all") FutaColors.BrandGreen else FutaColors.Slate
                            )
                        }
                    }
                }
            }

            // Filter Horizontal Scroll Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statusFilters.forEach { (key, label) ->
                        val isSelected = selectedStatus == key
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable {
                                selectedStatus = key
                                page = 1
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Total Count Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${if (totalCount > 0) totalCount else customers.size} khách hàng",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Slate
                    )
                    if (selectedStatus != "all" || selectedSegment != "all") {
                        Text(
                            "Đặt lại bộ lọc",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FutaColors.BrandGreen,
                            modifier = Modifier.clickable {
                                selectedStatus = "all"
                                selectedSegment = "all"
                                page = 1
                            }
                        )
                    }
                }
            }

            // Customer Feed
            if (loading && customers.isEmpty()) {
                items(5) {
                    FutaSkeletonBlock(height = 80.dp, radius = 16.dp)
                }
            } else if (customers.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không tìm thấy khách hàng",
                        message = "Thêm hồ sơ khách hàng mới hoặc thay đổi từ khóa tìm kiếm."
                    )
                }
            } else {
                itemsIndexed(customers, key = { idx, item -> item.id.ifEmpty { "c-$idx" } }) { _, customer ->
                    CustomerCardRowItem(
                        customer = customer,
                        onClick = { selectedCustomer = customer }
                    )
                }

                if (totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trang $page / $totalPages", fontSize = 12.sp, color = FutaColors.Slate)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FutaButton(
                                    text = "Trước",
                                    variant = FutaButtonVariant.OUTLINE,
                                    enabled = page > 1,
                                    onClick = { if (page > 1) page-- }
                                )
                                FutaButton(
                                    text = "Sau",
                                    variant = FutaButtonVariant.OUTLINE,
                                    enabled = page < totalPages,
                                    onClick = { if (page < totalPages) page++ }
                                )
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

    // Customer Detail BottomSheet (Full Parity with tabs)
    selectedCustomer?.let { c ->
        CustomerDetailSheet(
            customer = c,
            onDismiss = { selectedCustomer = null },
            onCustomerUpdated = {
                selectedCustomer = null
                loadCustomers()
            }
        )
    }

    // Create Customer Dialog
    if (showCreateDialog) {
        CreateCustomerDialog(
            onDismiss = { showCreateDialog = false },
            onCreated = {
                showCreateDialog = false
                loadCustomers()
            }
        )
    }

    // Filter Sheet
    if (showFilterSheet) {
        CustomerFilterDialog(
            selectedStatus = selectedStatus,
            selectedSegment = selectedSegment,
            onDismiss = { showFilterSheet = false },
            onApply = { st, sg ->
                selectedStatus = st
                selectedSegment = sg
                page = 1
                showFilterSheet = false
            }
        )
    }
}

@Composable
private fun CustomerCardRowItem(
    customer: JSONValue,
    onClick: () -> Unit
) {
    val name = customer["customerName"].string.ifEmpty { customer["name"].string.ifEmpty { "Khách hàng" } }
    val phone = customer["customerPhone"].string.ifEmpty { customer["phone"].string }
    val email = customer["customerEmail"].string.ifEmpty { customer["email"].string }
    val status = customer["customerStatus"].string.ifEmpty { "Khách mới" }
    val segment = customer["customerSegment"].string
    val hasTransaction = customer["hasTransaction"].bool
    val aptTypes = customer["interestedApartmentTypes"].array.map { it.string }.filter { it.isNotEmpty() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = CircleShape, color = Color(0xFFEAF5EF), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name.trim().take(2).uppercase().ifEmpty { "KH" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = CircleShape, color = Color(0xFFEAF5EF)) {
                            Text(status, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(phone, fontSize = 12.sp, color = FutaColors.Slate)
                        if (email.isNotEmpty()) {
                            Text("•", fontSize = 12.sp, color = FutaColors.Slate)
                            Text(email, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (hasTransaction || segment.isNotEmpty() || aptTypes.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasTransaction) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEAF5EF)) {
                            Text("Đã giao dịch", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (segment.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEFF6FF)) {
                            Text(segment, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E40AF), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    aptTypes.take(2).forEach { t ->
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFAF5FF)) {
                            Text(t, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7E22CE), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FutaColors.Slate.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailSheet(
    customer: JSONValue,
    onDismiss: () -> Unit,
    onCustomerUpdated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf(customer) }
    var activities by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var newNote by remember { mutableStateOf("") }
    var isSavingNote by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("info") } // "info", "care", "activities", "documents"

    val phone = detail["customerPhone"].string
    val name = detail["customerName"].string.ifEmpty { "Khách hàng" }
    val email = detail["email"].string
    val status = detail["customerStatus"].string.ifEmpty { "Khách mới" }

    fun loadFullDetail() {
        scope.launch {
            try {
                val encoded = java.net.URLEncoder.encode(phone, "UTF-8")
                val res = APIClient.get().request("/customers/$encoded")
                if (!res["data"].isNull) detail = res["data"]
                val actRes = APIClient.get().request("/customers/$encoded/activities")
                activities = actRes["data"].array
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(phone) {
        loadFullDetail()
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Hồ sơ khách hàng"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Surface(shape = CircleShape, color = Color(0xFFEAF5EF)) {
                            Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    Text("Số điện thoại: $phone", fontSize = 13.sp, color = FutaColors.Slate)
                    if (email.isNotEmpty()) Text("Email: $email", fontSize = 13.sp, color = FutaColors.Slate)
                }
            }

            // Tab Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("info" to "Hồ sơ", "care" to "Chăm sóc", "activities" to "Hoạt động", "documents" to "Giấy tờ").forEach { (tabKey, tabLabel) ->
                    val isSel = selectedTab == tabKey
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = tabKey }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(tabLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else FutaColors.Slate)
                        }
                    }
                }
            }

            // Tab 1: Info (Demand & Assignment)
            if (selectedTab == "info") {
                val segment = detail["customerSegment"].string
                val aptTypes = detail["interestedApartmentTypes"].array.map { it.string }.filter { it.isNotEmpty() }
                val furnitures = detail["interestedFurniture"].array.map { it.string }.filter { it.isNotEmpty() }

                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Nhu cầu tìm kiếm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (segment.isNotEmpty()) Text("• Phân khúc: $segment", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (aptTypes.isNotEmpty()) Text("• Loại căn: ${aptTypes.joinToString(", ")}", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (furnitures.isNotEmpty()) Text("• Nội thất: ${furnitures.joinToString(", ")}", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (segment.isEmpty() && aptTypes.isEmpty() && furnitures.isEmpty()) {
                            Text("Chưa ghi nhận nhu cầu cụ thể", fontSize = 12.sp, color = FutaColors.Slate)
                        }
                    }
                }

                val sales = detail["sales"].array
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Nhân sự phụ trách", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (sales.isEmpty()) {
                            Text("Chưa phân công nhân sự", fontSize = 12.sp, color = FutaColors.Slate)
                        } else {
                            sales.forEach { s ->
                                Text("• ${s["name"].string} (${s["phone"].string.ifEmpty { s["email"].string }})", fontSize = 12.5.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }
            }

            // Tab 2: Care Notes
            if (selectedTab == "care") {
                FutaInput(
                    value = newNote,
                    onValueChange = { newNote = it },
                    placeholder = "Nhập nội dung tương tác chăm sóc...",
                    modifier = Modifier.fillMaxWidth()
                )
                FutaButton(
                    text = if (isSavingNote) "Đang lưu..." else "Gửi ghi chú",
                    enabled = newNote.trim().isNotEmpty() && !isSavingNote,
                    onClick = {
                        scope.launch {
                            isSavingNote = true
                            try {
                                val encoded = java.net.URLEncoder.encode(phone, "UTF-8")
                                val cleanNote = newNote.trim().replace("\"", "\\\"")
                                APIClient.get().request(
                                    "/customers/$encoded/notes",
                                    method = "POST",
                                    bodyJson = "{\"content\":\"$cleanNote\"}"
                                )
                                newNote = ""
                                loadFullDetail()
                                onCustomerUpdated()
                            } catch (_: Exception) {}
                            finally { isSavingNote = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                val notes = detail["notes"].array
                if (notes.isEmpty()) {
                    Text("Chưa có ghi chú nào", fontSize = 12.sp, color = FutaColors.Slate)
                } else {
                    notes.forEach { n ->
                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(n["content"].string, fontSize = 12.5.sp, color = FutaColors.Navy)
                                Text("${n["authorName"].string.ifEmpty { "Hệ thống" }} · ${n["createdAt"].string.take(10)}", fontSize = 10.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }
            }

            // Tab 3: Activities
            if (selectedTab == "activities") {
                if (activities.isEmpty()) {
                    Text("Chưa có lịch sử hoạt động", fontSize = 12.sp, color = FutaColors.Slate)
                } else {
                    activities.forEach { a ->
                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(a["displayName"].string.ifEmpty { a["type"].string }, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                if (a["description"].string.isNotEmpty()) {
                                    Text(a["description"].string, fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                                Text(a["createdAt"].string.take(10), fontSize = 10.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }
            }

            // Tab 4: Documents / CCCD
            if (selectedTab == "documents") {
                val cccd = detail["cccd"].string.ifEmpty { detail["customerCccd"].string }
                val permAddr = detail["permanentAddress"].string
                val contAddr = detail["contactAddress"].string
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Thông tin CCCD / Định danh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (cccd.isNotEmpty()) Text("• Số CCCD: $cccd", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (permAddr.isNotEmpty()) Text("• Thường trú: $permAddr", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (contAddr.isNotEmpty()) Text("• Liên hệ: $contAddr", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (cccd.isEmpty() && permAddr.isEmpty() && contAddr.isEmpty()) {
                            Text("Chưa cập nhật thông tin định danh", fontSize = 12.sp, color = FutaColors.Slate)
                        }
                    }
                }
            }

            FutaButton(
                text = "Đóng",
                variant = FutaButtonVariant.OUTLINE,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CreateCustomerDialog(
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var customerSegment by remember { mutableStateOf("Gia đình") }
    var customerStatus by remember { mutableStateOf("Khách mới") }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm khách hàng mới", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FutaInput(value = name, onValueChange = { name = it }, placeholder = "Họ và tên *", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                FutaInput(value = phone, onValueChange = { phone = it }, placeholder = "Số điện thoại *", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                FutaInput(value = email, onValueChange = { email = it }, placeholder = "Email (tuỳ chọn)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done))
            }
        },
        confirmButton = {
            FutaButton(
                text = if (isSaving) "Đang lưu..." else "Lưu",
                enabled = name.isNotBlank() && phone.isNotBlank() && !isSaving,
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val bodyMap = mutableListOf(
                                "\"customerName\":\"${name.trim().replace("\"", "\\\"")}\"",
                                "\"customerPhone\":\"${phone.trim()}\"",
                                "\"customerSegment\":\"$customerSegment\"",
                                "\"customerStatus\":\"$customerStatus\""
                            )
                            if (email.isNotBlank()) bodyMap.add("\"email\":\"${email.trim()}\"")
                            val body = "{" + bodyMap.joinToString(",") + "}"
                            APIClient.get().request("/customers", method = "POST", bodyJson = body)
                            onCreated()
                        } catch (_: Exception) {}
                        finally { isSaving = false }
                    }
                }
            )
        },
        dismissButton = {
            FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = onDismiss)
        }
    )
}

@Composable
private fun CustomerFilterDialog(
    selectedStatus: String,
    selectedSegment: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit
) {
    var status by remember { mutableStateOf(selectedStatus) }
    var segment by remember { mutableStateOf(selectedSegment) }

    val statuses = listOf(
        "all" to "Tất cả trạng thái",
        "Khách mới" to "Khách mới",
        "Chưa liên hệ được" to "Chưa liên hệ được",
        "Đã liên hệ" to "Đã liên hệ",
        "Đang thương lượng" to "Đang thương lượng",
        "Đợi xem nhà" to "Đợi xem nhà",
        "Đã xem nhà" to "Đã xem nhà",
        "Đã cọc chờ ký HĐ" to "Đã cọc chờ ký HĐ"
    )

    val segments = listOf(
        "all" to "Tất cả phân khúc",
        "Gia đình" to "Gia đình",
        "Sinh viên" to "Sinh viên",
        "Ở ghép" to "Ở ghép",
        "NNN" to "Khách nước ngoài (NNN)",
        "NVVP" to "Nhân viên văn phòng (NVVP)",
        "Tiềm năng" to "Tiềm năng",
        "VIP" to "VIP",
        "Đầu tư" to "Đầu tư"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bộ lọc khách hàng", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Trạng thái", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                statuses.forEach { (k, lbl) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { status = k }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lbl, fontSize = 12.5.sp, color = if (status == k) FutaColors.BrandGreen else FutaColors.Navy)
                        if (status == k) Icon(Icons.Default.Check, contentDescription = null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Phân khúc", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                segments.forEach { (k, lbl) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { segment = k }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lbl, fontSize = 12.5.sp, color = if (segment == k) FutaColors.BrandGreen else FutaColors.Navy)
                        if (segment == k) Icon(Icons.Default.Check, contentDescription = null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            FutaButton(
                text = "Áp dụng",
                onClick = { onApply(status, segment) }
            )
        },
        dismissButton = {
            FutaButton(text = "Đóng", variant = FutaButtonVariant.OUTLINE, onClick = onDismiss)
        }
    )
}
