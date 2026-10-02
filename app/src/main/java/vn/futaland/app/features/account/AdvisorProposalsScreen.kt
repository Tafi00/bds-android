package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

/** Category values sent to the backend, with the labels iOS shows for them. */
private val proposalCategories = listOf(
    "Sản phẩm" to "Sản phẩm",
    "Chính sách" to "Chính sách bán hàng",
    "Đào tạo" to "Đào tạo & Kỹ năng",
    "Hệ thống" to "Hệ thống phần mềm",
    "Khác" to "Khác"
)

private fun proposalStatus(status: String): Pair<String, Color> = when (status) {
    "submitted" -> "Đã gửi" to Color(0xFF2563EB)
    "reviewing" -> "Đang xem xét" to Color(0xFFF97316)
    "approved" -> "Đã duyệt" to FutaColors.BrandGreen
    "rejected" -> "Từ chối" to Color(0xFFDC2626)
    else -> "Bản nháp" to FutaColors.Slate
}

/**
 * "Đề xuất của tôi" (iOS `AdvisorProposalsView`): GET/POST /advisor/proposals,
 * PUT/DELETE /advisor/proposals/:id; the admin reply is shown in the detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvisorProposalsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var proposals by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONValue?>(null) }

    suspend fun load() {
        try {
            proposals = APIClient.get().request("/advisor/proposals")["data"].array
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không tải được dữ liệu")
            ToastCenter.show(loadError.orEmpty(), isError = true)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    val filtered = proposals.filter { selectedStatus.isEmpty() || it["status"].string == selectedStatus }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            AdvisorTopBar("Đề xuất của tôi", onBack) {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, tr("Tạo đề xuất mới"), tint = FutaColors.BrandGreen)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("" to "Tất cả", "submitted" to "Đã gửi", "reviewing" to "Đang xem xét", "approved" to "Đã duyệt", "rejected" to "Từ chối")
                    .forEach { (key, label) -> ChoiceChip(tr(label), selectedStatus == key) { selectedStatus = key } }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch { load(); refreshing = false }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    loading && proposals.isEmpty() -> AdvisorLoadingState()
                    loadError != null && proposals.isEmpty() -> AdvisorErrorState(loadError.orEmpty(), onRetry = { loading = true; scope.launch { load() } })
                    proposals.isEmpty() -> FutaEmptyState(
                        title = "Không có đề xuất",
                        message = "Gửi ý kiến đóng góp hoặc đề xuất kinh doanh mới tới quản trị viên.",
                        icon = Icons.Default.Lightbulb,
                        actionButton = { FutaButton(text = "Tạo đề xuất mới", icon = Icons.Default.Add, onClick = { showCreate = true }) }
                    )
                    filtered.isEmpty() -> FutaEmptyState(
                        title = "Không có đề xuất phù hợp",
                        message = "Không có đề xuất nào ở trạng thái đã chọn.",
                        icon = Icons.Default.FilterList,
                        actionButton = { FutaButton(text = "Xoá bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = { selectedStatus = "" }) }
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { prop -> ProposalRowCard(prop) { selected = prop } }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    if (showCreate) {
        ProposalEditorSheet(
            proposal = null,
            onDismiss = { showCreate = false },
            onSaved = {
                showCreate = false
                load()
            }
        )
    }

    selected?.let { proposal ->
        ProposalDetailSheet(
            proposal = proposal,
            onDismiss = { selected = null },
            onChanged = { load() }
        )
    }
}

@Composable
private fun ProposalRowCard(item: JSONValue, onClick: () -> Unit) {
    FutaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                VerbatimText(item["title"].string, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                val (label, color) = proposalStatus(item["status"].string)
                AdvisorBadge(tr(label), color)
            }
            if (item["description"].string.isNotEmpty()) {
                VerbatimText(item["description"].string, fontSize = 13.sp, color = FutaColors.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = FutaColors.Slate, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(item["category"].string, fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                if (item["adminReply"].string.isNotEmpty()) {
                    Icon(Icons.Default.Reply, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Đã phản hồi", fontSize = 11.sp, color = FutaColors.BrandGreen, modifier = Modifier.padding(end = 8.dp))
                }
                VerbatimText(shortDate(item["createdAt"].string), fontSize = 11.sp, color = FutaColors.Slate)
            }
        }
    }
}

/** Create (proposal == null) or edit form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalEditorSheet(
    proposal: JSONValue?,
    onDismiss: () -> Unit,
    onSaved: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val initialTitle = proposal?.get("title")?.string.orEmpty()
    val initialCategory = proposal?.get("category")?.string?.ifEmpty { null } ?: "Sản phẩm"
    val initialDescription = proposal?.get("description")?.string.orEmpty()
    var title by remember { mutableStateOf(initialTitle) }
    var category by remember { mutableStateOf(initialCategory) }
    var description by remember { mutableStateOf(initialDescription) }
    var isSaving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = title != initialTitle || category != initialCategory || description != initialDescription
    val isEdit = proposal != null

    fun requestClose() {
        if (isSaving) return
        if (dirty) confirmDiscard = true else onDismiss()
    }

    fun submit() {
        if (title.isBlank()) {
            ToastCenter.show(tr("Vui lòng nhập tiêu đề đề xuất"), isError = true)
            return
        }
        if (!isEdit && description.isBlank()) {
            ToastCenter.show(tr("Vui lòng nhập mô tả chi tiết"), isError = true)
            return
        }
        scope.launch {
            isSaving = true
            try {
                // The backend accepts only draft | submitted; any reviewed state is re-submitted.
                val status = if (proposal?.get("status")?.string == "draft") "draft" else "submitted"
                val body = buildJsonObject {
                    put("title", title.trim())
                    put("category", category)
                    put("description", description.trim())
                    put("status", status)
                }.toString()
                if (proposal == null) {
                    APIClient.get().request("/advisor/proposals", method = "POST", bodyJson = body)
                    ToastCenter.show(tr("Đã gửi đề xuất thành công"))
                } else {
                    APIClient.get().request("/advisor/proposals/${proposal.id}", method = "PUT", bodyJson = body)
                    ToastCenter.show(tr("Đã cập nhật đề xuất"))
                }
                onSaved()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không lưu được đề xuất"), isError = true)
            } finally {
                isSaving = false
            }
        }
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = { requestClose() },
        title = if (isEdit) tr("Chỉnh sửa đề xuất") else tr("Tạo đề xuất mới"),
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Huỷ", variant = FutaButtonVariant.OUTLINE, enabled = !isSaving, onClick = { requestClose() }, modifier = Modifier.weight(1f))
                FutaButton(
                    text = when {
                        isSaving -> "Đang lưu..."
                        isEdit -> "Lưu cập nhật"
                        else -> "Gửi đề xuất"
                    },
                    enabled = !isSaving && title.isNotBlank() && (isEdit || description.isNotBlank()),
                    onClick = { submit() },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FutaFormSectionField(label = "Tiêu đề đề xuất", required = true) {
                FutaInput(value = title, onValueChange = { title = it }, placeholder = tr("Tiêu đề đề xuất *"), modifier = Modifier.fillMaxWidth())
            }
            FutaFormSectionField(label = "Danh mục") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    proposalCategories.forEach { (value, label) -> ChoiceChip(tr(label), category == value) { category = value } }
                }
            }
            FutaFormSectionField(label = "Nội dung", required = !isEdit) {
                FutaTextArea(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = tr("Mô tả chi tiết ý kiến đóng góp *"),
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    FutaDialog(
        visible = confirmDiscard,
        onDismiss = { confirmDiscard = false },
        title = "Huỷ thay đổi?",
        confirmText = "Huỷ thay đổi",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Tiếp tục chỉnh sửa",
        onConfirm = onDismiss
    ) {
        Text("Nội dung bạn vừa nhập sẽ không được lưu.", fontSize = 13.5.sp, color = FutaColors.Slate)
    }
}

@Composable
private fun ProposalDetailSheet(
    proposal: JSONValue,
    onDismiss: () -> Unit,
    onChanged: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    if (editing) {
        ProposalEditorSheet(
            proposal = proposal,
            onDismiss = { editing = false },
            onSaved = {
                editing = false
                onChanged()
                onDismiss()
            }
        )
        return
    }

    val (statusLabel, statusColor) = proposalStatus(proposal["status"].string)
    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = tr("Chi tiết đề xuất"),
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(
                    text = "Xoá đề xuất",
                    variant = FutaButtonVariant.DANGER,
                    icon = Icons.Default.Delete,
                    enabled = !isDeleting,
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f)
                )
                FutaButton(
                    text = "Chỉnh sửa đề xuất",
                    icon = Icons.Default.Edit,
                    enabled = !isDeleting,
                    onClick = { editing = true },
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                VerbatimText(proposal["title"].string, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                AdvisorBadge(tr(statusLabel), statusColor)
            }
            AdvisorSection(header = "Thông tin đề xuất") {
                AdvisorLabeledRow("Danh mục", tr(proposal["category"].string))
                AdvisorLabeledRow("Trạng thái", tr(statusLabel), valueColor = statusColor)
                AdvisorLabeledRow("Ngày gửi", shortDate(proposal["createdAt"].string))
                if (proposal["updatedAt"].string.isNotEmpty()) AdvisorLabeledRow("Cập nhật", shortDate(proposal["updatedAt"].string))
            }
            AdvisorSection(header = "Nội dung") {
                VerbatimText(proposal["description"].string.ifEmpty { "-" }, fontSize = 13.5.sp, color = FutaColors.Navy, lineHeight = 19.sp)
            }
            AdvisorSection(header = "Phản hồi từ quản trị viên") {
                val reply = proposal["adminReply"].string
                if (reply.isEmpty()) {
                    Text("Chưa có phản hồi. Quản trị viên sẽ xem xét đề xuất của bạn.", fontSize = 13.sp, color = FutaColors.Slate)
                } else {
                    VerbatimText(reply, fontSize = 13.5.sp, color = FutaColors.Navy, lineHeight = 19.sp)
                    proposal["repliedAt"].string.takeIf { it.isNotEmpty() }?.let {
                        Text(tr("Phản hồi lúc: {0}", shortDate(it)), fontSize = 11.5.sp, color = FutaColors.Slate)
                    }
                }
            }
        }
    }

    FutaDialog(
        visible = confirmDelete,
        onDismiss = { confirmDelete = false },
        title = "Xoá đề xuất?",
        confirmText = "Xoá vĩnh viễn",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Huỷ",
        onConfirm = {
            scope.launch {
                isDeleting = true
                try {
                    APIClient.get().request("/advisor/proposals/${proposal.id}", method = "DELETE")
                    ToastCenter.show(tr("Đã xoá đề xuất"))
                    onChanged()
                    onDismiss()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không xoá được đề xuất"), isError = true)
                } finally {
                    isDeleting = false
                }
            }
        }
    ) {
        Text("Đề xuất sẽ bị xoá khỏi danh sách của bạn và không thể khôi phục.", fontSize = 13.5.sp, color = FutaColors.Slate)
    }
}
