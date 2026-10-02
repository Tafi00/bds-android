package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.designsystem.*

// Shared building blocks for the CRM, contracts and customers admin screens
// (iOS CRMViews.swift): full-screen create/detail/edit pages, option pickers,
// the filter button with its active count, pagination and the share sheet.

val CrmPageBackground = Color(0xFFF8FAFC)

/** Opens the system share sheet with plain text (iOS `ActivityShareView`). */
fun crmShareText(context: Context, text: String, chooserTitle: String = "Chia sẻ") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, tr(chooserTitle))) }
        .onFailure { ToastCenter.show(tr("Không mở được trình chia sẻ"), isError = true) }
}

fun crmDial(context: Context, phone: String) {
    if (phone.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))) }
}

fun crmSms(context: Context, phone: String) {
    if (phone.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}"))) }
        .onFailure { ToastCenter.show(tr("Không mở được ứng dụng tin nhắn"), isError = true) }
}

fun crmZalo(context: Context, phone: String) {
    if (phone.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://zalo.me/${phone.trim()}"))) }
}

/** CSV cell: quoted when it contains a separator, quote or newline. */
fun crmCsvCell(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/** "#RRGGBB" → Color, or [fallback] when the value is missing or malformed. */
fun crmHexColor(hex: String, fallback: Color = FutaColors.BrandGreen): Color {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return fallback
    return clean.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback
}

/**
 * Full-screen page layered over a list (create / detail / edit), so the list keeps its scroll
 * position. System back and the header back button both call [onBack].
 */
@Composable
fun CrmFullScreenPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onBack)
    Surface(color = CrmPageBackground, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            AdvisorTopBar(title = title, onBack = onBack, actions = actions)
            val body = Modifier
                .weight(1f)
                .fillMaxWidth()
            Column(
                modifier = if (scrollable) body.verticalScroll(rememberScrollState()).padding(16.dp) else body.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
            bottomBar?.invoke()
        }
    }
}

/** Sticky Cancel / Save bar for long forms. */
@Composable
fun CrmFormActionBar(
    submitText: String,
    submitting: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean = true,
    cancelText: String = "Huỷ",
    submitVariant: FutaButtonVariant = FutaButtonVariant.PRIMARY
) {
    FutaStickyActionBar {
        FutaButton(
            text = cancelText,
            variant = FutaButtonVariant.OUTLINE,
            enabled = !submitting,
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        FutaButton(
            text = if (submitting) "Đang lưu..." else submitText,
            variant = submitVariant,
            enabled = submitEnabled && !submitting,
            onClick = onSubmit,
            modifier = Modifier.weight(1.4f)
        )
    }
}

/** "Discard changes?" confirmation shown when leaving a dirty form. */
@Composable
fun CrmDiscardDialog(visible: Boolean, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    FutaDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = "Huỷ thay đổi?",
        confirmText = "Bỏ thay đổi",
        confirmVariant = FutaButtonVariant.DANGER,
        onConfirm = onDiscard,
        cancelText = "Tiếp tục chỉnh sửa"
    ) {
        Text("Các thay đổi chưa lưu sẽ bị mất.", fontSize = 13.5.sp, color = FutaColors.Slate)
    }
}

/** Confirmation for destructive or status-changing actions. */
@Composable
fun CrmConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    FutaDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        confirmText = confirmText,
        confirmVariant = if (destructive) FutaButtonVariant.DANGER else FutaButtonVariant.PRIMARY,
        onConfirm = onConfirm,
        cancelText = "Huỷ"
    ) {
        Text(message, fontSize = 13.5.sp, color = FutaColors.Slate, lineHeight = 19.sp)
    }
}

/** Labelled select field that opens [CrmOptionSheet]. */
@Composable
fun CrmSelectRow(label: String, value: String, placeholder: String = "Chọn...", enabled: Boolean = true, onClick: () -> Unit) {
    FutaFormSectionField(label = label) {
        FutaSelectField(
            displayValue = value,
            placeholder = placeholder,
            onClick = { if (enabled) onClick() }
        )
    }
}

/** Single-choice bottom sheet. Labels are translated unless [verbatim] (people's names). */
@Composable
fun <T> CrmOptionSheet(
    visible: Boolean,
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    verbatim: Boolean = false
) {
    FutaBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {
        if (options.isEmpty()) {
            Text("Không có lựa chọn", fontSize = 13.sp, color = FutaColors.Slate, modifier = Modifier.padding(vertical = 16.dp))
        }
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(option)
                        onDismiss()
                    }
                    .padding(vertical = 13.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = if (isSelected) FutaColors.BrandGreen else FutaColors.Navy
                val weight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                if (verbatim) {
                    VerbatimText(label(option), fontSize = 14.sp, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
                } else {
                    Text(label(option), fontSize = 14.sp, fontWeight = weight, color = color, modifier = Modifier.weight(1f))
                }
                if (isSelected) Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))
        }
    }
}

/** Multi-choice bottom sheet (staff assignment) with Apply / Clear. */
@Composable
fun CrmMultiSelectSheet(
    visible: Boolean,
    title: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    var draft by remember(selected) { mutableStateOf(selected) }
    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = title,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Bỏ chọn", variant = FutaButtonVariant.OUTLINE, onClick = { draft = emptySet() }, modifier = Modifier.weight(1f))
                FutaButton(
                    text = tr("Áp dụng ({0})", draft.size),
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    ) {
        if (options.isEmpty()) {
            Text("Chưa có nhân sự", fontSize = 13.sp, color = FutaColors.Slate, modifier = Modifier.padding(vertical = 16.dp))
        }
        options.forEach { (id, name) ->
            val checked = id in draft
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { draft = if (checked) draft - id else draft + id }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { draft = if (checked) draft - id else draft + id },
                    colors = CheckboxDefaults.colors(checkedColor = FutaColors.BrandGreen)
                )
                VerbatimText(name, fontSize = 14.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Filter trigger showing the number of active filters. */
@Composable
fun CrmFilterButton(activeCount: Int, onClick: () -> Unit) {
    val active = activeCount > 0
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (active) Color(0xFFEAF5EF) else Color.White,
            border = BorderStroke(1.dp, if (active) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
            modifier = Modifier
                .size(46.dp)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FilterList, tr("Bộ lọc"), tint = if (active) FutaColors.BrandGreen else FutaColors.Slate)
            }
        }
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .background(FutaColors.BrandOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                VerbatimText("$activeCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** Applied filter chips (each removable) plus "Xoá tất cả". */
@Composable
fun CrmAppliedFilterChips(chips: List<Pair<String, () -> Unit>>, onClearAll: () -> Unit) {
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chips.forEach { (label, onRemove) ->
            Surface(
                shape = CircleShape,
                color = Color(0xFFEAF5EF),
                border = BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.35f)),
                modifier = Modifier.clickable(onClick = onRemove)
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VerbatimText(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
                    Icon(Icons.Default.Close, tr("Xoá"), tint = FutaColors.BrandGreen, modifier = Modifier.padding(start = 4.dp).size(13.dp))
                }
            }
        }
        Text(
            "Xoá tất cả",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = FutaColors.BrandOrange,
            modifier = Modifier
                .clickable(onClick = onClearAll)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/** Filter section inside the filter sheet: wrapping capsule options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> CrmFilterOptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    verbatim: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) FutaColors.BrandGreen else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { onSelect(option) }
                ) {
                    val color = if (isSelected) Color.White else FutaColors.Navy
                    val m = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    if (verbatim) {
                        VerbatimText(label(option), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = m)
                    } else {
                        Text(label(option), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = m)
                    }
                }
            }
        }
    }
}

/** Footer of a filter sheet: Reset / Cancel / Apply. */
@Composable
fun CrmFilterSheetFooter(onReset: () -> Unit, onCancel: () -> Unit, onApply: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FutaButton(text = "Đặt lại", variant = FutaButtonVariant.GHOST, onClick = onReset, modifier = Modifier.weight(1f))
        FutaButton(text = "Đóng", variant = FutaButtonVariant.OUTLINE, onClick = onCancel, modifier = Modifier.weight(1f))
        FutaButton(text = "Áp dụng", onClick = onApply, modifier = Modifier.weight(1.2f))
    }
}

/** Previous / next pagination with "Trang x / y · n mục". */
@Composable
fun CrmPaginationBar(page: Int, totalPages: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    if (totalPages <= 1 && total <= 0) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(tr("Trang {0} / {1}", page, maxOf(1, totalPages)), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            Text(tr("{0} mục", total), fontSize = 11.sp, color = FutaColors.Slate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FutaButton(text = "Trang trước", variant = FutaButtonVariant.OUTLINE, enabled = page > 1, height = 38.dp, onClick = onPrevious)
            FutaButton(text = "Trang sau", variant = FutaButtonVariant.OUTLINE, enabled = page < totalPages, height = 38.dp, onClick = onNext)
        }
    }
}

/** Small stat tile (iOS CRMStatCard). */
@Composable
fun CrmStatTile(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            VerbatimText(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            Text(title, fontSize = 10.5.sp, color = FutaColors.Slate, maxLines = 1)
        }
    }
}

/** Danger zone card, visually separated from the primary actions. */
@Composable
fun CrmDangerZone(buttonText: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFEF2F2),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Vùng nguy hiểm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
            FutaButton(
                text = buttonText,
                variant = FutaButtonVariant.DANGER,
                icon = Icons.Default.Delete,
                enabled = enabled,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Call / SMS / Zalo quick actions for a phone number. */
@Composable
fun CrmContactActions(phone: String, modifier: Modifier = Modifier, showZalo: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FutaButton(
            text = "Gọi điện",
            icon = Icons.Default.Phone,
            enabled = phone.isNotBlank(),
            height = 40.dp,
            onClick = { crmDial(context, phone) },
            modifier = Modifier.weight(1f)
        )
        FutaButton(
            text = "Nhắn SMS",
            icon = Icons.Default.Sms,
            variant = FutaButtonVariant.OUTLINE,
            enabled = phone.isNotBlank(),
            height = 40.dp,
            onClick = { crmSms(context, phone) },
            modifier = Modifier.weight(1f)
        )
        if (showZalo) {
            FutaButton(
                text = "Zalo",
                variant = FutaButtonVariant.MINT,
                enabled = phone.isNotBlank(),
                height = 40.dp,
                onClick = { crmZalo(context, phone) },
                modifier = Modifier.weight(0.8f)
            )
        }
    }
}

/** Labelled form input with an optional inline validation error. */
@Composable
fun CrmTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    required: Boolean = false,
    error: String? = null,
    multiline: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    enabled: Boolean = true
) {
    FutaFormSectionField(label = label, required = required) {
        val options = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType)
        if (multiline) {
            FutaTextArea(value = value, onValueChange = onValueChange, placeholder = placeholder, enabled = enabled, keyboardOptions = options, modifier = Modifier.fillMaxWidth())
        } else {
            FutaTextField(value = value, onValueChange = onValueChange, placeholder = placeholder, enabled = enabled, keyboardOptions = options)
        }
        if (!error.isNullOrEmpty()) {
            Text(error, fontSize = 11.5.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        }
    }
}

private val crmEmailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

fun crmIsValidEmail(value: String): Boolean = crmEmailRegex.matches(value.trim())
