package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.i18n.LocalizedGender
import vn.futaland.app.core.i18n.translated
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.messaging.ChatWebSocketManager
import vn.futaland.app.features.properties.PropertyFormatters
import vn.futaland.app.features.salesadmin.*

// Mirrors iOS Features/SalesAdmin/AdminRegistrationsView.swift:
// list (/sales/registrations) with holding/rights tabs → detail with admin actions
// (PATCH …/status, PATCH …/booking-status, GET …/payment-qr, POST …/hold/refresh|cancel).

enum class RegistrationTab(val label: String) {
    HOLDING("Giữ chỗ trực tuyến"),
    RIGHTS("Quyền bán TVV")
}

/** Kept for other files in this package that still use the short name. */
typealias Bool = Boolean

private enum class RegistrationSort(val title: String) {
    NEWEST("Mới nhất"),
    OLDEST("Cũ nhất"),
    CODE_ASC("Mã căn: A → Z")
}

private data class RegistrationRoute(val id: String, val version: Int = 0)

private fun regProject(r: JSONValue): String {
    val apt = r.nestedProperty
    return apt["project"]["displayName"].string.ifEmpty { apt["projectName"].string }
}

private fun regCode(r: JSONValue): String = r.nestedProperty["propertyCode"].string

private fun regTime(r: JSONValue): String = r["holdingSubmittedAt"].string.ifEmpty { r["createdAt"].string }

private fun isErpRegistration(r: JSONValue): Boolean = r.nestedProperty["source"].string == "ERP" || r["erpHoldId"].string.isNotEmpty()

@Composable
fun AdminRegistrationsScreen(
    onBack: () -> Unit,
    initialPropertyId: String? = null
) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<RegistrationRoute>() }
    var registrations by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var activeTab by rememberSaveable { mutableStateOf(RegistrationTab.HOLDING) }
    var holdingFilter by rememberSaveable { mutableStateOf("all") }
    var rightsFilter by rememberSaveable { mutableStateOf("all") }
    var projectFilter by rememberSaveable { mutableStateOf("all") }
    var onlyCompeting by rememberSaveable { mutableStateOf(false) }
    var sort by remember { mutableStateOf(RegistrationSort.NEWEST) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isAdmin = AppSession.shared.role == "admin"

    suspend fun fetch(silent: Boolean) {
        if (!silent && registrations.isEmpty()) loading = true
        try {
            val q = search.trim()
            registrations = APIClient.get().request("/sales/registrations", query = if (q.isEmpty()) emptyMap() else mapOf("search" to q))["data"].array
            loadError = null
        } catch (e: Exception) {
            if (!silent) loadError = e.message ?: tr("Không thể tải hồ sơ đăng ký")
        } finally {
            loading = false
        }
    }

    fun reload() { scope.launch { fetch(silent = true) } }

    LaunchedEffect(search) {
        if (registrations.isNotEmpty()) delay(300)
        fetch(silent = registrations.isNotEmpty())
    }

    // Deep link (notification tap /admin/sales-registrations/{propertyId}): open the matching dossier.
    var deepLinkHandled by remember { mutableStateOf(false) }
    LaunchedEffect(registrations, initialPropertyId) {
        val target = initialPropertyId?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        if (deepLinkHandled || registrations.isEmpty()) return@LaunchedEffect
        val match = registrations.firstOrNull { reg ->
            reg["propertyId"].string.equals(target, ignoreCase = true) ||
                reg["property"]["id"].string.equals(target, ignoreCase = true) ||
                reg["unitCode"].string.equals(target, ignoreCase = true) ||
                regCode(reg).equals(target, ignoreCase = true)
        }
        if (match != null) {
            deepLinkHandled = true
            stack.push(RegistrationRoute(match.id))
        }
    }

    DisposableEffect(Unit) {
        ChatWebSocketManager.shared.connect()
        val listenerKey = "AdminRegistrationsScreen"
        ChatWebSocketManager.shared.addProductEventListener(listenerKey) { event ->
            val targetPropId = event["propertyId"].string
            val targetRegId = event["registrationId"].string
            if (targetPropId.isEmpty() && targetRegId.isEmpty()) return@addProductEventListener
            var matched = false
            val updated = registrations.map { reg ->
                val rId = reg["id"].string.ifEmpty { reg.id }
                val pId = reg["propertyId"].string.ifEmpty { reg["property"]["id"].string }
                if ((targetRegId.isNotEmpty() && rId == targetRegId) || (targetPropId.isNotEmpty() && pId == targetPropId)) {
                    matched = true
                    val updates = mutableMapOf<String, Any?>()
                    listOf("bookingStatus", "activeHoldingStatus", "activeHoldingExpiresAt", "activeHoldingAdvisorId", "onlineHoldExpiresAt", "status").forEach { key ->
                        event[key].string.takeIf { it.isNotEmpty() }?.let { updates[key] = it }
                    }
                    reg.withUpdates(updates)
                } else reg
            }
            if (matched) registrations = updated else reload()
        }
        onDispose { ChatWebSocketManager.shared.removeProductEventListener(listenerKey) }
    }

    // Units with more than one registration are "competing" (iOS cachedCompetingUnitCodes).
    val competingCodes = remember(registrations) {
        registrations.map { regCode(it) }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }
    val availableProjects = remember(registrations) { registrations.map { regProject(it) }.filter { it.isNotEmpty() }.distinct().sorted() }
    val onlineHoldingCount = registrations.count { it["bookingStatus"].string == "online_holding" }
    val pendingDepositCount = registrations.count { it["bookingStatus"].string == "pending_booking" }
    val activeRightsCount = registrations.count { it["status"].string == "active" }

    val filtered = remember(registrations, search, activeTab, holdingFilter, rightsFilter, projectFilter, onlyCompeting, sort, competingCodes) {
        val q = search.trim()
        registrations.filter { r ->
            val matchesSearch = q.isEmpty() || listOf(regCode(r), r["code"].string, r["advisor"]["name"].string, r["customerName"].string, r["customerPhone"].string, regProject(r))
                .joinToString(" ").contains(q, ignoreCase = true)
            val matchesProject = projectFilter == "all" || regProject(r) == projectFilter
            val matchesCompeting = !onlyCompeting || competingCodes.contains(regCode(r))
            val matchesTab = if (activeTab == RegistrationTab.HOLDING) {
                holdingFilter == "all" || r["bookingStatus"].string.ifEmpty { "none" } == holdingFilter
            } else {
                rightsFilter == "all" || r["status"].string.ifEmpty { "pending" } == rightsFilter
            }
            matchesSearch && matchesProject && matchesCompeting && matchesTab
        }.let { list ->
            when (sort) {
                RegistrationSort.NEWEST -> list.sortedByDescending { regTime(it) }
                RegistrationSort.OLDEST -> list.sortedBy { regTime(it) }
                RegistrationSort.CODE_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { regCode(it) })
            }
        }
    }
    val slice = filtered.pageSlice(page, 20)
    val sheetFilterCount = (if (projectFilter != "all") 1 else 0) + (if (onlyCompeting) 1 else 0)

    ScreenStackHost(
        stack = stack,
        base = {
            Scaffold(
                containerColor = FutaColors.PageBg,
                topBar = {
                    SalesAdminTopBar(title = if (isAdmin) "Duyệt bán & Giữ chỗ" else "Đăng ký bán của tôi", onBack = onBack) {
                        SortMenuButton(RegistrationSort.entries, sort, { it.title }, { sort = it; page = 1 }, isDefault = sort == RegistrationSort.NEWEST)
                        BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), sheetFilterCount) { showFilterSheet = true }
                    }
                }
            ) { padding ->
                when {
                    loading && registrations.isEmpty() -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp); FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp) } }
                        repeat(3) { FutaRegistrationCardSkeleton() }
                    }
                    loadError != null && registrations.isEmpty() -> AdminErrorState(loadError!!, { scope.launch { fetch(false) } }, Modifier.padding(padding))
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item { AdminSearchField(search, { search = it; page = 1 }, "Tìm theo mã căn, TVV, khách hàng, SĐT…") }
                        item {
                            MetricGrid(
                                listOf(
                                    { m -> SalesMetricCard("Tổng hồ sơ", "${registrations.size}", Icons.Default.Description, FutaColors.BrandGreen, m) },
                                    { m -> SalesMetricCard("Giữ chỗ online", "$onlineHoldingCount", Icons.Default.Shield, Color(0xFF2563EB), m) },
                                    { m -> SalesMetricCard("Chờ duyệt cọc", "$pendingDepositCount", Icons.Default.HourglassTop, FutaColors.BrandOrange, m) },
                                    { m -> SalesMetricCard("Căn cạnh tranh", "${competingCodes.size}", Icons.Default.LocalFireDepartment, Color(0xFFDC2626), m, selected = onlyCompeting, onClick = { onlyCompeting = !onlyCompeting; page = 1 }) }
                                )
                            )
                        }
                        item {
                            FutaSegmentTabs(
                                items = RegistrationTab.entries,
                                selectedItem = activeTab,
                                onSelect = { activeTab = it; page = 1 },
                                titleFor = { tr(it.label) },
                                modifier = Modifier.padding(horizontal = 0.dp)
                            )
                        }
                        item {
                            QuickChipRow {
                                if (activeTab == RegistrationTab.HOLDING) {
                                    listOf(
                                        "all" to tr("Tất cả ({0})", registrations.size),
                                        "online_holding" to tr("Giữ chỗ online ({0})", onlineHoldingCount),
                                        "pending_booking" to tr("Chờ duyệt cọc ({0})", pendingDepositCount),
                                        "holding_success" to tr("Giữ chỗ thành công"),
                                        "deposited" to tr("Đã nộp cọc"),
                                        "commission_paid" to tr("Đã chi hoa hồng"),
                                        "cancelled" to tr("Đã hủy")
                                    ).forEach { (id, title) -> QuickChip(title, holdingFilter == id) { holdingFilter = id; page = 1 } }
                                } else {
                                    listOf(
                                        "all" to tr("Tất cả ({0})", registrations.size),
                                        "active" to tr("Đang có quyền bán ({0})", activeRightsCount),
                                        "pending" to tr("Chờ duyệt"),
                                        "rejected" to tr("Đã từ chối"),
                                        "revoked" to tr("Đã thu hồi")
                                    ).forEach { (id, title) -> QuickChip(title, rightsFilter == id) { rightsFilter = id; page = 1 } }
                                }
                            }
                        }
                        val applied = buildList {
                            if (projectFilter != "all") add(AppliedFilter(projectFilter.translated("project")) { projectFilter = "all" })
                            if (onlyCompeting) add(AppliedFilter(tr("Căn tranh chấp ({0})", competingCodes.size)) { onlyCompeting = false })
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tr("Hiển thị {0}/{1} đăng ký", filtered.size, registrations.size), fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = FutaColors.Slate)
                                AppliedFilterChips(applied) { projectFilter = "all"; onlyCompeting = false }
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                if (registrations.isEmpty() && search.isEmpty()) {
                                    FutaEmptyState(title = "Chưa có hồ sơ", message = "Chưa có hồ sơ đăng ký bán hoặc giữ chỗ nào.", icon = Icons.Default.Description)
                                } else {
                                    FutaEmptyState(
                                        title = "Không có hồ sơ nào",
                                        message = "Không tìm thấy hồ sơ đăng ký bán hoặc giữ chỗ nào phù hợp với bộ lọc.",
                                        icon = Icons.Default.SearchOff,
                                        actionButton = {
                                            FutaButton(text = "Xóa bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = {
                                                search = ""; projectFilter = "all"; onlyCompeting = false; holdingFilter = "all"; rightsFilter = "all"
                                            })
                                        }
                                    )
                                }
                            }
                        } else {
                            items(slice.items) { reg ->
                                RegistrationCardRow(
                                    registration = reg,
                                    isCompeting = competingCodes.contains(regCode(reg)),
                                    onClick = { stack.push(RegistrationRoute(reg.id)) }
                                )
                            }
                            item {
                                PaginationBar(
                                    slice.page, slice.totalPages, tr("Hiển thị {0}–{1} / {2} hồ sơ", slice.start, slice.end, slice.total),
                                    onPrevious = { page = slice.page - 1; scope.launch { listState.scrollToItem(0) } },
                                    onNext = { page = slice.page + 1; scope.launch { listState.scrollToItem(0) } }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    ) { route ->
        AdminRegistrationDetailScreen(
            registrationId = route.id,
            initial = registrations.firstOrNull { it.id == route.id },
            onBack = { stack.pop() },
            onChanged = { reload() }
        )
    }

    if (showFilterSheet) {
        var draftProject by remember { mutableStateOf(projectFilter) }
        var draftCompeting by remember { mutableStateOf(onlyCompeting) }
        FilterSheet(
            visible = true,
            title = "Bộ lọc hồ sơ",
            applyTitle = tr("Áp dụng"),
            canReset = draftProject != "all" || draftCompeting,
            onReset = { draftProject = "all"; draftCompeting = false },
            onApply = { projectFilter = draftProject; onlyCompeting = draftCompeting; page = 1; showFilterSheet = false },
            onDismiss = { showFilterSheet = false }
        ) {
            AdminSelectField(
                "Dự án", draftProject,
                listOf(SelectOption("all", "Tất cả dự án")) + availableProjects.map { SelectOption(it, it.translated("project")) },
                { draftProject = it }
            )
            FormToggle(tr("Căn tranh chấp ({0})", competingCodes.size), draftCompeting, { draftCompeting = it }, "Chỉ hiện các căn có từ 2 hồ sơ đăng ký trở lên.")
        }
    }
}

// ============================================================================
// Detail
// ============================================================================

@Composable
private fun AdminRegistrationDetailScreen(registrationId: String, initial: JSONValue?, onBack: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reg by remember { mutableStateOf(initial ?: JSONValue.EmptyObject) }
    var loading by remember { mutableStateOf(initial == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var showApprove by remember { mutableStateOf(false) }
    var showReject by remember { mutableStateOf(false) }
    var showRevoke by remember { mutableStateOf(false) }
    var showCancelHold by remember { mutableStateOf(false) }
    var showBooking by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var qrData by remember { mutableStateOf<JSONValue?>(null) }
    var viewer by remember { mutableStateOf<Pair<String, String>?>(null) }
    val isAdmin = AppSession.shared.role == "admin"

    suspend fun refresh() {
        try {
            // No single-registration endpoint: pick the dossier from the list (as iOS does).
            val found = APIClient.get().request("/sales/registrations")["data"].array.firstOrNull { it.id == registrationId }
            if (found != null) reg = found
            loadError = if (found == null && reg.id.isEmpty()) tr("Không tìm thấy hồ sơ đăng ký") else null
        } catch (e: Exception) {
            if (reg.id.isEmpty()) loadError = e.message ?: tr("Không thể tải hồ sơ đăng ký")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(registrationId) { refresh() }

    fun action(key: String, success: String, block: suspend () -> Unit) {
        scope.launch {
            busy = key
            try {
                block()
                ToastCenter.show(success)
                refresh()
                onChanged()
            } catch (e: Exception) {
                ToastCenter.show(tr("Lỗi: {0}", e.message), isError = true)
            } finally {
                busy = null
            }
        }
    }

    val apt = reg.nestedProperty
    // "Thời hạn quyền bán sau duyệt": the campaign value wins over the project one.
    val rightsDays = apt["salesCampaign"]["salesDurationDays"].int.takeIf { it > 0 }
        ?: apt["project"]["salesDurationDays"].int.takeIf { it > 0 } ?: 15

    fun updateRights(status: String, reason: String) = action("rights", "Cập nhật quyền bán thành công") {
        val body = buildJsonObject {
            put("status", status)
            if (reason.isNotEmpty()) put("reason", reason)
        }.toString()
        APIClient.get().request("/sales/registrations/${Uri.encode(registrationId)}/status", "PATCH", body)
    }

    fun loadQr() {
        showQr = true
        qrData = null
        scope.launch {
            busy = "qr"
            try {
                qrData = APIClient.get().request("/sales/registrations/${Uri.encode(registrationId)}/payment-qr")["data"]
            } catch (e: Exception) {
                showQr = false
                ToastCenter.show(tr("Không tải được mã QR: {0}", e.message), isError = true)
            } finally {
                busy = null
            }
        }
    }

    val code = regCode(reg).ifEmpty { tr("Căn hộ") }
    val bookingStatus = reg["bookingStatus"].string
    val rightsStatus = reg["status"].string
    val erp = isErpRegistration(reg)

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = regCode(reg).ifEmpty { tr("Hồ sơ đăng ký") }, subtitle = reg["code"].string.takeIf { it.isNotEmpty() }, onBack = onBack) }
    ) { padding ->
        when {
            loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FutaSkeletonBlock(height = 180.dp, radius = 16.dp)
                FutaSkeletonBlock(height = 220.dp, radius = 16.dp)
                FutaSkeletonLines(6)
            }
            loadError != null -> AdminErrorState(loadError!!, { loading = true; scope.launch { refresh() } }, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Hồ sơ giữ chỗ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(code, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                            if (erp) ErpLockBadge()
                        }
                        HorizontalDivider(color = FutaColors.PanelDivider)
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Trạng thái giữ chỗ", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                StatusPill(tr(BookingStepHelper.title(bookingStatus)), BookingStepHelper.color(bookingStatus))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Quyền bán", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                StatusPill(tr(RegistrationStatusHelper.title(rightsStatus)), RegistrationStatusHelper.color(rightsStatus))
                            }
                        }
                        if (reg["onlineHoldExpiresAt"].string.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(15.dp))
                                Text(tr("Hạn giữ chỗ online: {0}", SalesFormatters.dateTime(reg["onlineHoldExpiresAt"].string)), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandOrange)
                            }
                        }
                    }
                }

                // Actions (admin only — the backend restricts these routes to admins)
                if (isAdmin) {
                    DetailSection("Xử lý hồ sơ", Icons.Default.Verified) {
                        FutaButton(text = "Cập nhật giữ chỗ / cọc", icon = Icons.Default.Sync, enabled = busy == null, onClick = { showBooking = true }, modifier = Modifier.fillMaxWidth())
                        when (rightsStatus) {
                            "pending" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FutaButton(text = "Duyệt quyền bán", icon = Icons.Default.CheckCircle, variant = FutaButtonVariant.MINT, enabled = busy == null, onClick = { showApprove = true }, modifier = Modifier.weight(1f))
                                FutaButton(text = "Từ chối", icon = Icons.Default.Block, variant = FutaButtonVariant.OUTLINE, enabled = busy == null, onClick = { showReject = true }, modifier = Modifier.weight(1f))
                            }
                            "active" -> FutaButton(text = "Thu hồi quyền bán", icon = Icons.Default.Undo, variant = FutaButtonVariant.OUTLINE, enabled = busy == null, onClick = { showRevoke = true }, modifier = Modifier.fillMaxWidth())
                        }
                        HorizontalDivider(color = FutaColors.PanelDivider)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FutaButton(text = "QR thanh toán cọc", icon = Icons.Default.QrCode2, variant = FutaButtonVariant.MINT, enabled = busy == null, onClick = { loadQr() }, modifier = Modifier.weight(1f))
                            FutaButton(
                                text = if (busy == "refresh") "Đang làm mới…" else "Làm mới ERP", icon = Icons.Default.Refresh, variant = FutaButtonVariant.OUTLINE, enabled = busy == null,
                                onClick = {
                                    action("refresh", "Đã làm mới trạng thái giữ chỗ từ ERP") {
                                        APIClient.get().request("/sales/registrations/${Uri.encode(registrationId)}/hold/refresh", "POST", "{}")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Destructive action set apart at the bottom of the card.
                        FutaButton(text = "Hủy giữ chỗ", icon = Icons.Default.Cancel, variant = FutaButtonVariant.DANGER, enabled = busy == null, onClick = { showCancelHold = true }, modifier = Modifier.fillMaxWidth())
                    }
                }

                // Property & price
                DetailSection("Thông tin căn hộ & Giá bán", Icons.Default.Apartment) {
                    Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))) {
                        AdminRemoteImage(PropertyFormatters.resolveImage(if (apt.isNull) reg else apt), Modifier.fillMaxSize())
                    }
                    DossierRow("Mã sản phẩm", regCode(reg))
                    DossierRow("Dự án / Phân khu", regProject(reg).translated("project"))
                    DossierRow("Tòa / Tầng", tr("Tòa {0} · Tầng {1}", apt["block"].string.ifEmpty { "-" }, apt["floor"].string.ifEmpty { "-" }))
                    DossierRow("Loại căn", apt["unitType"].string)
                    val size = apt["size_m2"].double
                    DossierRow("Diện tích", if (size > 0) SalesFormatters.area(size) else apt["size_m2"].string)
                    val price = apt["sellPrice"].double.takeIf { it > 0 } ?: apt["price"].double
                    DossierRow("Giá bán ERP", SalesFormatters.currency(price), FutaColors.BrandGreen)
                    DossierRow("Số tiền cọc dự kiến", SalesFormatters.currency(reg["depositAmount"].double), FutaColors.BrandOrange)
                }

                // Customer
                DetailSection("Hồ sơ khách hàng", Icons.Default.Person) {
                    DossierRow("Họ và tên", reg["customerName"].string, verbatim = true)
                    DossierRow("Số điện thoại", reg["customerPhone"].string, verbatim = true)
                    DossierRow("Email", reg["customerEmail"].string, verbatim = true)
                    DossierRow("Số CCCD / Hộ chiếu", reg["customerCccd"].string, verbatim = true)
                    DossierRow("Giới tính", reg["customerGender"].string.takeIf { it.isNotEmpty() }?.let { LocalizedGender.name(it) }.orEmpty(), verbatim = true)
                    DossierRow("Ngày sinh", if (reg["customerBirthDate"].string.isEmpty()) "" else SalesFormatters.dateOnly(reg["customerBirthDate"].string))
                    DossierRow("Địa chỉ thường trú", reg["customerPermanentAddress"].string, verbatim = true)
                    DossierRow("Địa chỉ liên hệ", reg["customerContactAddress"].string, verbatim = true)
                }
                val idDocs = listOf(
                    "Mặt trước" to reg["customerIdFrontUrl"].string,
                    "Mặt sau" to reg["customerIdBackUrl"].string,
                    "Định danh số" to reg["customerDigitalIdUrl"].string
                ).filter { it.second.isNotEmpty() }
                if (idDocs.isNotEmpty()) {
                    DetailSection("Ảnh giấy tờ định danh", Icons.Default.Badge) {
                        idDocs.forEach { (title, url) ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, FutaColors.LightBlueBorder, RoundedCornerShape(12.dp))
                                        .clickable { viewer = tr(title) to url }
                                ) {
                                    AdminRemoteImage(url, Modifier.fillMaxSize(), ContentScale.Fit)
                                }
                                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            }
                        }
                    }
                }

                // Advisor
                val advisor = reg["advisor"]
                DetailSection("Tư vấn viên phụ trách", Icons.Default.SupportAgent) {
                    DossierRow("Họ và tên", advisor["name"].string.ifEmpty { tr("Tư vấn viên FUTA Land") }, verbatim = true)
                    DossierRow("Số điện thoại", advisor["phone"].string, verbatim = true)
                    if (advisor["phone"].string.isNotEmpty()) {
                        FutaButton(text = "Gọi tư vấn viên", icon = Icons.Default.Phone, variant = FutaButtonVariant.MINT, onClick = { dialPhone(context, advisor["phone"].string) }, modifier = Modifier.fillMaxWidth())
                    }
                }

                // ERP hold
                DetailSection("Đồng bộ ERP FUTA Land", Icons.Default.CloudSync, trailing = { ErpLockBadge() }) {
                    DossierRow("Mã Booking ERP", reg["erpBookingCode"].string)
                    DossierRow("Mã Hold ERP", reg["erpHoldId"].string)
                    DossierRow("Trạng thái Hold ERP", reg["erpHoldStatusLabel"].string.ifEmpty { reg["erpHoldStatus"].string })
                    if (reg["erpHoldLastError"].string.isNotEmpty()) DossierRow("Lỗi đồng bộ gần nhất", reg["erpHoldLastError"].string, FutaColors.RedPdf)
                }

                // Payment documents
                DetailSection("Chứng từ & Thanh toán đặt cọc", Icons.Default.Payments) {
                    DossierRow("Hình thức thanh toán", paymentMethodLabel(reg["paymentMethod"].string))
                    DossierRow("Số tiền cọc", SalesFormatters.currency(reg["depositAmount"].double), FutaColors.BrandOrange)
                    if (reg["commissionAmount"].double > 0) DossierRow("Hoa hồng môi giới", SalesFormatters.currency(reg["commissionAmount"].double), FutaColors.BrandOrange)
                    val slip = reg["depositSlipUrl"].string
                    val receipt = reg["depositReceiptUrl"].string
                    if (slip.isNotEmpty()) DocumentLink("Ủy nhiệm chi / UNC", "Xem chứng từ", slip) { viewDocument(context, tr("Ủy nhiệm chi / UNC"), slip) { viewer = it } }
                    if (receipt.isNotEmpty()) DocumentLink("Phiếu thu / Hóa đơn cọc", "Xem phiếu thu", receipt) { viewDocument(context, tr("Phiếu thu / Hóa đơn cọc"), receipt) { viewer = it } }
                    if (slip.isEmpty() && receipt.isEmpty()) Text("Chưa có chứng từ thanh toán.", fontSize = 12.5.sp, color = FutaColors.Slate)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    ConfirmDialog(
        visible = showApprove,
        title = "Duyệt quyền bán cho TVV?",
        message = tr("Cấp quyền bán căn hộ cho TVV trong {0} ngày theo chương trình bán hàng đang áp dụng.", rightsDays),
        confirmText = "Xác nhận duyệt quyền bán",
        onDismiss = { showApprove = false },
        // validDays is not sent: the campaign's "rights duration after approval" is the only source.
        onConfirm = { updateRights("active", tr("Quản trị viên phê duyệt quyền bán ({0} ngày)", rightsDays)) }
    )
    ReasonDialog(
        visible = showReject,
        title = "Từ chối quyền bán TVV",
        message = "Vui lòng nhập lý do từ chối cấp quyền bán cho tư vấn viên.",
        placeholder = "Nhập lý do từ chối…",
        confirmText = "Xác nhận từ chối",
        onDismiss = { showReject = false },
        onConfirm = { updateRights("rejected", it.ifEmpty { tr("Quản trị viên từ chối cấp quyền bán") }) }
    )
    ReasonDialog(
        visible = showRevoke,
        title = "Thu hồi quyền bán TVV",
        message = "Quyền bán căn hộ của tư vấn viên sẽ bị thu hồi ngay lập tức.",
        placeholder = "Nhập lý do thu hồi…",
        confirmText = "Thu hồi quyền bán",
        onDismiss = { showRevoke = false },
        onConfirm = { updateRights("revoked", it.ifEmpty { tr("Quản trị viên thu hồi quyền bán") }) }
    )
    ReasonDialog(
        visible = showCancelHold,
        title = "Hủy giữ chỗ ERP?",
        message = "Lệnh hủy giữ chỗ sẽ được gửi đồng bộ tới ERP.",
        placeholder = "Lý do hủy (không bắt buộc)…",
        confirmText = "Xác nhận hủy giữ chỗ",
        onDismiss = { showCancelHold = false },
        onConfirm = { reason ->
            action("cancel", "Đã hủy giữ chỗ ERP") {
                val body = buildJsonObject { put("reason", reason.ifEmpty { tr("Hủy theo yêu cầu quản trị viên") }) }.toString()
                APIClient.get().request("/sales/registrations/${Uri.encode(registrationId)}/hold/cancel", "POST", body)
            }
        }
    )
    if (showBooking) {
        BookingUpdateSheet(
            reg = reg,
            erp = erp,
            onDismiss = { showBooking = false },
            onSubmit = { body ->
                showBooking = false
                action("booking", "Cập nhật trạng thái thành công") {
                    APIClient.get().request("/sales/registrations/${Uri.encode(registrationId)}/booking-status", "PATCH", body)
                }
            }
        )
    }
    if (showQr) {
        FutaBottomSheet(visible = true, onDismiss = { showQr = false }, title = "Mã QR đặt cọc") {
            val data = qrData
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    data == null -> {
                        CircularProgressIndicator(color = FutaColors.BrandGreen)
                        Text("Đang tải thông tin chuyển khoản…", fontSize = 13.sp, color = FutaColors.Slate)
                    }
                    data["qrCodeUrl"].string.isEmpty() -> Text("Không thể tạo mã QR thanh toán vào lúc này.", fontSize = 13.sp, color = FutaColors.Slate)
                    else -> {
                        AsyncImage(model = data["qrCodeUrl"].string, contentDescription = tr("Mã QR đặt cọc"), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(260.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FAFC)).padding(12.dp)) {
                            InfoRow("Ngân hàng:", data["bankName"].string, verbatim = true)
                            InfoRow("Số tài khoản:", data["accountNumber"].string, verbatim = true)
                            InfoRow("Chủ tài khoản:", data["accountHolder"].string, verbatim = true)
                            InfoRow("Số tiền:", SalesFormatters.currency(data["amount"].double), FutaColors.BrandGreen)
                            InfoRow("Cú pháp chuyển khoản:", data["syntax"].string, FutaColors.BrandOrange, verbatim = true)
                        }
                    }
                }
                FutaButton(text = "Đóng", variant = FutaButtonVariant.OUTLINE, onClick = { showQr = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    viewer?.let { (title, url) -> ZoomableImageDialog(url = url, title = title, onDismiss = { viewer = null }) }
}

private fun paymentMethodLabel(raw: String): String = when (raw) {
    "standard" -> tr("Thanh toán tiêu chuẩn")
    "fast" -> tr("Thanh toán nhanh")
    "loan" -> tr("Vay ngân hàng")
    else -> raw
}

/** Images open in the zoomable viewer; PDFs and other files open externally. */
private fun viewDocument(context: android.content.Context, title: String, url: String, showImage: (Pair<String, String>) -> Unit) {
    val lower = url.lowercase().substringBefore('?')
    if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")) openExternalUrl(context, url) else showImage(title to url)
}

@Composable
private fun DossierRow(title: String, value: String, color: Color = FutaColors.Body, verbatim: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
        val v = value.ifBlank { "-" }
        if (verbatim) VerbatimText(v, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color) else Text(v, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun DocumentLink(title: String, action: String, url: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.MintBg).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.ReceiptLong, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = FutaColors.Slate)
            Text(action, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
        }
        Icon(Icons.Default.ChevronRight, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
    }
}

private val bookingStatusOptions = listOf(
    SelectOption("online_holding", "Giữ chỗ online"),
    SelectOption("pending_booking", "Chờ duyệt cọc"),
    SelectOption("holding_success", "Giữ chỗ thành công"),
    SelectOption("deposited", "Đã nộp cọc"),
    SelectOption("commission_pending", "Chờ chi hoa hồng"),
    SelectOption("commission_paid", "Đã chi hoa hồng"),
    SelectOption("purchased", "Đã ký HĐMB"),
    SelectOption("rejected", "Từ chối"),
    SelectOption("cancelled", "Hủy giữ chỗ")
)

/** Booking / deposit update (iOS `updateBookingSheet`) → PATCH …/booking-status. */
@Composable
private fun BookingUpdateSheet(reg: JSONValue, erp: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val current = reg["bookingStatus"].string.ifEmpty { "pending_booking" }
    var status by remember { mutableStateOf(current) }
    var erpCode by remember { mutableStateOf(reg["erpBookingCode"].string) }
    var commission by remember { mutableStateOf(reg["commissionAmount"].double.takeIf { it > 0 }?.toLong()?.toString().orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Cập nhật giữ chỗ",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = onDismiss, modifier = Modifier.weight(1f))
                FutaButton(
                    text = "Lưu cập nhật", icon = Icons.Default.Check, modifier = Modifier.weight(1.5f),
                    onClick = {
                        val body = buildJsonObject {
                            // The backend requires bookingStatus; ERP-managed dossiers keep their synced status.
                            put("bookingStatus", if (erp) current else status)
                            if (erpCode.isNotBlank()) put("erpBookingCode", erpCode.trim())
                            commission.toLongOrNull()?.takeIf { it > 0 }?.let { put("commissionAmount", it) }
                            if (reason.isNotBlank()) put("reason", reason.trim())
                            if (notes.isNotBlank()) put("notes", notes.trim())
                        }.toString()
                        onSubmit(body)
                    }
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (erp) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.CreamBg).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ErpLockBadge()
                        Text("Phiếu giữ chỗ liên kết ERP FUTA Land", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    }
                    Text(
                        "Trạng thái giữ chỗ được quản lý và đồng bộ trực tiếp từ hệ thống ERP. Hãy dùng chức năng 'Làm mới ERP' để cập nhật hoặc 'Hủy giữ chỗ' để gửi lệnh hủy.",
                        fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 16.sp
                    )
                }
                InfoRow("Trạng thái giữ chỗ ERP:", tr(BookingStepHelper.title(current)), BookingStepHelper.color(current))
            } else {
                AdminSelectField("Trạng thái", status, bookingStatusOptions, { status = it })
            }
            FormTextField("Mã Booking ERP", erpCode, { erpCode = it }, "Nhập mã Booking ERP (nếu có)…")
            FormTextField("Hoa hồng (VND)", commission, { commission = it.filter { c -> c.isDigit() } }, "0", keyboardType = KeyboardType.Number)
            commission.toLongOrNull()?.takeIf { it > 0 }?.let { Text(SalesFormatters.currency(it.toDouble()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandOrange) }
            FormTextField("Lý do thay đổi", reason, { reason = it }, "Nhập lý do thay đổi…")
            FormTextField("Ghi chú nội bộ", notes, { notes = it }, "Ghi chú nội bộ…", multiline = true)
        }
    }
}

// ============================================================================
// Card
// ============================================================================

@Composable
private fun RegistrationCardRow(
    registration: JSONValue,
    isCompeting: Boolean,
    onClick: () -> Unit
) {
    val property = registration.nestedProperty
    val advisor = registration["advisor"]
    val bookingStatus = registration["bookingStatus"].string
    val rightsStatus = registration["status"].string
    val propertyCode = property["propertyCode"].string.ifEmpty { tr("Căn hộ") }
    val projName = regProject(registration).ifEmpty { tr("Dự án FUTA") }.translated("project")
    val subLocation = tr("{0} · Tòa {1} · Tầng {2}", projName, property["block"].string.ifEmpty { "-" }, property["floor"].string.ifEmpty { "-" })
    val customerName = registration["customerName"].string
    val customerPhone = registration["customerPhone"].string
    val priceVal = property["sellPrice"].double.takeIf { it > 0 } ?: property["price"].double
    val deposit = registration["depositAmount"].double

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(if (isCompeting) 1.5.dp else 1.dp, if (isCompeting) Color(0xFFFCA5A5) else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(propertyCode, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (isCompeting) {
                            Row(
                                Modifier.clip(CircleShape).background(Color(0xFFFEE2E2)).padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFDC2626), modifier = Modifier.size(11.dp))
                                Text("Tranh chấp", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                        }
                    }
                    Text(subLocation, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (bookingStatus.isNotEmpty() && bookingStatus != "none") StatusPill(tr(BookingStepHelper.title(bookingStatus)), BookingStepHelper.color(bookingStatus))
                    StatusPill(tr(RegistrationStatusHelper.title(rightsStatus)), RegistrationStatusHelper.color(rightsStatus))
                }
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = PropertyFormatters.resolveImage(if (!property.isNull && property["propertyCode"].string.isNotEmpty()) property else registration),
                    contentDescription = propertyCode,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9))
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.SupportAgent, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp))
                        VerbatimText(tr("TVV: {0}", advisor["name"].string.ifEmpty { "FUTA Land" }), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (advisor["phone"].string.isNotEmpty()) VerbatimText(advisor["phone"].string, fontSize = 11.5.sp, color = FutaColors.Slate)
                    if (customerName.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Person, null, tint = FutaColors.Slate, modifier = Modifier.size(14.dp))
                            VerbatimText(customerName + if (customerPhone.isNotEmpty()) " · $customerPhone" else "", fontSize = 12.sp, color = Color(0xFF334155), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (priceVal > 0) SalesFormatters.currency(priceVal) else tr("Đang cập nhật"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                if (deposit > 0) Text(" · " + tr("Cọc: {0}", SalesFormatters.compactCurrency(deposit)), fontSize = 11.5.sp, color = FutaColors.BrandOrange)
                Spacer(Modifier.weight(1f))
                Text(SalesFormatters.dateTime(registration["createdAt"].string), fontSize = 11.sp, color = FutaColors.Slate)
                Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Navy, modifier = Modifier.size(14.dp))
            }
        }
    }
}
