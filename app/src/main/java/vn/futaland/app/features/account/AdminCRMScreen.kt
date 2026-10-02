package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.APIError
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

// CRM workspace (iOS CRMView): groups ("nhóm làm việc") with their leads, pipeline stages and
// staff from GET /crm; leads are created/edited/noted per group under /crm/groups/:groupId/leads.

private sealed interface CrmPane {
    data class LeadDetail(val groupId: String, val lead: JSONValue) : CrmPane
    data object LeadCreate : CrmPane
    data object LeadBulkAdd : CrmPane
    data class GroupForm(val group: JSONValue?) : CrmPane
    data object Redistribute : CrmPane
    data object BulkActions : CrmPane
    data object Pipeline : CrmPane
    data object LegacyImport : CrmPane
}

// Backend enums (crm.schema.ts). iOS also offers "urgent" priority, "message"/"email" events,
// "uncontacted" redistribution and "manual" distribution, which the API rejects.
private val crmPriorities = listOf("low" to "Thấp", "medium" to "Trung bình", "high" to "Cao")
private val crmEventTypes = listOf("call" to "Cuộc gọi", "meeting" to "Gặp mặt", "note" to "Ghi chú", "info" to "Thông tin")
private val crmDistributionModes = listOf(
    "round_robin" to "Xoay vòng (Round Robin)",
    "team" to "Cân bằng theo khối lượng",
    "weighted" to "Theo tỷ trọng (Weighted)",
    "single" to "Một nhân sự phụ trách"
)
private val crmGroupColors = listOf("#2563EB", "#059669", "#DC2626", "#7C3AED", "#F59E0B", "#0891B2")
private val crmSorts = listOf(
    "updated" to "Mới cập nhật",
    "created" to "Mới tạo",
    "name" to "Tên A-Z",
    "priority" to "Ưu tiên cao trước"
)

private fun crmEnc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
private fun leadPath(groupId: String, leadId: String) = "/crm/groups/${crmEnc(groupId)}/leads/${crmEnc(leadId)}"

private fun stageLabel(stages: List<JSONValue>, id: String): String =
    stages.firstOrNull { it["id"].string == id }?.get("label")?.string?.ifEmpty { null } ?: id

private val crmStagePalette = listOf(0xFF2563EB, 0xFF0891B2, 0xFF7C3AED, 0xFFF59E0B, 0xFFF97316, 0xFFDB2777, 0xFF207446, 0xFFDC2626)

private fun stageColor(stages: List<JSONValue>, id: String): Color {
    when (id) {
        "won" -> return Color(0xFF207446)
        "lost" -> return Color(0xFFDC2626)
    }
    val idx = stages.indexOfFirst { it["id"].string == id }
    return Color(crmStagePalette[(if (idx < 0) 0 else idx) % crmStagePalette.size])
}

private fun priorityLabel(p: String) = crmPriorities.firstOrNull { it.first == p }?.second ?: "Trung bình"
private fun priorityColor(p: String) = when (p) {
    "high" -> Color(0xFFDC2626)
    "low" -> Color(0xFF64748B)
    else -> Color(0xFFF59E0B)
}

private fun staffName(staff: List<JSONValue>, id: String): String =
    staff.firstOrNull { it.id == id }?.let { it["name"].string.ifEmpty { it["phone"].string } } ?: id.take(8)

private fun staffOptions(staff: List<JSONValue>) = staff.map { it.id to it["name"].string.ifEmpty { it["phone"].string } }

/** Group `access` flag from the API; when the server omits it, fall back to the permission. */
private fun JSONValue?.allows(key: String, permission: String): Boolean {
    val flag = this?.get("access")?.get(key)
    return if (flag == null || flag.isNull) AppSession.shared.hasPermission(permission) else flag.bool
}

private fun skippedReason(reason: String) = when (reason) {
    "duplicate_phone" -> "Số điện thoại đã tồn tại"
    "invalid_status" -> "Trạng thái không hợp lệ"
    "missing_required_fields" -> "Thiếu tên hoặc số điện thoại"
    else -> reason
}

@Composable
fun AdminCRMScreen(
    onBack: () -> Unit,
    initialGroupId: String? = null,
    initialLeadId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var stages by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var staff by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var sources by remember { mutableStateOf<List<String>>(emptyList()) }
    var leads by remember { mutableStateOf<List<JSONValue>>(emptyList()) }

    // groupQuery is what the user picked (sent as ?groupId); selectedGroupId is what the server resolved.
    var groupQuery by remember { mutableStateOf(initialGroupId.orEmpty()) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId.orEmpty()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var priorityFilter by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf("") }
    var assigneeFilter by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("updated") }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var totalLeads by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadedOnce by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pane by remember { mutableStateOf<CrmPane?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var confirmDeleteGroup by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var didOpenInitialLead by remember { mutableStateOf(false) }

    val currentGroup = groups.firstOrNull { it.id == selectedGroupId }
    val canCreateGroup = AppSession.shared.hasPermission("customers:create")
    val canCreateLead = currentGroup != null && currentGroup.allows("canCreateLead", "customers:create")
    val canEditGroup = currentGroup.allows("canEditGroup", "customers:edit")
    val canDeleteGroup = currentGroup.allows("canDeleteGroup", "customers:edit")
    val canRedistribute = currentGroup.allows("canRedistribute", "customers:edit")
    val canManagePipeline = currentGroup.allows("canManagePipeline", "customers:edit")
    val canAssignLead = currentGroup.allows("canAssignLead", "customers:edit")

    fun workspaceQuery(pageNumber: Int, limit: Int): Map<String, String> {
        val q = mutableMapOf("page" to "$pageNumber", "limit" to "$limit")
        if (groupQuery.isNotEmpty()) q["groupId"] = groupQuery
        search.trim().takeIf { it.isNotEmpty() }?.let { q["search"] = it }
        if (statusFilter.isNotEmpty()) q["status"] = statusFilter
        if (priorityFilter.isNotEmpty()) q["priority"] = priorityFilter
        if (sourceFilter.isNotEmpty()) q["source"] = sourceFilter
        if (assigneeFilter.isNotEmpty()) q["assigneeId"] = assigneeFilter
        return q
    }

    suspend fun fetchWorkspace() {
        loading = true
        try {
            val data = APIClient.get().request("/crm", query = workspaceQuery(page, 50))["data"]
            groups = data["groups"].array
            stages = data["pipelineStages"].array
            staff = data["staff"].array
            sources = data["filterOptions"]["sources"].array.map { it.string }.filter { it.isNotEmpty() }
            val resolved = data["selectedGroupId"].string
            selectedGroupId = resolved
            leads = groups.firstOrNull { it.id == resolved }?.get("leads")?.array ?: emptyList()
            val pagination = data["pagination"]
            totalLeads = pagination["total"].int
            totalPages = maxOf(1, pagination["totalPages"].int)
            selectedIds = selectedIds.intersect(leads.map { it.id }.toSet())
            error = null
            loadedOnce = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: APIError) {
            // The picked group was deleted or is no longer shared: reload with the default group.
            if (e.statusCode == 404 && groupQuery.isNotEmpty()) {
                groupQuery = ""
                return
            }
            error = e.message
            ToastCenter.show(error ?: "", isError = true)
        } catch (e: Exception) {
            error = e.message ?: tr("Không tải được dữ liệu")
            ToastCenter.show(error ?: "", isError = true)
        } finally {
            loading = false
        }

        // Deep link (notification tap /crm?groupId=…&leadId=…): open that lead once.
        val targetLead = initialLeadId?.takeIf { it.isNotEmpty() }
        if (targetLead != null && !didOpenInitialLead && loadedOnce) {
            didOpenInitialLead = true
            val gid = initialGroupId?.takeIf { it.isNotEmpty() } ?: selectedGroupId
            val local = leads.firstOrNull { it.id == targetLead }
            if (local != null && gid == selectedGroupId) {
                pane = CrmPane.LeadDetail(gid, local)
            } else if (gid.isNotEmpty()) {
                try {
                    val lead = APIClient.get().request(leadPath(gid, targetLead))["data"]
                    if (lead.id == targetLead) pane = CrmPane.LeadDetail(gid, lead)
                    else ToastCenter.show(tr("Lead không còn khả dụng."), isError = true)
                } catch (_: Exception) {
                    ToastCenter.show(tr("Lead không còn khả dụng hoặc bạn không có quyền truy cập."), isError = true)
                }
            }
        }
    }

    LaunchedEffect(groupQuery, search, statusFilter, priorityFilter, sourceFilter, assigneeFilter, page, reloadKey) {
        if (loadedOnce) delay(300)
        fetchWorkspace()
    }

    fun reload() { reloadKey++ }
    fun selectGroup(id: String) {
        if (id == selectedGroupId) return
        groupQuery = id
        selectedGroupId = id
        page = 1
        statusFilter = ""; sourceFilter = ""; assigneeFilter = ""; priorityFilter = ""
        selectedIds = emptySet()
    }
    fun clearFilters() {
        statusFilter = ""; priorityFilter = ""; sourceFilter = ""; assigneeFilter = ""; search = ""; page = 1
    }

    val sortedLeads = remember(leads, sort) {
        when (sort) {
            "name" -> leads.sortedBy { it["customerName"].string.lowercase() }
            "created" -> leads.sortedByDescending { it["createdAt"].string }
            "priority" -> leads.sortedByDescending { when (it["priority"].string) { "high" -> 2; "medium" -> 1; else -> 0 } }
            else -> leads
        }
    }
    val activeFilterCount = listOf(statusFilter, priorityFilter, sourceFilter, assigneeFilter).count { it.isNotEmpty() }
    val hasQuery = activeFilterCount > 0 || search.isNotBlank()

    fun exportCsv() {
        if (selectedGroupId.isEmpty()) return
        scope.launch {
            exporting = true
            try {
                val all = mutableListOf<JSONValue>()
                var p = 1
                var pages: Int
                do {
                    val data = APIClient.get().request("/crm", query = workspaceQuery(p, 100) + ("groupId" to selectedGroupId))["data"]
                    all += data["groups"].array.firstOrNull { it.id == selectedGroupId }?.get("leads")?.array.orEmpty()
                    pages = data["pagination"]["totalPages"].int
                    p++
                } while (p <= pages && p <= 50)
                val header = "Họ tên,Số điện thoại,Email,Nhu cầu,Nguồn,Trạng thái,Độ ưu tiên,Ngân sách,Khu vực,Phụ trách,Ngày tạo"
                val rows = all.map { l ->
                    listOf(
                        l["customerName"].string,
                        l["customerPhone"].string,
                        l["email"].string,
                        l["demand"].string,
                        l["source"].string,
                        stageLabel(stages, l["status"].string),
                        tr(priorityLabel(l["priority"].string)),
                        l["budget"].string,
                        l["area"].string,
                        l["assignedSaleIds"].array.joinToString("; ") { staffName(staff, it.string) },
                        shortDate(l["createdAt"].string)
                    ).joinToString(",") { crmCsvCell(it) }
                }
                if (all.isEmpty()) {
                    ToastCenter.show(tr("Không có dữ liệu để xuất"))
                } else {
                    crmShareText(context, (listOf(header) + rows).joinToString("\n"), "Xuất dữ liệu CSV")
                }
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không xuất được dữ liệu"), isError = true)
            } finally {
                exporting = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = CrmPageBackground,
            topBar = {
                Surface(color = FutaColors.PageBg, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FutaHeaderIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", onClick = onBack)
                        Text(
                            text = "Khách hàng tiềm năng",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (selectionMode) "Xong" else "Chọn",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (leads.isEmpty() && !selectionMode) FutaColors.Slate else FutaColors.BrandGreen,
                            modifier = Modifier
                                .clickable(enabled = leads.isNotEmpty() || selectionMode) {
                                    selectionMode = !selectionMode
                                    if (!selectionMode) selectedIds = emptySet()
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                        FutaHeaderIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Thêm khách hàng",
                            tint = if (canCreateLead) FutaColors.BrandGreen else FutaColors.Slate.copy(alpha = 0.4f),
                            onClick = { if (canCreateLead) pane = CrmPane.LeadCreate }
                        )
                        Box {
                            FutaHeaderIconButton(icon = Icons.Default.MoreVert, contentDescription = "Thêm", onClick = { showMenu = true })
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = Color.White
                            ) {
                                @Composable
                                fun item(label: String, icon: ImageVector, danger: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 14.sp, color = if (danger) Color(0xFFDC2626) else FutaColors.Navy) },
                                        leadingIcon = { Icon(icon, null, tint = if (danger) Color(0xFFDC2626) else FutaColors.Slate) },
                                        enabled = enabled,
                                        onClick = {
                                            showMenu = false
                                            action()
                                        }
                                    )
                                }
                                if (canCreateGroup) item("Tạo nhóm mới", Icons.Default.CreateNewFolder) { pane = CrmPane.GroupForm(null) }
                                if (currentGroup != null) {
                                    if (canEditGroup) item("Cấu hình nhóm", Icons.Default.Tune) { pane = CrmPane.GroupForm(currentGroup) }
                                    if (canRedistribute) item("Phân bổ lại leads", Icons.Default.Autorenew) { pane = CrmPane.Redistribute }
                                    if (canCreateLead) item("Thêm nhiều khách hàng", Icons.Default.PlaylistAdd) { pane = CrmPane.LeadBulkAdd }
                                }
                                item("Quy trình bán hàng (Pipeline)", Icons.Default.ViewKanban) { pane = CrmPane.Pipeline }
                                if (currentGroup != null) {
                                    item(if (exporting) "Đang xuất..." else "Xuất dữ liệu CSV", Icons.Default.IosShare, enabled = !exporting) { exportCsv() }
                                }
                                if (canCreateGroup) item("Nhập dữ liệu lưu trữ (Import)", Icons.Default.CloudUpload) { pane = CrmPane.LegacyImport }
                                if (currentGroup != null && canDeleteGroup) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    item("Xoá nhóm hiện tại", Icons.Default.Delete, danger = true) { confirmDeleteGroup = true }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            when {
                loading && !loadedOnce -> CrmWorkspaceSkeleton(Modifier.padding(padding))
                error != null && !loadedOnce -> AdvisorErrorState(
                    message = error ?: "",
                    onRetry = { reload() },
                    modifier = Modifier.padding(padding).padding(top = 60.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CrmGroupSelector(
                            groups = groups,
                            selectedGroupId = selectedGroupId,
                            canCreateGroup = canCreateGroup,
                            onSelect = { selectGroup(it) },
                            onCreate = { pane = CrmPane.GroupForm(null) }
                        )
                    }
                    if (currentGroup != null) {
                        item {
                            val stats = currentGroup["stats"]
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CrmStatTile("Tổng Leads", "${stats["total"].int}", Color(0xFF2563EB), Modifier.weight(1f))
                                CrmStatTile("Đã giao", "${stats["assigned"].int}", FutaColors.BrandGreen, Modifier.weight(1f))
                                CrmStatTile("Chưa giao", "${stats["unassigned"].int}", FutaColors.BrandOrange, Modifier.weight(1f))
                                CrmStatTile("Đang chăm sóc", "${stats["active"].int}", Color(0xFF7C3AED), Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FutaInput(
                                    value = search,
                                    onValueChange = { search = it; page = 1 },
                                    placeholder = "Tìm kiếm theo tên, SĐT, email...",
                                    leadingIcon = Icons.Default.Search,
                                    trailingIcon = if (search.isNotEmpty()) {
                                        {
                                            Icon(
                                                Icons.Default.Close, tr("Xoá"), tint = FutaColors.Slate,
                                                modifier = Modifier.size(18.dp).clickable { search = ""; page = 1 }
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, if (sort != "updated") FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                        modifier = Modifier.size(46.dp).clickable { showSortMenu = true }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Filled.Sort, tr("Sắp xếp"), tint = if (sort != "updated") FutaColors.BrandGreen else FutaColors.Slate)
                                        }
                                    }
                                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, containerColor = Color.White) {
                                        crmSorts.forEach { (key, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label, fontSize = 14.sp, fontWeight = if (sort == key) FontWeight.Bold else FontWeight.Normal, color = FutaColors.Navy) },
                                                trailingIcon = { if (sort == key) Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen) },
                                                onClick = { sort = key; showSortMenu = false }
                                            )
                                        }
                                    }
                                }
                                CrmFilterButton(activeCount = activeFilterCount, onClick = { showFilter = true })
                            }
                        }
                        item {
                            val chips = buildList<Pair<String, () -> Unit>> {
                                if (statusFilter.isNotEmpty()) add(tr("Trạng thái: {0}", tr(stageLabel(stages, statusFilter))) to { statusFilter = ""; page = 1 })
                                if (priorityFilter.isNotEmpty()) add(tr("Ưu tiên: {0}", tr(priorityLabel(priorityFilter))) to { priorityFilter = ""; page = 1 })
                                if (sourceFilter.isNotEmpty()) add(tr("Nguồn: {0}", sourceFilter) to { sourceFilter = ""; page = 1 })
                                if (assigneeFilter.isNotEmpty()) {
                                    val name = if (assigneeFilter == "unassigned") tr("Chưa giao") else staffName(staff, assigneeFilter)
                                    add(tr("Phụ trách: {0}", name) to { assigneeFilter = ""; page = 1 })
                                }
                            }
                            CrmAppliedFilterChips(chips = chips, onClearAll = { clearFilters() })
                        }
                        if (selectionMode && selectedIds.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = FutaColors.MintBg,
                                    border = BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(tr("Đã chọn {0} khách hàng", selectedIds.size), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                        FutaButton(text = "Thao tác hàng loạt", height = 36.dp, onClick = { pane = CrmPane.BulkActions })
                                    }
                                }
                            }
                        }
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Danh sách khách hàng ({0})", totalLeads), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                if (loading) CircularProgressIndicator(strokeWidth = 2.dp, color = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                                if (selectionMode && sortedLeads.isNotEmpty()) {
                                    val allSelected = selectedIds.size == sortedLeads.size
                                    Text(
                                        if (allSelected) "Bỏ chọn tất cả" else "Chọn tất cả",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.BrandGreen,
                                        modifier = Modifier
                                            .clickable { selectedIds = if (allSelected) emptySet() else sortedLeads.map { it.id }.toSet() }
                                            .padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                        if (error != null && leads.isEmpty()) {
                            item { AdvisorErrorState(message = error ?: "", onRetry = { reload() }) }
                        } else if (sortedLeads.isEmpty() && !loading) {
                            item {
                                if (hasQuery) {
                                    FutaEmptyState(
                                        title = "Không có kết quả phù hợp",
                                        message = "Không tìm thấy khách hàng khớp với từ khoá hoặc bộ lọc hiện tại.",
                                        icon = Icons.Default.SearchOff,
                                        actionButton = { FutaButton(text = "Xoá bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = { clearFilters() }) }
                                    )
                                } else {
                                    FutaEmptyState(
                                        title = "Chưa có khách hàng",
                                        message = "Thêm lead mới hoặc thay đổi bộ lọc để xem danh sách.",
                                        icon = Icons.Default.PersonAdd,
                                        actionButton = if (canCreateLead) {
                                            { FutaButton(text = "Thêm khách hàng", icon = Icons.Default.Add, onClick = { pane = CrmPane.LeadCreate }) }
                                        } else null
                                    )
                                }
                            }
                        } else if (sortedLeads.isEmpty()) {
                            items(4) { FutaAdminRowSkeleton() }
                        } else {
                            items(sortedLeads, key = { it.id }) { lead ->
                                val checked = lead.id in selectedIds
                                CrmLeadRow(
                                    lead = lead,
                                    stages = stages,
                                    staff = staff,
                                    selectionMode = selectionMode,
                                    checked = checked,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (checked) selectedIds - lead.id else selectedIds + lead.id
                                        } else {
                                            pane = CrmPane.LeadDetail(selectedGroupId, lead)
                                        }
                                    }
                                )
                            }
                            item {
                                CrmPaginationBar(
                                    page = page,
                                    totalPages = totalPages,
                                    total = totalLeads,
                                    onPrevious = { if (page > 1) page-- },
                                    onNext = { if (page < totalPages) page++ }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }

        when (val p = pane) {
            is CrmPane.LeadDetail -> CrmLeadDetailPage(
                groupId = p.groupId,
                initial = p.lead,
                stages = stages,
                staff = staff,
                onBack = { pane = null },
                onChanged = { reload() },
                onDeleted = { pane = null; reload() }
            )
            CrmPane.LeadCreate -> CrmLeadFormPage(
                groupId = selectedGroupId,
                lead = null,
                stages = stages,
                staff = staff,
                canAssign = canAssignLead,
                onBack = { pane = null },
                onSaved = { pane = null; reload() }
            )
            CrmPane.LeadBulkAdd -> CrmBulkAddPage(
                groupId = selectedGroupId,
                onBack = { pane = null },
                onDone = { pane = null; reload() }
            )
            is CrmPane.GroupForm -> CrmGroupFormPage(
                group = p.group,
                staff = staff,
                onBack = { pane = null },
                onSaved = { newId ->
                    pane = null
                    if (newId != null) selectGroup(newId)
                    reload()
                }
            )
            CrmPane.Redistribute -> CrmRedistributePage(
                groupId = selectedGroupId,
                group = currentGroup,
                staff = staff,
                onBack = { pane = null },
                onDone = { pane = null; reload() }
            )
            CrmPane.BulkActions -> CrmBulkActionsPage(
                groupId = selectedGroupId,
                leadIds = selectedIds.toList(),
                stages = stages,
                staff = staff,
                canAssign = currentGroup.allows("canBulkAssign", "customers:edit"),
                canDelete = currentGroup.allows("canBulkDelete", "customers:edit"),
                onBack = { pane = null },
                onDone = {
                    pane = null
                    selectedIds = emptySet()
                    selectionMode = false
                    reload()
                }
            )
            CrmPane.Pipeline -> CrmPipelinePage(
                stages = stages,
                canReset = canManagePipeline,
                onBack = { pane = null },
                onDone = { pane = null; reload() }
            )
            CrmPane.LegacyImport -> CrmLegacyImportPage(
                onBack = { pane = null },
                onDone = { pane = null; reload() }
            )
            null -> Unit
        }
    }

    if (showFilter) {
        var draftStatus by remember { mutableStateOf(statusFilter) }
        var draftPriority by remember { mutableStateOf(priorityFilter) }
        var draftSource by remember { mutableStateOf(sourceFilter) }
        var draftAssignee by remember { mutableStateOf(assigneeFilter) }
        FutaBottomSheet(
            visible = true,
            onDismiss = { showFilter = false },
            title = "Bộ lọc khách hàng",
            footer = {
                CrmFilterSheetFooter(
                    onReset = { draftStatus = ""; draftPriority = ""; draftSource = ""; draftAssignee = "" },
                    onCancel = { showFilter = false },
                    onApply = {
                        statusFilter = draftStatus
                        priorityFilter = draftPriority
                        sourceFilter = draftSource
                        assigneeFilter = draftAssignee
                        page = 1
                        showFilter = false
                    }
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CrmFilterOptionGroup(
                    title = "Trạng thái",
                    options = listOf("") + stages.map { it["id"].string },
                    selected = draftStatus,
                    label = { if (it.isEmpty()) "Tất cả" else stageLabel(stages, it) },
                    onSelect = { draftStatus = it }
                )
                CrmFilterOptionGroup(
                    title = "Độ ưu tiên",
                    options = listOf("") + crmPriorities.map { it.first },
                    selected = draftPriority,
                    label = { if (it.isEmpty()) "Tất cả" else priorityLabel(it) },
                    onSelect = { draftPriority = it }
                )
                if (sources.isNotEmpty()) {
                    CrmFilterOptionGroup(
                        title = "Nguồn",
                        options = listOf("") + sources,
                        selected = draftSource,
                        label = { if (it.isEmpty()) tr("Tất cả") else it },
                        onSelect = { draftSource = it },
                        verbatim = true
                    )
                }
                if (staff.isNotEmpty()) {
                    CrmFilterOptionGroup(
                        title = "Người phụ trách",
                        options = listOf("", "unassigned") + staff.map { it.id },
                        selected = draftAssignee,
                        label = {
                            when (it) {
                                "" -> tr("Tất cả")
                                "unassigned" -> tr("Chưa giao")
                                else -> staffName(staff, it)
                            }
                        },
                        onSelect = { draftAssignee = it },
                        verbatim = true
                    )
                }
            }
        }
    }

    CrmConfirmDialog(
        visible = confirmDeleteGroup,
        title = "Xoá nhóm khách hàng?",
        message = "Toàn bộ dữ liệu nhóm sẽ bị xoá. Bạn có chắc chắn?",
        confirmText = "Xoá vĩnh viễn",
        destructive = true,
        onConfirm = {
            val gid = selectedGroupId
            if (gid.isNotEmpty() && !deletingGroup) {
                scope.launch {
                    deletingGroup = true
                    try {
                        APIClient.get().request("/crm/groups/${crmEnc(gid)}", method = "DELETE")
                        ToastCenter.show(tr("Đã xoá nhóm khách hàng"))
                        groupQuery = ""
                        selectedGroupId = ""
                        page = 1
                        reload()
                    } catch (e: Exception) {
                        ToastCenter.show(e.message ?: tr("Không xoá được nhóm"), isError = true)
                    } finally {
                        deletingGroup = false
                    }
                }
            }
        },
        onDismiss = { confirmDeleteGroup = false }
    )
}

@Composable
private fun CrmWorkspaceSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { FutaSkeletonBlock(height = 34.dp, width = 96.dp, radius = 10.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { FutaSkeletonBlock(height = 62.dp, radius = 12.dp, modifier = Modifier.weight(1f)) }
        }
        FutaSkeletonBlock(height = 46.dp, radius = 12.dp)
        repeat(5) { FutaAdminRowSkeleton() }
    }
}

@Composable
private fun CrmGroupSelector(
    groups: List<JSONValue>,
    selectedGroupId: String,
    canCreateGroup: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Nhóm làm việc", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate, modifier = Modifier.weight(1f))
            Text(tr("{0} nhóm", groups.size), fontSize = 11.sp, color = FutaColors.Slate)
        }
        if (groups.isEmpty()) {
            FutaEmptyState(
                title = "Chưa có nhóm khách hàng",
                message = "Tạo nhóm để bắt đầu quản lý và phân bổ khách hàng tiềm năng.",
                icon = Icons.Default.FolderOpen,
                actionButton = if (canCreateGroup) {
                    { FutaButton(text = "Tạo nhóm khách hàng đầu tiên", icon = Icons.Default.Add, onClick = onCreate) }
                } else null
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { g ->
                    val isSelected = g.id == selectedGroupId
                    val count = g["leadCount"].int.takeIf { it > 0 } ?: g["stats"]["total"].int
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) FutaColors.MintBg else Color.White,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { onSelect(g.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(8.dp).background(crmHexColor(g["color"].string), CircleShape))
                            VerbatimText(
                                g["name"].string,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = FutaColors.Navy
                            )
                            if (count > 0) VerbatimText("($count)", fontSize = 11.sp, color = FutaColors.Slate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrmStageBadge(stages: List<JSONValue>, status: String) {
    AdvisorBadge(text = stageLabel(stages, status).ifEmpty { "-" }, color = stageColor(stages, status))
}

@Composable
private fun CrmLeadRow(
    lead: JSONValue,
    stages: List<JSONValue>,
    staff: List<JSONValue>,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit
) {
    FutaCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (checked) FutaColors.BrandGreen else FutaColors.LightBlueBorder,
        onClick = onClick
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Icon(
                    if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    null,
                    tint = if (checked) FutaColors.BrandGreen else FutaColors.Slate,
                    modifier = Modifier.padding(end = 10.dp).size(22.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        if (lead["customerName"].string.isEmpty()) {
                            Text("Chưa có tên", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        } else {
                            VerbatimText(lead["customerName"].string, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        VerbatimText(lead["customerPhone"].string, fontSize = 12.5.sp, color = FutaColors.Slate)
                    }
                    CrmStageBadge(stages, lead["status"].string)
                }
                val demand = lead["demand"].string
                if (demand.isNotEmpty()) {
                    Text(tr("Nhu cầu: {0}", demand), fontSize = 12.5.sp, color = FutaColors.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val priority = lead["priority"].string
                    if (priority.isNotEmpty()) {
                        Text(priorityLabel(priority), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = priorityColor(priority))
                    }
                    if (lead["source"].string.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sell, null, tint = FutaColors.Slate, modifier = Modifier.size(12.dp))
                            VerbatimText(lead["source"].string, fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
                        }
                    }
                    if (lead["budget"].string.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, null, tint = FutaColors.Slate, modifier = Modifier.size(12.dp))
                            VerbatimText(lead["budget"].string, fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1, modifier = Modifier.padding(start = 3.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val assigned = lead["assignedSaleIds"].array.map { it.string }
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        assigned.take(3).forEach { sid ->
                            Box(
                                Modifier.size(20.dp).background(FutaColors.BrandGreen.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                VerbatimText(staffName(staff, sid).take(1).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Lead detail

@Composable
private fun CrmLeadDetailPage(
    groupId: String,
    initial: JSONValue,
    stages: List<JSONValue>,
    staff: List<JSONValue>,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var lead by remember(initial.id) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var pendingStatus by remember { mutableStateOf<String?>(null) }
    var showPrioritySheet by remember { mutableStateOf(false) }
    var showAssignSheet by remember { mutableStateOf(false) }
    var eventType by remember { mutableStateOf("call") }
    var eventTitle by remember { mutableStateOf("") }
    var eventDesc by remember { mutableStateOf("") }
    var savingEvent by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var savingNote by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val leadId = lead.id
    val canEdit = lead["access"]["canEdit"].let { it.isNull || it.bool } && AppSession.shared.hasPermission("customers:edit")
    val canAssign = lead["access"]["canAssign"].let { if (it.isNull) AppSession.shared.hasPermission("customers:edit") else it.bool }
    val canDelete = lead["access"]["canDelete"].let { if (it.isNull) AppSession.shared.hasPermission("customers:edit") else it.bool }

    // Refresh so notes/history are current (the list copy may be older).
    LaunchedEffect(leadId) {
        try {
            val fresh = APIClient.get().request(leadPath(groupId, leadId))["data"]
            if (fresh.id == leadId) lead = fresh
        } catch (_: Exception) {}
    }

    fun update(body: JsonObject, toast: String) {
        scope.launch {
            busy = true
            try {
                val res = APIClient.get().request(leadPath(groupId, leadId), method = "PUT", bodyJson = body.toString())
                if (!res["data"].isNull) lead = res["data"]
                ToastCenter.show(tr(toast))
                onChanged()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không cập nhật được"), isError = true)
            } finally {
                busy = false
            }
        }
    }

    val name = lead["customerName"].string
    val phone = lead["customerPhone"].string
    val status = lead["status"].string
    val priority = lead["priority"].string.ifEmpty { "medium" }
    val assigned = lead["assignedSaleIds"].array.map { it.string }

    Box(Modifier.fillMaxSize()) {
        CrmFullScreenPage(
            title = name.ifEmpty { tr("Chi tiết Lead") },
            onBack = onBack,
            actions = {
                if (canEdit) {
                    TextButton(onClick = { editing = true }) {
                        Icon(Icons.Default.Edit, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                        Text("Sửa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        ) {
            // Header card
            FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(50.dp).background(FutaColors.BrandGreen, CircleShape), contentAlignment = Alignment.Center) {
                            VerbatimText(name.trim().take(2).uppercase().ifEmpty { "KH" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            VerbatimText(name.ifEmpty { "-" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            VerbatimText(phone, fontSize = 13.sp, color = FutaColors.Slate)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CrmStageBadge(stages, status)
                                AdvisorBadge(text = priorityLabel(priority), color = priorityColor(priority))
                            }
                        }
                    }
                    CrmContactActions(phone = phone)
                }
            }

            AdvisorSection(header = "Thông tin liên hệ") {
                AdvisorLabeledRow("Họ và tên", name)
                AdvisorLabeledRow("Điện thoại", phone)
                if (lead["email"].string.isNotEmpty()) AdvisorLabeledRow("Email", lead["email"].string)
                if (lead["source"].string.isNotEmpty()) AdvisorLabeledRow("Nguồn", lead["source"].string)
                if (lead["demand"].string.isNotEmpty()) AdvisorLabeledRow("Nhu cầu", lead["demand"].string)
                if (lead["budget"].string.isNotEmpty()) AdvisorLabeledRow("Ngân sách", lead["budget"].string)
                if (lead["area"].string.isNotEmpty()) AdvisorLabeledRow("Khu vực", lead["area"].string)
                if (lead["note"].string.isNotEmpty()) AdvisorLabeledRow("Ghi chú", lead["note"].string, bold = false)
                AdvisorLabeledRow("Ngày tạo", shortDate(lead["createdAt"].string), bold = false)
                AdvisorLabeledRow("Cập nhật", shortDate(lead["updatedAt"].string), bold = false)
            }

            AdvisorSection(header = "Quy trình & Phân công") {
                CrmSelectRow("Trạng thái", tr(stageLabel(stages, status)), enabled = canEdit && !busy) { showStatusSheet = true }
                CrmSelectRow("Độ ưu tiên", tr(priorityLabel(priority)), enabled = canEdit && !busy) { showPrioritySheet = true }
                FutaFormSectionField(label = "Người phụ trách") {
                    if (assigned.isEmpty()) {
                        Text("Chưa giao", fontSize = 13.sp, color = FutaColors.Slate)
                    } else {
                        VerbatimText(assigned.joinToString(", ") { staffName(staff, it) }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                    }
                    if (canAssign) {
                        FutaButton(
                            text = "Phân công nhân viên",
                            icon = Icons.Default.PersonAdd,
                            variant = FutaButtonVariant.OUTLINE,
                            height = 38.dp,
                            enabled = !busy,
                            onClick = { showAssignSheet = true },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            if (canEdit) {
                AdvisorSection(header = "Ghi nhận chăm sóc (Care Event)") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        crmEventTypes.forEach { (key, label) ->
                            val sel = eventType == key
                            Surface(
                                shape = CircleShape,
                                color = if (sel) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { eventType = key }
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else FutaColors.Navy, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                    FutaTextField(value = eventTitle, onValueChange = { eventTitle = it }, placeholder = "Tiêu đề chăm sóc (ví dụ: Đã gọi tư vấn căn 2PN)")
                    FutaTextArea(value = eventDesc, onValueChange = { eventDesc = it }, placeholder = "Nội dung chi tiết...", minLines = 2, modifier = Modifier.fillMaxWidth())
                    FutaButton(
                        text = if (savingEvent) "Đang lưu..." else "Thêm hoạt động chăm sóc",
                        enabled = eventTitle.isNotBlank() && !savingEvent,
                        onClick = {
                            scope.launch {
                                savingEvent = true
                                try {
                                    val body = buildJsonObject {
                                        put("type", eventType)
                                        put("title", eventTitle.trim())
                                        if (eventDesc.isNotBlank()) put("description", eventDesc.trim())
                                    }
                                    val res = APIClient.get().request("${leadPath(groupId, leadId)}/events", method = "POST", bodyJson = body.toString())
                                    if (!res["data"].isNull) lead = res["data"]
                                    eventTitle = ""
                                    eventDesc = ""
                                    ToastCenter.show(tr("Đã ghi nhận chăm sóc"))
                                    onChanged()
                                } catch (e: Exception) {
                                    ToastCenter.show(e.message ?: tr("Không lưu được"), isError = true)
                                } finally {
                                    savingEvent = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AdvisorSection(header = "Ghi chú nhanh") {
                    FutaTextArea(value = noteText, onValueChange = { noteText = it }, placeholder = "Thêm ghi chú...", minLines = 2, modifier = Modifier.fillMaxWidth())
                    FutaButton(
                        text = if (savingNote) "Đang lưu..." else "Lưu ghi chú",
                        variant = FutaButtonVariant.MINT,
                        enabled = noteText.isNotBlank() && !savingNote,
                        onClick = {
                            scope.launch {
                                savingNote = true
                                try {
                                    val body = buildJsonObject { put("content", noteText.trim()) }
                                    val res = APIClient.get().request("${leadPath(groupId, leadId)}/notes", method = "POST", bodyJson = body.toString())
                                    if (!res["data"].isNull) lead = res["data"]
                                    noteText = ""
                                    ToastCenter.show(tr("Đã lưu ghi chú"))
                                    onChanged()
                                } catch (e: Exception) {
                                    ToastCenter.show(e.message ?: tr("Không lưu được"), isError = true)
                                } finally {
                                    savingNote = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val notes = lead["notes"].array
            if (notes.isNotEmpty()) {
                AdvisorSection(header = tr("Ghi chú ({0})", notes.size)) {
                    notes.forEachIndexed { idx, note ->
                        if (idx > 0) HorizontalDivider(color = Color(0xFFF1F5F9))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            VerbatimText(note["content"].string, fontSize = 13.5.sp, color = FutaColors.Navy)
                            Row {
                                if (note["authorName"].string.isEmpty()) {
                                    Text("Hệ thống", fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                                } else {
                                    VerbatimText(note["authorName"].string, fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                                }
                                VerbatimText(shortDate(note["createdAt"].string), fontSize = 11.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }
            }

            val history = lead["careHistory"].array
            AdvisorSection(header = tr("Lịch sử tương tác ({0})", history.size)) {
                if (history.isEmpty()) {
                    Text("Chưa có lịch sử tương tác", fontSize = 12.5.sp, color = FutaColors.Slate)
                }
                history.forEachIndexed { idx, event ->
                    if (idx > 0) HorizontalDivider(color = Color(0xFFF1F5F9))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            when (event["type"].string) {
                                "call" -> Icons.Default.Phone
                                "meeting" -> Icons.Default.Groups
                                "status" -> Icons.Default.SwapHoriz
                                "info" -> Icons.Default.Info
                                else -> Icons.Default.StickyNote2
                            },
                            null,
                            tint = FutaColors.BrandGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            VerbatimText(event["title"].string, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                            if (event["description"].string.isNotEmpty()) {
                                VerbatimText(event["description"].string, fontSize = 12.5.sp, color = FutaColors.Slate)
                            }
                            Row {
                                VerbatimText(event["authorName"].string, fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                                VerbatimText(shortDate(event["createdAt"].string), fontSize = 11.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }
            }

            if (canDelete) {
                CrmDangerZone(buttonText = if (deleting) "Đang xoá..." else "Xoá khách hàng này", enabled = !deleting) { confirmDelete = true }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (editing) {
            CrmLeadFormPage(
                groupId = groupId,
                lead = lead,
                stages = stages,
                staff = staff,
                canAssign = false,
                onBack = { editing = false },
                onSaved = { updated ->
                    if (updated != null && !updated.isNull) lead = updated
                    editing = false
                    onChanged()
                }
            )
        }
    }

    CrmOptionSheet(
        visible = showStatusSheet,
        title = "Trạng thái",
        options = stages.map { it["id"].string },
        selected = status,
        label = { stageLabel(stages, it) },
        onSelect = { if (it != status) pendingStatus = it },
        onDismiss = { showStatusSheet = false }
    )
    CrmConfirmDialog(
        visible = pendingStatus != null,
        title = "Đổi trạng thái?",
        message = tr("Chuyển khách hàng sang \"{0}\"?", tr(stageLabel(stages, pendingStatus.orEmpty()))),
        confirmText = "Xác nhận",
        onConfirm = {
            val next = pendingStatus ?: return@CrmConfirmDialog
            update(buildJsonObject { put("status", next) }, "Đã cập nhật trạng thái")
        },
        onDismiss = { pendingStatus = null }
    )
    CrmOptionSheet(
        visible = showPrioritySheet,
        title = "Độ ưu tiên",
        options = crmPriorities.map { it.first },
        selected = priority,
        label = { priorityLabel(it) },
        onSelect = { if (it != priority) update(buildJsonObject { put("priority", it) }, "Đã cập nhật độ ưu tiên") },
        onDismiss = { showPrioritySheet = false }
    )
    CrmMultiSelectSheet(
        visible = showAssignSheet,
        title = "Phân công nhân viên",
        options = staffOptions(staff),
        selected = assigned.toSet(),
        onApply = { ids ->
            if (ids != assigned.toSet()) {
                update(buildJsonObject { putJsonArray("assignedSaleIds") { ids.forEach { add(it) } } }, "Đã cập nhật phân công")
            }
        },
        onDismiss = { showAssignSheet = false }
    )
    CrmConfirmDialog(
        visible = confirmDelete,
        title = "Xoá khách hàng tiềm năng?",
        message = "Khách hàng này cùng ghi chú và lịch sử chăm sóc sẽ bị xoá vĩnh viễn.",
        confirmText = "Xoá vĩnh viễn",
        destructive = true,
        onConfirm = {
            scope.launch {
                deleting = true
                try {
                    APIClient.get().request(leadPath(groupId, leadId), method = "DELETE")
                    ToastCenter.show(tr("Đã xoá khách hàng"))
                    onDeleted()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không xoá được"), isError = true)
                } finally {
                    deleting = false
                }
            }
        },
        onDismiss = { confirmDelete = false }
    )
}

// MARK: - Lead create / edit

@Composable
private fun CrmLeadFormPage(
    groupId: String,
    lead: JSONValue?,
    stages: List<JSONValue>,
    staff: List<JSONValue>,
    canAssign: Boolean,
    onBack: () -> Unit,
    onSaved: (JSONValue?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEdit = lead != null
    val initialName = lead?.get("customerName")?.string.orEmpty()
    val initialPhone = lead?.get("customerPhone")?.string.orEmpty()
    val initialEmail = lead?.get("email")?.string.orEmpty()
    val initialSource = lead?.get("source")?.string.orEmpty()
    val initialDemand = lead?.get("demand")?.string.orEmpty()
    val initialBudget = lead?.get("budget")?.string.orEmpty()
    val initialArea = lead?.get("area")?.string.orEmpty()
    val initialNote = lead?.get("note")?.string.orEmpty()
    val defaultStatus = stages.firstOrNull()?.get("id")?.string ?: "new"

    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var email by remember { mutableStateOf(initialEmail) }
    var source by remember { mutableStateOf(initialSource) }
    var demand by remember { mutableStateOf(initialDemand) }
    var budget by remember { mutableStateOf(initialBudget) }
    var area by remember { mutableStateOf(initialArea) }
    var note by remember { mutableStateOf(initialNote) }
    var status by remember { mutableStateOf(defaultStatus) }
    var priority by remember { mutableStateOf("medium") }
    var assigned by remember { mutableStateOf<Set<String>>(emptySet()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var showPrioritySheet by remember { mutableStateOf(false) }
    var showAssignSheet by remember { mutableStateOf(false) }

    val dirty = name != initialName || phone != initialPhone || email != initialEmail || source != initialSource ||
        demand != initialDemand || budget != initialBudget || area != initialArea || note != initialNote ||
        (!isEdit && (status != defaultStatus || priority != "medium" || assigned.isNotEmpty()))

    fun requestBack() { if (dirty && !saving) showDiscard = true else onBack() }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (name.isBlank()) e["name"] = tr("Tên khách hàng không được để trống")
        val p = phone.trim()
        if (p.isEmpty()) e["phone"] = tr("Số điện thoại không được để trống")
        else if (p.length < 7 || p.length > 20) e["phone"] = tr("Số điện thoại không hợp lệ")
        if (email.isNotBlank() && !crmIsValidEmail(email)) e["email"] = tr("Email không hợp lệ")
        errors = e
        if (e.isNotEmpty()) ToastCenter.show(e.values.first(), isError = true)
        return e.isEmpty()
    }

    fun submit() {
        if (saving || !validate()) return
        scope.launch {
            saving = true
            try {
                if (lead != null) {
                    val body = buildJsonObject {
                        put("customerName", name.trim())
                        put("customerPhone", phone.trim())
                        put("email", email.trim())
                        if (source.isNotBlank()) put("source", source.trim())
                        put("demand", demand.trim())
                        put("budget", budget.trim())
                        put("area", area.trim())
                        put("note", note.trim())
                    }
                    val res = APIClient.get().request(leadPath(groupId, lead.id), method = "PUT", bodyJson = body.toString())
                    ToastCenter.show(tr("Đã cập nhật thông tin"))
                    onSaved(res["data"])
                } else {
                    val body = buildJsonObject {
                        putJsonArray("leads") {
                            add(buildJsonObject {
                                put("customerName", name.trim())
                                put("customerPhone", phone.trim())
                                if (email.isNotBlank()) put("email", email.trim())
                                if (source.isNotBlank()) put("source", source.trim())
                                if (demand.isNotBlank()) put("demand", demand.trim())
                                if (budget.isNotBlank()) put("budget", budget.trim())
                                if (area.isNotBlank()) put("area", area.trim())
                                if (note.isNotBlank()) put("note", note.trim())
                                put("status", status)
                                put("priority", priority)
                                if (assigned.isNotEmpty()) putJsonArray("assignedSaleIds") { assigned.forEach { add(it) } }
                                put("importedFrom", "manual")
                            })
                        }
                    }
                    val res = APIClient.get().request("/crm/groups/${crmEnc(groupId)}/leads/bulk", method = "POST", bodyJson = body.toString())
                    val data = res["data"]
                    if (data["added"].int == 0 && data["skippedRows"].array.isNotEmpty()) {
                        ToastCenter.show(tr(skippedReason(data["skippedRows"][0]["reason"].string)), isError = true)
                    } else {
                        ToastCenter.show(tr("Đã thêm khách hàng tiềm năng"))
                        onSaved(data["leads"][0])
                    }
                }
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không lưu được"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    CrmFullScreenPage(
        title = if (isEdit) tr("Chỉnh sửa thông tin") else tr("Thêm khách hàng"),
        onBack = ::requestBack,
        bottomBar = {
            CrmFormActionBar(
                submitText = if (isEdit) "Cập nhật" else "Lưu",
                submitting = saving,
                onCancel = ::requestBack,
                onSubmit = ::submit
            )
        }
    ) {
        AdvisorSection(header = if (isEdit) "Thông tin cơ bản" else "Thông tin bắt buộc") {
            CrmTextField("Họ và tên", name, { name = it; errors = errors - "name" }, "Họ và tên khách hàng", required = true, error = errors["name"])
            CrmTextField("Số điện thoại", phone, { phone = it; errors = errors - "phone" }, "0901 234 567", required = true, error = errors["phone"], keyboardType = KeyboardType.Phone)
            CrmTextField("Email", email, { email = it; errors = errors - "email" }, "email@example.com", error = errors["email"], keyboardType = KeyboardType.Email)
        }
        AdvisorSection(header = "Nhu cầu & Nguồn") {
            CrmTextField("Nguồn khách hàng", source, { source = it }, "Facebook, Zalo, giới thiệu...")
            CrmTextField("Nhu cầu", demand, { demand = it }, "Nhu cầu (Loại BĐS, mục đích...)", multiline = true)
            CrmTextField("Ngân sách dự kiến", budget, { budget = it }, "Ví dụ: 3 - 4 tỷ")
            CrmTextField("Khu vực quan tâm", area, { area = it }, "Quận, dự án...")
            CrmTextField("Ghi chú", note, { note = it }, "Ghi chú ban đầu", multiline = true)
        }
        if (!isEdit) {
            AdvisorSection(header = "Phân loại") {
                CrmSelectRow("Trạng thái", tr(stageLabel(stages, status))) { showStatusSheet = true }
                CrmSelectRow("Độ ưu tiên", tr(priorityLabel(priority))) { showPrioritySheet = true }
                if (canAssign && staff.isNotEmpty()) {
                    CrmSelectRow(
                        "Người phụ trách",
                        if (assigned.isEmpty()) "" else assigned.joinToString(", ") { staffName(staff, it) },
                        placeholder = "Tự động phân bổ theo nhóm"
                    ) { showAssignSheet = true }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    CrmOptionSheet(showStatusSheet, "Trạng thái", stages.map { it["id"].string }, status, { stageLabel(stages, it) }, { status = it }, { showStatusSheet = false })
    CrmOptionSheet(showPrioritySheet, "Độ ưu tiên", crmPriorities.map { it.first }, priority, { priorityLabel(it) }, { priority = it }, { showPrioritySheet = false })
    CrmMultiSelectSheet(showAssignSheet, "Người phụ trách", staffOptions(staff), assigned, { assigned = it }, { showAssignSheet = false })
    CrmDiscardDialog(visible = showDiscard, onDiscard = onBack, onDismiss = { showDiscard = false })
}

// MARK: - Bulk add (POST /crm/groups/:id/leads/bulk with many rows)

private data class CrmDraftRow(val name: String, val phone: String, val demand: String, val budget: String, val source: String)

private fun parseBulkRows(text: String): Pair<List<CrmDraftRow>, Int> {
    var invalid = 0
    val rows = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@mapNotNull null
        val parts = line.split('\t', ',', ';').map { it.trim() }
        val name = parts.getOrNull(0).orEmpty()
        val phone = parts.getOrNull(1).orEmpty()
        if (name.isEmpty() || phone.length !in 7..20) {
            invalid++
            null
        } else {
            CrmDraftRow(name, phone, parts.getOrNull(2).orEmpty(), parts.getOrNull(3).orEmpty(), parts.getOrNull(4).orEmpty())
        }
    }
    return rows to invalid
}

@Composable
private fun CrmBulkAddPage(groupId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val (rows, invalid) = remember(text) { parseBulkRows(text) }

    fun requestBack() { if (text.isNotBlank() && !saving) showDiscard = true else onBack() }

    fun submit() {
        if (rows.isEmpty() || saving) return
        scope.launch {
            saving = true
            try {
                var added = 0
                var skipped = 0
                rows.chunked(500).forEach { chunk ->
                    val body = buildJsonObject {
                        putJsonArray("leads") {
                            chunk.forEach { r ->
                                add(buildJsonObject {
                                    put("customerName", r.name)
                                    put("customerPhone", r.phone)
                                    if (r.demand.isNotEmpty()) put("demand", r.demand)
                                    if (r.budget.isNotEmpty()) put("budget", r.budget)
                                    if (r.source.isNotEmpty()) put("source", r.source)
                                    put("importedFrom", "manual")
                                })
                            }
                        }
                    }
                    val data = APIClient.get().request("/crm/groups/${crmEnc(groupId)}/leads/bulk", method = "POST", bodyJson = body.toString())["data"]
                    added += data["added"].int
                    skipped += data["skipped"].int
                }
                ToastCenter.show(tr("Đã thêm {0} khách hàng, bỏ qua {1}", added, skipped))
                onDone()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không lưu được"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    CrmFullScreenPage(
        title = tr("Thêm nhiều khách hàng"),
        onBack = ::requestBack,
        bottomBar = {
            CrmFormActionBar(
                submitText = tr("Thêm {0} khách hàng", rows.size),
                submitting = saving,
                submitEnabled = rows.isNotEmpty(),
                onCancel = ::requestBack,
                onSubmit = ::submit
            )
        }
    ) {
        AdvisorSection(
            header = "Danh sách khách hàng",
            footer = "Mỗi dòng một khách hàng: Họ tên, Số điện thoại, Nhu cầu, Ngân sách, Nguồn (phân cách bằng dấu phẩy hoặc tab). Có thể dán trực tiếp từ Excel."
        ) {
            FutaTextArea(value = text, onValueChange = { text = it }, placeholder = "Nguyễn Văn A, 0901234567, Căn 2PN, 3 tỷ, Facebook", minLines = 8, maxLines = 16, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdvisorBadge(text = tr("{0} dòng hợp lệ", rows.size), color = FutaColors.BrandGreen)
                if (invalid > 0) AdvisorBadge(text = tr("{0} dòng lỗi", invalid), color = Color(0xFFDC2626))
            }
        }
        if (rows.isNotEmpty()) {
            AdvisorSection(header = "Xem trước") {
                rows.take(5).forEach { r -> AdvisorLabeledRow(r.name, r.phone) }
                if (rows.size > 5) Text(tr("và {0} khách hàng khác", rows.size - 5), fontSize = 12.sp, color = FutaColors.Slate)
            }
        }
    }
    CrmDiscardDialog(visible = showDiscard, onDiscard = onBack, onDismiss = { showDiscard = false })
}

// MARK: - Group create / edit

@Composable
private fun CrmGroupFormPage(group: JSONValue?, staff: List<JSONValue>, onBack: () -> Unit, onSaved: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val initialName = group?.get("name")?.string.orEmpty()
    val initialDesc = group?.get("description")?.string.orEmpty()
    val initialColor = group?.get("color")?.string?.uppercase()?.ifEmpty { null } ?: crmGroupColors[1]
    val initialMode = group?.get("distributionMode")?.string?.ifEmpty { null } ?: "round_robin"
    val initialStaff = group?.get("assignedSaleIds")?.array?.map { it.string }?.toSet().orEmpty()
    val initialAuto = group?.get("autoDistribute")?.let { if (it.isNull) true else it.bool } ?: true

    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDesc) }
    var color by remember { mutableStateOf(initialColor) }
    var mode by remember { mutableStateOf(initialMode) }
    var assigned by remember { mutableStateOf(initialStaff) }
    var autoDistribute by remember { mutableStateOf(initialAuto) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    var showStaffSheet by remember { mutableStateOf(false) }

    val dirty = name != initialName || description != initialDesc || color != initialColor || mode != initialMode ||
        assigned != initialStaff || autoDistribute != initialAuto
    fun requestBack() { if (dirty && !saving) showDiscard = true else onBack() }

    fun submit() {
        if (saving) return
        if (name.isBlank()) {
            nameError = tr("Tên nhóm không được để trống")
            ToastCenter.show(nameError ?: "", isError = true)
            return
        }
        scope.launch {
            saving = true
            try {
                val body = buildJsonObject {
                    put("name", name.trim())
                    put("description", description.trim())
                    put("color", color)
                    put("distributionMode", mode)
                    putJsonArray("assignedSaleIds") { assigned.forEach { add(it) } }
                    put("autoDistribute", autoDistribute)
                }
                if (group != null) {
                    APIClient.get().request("/crm/groups/${crmEnc(group.id)}", method = "PUT", bodyJson = body.toString())
                    ToastCenter.show(tr("Đã cập nhật nhóm"))
                    onSaved(null)
                } else {
                    val res = APIClient.get().request("/crm/groups", method = "POST", bodyJson = body.toString())
                    ToastCenter.show(tr("Đã tạo nhóm mới"))
                    onSaved(res["data"].id.ifEmpty { null })
                }
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không lưu được"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    CrmFullScreenPage(
        title = if (group == null) tr("Tạo nhóm khách hàng") else tr("Cấu hình nhóm"),
        onBack = ::requestBack,
        bottomBar = {
            CrmFormActionBar(
                submitText = if (group == null) "Tạo nhóm" else "Lưu",
                submitting = saving,
                onCancel = ::requestBack,
                onSubmit = ::submit
            )
        }
    ) {
        AdvisorSection(header = "Thông tin nhóm") {
            CrmTextField("Tên nhóm khách hàng", name, { name = it; nameError = null }, "Ví dụ: Khách quan tâm dự án A", required = true, error = nameError)
            CrmTextField("Mô tả nhóm", description, { description = it }, "Mô tả ngắn", multiline = true)
            FutaFormSectionField(label = "Màu nhận diện") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    crmGroupColors.forEach { hex ->
                        val sel = hex.equals(color, ignoreCase = true)
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(crmHexColor(hex), CircleShape)
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        AdvisorSection(
            header = "Chế độ phân bổ leads",
            footer = "Leads mới được giao tự động cho nhân sự trong nhóm theo chế độ đã chọn."
        ) {
            CrmSelectRow("Phân bổ tự động", tr(crmDistributionModes.firstOrNull { it.first == mode }?.second ?: mode)) { showModeSheet = true }
            CrmSelectRow(
                "Nhân sự trong nhóm",
                if (assigned.isEmpty()) "" else assigned.joinToString(", ") { staffName(staff, it) },
                placeholder = "Chưa chọn nhân sự"
            ) { showStaffSheet = true }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tự động phân bổ lead mới", fontSize = 13.5.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                FutaSwitch(checked = autoDistribute, onCheckedChange = { autoDistribute = it })
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    CrmOptionSheet(showModeSheet, "Phân bổ tự động", crmDistributionModes.map { it.first }, mode, { k -> crmDistributionModes.first { it.first == k }.second }, { mode = it }, { showModeSheet = false })
    CrmMultiSelectSheet(showStaffSheet, "Nhân sự trong nhóm", staffOptions(staff), assigned, { assigned = it }, { showStaffSheet = false })
    CrmDiscardDialog(visible = showDiscard, onDiscard = onBack, onDismiss = { showDiscard = false })
}

// MARK: - Redistribute

@Composable
private fun CrmRedistributePage(groupId: String, group: JSONValue?, staff: List<JSONValue>, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var redistributeScope by remember { mutableStateOf("unassigned") }
    var processing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val members = group?.get("assignedSaleIds")?.array?.map { it.string }.orEmpty()
    val options = listOf("unassigned" to "Chỉ leads chưa giao", "all" to "Toàn bộ leads trong nhóm")

    CrmFullScreenPage(
        title = tr("Phân bổ lại leads"),
        onBack = onBack,
        bottomBar = {
            CrmFormActionBar(
                submitText = "Thực hiện",
                submitting = processing,
                submitEnabled = members.isNotEmpty(),
                onCancel = onBack,
                onSubmit = { confirm = true }
            )
        }
    ) {
        AdvisorSection(header = "Phạm vi phân bổ lại") {
            options.forEach { (key, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { redistributeScope = key }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = redistributeScope == key, onClick = { redistributeScope = key }, colors = RadioButtonDefaults.colors(selectedColor = FutaColors.BrandGreen))
                    Text(label, fontSize = 14.sp, color = FutaColors.Navy)
                }
            }
        }
        AdvisorSection(
            header = "Nhân sự nhận leads",
            footer = "Hệ thống sẽ dựa vào danh sách tư vấn viên trong nhóm và cơ chế phân bổ đã cấu hình để giao lại khách hàng một cách công bằng."
        ) {
            if (members.isEmpty()) {
                Text("Nhóm chưa có nhân sự. Hãy cấu hình nhóm trước khi phân bổ.", fontSize = 13.sp, color = Color(0xFFDC2626))
            } else {
                VerbatimText(members.joinToString(", ") { staffName(staff, it) }, fontSize = 13.5.sp, color = FutaColors.Navy)
                val mode = group?.get("distributionMode")?.string.orEmpty()
                AdvisorLabeledRow(tr("Chế độ"), tr(crmDistributionModes.firstOrNull { it.first == mode }?.second ?: mode))
            }
        }
    }

    CrmConfirmDialog(
        visible = confirm,
        title = "Phân bổ lại leads?",
        message = if (redistributeScope == "all") "Toàn bộ leads trong nhóm sẽ được giao lại cho nhân sự." else "Các leads chưa giao sẽ được giao cho nhân sự trong nhóm.",
        confirmText = "Thực hiện",
        onConfirm = {
            scope.launch {
                processing = true
                try {
                    val body = buildJsonObject { put("scope", redistributeScope) }
                    val data = APIClient.get().request("/crm/groups/${crmEnc(groupId)}/leads/redistribute", method = "POST", bodyJson = body.toString())["data"]
                    ToastCenter.show(tr("Đã phân bổ lại {0} khách hàng", data["updated"].int))
                    onDone()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thực hiện được"), isError = true)
                } finally {
                    processing = false
                }
            }
        },
        onDismiss = { confirm = false }
    )
}

// MARK: - Bulk actions (PATCH /crm/groups/:id/leads/bulk)

@Composable
private fun CrmBulkActionsPage(
    groupId: String,
    leadIds: List<String>,
    stages: List<JSONValue>,
    staff: List<JSONValue>,
    canAssign: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val actions = buildList {
        add("status" to "Đổi trạng thái")
        if (canAssign) add("assign" to "Phân công nhân viên")
        if (canDelete) add("delete" to "Xoá khỏi nhóm")
    }
    var action by remember { mutableStateOf("status") }
    var newStatus by remember { mutableStateOf(stages.firstOrNull()?.get("id")?.string.orEmpty()) }
    var assignees by remember { mutableStateOf<Set<String>>(emptySet()) }
    var processing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var showStaffSheet by remember { mutableStateOf(false) }

    val ready = leadIds.isNotEmpty() && when (action) {
        "status" -> newStatus.isNotEmpty()
        "assign" -> assignees.isNotEmpty()
        else -> true
    }

    CrmFullScreenPage(
        title = tr("Thao tác hàng loạt"),
        onBack = onBack,
        bottomBar = {
            CrmFormActionBar(
                submitText = "Xác nhận",
                submitting = processing,
                submitEnabled = ready,
                submitVariant = if (action == "delete") FutaButtonVariant.DANGER else FutaButtonVariant.PRIMARY,
                onCancel = onBack,
                onSubmit = { confirm = true }
            )
        }
    ) {
        AdvisorSection(header = "Khách hàng được chọn") {
            Text(tr("Số lượng: {0} khách hàng", leadIds.size), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
        }
        AdvisorSection(header = "Chọn hành động") {
            actions.forEach { (key, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { action = key }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = action == key,
                        onClick = { action = key },
                        colors = RadioButtonDefaults.colors(selectedColor = if (key == "delete") Color(0xFFDC2626) else FutaColors.BrandGreen)
                    )
                    Text(label, fontSize = 14.sp, color = if (key == "delete") Color(0xFFDC2626) else FutaColors.Navy)
                }
            }
        }
        when (action) {
            "status" -> AdvisorSection(header = "Trạng thái mới") {
                CrmSelectRow("Trạng thái", tr(stageLabel(stages, newStatus))) { showStatusSheet = true }
            }
            "assign" -> AdvisorSection(header = "Tư vấn viên") {
                CrmSelectRow(
                    "Chọn nhân viên",
                    if (assignees.isEmpty()) "" else assignees.joinToString(", ") { staffName(staff, it) },
                    placeholder = "Chưa chọn"
                ) { showStaffSheet = true }
            }
            "delete" -> Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFEF2F2), border = BorderStroke(1.dp, Color(0xFFFECACA))) {
                Text(
                    tr("Cảnh báo: Toàn bộ {0} khách hàng được chọn sẽ bị xoá vĩnh viễn khỏi nhóm.", leadIds.size),
                    fontSize = 13.sp,
                    color = Color(0xFFB91C1C),
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }

    CrmOptionSheet(showStatusSheet, "Trạng thái", stages.map { it["id"].string }, newStatus, { stageLabel(stages, it) }, { newStatus = it }, { showStatusSheet = false })
    CrmMultiSelectSheet(showStaffSheet, "Chọn nhân viên", staffOptions(staff), assignees, { assignees = it }, { showStaffSheet = false })
    CrmConfirmDialog(
        visible = confirm,
        title = if (action == "delete") "Xoá khách hàng đã chọn?" else "Xác nhận thao tác hàng loạt?",
        message = when (action) {
            "delete" -> tr("{0} khách hàng sẽ bị xoá vĩnh viễn.", leadIds.size)
            "assign" -> tr("Giao {0} khách hàng cho nhân viên đã chọn?", leadIds.size)
            else -> tr("Chuyển {0} khách hàng sang \"{1}\"?", leadIds.size, tr(stageLabel(stages, newStatus)))
        },
        confirmText = if (action == "delete") "Xoá vĩnh viễn" else "Xác nhận",
        destructive = action == "delete",
        onConfirm = {
            scope.launch {
                processing = true
                try {
                    val body = buildJsonObject {
                        put("action", action)
                        putJsonArray("leadIds") { leadIds.forEach { add(it) } }
                        if (action == "status") put("status", newStatus)
                        if (action == "assign") putJsonArray("assignedSaleIds") { assignees.forEach { add(it) } }
                    }
                    val data = APIClient.get().request("/crm/groups/${crmEnc(groupId)}/leads/bulk", method = "PATCH", bodyJson = body.toString())["data"]
                    ToastCenter.show(tr("Đã cập nhật {0} khách hàng", data["updated"].int))
                    onDone()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thực hiện được"), isError = true)
                } finally {
                    processing = false
                }
            }
        },
        onDismiss = { confirm = false }
    )
}

// MARK: - Pipeline stages

@Composable
private fun CrmPipelinePage(stages: List<JSONValue>, canReset: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var resetting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    CrmFullScreenPage(title = tr("Quy trình bán hàng"), onBack = onBack) {
        AdvisorSection(header = "Các giai đoạn trong phễu bán hàng") {
            if (stages.isEmpty()) Text("Chưa có giai đoạn", fontSize = 13.sp, color = FutaColors.Slate)
            stages.forEachIndexed { idx, s ->
                if (idx > 0) HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).background(stageColor(stages, s["id"].string), CircleShape))
                    Text(s["label"].string.ifEmpty { s["id"].string }, fontSize = 14.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                    VerbatimText(s["id"].string, fontSize = 11.sp, color = FutaColors.Slate, fontWeight = FontWeight.Normal)
                }
            }
        }
        if (canReset) {
            AdvisorSection(footer = "Thiết lập lại phễu theo chuẩn: Mới → Đã phân bổ → Đã liên hệ → Quan tâm → Đã hẹn xem → Đàm phán → Chốt / Rớt.") {
                FutaButton(
                    text = if (resetting) "Đang xử lý..." else "Khôi phục cấu hình phễu mặc định",
                    variant = FutaButtonVariant.DANGER,
                    icon = Icons.Default.RestartAlt,
                    enabled = !resetting,
                    onClick = { confirm = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    CrmConfirmDialog(
        visible = confirm,
        title = "Khôi phục quy trình mặc định?",
        message = "Các giai đoạn tuỳ chỉnh sẽ được thay bằng phễu mặc định.",
        confirmText = "Khôi phục",
        destructive = true,
        onConfirm = {
            scope.launch {
                resetting = true
                try {
                    APIClient.get().request("/crm/pipeline/reset", method = "POST")
                    ToastCenter.show(tr("Đã khôi phục quy trình mặc định"))
                    onDone()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thực hiện được"), isError = true)
                } finally {
                    resetting = false
                }
            }
        },
        onDismiss = { confirm = false }
    )
}

// MARK: - Legacy import (POST /crm/legacy-import)

@Composable
private fun CrmLegacyImportPage(onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var json by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }

    fun requestBack() { if (json.isNotBlank() && !importing) showDiscard = true else onBack() }

    fun submit() {
        if (importing) return
        val parsed = runCatching { Json.parseToJsonElement(json.trim()).jsonObject }.getOrNull()
        val groups = parsed?.get("groups")?.let { runCatching { it.jsonArray }.getOrNull() }
        if (parsed == null || groups.isNullOrEmpty()) {
            parseError = tr("JSON không hợp lệ hoặc thiếu mảng 'groups'")
            ToastCenter.show(parseError ?: "", isError = true)
            return
        }
        scope.launch {
            importing = true
            try {
                val data = APIClient.get().request("/crm/legacy-import", method = "POST", bodyJson = parsed.toString())["data"]
                ToastCenter.show(tr("Đã nhập {0} nhóm và {1} khách hàng", data["groupsCreated"].int, data["leadsAdded"].int))
                onDone()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không nhập được dữ liệu"), isError = true)
            } finally {
                importing = false
            }
        }
    }

    CrmFullScreenPage(
        title = tr("Nhập dữ liệu lưu trữ"),
        onBack = ::requestBack,
        bottomBar = {
            CrmFormActionBar(
                submitText = "Tiến hành",
                submitting = importing,
                submitEnabled = json.isNotBlank(),
                onCancel = ::requestBack,
                onSubmit = ::submit
            )
        }
    ) {
        AdvisorSection(header = "Dữ liệu CRM JSON", footer = "Dán nội dung sao lưu CRM (phải chứa mảng 'groups' gồm các nhóm và leads):") {
            OutlinedTextField(
                value = json,
                onValueChange = { json = it; parseError = null },
                minLines = 10,
                maxLines = 20,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = FutaColors.Navy),
                isError = parseError != null,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FutaColors.BrandGreen, unfocusedBorderColor = FutaColors.LightBlueBorder)
            )
            parseError?.let { Text(it, fontSize = 11.5.sp, color = Color(0xFFDC2626)) }
            FutaButton(
                text = "Sử dụng mẫu JSON nhóm mẫu",
                variant = FutaButtonVariant.OUTLINE,
                icon = Icons.Default.Description,
                onClick = {
                    val template = buildJsonObject {
                        putJsonArray("groups") {
                            add(buildJsonObject {
                                put("id", "group-import-" + java.util.UUID.randomUUID().toString().take(8))
                                put("name", "Nhóm nhập khẩu")
                                put("description", "Nhóm khách hàng đồng bộ")
                                put("color", "#10B981")
                                put("distributionMode", "round_robin")
                                put("leads", JsonArray(emptyList()))
                            })
                        }
                    }
                    json = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), template)
                    parseError = null
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    CrmDiscardDialog(visible = showDiscard, onDiscard = onBack, onDismiss = { showDiscard = false })
}

