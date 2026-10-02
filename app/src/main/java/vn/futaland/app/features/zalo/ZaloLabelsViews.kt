package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import vn.futaland.app.designsystem.*

private val presetColors = listOf(
    "#EF4444", "#F97316", "#F59E0B", "#EAB308", "#84CC16", "#22C55E",
    "#10B981", "#14B8A6", "#06B6D4", "#0EA5E9", "#3B82F6", "#6366F1",
    "#8B5CF6", "#A855F7", "#D946EF", "#EC4899", "#F43F5E", "#64748B",
)

@Composable
private fun LabelsSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { FutaAdminRowSkeleton() }
    }
}

// MARK: - Labels management

@Composable
fun ZaloLabelsScreen(navigator: ZaloNavigator) {
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf<List<ZaloCustomLabelModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var labelToDelete by remember { mutableStateOf<ZaloCustomLabelModel?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var formEditing by remember { mutableStateOf<ZaloCustomLabelModel?>(null) }
    var formSaving by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        try {
            labels = ZaloService.fetchLabels()
        } catch (e: Exception) {
            error = e.message ?: tr("Không thể tải nhãn")
        }
        loading = false
    }

    LaunchedEffect(navigator.labelsVersion) { load() }

    fun delete(label: ZaloCustomLabelModel) {
        if (deletingId != null) return
        deletingId = label.id
        scope.launch {
            try {
                ZaloService.deleteLabel(label.id)
                labels = labels.filterNot { it.id == label.id }
                ToastCenter.show(tr("Đã xoá nhãn"))
                navigator.labelsVersion++
                navigator.conversationsVersion++
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể xoá nhãn"), isError = true)
            }
            deletingId = null
        }
    }

    ZaloTopBar(title = "Quản lý nhãn", onBack = { if (!formSaving && deletingId == null) navigator.pop() }) {
        FutaHeaderIconButton(Icons.Default.Add, tr("Tạo nhãn mới"), {
            if (!loading && deletingId == null) { formEditing = null; formOpen = true }
        })
    }

    when {
        loading && labels.isEmpty() -> LabelsSkeleton()
        error != null && labels.isEmpty() -> ZaloErrorState(error!!, onRetry = { scope.launch { load() } })
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { msg -> item { ZaloInlineError(msg) { scope.launch { load() } } } }
            item {
                Text(tr("Danh sách nhãn ({0})", labels.size).uppercase(), fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold, color = FutaColors.Slate, modifier = Modifier.padding(start = 4.dp))
            }
            if (labels.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Chưa có nhãn nào",
                        message = "Chưa có nhãn nào. Bấm '+' để tạo nhãn mới.",
                        icon = Icons.Default.Label,
                        actionButton = { FutaButton("Tạo nhãn mới", onClick = { formEditing = null; formOpen = true }, height = 40.dp) }
                    )
                }
            } else {
                items(labels, key = { it.id }) { label ->
                    FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(label.color))
                            VerbatimText(label.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { formEditing = label; formOpen = true }, enabled = deletingId == null) {
                                Icon(Icons.Default.Edit, tr("Chỉnh sửa nhãn {0}", label.name), tint = FutaColors.Slate, modifier = Modifier.size(19.dp))
                            }
                            // Destructive action kept apart from edit, with its own colour.
                            if (deletingId == label.id) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ZaloSpinner() }
                            } else {
                                IconButton(onClick = { labelToDelete = label }, enabled = deletingId == null) {
                                    Icon(Icons.Default.Delete, tr("Xoá nhãn {0}", label.name), tint = FutaColors.RedPdf, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Nhãn giúp phân loại và lọc các cuộc trò chuyện theo nhóm khách hàng.", fontSize = 11.5.sp,
                    color = FutaColors.Slate, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }

    if (formOpen) {
        val editing = formEditing
        ZaloLabelFormSheet(
            editing = editing,
            onSavingChange = { formSaving = it },
            onDismiss = { formOpen = false },
            onSaved = { saved ->
                labels = if (editing != null) labels.map { if (it.id == editing.id) saved else it } else labels + saved
                navigator.labelsVersion++
                navigator.conversationsVersion++
                formOpen = false
            }
        )
    }

    ZaloConfirmDialog(
        visible = labelToDelete != null,
        title = "Xoá nhãn",
        message = tr("Bạn có chắc chắn muốn xoá nhãn '{0}'? Nhãn sẽ bị gỡ khỏi tất cả cuộc trò chuyện.", labelToDelete?.name.orEmpty()),
        confirmText = "Xoá nhãn",
        onConfirm = { labelToDelete?.let { delete(it) } },
        onDismiss = { labelToDelete = null }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZaloLabelFormSheet(
    editing: ZaloCustomLabelModel?,
    onSavingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSaved: (ZaloCustomLabelModel) -> Unit
) {
    val scope = rememberCoroutineScope()
    val initialColor = editing?.colorHex?.ifEmpty { null } ?: presetColors[0]
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var color by remember { mutableStateOf(initialColor) }
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val hasChanges = name != editing?.name.orEmpty() || !color.equals(initialColor, ignoreCase = true)

    fun close() {
        if (saving) return
        if (hasChanges) confirmDiscard = true else onDismiss()
    }

    fun save() {
        val trimmed = name.trim()
        if (saving) return
        if (trimmed.isEmpty()) {
            formError = "Tên nhãn không được để trống."
            return
        }
        formError = null
        saving = true
        onSavingChange(true)
        scope.launch {
            try {
                val saved = if (editing != null) ZaloService.updateLabel(editing.id, trimmed, color)
                else ZaloService.createLabel(trimmed, color)
                ToastCenter.show(if (editing != null) tr("Đã cập nhật nhãn") else tr("Đã tạo nhãn mới"))
                onSaved(saved)
            } catch (e: Exception) {
                formError = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu nhãn"), isError = true)
            }
            saving = false
            onSavingChange(false)
        }
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = { close() },
        title = if (editing == null) tr("Tạo nhãn mới") else tr("Chỉnh sửa nhãn"),
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton("Huỷ", onClick = { close() }, variant = FutaButtonVariant.OUTLINE, enabled = !saving,
                    modifier = Modifier.weight(1f))
                ZaloButton(if (editing == null) "Tạo nhãn" else "Lưu", onClick = { save() }, loading = saving,
                    enabled = name.isNotBlank(), modifier = Modifier.weight(1f))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            formError?.let { ZaloInlineError(it) }
            FutaFormSectionField(label = "Tên nhãn", required = true) {
                FutaInput(value = name, onValueChange = { name = it.take(60) },
                    placeholder = tr("Nhập tên nhãn (ví dụ: Khách VIP, Đang đàm phán)"), enabled = !saving)
                Text("Tên nhãn không được để trống.", fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.padding(top = 4.dp))
            }
            FutaFormSectionField(label = "Màu sắc") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presetColors.forEach { hex ->
                        val selected = color.equals(hex, ignoreCase = true)
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(zaloColorFromHex(hex))
                                .then(if (selected) Modifier.border(2.dp, FutaColors.Navy, CircleShape) else Modifier)
                                .clickable(enabled = !saving) { color = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Default.Check, tr("Màu {0}", hex), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            FutaFormSectionField(label = "Xem trước") {
                val previewColor = zaloColorFromHex(color)
                Row(
                    Modifier.clip(CircleShape).background(previewColor.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Label, null, tint = previewColor, modifier = Modifier.size(15.dp))
                    if (name.isBlank()) Text("Tên nhãn", fontSize = 13.sp, color = previewColor)
                    else VerbatimText(name, fontSize = 13.sp, color = previewColor)
                }
            }
        }
    }

    ZaloConfirmDialog(
        visible = confirmDiscard,
        title = "Bỏ thay đổi chưa lưu?",
        message = "Các thay đổi chưa lưu sẽ bị mất.",
        confirmText = "Bỏ thay đổi",
        cancelText = "Tiếp tục chỉnh sửa",
        onConfirm = onDismiss,
        onDismiss = { confirmDiscard = false }
    )
}

// MARK: - Assign labels to a conversation

@Composable
fun ZaloConversationLabelSheet(
    conversation: ZaloConversationModel,
    navigator: ZaloNavigator,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var allLabels by remember { mutableStateOf<List<ZaloCustomLabelModel>>(emptyList()) }
    var assigned by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var updatingId by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload, navigator.labelsVersion) {
        loading = true
        error = null
        try {
            coroutineScope {
                val labelsTask = async { ZaloService.fetchLabels() }
                val assignedTask = async {
                    ZaloService.fetchConversationLabels(conversation.provider, conversation.accountId, conversation.threadId)
                }
                allLabels = labelsTask.await()
                assigned = assignedTask.await().map { it.id }.toSet()
            }
        } catch (e: Exception) {
            error = e.message ?: tr("Không thể tải nhãn")
        }
        loading = false
    }

    fun toggle(label: ZaloCustomLabelModel) {
        if (updatingId != null) return
        updatingId = label.id
        val isAssigned = label.id in assigned
        scope.launch {
            try {
                if (isAssigned) {
                    ZaloService.removeLabel(conversation.provider, conversation.accountId, conversation.threadId, label.id)
                    assigned = assigned - label.id
                    ToastCenter.show(tr("Đã gỡ nhãn {0}", label.name))
                } else {
                    ZaloService.assignLabel(conversation.provider, conversation.accountId, conversation.threadId, label.id)
                    assigned = assigned + label.id
                    ToastCenter.show(tr("Đã gán nhãn {0}", label.name))
                }
                navigator.conversationsVersion++
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể cập nhật nhãn"), isError = true)
            }
            updatingId = null
        }
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = { if (updatingId == null) onDismiss() },
        title = tr("Gán nhãn khách hàng"),
        headerTrailing = {
            Text("Quản lý nhãn", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZaloBlue,
                modifier = Modifier.clickable(enabled = updatingId == null) {
                    onDismiss()
                    navigator.push(ZaloRoute.Labels)
                })
        },
        footer = {
            FutaButton("Xong", onClick = onDismiss, enabled = updatingId == null, modifier = Modifier.fillMaxWidth())
        }
    ) {
        when {
            loading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(5) { FutaSkeletonBlock(height = 20.dp) }
            }
            error != null -> ZaloErrorState(error!!, onRetry = { reload++ })
            allLabels.isEmpty() -> FutaEmptyState(
                title = "Chưa có nhãn nào trong hệ thống",
                message = "Hãy tạo nhãn trong mục Quản lý nhãn trước khi gán cho khách hàng.",
                icon = Icons.Default.LabelOff,
                actionButton = {
                    FutaButton("Tạo nhãn mới", onClick = { onDismiss(); navigator.push(ZaloRoute.Labels) }, height = 40.dp)
                }
            )
            else -> Column {
                allLabels.forEach { label ->
                    val isAssigned = label.id in assigned
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = updatingId == null) { toggle(label) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(label.color))
                        VerbatimText(label.name, fontSize = 15.sp, color = FutaColors.Body, modifier = Modifier.weight(1f))
                        when {
                            updatingId == label.id -> ZaloSpinner()
                            isAssigned -> Icon(Icons.Default.Check, tr("Đã gán"), tint = FutaColors.BrandGreen)
                        }
                    }
                }
                Text("Nhãn giúp phân loại và lọc các cuộc trò chuyện theo nhóm khách hàng.", fontSize = 11.5.sp,
                    color = FutaColors.Slate, textAlign = TextAlign.Start, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
