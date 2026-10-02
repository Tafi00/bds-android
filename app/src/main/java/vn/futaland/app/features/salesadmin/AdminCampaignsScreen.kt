package vn.futaland.app.features.salesadmin

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.translated
import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Mirrors iOS Features/SalesAdmin/AdminCampaignsView.swift:
// list (/sales/campaigns) → detail → create/edit (POST/PUT /sales/campaigns), delete, activate/deactivate.

private sealed interface CampaignRoute {
    data class Detail(val id: String, val version: Int = 0) : CampaignRoute
    data object Create : CampaignRoute
    data class Edit(val campaign: JSONValue) : CampaignRoute
}

/** iOS `CampaignSortOption`. */
private enum class CampaignSort(val title: String) {
    NEWEST("Mới nhất"),
    NAME_ASC("Tên: A → Z"),
    NAME_DESC("Tên: Z → A"),
    COMMISSION_DESC("Hoa hồng: Cao → Thấp"),
    COMMISSION_ASC("Hoa hồng: Thấp → Cao"),
    DURATION_DESC("Thời hạn bán: Dài nhất")
}

/** iOS `CampaignFilterState`. */
private data class CampaignFilters(val status: String = "all", val projectName: String = "all", val productType: String = "all") {
    val activeCount: Int get() = listOf(status, projectName, productType).count { it != "all" }
}

/** iOS `formatProductType`. */
internal fun formatProductType(raw: String): String {
    val trimmed = raw.trim()
    return when (trimmed.lowercase()) {
        "can-ho-chung-cu", "can-ho", "apartment" -> "Căn hộ"
        "nha-pho", "townhouse" -> "Nhà phố"
        "nha-lien-ke" -> "Nhà liên kế"
        "biet-thu", "villa" -> "Biệt thự"
        "biet-thu-lien-ke" -> "Biệt thự liền kề"
        "shophouse" -> "Shophouse"
        "dat-nen", "land" -> "Đất nền"
        "studio", "std" -> "Studio"
        "duplex" -> "Duplex"
        "penthouse" -> "Penthouse"
        "1pn", "1 pn", "1 bed" -> "1 Phòng ngủ"
        "2pn", "2 pn", "2 bed" -> "2 Phòng ngủ"
        "3pn", "3 pn", "3 bed" -> "3 Phòng ngủ"
        "4pn", "4 pn", "4 bed" -> "4 Phòng ngủ"
        else -> trimmed.ifEmpty { "Căn hộ" }
    }
}

private fun campaignStatusLabel(status: String): String = when (status) {
    "active" -> "Đang diễn ra"
    "scheduled" -> "Sắp diễn ra"
    "ended" -> "Đã kết thúc"
    else -> status.ifEmpty { "Khác" }
}

private fun campaignStatusColor(status: String): Color = when (status) {
    "active" -> FutaColors.BrandGreen
    "scheduled" -> FutaColors.BrandOrange
    "ended" -> Color(0xFF6B7280)
    else -> Color(0xFF2563EB)
}

private fun campaignProject(c: JSONValue): String = c["projectName"].string.ifEmpty { c["project"]["displayName"].string }

private fun matchesCampaign(c: JSONValue, f: CampaignFilters, search: String): Boolean {
    val q = search.trim()
    if (q.isNotEmpty()) {
        val text = listOf("name", "code", "projectName", "productType", "description").joinToString(" ") { c[it].string }
        if (!text.contains(q, ignoreCase = true)) return false
    }
    if (f.status != "all" && c["status"].string != f.status) return false
    if (f.projectName != "all" && campaignProject(c) != f.projectName) return false
    if (f.productType != "all" && c["productType"].string != f.productType) return false
    return true
}

private fun sortCampaigns(list: List<JSONValue>, sort: CampaignSort): List<JSONValue> = when (sort) {
    CampaignSort.NEWEST -> list.sortedWith(
        compareByDescending<JSONValue> { it["createdAt"].string.ifEmpty { it["startDate"].string } }.thenByDescending { it["code"].string }
    )
    CampaignSort.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["name"].string })
    CampaignSort.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it["name"].string })
    CampaignSort.COMMISSION_DESC -> list.sortedByDescending { it["commissionRate"].double }
    CampaignSort.COMMISSION_ASC -> list.sortedBy { it["commissionRate"].double }
    CampaignSort.DURATION_DESC -> list.sortedByDescending { it["salesDurationDays"].int }
}

/** Policy entry normalized to the backend schema (all keys present as strings). */
private fun normalizePolicy(p: JSONValue, index: Int): JsonObject = buildJsonObject {
    // Keep any extra keys the web editor stored on the policy.
    (p.element as? JsonObject)?.forEach { (k, v) -> put(k, v) }
    put("id", p["id"].string.ifEmpty { "pol-${System.currentTimeMillis()}-$index" })
    put("title", p["title"].string)
    put("status", p["status"].string.ifEmpty { "active" })
    put("tag", p["tag"].string)
    put("validUntil", p["validUntil"].string)
    put("content", p["content"].string)
}

/** Full PUT/POST body from campaign field values (the backend validates the whole object). */
private fun campaignBody(
    code: String, name: String, projectName: String, projectId: String, productType: String, transactionType: String,
    startDate: String, endDate: String, discountPercent: Double, commissionRate: Double, commissionNote: String,
    salesDurationDays: Int, status: String, description: String, policyDocumentName: String, policies: List<JSONValue>,
    appliedUnitsCount: Int? = null
): String = buildJsonObject {
    put("code", code)
    put("name", name)
    put("projectName", projectName)
    if (projectId.isNotEmpty()) put("projectId", projectId)
    put("productType", productType)
    put("transactionType", transactionType)
    put("startDate", startDate)
    put("endDate", endDate)
    put("discountPercent", discountPercent)
    put("commissionRate", commissionRate)
    put("commissionNote", commissionNote)
    put("salesDurationDays", salesDurationDays.coerceIn(1, 365))
    put("status", status)
    put("description", description)
    if (policyDocumentName.isNotEmpty()) put("policyDocumentName", policyDocumentName)
    if (appliedUnitsCount != null) put("appliedUnitsCount", appliedUnitsCount)
    put("policies", JsonArray(policies.mapIndexed { i, p -> normalizePolicy(p, i) }))
}.toString()

private fun bodyFromCampaign(c: JSONValue, status: String): String = campaignBody(
    code = c["code"].string,
    name = c["name"].string,
    projectName = campaignProject(c),
    projectId = c["projectId"].string,
    productType = c["productType"].string,
    transactionType = c["transactionType"].string,
    startDate = c["startDate"].string.take(10),
    endDate = c["endDate"].string.take(10),
    discountPercent = c["discountPercent"].double,
    commissionRate = c["commissionRate"].double,
    commissionNote = c["commissionNote"].string,
    salesDurationDays = c["salesDurationDays"].int.takeIf { it > 0 } ?: 15,
    status = status,
    description = c["description"].string,
    policyDocumentName = c["policyDocumentName"].string,
    policies = c["policies"].array,
    appliedUnitsCount = c["appliedUnitsCount"].int.takeIf { !c["appliedUnitsCount"].isNull }
)

@Composable
fun AdminCampaignsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<CampaignRoute>() }
    var campaigns by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(CampaignFilters()) }
    var sort by remember { mutableStateOf(CampaignSort.NEWEST) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val listState = rememberLazyListState()

    suspend fun fetch() {
        try {
            val q = search.trim()
            campaigns = APIClient.get().request("/sales/campaigns", query = if (q.isEmpty()) emptyMap() else mapOf("search" to q))["data"].array
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải chiến dịch")
        } finally {
            loading = false
        }
    }

    fun load() { scope.launch { fetch() } }

    // Server-side search (debounced), like iOS `.task(id: search)`.
    LaunchedEffect(search) {
        if (campaigns.isNotEmpty()) delay(300)
        fetch()
    }

    ScreenStackHost(
        stack = stack,
        base = {
            CampaignListContent(
                campaigns, loading, loadError, search, { search = it; page = 1 }, filters, { filters = it; page = 1 },
                sort, { sort = it; page = 1 }, page, { page = it; scope.launch { listState.scrollToItem(0) } }, listState,
                onBack = onBack,
                onRetry = { loading = true; load() },
                onOpen = { stack.push(CampaignRoute.Detail(it)) },
                onCreate = { stack.push(CampaignRoute.Create) }
            )
        }
    ) { route ->
        when (route) {
            is CampaignRoute.Detail -> AdminCampaignDetailScreen(
                campaignId = route.id,
                onBack = { stack.pop() },
                onEdit = { stack.push(CampaignRoute.Edit(it)) },
                onChanged = { load() },
                onDeleted = { stack.pop(); load() }
            )
            CampaignRoute.Create -> AdminCampaignFormScreen(null, onClose = { stack.pop() }, onSaved = { stack.pop(); load() })
            is CampaignRoute.Edit -> AdminCampaignFormScreen(route.campaign, onClose = { stack.pop() }, onSaved = {
                stack.pop()
                val detail = stack.entries.lastOrNull()
                if (detail is CampaignRoute.Detail) stack.replaceTop(detail.copy(version = detail.version + 1))
                load()
            })
        }
    }
}

// ============================================================================
// Listing
// ============================================================================

@Composable
private fun CampaignListContent(
    campaigns: List<JSONValue>,
    loading: Boolean,
    loadError: String?,
    search: String,
    onSearch: (String) -> Unit,
    filters: CampaignFilters,
    onFilters: (CampaignFilters) -> Unit,
    sort: CampaignSort,
    onSort: (CampaignSort) -> Unit,
    page: Int,
    onPage: (Int) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit
) {
    var showFilter by remember { mutableStateOf(false) }
    val filtered = remember(campaigns, filters, search, sort) { sortCampaigns(campaigns.filter { matchesCampaign(it, filters, search) }, sort) }
    val slice = filtered.pageSlice(page, 20)
    val projectOptions = remember(campaigns) { campaigns.map { campaignProject(it) }.filter { it.isNotEmpty() }.distinct().sorted() }
    val typeOptions = remember(campaigns) { campaigns.map { it["productType"].string }.filter { it.isNotEmpty() }.distinct().sorted() }
    val active = campaigns.count { it["status"].string == "active" }
    val scheduled = campaigns.count { it["status"].string == "scheduled" }
    val ended = campaigns.count { it["status"].string == "ended" }
    val isAdmin = vn.futaland.app.core.auth.AppSession.shared.role == "admin"

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Chiến dịch bán hàng", onBack = onBack) {
                SortMenuButton(CampaignSort.entries, sort, { it.title }, onSort, isDefault = sort == CampaignSort.NEWEST)
                BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), filters.activeCount) { showFilter = true }
                if (isAdmin) AddHeaderButton(tr("Thêm chiến dịch"), onCreate)
            }
        }
    ) { padding ->
        when {
            loading && campaigns.isEmpty() -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FutaSkeletonBlock(height = 48.dp, radius = 12.dp)
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp)
                        FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp)
                    }
                }
                repeat(3) { FutaSkeletonBlock(height = 150.dp, radius = 16.dp) }
            }
            loadError != null && campaigns.isEmpty() && search.isEmpty() -> AdminErrorState(loadError, onRetry, Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { AdminSearchField(search, onSearch, "Tìm theo tên, mã chiến dịch, dự án…") }
                item {
                    MetricGrid(
                        listOf(
                            { m -> SalesMetricCard("Tổng chiến dịch", "${campaigns.size}", Icons.Default.Campaign, FutaColors.BrandGreen, m) },
                            { m -> SalesMetricCard("Đang diễn ra", "$active", Icons.Default.Bolt, Color(0xFF2563EB), m) },
                            { m -> SalesMetricCard("Sắp diễn ra", "$scheduled", Icons.Default.Schedule, FutaColors.BrandOrange, m) },
                            { m -> SalesMetricCard("Đã kết thúc", "$ended", Icons.Default.EventBusy, Color(0xFF6B7280), m) }
                        )
                    )
                }
                item {
                    QuickChipRow {
                        QuickChip(tr("Tất cả ({0})", campaigns.size), filters.status == "all") { onFilters(filters.copy(status = "all")) }
                        QuickChip(tr("Đang diễn ra ({0})", active), filters.status == "active") { onFilters(filters.copy(status = "active")) }
                        QuickChip(tr("Sắp diễn ra ({0})", scheduled), filters.status == "scheduled") { onFilters(filters.copy(status = "scheduled")) }
                        QuickChip(tr("Đã kết thúc ({0})", ended), filters.status == "ended") { onFilters(filters.copy(status = "ended")) }
                    }
                }
                val applied = buildList {
                    if (filters.status != "all") add(AppliedFilter(tr(campaignStatusLabel(filters.status))) { onFilters(filters.copy(status = "all")) })
                    if (filters.projectName != "all") add(AppliedFilter(filters.projectName.translated("project")) { onFilters(filters.copy(projectName = "all")) })
                    if (filters.productType != "all") add(AppliedFilter(tr(formatProductType(filters.productType))) { onFilters(filters.copy(productType = "all")) })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Hiển thị {0}/{1} chiến dịch", slice.items.size, filtered.size), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        AppliedFilterChips(applied) { onFilters(CampaignFilters()) }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        if (campaigns.isEmpty() && search.isEmpty()) {
                            FutaEmptyState(title = "Chưa có chiến dịch", message = "Chưa có chiến dịch bán hàng nào được tạo.", icon = Icons.Default.Campaign)
                        } else {
                            FutaEmptyState(
                                title = "Không có chiến dịch nào",
                                message = "Không có chiến dịch bán hàng nào phù hợp với điều kiện tìm kiếm hoặc bộ lọc.",
                                icon = Icons.Default.SearchOff,
                                actionButton = { FutaButton(text = "Đặt lại bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = { onFilters(CampaignFilters()); onSearch("") }) }
                            )
                        }
                    }
                } else {
                    items(slice.items, key = { it.id.ifEmpty { it["code"].string } }) { campaign ->
                        CampaignCard(campaign) { onOpen(campaign.id) }
                    }
                    item {
                        PaginationBar(
                            slice.page, slice.totalPages, tr("Hiển thị {0}–{1} / {2} chiến dịch", slice.start, slice.end, slice.total),
                            onPrevious = { onPage(slice.page - 1) }, onNext = { onPage(slice.page + 1) }
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showFilter) {
        var draft by remember { mutableStateOf(filters) }
        FilterSheet(
            visible = true,
            title = "Bộ lọc chiến dịch",
            applyTitle = if (draft.activeCount > 0) tr("Áp dụng ({0})", draft.activeCount) else tr("Áp dụng"),
            canReset = draft.activeCount > 0,
            onReset = { draft = CampaignFilters() },
            onApply = { onFilters(draft); showFilter = false },
            onDismiss = { showFilter = false }
        ) {
            AdminSelectField(
                "Trạng thái", draft.status,
                listOf(SelectOption("all", "Tất cả trạng thái"), SelectOption("active", "Đang diễn ra"), SelectOption("scheduled", "Sắp diễn ra"), SelectOption("ended", "Đã kết thúc")),
                { draft = draft.copy(status = it) }
            )
            AdminSelectField(
                "Dự án áp dụng", draft.projectName,
                listOf(SelectOption("all", "Tất cả dự án")) + projectOptions.map { SelectOption(it, it.translated("project")) },
                { draft = draft.copy(projectName = it) }
            )
            AdminSelectField(
                "Loại sản phẩm", draft.productType,
                listOf(SelectOption("all", "Tất cả loại sản phẩm")) + typeOptions.map { SelectOption(it, formatProductType(it)) },
                { draft = draft.copy(productType = it) }
            )
        }
    }
}

@Composable
private fun CampaignCard(campaign: JSONValue, onClick: () -> Unit) {
    val status = campaign["status"].string
    val project = campaignProject(campaign)
    FutaCard(Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder, onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(campaign["code"].string, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    Text(campaign["name"].string.translated("policy"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(tr(campaignStatusLabel(status)), campaignStatusColor(status), solid = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Apartment, null, tint = FutaColors.Slate, modifier = Modifier.size(14.dp))
                Text(project.ifEmpty { tr("Dự án FUTA") }.translated("project"), fontSize = 12.5.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (campaign["productType"].string.isNotEmpty()) Text("· " + tr(formatProductType(campaign["productType"].string)), fontSize = 12.5.sp, color = FutaColors.Slate, maxLines = 1)
            }
            HorizontalDivider(color = FutaColors.PanelDivider)
            QuickChipRow {
                if (campaign["commissionRate"].double > 0) MiniTag(Icons.Default.Paid, tr("HH {0}", SalesFormatters.percent(campaign["commissionRate"].double)), FutaColors.BrandGreen)
                if (campaign["discountPercent"].double > 0) MiniTag(Icons.Default.Sell, tr("CK {0}", SalesFormatters.percent(campaign["discountPercent"].double)), FutaColors.BrandOrange)
                if (campaign["salesDurationDays"].int > 0) MiniTag(Icons.Default.Timer, tr("{0} ngày", campaign["salesDurationDays"].int), Color(0xFF0284C7))
                if (campaign["appliedUnitsCount"].int > 0) MiniTag(Icons.Default.House, tr("{0} căn", campaign["appliedUnitsCount"].int), FutaColors.SubLabel)
            }
            if (campaign["startDate"].string.isNotEmpty() || campaign["endDate"].string.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = FutaColors.Slate, modifier = Modifier.size(13.dp))
                    Text("${SalesFormatters.dateOnly(campaign["startDate"].string)} → ${SalesFormatters.dateOnly(campaign["endDate"].string)}", fontSize = 11.5.sp, color = FutaColors.Slate)
                }
            }
        }
    }
}

@Composable
private fun MiniTag(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ============================================================================
// Detail
// ============================================================================

@Composable
private fun AdminCampaignDetailScreen(
    campaignId: String,
    onBack: () -> Unit,
    onEdit: (JSONValue) -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var campaign by remember { mutableStateOf<JSONValue?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pendingStatus by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val isAdmin = vn.futaland.app.core.auth.AppSession.shared.role == "admin"

    fun load() {
        scope.launch {
            try {
                // No single-campaign endpoint: the list is small, pick the record from it (as iOS does).
                val found = APIClient.get().request("/sales/campaigns")["data"].array.firstOrNull { it.id == campaignId }
                campaign = found
                loadError = if (found == null) tr("Không tìm thấy chiến dịch") else null
            } catch (e: Exception) {
                loadError = e.message ?: tr("Không thể tải chiến dịch")
            } finally {
                loading = false
            }
        }
    }

    fun updateStatus(status: String) {
        val current = campaign ?: return
        scope.launch {
            busy = true
            try {
                APIClient.get().request("/sales/campaigns/${Uri.encode(campaignId)}", "PUT", bodyFromCampaign(current, status))
                ToastCenter.show("Đã cập nhật trạng thái chiến dịch")
                load()
                onChanged()
            } catch (e: Exception) {
                ToastCenter.show(tr("Lỗi cập nhật: {0}", e.message), isError = true)
            } finally {
                busy = false
            }
        }
    }

    fun delete() {
        scope.launch {
            busy = true
            try {
                APIClient.get().request("/sales/campaigns/${Uri.encode(campaignId)}", "DELETE")
                ToastCenter.show("Đã xóa chiến dịch thành công")
                onDeleted()
            } catch (e: Exception) {
                ToastCenter.show(tr("Không thể xóa: {0}", e.message), isError = true)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(campaignId) { load() }

    val c = campaign ?: JSONValue.EmptyObject
    val status = c["status"].string
    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = c["name"].string.ifEmpty { tr("Chiến dịch") }.translated("policy"), subtitle = c["code"].string.takeIf { it.isNotEmpty() }, onBack = onBack) },
        bottomBar = {
            if (campaign != null && isAdmin) {
                FutaStickyActionBar {
                    FutaButton(text = "Chỉnh sửa", icon = Icons.Default.Edit, enabled = !busy, onClick = { onEdit(c) }, modifier = Modifier.weight(1f))
                    if (status == "active") {
                        FutaButton(text = "Ngưng hoạt động", icon = Icons.Default.PauseCircle, variant = FutaButtonVariant.CREAM, enabled = !busy, onClick = { pendingStatus = "ended" }, modifier = Modifier.weight(1.2f))
                    } else {
                        FutaButton(text = "Kích hoạt", icon = Icons.Default.PlayCircle, variant = FutaButtonVariant.MINT, enabled = !busy, onClick = { pendingStatus = "active" }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.2f)),
                        modifier = Modifier.size(44.dp).clickable(enabled = !busy) { showDelete = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, tr("Xóa chiến dịch"), tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    ) { padding ->
        when {
            loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FutaSkeletonBlock(height = 130.dp, radius = 16.dp)
                repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FutaSkeletonBlock(Modifier.weight(1f), height = 110.dp, radius = 14.dp); FutaSkeletonBlock(Modifier.weight(1f), height = 110.dp, radius = 14.dp) } }
                FutaSkeletonBlock(height = 120.dp, radius = 16.dp)
            }
            loadError != null || campaign == null -> AdminErrorState(loadError.orEmpty(), { loading = true; load() }, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c["code"].string.ifEmpty { "CTBH" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(FutaColors.MintBg).padding(horizontal = 8.dp, vertical = 3.dp))
                            Spacer(Modifier.weight(1f))
                            StatusPill(tr(if (status == "active") "Đang hoạt động" else if (status == "ended") "Ngưng hoạt động" else campaignStatusLabel(status)), campaignStatusColor(status), dot = true)
                        }
                        Text(c["name"].string.ifEmpty { tr("Chiến dịch bán hàng") }.translated("policy"), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = FutaColors.Navy)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Apartment, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                            Text(campaignProject(c).ifEmpty { tr("Dự án FUTA") }.translated("project"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                            if (c["productType"].string.isNotEmpty()) Text("· " + tr(formatProductType(c["productType"].string)), fontSize = 13.sp, color = FutaColors.Slate)
                        }
                        if (c["transactionType"].string.isNotEmpty()) {
                            Text(tr("Hình thức: {0}", c["transactionType"].string.translated("policy")), fontSize = 12.sp, color = FutaColors.Slate)
                        }
                    }
                }
                TwoColumnGrid(
                    listOf(
                        { m -> CampaignMetric("Tỷ lệ hoa hồng", SalesFormatters.percent(c["commissionRate"].double), "Hoa hồng môi giới", Icons.Default.Percent, FutaColors.BrandGreen, m) },
                        { m -> CampaignMetric("Mức chiết khấu", SalesFormatters.percent(c["discountPercent"].double), "Ưu đãi mở bán", Icons.Default.Sell, FutaColors.BrandOrange, m) },
                        { m -> CampaignMetric("Thời hạn quyền bán", tr("{0} ngày", c["salesDurationDays"].int), "Sau duyệt đăng ký", Icons.Default.EventAvailable, Color(0xFF0284C7), m) },
                        { m -> CampaignMetric("Sản phẩm áp dụng", tr("{0} căn", c["appliedUnitsCount"].int), "Trong giỏ hàng", Icons.Default.House, FutaColors.SubLabel, m) }
                    ),
                    spacing = 12.dp
                )
                DetailSection("Thời gian hiệu lực", Icons.Default.CalendarMonth, trailing = { if (status == "active") StatusPill(tr("Đang áp dụng"), FutaColors.BrandGreen) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ngày bắt đầu", fontSize = 11.5.sp, color = FutaColors.Slate)
                            Text(SalesFormatters.dateOnly(c["startDate"].string), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }
                        Icon(Icons.Default.ArrowForward, null, tint = FutaColors.Slate, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Ngày kết thúc", fontSize = 11.5.sp, color = FutaColors.Slate)
                            Text(SalesFormatters.dateOnly(c["endDate"].string), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }
                    }
                }
                DetailSection("Quy định tài chính & Quyền bán", Icons.Default.Payments) {
                    InfoRow("Tỷ lệ hoa hồng", SalesFormatters.percent(c["commissionRate"].double), FutaColors.BrandGreen)
                    HorizontalDivider(color = FutaColors.PanelDivider)
                    InfoRow("Mức chiết khấu niêm yết", SalesFormatters.percent(c["discountPercent"].double), FutaColors.BrandOrange)
                    HorizontalDivider(color = FutaColors.PanelDivider)
                    InfoRow("Thời hạn quyền bán sau duyệt", tr("{0} ngày", c["salesDurationDays"].int))
                    Text("Tính từ thời điểm quản trị viên duyệt đăng ký bán.", fontSize = 11.sp, color = FutaColors.Slate)
                    if (c["transactionType"].string.isNotEmpty()) {
                        HorizontalDivider(color = FutaColors.PanelDivider)
                        InfoRow("Hình thức giao dịch", c["transactionType"].string.translated("policy"))
                    }
                    if (c["commissionNote"].string.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.CreamBg).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(14.dp))
                                Text("Lưu ý hoa hồng trên sản phẩm", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                            }
                            Text(c["commissionNote"].string.translated("policy"), fontSize = 13.sp, color = FutaColors.Navy)
                            Text("Sản phẩm thuộc chương trình sẽ chỉ hiển thị nội dung này và không cho sửa riêng.", fontSize = 11.sp, color = FutaColors.Slate, fontStyle = FontStyle.Italic)
                        }
                    }
                }
                if (c["description"].string.isNotEmpty()) {
                    DetailSection("Mô tả chương trình", Icons.Default.Notes) {
                        Text(c["description"].string.translated("policy"), fontSize = 13.5.sp, color = FutaColors.Body, lineHeight = 20.sp)
                    }
                }
                if (c["policyDocumentName"].string.isNotEmpty()) {
                    DetailSection("Văn bản chính sách", Icons.Default.Description) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(FutaColors.RedPdfBg), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = FutaColors.RedPdf, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(c["policyDocumentName"].string.translated("policy"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                                Text("Tài liệu chính sách ban hành kèm theo", fontSize = 11.5.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }
                val blocks = c["externalPayload"]["blocks"].array
                if (blocks.isNotEmpty()) {
                    DetailSection(tr("Phạm vi mở bán ERP ({0} phân khu/tòa)", blocks.size), Icons.Default.Lock, iconTint = FutaColors.BrandOrange, trailing = { ErpLockBadge() }) {
                        blocks.forEach { blk ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAFC)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Apartment, null, tint = FutaColors.Slate, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Tòa/Khối: {0}", blk["block"].string), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                val parts = listOfNotNull(
                                    blk["floors"].int.takeIf { it > 0 }?.let { tr("{0} tầng", it) },
                                    blk["rooms"].int.takeIf { it > 0 }?.let { tr("{0} căn", it) }
                                ).joinToString(" · ")
                                if (parts.isNotEmpty()) Text(parts, fontSize = 12.sp, color = FutaColors.Slate)
                            }
                        }
                        if (c["appliedUnitsCount"].int > 0) {
                            Text(tr("Tổng cộng: {0} sản phẩm được áp dụng trong chương trình", c["appliedUnitsCount"].int), fontSize = 11.5.sp, color = FutaColors.Slate)
                        }
                    }
                }
                val policies = c["policies"].array
                DetailSection(tr("Chính sách ưu đãi dành cho khách hàng ({0})", policies.size), Icons.Default.CardGiftcard, iconTint = FutaColors.BrandOrange) {
                    if (policies.isEmpty()) {
                        Text("Chưa có chính sách ưu đãi bổ sung nào.", fontSize = 12.5.sp, color = FutaColors.Slate)
                    } else {
                        policies.forEachIndexed { i, policy -> PolicyItem(policy, i, onDelete = null) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    pendingStatus?.let { target ->
        ConfirmDialog(
            visible = true,
            title = if (target == "active") "Kích hoạt lại chiến dịch?" else "Ngưng hoạt động chiến dịch?",
            message = if (target == "active") "Chiến dịch sẽ được chuyển sang trạng thái đang hoạt động và mở quyền áp dụng chính sách." else "Chiến dịch sẽ ngưng hoạt động trên hệ thống bán hàng.",
            confirmText = if (target == "active") "Kích hoạt" else "Ngưng hoạt động",
            destructive = target != "active",
            onDismiss = { pendingStatus = null },
            onConfirm = { updateStatus(target) }
        )
    }
    ConfirmDialog(
        visible = showDelete,
        title = "Xác nhận xóa chiến dịch bán hàng?",
        message = tr("Chiến dịch \"{0}\" sẽ bị xóa vĩnh viễn khỏi hệ thống bán hàng.", c["name"].string),
        confirmText = "Xóa chiến dịch",
        destructive = true,
        onDismiss = { showDelete = false },
        onConfirm = { delete() }
    )
}

@Composable
private fun CampaignMetric(title: String, value: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder), modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            Text(subtitle, fontSize = 10.5.sp, color = FutaColors.Slate)
        }
    }
}

@Composable
private fun PolicyItem(policy: JSONValue, index: Int, onDelete: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FAFC)).border(1.dp, FutaColors.PanelDivider, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(FutaColors.BrandOrange), contentAlignment = Alignment.Center) {
                Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(policy["title"].string.translated("policy"), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            if (policy["status"].string == "active") StatusPill(tr("Hiệu lực"), FutaColors.BrandGreen)
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Delete, tr("Xóa chính sách"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (policy["tag"].string.isNotEmpty() || policy["validUntil"].string.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (policy["tag"].string.isNotEmpty()) StatusPill(policy["tag"].string.translated("policy"), FutaColors.BrandOrange, FutaColors.CreamBg)
                if (policy["validUntil"].string.isNotEmpty()) {
                    Icon(Icons.Default.Schedule, null, tint = FutaColors.Slate, modifier = Modifier.size(12.dp))
                    Text(tr("Áp dụng đến: {0}", policy["validUntil"].string), fontSize = 11.5.sp, color = FutaColors.Slate)
                }
            }
        }
        if (policy["content"].string.isNotEmpty()) {
            Text(policy["content"].string.translated("policy"), fontSize = 12.5.sp, color = FutaColors.Body, lineHeight = 18.sp)
        }
    }
}

// ============================================================================
// Create / edit form
// ============================================================================

private data class CampaignForm(
    val code: String,
    val name: String,
    val projectName: String,
    val projectId: String,
    val productType: String,
    val transactionType: String,
    val startDate: String,
    val endDate: String,
    val discountPercent: String,
    val commissionRate: String,
    val commissionNote: String,
    val salesDurationDays: String,
    val status: String,
    val description: String,
    val policyDocumentName: String,
    val policies: List<JSONValue>
) {
    companion object {
        private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun from(c: JSONValue?): CampaignForm {
            val today = Calendar.getInstance()
            val sixMonths = (today.clone() as Calendar).apply { add(Calendar.MONTH, 6) }
            fun num(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
            return CampaignForm(
                code = c?.get("code")?.string ?: "CTBH-${(1000..9999).random()}",
                name = c?.get("name")?.string.orEmpty(),
                projectName = c?.let { campaignProject(it) }.orEmpty(),
                projectId = c?.get("projectId")?.string.orEmpty(),
                productType = c?.get("productType")?.string?.ifEmpty { null } ?: "Căn hộ",
                transactionType = c?.get("transactionType")?.string?.ifEmpty { null } ?: "Căn hộ (có phí bảo trì)",
                startDate = c?.get("startDate")?.string?.take(10)?.ifEmpty { null } ?: iso.format(today.time),
                endDate = c?.get("endDate")?.string?.take(10)?.ifEmpty { null } ?: iso.format(sixMonths.time),
                discountPercent = if (c == null) "5" else num(c["discountPercent"].double),
                commissionRate = if (c == null) "1.5" else num(c["commissionRate"].double),
                commissionNote = c?.get("commissionNote")?.string.orEmpty(),
                salesDurationDays = (c?.get("salesDurationDays")?.int?.takeIf { it > 0 } ?: 15).toString(),
                status = c?.get("status")?.string?.ifEmpty { null } ?: "active",
                description = c?.get("description")?.string.orEmpty(),
                policyDocumentName = c?.get("policyDocumentName")?.string.orEmpty(),
                policies = c?.get("policies")?.array.orEmpty()
            )
        }
    }
}

private val baseProductTypes = listOf("Căn hộ", "Biệt thự", "Biệt thự liền kề", "Nhà phố", "Nhà liên kế", "Đất nền", "Căn hộ biển", "Shophouse", "Studio", "Duplex", "Penthouse")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminCampaignFormScreen(campaign: JSONValue?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isEdit = campaign != null
    val initial = remember(campaign) { CampaignForm.from(campaign) }
    var form by remember(campaign) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showAddPolicy by remember { mutableStateOf(false) }
    var policyToDelete by remember { mutableStateOf<Int?>(null) }
    var datePickerFor by remember { mutableStateOf<String?>(null) }
    var projects by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    val dirty = form != initial

    LaunchedEffect(Unit) {
        projects = runCatching { APIClient.get().request("/projects/admin/list")["data"].array }.getOrNull()
            ?: runCatching { APIClient.get().request("/projects")["data"].array }.getOrDefault(emptyList())
    }

    fun cancel() { if (dirty) showDiscard = true else onClose() }
    BackHandler { cancel() }

    fun projectLabel(p: JSONValue) = firstNonEmpty(p["displayName"].string, p["name"].string, p["zone"].string)

    fun save() {
        val code = form.code.trim().uppercase()
        val name = form.name.trim()
        val project = form.projectName.trim()
        val discount = form.discountPercent.replace(',', '.').toDoubleOrNull()
        val commission = form.commissionRate.replace(',', '.').toDoubleOrNull()
        val days = form.salesDurationDays.toIntOrNull()
        errorMessage = when {
            code.length < 2 -> tr("Vui lòng nhập mã chiến dịch")
            name.length < 2 -> tr("Vui lòng nhập tên chiến dịch")
            project.length < 2 -> tr("Vui lòng chọn hoặc nhập dự án")
            form.endDate < form.startDate -> tr("Ngày kết thúc phải sau hoặc cùng ngày bắt đầu")
            discount == null || discount < 0 || discount > 100 -> tr("Chiết khấu phải từ 0 đến 100%")
            commission == null || commission < 0 || commission > 100 -> tr("Tỷ lệ hoa hồng phải từ 0 đến 100%")
            days == null || days !in 1..365 -> tr("Thời hạn quyền bán phải từ 1 đến 365 ngày")
            else -> null
        }
        if (errorMessage != null) {
            ToastCenter.show(errorMessage!!, isError = true)
            return
        }
        val body = campaignBody(
            code = code, name = name, projectName = project, projectId = form.projectId, productType = form.productType,
            transactionType = form.transactionType, startDate = form.startDate, endDate = form.endDate,
            discountPercent = discount!!, commissionRate = commission!!, commissionNote = form.commissionNote,
            salesDurationDays = days!!, status = form.status, description = form.description,
            policyDocumentName = form.policyDocumentName.trim(), policies = form.policies,
            appliedUnitsCount = campaign?.get("appliedUnitsCount")?.takeIf { !it.isNull }?.int
        )
        scope.launch {
            saving = true
            try {
                if (isEdit) {
                    APIClient.get().request("/sales/campaigns/${Uri.encode(campaign!!.id)}", "PUT", body)
                    ToastCenter.show("Cập nhật chiến dịch thành công")
                } else {
                    APIClient.get().request("/sales/campaigns", "POST", body)
                    ToastCenter.show("Tạo chiến dịch mới thành công")
                }
                onSaved()
            } catch (e: Exception) {
                errorMessage = e.message
                ToastCenter.show(tr("Lỗi: {0}", e.message), isError = true)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = if (isEdit) "Sửa chiến dịch" else "Thêm chiến dịch", subtitle = campaign?.get("name")?.string, onBack = { cancel() }) {
                if (isEdit) {
                    FutaHeaderIconButton(
                        icon = Icons.Default.RestartAlt, contentDescription = tr("Khôi phục dữ liệu gốc"),
                        onClick = { if (dirty) form = initial }, tint = if (dirty) Color(0xFFDC2626) else Color(0xFFCBD5E1)
                    )
                }
            }
        },
        bottomBar = { FormActionBar(if (isEdit) "Lưu thay đổi" else "Tạo chiến dịch", saving, true, { cancel() }, { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).clearFocusOnTap().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FormErrorBanner(errorMessage)
            FormSection("Thông tin chung") {
                FormTextField("Mã chiến dịch", form.code, { form = form.copy(code = it) }, "Nhập mã chiến dịch…", required = true)
                FormTextField("Tên chiến dịch", form.name, { form = form.copy(name = it) }, "Nhập tên chiến dịch…", required = true)
                if (projects.isNotEmpty()) {
                    val options = projects.map { projectLabel(it) }.filter { it.isNotEmpty() }.distinct()
                    AdminSelectField(
                        "Dự án áp dụng *", form.projectName,
                        (options + listOfNotNull(form.projectName.takeIf { it.isNotEmpty() && it !in options })).map { SelectOption(it, it.translated("project")) },
                        { selected ->
                            val match = projects.firstOrNull { projectLabel(it) == selected }
                            form = form.copy(
                                projectName = selected,
                                projectId = match?.id ?: form.projectId,
                                productType = match?.get("projectType")?.string?.ifEmpty { null } ?: form.productType
                            )
                        },
                        placeholder = "Chọn dự án…"
                    )
                } else {
                    FormTextField("Tên dự án", form.projectName, { form = form.copy(projectName = it, projectId = "") }, "Nhập tên dự án…", required = true)
                }
                val typeOptions = (listOfNotNull(form.productType.takeIf { it.isNotEmpty() }) + baseProductTypes + projects.map { it["projectType"].string }.filter { it.isNotEmpty() }).distinct()
                AdminSelectField("Loại sản phẩm", form.productType, typeOptions.map { SelectOption(it, it) }, { form = form.copy(productType = it) }, placeholder = "Chọn loại sản phẩm...")
                FormTextField("Tùy chỉnh loại sản phẩm", form.productType, { form = form.copy(productType = it) }, "Nhập hoặc tùy chỉnh loại sản phẩm")
                FormTextField("Hình thức giao dịch", form.transactionType, { form = form.copy(transactionType = it) }, "Nhập hình thức giao dịch (VD: Bán, Cho thuê)")
                AdminSelectField(
                    "Trạng thái", form.status,
                    listOf(SelectOption("active", "Đang diễn ra"), SelectOption("scheduled", "Sắp diễn ra"), SelectOption("ended", "Đã kết thúc")),
                    { form = form.copy(status = it) }
                )
            }
            FormSection("Thời hạn & Quy định bán hàng") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { FutaSelectField(title = tr("Ngày bắt đầu"), displayValue = SalesFormatters.dateOnly(form.startDate), onClick = { datePickerFor = "start" }) }
                    Box(Modifier.weight(1f)) { FutaSelectField(title = tr("Ngày kết thúc"), displayValue = SalesFormatters.dateOnly(form.endDate), onClick = { datePickerFor = "end" }) }
                }
                FormTextField("Thời hạn quyền bán TVV (ngày)", form.salesDurationDays, { form = form.copy(salesDurationDays = it.filter { c -> c.isDigit() }.take(3)) }, "15", keyboardType = KeyboardType.Number)
            }
            FormSection("Chính sách tài chính & Hoa hồng") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { FormTextField("Chiết khấu (%)", form.discountPercent, { form = form.copy(discountPercent = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, "0.0", keyboardType = KeyboardType.Decimal) }
                    Box(Modifier.weight(1f)) { FormTextField("Tỷ lệ hoa hồng (%)", form.commissionRate, { form = form.copy(commissionRate = it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, "0.0", keyboardType = KeyboardType.Decimal) }
                }
                FormTextField("Nội dung lưu ý hoa hồng & Thưởng", form.commissionNote, { form = form.copy(commissionNote = it) }, "Nội dung lưu ý hoa hồng & Thưởng…", multiline = true)
            }
            FormSection("Mô tả & Văn bản chính sách") {
                FormTextField("Mô tả chi tiết chiến dịch", form.description, { form = form.copy(description = it) }, "Mô tả chi tiết chiến dịch…", multiline = true)
                FormTextField("Tên văn bản chính sách", form.policyDocumentName, { form = form.copy(policyDocumentName = it) }, "VD: Chính sách bán hàng Q3/2026")
            }
            FormSection(tr("Chính sách ưu đãi khách hàng ({0})", form.policies.size)) {
                form.policies.forEachIndexed { i, policy -> PolicyItem(policy, i, onDelete = { policyToDelete = i }) }
                FutaButton(text = "Thêm chính sách ưu đãi", icon = Icons.Default.AddCircle, variant = FutaButtonVariant.MINT, height = 40.dp, onClick = { showAddPolicy = true })
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = onClose)
    policyToDelete?.let { index ->
        ConfirmDialog(
            visible = true,
            title = "Xác nhận xóa chính sách?",
            message = "Chính sách ưu đãi này sẽ bị xóa khỏi chương trình.",
            confirmText = "Xóa chính sách",
            destructive = true,
            onDismiss = { policyToDelete = null },
            onConfirm = { form = form.copy(policies = form.policies.filterIndexed { i, _ -> i != index }) }
        )
    }
    if (showAddPolicy) {
        var title by remember { mutableStateOf("") }
        var tag by remember { mutableStateOf("ƯU ĐÃI ĐẶC BIỆT") }
        var validUntil by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        FutaBottomSheet(
            visible = true,
            onDismiss = { showAddPolicy = false },
            title = "Thêm chính sách",
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = { showAddPolicy = false }, modifier = Modifier.weight(1f))
                    FutaButton(
                        text = "Thêm", icon = Icons.Default.Check, enabled = title.isNotBlank() && content.isNotBlank(), modifier = Modifier.weight(1.5f),
                        onClick = {
                            val entry = buildJsonObject {
                                put("id", "pol-${System.currentTimeMillis()}")
                                put("title", title.trim())
                                put("tag", tag.trim())
                                put("status", "active")
                                put("validUntil", validUntil.trim())
                                put("content", content.trim())
                            }
                            form = form.copy(policies = form.policies + JSONValue(entry))
                            showAddPolicy = false
                        }
                    )
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormTextField("Tiêu đề chính sách", title, { title = it }, "Nhập tiêu đề chính sách…", required = true)
                FormTextField("Thẻ tag", tag, { tag = it }, "VD: HOT, ƯU ĐÃI ĐẶC BIỆT")
                FormTextField("Thời hạn áp dụng", validUntil, { validUntil = it }, "VD: 31/12/2026")
                FormTextField("Nội dung chính sách ưu đãi", content, { content = it }, "Nhập nội dung ưu đãi…", required = true, multiline = true)
            }
        }
    }
    datePickerFor?.let { which ->
        val iso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
        val current = if (which == "start") form.startDate else form.endDate
        val state = rememberDatePickerState(initialSelectedDateMillis = runCatching { iso.parse(current)?.time }.getOrNull())
        DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val value = iso.format(java.util.Date(millis))
                        form = if (which == "start") form.copy(startDate = value) else form.copy(endDate = value)
                    }
                    datePickerFor = null
                }) { Text("Chọn", color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { datePickerFor = null }) { Text("Hủy", color = FutaColors.Slate) } }
        ) {
            DatePicker(state = state)
        }
    }
}
