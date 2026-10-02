package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.APIError
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.*

// Mirrors iOS AdminCMSView.swift taxonomy editors: /location-options, /property-types, /zones,
// /apartment-labels (list GET, POST create, PUT /:value update, DELETE /:value).

internal enum class TaxonomyKind(
    val tabTitle: String,
    val icon: ImageVector,
    val path: String,
    val noun: String,
    val emptyTitle: String,
    val emptyMessage: String
) {
    LOCATION("Tỉnh thành", Icons.Default.Map, "/location-options", "khu vực", "Chưa có khu vực", "Nhấn nút thêm để bổ sung khu vực/tỉnh thành."),
    PROPERTY_TYPE("Loại BĐS", Icons.Default.Home, "/property-types", "loại BĐS", "Chưa có loại BĐS", "Bấm thêm để cấu hình loại bất động sản."),
    ZONE("Phân khu", Icons.Default.GridView, "/zones", "phân khu", "Chưa có phân khu", "Bấm thêm để tạo phân khu / Zone."),
    LABEL("Nhãn BĐS", Icons.Default.Sell, "/apartment-labels", "nhãn căn hộ", "Chưa có nhãn", "Bấm thêm để tạo nhãn căn hộ.");

    val createTitle: String get() = when (this) {
        LOCATION -> "Thêm khu vực"; PROPERTY_TYPE -> "Thêm loại BĐS"; ZONE -> "Thêm phân khu"; LABEL -> "Thêm nhãn căn hộ"
    }
    val editTitle: String get() = when (this) {
        LOCATION -> "Sửa khu vực"; PROPERTY_TYPE -> "Sửa loại BĐS"; ZONE -> "Sửa phân khu"; LABEL -> "Sửa nhãn căn hộ"
    }
}

private enum class TaxonomySort(val title: String) { LABEL_ASC("Tên: A → Z"), LABEL_DESC("Tên: Z → A"), VALUE("Mã: A → Z"), COUNT("Số tin: Nhiều nhất") }

internal fun parseHexColor(raw: String, fallback: Color = Color(0xFF16803C)): Color {
    val clean = raw.trim().removePrefix("#")
    if (clean.length != 6) return fallback
    return clean.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback
}

@Composable
internal fun AdminTaxonomiesScreen(version: Int, onBack: () -> Unit, onOpen: (TaxonomyKind, JSONValue?) -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var kind by remember { mutableStateOf(TaxonomyKind.LOCATION) }
    var entries by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember(kind) { mutableStateOf("") }
    var sort by remember(kind) { mutableStateOf(TaxonomySort.LABEL_ASC) }
    var page by remember(kind) { mutableIntStateOf(1) }
    // PROPERTY_TYPE: "new" flag; ZONE: has address.
    var filter by remember(kind) { mutableStateOf("all") }
    var draftFilter by remember { mutableStateOf("all") }
    var showFilter by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<JSONValue?>(null) }

    suspend fun fetch() {
        loading = true
        try {
            entries = APIClient.get().request(kind.path)["data"].array
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(kind, version) { entries = emptyList(); fetch() }

    val filterOptions: List<SelectOption> = when (kind) {
        TaxonomyKind.PROPERTY_TYPE -> listOf(SelectOption("all", "Tất cả"), SelectOption("new", "Đánh dấu mới"), SelectOption("regular", "Thông thường"))
        TaxonomyKind.ZONE -> listOf(SelectOption("all", "Tất cả"), SelectOption("address", "Có địa chỉ"), SelectOption("noAddress", "Chưa có địa chỉ"))
        else -> emptyList()
    }
    val sortOptions = if (kind == TaxonomyKind.PROPERTY_TYPE) TaxonomySort.entries else TaxonomySort.entries.filter { it != TaxonomySort.COUNT }
    val filtered = remember(entries, search, sort, filter) {
        val q = search.trim()
        entries.filter { item ->
            (q.isEmpty() || listOf("label", "value", "address").joinToString(" ") { item[it].string }.contains(q, ignoreCase = true)) &&
                when (filter) {
                    "new" -> item["isNew"].bool
                    "regular" -> !item["isNew"].bool
                    "address" -> item["address"].string.isNotBlank()
                    "noAddress" -> item["address"].string.isBlank()
                    else -> true
                }
        }.let { list ->
            when (sort) {
                TaxonomySort.LABEL_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["label"].string })
                TaxonomySort.LABEL_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it["label"].string })
                TaxonomySort.VALUE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["value"].string })
                TaxonomySort.COUNT -> list.sortedByDescending { it["count"].int }
            }
        }
    }
    val slice = filtered.pageSlice(page, 25)

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Danh mục hệ thống", subtitle = tr("{0} mục", entries.size), onBack = onBack) {
                SortMenuButton(sortOptions, sort, { it.title }, { sort = it; page = 1 }, sort == TaxonomySort.LABEL_ASC)
                if (filterOptions.isNotEmpty()) {
                    BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), if (filter != "all") 1 else 0) { draftFilter = filter; showFilter = true }
                }
                AddHeaderButton(tr(kind.createTitle)) { onOpen(kind, null) }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                QuickChipRow {
                    TaxonomyKind.entries.forEach { k -> QuickChip(k.tabTitle, k == kind, k.icon) { if (k != kind) kind = k } }
                }
            }
            item { AdminSearchField(search, { search = it; page = 1 }, "Tìm theo tên, mã…") }
            if (filter != "all") item {
                AppliedFilterChips(listOf(AppliedFilter(tr(filterOptions.firstOrNull { it.value == filter }?.label.orEmpty())) { filter = "all"; page = 1 })) { filter = "all"; page = 1 }
            }
            when {
                loading && entries.isEmpty() -> items(6) { FutaAdminRowSkeleton() }
                loadError != null && entries.isEmpty() -> item { AdminErrorState(loadError.orEmpty(), { scope.launch { fetch() } }) }
                slice.items.isEmpty() -> item {
                    AdminListEmpty(entries.isNotEmpty(), kind.emptyTitle, kind.emptyMessage, { search = ""; filter = "all"; page = 1 }, kind.icon)
                }
                else -> {
                    items(slice.items, key = { kind.name + it["value"].string }) { item ->
                        TaxonomyRow(kind, item, onClick = { onOpen(kind, item) }, onDelete = { deleting = item })
                    }
                    if (slice.totalPages > 1) item {
                        PaginationBar(slice.page, slice.totalPages, tr("{0}–{1} / {2}", slice.start, slice.end, slice.total),
                            { page = slice.page - 1; scope.launch { listState.scrollToItem(0) } },
                            { page = slice.page + 1; scope.launch { listState.scrollToItem(0) } })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    FilterSheet(
        visible = showFilter, title = "Bộ lọc", applyTitle = "Áp dụng",
        canReset = draftFilter != "all", onReset = { draftFilter = "all" },
        onApply = { filter = draftFilter; page = 1; showFilter = false }, onDismiss = { showFilter = false }
    ) {
        FilterChipGroup(if (kind == TaxonomyKind.ZONE) "Địa chỉ" else "Trạng thái", filterOptions, draftFilter) { draftFilter = it }
    }

    val target = deleting
    ConfirmDialog(
        visible = target != null,
        title = tr("Xóa {0}?", tr(kind.noun)),
        message = tr("\"{0}\" sẽ bị xóa và gỡ khỏi các tin đăng đang dùng mục này.", target?.get("label")?.string.orEmpty()),
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { deleting = null },
        onConfirm = {
            val t = target ?: return@ConfirmDialog
            deleting = null
            scope.launch {
                try {
                    APIClient.get().request("${kind.path}/${android.net.Uri.encode(t["value"].string)}", method = "DELETE")
                    ToastCenter.show(tr("Đã xóa thành công"))
                    fetch()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể xóa"), isError = true)
                }
            }
        }
    )
}

@Composable
private fun TaxonomyRow(kind: TaxonomyKind, item: JSONValue, onClick: () -> Unit, onDelete: () -> Unit) {
    AdminRowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (kind == TaxonomyKind.LABEL) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(parseHexColor(item["color"].string)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                VerbatimText(item["label"].string, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                val meta = buildString {
                    append(tr("Mã: {0}", item["value"].string))
                    if (kind == TaxonomyKind.ZONE && item["address"].string.isNotBlank()) append(" • " + item["address"].string)
                    if (kind == TaxonomyKind.PROPERTY_TYPE) append(" • " + tr("{0} tin", item["count"].int))
                }
                VerbatimText(meta, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 2)
            }
            if (kind == TaxonomyKind.PROPERTY_TYPE && item["isNew"].bool) StatusPill("Mới", FutaColors.BrandOrange)
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, tr("Xóa"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val labelColorPresets = listOf("#16803C", "#207446", "#2563EB", "#0EA5E9", "#F97316", "#EAB308", "#DC2626", "#DB2777", "#7C3AED", "#475569")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaxonomyFormScreen(kind: TaxonomyKind, item: JSONValue?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isEdit = item != null
    val initLabel = item?.get("label")?.string.orEmpty()
    val initValue = item?.get("value")?.string.orEmpty()
    val initAddress = item?.get("address")?.string.orEmpty()
    val initColor = item?.get("color")?.string?.ifEmpty { null } ?: "#16803C"
    val initNew = item?.get("isNew")?.bool ?: false
    var label by remember { mutableStateOf(initLabel) }
    var value by remember { mutableStateOf(initValue) }
    var address by remember { mutableStateOf(initAddress) }
    var color by remember { mutableStateOf(initColor) }
    var isNew by remember { mutableStateOf(initNew) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRenameConfirm by remember { mutableStateOf(false) }

    val dirty = label != initLabel || value != initValue || address != initAddress || color != initColor || isNew != initNew
    fun close() { if (dirty && !saving) showDiscard = true else onClose() }
    BackHandler { close() }

    fun submit() {
        val body = buildJsonObject {
            put("label", label.trim())
            if (value.isNotBlank()) put("value", value.trim())
            when (kind) {
                TaxonomyKind.LOCATION -> if (!isEdit) put("keywords", kotlinx.serialization.json.JsonArray(emptyList()))
                TaxonomyKind.PROPERTY_TYPE -> put("isNew", isNew)
                TaxonomyKind.ZONE -> put("address", address.trim())
                TaxonomyKind.LABEL -> put("color", color.trim().uppercase())
            }
        }.toString()
        scope.launch {
            saving = true
            try {
                val api = APIClient.get()
                if (item != null) {
                    val path = "${kind.path}/${android.net.Uri.encode(initValue)}"
                    try {
                        api.request(path, method = "PUT", bodyJson = body)
                    } catch (e: APIError) {
                        // Zones listed from projects may have no catalog row yet: create the override instead.
                        if (kind == TaxonomyKind.ZONE && e.statusCode == 404) {
                            api.request(kind.path, method = "POST", bodyJson = buildJsonObject {
                                put("label", label.trim()); put("value", initValue); put("address", address.trim())
                            }.toString())
                        } else throw e
                    }
                } else {
                    api.request(kind.path, method = "POST", bodyJson = body)
                }
                ToastCenter.show(if (isEdit) tr("Đã cập nhật thành công") else tr("Đã thêm thành công"))
                onSaved()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    fun save() {
        error = when {
            label.isBlank() -> tr("Vui lòng nhập tên hiển thị")
            value.isBlank() && kind != TaxonomyKind.PROPERTY_TYPE -> tr("Vui lòng nhập mã / giá trị")
            isEdit && value.isBlank() -> tr("Vui lòng nhập mã / giá trị")
            kind == TaxonomyKind.LABEL && !Regex("^#[0-9A-Fa-f]{6}$").matches(color.trim()) -> tr("Mã màu phải có dạng #RRGGBB")
            else -> null
        }
        if (error != null) return
        // Renaming the code rewrites every listing that uses it: confirm.
        if (isEdit && value.trim() != initValue) showRenameConfirm = true else submit()
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = if (isEdit) kind.editTitle else kind.createTitle, subtitle = if (isEdit) tr("Mã: {0}", initValue) else null, onBack = { close() }) {
                if (kind == TaxonomyKind.PROPERTY_TYPE && isEdit) StatusPill(tr("{0} tin", item!!["count"].int), Color(0xFF2563EB))
            }
        },
        bottomBar = { FormActionBar(if (isEdit) "Lưu thay đổi" else "Thêm mới", saving, enabled = !deleting && (dirty || !isEdit), onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            FormSection("Thông tin") {
                FormTextField("Tên hiển thị", label, { label = it }, placeholder = "Ví dụ: Hà Nội, Chung cư", required = true)
                FormTextField(
                    "Mã / Giá trị", value, { value = it },
                    placeholder = if (kind == TaxonomyKind.PROPERTY_TYPE && !isEdit) "Tự tạo theo tên nếu để trống" else "Ví dụ: ha-noi, apartment",
                    required = kind != TaxonomyKind.PROPERTY_TYPE || isEdit
                )
                if (isEdit) Text("Đổi mã sẽ cập nhật toàn bộ tin đăng đang dùng mã này.", fontSize = 11.5.sp, color = FutaColors.Slate)
                when (kind) {
                    TaxonomyKind.ZONE -> FormTextField("Địa chỉ phân khu", address, { address = it })
                    TaxonomyKind.PROPERTY_TYPE -> FormToggle("Đánh dấu loại BĐS mới", isNew, { isNew = it })
                    TaxonomyKind.LABEL -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(parseHexColor(color)).border(1.dp, FutaColors.LightBlueBorder, CircleShape))
                            Box(Modifier.weight(1f)) { FormTextField("Mã màu Hex (#RRGGBB)", color, { color = it.take(7) }) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            labelColorPresets.forEach { preset ->
                                val selected = preset.equals(color.trim(), ignoreCase = true)
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(parseHexColor(preset))
                                        .border(if (selected) 3.dp else 0.dp, if (selected) FutaColors.Navy else Color.Transparent, CircleShape)
                                        .clickable { color = preset }
                                )
                            }
                        }
                        Surface(shape = CircleShape, color = parseHexColor(color).copy(alpha = 0.12f), border = BorderStroke(1.dp, parseHexColor(color).copy(alpha = 0.4f))) {
                            VerbatimText(label.ifBlank { tr("Xem trước") }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = parseHexColor(color), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    TaxonomyKind.LOCATION -> Unit
                }
            }
            if (isEdit) {
                DangerZoneCard(
                    title = tr("Xóa {0}", tr(kind.noun)),
                    message = "Mục này sẽ bị xóa và gỡ khỏi các tin đăng đang dùng.",
                    buttonText = if (deleting) "Đang xóa…" else "Xóa",
                    enabled = !deleting && !saving,
                    onClick = { showDelete = true }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
    ConfirmDialog(
        visible = showRenameConfirm,
        title = "Đổi mã danh mục?",
        message = tr("Mã \"{0}\" sẽ đổi thành \"{1}\" trên toàn bộ tin đăng liên quan.", initValue, value.trim()),
        confirmText = "Đổi mã",
        onDismiss = { showRenameConfirm = false },
        onConfirm = { showRenameConfirm = false; submit() }
    )
    ConfirmDialog(
        visible = showDelete,
        title = tr("Xóa {0}?", tr(kind.noun)),
        message = tr("\"{0}\" sẽ bị xóa và gỡ khỏi các tin đăng đang dùng mục này.", initLabel),
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { showDelete = false },
        onConfirm = {
            showDelete = false
            scope.launch {
                deleting = true
                try {
                    APIClient.get().request("${kind.path}/${android.net.Uri.encode(initValue)}", method = "DELETE")
                    ToastCenter.show(tr("Đã xóa thành công"))
                    onSaved()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể xóa"), isError = true)
                } finally {
                    deleting = false
                }
            }
        }
    )
}
