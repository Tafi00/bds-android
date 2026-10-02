package vn.futaland.app.features.salesadmin

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.translated
import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

// Mirrors iOS Features/SalesAdmin/AdminProjectsView.swift:
// list (/projects/admin/list) → detail (/projects/:id) → create/edit (/projects/admin).

private sealed interface ProjectRoute {
    data class Detail(val id: String, val version: Int = 0) : ProjectRoute
    data object Create : ProjectRoute
    data class Edit(val project: JSONValue) : ProjectRoute
}

/** iOS `ProjectFilterState` (5 filters). */
private data class ProjectFilters(
    val status: String = "all",
    val visibility: String = "all",
    val projectType: String = "all",
    val location: String = "all",
    val showOnHome: String = "all"
) {
    val activeCount: Int
        get() = listOf(status, visibility, projectType, location, showOnHome).count { it != "all" }
}

/** iOS `ProjectSortOption` (6 options). */
private enum class ProjectSort(val title: String) {
    HOME_ORDER("Thứ tự trang chủ (mặc định)"),
    NAME_ASC("Tên dự án (A → Z)"),
    NAME_DESC("Tên dự án (Z → A)"),
    CODE_ASC("Mã dự án (A → Z)"),
    LAND_AREA_DESC("Quy mô diện tích (Lớn → Nhỏ)"),
    SELLING_FIRST("Đang mở bán ưu tiên")
}

private val projectStatusOptions = listOf(
    SelectOption("selling", "Đang mở bán"),
    SelectOption("upcoming", "Sắp mở bán"),
    SelectOption("sold_out", "Đã bán hết")
)

private fun projectStatusLabel(status: String): String = when (status) {
    "selling" -> "Đang mở bán"
    "upcoming" -> "Sắp mở bán"
    "sold_out", "sold" -> "Đã bán hết"
    else -> status.ifEmpty { "Khác" }
}

private fun projectStatusColor(status: String): Color = when (status) {
    "selling" -> FutaColors.BrandGreen
    "upcoming" -> FutaColors.BrandOrange
    "sold_out", "sold" -> Color(0xFF6B7280)
    else -> Color(0xFF2563EB)
}

private fun projectTitle(p: JSONValue): String = firstNonEmpty(p["displayName"].string, p["name"].string, p["code"].string)

private fun projectZone(p: JSONValue): String = firstNonEmpty(p["zone"].string, p["name"].string, p["code"].string)

private fun projectLocation(p: JSONValue): String = firstNonEmpty(p["address"].string, p["location"].string, p["province"].string)

private fun landAreaNumber(raw: String): Double =
    raw.replace(",", ".").filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0

private fun landAreaLabel(raw: String): String = when {
    raw.isBlank() -> ""
    raw.contains("m") || raw.contains("ha") -> raw
    else -> "$raw m²"
}

private fun matchesProject(p: JSONValue, f: ProjectFilters, search: String): Boolean {
    val q = search.trim()
    if (q.isNotEmpty()) {
        val text = listOf("displayName", "name", "code", "zone", "location", "province", "developer").joinToString(" ") { p[it].string }
        if (!text.contains(q, ignoreCase = true)) return false
    }
    if (f.status != "all" && p["status"].string != f.status) return false
    if (f.visibility == "visible" && p["hidden"].bool) return false
    if (f.visibility == "hidden" && !p["hidden"].bool) return false
    if (f.projectType != "all" && p["projectType"].string.ifEmpty { "Căn hộ" } != f.projectType) return false
    if (f.location != "all" && p["province"].string.ifEmpty { p["location"].string } != f.location) return false
    if (f.showOnHome == "yes" && !p["showOnHome"].bool) return false
    if (f.showOnHome == "no" && p["showOnHome"].bool) return false
    return true
}

private fun sortProjects(list: List<JSONValue>, sort: ProjectSort): List<JSONValue> {
    val nameOf = { p: JSONValue -> p["displayName"].string.ifEmpty { p["code"].string } }
    val homeOrder = { p: JSONValue -> p["homeOrder"].int.takeIf { it > 0 } ?: 9999 }
    return when (sort) {
        ProjectSort.HOME_ORDER -> list.sortedWith(compareBy<JSONValue> { homeOrder(it) }.thenBy(String.CASE_INSENSITIVE_ORDER) { nameOf(it) })
        ProjectSort.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { nameOf(it) })
        ProjectSort.NAME_DESC -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { nameOf(it) })
        ProjectSort.CODE_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["code"].string })
        ProjectSort.LAND_AREA_DESC -> list.sortedByDescending { landAreaNumber(it["landArea"].string) }
        ProjectSort.SELLING_FIRST -> list.sortedBy {
            when (it["status"].string) { "selling" -> 0; "upcoming" -> 1; else -> 2 }
        }
    }
}

@Composable
fun AdminProjectsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<ProjectRoute>() }

    // Listing state lives in the host so it survives opening detail/create/edit.
    var projects by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(ProjectFilters()) }
    var sort by remember { mutableStateOf(ProjectSort.HOME_ORDER) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val listState = rememberLazyListState()

    fun load() {
        scope.launch {
            if (projects.isEmpty()) loading = true
            try {
                projects = APIClient.get().request("/projects/admin/list")["data"].array
                loadError = null
            } catch (e: Exception) {
                loadError = e.message ?: tr("Không thể tải danh sách dự án")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenStackHost(
        stack = stack,
        base = {
            ProjectListContent(
                projects = projects,
                loading = loading,
                loadError = loadError,
                search = search,
                onSearch = { search = it; page = 1 },
                filters = filters,
                onFilters = { filters = it; page = 1 },
                sort = sort,
                onSort = { sort = it; page = 1 },
                page = page,
                onPage = { page = it; scope.launch { listState.scrollToItem(0) } },
                listState = listState,
                onBack = onBack,
                onRetry = { load() },
                onReload = { load() },
                onOpen = { stack.push(ProjectRoute.Detail(it)) },
                onCreate = { stack.push(ProjectRoute.Create) }
            )
        }
    ) { route ->
        when (route) {
            is ProjectRoute.Detail -> AdminProjectDetailScreen(
                projectId = route.id,
                onBack = { stack.pop() },
                onEdit = { stack.push(ProjectRoute.Edit(it)) },
                onChanged = { load() },
                onDeleted = { stack.pop(); load() }
            )
            ProjectRoute.Create -> AdminProjectFormScreen(
                project = null,
                onClose = { stack.pop() },
                onSaved = { stack.pop(); load() }
            )
            is ProjectRoute.Edit -> AdminProjectFormScreen(
                project = route.project,
                onClose = { stack.pop() },
                onSaved = {
                    stack.pop()
                    // Re-open the detail so it shows the saved values.
                    val detail = stack.entries.lastOrNull()
                    if (detail is ProjectRoute.Detail) stack.replaceTop(detail.copy(version = detail.version + 1))
                    load()
                }
            )
        }
    }
}

// ============================================================================
// Listing
// ============================================================================

@Composable
private fun ProjectListContent(
    projects: List<JSONValue>,
    loading: Boolean,
    loadError: String?,
    search: String,
    onSearch: (String) -> Unit,
    filters: ProjectFilters,
    onFilters: (ProjectFilters) -> Unit,
    sort: ProjectSort,
    onSort: (ProjectSort) -> Unit,
    page: Int,
    onPage: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReload: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showFilter by remember { mutableStateOf(false) }
    var pendingHide by remember { mutableStateOf<JSONValue?>(null) }
    val canCreate = vn.futaland.app.core.auth.AppSession.shared.let { it.hasPermission("projects:create") || it.hasPermission("projects:edit") }

    val filtered = remember(projects, filters, search, sort) {
        sortProjects(projects.filter { matchesProject(it, filters, search) }, sort)
    }
    val slice = filtered.pageSlice(page, 20)
    val distinctTypes = remember(projects) { projects.map { it["projectType"].string }.filter { it.isNotEmpty() }.distinct().sorted() }
    val distinctLocations = remember(projects) {
        projects.map { it["province"].string.ifEmpty { it["location"].string } }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    fun toggleHide(project: JSONValue) {
        scope.launch {
            val newHidden = !project["hidden"].bool
            try {
                APIClient.get().request(
                    "/projects/admin/${Uri.encode(project.id)}", "PUT",
                    buildJsonObject { put("hidden", newHidden) }.toString()
                )
                ToastCenter.show(tr(if (newHidden) "Đã ẩn dự án “{0}”" else "Đã hiển thị dự án “{0}”", projectTitle(project)))
                onReload()
            } catch (e: Exception) {
                ToastCenter.show(tr("Lỗi cập nhật trạng thái: {0}", e.message), isError = true)
            }
        }
    }

    fun moveOrder(project: JSONValue, up: Boolean) {
        val current = project["homeOrder"].int.takeIf { it > 0 } ?: 1
        val next = if (up) maxOf(1, current - 1) else current + 1
        if (next == current) return
        scope.launch {
            try {
                APIClient.get().request(
                    "/projects/admin/${Uri.encode(project.id)}", "PUT",
                    buildJsonObject { put("homeOrder", next) }.toString()
                )
                ToastCenter.show(tr("Đã đổi thứ tự dự án thành #{0}", next))
                onReload()
            } catch (e: Exception) {
                ToastCenter.show(tr("Lỗi đổi thứ tự: {0}", e.message), isError = true)
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Quản lý dự án", onBack = onBack) {
                SortMenuButton(ProjectSort.entries, sort, { it.title }, onSort, isDefault = sort == ProjectSort.HOME_ORDER)
                BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), filters.activeCount) { showFilter = true }
                if (canCreate) AddHeaderButton(tr("Thêm dự án"), onCreate)
            }
        }
    ) { padding ->
        when {
            loading && projects.isEmpty() -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp)
                        FutaSkeletonBlock(Modifier.weight(1f), height = 96.dp, radius = 14.dp)
                    }
                }
                repeat(2) { FutaSkeletonBlock(height = 260.dp, radius = 16.dp) }
            }
            loadError != null && projects.isEmpty() -> AdminErrorState(loadError, onRetry, Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    AdminSearchField(search, onSearch, "Tìm theo tên, mã, phân khu, vị trí…")
                }
                item {
                    val visible = projects.count { !it["hidden"].bool }
                    MetricGrid(
                        listOf(
                            { m -> SalesMetricCard("Tổng dự án", "${projects.size}", Icons.Default.Apartment, FutaColors.BrandGreen, m, subtitle = tr("{0} hiển thị", visible)) },
                            { m -> SalesMetricCard("Đang mở bán", "${projects.count { it["status"].string == "selling" }}", Icons.Default.LocalFireDepartment, Color(0xFF2563EB), m, selected = filters.status == "selling", onClick = { onFilters(filters.copy(status = if (filters.status == "selling") "all" else "selling")) }) },
                            { m -> SalesMetricCard("Sắp mở bán", "${projects.count { it["status"].string == "upcoming" }}", Icons.Default.Schedule, FutaColors.BrandOrange, m, selected = filters.status == "upcoming", onClick = { onFilters(filters.copy(status = if (filters.status == "upcoming") "all" else "upcoming")) }) },
                            { m -> SalesMetricCard("Đã bàn giao", "${projects.count { it["status"].string == "sold_out" }}", Icons.Default.Verified, Color(0xFF6B7280), m, selected = filters.status == "sold_out", onClick = { onFilters(filters.copy(status = if (filters.status == "sold_out") "all" else "sold_out")) }) }
                        )
                    )
                }
                val applied = buildList {
                    if (filters.status != "all") add(AppliedFilter(tr(projectStatusLabel(filters.status))) { onFilters(filters.copy(status = "all")) })
                    if (filters.visibility != "all") add(AppliedFilter(tr(if (filters.visibility == "visible") "Đang hiển thị" else "Đã ẩn")) { onFilters(filters.copy(visibility = "all")) })
                    if (filters.projectType != "all") add(AppliedFilter(filters.projectType.translated("project")) { onFilters(filters.copy(projectType = "all")) })
                    if (filters.location != "all") add(AppliedFilter(filters.location.translated("project")) { onFilters(filters.copy(location = "all")) })
                    if (filters.showOnHome != "all") add(AppliedFilter(tr(if (filters.showOnHome == "yes") "Trong bộ lọc tìm kiếm" else "Ẩn khỏi bộ lọc tìm kiếm")) { onFilters(filters.copy(showOnHome = "all")) })
                }
                if (applied.isNotEmpty() || search.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Tìm thấy {0} dự án", filtered.size), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                            AppliedFilterChips(applied) { onFilters(ProjectFilters()); onSearch("") }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        if (projects.isEmpty()) {
                            FutaEmptyState(
                                title = "Chưa có dự án",
                                message = "Hệ thống chưa có dự án nào. Nhấn + để thêm dự án đầu tiên.",
                                icon = Icons.Default.Apartment
                            )
                        } else {
                            FutaEmptyState(
                                title = "Không tìm thấy dự án",
                                message = "Không có dự án nào khớp với điều kiện tìm kiếm hoặc bộ lọc hiện tại.",
                                icon = Icons.Default.SearchOff,
                                actionButton = { FutaButton(text = "Đặt lại bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = { onFilters(ProjectFilters()); onSearch("") }) }
                            )
                        }
                    }
                } else {
                    items(slice.items, key = { it.id.ifEmpty { projectTitle(it) } }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onOpen(project.id) },
                            onToggleHide = { pendingHide = project },
                            onMove = { up -> moveOrder(project, up) }
                        )
                    }
                    item {
                        PaginationBar(
                            currentPage = slice.page,
                            totalPages = slice.totalPages,
                            rangeText = tr("Hiển thị {0}–{1} / {2} dự án", slice.start, slice.end, slice.total),
                            onPrevious = { onPage(slice.page - 1) },
                            onNext = { onPage(slice.page + 1) }
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    pendingHide?.let { project ->
        val hidden = project["hidden"].bool
        ConfirmDialog(
            visible = true,
            title = if (hidden) "Hiển thị lại dự án?" else "Ẩn dự án này?",
            message = tr(
                if (hidden) "Dự án “{0}” sẽ được hiển thị công khai trên website và ứng dụng." else "Dự án “{0}” sẽ bị ẩn khỏi trang chủ và danh sách công khai.",
                projectTitle(project)
            ),
            confirmText = if (hidden) "Hiển thị dự án" else "Ẩn dự án",
            destructive = !hidden,
            onDismiss = { pendingHide = null },
            onConfirm = { toggleHide(project) }
        )
    }

    if (showFilter) {
        ProjectFilterSheet(
            initial = filters,
            projects = projects,
            search = search,
            types = distinctTypes,
            locations = distinctLocations,
            onApply = { onFilters(it); showFilter = false },
            onDismiss = { showFilter = false }
        )
    }
}

@Composable
private fun ProjectFilterSheet(
    initial: ProjectFilters,
    projects: List<JSONValue>,
    search: String,
    types: List<String>,
    locations: List<String>,
    onApply: (ProjectFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    val matching = projects.count { matchesProject(it, draft, search) }
    FilterSheet(
        visible = true,
        title = "Bộ lọc dự án",
        applyTitle = tr("Áp dụng ({0} dự án)", matching),
        canReset = draft != ProjectFilters(),
        onReset = { draft = ProjectFilters() },
        onApply = { onApply(draft) },
        onDismiss = onDismiss
    ) {
        AdminSelectField(
            "Trạng thái", draft.status,
            listOf(SelectOption("all", "Tất cả trạng thái"), SelectOption("selling", "Đang mở bán"), SelectOption("upcoming", "Sắp mở bán"), SelectOption("sold_out", "Đã bàn giao")),
            { draft = draft.copy(status = it) }
        )
        AdminSelectField(
            "Hiển thị", draft.visibility,
            listOf(SelectOption("all", "Tất cả"), SelectOption("visible", "Có - Hiển thị tại trang /projects"), SelectOption("hidden", "Không - Ẩn khỏi trang dự án")),
            { draft = draft.copy(visibility = it) }
        )
        AdminSelectField(
            "Loại dự án", draft.projectType,
            listOf(SelectOption("all", "Tất cả loại dự án")) + types.map { SelectOption(it, it.translated("project")) },
            { draft = draft.copy(projectType = it) }
        )
        AdminSelectField(
            "Khu vực / Tỉnh thành", draft.location,
            listOf(SelectOption("all", "Tất cả khu vực")) + locations.map { SelectOption(it, it.translated("project")) },
            { draft = draft.copy(location = it) }
        )
        AdminSelectField(
            "Bộ lọc tìm kiếm sản phẩm", draft.showOnHome,
            listOf(SelectOption("all", "Tất cả"), SelectOption("yes", "Có - Cho phép chọn trong bộ lọc"), SelectOption("no", "Không - Ẩn khỏi bộ lọc tìm kiếm")),
            { draft = draft.copy(showOnHome = it) }
        )
    }
}

@Composable
private fun ProjectCard(project: JSONValue, onClick: () -> Unit, onToggleHide: () -> Unit, onMove: (Boolean) -> Unit) {
    val title = projectTitle(project)
    val banner = firstNonEmpty(project["bannerImageMobile"].string, project["bannerImage"].string, project["image"].string)
    val status = project["status"].string
    var menu by remember { mutableStateOf(false) }
    val homeOrder = project["homeOrder"].int.takeIf { it > 0 } ?: 1

    FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder, onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().height(160.dp)) {
                if (banner.isNotEmpty()) {
                    AdminRemoteImage(banner, Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Apartment, null, tint = FutaColors.Slate, modifier = Modifier.size(40.dp))
                            Text(project["code"].string, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (project["showOnHome"].bool) StatusPill(tr("Trang chủ #{0}", homeOrder), FutaColors.Navy, Color.White.copy(alpha = 0.94f))
                    if (project["hidden"].bool) StatusPill(tr("Đã ẩn"), FutaColors.BrandOrange, Color(0xFFFFF7ED), dot = true)
                    StatusPill(tr(when (status) { "selling" -> "Đang mở bán"; "upcoming" -> "Sắp mở bán"; "sold_out" -> "Đã bàn giao"; else -> status.ifEmpty { "Khác" } }), projectStatusColor(status), solid = true)
                }
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(34.dp).clickable { menu = true }) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreVert, tr("Thao tác"), tint = FutaColors.Navy, modifier = Modifier.size(18.dp)) }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem(
                            text = { Text(if (project["hidden"].bool) "Hiển thị lại dự án" else "Ẩn dự án", fontSize = 13.sp) },
                            leadingIcon = { Icon(if (project["hidden"].bool) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menu = false; onToggleHide() }
                        )
                        if (project["showOnHome"].bool) {
                            DropdownMenuItem(
                                text = { Text(tr("Đưa lên trên (#{0})", maxOf(1, homeOrder - 1)), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp)) },
                                enabled = homeOrder > 1,
                                onClick = { menu = false; onMove(true) }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Đưa xuống dưới (#{0})", homeOrder + 1), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp)) },
                                onClick = { menu = false; onMove(false) }
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title.translated("project"), fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                    if (project["code"].string.isNotEmpty()) {
                        Text(
                            project["code"].string, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF1F5F9)).padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Place, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                    Text(projectLocation(project).ifEmpty { tr("Chưa cập nhật địa chỉ") }.translated("project"), fontSize = 13.sp, color = FutaColors.Slate)
                }
                if (project["zone"].string.isNotEmpty() && project["zone"].string != title) {
                    Text(tr("Phân khu: {0}", project["zone"].string.translated("project")), fontSize = 12.sp, color = FutaColors.Slate)
                }
                if (project["landArea"].string.isNotEmpty() || project["projectType"].string.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAFC)).padding(12.dp)) {
                        if (project["projectType"].string.isNotEmpty()) {
                            Column(Modifier.weight(1f)) {
                                Text("Loại hình", fontSize = 11.sp, color = FutaColors.Slate)
                                Text(project["projectType"].string.translated("project"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                            }
                        }
                        if (project["landArea"].string.isNotEmpty()) {
                            Column(Modifier.weight(1f)) {
                                Text("Quy mô", fontSize = 11.sp, color = FutaColors.Slate)
                                Text(landAreaLabel(project["landArea"].string), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
                            }
                        }
                    }
                }
                HorizontalDivider(color = FutaColors.PanelDivider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (project["developer"].string.isNotEmpty()) {
                            Text("Chủ đầu tư", fontSize = 11.sp, color = FutaColors.Slate)
                            Text(project["developer"].string.translated("project"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy)
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(FutaColors.BrandGreen.copy(alpha = 0.08f)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Chi tiết", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                        Icon(Icons.Default.ChevronRight, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ============================================================================
// Detail
// ============================================================================

@Composable
private fun AdminProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onEdit: (JSONValue) -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var project by remember { mutableStateOf(JSONValue.EmptyObject) }
    var units by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showToggleHide by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val session = vn.futaland.app.core.auth.AppSession.shared
    val canEdit = session.hasPermission("projects:edit")
    val canDelete = session.hasPermission("projects:delete")

    fun load() {
        scope.launch {
            try {
                val data = APIClient.get().request("/projects/${Uri.encode(projectId)}")["data"]
                project = data
                loadError = null
                val zone = projectZone(data)
                if (zone.isNotEmpty()) {
                    units = runCatching {
                        APIClient.get().request(
                            "/apartments",
                            query = mapOf("zone" to zone, "limit" to "50", "context" to "admin", "includeUnpublished" to "true")
                        )["data"].array
                    }.getOrDefault(emptyList())
                }
            } catch (e: Exception) {
                loadError = e.message ?: tr("Không thể tải dự án")
            } finally {
                loading = false
            }
        }
    }

    fun toggleHide() {
        scope.launch {
            busy = true
            val newHidden = !project["hidden"].bool
            try {
                APIClient.get().request("/projects/admin/${Uri.encode(project.id.ifEmpty { projectId })}", "PUT", buildJsonObject { put("hidden", newHidden) }.toString())
                ToastCenter.show(if (newHidden) "Đã ẩn dự án thành công" else "Đã hiển thị dự án thành công")
                load()
                onChanged()
            } catch (e: Exception) {
                ToastCenter.show(tr("Lỗi cập nhật trạng thái: {0}", e.message), isError = true)
            } finally {
                busy = false
            }
        }
    }

    fun delete() {
        scope.launch {
            busy = true
            try {
                APIClient.get().request("/projects/admin/${Uri.encode(project.id.ifEmpty { projectId })}", "DELETE")
                ToastCenter.show("Đã xóa dự án thành công")
                onDeleted()
            } catch (e: Exception) {
                ToastCenter.show(tr("Không thể xóa: {0}", e.message), isError = true)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(projectId) { load() }

    val hidden = project["hidden"].bool
    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(
                title = projectTitle(project).ifEmpty { tr("Dự án") }.translated("project"),
                subtitle = project["code"].string.takeIf { it.isNotEmpty() },
                onBack = onBack
            )
        },
        bottomBar = {
            if (!loading && loadError == null && (canEdit || canDelete)) {
                FutaStickyActionBar {
                    if (canEdit) {
                        FutaButton(text = "Chỉnh sửa dự án", icon = Icons.Default.Edit, onClick = { onEdit(project) }, modifier = Modifier.weight(1.4f), enabled = !busy)
                        FutaButton(
                            text = if (hidden) "Hiển thị" else "Ẩn",
                            icon = if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            variant = FutaButtonVariant.OUTLINE,
                            enabled = !busy,
                            onClick = { showToggleHide = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (canDelete) {
                        // Destructive action kept apart from the primary ones.
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDC2626).copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.2f)),
                            modifier = Modifier.size(44.dp).clickable(enabled = !busy) { showDelete = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, tr("Xóa dự án"), tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FutaSkeletonBlock(height = 210.dp, radius = 18.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { FutaSkeletonBlock(Modifier.weight(1f), height = 58.dp, radius = 14.dp) } }
                FutaSkeletonBlock(height = 320.dp, radius = 16.dp)
                FutaSkeletonLines(3)
            }
            loadError != null -> AdminErrorState(loadError!!, { loading = true; load() }, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProjectHero(project)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("Quy mô khu đất", landAreaLabel(project["landArea"].string).ifEmpty { "—" }, Icons.Default.BarChart, FutaColors.BrandGreen, Modifier.weight(1f).fillMaxHeight())
                    MiniStat("Loại hình dự án", project["projectType"].string.ifEmpty { "Căn hộ" }.translated("project"), Icons.Default.House, FutaColors.Navy, Modifier.weight(1f).fillMaxHeight())
                    MiniStat("Sản phẩm", tr("{0} căn", units.size), Icons.Default.Apartment, FutaColors.BrandOrange, Modifier.weight(1f).fillMaxHeight())
                }
                DetailSection("Thông tin & Quy mô dự án", Icons.Default.Info) {
                    TwoColumnGrid(
                        listOf(
                            { m -> SpecTile("Chủ đầu tư", project["developer"].string.translated("project"), Icons.Default.AccountBalance, m) },
                            { m -> SpecTile("Địa điểm", project["location"].string.translated("project"), Icons.Default.Place, m) },
                            { m -> SpecTile("Tỉnh / Thành phố", project["province"].string.translated("project"), Icons.Default.Map, m) },
                            { m -> SpecTile("Loại dự án", project["projectType"].string.translated("project"), Icons.Default.House, m) },
                            { m -> SpecTile("Hình thức giao dịch", project["transactionType"].string.translated("project"), Icons.Default.SwapHoriz, m) },
                            { m -> SpecTile("Hình thức sở hữu", project["ownershipType"].string.translated("project"), Icons.Default.Gavel, m) },
                            { m -> SpecTile("Diện tích khu đất", landAreaLabel(project["landArea"].string), Icons.Default.Straighten, m) },
                            { m -> SpecTile("Tối đa TVV / Căn", tr("{0} TVV", project["maxAdvisorsPerProduct"].int), Icons.Default.Groups, m) },
                            { m -> SpecTile("Thời hạn giữ quyền", tr("{0} ngày", project["salesDurationDays"].int), Icons.Default.EventAvailable, m) },
                            { m -> SpecTile("Hiển thị trang chủ", if (project["showOnHome"].bool) tr("Có (#{0})", project["homeOrder"].int) else tr("Không"), Icons.Default.Star, m) },
                            { m -> SpecTile("Trạng thái mở bán", tr(projectStatusLabel(project["status"].string)), Icons.Default.Sell, m) },
                            { m -> SpecTile("Chế độ hiển thị", tr(if (hidden) "Đang ẩn khỏi website" else "Công khai"), Icons.Default.Visibility, m) }
                        )
                    )
                }
                ProjectDepositCard(project)
                if (project["description"].string.isNotEmpty() || project["amenities"].array.isNotEmpty()) {
                    DetailSection("Giới thiệu & Tiện ích dự án", Icons.Default.Notes) {
                        if (project["description"].string.isNotEmpty()) {
                            Text(project["description"].string.translated("project"), fontSize = 13.5.sp, color = FutaColors.Body, lineHeight = 20.sp)
                        }
                        val amenities = project["amenities"].array.map { it.string }.filter { it.isNotEmpty() }
                        if (amenities.isNotEmpty()) {
                            Text(tr("Tiện ích dự án ({0})", amenities.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            TwoColumnGrid(amenities.map { amenity ->
                                { m: Modifier ->
                                    Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                                        Text(amenity.translated("project"), fontSize = 12.5.sp, color = FutaColors.Navy)
                                    }
                                }
                            }, spacing = 8.dp)
                        }
                    }
                }
                val flycam = project["flycamVideoUrl"].string
                val tour = project["virtualTourUrl"].string
                val mobileBanner = project["bannerImageMobile"].string
                if (flycam.isNotEmpty() || tour.isNotEmpty() || mobileBanner.isNotEmpty()) {
                    DetailSection("Truyền thông & Trải nghiệm số", Icons.Default.OndemandVideo) {
                        if (flycam.isNotEmpty()) MediaLinkRow(Icons.Default.Videocam, Color(0xFFDC2626), "Xem video Flycam tiến độ", flycam) { openExternalUrl(context, flycam) }
                        if (tour.isNotEmpty()) MediaLinkRow(Icons.Default.ThreeSixty, Color(0xFF2563EB), "Trải nghiệm thực tế ảo VR 360°", tour) { openExternalUrl(context, tour) }
                        if (mobileBanner.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(FutaColors.CreamBg), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PhoneIphone, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Banner hiển thị riêng trên thiết bị Mobile", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                                    Text("Đã cấu hình banner tối ưu tỉ lệ hiển thị dọc trên điện thoại", fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))) { AdminRemoteImage(mobileBanner, Modifier.fillMaxSize()) }
                        }
                    }
                }
                val docs = project["legalDocuments"].array
                if (docs.isNotEmpty()) {
                    DetailSection("Hồ sơ pháp lý", Icons.Default.Description, trailing = { Text(tr("{0} tài liệu", docs.size), fontSize = 12.sp, color = FutaColors.Slate) }) {
                        docs.forEach { doc ->
                            LegalDocRow(doc, onOpen = { openExternalUrl(context, doc["url"].string) })
                        }
                    }
                }
                DetailSection("Sản phẩm thuộc dự án", Icons.Default.Apartment, trailing = { Text(tr("{0} căn", units.size), fontSize = 12.sp, color = FutaColors.Slate) }) {
                    if (units.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Apartment, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(28.dp))
                            Text("Chưa có sản phẩm nào thuộc phân khu dự án này.", fontSize = 12.5.sp, color = FutaColors.Slate)
                        }
                    } else {
                        units.take(20).forEach { unit -> ProjectUnitRow(unit) }
                        if (units.size > 20) Text(tr("Hiển thị 20 / {0} sản phẩm đầu tiên", units.size), fontSize = 11.5.sp, color = FutaColors.Slate)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    ConfirmDialog(
        visible = showToggleHide,
        title = if (hidden) "Hiển thị lại dự án?" else "Ẩn dự án này?",
        message = if (hidden) "Dự án sẽ được hiển thị công khai trên website và ứng dụng." else "Dự án sẽ bị ẩn khỏi trang chủ và danh sách công khai.",
        confirmText = if (hidden) "Hiển thị dự án" else "Ẩn dự án",
        destructive = !hidden,
        onDismiss = { showToggleHide = false },
        onConfirm = { toggleHide() }
    )
    ConfirmDialog(
        visible = showDelete,
        title = "Xác nhận xóa dự án?",
        message = "Hành động này không thể hoàn tác. Các sản phẩm thuộc dự án có thể bị ảnh hưởng.",
        confirmText = "Xóa dự án",
        destructive = true,
        onDismiss = { showDelete = false },
        onConfirm = { delete() }
    )
}

@Composable
private fun ProjectHero(project: JSONValue) {
    val banner = firstNonEmpty(project["bannerImageMobile"].string, project["bannerImage"].string, project["image"].string)
    val zone = projectZone(project)
    val status = project["status"].string
    Box(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp))
            .border(1.dp, FutaColors.LightBlueBorder, RoundedCornerShape(18.dp))
    ) {
        if (banner.isNotEmpty()) {
            AdminRemoteImage(banner, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(FutaColors.BrandGreenDark, FutaColors.Navy))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x4D061F19), Color(0xD9061D3D)))))
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(tr(projectStatusLabel(status)), projectStatusColor(status), solid = true)
                if (project["hidden"].bool) StatusPill(tr("Đã ẩn"), Color(0xFFDC2626), solid = true)
                if (project["showOnHome"].bool) StatusPill(tr("Trang chủ #{0}", project["homeOrder"].int), FutaColors.BrandOrange, solid = true)
            }
            Spacer(Modifier.weight(1f))
            Text(projectTitle(project).ifEmpty { tr("Dự án") }.translated("project"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (project["code"].string.isNotEmpty()) Text("# ${project["code"].string}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
                if (zone.isNotEmpty()) Text(zone.translated("project"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Place, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(14.dp))
                Text(projectLocation(project).ifEmpty { tr("Chưa cập nhật địa chỉ") }.translated("project"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
            }
        }
    }
}

@Composable
private fun MiniStat(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
                Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectDepositCard(project: JSONValue) {
    val bankName = project["depositBankName"].string
    val bankCode = project["depositBankCode"].string
    val account = project["depositAccountNumber"].string
    val holder = project["depositAccountHolder"].string
    val prefix = project["depositTransferSyntaxPrefix"].string
    val amount = project["depositDefaultAmount"].double
    val hasBank = account.isNotEmpty() && (bankCode.isNotEmpty() || bankName.isNotEmpty())
    DetailSection(
        "Tài khoản nhận cọc dự án", Icons.Default.Payments,
        trailing = {
            if (hasBank) StatusPill(tr("Tài khoản riêng"), FutaColors.BrandGreen, FutaColors.MintBg)
            else StatusPill(tr("Tài khoản mặc định"), FutaColors.Slate, Color(0xFFF1F5F9))
        }
    ) {
        if (hasBank) {
            TwoColumnGrid(buildList {
                add { m: Modifier -> SpecTile("Ngân hàng", if (bankCode.isNotEmpty()) "$bankName ($bankCode)" else bankName, Icons.Default.AccountBalance, m) }
                add { m: Modifier -> SpecTile("Số tài khoản", account, Icons.Default.CreditCard, m) }
                add { m: Modifier -> SpecTile("Chủ tài khoản", holder, Icons.Default.Person, m) }
                add { m: Modifier -> SpecTile("Tiền tố chuyển khoản", prefix.ifEmpty { tr("FUTA (mặc định)") }, Icons.Default.FormatQuote, m) }
                if (amount > 0) add { m: Modifier -> SpecTile("Số tiền cọc mặc định", SalesFormatters.currency(amount), Icons.Default.Paid, m) }
            })
        } else {
            Text(
                "Dự án chưa thiết lập số tài khoản nhận cọc riêng. Hệ thống đang sử dụng tài khoản ngân hàng thụ hưởng mặc định của công ty.",
                fontSize = 13.sp, color = FutaColors.Slate, lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun MediaLinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, url: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FAFC)).clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            Text(url, fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.OpenInNew, tr("Mở"), tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DocBadge(name: String, mimeType: String) {
    val lower = name.lowercase()
    val isImage = listOf(".png", ".jpg", ".jpeg", ".webp").any { lower.endsWith(it) } || mimeType.contains("image")
    val (icon, tint) = when {
        isImage -> Icons.Default.Image to Color(0xFF2563EB)
        lower.endsWith(".pdf") || mimeType == "application/pdf" || mimeType.isEmpty() -> Icons.Default.PictureAsPdf to Color(0xFFDC2626)
        else -> Icons.Default.InsertDriveFile to FutaColors.Slate
    }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun LegalDocRow(doc: JSONValue, onOpen: () -> Unit, onRemove: (() -> Unit)? = null, fallbackName: String = tr("Tài liệu pháp lý")) {
    val name = doc["name"].string.ifEmpty { fallbackName }
    val url = doc["url"].string
    val size = doc["size"].int
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FAFC)).border(1.dp, FutaColors.PanelDivider, RoundedCornerShape(12.dp))
            .clickable(enabled = url.isNotEmpty(), onClick = onOpen).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        DocBadge(name, doc["mimeType"].string)
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = if (size > 0) formatFileSize(size) else url
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (url.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Visibility, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp))
                Text("Xem", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, tr("Xóa tài liệu"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Unit status from the inventory status array → (label, fg, bg) like iOS `unitStatusBadge`. */
private fun unitStatusStyle(raw: String): Triple<String, Color, Color> {
    val n = raw.lowercase()
    return when {
        n.contains("cọc") || n.contains("deposit") -> Triple("Đã nộp cọc", FutaColors.BrandGreen, FutaColors.MintBg)
        n.contains("giữ") || n.contains("holding") || n.contains("chờ") -> Triple("Đang giữ chỗ", FutaColors.BrandOrange, FutaColors.CreamBg)
        n.contains("đã bán") || n.contains("sold") || n.contains("purchased") -> Triple("Đã bán", Color(0xFF6B7280), Color(0xFFF1F5F9))
        n.contains("mở") || n.contains("selling") || n.contains("available") || n.contains("active") -> Triple("Đang mở bán", FutaColors.BrandGreen, FutaColors.MintBg)
        else -> Triple(raw.ifEmpty { "Chưa mở bán" }, Color(0xFF2563EB), Color(0xFFEFF6FF))
    }
}

@Composable
private fun ProjectUnitRow(unit: JSONValue) {
    val code = unit["propertyCode"].string.ifEmpty { unit["title"].string }
    val block = unit["block"].string.ifEmpty { unit["building"].string }
    val type = unit["unitType"].string.ifEmpty { unit["apartmentType"].string }
    val specs = buildList {
        if (unit["floor"].string.isNotEmpty()) add(tr("Tầng {0}", unit["floor"].string))
        if (block.isNotEmpty()) add(tr("Tòa {0}", block))
        if (type.isNotEmpty()) add(type)
        if (unit["size_m2"].double > 0) add(SalesFormatters.area(unit["size_m2"].double))
    }.joinToString(" · ")
    val price = unit["sellPrice"].double.takeIf { it > 0 } ?: unit["price"].double
    val (label, fg, bg) = unitStatusStyle(unit["status"].array.firstOrNull()?.string ?: unit["status"].string.ifEmpty { "available" })
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FAFC)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(code, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            if (specs.isNotEmpty()) Text(specs, fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(SalesFormatters.currency(price), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
            StatusPill(tr(label), fg, bg)
        }
    }
}

// ============================================================================
// Create / edit form
// ============================================================================

private val defaultAmenities = listOf("Bảo vệ 24/7", "Hồ bơi tràn bờ", "Chỗ đỗ xe ô tô", "Thang máy tốc độ cao", "Công viên sinh thái", "Khu BBQ", "Camera an ninh")
private val allAmenityOptions = defaultAmenities + listOf("Phòng Gym & Yoga", "Khu vui chơi trẻ em", "Trung tâm thương mại", "Hệ thống PCCC", "Sảnh đón lễ tân")

/** Form values; compared against the initial copy for dirty tracking. */
private data class ProjectForm(
    val code: String,
    val displayName: String,
    val zone: String,
    val location: String,
    val province: String,
    val projectType: String,
    val developer: String,
    val status: String,
    val transactionType: String,
    val ownershipType: String,
    val landArea: String,
    val featuredTitle: String,
    val description: String,
    val image: String,
    val bannerImage: String,
    val flycamVideoUrl: String,
    val virtualTourUrl: String,
    val showOnHome: Boolean,
    val homeOrder: Int,
    val hidden: Boolean,
    val maxAdvisorsPerProduct: Int,
    val salesDurationDays: Int,
    val legalDocuments: List<JSONValue>,
    val amenities: List<String>,
    val depositBankName: String,
    val depositBankCode: String,
    val depositAccountNumber: String,
    val depositAccountHolder: String,
    val depositTransferSyntaxPrefix: String,
    val depositDefaultAmount: String
) {
    companion object {
        fun from(p: JSONValue?): ProjectForm {
            val amenities = p?.get("amenities")?.array?.map { it.string }?.filter { it.isNotEmpty() }.orEmpty()
            return ProjectForm(
                code = p?.get("code")?.string ?: "PROJ-${(1000..9999).random()}",
                displayName = p?.get("displayName")?.string.orEmpty(),
                zone = if (p == null) "" else projectZone(p),
                location = p?.get("location")?.string.orEmpty(),
                province = p?.get("province")?.string?.ifEmpty { null } ?: "Thành Phố Đà Nẵng",
                projectType = p?.get("projectType")?.string?.ifEmpty { null } ?: "Căn hộ",
                developer = p?.get("developer")?.string?.ifEmpty { null } ?: "Công ty Cổ Phần Kim Long Nam",
                status = p?.get("status")?.string?.ifEmpty { null } ?: "selling",
                transactionType = p?.get("transactionType")?.string?.ifEmpty { null } ?: "Bán",
                ownershipType = p?.get("ownershipType")?.string?.ifEmpty { null } ?: "Vĩnh viễn",
                landArea = p?.get("landArea")?.string.orEmpty(),
                featuredTitle = p?.get("featuredTitle")?.string.orEmpty(),
                description = p?.get("description")?.string.orEmpty(),
                image = p?.get("image")?.string.orEmpty(),
                bannerImage = p?.get("bannerImage")?.string.orEmpty(),
                flycamVideoUrl = p?.get("flycamVideoUrl")?.string.orEmpty(),
                virtualTourUrl = p?.get("virtualTourUrl")?.string.orEmpty(),
                showOnHome = if (p == null) true else p["showOnHome"].bool,
                homeOrder = p?.get("homeOrder")?.int?.takeIf { it > 0 } ?: 1,
                hidden = p?.get("hidden")?.bool ?: false,
                maxAdvisorsPerProduct = p?.get("maxAdvisorsPerProduct")?.int?.takeIf { it > 0 } ?: 3,
                salesDurationDays = p?.get("salesDurationDays")?.int?.takeIf { it > 0 } ?: 15,
                legalDocuments = p?.get("legalDocuments")?.array.orEmpty(),
                amenities = amenities.ifEmpty { defaultAmenities },
                depositBankName = p?.get("depositBankName")?.string.orEmpty(),
                depositBankCode = p?.get("depositBankCode")?.string.orEmpty(),
                depositAccountNumber = p?.get("depositAccountNumber")?.string.orEmpty(),
                depositAccountHolder = p?.get("depositAccountHolder")?.string.orEmpty(),
                depositTransferSyntaxPrefix = p?.get("depositTransferSyntaxPrefix")?.string.orEmpty(),
                depositDefaultAmount = p?.get("depositDefaultAmount")?.double?.takeIf { it > 0 }?.toLong()?.toString().orEmpty()
            )
        }
    }
}

@Composable
private fun AdminProjectFormScreen(project: JSONValue?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isEdit = project != null
    val initial = remember(project) { ProjectForm.from(project) }
    var form by remember(project) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }
    var uploadingBanner by remember { mutableStateOf(false) }
    var uploadingDoc by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showAddDocLink by remember { mutableStateOf(false) }
    val dirty = form != initial

    fun cancel() { if (dirty) showDiscard = true else onClose() }
    BackHandler { cancel() }

    fun uploadImage(uri: Uri, banner: Boolean) {
        scope.launch {
            if (banner) uploadingBanner = true else uploadingImage = true
            try {
                val url = uploadPickedImage(context, uri, "project")
                form = if (banner) form.copy(bannerImage = url) else form.copy(image = url)
                ToastCenter.show("Tải ảnh lên thành công")
            } catch (e: Exception) {
                ToastCenter.show(tr("Lỗi tải ảnh: {0}", e.message), isError = true)
            } finally {
                if (banner) uploadingBanner = false else uploadingImage = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploadImage(it, false) } }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploadImage(it, true) } }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                uploadingDoc = true
                try {
                    val doc = uploadPickedDocument(context, uri)
                    val entry = buildJsonObject {
                        put("name", doc.name)
                        put("url", doc.url)
                        put("size", doc.size)
                        put("mimeType", doc.mimeType)
                    }
                    form = form.copy(legalDocuments = form.legalDocuments + JSONValue(entry))
                    ToastCenter.show("Tải tài liệu lên thành công")
                } catch (e: Exception) {
                    errorMessage = tr("Tải tài liệu thất bại: {0}", e.message)
                    ToastCenter.show(tr("Tải tài liệu thất bại: {0}", e.message), isError = true)
                } finally {
                    uploadingDoc = false
                }
            }
        }
    }

    fun save() {
        val name = form.displayName.trim()
        val zone = form.zone.trim()
        val code = form.code.trim()
        val location = form.location.trim()
        errorMessage = when {
            name.isEmpty() -> tr("Vui lòng nhập tên hiển thị dự án")
            zone.isEmpty() -> tr("Vui lòng nhập phân khu (zone)")
            code.isEmpty() -> tr("Vui lòng nhập mã dự án")
            location.isEmpty() -> tr("Vui lòng nhập vị trí dự án")
            form.flycamVideoUrl.isNotBlank() && !form.flycamVideoUrl.trim().startsWith("http") -> tr("URL video Flycam không hợp lệ")
            form.virtualTourUrl.isNotBlank() && !form.virtualTourUrl.trim().startsWith("http") -> tr("URL Virtual Tour không hợp lệ")
            else -> null
        }
        if (errorMessage != null) {
            ToastCenter.show(errorMessage!!, isError = true)
            return
        }
        val body = buildJsonObject {
            put("zone", zone)
            put("code", code)
            put("displayName", name)
            put("location", location)
            put("province", form.province.trim())
            put("projectType", form.projectType)
            put("developer", form.developer.trim())
            put("status", form.status)
            put("transactionType", form.transactionType)
            put("ownershipType", form.ownershipType)
            put("landArea", form.landArea.trim())
            put("featuredTitle", form.featuredTitle.trim())
            put("description", form.description)
            put("showOnHome", form.showOnHome)
            put("homeOrder", form.homeOrder)
            put("hidden", form.hidden)
            put("maxAdvisorsPerProduct", form.maxAdvisorsPerProduct)
            put("salesDurationDays", form.salesDurationDays)
            put("legalDocuments", JsonArray(form.legalDocuments.map { doc ->
                buildJsonObject {
                    put("name", doc["name"].string.ifEmpty { tr("Tài liệu pháp lý") })
                    put("url", doc["url"].string)
                    if (doc["size"].int > 0) put("size", doc["size"].int)
                    if (doc["mimeType"].string.isNotEmpty()) put("mimeType", doc["mimeType"].string)
                }
            }))
            put("amenities", JsonArray(form.amenities.map { JsonPrimitive(it) }))
            put("depositBankName", form.depositBankName.trim())
            put("depositBankCode", form.depositBankCode.trim())
            put("depositAccountNumber", form.depositAccountNumber.trim())
            put("depositAccountHolder", form.depositAccountHolder.trim())
            put("depositTransferSyntaxPrefix", form.depositTransferSyntaxPrefix.trim())
            val amount = form.depositDefaultAmount.filter { it.isDigit() }.toLongOrNull()
            if (amount != null && amount > 0) put("depositDefaultAmount", amount) else put("depositDefaultAmount", JsonNull)
            // Empty media fields are sent as null so an editor can clear them.
            put("image", jsonString(form.image.trim().ifEmpty { null }))
            put("bannerImage", jsonString(form.bannerImage.trim().ifEmpty { null }))
            put("flycamVideoUrl", jsonString(form.flycamVideoUrl.trim().ifEmpty { null }))
            put("virtualTourUrl", jsonString(form.virtualTourUrl.trim().ifEmpty { null }))
        }.toString()
        scope.launch {
            saving = true
            try {
                if (isEdit) {
                    APIClient.get().request("/projects/admin/${Uri.encode(project!!.id)}", "PUT", body)
                    ToastCenter.show("Cập nhật dự án thành công")
                } else {
                    APIClient.get().request("/projects/admin", "POST", body)
                    ToastCenter.show("Tạo dự án mới thành công")
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
            SalesAdminTopBar(title = if (isEdit) "Sửa dự án" else "Thêm dự án", onBack = { cancel() }, subtitle = if (isEdit) projectTitle(project!!) else null) {
                if (isEdit) {
                    FutaHeaderIconButton(
                        icon = Icons.Default.RestartAlt,
                        contentDescription = tr("Khôi phục dữ liệu gốc"),
                        onClick = { if (dirty) showReset = true },
                        tint = if (dirty) Color(0xFFDC2626) else Color(0xFFCBD5E1)
                    )
                }
            }
        },
        bottomBar = {
            FormActionBar(
                saveTitle = if (isEdit) "Lưu thay đổi" else "Tạo dự án",
                saving = saving,
                enabled = !uploadingImage && !uploadingBanner && !uploadingDoc,
                onCancel = { cancel() },
                onSave = { save() }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).clearFocusOnTap().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FormErrorBanner(errorMessage)
            FormSection("Thông tin cơ bản") {
                FormTextField("Tên hiển thị dự án", form.displayName, { form = form.copy(displayName = it) }, "Nhập tên hiển thị dự án…", required = true)
                FormTextField("Mã dự án", form.code, { form = form.copy(code = it) }, "Nhập mã dự án…", required = true)
                FormTextField("Phân khu / Zone", form.zone, { form = form.copy(zone = it) }, "Nhập phân khu…", required = true)
                FormTextField("Vị trí / Địa chỉ", form.location, { form = form.copy(location = it) }, "Nhập vị trí / địa chỉ…", required = true)
                FormTextField("Tỉnh / Thành phố", form.province, { form = form.copy(province = it) }, "Nhập tỉnh / thành phố…")
                FormTextField("Chủ đầu tư", form.developer, { form = form.copy(developer = it) }, "Nhập chủ đầu tư…")
                FormTextField("Diện tích tổng thể", form.landArea, { form = form.copy(landArea = it) }, "VD: 420 ha hoặc 15.000 m²")
                AdminSelectField("Trạng thái", form.status, projectStatusOptions, { form = form.copy(status = it) })
                AdminSelectField(
                    "Loại dự án", form.projectType,
                    (listOf("Căn hộ", "Nhà phố", "Biệt thự", "Đất nền", "Shophouse") + listOfNotNull(form.projectType.takeIf { it.isNotEmpty() })).distinct().map { SelectOption(it, it) },
                    { form = form.copy(projectType = it) }
                )
                AdminSelectField(
                    "Hình thức giao dịch", form.transactionType,
                    (listOf("Bán", "Cho thuê", "Chuyển nhượng") + listOf(form.transactionType)).distinct().map { SelectOption(it, it) },
                    { form = form.copy(transactionType = it) }
                )
                AdminSelectField(
                    "Hình thức sở hữu", form.ownershipType,
                    (listOf("Vĩnh viễn", "Lâu dài", "50 năm") + listOf(form.ownershipType)).distinct().map { SelectOption(it, it) },
                    { form = form.copy(ownershipType = it) }
                )
            }
            FormSection("Hình ảnh & Media") {
                ImageUploadField("Ảnh đại diện dự án", form.image, uploadingImage, onPick = { imagePicker.launch("image/*") }, onRemove = { form = form.copy(image = "") })
                ImageUploadField("Ảnh banner dự án", form.bannerImage, uploadingBanner, onPick = { bannerPicker.launch("image/*") }, onRemove = { form = form.copy(bannerImage = "") })
                FormTextField("Tiêu đề nổi bật", form.featuredTitle, { form = form.copy(featuredTitle = it) }, "Nhập tiêu đề nổi bật…")
                FormTextField("URL video Flycam (YouTube)", form.flycamVideoUrl, { form = form.copy(flycamVideoUrl = it) }, "https://www.youtube.com/watch?v=…", keyboardType = KeyboardType.Uri)
                FormTextField("URL Virtual Tour 360°", form.virtualTourUrl, { form = form.copy(virtualTourUrl = it) }, "https://kuula.co/post/… hoặc link 360", keyboardType = KeyboardType.Uri)
            }
            FormSection("Chính sách & Bán hàng") {
                FormStepper(tr("Tối đa TVV / Căn: {0}", form.maxAdvisorsPerProduct), form.maxAdvisorsPerProduct, 1..20) { form = form.copy(maxAdvisorsPerProduct = it) }
                FormStepper(tr("Thời hạn giữ quyền: {0} ngày", form.salesDurationDays), form.salesDurationDays, 1..90) { form = form.copy(salesDurationDays = it) }
            }
            FormSection("Tài khoản ngân hàng nhận cọc") {
                FormTextField("Ngân hàng thụ hưởng", form.depositBankName, { form = form.copy(depositBankName = it) }, "VD: Ngân hàng TMCP Quân đội (MB Bank)…")
                FormTextField("Mã ngân hàng (VietQR code)", form.depositBankCode, { form = form.copy(depositBankCode = it) }, "VD: MB, VCB, BIDV, TCB…")
                FormTextField("Số tài khoản nhận cọc", form.depositAccountNumber, { form = form.copy(depositAccountNumber = it) }, "Nhập số tài khoản ngân hàng…", keyboardType = KeyboardType.Number)
                FormTextField("Tên chủ tài khoản thụ hưởng", form.depositAccountHolder, { form = form.copy(depositAccountHolder = it) }, "VD: CTY CO PHAN PHUONG TRANG…")
                FormTextField("Tiền tố cú pháp chuyển khoản", form.depositTransferSyntaxPrefix, { form = form.copy(depositTransferSyntaxPrefix = it) }, "Mặc định: FUTA (VD: TIMES, KIMAN)…")
                FormTextField(
                    "Số tiền cọc mặc định (VNĐ)",
                    form.depositDefaultAmount,
                    { form = form.copy(depositDefaultAmount = it.filter { c -> c.isDigit() }) },
                    "VD: 50000000…",
                    keyboardType = KeyboardType.Number
                )
                form.depositDefaultAmount.toLongOrNull()?.takeIf { it > 0 }?.let {
                    Text(SalesFormatters.currency(it.toDouble()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandOrange)
                }
                Text(
                    "Khi khách hàng/TVV tạo yêu cầu đặt cọc giữ chỗ cho sản phẩm của dự án này, mã VietQR sẽ được tạo theo tài khoản riêng ở trên. Nếu để trống, hệ thống sẽ sử dụng tài khoản chung mặc định.",
                    fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 16.sp
                )
            }
            FormSection("Cấu hình hiển thị & Tìm kiếm") {
                FormToggle(
                    "Hiển thị tại trang danh sách dự án (/projects)", !form.hidden, { form = form.copy(hidden = !it) },
                    "Dự án sẽ luôn xuất hiện trên trang danh sách dự án để giới thiệu thông tin quy mô, tiện ích, vị trí."
                )
                FormToggle(
                    "Hiển thị trong bộ lọc tìm kiếm sản phẩm", form.showOnHome, { form = form.copy(showOnHome = it) },
                    "Bật khi dự án có sản phẩm mở bán để khách hàng chọn trong bộ lọc tìm kiếm tại trang chủ và listing."
                )
                if (form.showOnHome) {
                    FormStepper(tr("Thứ tự ưu tiên lọc: {0}", form.homeOrder), form.homeOrder, 1..100) { form = form.copy(homeOrder = it) }
                }
            }
            FormSection("Mô tả chi tiết") {
                FormTextField("Mô tả dự án", form.description, { form = form.copy(description = it) }, "Mô tả dự án…", multiline = true)
            }
            FormSection(tr("Tiện ích dự án ({0})", form.amenities.size)) {
                (allAmenityOptions + form.amenities).distinct().forEach { option ->
                    val selected = form.amenities.contains(option)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            form = form.copy(amenities = if (selected) form.amenities - option else form.amenities + option)
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (selected) FutaColors.BrandGreen else Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                        Text(option.translated("project"), fontSize = 13.5.sp, color = FutaColors.Navy)
                    }
                }
            }
            FormSection(tr("Hồ sơ pháp lý ({0})", form.legalDocuments.size)) {
                if (form.legalDocuments.isEmpty()) {
                    Text("Chưa có tài liệu pháp lý nào được đính kèm.", fontSize = 12.5.sp, color = FutaColors.Slate)
                }
                form.legalDocuments.forEachIndexed { index, doc ->
                    LegalDocRow(
                        doc,
                        onOpen = { openExternalUrl(context, doc["url"].string) },
                        onRemove = { form = form.copy(legalDocuments = form.legalDocuments.filterIndexed { i, _ -> i != index }) },
                        fallbackName = tr("Tài liệu {0}", index + 1)
                    )
                }
                if (uploadingDoc) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = FutaColors.BrandGreen)
                        Text("Đang tải tệp lên hệ thống...", fontSize = 12.sp, color = FutaColors.Slate)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutaButton(
                        text = "Tải lên tệp (PDF / Ảnh)", icon = Icons.Default.UploadFile, variant = FutaButtonVariant.MINT,
                        enabled = !uploadingDoc && form.legalDocuments.size < 20, height = 40.dp,
                        onClick = { docPicker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.weight(1f)
                    )
                    FutaButton(
                        text = "Nhập URL", icon = Icons.Default.Link, variant = FutaButtonVariant.OUTLINE,
                        enabled = !uploadingDoc && form.legalDocuments.size < 20, height = 40.dp,
                        onClick = { showAddDocLink = true }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = onClose)
    ConfirmDialog(
        visible = showReset,
        title = "Khôi phục dữ liệu ban đầu?",
        message = "Tất cả chỉnh sửa chưa lưu sẽ được đặt lại theo dữ liệu ban đầu của dự án.",
        confirmText = "Khôi phục",
        destructive = true,
        onDismiss = { showReset = false },
        onConfirm = { form = initial }
    )
    if (showAddDocLink) {
        var docName by remember { mutableStateOf("") }
        var docUrl by remember { mutableStateOf("") }
        FutaDialog(
            visible = true,
            onDismiss = { showAddDocLink = false },
            title = "Thêm tài liệu pháp lý",
            confirmText = "Thêm",
            onConfirm = {
                if (docName.isNotBlank() && docUrl.isNotBlank()) {
                    val entry = buildJsonObject { put("name", docName.trim()); put("url", docUrl.trim()) }
                    form = form.copy(legalDocuments = form.legalDocuments + JSONValue(entry))
                } else {
                    ToastCenter.show(tr("Vui lòng nhập tên và URL tài liệu"), isError = true)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaInput(value = docName, onValueChange = { docName = it }, placeholder = "Tên tài liệu")
                FutaInput(value = docUrl, onValueChange = { docUrl = it }, placeholder = "URL tệp (PDF/Link)")
            }
        }
    }
}
