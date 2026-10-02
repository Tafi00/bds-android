package vn.futaland.app.features.salesadmin

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.i18n.LocalizedDirection
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PaymentSchedulePanel
import vn.futaland.app.features.properties.PropertyFormatters

// Mirrors iOS Features/SalesAdmin/AdminInventoryView.swift:
// list (/apartments, all pages) → detail (/apartments/:id?context=admin) → edit (PUT /apartments/:id),
// open/withdraw sale and bulk publish (POST /apartments/bulk-publication).

private sealed interface InventoryRoute {
    data class Detail(val id: String, val version: Int = 0) : InventoryRoute
    data class Edit(val unit: JSONValue) : InventoryRoute
}

/** iOS `InventoryFilters` (11 filters). */
private data class InventoryFilters(
    val status: String = "all",
    val project: String = "all",
    val projectName: String = "all",
    val block: String = "all",
    val floor: String = "all",
    val productType: String = "all",
    val propertyType: String = "all",
    val direction: String = "all",
    val balconyDirection: String = "all",
    val source: String = "all",
    val isFeatured: String = "all"
) {
    val activeCount: Int
        get() = listOf(status, project, projectName, block, floor, productType, propertyType, direction, balconyDirection, source, isFeatured).count { it != "all" }
}

/** iOS `InventorySortOption` (8 sorts). */
private enum class InventorySort(val title: String) {
    UPDATED_DESC("Mới nhất"),
    PRICE_ASC("Giá: Thấp đến Cao"),
    PRICE_DESC("Giá: Cao đến Thấp"),
    AREA_ASC("Diện tích: Bé đến Lớn"),
    AREA_DESC("Diện tích: Lớn đến Bé"),
    FLOOR_ASC("Tầng: Thấp đến Cao"),
    FLOOR_DESC("Tầng: Cao đến Thấp"),
    CODE_ASC("Mã căn: A → Z")
}

private val inventoryStatusOptions = listOf(
    SelectOption("all", "Tất cả trạng thái"),
    SelectOption("selling", "Đang mở bán"),
    SelectOption("holding", "Đang giữ chỗ / Đã cọc"),
    SelectOption("unopened", "Chưa mở bán"),
    SelectOption("sold", "Đã bán"),
    SelectOption("paused", "Ngừng bán / Tạm khóa")
)

/** `status` is an array of labels on inventory rows; tolerate a plain string too. */
private fun statusList(item: JSONValue): List<String> {
    val arr = item["status"].array.map { it.string }.filter { it.isNotEmpty() }
    if (arr.isNotEmpty()) return arr
    return listOfNotNull(item["status"].string.takeIf { it.isNotEmpty() })
}

private fun unitId(item: JSONValue): String = item["id"].string.ifEmpty { item.id }

private fun unitCode(item: JSONValue): String = firstNonEmpty(item["propertyCode"].string, item["code"].string, item["title"].string)

private fun isErpUnit(item: JSONValue) = item["source"].string == "ERP" || item["externalLockedFields"].array.isNotEmpty() || item["externalId"].string.isNotEmpty()

private fun isHoldingUnit(item: JSONValue): Boolean {
    val s = statusList(item)
    return s.contains("Đang giữ chỗ") || s.contains("Đã cọc") || item["activeHoldingRegistrationId"].string.isNotEmpty()
}

private fun unitPrice(item: JSONValue) = item["sellPrice"].double.takeIf { it > 0 } ?: item["price"].double

private fun unitArea(item: JSONValue) = item["usableAreaM2"].double.takeIf { it > 0 } ?: item["size_m2"].double.takeIf { it > 0 } ?: item["usableArea"].double

private fun unitFloor(item: JSONValue) = item["floor"].string.toIntOrNull() ?: item["floor"].int

private fun resolveImageEntry(value: JSONValue): String {
    val s = value.string
    if (s.isNotEmpty() && (s.startsWith("http") || s.startsWith("/"))) return s
    return firstNonEmpty(value["original"].string, value["url"].string, value["imageUrl"].string, value["path"].string)
}

private fun unitThumbnail(item: JSONValue): String {
    item["images"].array.forEach { img -> resolveImageEntry(img).takeIf { it.isNotEmpty() }?.let { return it } }
    return firstNonEmpty(item["image"].string, item["imageUrl"].string)
}

/** iOS `formatUnitType`. */
private fun formatUnitType(raw: String, bedrooms: Int): String {
    val lower = raw.trim().lowercase()
    if (lower.contains("studio") || lower == "stu") return "Studio"
    if (bedrooms > 0) return tr("{0} PN", bedrooms)
    return when (lower) {
        "apartment", "can-ho", "can-ho-chung-cu" -> "Căn hộ"
        "1pn", "1pn+" -> "1 PN"
        "2pn", "2pn+", "2pn1wc", "2pn2wc" -> "2 PN"
        "3pn", "3pn+" -> "3 PN"
        "penhouse", "penthouse" -> "Penthouse"
        "duplex" -> "Duplex"
        "shophouse" -> "Shophouse"
        "villa", "biet-thu" -> "Biệt thự"
        "townhouse", "nha-pho", "nha-lien-ke" -> "Nhà phố"
        else -> raw.ifEmpty { "Căn hộ" }
    }
}

/** iOS `formatPropertyType`. */
private fun formatPropertyType(raw: String): String = when (raw.lowercase()) {
    "can-ho-chung-cu", "apartment", "can-ho" -> "Căn hộ chung cư"
    "nha-pho", "townhouse" -> "Nhà phố liền kề"
    "biet-thu", "villa" -> "Biệt thự"
    "shophouse" -> "Nhà phố thương mại (Shophouse)"
    "dat-nen", "land" -> "Đất nền dự án"
    "duplex" -> "Duplex"
    "penthouse" -> "Penthouse"
    else -> raw.ifEmpty { "Căn hộ" }
}

/** iOS `formatLockedField`. */
private fun formatLockedField(key: String): String = when (key) {
    "title" -> "Tên sản phẩm"
    "propertyCode" -> "Mã căn"
    "zone" -> "Phân khu"
    "projectId" -> "Dự án"
    "salesCampaignId" -> "Chiến dịch"
    "building" -> "Tòa nhà"
    "floor" -> "Tầng"
    "unit" -> "Số căn"
    "direction" -> "Hướng cửa"
    "balconyDirection" -> "Hướng ban công"
    "views" -> "Tầm view"
    "apartmentType" -> "Loại hình BĐS"
    "size_m2", "areaM2" -> "Diện tích tim tường"
    "usableAreaM2" -> "Diện tích thông thủy"
    "wallAreaM2" -> "Diện tích xây dựng"
    "landArea" -> "Diện tích đất"
    "totalFloorArea" -> "Tổng sàn"
    "totalFloors" -> "Số tầng"
    "townhouseSpecs" -> "Thông số nhà phố"
    "price", "sellPrice" -> "Giá bán ERP"
    "salePriceLabel" -> "Nhãn giá"
    "pricingBreakdown" -> "Bảng giá chi tiết"
    "address" -> "Địa chỉ"
    "province" -> "Tỉnh/TP"
    "ward" -> "Phường/Xã"
    "latitude", "longitude" -> "Tọa độ GPS"
    else -> key
}

private fun matchesUnit(item: JSONValue, f: InventoryFilters, search: String): Boolean {
    val q = search.trim()
    if (q.isNotEmpty()) {
        val text = listOf("propertyCode", "code", "title", "projectName", "block", "unitNumber", "floor", "unitType", "propertyType").joinToString(" ") { item[it].string }
        if (!text.contains(q, ignoreCase = true)) return false
    }
    val s = statusList(item)
    when (f.status) {
        "selling" -> if (!s.contains("Đang mở bán") && !s.contains("selling")) return false
        "holding" -> if (!isHoldingUnit(item)) return false
        "unopened" -> if (!s.contains("Chưa mở bán") && s.isNotEmpty()) return false
        "sold" -> if (!s.contains("Đã bán") && !s.contains("sold")) return false
        "paused" -> if (!s.contains("Ngừng bán") && !s.contains("Tạm khóa") && !s.contains("paused")) return false
    }
    if (f.project != "all" && item["projectName"].string != f.project) return false
    if (f.projectName != "all" && item["zone"].string.ifEmpty { item["projectName"].string } != f.projectName) return false
    if (f.block != "all" && item["block"].string != f.block) return false
    if (f.floor != "all" && item["floor"].string != f.floor) return false
    if (f.productType != "all" && item["unitType"].string != f.productType) return false
    if (f.propertyType != "all" && item["propertyType"].string != f.propertyType) return false
    if (f.direction != "all" && item["direction"].string != f.direction) return false
    if (f.balconyDirection != "all" && item["balconyDirection"].string != f.balconyDirection) return false
    val erp = item["source"].string == "ERP" || item["externalLockedFields"].array.isNotEmpty()
    if (f.source == "ERP" && !erp) return false
    if (f.source == "CMS" && erp) return false
    val featured = item["isFavorite"].bool || item["featured"].bool
    if (f.isFeatured == "featured" && !featured) return false
    if (f.isFeatured == "standard" && featured) return false
    return true
}

private fun sortUnits(list: List<JSONValue>, sort: InventorySort): List<JSONValue> = when (sort) {
    InventorySort.UPDATED_DESC -> list.sortedByDescending { it["updatedAt"].string }
    InventorySort.PRICE_ASC -> list.sortedBy { unitPrice(it) }
    InventorySort.PRICE_DESC -> list.sortedByDescending { unitPrice(it) }
    InventorySort.AREA_ASC -> list.sortedBy { it["usableAreaM2"].double.takeIf { v -> v > 0 } ?: it["size_m2"].double }
    InventorySort.AREA_DESC -> list.sortedByDescending { it["usableAreaM2"].double.takeIf { v -> v > 0 } ?: it["size_m2"].double }
    InventorySort.FLOOR_ASC -> list.sortedBy { unitFloor(it) }
    InventorySort.FLOOR_DESC -> list.sortedByDescending { unitFloor(it) }
    InventorySort.CODE_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["propertyCode"].string.ifEmpty { it["title"].string } })
}

/** Loads every page of the admin inventory, 200 rows at a time (iOS `loadInventory`). */
private suspend fun fetchAllUnits(): List<JSONValue> = coroutineScope {
    val base = mapOf("limit" to "200", "context" to "admin", "includeUnpublished" to "true")
    val first = APIClient.get().request("/apartments", query = base + ("page" to "1"))
    val totalPages = first["pagination"]["totalPages"].int.coerceIn(1, 50)
    val rest = (2..totalPages).map { page ->
        async { runCatching { APIClient.get().request("/apartments", query = base + ("page" to "$page"))["data"].array }.getOrDefault(emptyList()) }
    }.awaitAll()
    (first["data"].array + rest.flatten()).distinctBy { unitId(it).ifEmpty { unitCode(it) } }
}

@Composable
fun AdminInventoryScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<InventoryRoute>() }
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(InventoryFilters()) }
    var sort by remember { mutableStateOf(InventorySort.UPDATED_DESC) }
    var displayLimit by rememberSaveable { mutableIntStateOf(20) }
    val listState = rememberLazyListState()

    fun load() {
        scope.launch {
            try {
                items = fetchAllUnits()
                loadError = null
            } catch (e: Exception) {
                loadError = e.message ?: tr("Không thể tải kho sản phẩm")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenStackHost(
        stack = stack,
        base = {
            InventoryListContent(
                items = items, loading = loading, loadError = loadError,
                search = search, onSearch = { search = it; displayLimit = 20 },
                filters = filters, onFilters = { filters = it; displayLimit = 20 },
                sort = sort, onSort = { sort = it; displayLimit = 20 },
                displayLimit = displayLimit, onDisplayLimit = { displayLimit = it },
                listState = listState,
                onBack = onBack,
                onRetry = { loading = true; load() },
                onReload = { load() },
                onOpen = { stack.push(InventoryRoute.Detail(it)) }
            )
        }
    ) { route ->
        when (route) {
            is InventoryRoute.Detail -> AdminInventoryDetailScreen(
                unitIdArg = route.id,
                onBack = { stack.pop() },
                onEdit = { stack.push(InventoryRoute.Edit(it)) },
                onChanged = { load() }
            )
            is InventoryRoute.Edit -> AdminInventoryEditScreen(route.unit, onClose = { stack.pop() }, onSaved = {
                stack.pop()
                val detail = stack.entries.lastOrNull()
                if (detail is InventoryRoute.Detail) stack.replaceTop(detail.copy(version = detail.version + 1))
                load()
            })
        }
    }
}

// ============================================================================
// Listing
// ============================================================================

@Composable
private fun InventoryListContent(
    items: List<JSONValue>,
    loading: Boolean,
    loadError: String?,
    search: String,
    onSearch: (String) -> Unit,
    filters: InventoryFilters,
    onFilters: (InventoryFilters) -> Unit,
    sort: InventorySort,
    onSort: (InventorySort) -> Unit,
    displayLimit: Int,
    onDisplayLimit: (Int) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOpen: (String) -> Unit
) {
    var showFilter by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showBulk by remember { mutableStateOf(false) }
    val isAdmin = AppSession.shared.role == "admin"

    val sorted = remember(items, filters, search, sort) { sortUnits(items.filter { matchesUnit(it, filters, search) }, sort) }
    val displayed = sorted.take(displayLimit)
    val sellingCount = items.count { statusList(it).let { s -> s.contains("Đang mở bán") || s.contains("selling") } }
    val holdingCount = items.count { isHoldingUnit(it) }
    val soldCount = items.count { statusList(it).let { s -> s.contains("Đã bán") || s.contains("sold") } }
    val unopenedCount = items.count { statusList(it).let { s -> s.contains("Chưa mở bán") || s.isEmpty() } }
    val erpCount = items.count { isErpUnit(it) }

    // Auto "load more" when the list reaches the last displayed card.
    LaunchedEffect(listState, sorted.size, displayLimit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= listState.layoutInfo.totalItemsCount - 3 && displayLimit < sorted.size) onDisplayLimit(minOf(displayLimit + 20, sorted.size))
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Kho sản phẩm", onBack = onBack, subtitle = if (selectionMode) tr("Đã chọn {0} căn", selected.size) else null) {
                if (isAdmin && items.isNotEmpty()) {
                    FutaHeaderIconButton(
                        icon = if (selectionMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
                        contentDescription = tr("Chọn nhiều"),
                        tint = if (selectionMode) FutaColors.BrandGreen else FutaColors.Navy,
                        onClick = { selectionMode = !selectionMode; if (!selectionMode) selected = emptySet() }
                    )
                }
                SortMenuButton(InventorySort.entries, sort, { it.title }, onSort, isDefault = sort == InventorySort.UPDATED_DESC)
                BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), filters.activeCount) { showFilter = true }
            }
        },
        bottomBar = {
            if (selectionMode && selected.isNotEmpty()) {
                FutaStickyActionBar {
                    FutaButton(text = "Bỏ hết", variant = FutaButtonVariant.OUTLINE, onClick = { selected = emptySet() }, modifier = Modifier.weight(1f))
                    FutaButton(text = tr("Xử lý ({0})", selected.size), icon = Icons.Default.Publish, onClick = { showBulk = true }, modifier = Modifier.weight(1.6f))
                }
            }
        }
    ) { padding ->
        when {
            loading && items.isEmpty() -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp); FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp) } }
                repeat(4) { FutaPropertyCardSkeleton() }
            }
            loadError != null && items.isEmpty() -> AdminErrorState(loadError, onRetry, Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { AdminSearchField(search, onSearch, "Tìm theo mã căn, tòa, tầng, dự án…") }
                item {
                    fun toggle(status: String) = onFilters(filters.copy(status = if (filters.status == status) "all" else status))
                    MetricGrid(
                        listOf(
                            { m -> SalesMetricCard("Tổng kho căn", "${items.size}", Icons.Default.GridView, FutaColors.BrandGreen, m, subtitle = tr("Toàn bộ"), selected = filters.status == "all", onClick = { onFilters(filters.copy(status = "all")) }) },
                            { m -> SalesMetricCard("Đang mở bán", "$sellingCount", Icons.Default.LocalFireDepartment, Color(0xFF2563EB), m, subtitle = tr("Sẵn sàng"), selected = filters.status == "selling", onClick = { toggle("selling") }) },
                            { m -> SalesMetricCard("Đang giữ chỗ / Cọc", "$holdingCount", Icons.Default.Shield, FutaColors.BrandOrange, m, subtitle = tr("Đang giao dịch"), selected = filters.status == "holding", onClick = { toggle("holding") }) },
                            { m -> SalesMetricCard("Đã bán", "$soldCount", Icons.Default.CheckCircle, Color(0xFF7C3AED), m, subtitle = tr("Thành công"), selected = filters.status == "sold", onClick = { toggle("sold") }) }
                        )
                    )
                }
                item {
                    QuickChipRow {
                        QuickChip(tr("Tất cả ({0})", items.size), filters.status == "all", Icons.Default.Inbox) { onFilters(filters.copy(status = "all")) }
                        QuickChip(tr("Đang mở bán ({0})", sellingCount), filters.status == "selling", Icons.Default.LocalFireDepartment) { onFilters(filters.copy(status = "selling")) }
                        QuickChip(tr("Đang giữ chỗ ({0})", holdingCount), filters.status == "holding", Icons.Default.Shield) { onFilters(filters.copy(status = "holding")) }
                        QuickChip(tr("Đã bán ({0})", soldCount), filters.status == "sold", Icons.Default.CheckCircle) { onFilters(filters.copy(status = "sold")) }
                        QuickChip(tr("Chưa mở bán ({0})", unopenedCount), filters.status == "unopened", Icons.Default.Schedule) { onFilters(filters.copy(status = "unopened")) }
                        if (erpCount > 0) QuickChip(tr("Khóa ERP ({0})", erpCount), filters.source == "ERP", Icons.Default.Lock) { onFilters(filters.copy(source = if (filters.source == "ERP") "all" else "ERP")) }
                    }
                }
                val applied = buildList {
                    if (filters.status != "all") add(AppliedFilter(tr(inventoryStatusOptions.first { it.value == filters.status }.label)) { onFilters(filters.copy(status = "all")) })
                    if (filters.project != "all") add(AppliedFilter(filters.project.translated("project")) { onFilters(filters.copy(project = "all")) })
                    if (filters.projectName != "all") add(AppliedFilter(tr("Phân khu: {0}", filters.projectName)) { onFilters(filters.copy(projectName = "all")) })
                    if (filters.block != "all") add(AppliedFilter(tr("Tòa {0}", filters.block)) { onFilters(filters.copy(block = "all")) })
                    if (filters.floor != "all") add(AppliedFilter(tr("Tầng {0}", filters.floor)) { onFilters(filters.copy(floor = "all")) })
                    if (filters.propertyType != "all") add(AppliedFilter(tr(formatPropertyType(filters.propertyType))) { onFilters(filters.copy(propertyType = "all")) })
                    if (filters.productType != "all") add(AppliedFilter(filters.productType) { onFilters(filters.copy(productType = "all")) })
                    if (filters.direction != "all") add(AppliedFilter(tr("Cửa: {0}", LocalizedDirection.name(filters.direction))) { onFilters(filters.copy(direction = "all")) })
                    if (filters.balconyDirection != "all") add(AppliedFilter(tr("Ban công: {0}", LocalizedDirection.name(filters.balconyDirection))) { onFilters(filters.copy(balconyDirection = "all")) })
                    if (filters.source != "all") add(AppliedFilter(tr(if (filters.source == "ERP") "Đồng bộ ERP" else "Nhập từ CMS")) { onFilters(filters.copy(source = "all")) })
                    if (filters.isFeatured != "all") add(AppliedFilter(tr(if (filters.isFeatured == "featured") "Sản phẩm nổi bật" else "Sản phẩm tiêu chuẩn")) { onFilters(filters.copy(isFeatured = "all")) })
                }
                if (applied.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Đang lọc theo {0} tiêu chí · {1} căn", filters.activeCount, sorted.size), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                            AppliedFilterChips(applied) { onFilters(InventoryFilters()) }
                        }
                    }
                }
                if (selectionMode) {
                    item {
                        val pageIds = displayed.map { unitId(it) }
                        val allSelected = pageIds.isNotEmpty() && pageIds.all { it in selected }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.MintBg).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tr("Đã chọn {0} căn", selected.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.weight(1f))
                            Text(
                                if (allSelected) "Bỏ chọn đang xem" else "Chọn các căn đang xem",
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy,
                                modifier = Modifier.clickable { selected = if (allSelected) selected - pageIds.toSet() else selected + pageIds }
                            )
                        }
                    }
                }
                if (sorted.isEmpty()) {
                    item {
                        if (items.isEmpty()) {
                            FutaEmptyState(title = "Kho sản phẩm trống", message = "Chưa có căn hộ nào trong kho sản phẩm.", icon = Icons.Default.Home)
                        } else {
                            FutaEmptyState(
                                title = "Không tìm thấy sản phẩm",
                                message = "Không có căn hộ nào khớp với tiêu chí tìm kiếm hoặc bộ lọc hiện tại.",
                                icon = Icons.Default.SearchOff,
                                actionButton = { FutaButton(text = "Đặt lại bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = { onFilters(InventoryFilters()); onSearch("") }) }
                            )
                        }
                    }
                } else {
                    items(displayed, key = { unitId(it).ifEmpty { unitCode(it) } }) { item ->
                        val id = unitId(item)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (selectionMode) {
                                Icon(
                                    if (id in selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    tr("Chọn"),
                                    tint = if (id in selected) FutaColors.BrandGreen else Color(0xFF94A3B8),
                                    modifier = Modifier.size(26.dp).clickable { selected = if (id in selected) selected - id else selected + id }
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                InventoryCard(item) {
                                    if (selectionMode) selected = if (id in selected) selected - id else selected + id else onOpen(id)
                                }
                            }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (displayLimit < sorted.size) {
                                Text(tr("Đang hiển thị {0} / {1} căn", displayed.size, sorted.size), fontSize = 12.sp, color = FutaColors.Slate)
                                FutaButton(
                                    text = tr("Tải thêm {0} sản phẩm", minOf(20, sorted.size - displayLimit)),
                                    icon = Icons.Default.ExpandMore, variant = FutaButtonVariant.OUTLINE, height = 38.dp,
                                    onClick = { onDisplayLimit(minOf(displayLimit + 20, sorted.size)) }
                                )
                            } else {
                                Text(tr("Đã hiển thị toàn bộ {0} sản phẩm", sorted.size), fontSize = 12.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showFilter) {
        InventoryFilterSheet(filters, items, onApply = { onFilters(it); showFilter = false }, onDismiss = { showFilter = false })
    }
    if (showBulk) {
        BulkPublicationSheet(
            unitIds = selected.toList(),
            onDismiss = { showBulk = false },
            onDone = {
                showBulk = false
                selectionMode = false
                selected = emptySet()
                onReload()
            }
        )
    }
}

@Composable
private fun InventoryFilterSheet(initial: InventoryFilters, items: List<JSONValue>, onApply: (InventoryFilters) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    fun distinct(key: String) = items.map { it[key].string }.filter { it.isNotEmpty() }.distinct().sorted()
    val projects = remember(items) { distinct("projectName") }
    val zones = remember(items) { items.map { it["zone"].string.ifEmpty { it["projectName"].string } }.filter { it.isNotEmpty() }.distinct().sorted() }
    val blocks = remember(items) { distinct("block") }
    val floors = remember(items) { items.map { it["floor"].string }.filter { it.isNotEmpty() }.distinct().sortedBy { it.toIntOrNull() ?: 0 } }
    val unitTypes = remember(items) { distinct("unitType") }
    val propertyTypes = remember(items) { distinct("propertyType") }
    val directions = remember(items) { distinct("direction") }
    val balconies = remember(items) { distinct("balconyDirection") }
    FilterSheet(
        visible = true,
        title = "Bộ lọc sản phẩm",
        applyTitle = if (draft.activeCount > 0) tr("Áp dụng ({0} tiêu chí)", draft.activeCount) else tr("Áp dụng bộ lọc"),
        canReset = draft != InventoryFilters(),
        onReset = { draft = InventoryFilters() },
        onApply = { onApply(draft) },
        onDismiss = onDismiss
    ) {
        Text("Trạng thái kinh doanh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
        AdminSelectField(null, draft.status, inventoryStatusOptions, { draft = draft.copy(status = it) })
        Text("Dự án & Vị trí", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
        if (projects.isNotEmpty()) AdminSelectField("Dự án", draft.project, listOf(SelectOption("all", "Tất cả dự án")) + projects.map { SelectOption(it, it.translated("project")) }, { draft = draft.copy(project = it) })
        if (zones.isNotEmpty()) AdminSelectField("Phân khu / Zone", draft.projectName, listOf(SelectOption("all", "Tất cả phân khu")) + zones.map { SelectOption(it, it.translated("project")) }, { draft = draft.copy(projectName = it) })
        if (blocks.isNotEmpty()) AdminSelectField("Tòa nhà", draft.block, listOf(SelectOption("all", "Tất cả tòa nhà")) + blocks.map { SelectOption(it, it) }, { draft = draft.copy(block = it) })
        if (floors.isNotEmpty()) AdminSelectField("Tầng", draft.floor, listOf(SelectOption("all", "Tất cả tầng")) + floors.map { SelectOption(it, tr("Tầng {0}", it)) }, { draft = draft.copy(floor = it) })
        Text("Đặc điểm sản phẩm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
        if (propertyTypes.isNotEmpty()) AdminSelectField("Loại hình BĐS", draft.propertyType, listOf(SelectOption("all", "Tất cả loại hình")) + propertyTypes.map { SelectOption(it, formatPropertyType(it)) }, { draft = draft.copy(propertyType = it) })
        if (unitTypes.isNotEmpty()) AdminSelectField("Loại căn / Phòng ngủ", draft.productType, listOf(SelectOption("all", "Tất cả loại căn")) + unitTypes.map { SelectOption(it, it) }, { draft = draft.copy(productType = it) })
        if (directions.isNotEmpty()) AdminSelectField("Hướng cửa chính", draft.direction, listOf(SelectOption("all", "Tất cả hướng cửa")) + directions.map { SelectOption(it, LocalizedDirection.name(it)) }, { draft = draft.copy(direction = it) })
        if (balconies.isNotEmpty()) AdminSelectField("Hướng ban công", draft.balconyDirection, listOf(SelectOption("all", "Tất cả hướng ban công")) + balconies.map { SelectOption(it, LocalizedDirection.name(it)) }, { draft = draft.copy(balconyDirection = it) })
        Text("Nguồn dữ liệu & Nổi bật", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
        AdminSelectField("Nguồn dữ liệu", draft.source, listOf(SelectOption("all", "Tất cả nguồn"), SelectOption("ERP", "Đồng bộ ERP"), SelectOption("CMS", "Nhập từ CMS")), { draft = draft.copy(source = it) })
        AdminSelectField("Sản phẩm nổi bật", draft.isFeatured, listOf(SelectOption("all", "Tất cả"), SelectOption("featured", "Sản phẩm nổi bật"), SelectOption("standard", "Sản phẩm tiêu chuẩn")), { draft = draft.copy(isFeatured = it) })
    }
}

/** Status badge colors used on the web inventory (iOS `WebInventoryStatusBadge`). */
@Composable
private fun InventoryStatusBadge(status: String) {
    val (fg, bg, label) = when (status.trim()) {
        "Đang mở bán", "open", "selling" -> Triple(Color(0xFF238451), Color(0xFFEAF8F1), "Đang mở bán")
        "Chưa mở bán", "unpublished", "" -> Triple(Color(0xFF475569), Color(0xFFF1F5F9), "Chưa mở bán")
        "Đang giữ chỗ", "holding" -> Triple(Color(0xFFEA580C), Color(0xFFFFF7ED), "Đang giữ chỗ")
        "Đã bán", "sold" -> Triple(Color(0xFF64748B), Color(0xFFF1F5F9), "Đã bán")
        "Ngừng bán", "paused" -> Triple(Color(0xFFDC2626), Color(0xFFFEF2F2), "Ngừng bán")
        else -> Triple(Color(0xFF475569), Color(0xFFF1F5F9), status)
    }
    StatusPill(tr(label), fg, bg, dot = true)
}

@Composable
private fun InventoryCard(item: JSONValue, onClick: () -> Unit) {
    val thumb = unitThumbnail(item).ifEmpty { PropertyFormatters.resolveImage(item) }
    val area = item["usableAreaM2"].double.takeIf { it > 0 } ?: item["size_m2"].double
    val price = unitPrice(item)
    FutaCard(Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder, onClick = onClick) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(12.dp))) {
                AdminRemoteImage(thumb, Modifier.fillMaxSize())
                if (isErpUnit(item)) {
                    Row(
                        Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(bottomEnd = 8.dp)).background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(9.dp))
                        Text("ERP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item["propertyCode"].string.ifEmpty { item["title"].string }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    InventoryStatusBadge(statusList(item).firstOrNull() ?: "Chưa mở bán")
                }
                val location = buildList {
                    firstNonEmpty(item["projectName"].string, item["zone"].string).takeIf { it.isNotEmpty() }?.let { add(it.translated("project")) }
                    item["block"].string.takeIf { it.isNotEmpty() }?.let { add(tr("Tòa {0}", it)) }
                    item["floor"].string.takeIf { it.isNotEmpty() }?.let { add(tr("Tầng {0}", it)) }
                }.joinToString(" · ")
                if (location.isNotEmpty()) Text(location, fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    SpecChip(Icons.Default.Bed, tr(formatUnitType(item["unitType"].string, item["bedrooms"].int)))
                    if (area > 0) SpecChip(Icons.Default.CropFree, SalesFormatters.area(area))
                    if (item["direction"].string.isNotEmpty()) SpecChip(Icons.Default.Explore, LocalizedDirection.name(item["direction"].string))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(SalesFormatters.currency(price), fontSize = 14.sp, fontWeight = FontWeight.Black, color = FutaColors.BrandOrange, maxLines = 1)
                    if (area > 0 && price > 0) Text("(${SalesFormatters.compactCurrency(price / area)}/m²)", fontSize = 10.5.sp, color = FutaColors.Slate, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    if (isHoldingUnit(item)) {
                        Row(
                            Modifier.clip(CircleShape).background(FutaColors.CreamBg).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(9.dp))
                            Text("Giữ chỗ", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SpecChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF1F5F9)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = FutaColors.Slate, modifier = Modifier.size(11.dp))
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy, maxLines = 1)
    }
}

/** Bulk open-sale / withdraw for the selected units (iOS `AdminBulkPublicationSheet`). */
@Composable
private fun BulkPublicationSheet(unitIds: List<String>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf("publish") }
    var reason by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    fun execute() {
        scope.launch {
            processing = true
            errorMessage = null
            try {
                val body = buildJsonObject {
                    put("action", action)
                    put("recordIds", JsonArray(unitIds.map { JsonPrimitive(it) }))
                    if (action == "revoke") put("reason", reason.trim())
                }.toString()
                APIClient.get().request("/apartments/bulk-publication", "POST", body)
                ToastCenter.show("Xử lý hàng loạt thành công")
                onDone()
            } catch (e: Exception) {
                errorMessage = e.message
                ToastCenter.show(tr("Lỗi: {0}", e.message), isError = true)
            } finally {
                processing = false
            }
        }
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = { if (!processing) onDismiss() },
        title = "Xử lý mở bán hàng loạt",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, enabled = !processing, onClick = onDismiss, modifier = Modifier.weight(1f))
                FutaButton(
                    text = if (processing) "Đang xử lý…" else "Thực hiện",
                    variant = if (action == "revoke") FutaButtonVariant.DANGER else FutaButtonVariant.PRIMARY,
                    enabled = !processing && (action == "publish" || reason.isNotBlank()),
                    onClick = { confirm = true },
                    modifier = Modifier.weight(1.5f)
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Đang chọn {0} sản phẩm để xử lý mở bán hoặc thu hồi hàng loạt.", unitIds.size), fontSize = 13.sp, color = FutaColors.Slate)
            FutaSegmentTabs(
                items = listOf("publish", "revoke"),
                selectedItem = action,
                onSelect = { action = it },
                titleFor = { tr(if (it == "publish") "Mở bán hàng loạt" else "Thu hồi mở bán") },
                modifier = Modifier.padding(horizontal = 0.dp)
            )
            if (action == "revoke") {
                FormTextField("Lý do thu hồi sản phẩm", reason, { reason = it }, "Nhập lý do thu hồi sản phẩm…", required = true, multiline = true)
            }
            FormErrorBanner(errorMessage)
        }
    }
    ConfirmDialog(
        visible = confirm,
        title = if (action == "publish") "Xác nhận mở bán hàng loạt?" else "Xác nhận thu hồi hàng loạt?",
        message = if (action == "publish") tr("Bạn đang chuẩn bị mở bán {0} sản phẩm đã chọn.", unitIds.size) else tr("Bạn đang chuẩn bị thu hồi {0} sản phẩm về trạng thái Chưa mở bán.", unitIds.size),
        confirmText = if (action == "publish") "Xác nhận mở bán" else "Xác nhận thu hồi",
        destructive = action == "revoke",
        onDismiss = { confirm = false },
        onConfirm = { execute() }
    )
}

// ============================================================================
// Detail
// ============================================================================

private fun youtubeId(url: String): String? {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
    if (uri.host?.contains("youtu.be") == true) return uri.pathSegments.firstOrNull()
    return uri.getQueryParameter("v")
}

/** iOS `isTownhouse`: landed product (townhouse/villa/shophouse/land) vs. high-rise unit. */
private fun isTownhouse(unit: JSONValue): Boolean {
    val projType = unit["projectType"].string.ifEmpty { unit["project"]["projectType"].string }.lowercase().trim()
    if (projType.isNotEmpty()) {
        if (listOf("căn hộ", "chung cư", "condotel", "officetel", "can-ho").any { projType.contains(it) }) return false
        if (listOf("nhà phố", "liền kề", "biệt thự", "shophouse", "đất nền", "nha-pho", "biet-thu", "dat-nen").any { projType.contains(it) }) return true
    }
    val propType = unit["propertyType"].string.lowercase().trim()
    val aptType = unit["unitType"].string.lowercase().trim()
    val prodType = unit["productType"].string.lowercase().trim()
    if (propType in listOf("can-ho-chung-cu", "chung-cu-mini", "can-ho-du-lich", "can-ho", "condotel", "officetel") ||
        propType.contains("chung cư") || propType.contains("căn hộ") ||
        listOf("studio", "stu", "1pn", "2pn", "3pn").any { aptType.contains(it) } ||
        prodType.contains("studio") || prodType.contains("căn hộ")
    ) return false
    val s = "$prodType $aptType $propType ${unit["code"].string}".lowercase()
    if (propType in listOf("biet-thu-lien-ke", "nha-mat-pho", "shophouse", "nha-rieng") ||
        listOf("liền kề", "nhà phố", "biệt thự", "shophouse", "nha-lien-ke", "nha-pho", "biet-thu").any { s.contains(it) }
    ) return true
    val floors = unit["floorAreas"].array.ifEmpty { unit["townhouseSpecs"]["floorAreas"].array }
    return floors.size > 1
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AdminInventoryDetailScreen(unitIdArg: String, onBack: () -> Unit, onEdit: (JSONValue) -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var unit by remember { mutableStateOf(JSONValue.EmptyObject) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var registrations by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var regFilter by remember { mutableStateOf("all") }
    var isFavorite by remember { mutableStateOf(false) }
    var updatingFavorite by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var showPublish by remember { mutableStateOf(false) }
    var showRevoke by remember { mutableStateOf(false) }
    var mediaTab by remember { mutableStateOf("photos") }
    var showFloorPlan by remember { mutableStateOf(false) }

    suspend fun fetch(silent: Boolean) {
        try {
            val data = APIClient.get().request("/apartments/${Uri.encode(unitIdArg)}", query = mapOf("context" to "admin"))["data"]
            unit = data
            isFavorite = data["isFavorite"].bool
            loadError = null
        } catch (e: Exception) {
            if (!silent) loadError = e.message ?: tr("Không thể tải sản phẩm")
        } finally {
            loading = false
        }
        registrations = runCatching {
            APIClient.get().request("/sales/registrations", query = mapOf("propertyId" to unitIdArg, "limit" to "50"))["data"].array
        }.getOrDefault(registrations)
    }

    // Initial load + silent refresh every 30s while the detail is open (iOS polls the same way).
    LaunchedEffect(unitIdArg) {
        fetch(silent = false)
        while (true) {
            delay(30_000)
            fetch(silent = true)
        }
    }

    val code = firstNonEmpty(unit["propertyCode"].string, unit["code"].string).ifEmpty { tr("Căn hộ") }
    val gallery = remember(unit) {
        (unit["images"].array.map { resolveImageEntry(it) } + listOf(unitThumbnail(unit))).filter { it.isNotEmpty() }.distinct()
    }
    val videoUrl = firstNonEmpty(unit["videoUrl"].string, unit["youtubeUrl"].string, unit["youtubeUrl2"].string)
    val tourUrl = firstNonEmpty(unit["virtualTourUrl"].string, unit["link360"].string, unit["embedUrl360"].string)
    val floorPlan = firstNonEmpty(unit["floorPlan"].string, unit["floorPlanUrl"].string)
    val videoThumb = unit["videoThumbnailUrl"].string.ifEmpty { youtubeId(videoUrl)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty() }
    val price = unitPrice(unit)
    val area = unitArea(unit)
    val wallArea = unit["wallAreaM2"].double.takeIf { it > 0 } ?: unit["wallArea"].double
    val erp = isErpUnit(unit)
    val statuses = statusList(unit)
    val published = statuses.contains("Đang mở bán")
    val canEdit = AppSession.shared.isAuthenticated && unit["access"]["canEdit"].bool
    val canRecall = AppSession.shared.isAuthenticated && unit["access"]["canRecall"].bool

    val unitRegs = registrations.filter { reg ->
        val rId = reg["propertyId"].string.ifEmpty { reg["property"]["id"].string }
        val rCode = reg["apartmentCode"].string.ifEmpty { reg.nestedProperty["propertyCode"].string }
        rId == unitIdArg || rId == unit["id"].string || (rCode.isNotEmpty() && (rCode == code || rCode == unit["code"].string))
    }
    val visibleRegs = if (regFilter == "all") unitRegs else unitRegs.filter { it["status"].string == regFilter }

    fun toggleFavorite() {
        if (updatingFavorite) return
        val next = !isFavorite
        isFavorite = next
        scope.launch {
            updatingFavorite = true
            try {
                APIClient.get().request("/apartments/${Uri.encode(unitIdArg)}", "PUT", buildJsonObject { put("isFavorite", next) }.toString())
                ToastCenter.show(if (next) "Đã thêm vào sản phẩm nổi bật" else "Đã bỏ khỏi sản phẩm nổi bật")
                onChanged()
            } catch (e: Exception) {
                isFavorite = !next
                ToastCenter.show("Không thể cập nhật sản phẩm nổi bật", isError = true)
            } finally {
                updatingFavorite = false
            }
        }
    }

    fun publication(action: String, reason: String?) {
        scope.launch {
            publishing = true
            try {
                val body = buildJsonObject {
                    put("action", action)
                    put("recordIds", JsonArray(listOf(JsonPrimitive(unit["id"].string.ifEmpty { unitIdArg }))))
                    if (action == "revoke") put("reason", reason?.ifBlank { null } ?: tr("Thu hồi theo quyết định quản trị viên"))
                }.toString()
                APIClient.get().request("/apartments/bulk-publication", "POST", body)
                ToastCenter.show(if (action == "publish") "Đã mở bán sản phẩm thành công" else "Đã thu hồi mở bán")
                fetch(silent = false)
                onChanged()
            } catch (e: Exception) {
                ToastCenter.show(tr(if (action == "publish") "Không thể mở bán: {0}" else "Lỗi thu hồi: {0}", e.message), isError = true)
            } finally {
                publishing = false
            }
        }
    }

    fun requestPublish() {
        when {
            gallery.isEmpty() -> ToastCenter.show("Căn hộ cần có ít nhất một hình ảnh trước khi mở bán", isError = true)
            (!unit["externalOpenForSale"].isNull && !unit["externalOpenForSale"].bool) || unit["externalRemovedAt"].string.isNotEmpty() ->
                ToastCenter.show("ERP hiện không cho phép mở bán sản phẩm này", isError = true)
            else -> showPublish = true
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = code, onBack = onBack, subtitle = firstNonEmpty(unit["projectName"].string, unit["zone"].string).translated("project").takeIf { it.isNotEmpty() }) {
                if (canEdit) FutaHeaderIconButton(icon = Icons.Default.Edit, contentDescription = tr("Sửa"), onClick = { onEdit(unit) }, tint = FutaColors.BrandGreen)
            }
        },
        bottomBar = {
            if (!loading && loadError == null && (canEdit || canRecall)) {
                FutaStickyActionBar {
                    if (canEdit) FutaButton(text = "Chỉnh sửa", icon = Icons.Default.Edit, variant = FutaButtonVariant.OUTLINE, onClick = { onEdit(unit) }, modifier = Modifier.weight(1f))
                    if (canRecall && published) {
                        FutaButton(text = "Thu hồi mở bán", icon = Icons.Default.Undo, variant = FutaButtonVariant.DANGER, enabled = !publishing, onClick = { showRevoke = true }, modifier = Modifier.weight(1.3f))
                    } else if (canRecall) {
                        FutaButton(text = if (publishing) "Đang xử lý…" else "Mở bán ngay", icon = Icons.Default.LocalFireDepartment, variant = FutaButtonVariant.SECONDARY, enabled = !publishing, onClick = { requestPublish() }, modifier = Modifier.weight(1.3f))
                    }
                }
            }
        }
    ) { padding ->
        when {
            loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(3) { FutaSkeletonBlock(Modifier.weight(1f), height = 36.dp, radius = 9.dp) } }
                FutaSkeletonBlock(height = 230.dp, radius = 16.dp)
                FutaSkeletonBlock(height = 160.dp, radius = 16.dp)
                FutaSkeletonBlock(height = 220.dp, radius = 16.dp)
            }
            loadError != null -> AdminErrorState(loadError!!, { loading = true; scope.launch { fetch(false) } }, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Media hero: photos / video / 360
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.TabBg).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(Triple("photos", "Ảnh", gallery.isNotEmpty()), Triple("video", "Video", videoUrl.isNotEmpty()), Triple("tour", "View 360°", tourUrl.isNotEmpty())).forEach { (key, title, available) ->
                            val sel = mediaTab == key
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) Color.White else Color.Transparent)
                                    .clickable(enabled = available) { mediaTab = key }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(title, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) FutaColors.Navy else if (available) FutaColors.Slate else FutaColors.Slate.copy(alpha = 0.45f))
                            }
                        }
                    }
                    val pager = rememberPagerState(pageCount = { gallery.size })
                    Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(16.dp))) {
                        when (mediaTab) {
                            "video" -> MediaPlaceholder(
                                dark = Color(0xFF102235), image = videoThumb, icon = Icons.Default.PlayCircle,
                                title = if (videoUrl.isNotEmpty()) "Xem Video Thực Tế" else "Sản phẩm chưa có video",
                                subtitle = if (videoUrl.isNotEmpty()) "Nhấn để mở trình phát video" else null,
                                onClick = if (videoUrl.isNotEmpty()) ({ openExternalUrl(context, videoUrl) }) else null
                            )
                            "tour" -> MediaPlaceholder(
                                dark = Color(0xFF0A192F), image = "", icon = Icons.Default.ViewInAr,
                                title = if (tourUrl.isNotEmpty()) "Khám Phá View 360°" else "Sản phẩm chưa có dữ liệu 360°",
                                subtitle = if (tourUrl.isNotEmpty()) "Nhấn để mở chuyến tham quan thực tế ảo" else null,
                                onClick = if (tourUrl.isNotEmpty()) ({ openExternalUrl(context, tourUrl) }) else null
                            )
                            else -> if (gallery.isEmpty()) {
                                Box(Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(34.dp))
                                        Text("Sản phẩm chưa có hình ảnh", fontSize = 12.5.sp, color = FutaColors.Slate)
                                    }
                                }
                            } else {
                                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page -> AdminRemoteImage(gallery[page], Modifier.fillMaxSize()) }
                                Text(
                                    "${pager.currentPage + 1} / ${gallery.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (videoUrl.isNotEmpty() || gallery.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (videoUrl.isNotEmpty()) {
                                Box(
                                    Modifier.size(width = 72.dp, height = 50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF102235))
                                        .border(if (mediaTab == "video") 2.5.dp else 1.dp, if (mediaTab == "video") FutaColors.BrandOrange else FutaColors.LightBlueBorder, RoundedCornerShape(8.dp))
                                        .clickable { mediaTab = "video" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (videoThumb.isNotEmpty()) AsyncImage(model = videoThumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            gallery.forEachIndexed { index, url ->
                                val active = mediaTab == "photos" && pager.currentPage == index
                                Box(
                                    Modifier.size(width = 72.dp, height = 50.dp).clip(RoundedCornerShape(8.dp))
                                        .border(if (active) 2.5.dp else 1.dp, if (active) FutaColors.BrandOrange else FutaColors.LightBlueBorder, RoundedCornerShape(8.dp))
                                        .clickable { mediaTab = "photos"; scope.launch { pager.scrollToPage(index) } }
                                ) { AdminRemoteImage(url, Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }

                // 2. Status, favourite, share, price
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CHI TIẾT SẢN PHẨM", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                            if (AppSession.shared.role == "admin") {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFavorite) FutaColors.MintBg else Color.White,
                                    border = BorderStroke(1.dp, if (isFavorite) FutaColors.BrandGreen.copy(alpha = 0.3f) else FutaColors.LightBlueBorder),
                                    modifier = Modifier.size(32.dp).clickable(enabled = !updatingFavorite) { toggleFavorite() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tr("Sản phẩm nổi bật"), tint = if (isFavorite) FutaColors.BrandGreen else FutaColors.Slate, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
                                modifier = Modifier.size(32.dp).clickable {
                                    val url = PropertyFormatters.shareUrl(unit, code)
                                    shareText(context, tr("Chia sẻ thông tin căn {0}", code), "$code - ${SalesFormatters.currency(price)}\n$url")
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Share, tr("Chia sẻ"), tint = FutaColors.Slate, modifier = Modifier.size(16.dp)) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Mã căn: {0}", code), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                            if (erp) ErpLockBadge()
                            InventoryStatusBadge(statuses.firstOrNull() ?: "Chưa mở bán")
                        }
                        val loc = buildList {
                            unit["block"].string.takeIf { it.isNotEmpty() }?.let { add(tr("Tòa: {0}", it)) }
                            unit["floor"].string.takeIf { it.isNotEmpty() }?.let { add(tr("Tầng: {0}", it)) }
                            unit["unitNumber"].string.takeIf { it.isNotEmpty() && it != code }?.let { add(tr("Căn: {0}", it)) }
                        }
                        if (loc.isNotEmpty()) Text(loc.joinToString("   "), fontSize = 12.5.sp, color = FutaColors.Slate)
                        HorizontalDivider(color = FutaColors.PanelDivider)
                        Text(unit["salePriceLabel"].string.ifEmpty { tr("Giá bán (bao gồm VAT và PBT)") }, fontSize = 12.sp, color = FutaColors.Slate)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(SalesFormatters.currency(price), fontSize = 22.sp, fontWeight = FontWeight.Black, color = FutaColors.BrandOrange)
                            if (area > 0 && price > 0) Text("(${SalesFormatters.compactCurrency(price / area)}/m²)", fontSize = 12.sp, color = FutaColors.Slate, modifier = Modifier.padding(bottom = 3.dp))
                        }
                        val holdId = unit["activeHoldingRegistrationId"].string
                        if (holdId.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.CreamBg).padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Shield, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Căn đang được giữ chỗ trực tuyến", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                                    Text(tr("Mã hồ sơ: {0}", if (holdId.length >= 8) "#" + holdId.take(8).uppercase() else holdId), fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                            }
                        }
                    }
                }

                // 3. Specifications
                val townhouse = isTownhouse(unit)
                DetailSection("THÔNG SỐ SẢN PHẨM", Icons.Default.GridView) {
                    val bathrooms = unit["bathrooms"].int
                    TwoColumnGrid(
                        listOf<@Composable (Modifier) -> Unit>(
                            if (townhouse) { m -> SpecTile("Diện tích đất", unit["landArea"].string.ifEmpty { if (area > 0) SalesFormatters.area(area) else "-" }, Icons.Default.Straighten, m) }
                            else { m -> SpecTile("Diện tích tim tường", SalesFormatters.area(wallArea.takeIf { it > 0 } ?: area).takeIf { (wallArea > 0 || area > 0) } ?: "-", Icons.Default.CropFree, m) },
                            if (townhouse) { m -> SpecTile("Tổng diện tích sàn", unit["totalFloorArea"].string.ifEmpty { if (wallArea > 0) SalesFormatters.area(wallArea) else "-" }, Icons.Default.Apartment, m) }
                            else { m -> SpecTile("Diện tích thông thủy", if (area > 0) SalesFormatters.area(area) else "-", Icons.Default.Straighten, m) },
                            { m -> SpecTile("Số phòng ngủ", unit["bedrooms"].int.takeIf { it > 0 }?.let { tr("{0} PN", it) } ?: unit["bedrooms"].string.ifEmpty { "-" }, Icons.Default.Bed, m) },
                            { m -> SpecTile("Số phòng tắm", if (bathrooms > 0) tr("{0} WC", bathrooms) else unit["bathrooms"].string.ifEmpty { "-" }, Icons.Default.Shower, m) },
                            { m -> SpecTile("Hướng cửa chính", unit["direction"].string.takeIf { it.isNotEmpty() }?.let { LocalizedDirection.name(it) } ?: "-", Icons.Default.DoorFront, m) },
                            { m -> SpecTile("Hướng ban công", unit["balconyDirection"].string.takeIf { it.isNotEmpty() }?.let { LocalizedDirection.name(it) } ?: "-", Icons.Default.WbSunny, m) },
                            { m -> SpecTile("Loại sản phẩm", tr(formatUnitType(unit["unitType"].string.ifEmpty { unit["productType"].string }, unit["bedrooms"].int)), Icons.Default.House, m) },
                            { m -> SpecTile("Hướng nhìn (View)", unit["view"].string.ifEmpty { "-" }, Icons.Default.Visibility, m) }
                        )
                    )
                }

                // 4. Townhouse floors
                if (townhouse) {
                    val floors = unit["floorAreas"].array.ifEmpty { unit["townhouseSpecs"]["floorAreas"].array }
                    DetailSection("DIỆN TÍCH SÀN THEO TẦNG", Icons.Default.Layers) {
                        InfoRow("Diện tích đất", unit["landArea"].string.ifEmpty { tr("Chưa cập nhật") })
                        InfoRow("Tổng diện tích sàn", unit["totalFloorArea"].string.ifEmpty { tr("Chưa cập nhật") })
                        InfoRow("Tòa", unit["block"].string.ifEmpty { tr("Chưa cập nhật") })
                        InfoRow("Tổng số tầng", unit["totalFloors"].int.takeIf { it > 0 }?.toString() ?: unit["totalFloors"].string.ifEmpty { "${floors.size}" })
                        if (floors.isNotEmpty()) {
                            HorizontalDivider(color = FutaColors.PanelDivider)
                            floors.forEachIndexed { idx, f ->
                                val name = f["floor"].int.takeIf { it > 0 }?.let { tr("Tầng {0}", it) } ?: f["floor"].string.ifEmpty { tr("Tầng {0}", idx + 1) }
                                val a = f["area_m2"].double.takeIf { it > 0 }?.let { SalesFormatters.area(it) } ?: f["area_m2"].string.takeIf { it.isNotEmpty() }?.let { "$it m²" } ?: "-"
                                InfoRow(name, a)
                            }
                        }
                    }
                }

                // 5. Price table
                DetailSection("BẢNG GIÁ CHI TIẾT", Icons.Default.Payments) {
                    val basis = unit["erpPriceTable"]["basis"]
                    val plans = unit["erpPriceTable"]["paymentPlans"].array
                    val breakdown = unit["pricingBreakdown"].array
                    if (breakdown.isNotEmpty()) {
                        breakdown.forEach { row -> InfoRow(row["label"].string.translated("pricing"), if (row["value"].double > 0) SalesFormatters.currency(row["value"].double) else row["value"].string) }
                    } else {
                        if (basis["listPrice"].double > 0) InfoRow("Giá niêm yết", SalesFormatters.currency(basis["listPrice"].double))
                        if (basis["salePriceExVat"].double > 0) InfoRow("Giá trước VAT", SalesFormatters.currency(basis["salePriceExVat"].double))
                        if (basis["vatAmount"].double > 0) InfoRow("Thuế VAT (10%)", SalesFormatters.currency(basis["vatAmount"].double))
                        if (basis["maintenanceFee"].double > 0) InfoRow("Kinh phí bảo trì (2%)", SalesFormatters.currency(basis["maintenanceFee"].double))
                        if (plans.isNotEmpty()) InfoRow("Phương thức thanh toán", tr("{0} phương thức ERP", plans.size))
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.CreamBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(unit["salePriceLabel"].string.ifEmpty { tr("Tổng giá bán công bố") }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                        Text(SalesFormatters.currency(price), fontSize = 15.sp, fontWeight = FontWeight.Black, color = FutaColors.BrandOrange)
                    }
                }

                // 5A. Payment schedule simulator
                PaymentSchedulePanel(property = unit, unitLabelOverride = code)

                // 6. Commission & promotion
                val commRate = unit["commissionRate"].string.ifEmpty { unit["ownerInfo"]["commission"].string }
                val commAmount = unit["commissionAmount"].string
                val promoTag = unit["campaignTag"].string.ifEmpty { unit["commissionTag"].string }
                val promoTitle = unit["promotionTitle"].string.ifEmpty { unit["campaignName"].string }
                if (commRate.isNotEmpty() || commAmount.isNotEmpty() || promoTag.isNotEmpty() || promoTitle.isNotEmpty()) {
                    DetailSection("HOA HỒNG & CHƯƠNG TRÌNH ƯU ĐÃI", Icons.Default.CardGiftcard, iconTint = FutaColors.BrandOrange) {
                        if (commRate.isNotEmpty() || commAmount.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(FutaColors.MintBg).padding(12.dp)) {
                                    Text("Tỷ lệ hoa hồng (%)", fontSize = 11.sp, color = FutaColors.Slate)
                                    Text(commRate.ifEmpty { "-" }, fontSize = 16.sp, fontWeight = FontWeight.Black, color = FutaColors.BrandGreen)
                                }
                                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(FutaColors.CreamBg).padding(12.dp)) {
                                    Text("Số tiền hoa hồng dự kiến", fontSize = 11.sp, color = FutaColors.Slate)
                                    Text(commAmount.ifEmpty { tr("Theo chính sách") }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                                }
                            }
                            if (unit["commissionNote"].string.isNotEmpty()) {
                                Text(tr("Lưu ý: {0}", unit["commissionNote"].string.translated("policy")), fontSize = 12.sp, color = FutaColors.Slate, fontStyle = FontStyle.Italic)
                            }
                        }
                        if (promoTag.isNotEmpty() || promoTitle.isNotEmpty()) {
                            val expiry = if (unit["campaignEndDay"].string.isNotEmpty()) "${unit["campaignEndDay"].string}/${unit["campaignEndMonth"].string}/${unit["campaignEndYear"].string}" else unit["campaignEndDate"].string
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FAFC)).border(1.dp, FutaColors.PeachBorder, RoundedCornerShape(12.dp)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (promoTag.isNotEmpty()) StatusPill(promoTag.translated("policy"), FutaColors.BrandOrange, solid = true)
                                if (promoTitle.isNotEmpty()) Text(promoTitle.translated("policy"), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                if (unit["promotionContent"].string.isNotEmpty()) Text(unit["promotionContent"].string.translated("policy"), fontSize = 12.5.sp, color = FutaColors.Body)
                                if (expiry.isNotEmpty()) Text(tr("Áp dụng đến: {0}", expiry), fontSize = 11.5.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }

                // 7. Project info
                DetailSection("THÔNG TIN DỰ ÁN", Icons.Default.Apartment) {
                    val pName = firstNonEmpty(unit["projectName"].string, unit["project"]["displayName"].string, unit["project"]["name"].string).ifEmpty { "Dự án FUTA Land" }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pName.translated("project"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                        val pCode = unit["project"]["code"].string
                        if (pCode.isNotEmpty()) Text(pCode, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF1F5F9)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    InfoRow("Khu vực / Phân khu", firstNonEmpty(unit["province"].string, unit["zone"].string, unit["projectName"].string).ifEmpty { tr("Chưa cập nhật") }.translated("project"))
                    InfoRow("Địa chỉ", firstNonEmpty(unit["address"].string, unit["project"]["address"].string).ifEmpty { tr("Chưa cập nhật") }.translated("project"))
                    InfoRow("Chủ đầu tư", firstNonEmpty(unit["developer"].string, unit["project"]["developer"].string).ifEmpty { "FUTA Land" }.translated("project"))
                    InfoRow("Hình thức sở hữu", firstNonEmpty(unit["ownershipType"].string, unit["legalStatus"].string).ifEmpty { tr("Lâu dài") }.translated("project"))
                }

                // 8. ERP specs
                if (erp) {
                    DetailSection("THÔNG SỐ KỸ THUẬT ERP", Icons.Default.Lock, iconTint = FutaColors.BrandOrange, trailing = { ErpLockBadge() }) {
                        InfoRow("Mã sản phẩm ERP", code)
                        firstNonEmpty(unit["zone"].string, unit["projectName"].string).takeIf { it.isNotEmpty() }?.let { InfoRow("Zone / Phân khu", it) }
                        InfoRow("Tòa nhà", unit["block"].string.ifEmpty { "-" })
                        InfoRow("Tầng", unit["floor"].string.ifEmpty { "-" })
                        InfoRow("Số căn", unit["unitNumber"].string.ifEmpty { "-" })
                        InfoRow("Loại hình BĐS", tr(formatPropertyType(firstNonEmpty(unit["unitType"].string, unit["propertyType"].string).ifEmpty { "Căn hộ" })))
                        InfoRow("Giá bán ERP", SalesFormatters.currency(unit["price"].double))
                        InfoRow("Mở bán ERP", tr(if (unit["externalOpenForSale"].bool) "Cho phép mở bán" else "Khóa mở bán"))
                        firstNonEmpty(unit["externalSyncedAt"].string, unit["updatedAt"].string).takeIf { it.isNotEmpty() }?.let { InfoRow("Đồng bộ cuối", SalesFormatters.dateTime(it)) }
                        unit["legalStatus"].string.takeIf { it.isNotEmpty() }?.let { InfoRow("Tình trạng pháp lý", it) }
                        val locked = unit["externalLockedFields"].array.map { it.string }.filter { it.isNotEmpty() }
                        if (locked.isNotEmpty()) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.CreamBg).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(tr("Trường dữ liệu khóa đồng bộ ERP ({0})", locked.size), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                    Text("Chỉ đọc", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                                }
                                Text("Dữ liệu được khóa chỉnh sửa trực tiếp trên app để đảm bảo tính toàn vẹn với FUTA Land ERP.", fontSize = 11.sp, color = FutaColors.Slate)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    locked.take(10).forEach { field -> StatusPill(tr(formatLockedField(field)), FutaColors.Slate, Color.White) }
                                    if (locked.size > 10) StatusPill(tr("+{0} trường khác", locked.size - 10), FutaColors.Slate, Color.White)
                                }
                            }
                        }
                    }
                }

                // 9. Floor plan
                if (floorPlan.isNotEmpty()) {
                    DetailSection("SƠ ĐỒ MẶT BẰNG", Icons.Default.Map, trailing = {
                        Text("Phóng to sơ đồ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.clickable { showFloorPlan = true })
                    }) {
                        Text(tr("CHI TIẾT CĂN HỘ - {0}", code), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).clickable { showFloorPlan = true }) {
                            AdminRemoteImage(floorPlan, Modifier.fillMaxSize(), ContentScale.Fit)
                        }
                    }
                }

                // 10. Description
                val desc = unit["description"].string.ifEmpty { unit["projectDescription"].string }
                if (desc.isNotEmpty()) {
                    DetailSection("MÔ TẢ SẢN PHẨM", Icons.Default.Notes) {
                        Text(desc.translated("property"), fontSize = 13.5.sp, color = FutaColors.Body, lineHeight = 20.sp)
                    }
                }

                // 11. Advisors registered for the unit
                var regMenu by remember { mutableStateOf(false) }
                val regFilters = listOf("all" to "Tất cả", "active" to "Đã duyệt bán", "pending" to "Chờ phê duyệt", "expired" to "Hết hạn", "rejected" to "Từ chối", "revoked" to "Thu hồi")
                DetailSection("DANH SÁCH TVV ĐĂNG KÝ", Icons.Default.Groups, trailing = {
                    Box {
                        Row(
                            Modifier.clip(CircleShape).background(FutaColors.MintBg).clickable { regMenu = true }.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.FilterList, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(13.dp))
                            Text(regFilters.first { it.first == regFilter }.second, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                        }
                        DropdownMenu(expanded = regMenu, onDismissRequest = { regMenu = false }, containerColor = Color.White) {
                            regFilters.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontSize = 13.sp, fontWeight = if (key == regFilter) FontWeight.Bold else FontWeight.Medium) },
                                    trailingIcon = if (key == regFilter) ({ Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp)) }) else null,
                                    onClick = { regFilter = key; regMenu = false }
                                )
                            }
                        }
                    }
                }) {
                    if (visibleRegs.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.PersonSearch, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(30.dp))
                            Text("Chưa có yêu cầu đăng ký", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            Text("Các tư vấn viên gửi yêu cầu tham gia bán sản phẩm này sẽ được hiển thị tại đây.", fontSize = 11.5.sp, color = FutaColors.Slate)
                        }
                    } else {
                        visibleRegs.forEach { reg ->
                            val advName = firstNonEmpty(reg["advisorName"].string, reg["advisor"]["name"].string).ifEmpty { tr("Tư vấn viên") }
                            val advPhone = firstNonEmpty(reg["advisorPhone"].string, reg["advisor"]["phone"].string)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FAFC)).padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(38.dp).clip(CircleShape).background(FutaColors.MintBg), contentAlignment = Alignment.Center) {
                                    VerbatimText(advName.take(1).uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                }
                                Column(Modifier.weight(1f)) {
                                    VerbatimText(advName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1)
                                    if (reg["customerName"].string.isNotEmpty()) Text(tr("Khách: {0}", reg["customerName"].string), fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 1)
                                }
                                if (advPhone.isNotEmpty()) {
                                    Surface(shape = CircleShape, color = FutaColors.MintBg, modifier = Modifier.size(32.dp).clickable { dialPhone(context, advPhone) }) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Phone, tr("Gọi"), tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp)) }
                                    }
                                }
                                val st = reg["status"].string
                                StatusPill(tr(RegistrationStatusHelper.title(st)), RegistrationStatusHelper.color(st))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    ConfirmDialog(
        visible = showPublish,
        title = "Xác nhận mở bán căn hộ?",
        message = "Sản phẩm sẽ được chuyển sang trạng thái Đang mở bán và hiển thị công khai cho khách hàng và chuyên viên.",
        confirmText = "Xác nhận mở bán",
        onDismiss = { showPublish = false },
        onConfirm = { publication("publish", null) }
    )
    ReasonDialog(
        visible = showRevoke,
        title = "Thu hồi mở bán sản phẩm?",
        message = "Sản phẩm sẽ chuyển về trạng thái Chưa mở bán. Nhập lý do thu hồi nếu cần.",
        placeholder = "Nhập lý do thu hồi…",
        confirmText = "Xác nhận thu hồi",
        onDismiss = { showRevoke = false },
        onConfirm = { publication("revoke", it) }
    )
    if (showFloorPlan && floorPlan.isNotEmpty()) {
        ZoomableImageDialog(url = floorPlan, title = tr("Sơ đồ mặt bằng - {0}", code), onDismiss = { showFloorPlan = false })
    }
}

@Composable
private fun MediaPlaceholder(dark: Color, image: String, icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().background(dark).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
        if (image.isNotEmpty()) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(46.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Full-screen pinch-to-zoom image viewer (floor plan, ID documents). */
@Composable
fun ZoomableImageDialog(url: String, title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = PropertyFormatters.resolveImageUrl(url),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(36.dp).clickable(onClick = onDismiss)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, tr("Đóng"), tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

// ============================================================================
// Edit form
// ============================================================================

private data class InventoryForm(
    val bedrooms: Int,
    val bathrooms: Int,
    val description: String,
    val commission: String,
    val virtualTourUrl: String,
    val images: List<JSONValue>
)

@Composable
private fun AdminInventoryEditScreen(unit: JSONValue, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val initial = remember(unit) {
        InventoryForm(
            bedrooms = unit["bedrooms"].int,
            bathrooms = unit["bathrooms"].int,
            description = unit["description"].string,
            commission = unit["ownerInfo"]["commission"].string,
            virtualTourUrl = unit["virtualTourUrl"].string,
            images = unit["images"].array
        )
    }
    var form by remember(unit) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var removeIndex by remember { mutableStateOf<Int?>(null) }
    val dirty = form != initial
    val erp = isErpUnit(unit)

    fun cancel() { if (dirty) showDiscard = true else onClose() }
    BackHandler { cancel() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                uploading = true
                try {
                    val url = uploadPickedImage(context, uri, "unit")
                    val entry = buildJsonObject {
                        put("original", url)
                        put("name", tr("Ảnh tải lên"))
                    }
                    form = form.copy(images = form.images + JSONValue(entry))
                    ToastCenter.show("Tải ảnh lên thành công")
                } catch (e: Exception) {
                    ToastCenter.show(tr("Lỗi tải ảnh: {0}", e.message), isError = true)
                } finally {
                    uploading = false
                }
            }
        }
    }

    fun save() {
        scope.launch {
            saving = true
            errorMessage = null
            try {
                // Keep each image's existing variants/metadata; only drop entries without a usable URL.
                val images = form.images.mapNotNull { img -> resolveImageEntry(img).takeIf { it.isNotEmpty() }?.let { img to it } }
                    .mapIndexed { index, (img, original) ->
                        buildJsonObject {
                            put("original", original)
                            img["name"].string.takeIf { it.isNotEmpty() }?.let { put("name", it) }
                            if (img["size"].int > 0) put("size", img["size"].int)
                            put("variants", if (img["variants"].isNull) JsonArray(emptyList()) else img["variants"].element)
                            put("sortOrder", index)
                        }
                    }
                // Merge the commission into the existing owner info so phones/zalo/notes are not wiped
                // (the schema defaults missing ownerInfo keys).
                val ownerInfo = buildJsonObject {
                    (unit["ownerInfo"].element as? JsonObject)?.forEach { (k, v) -> put(k, v) }
                    put("commission", form.commission.trim())
                }
                val body = buildJsonObject {
                    put("bedrooms", form.bedrooms)
                    put("bathrooms", form.bathrooms)
                    put("description", form.description)
                    put("ownerInfo", ownerInfo)
                    put("virtualTourUrl", form.virtualTourUrl.trim())
                    put("images", JsonArray(images))
                }.toString()
                APIClient.get().request("/apartments/${Uri.encode(unitId(unit))}", "PUT", body)
                ToastCenter.show("Cập nhật sản phẩm thành công")
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
        topBar = { SalesAdminTopBar(title = "Chỉnh sửa căn hộ", subtitle = unitCode(unit), onBack = { cancel() }) },
        bottomBar = { FormActionBar("Lưu thay đổi", saving, !uploading, { cancel() }, { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).clearFocusOnTap().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FormErrorBanner(errorMessage)
            if (erp) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FutaColors.CreamBg).border(1.dp, FutaColors.PeachBorder, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ErpLockBadge()
                        Text("Sản phẩm đồng bộ từ ERP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    }
                    Text(
                        "Các trường mã căn, phân khu, tòa nhà, tầng, số căn, diện tích, giá bán và pháp lý được quản lý trực tiếp bởi ERP và được khóa trên ứng dụng.",
                        fontSize = 12.sp, color = FutaColors.Slate, lineHeight = 17.sp
                    )
                }
            }
            FormSection("Thông số phòng & Hoa hồng") {
                FormStepper(tr("Số phòng ngủ: {0}", form.bedrooms), form.bedrooms, 0..10) { form = form.copy(bedrooms = it) }
                FormStepper(tr("Số phòng tắm (WC): {0}", form.bathrooms), form.bathrooms, 0..10) { form = form.copy(bathrooms = it) }
                FormTextField("Mức hoa hồng môi giới", form.commission, { form = form.copy(commission = it) }, "VD: 1.5% hoặc 30.000.000đ")
                FormTextField("URL Virtual Tour 360°", form.virtualTourUrl, { form = form.copy(virtualTourUrl = it) }, "https://kuula.co/post/… hoặc link 360", keyboardType = KeyboardType.Uri)
            }
            FormSection(tr("Hình ảnh sản phẩm ({0})", form.images.size)) {
                form.images.forEachIndexed { index, img ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(8.dp))) { AdminRemoteImage(resolveImageEntry(img), Modifier.fillMaxSize()) }
                        Text(img["name"].string.ifEmpty { tr("Ảnh #{0}", index + 1) }, fontSize = 13.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { removeIndex = index }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Delete, tr("Xóa ảnh"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                FutaButton(
                    text = if (uploading) "Đang tải ảnh lên…" else "Thêm ảnh sản phẩm",
                    icon = Icons.Default.AddPhotoAlternate, variant = FutaButtonVariant.MINT, enabled = !uploading, height = 40.dp,
                    onClick = { picker.launch("image/*") }
                )
            }
            FormSection("Mô tả chi tiết") {
                FormTextField("Mô tả sản phẩm", form.description, { form = form.copy(description = it) }, "Mô tả sản phẩm…", multiline = true)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = onClose)
    removeIndex?.let { index ->
        ConfirmDialog(
            visible = true,
            title = "Xóa ảnh này?",
            message = "Ảnh sẽ bị gỡ khỏi sản phẩm khi bạn lưu thay đổi.",
            confirmText = "Xóa ảnh",
            destructive = true,
            onDismiss = { removeIndex = null },
            onConfirm = { form = form.copy(images = form.images.filterIndexed { i, _ -> i != index }) }
        )
    }
}
