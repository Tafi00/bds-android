package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.i18n.LocalizedDirection
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.DocumentUpload
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.messaging.ChatWebSocketManager
import vn.futaland.app.features.properties.DepositPaymentQrView
import vn.futaland.app.features.properties.DepositRequestGate
import vn.futaland.app.features.properties.ProductHoldingSheet
import vn.futaland.app.features.properties.rememberTickingNow

private val WebBrandGreen = Color(0xFF047857)
private val WebTextNavy = Color(0xFF17324D)
private val WebTextGray = Color(0xFF74777F)
private val WebCardBorder = Color(0xFFE2E8F0)

private fun JSONValue.regProperty(): JSONValue = if (this["property"].isNull) this["apartment"] else this["property"]

private fun regProjectName(reg: JSONValue): String =
    reg.regProperty()["projectName"].string.trim().ifEmpty { reg["property"]["projectName"].string.trim() }

private fun matchesStatus(reg: JSONValue, key: String): Boolean {
    val status = reg["status"].string.lowercase()
    return when (key) {
        "" -> true
        "holding" -> reg["bookingStatus"].string.lowercase().let { it.isNotEmpty() && it != "none" }
        "approved" -> status == "active" || status == "approved"
        "revoked" -> status in setOf("revoked", "expired", "cancelled", "rejected")
        else -> status == key
    }
}

/**
 * "Đăng ký bán của tôi" (iOS `AdvisorRegistrationsView`): the advisor's own sales registrations
 * from `GET /sales/registrations`, kept live through product websocket events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvisorRegistrationsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf("") }
    var selectedProject by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedReg by remember { mutableStateOf<JSONValue?>(null) }

    suspend fun load(silent: Boolean = false) {
        if (!silent && items.isEmpty()) loading = true
        try {
            items = APIClient.get().request("/sales/registrations")["data"].array
            loadError = null
        } catch (e: Exception) {
            if (!silent) {
                loadError = e.message ?: tr("Không tải được dữ liệu")
                ToastCenter.show(loadError.orEmpty(), isError = true)
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    DisposableEffect(Unit) {
        val key = "AdvisorRegistrationsScreen"
        ChatWebSocketManager.shared.connect()
        ChatWebSocketManager.shared.addProductEventListener(key) { event ->
            val targetPropId = event["propertyId"].string
            val targetRegId = event["registrationId"].string
            if (targetPropId.isEmpty() && targetRegId.isEmpty()) return@addProductEventListener
            val updates = buildMap<String, Any?> {
                listOf("bookingStatus", "activeHoldingStatus", "activeHoldingExpiresAt", "activeHoldingAdvisorId", "onlineHoldExpiresAt", "status")
                    .forEach { field -> event[field].string.takeIf { it.isNotEmpty() }?.let { put(field, it) } }
            }
            var matched = false
            val updated = items.map { reg ->
                val rId = reg["id"].string.ifEmpty { reg.id }
                val pId = reg["propertyId"].string.ifEmpty { reg["property"]["id"].string }
                if ((targetRegId.isNotEmpty() && rId == targetRegId) || (targetPropId.isNotEmpty() && pId == targetPropId)) {
                    matched = true
                    reg.withUpdates(updates)
                } else reg
            }
            if (matched) items = updated else scope.launch { load(silent = true) }
        }
        onDispose { ChatWebSocketManager.shared.removeProductEventListener(key) }
    }

    val availableProjects = items.map { regProjectName(it) }.filter { it.isNotEmpty() }.distinct().sorted()
    val filteredItems = items.filter { reg ->
        if (!matchesStatus(reg, selectedStatus)) return@filter false
        if (selectedProject.isNotEmpty() && regProjectName(reg) != selectedProject) return@filter false
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            val fields = listOf(
                reg["code"].string, reg.regProperty()["propertyCode"].string, reg["customerName"].string,
                reg["customerPhone"].string, reg.regProperty()["projectName"].string, reg["property"]["projectName"].string
            )
            if (fields.none { it.lowercase().contains(q) }) return@filter false
        }
        true
    }
    val hasFilters = selectedProject.isNotEmpty() || selectedStatus.isNotEmpty() || searchQuery.isNotEmpty()
    fun resetFilters() {
        selectedProject = ""
        selectedStatus = ""
        searchQuery = ""
    }

    Scaffold(
        topBar = { AdvisorTopBar("Đăng ký bán của tôi", onBack) },
        containerColor = Color(0xFFF7F9FC)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 8.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FutaInput(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = tr("Tìm mã căn, mã đăng ký, khách hàng..."),
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Cancel, tr("Xóa"), tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                if (availableProjects.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterPill(tr("Tất cả dự án"), selectedProject.isEmpty()) { selectedProject = "" } }
                        items(availableProjects) { project ->
                            FilterPill(project.translated("project"), selectedProject == project) {
                                selectedProject = if (selectedProject == project) "" else project
                            }
                        }
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chips = listOf(
                        "" to "Tất cả", "holding" to "Đang giữ chỗ", "pending" to "Chờ duyệt",
                        "approved" to "Hiệu lực", "deposited" to "Đã cọc", "revoked" to "Hết hạn / Huỷ"
                    )
                    items(chips) { (key, title) ->
                        FilterPill(tr(title), selectedStatus == key, count = items.count { matchesStatus(it, key) }) { selectedStatus = key }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Hiển thị {0}/{1} đăng ký", filteredItems.size, items.size), fontSize = 12.sp, color = WebTextGray, modifier = Modifier.weight(1f))
                    if (hasFilters) {
                        Text(
                            "Đặt lại",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WebBrandGreen,
                            modifier = Modifier.clickable { resetFilters() }
                        )
                    }
                }
            }
            HorizontalDivider(color = WebCardBorder)

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch { load(silent = items.isNotEmpty()); refreshing = false }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    loading && items.isEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { FutaRegistrationCardSkeleton() }
                    }
                    loadError != null && items.isEmpty() -> AdvisorErrorState(loadError.orEmpty(), onRetry = { scope.launch { load() } })
                    filteredItems.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Apartment, null, tint = Color(0xFFBFCCDB), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Không tìm thấy đăng ký bán", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WebTextNavy)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Thử thay đổi từ khoá tìm kiếm hoặc chọn dự án / trạng thái khác.",
                            fontSize = 13.sp,
                            color = WebTextGray,
                            textAlign = TextAlign.Center
                        )
                        if (hasFilters) {
                            Spacer(Modifier.height(10.dp))
                            Text("Xoá bộ lọc", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = WebBrandGreen, modifier = Modifier.clickable { resetFilters() })
                        }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it["id"].string.ifEmpty { it.id } }) { reg ->
                            RegistrationRowCard(reg) { selectedReg = reg }
                        }
                    }
                }
            }
        }
    }

    selectedReg?.let { reg ->
        RegistrationDetailSheet(
            registration = reg,
            onDismiss = { selectedReg = null },
            onDone = { load(silent = true) }
        )
    }
}

@Composable
private fun FilterPill(title: String, selected: Boolean, count: Int? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) WebBrandGreen else Color(0xFFF1F5F9))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerbatimText(
            title,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFF52637A)
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = if (selected) Color.White.copy(alpha = 0.25f) else WebCardBorder) {
                VerbatimText(
                    "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else Color(0xFF68788C),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun registrationStatusConfig(status: String): Pair<String, Color> = when (status) {
    "pending" -> "Chờ duyệt" to Color(0xFFD97706)
    "approved", "active" -> "Hiệu lực" to Color(0xFF059669)
    "deposited" -> "Đã cọc" to Color(0xFF2563EB)
    "expired" -> "Hết hạn" to Color(0xFF94A3B8)
    "revoked" -> "Đã thu hồi" to Color(0xFF64748B)
    "cancelled", "rejected" -> "Đã huỷ" to Color(0xFFDC2626)
    else -> (status.ifEmpty { "Đang xử lý" }) to WebTextGray
}

private fun holdingLabel(bs: String): Pair<String, Color> = when (bs) {
    "holding_success" -> "Đã khóa căn" to Color(0xFF218552)
    "deposited" -> "Đã nhận cọc" to Color(0xFF218552)
    "online_holding", "holding" -> "Đang giữ chỗ" to Color(0xFF8B5CF6)
    "deposit_pending", "pending_booking" -> "Chờ xác nhận cọc" to Color(0xFFD97706)
    "cancelled" -> "Đã hủy giữ chỗ" to Color(0xFFDC2626)
    "rejected" -> "Từ chối giữ chỗ" to Color(0xFFDC2626)
    else -> bs to WebTextGray
}

@Composable
private fun RegistrationRowCard(item: JSONValue, onClick: () -> Unit) {
    val prop = item.regProperty()
    val propCode = prop["propertyCode"].string.trim()
    val unitCodeDisplay = when {
        propCode.isNotEmpty() -> tr("Mã căn: {0}", propCode)
        item["code"].string.isNotBlank() -> item["code"].string.trim()
        else -> tr("Đăng ký bán")
    }
    val projectName = regProjectName(item).takeIf { it.isNotEmpty() }?.translated("project") ?: tr("Dự án FUTA Land")
    val customerName = item["customerName"].string.trim().ifEmpty { tr("Khách hàng") }
    val customerPhone = item["customerPhone"].string.trim().ifEmpty { "—" }
    val thumbnail = prop["images"].array.firstOrNull()?.let { it["original"].string.ifEmpty { it["url"].string } }.orEmpty()
        .ifEmpty { prop["image"].string }
    val specs = buildList {
        prop["block"].string.trim().takeIf { it.isNotEmpty() && it != "-" }?.let { add(tr("Tòa {0}", it)) }
        prop["size_m2"].string.trim().takeIf { it.isNotEmpty() }?.let { add("$it m²") }
        prop["bedrooms"].int.takeIf { it > 0 }?.let { add(tr("{0} PN", it)) }
        prop["direction"].string.trim().takeIf { it.isNotEmpty() }?.let { add(LocalizedDirection.name(it)) }
    }.joinToString(" · ")
    val rawPrice = prop["sellPrice"].double.takeIf { it > 0 } ?: prop["price"].double
    val priceText = if (rawPrice > 0) LocalizedPrice.full(if (rawPrice < 1000) rawPrice * 1_000_000 else rawPrice) else ""
    val bs = item["bookingStatus"].string.lowercase()
    val hasHolding = bs.isNotEmpty() && bs != "none"
    val (statusLabel, statusColor) = registrationStatusConfig(item["status"].string.lowercase())

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, WebCardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    VerbatimText(unitCodeDisplay, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WebTextNavy)
                    if (item["code"].string.isNotEmpty()) VerbatimText(item["code"].string, fontSize = 11.sp, color = Color(0xFF8C99AD))
                }
                AdvisorBadge(tr(statusLabel), statusColor)
            }
            HorizontalDivider(color = WebCardBorder, modifier = Modifier.padding(horizontal = 14.dp))
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF0F4F8))
                        .border(1.dp, WebCardBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail.isNotEmpty()) {
                        AsyncImage(model = DocumentUpload.absoluteUrl(thumbnail), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Default.Apartment, null, tint = Color(0xFFBFCCDB), modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VerbatimText(projectName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = WebTextNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (specs.isNotEmpty()) VerbatimText(specs, fontSize = 12.sp, color = Color(0xFF52637A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = WebTextGray, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        VerbatimText("$customerName · $customerPhone", fontSize = 12.sp, color = Color(0xFF334052), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (hasHolding) {
                        val (label, color) = holdingLabel(bs)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, null, tint = color, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Giữ chỗ: {0}", tr(label)), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAFCFF))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val expires = shortDate(item["expiresAt"].string)
                if (expires.isNotEmpty()) {
                    Icon(Icons.Default.CalendarMonth, null, tint = WebTextGray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Hạn: {0}", expires), fontSize = 11.sp, color = WebTextGray)
                }
                Spacer(Modifier.weight(1f))
                if (priceText.isNotEmpty()) {
                    VerbatimText(priceText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WebBrandGreen, modifier = Modifier.padding(end = 6.dp))
                }
                Text("Chi tiết", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WebTextNavy)
                Icon(Icons.Default.ChevronRight, null, tint = WebTextNavy, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Registration detail with refresh / payment QR / cancel and the deposit request entry. */
@Composable
private fun RegistrationDetailSheet(
    registration: JSONValue,
    onDismiss: () -> Unit,
    onDone: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentReg by remember(registration) { mutableStateOf(registration) }
    var paymentQr by remember { mutableStateOf<JSONValue?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var showDepositRequest by remember { mutableStateOf(false) }
    val regId = currentReg["id"].string.ifEmpty { currentReg.id }

    val sellStatusText = when (currentReg["status"].string.lowercase()) {
        "active", "approved" -> "Đang mở quyền bán"
        "pending" -> "Chờ duyệt quyền bán"
        "revoked", "expired", "cancelled", "rejected" -> "Đã hết hạn / Thu hồi"
        else -> currentReg["status"].string.ifEmpty { "Chưa đăng ký" }
    }
    val bookingStatusText = when (currentReg["bookingStatus"].string.lowercase()) {
        "", "none", "available" -> "Chưa giữ chỗ"
        "online_holding" -> "Đang giữ chỗ (15 phút)"
        "pending_booking" -> "Chờ xác nhận cọc"
        "cancel_requested" -> "Đang chờ duyệt huỷ giữ chỗ"
        "holding_success" -> "ERP đã khóa căn"
        "deposited" -> "Đã thu cọc"
        "commission_pending", "commission_paid", "purchased" -> "Giao dịch thành công"
        "rejected" -> "Bị từ chối giữ chỗ"
        "cancelled" -> "Đã huỷ giữ chỗ"
        else -> currentReg["bookingStatus"].string
    }
    val rejectReason = currentReg["rejectReason"].string.ifEmpty { currentReg.regProperty()["activeHoldingRejectReason"].string }

    fun refreshHold() {
        scope.launch {
            isProcessing = true
            try {
                val res = APIClient.get().request("/sales/registrations/$regId/hold/refresh", method = "POST")
                if (!res["data"].isNull) currentReg = res["data"]
                ToastCenter.show(tr("Đã gia hạn giữ chỗ thành công"))
                onDone()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không gia hạn được giữ chỗ"), isError = true)
            } finally {
                isProcessing = false
            }
        }
    }

    fun loadPaymentQr() {
        scope.launch {
            isProcessing = true
            try {
                paymentQr = APIClient.get().request("/sales/registrations/$regId/payment-qr")["data"]
                ToastCenter.show(tr("Đã tạo mã QR thanh toán tiền cọc"))
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không tạo được mã QR"), isError = true)
            } finally {
                isProcessing = false
            }
        }
    }

    fun cancelRegistration() {
        scope.launch {
            isProcessing = true
            try {
                val body = buildJsonObject { put("reason", "Khách hàng đổi ý") }.toString()
                APIClient.get().request("/sales/registrations/$regId/cancel", method = "POST", bodyJson = body)
                ToastCenter.show(tr("Đã huỷ đăng ký bán"))
                onDone()
                onDismiss()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không huỷ được đăng ký"), isError = true)
            } finally {
                isProcessing = false
            }
        }
    }

    FutaBottomSheet(
        visible = !showDepositRequest,
        onDismiss = onDismiss,
        title = tr("Đăng ký: {0}", currentReg["code"].string)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            AdvisorSection(header = "Mã & Căn hộ") {
                AdvisorLabeledRow("Mã đăng ký", currentReg["code"].string)
                AdvisorLabeledRow("Mã căn", currentReg.regProperty()["propertyCode"].string)
                AdvisorLabeledRow("Quyền bán", tr(sellStatusText))
                AdvisorLabeledRow("Giữ chỗ", tr(bookingStatusText))
                AdvisorLabeledRow("Thời hạn giữ", shortDate(currentReg["expiresAt"].string))
            }
            AdvisorSection(header = "Thông tin khách hàng") {
                AdvisorLabeledRow("Họ và tên", currentReg["customerName"].string)
                AdvisorLabeledRow("Số điện thoại", currentReg["customerPhone"].string)
                if (currentReg["customerEmail"].string.isNotEmpty()) AdvisorLabeledRow("Email", currentReg["customerEmail"].string)
                if (currentReg["customerCccd"].string.isNotEmpty()) AdvisorLabeledRow("CCCD", currentReg["customerCccd"].string)
            }
            if (rejectReason.isNotEmpty()) {
                AdvisorSection(header = "Lý do từ chối") {
                    VerbatimText(rejectReason, fontSize = 13.sp, color = Color(0xFFDC2626))
                }
            }
            if (currentReg["notes"].string.isNotEmpty()) {
                AdvisorSection(header = "Ghi chú xử lý") {
                    VerbatimText(currentReg["notes"].string, fontSize = 13.sp, color = FutaColors.Navy)
                }
            }
            paymentQr?.let { qr ->
                AdvisorSection(header = "Thanh toán tiền đặt cọc (VietQR)") { DepositPaymentQrView(qr) }
            }
            val now = rememberTickingNow()
            if (DepositRequestGate.canRequestDeposit(currentReg, now)) {
                val remaining = DepositRequestGate.remainingSeconds(currentReg, now)
                AdvisorSection(
                    header = "Yêu cầu xác nhận cọc",
                    footer = remaining?.let {
                        tr("Thời gian giữ chỗ online còn lại: {0}. Hoàn tất hồ sơ và gửi yêu cầu cọc trước khi hết giờ.", DepositRequestGate.countdownText(it))
                    }
                ) {
                    DetailAction(Icons.Default.Payments, "Gửi yêu cầu xác nhận cọc", FutaColors.BrandGreen, enabled = !isProcessing) {
                        showDepositRequest = true
                    }
                }
            }
            AdvisorSection(header = "Hành động") {
                DetailAction(Icons.Default.Refresh, "Gia hạn / Làm mới giữ chỗ", FutaColors.BrandGreen, enabled = !isProcessing) { refreshHold() }
                HorizontalDivider(color = FutaColors.PanelDivider)
                DetailAction(Icons.Default.QrCode, "Lấy mã QR thanh toán cọc", FutaColors.BrandGreen, enabled = !isProcessing) { loadPaymentQr() }
            }
            // Destructive action kept apart from the others.
            AdvisorSection {
                DetailAction(Icons.Default.Cancel, "Huỷ đăng ký giữ chỗ", Color(0xFFDC2626), enabled = !isProcessing) { confirmCancel = true }
            }
        }
    }

    FutaDialog(
        visible = confirmCancel,
        onDismiss = { confirmCancel = false },
        title = "Huỷ đăng ký giữ chỗ?",
        confirmText = "Xác nhận huỷ",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Không",
        onConfirm = { cancelRegistration() }
    ) {
        Text("Thao tác này sẽ giải phóng căn hộ và xoá phiên giữ chỗ của bạn.", fontSize = 13.5.sp, color = FutaColors.Slate)
    }

    if (showDepositRequest) {
        ProductHoldingSheet(
            product = currentReg,
            onDismiss = { showDepositRequest = false },
            onDone = onDone,
            onDepositSubmitted = { updated ->
                if (updated["id"].string.isNotEmpty()) currentReg = updated
                paymentQr = null
            }
        )
    }
}

@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) color else color.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) color else color.copy(alpha = 0.4f))
    }
}
