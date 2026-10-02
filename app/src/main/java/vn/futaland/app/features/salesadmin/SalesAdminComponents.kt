package vn.futaland.app.features.salesadmin

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PropertyFormatters
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ============================================================================
// Shared building blocks for the sales admin modules (projects, inventory,
// campaigns, registrations). Mirrors iOS SalesAdminHelpers.swift.
// ============================================================================

/** Amount and date formatting shared by the sales admin screens (iOS `SalesFormatters`). */
object SalesFormatters {
    fun currency(amount: Double): String = LocalizedPrice.full(amount)

    fun compactCurrency(amount: Double): String = when {
        amount >= 1_000_000 -> LocalizedPrice.compact(amount)
        amount >= 1_000 -> "${(amount / 1_000).toInt()}k"
        else -> LocalizedPrice.full(amount)
    }

    /** ISO timestamp → `dd/MM/yyyy HH:mm` in the device time zone. */
    fun dateTime(raw: String): String {
        if (raw.isBlank()) return "-"
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
            }.getOrNull()
            if (parsed != null) return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(parsed)
        }
        return raw.take(10)
    }

    /** `2026-08-01…` → `01/08/2026`. */
    fun dateOnly(raw: String): String {
        if (raw.isBlank()) return "-"
        if (raw.length >= 10) {
            val parts = raw.take(10).split("-")
            if (parts.size == 3) return "${parts[2]}/${parts[1]}/${parts[0]}"
            return raw.take(10)
        }
        return raw
    }

    /** Area with one decimal, the trailing `.0` dropped. */
    fun area(value: Double): String = "%.1f".format(Locale.US, value).removeSuffix(".0") + " m²"

    fun percent(value: Double): String = "%.1f".format(Locale.US, value) + "%"
}

/** Booking step labels/colors (iOS `BookingStepHelper`). */
object BookingStepHelper {
    fun title(step: String): String = when (step) {
        "online_holding" -> "Giữ chỗ online"
        "pending_booking" -> "Chờ duyệt cọc"
        "holding_success" -> "Giữ chỗ thành công"
        "deposit_pending" -> "Chờ xác nhận cọc"
        "deposited" -> "Đã nộp cọc"
        "deposit_contract" -> "Đã ký HĐ cọc"
        "commission_pending" -> "Chờ hoa hồng"
        "commission_paid" -> "Đã chi hoa hồng"
        "purchased" -> "Đã ký HĐMB"
        "rejected" -> "Từ chối"
        "cancelled" -> "Đã hủy"
        "cancel_requested" -> "Yêu cầu hủy"
        "none" -> "Chưa có giữ chỗ"
        else -> step.ifEmpty { "Chưa có" }
    }

    fun color(step: String): Color = when (step) {
        "online_holding" -> Color(0xFF2563EB)
        "pending_booking", "deposit_pending" -> Color(0xFFF97316)
        "holding_success" -> Color(0xFF7C3AED)
        "deposited", "deposit_contract" -> FutaColors.BrandGreen
        "commission_pending" -> Color(0xFF0D9488)
        "commission_paid" -> Color(0xFF4F46E5)
        "purchased" -> Color(0xFF16A34A)
        "rejected", "cancelled" -> Color(0xFFDC2626)
        "cancel_requested" -> Color(0xFFDB2777)
        else -> FutaColors.Slate
    }
}

/** Sales-rights status labels/colors (iOS `RegistrationStatusHelper`). */
object RegistrationStatusHelper {
    fun title(status: String): String = when (status) {
        "active" -> "Đang có quyền bán"
        "pending" -> "Chờ duyệt"
        "rejected" -> "Đã từ chối"
        "revoked" -> "Đã thu hồi"
        "expired" -> "Hết hạn"
        else -> status.ifEmpty { "Chưa duyệt" }
    }

    fun color(status: String): Color = when (status) {
        "active" -> FutaColors.BrandGreen
        "pending" -> Color(0xFFF97316)
        "rejected" -> Color(0xFFDC2626)
        else -> FutaColors.Slate
    }
}

/** Registration payloads carry the unit under `property` or (older rows) `apartment`. */
val JSONValue.nestedProperty: JSONValue
    get() = if (this["property"].isNull) this["apartment"] else this["property"]

/** First non-blank string among [values]. */
internal fun firstNonEmpty(vararg values: String): String = values.firstOrNull { it.isNotBlank() }.orEmpty()

/** Raw JSON element → kotlinx element, keeping nulls explicit. */
internal fun JSONValue?.orJsonNull(): JsonElement = this?.element ?: JsonNull

internal fun jsonString(value: String?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)

// ============================================================================
// Platform helpers: links, dialer, share, uploads
// ============================================================================

fun openExternalUrl(context: Context, raw: String) {
    val url = PropertyFormatters.resolveImageUrl(raw).ifEmpty { return }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { ToastCenter.show("Không thể mở liên kết", isError = true) }
}

fun dialPhone(context: Context, phone: String) {
    val clean = phone.filter { it.isDigit() || it == '+' }
    if (clean.isEmpty()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))) }
}

fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, subject)) }
}

/** Display name of a picked document (falls back to the last path segment). */
fun displayNameOf(context: Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return fromProvider?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
}

/**
 * Re-encodes a picked photo as JPEG (quality 85, longest side ≤ 2560px) and uploads it to
 * `POST /upload/images` (field `images`). Returns the public `original` URL.
 */
suspend fun uploadPickedImage(context: Context, uri: Uri, prefix: String): String {
    val jpeg = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException(tr("Không thể đọc ảnh đã chọn"))
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw IllegalStateException(tr("Không thể đọc ảnh đã chọn"))
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > 2560) {
            val ratio = 2560f / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }
    val res = APIClient.get().upload(
        data = jpeg,
        filename = "$prefix-${System.currentTimeMillis()}.jpg",
        mimeType = "image/jpeg",
        path = "/upload/images",
        field = "images"
    )
    val url = res["data"][0]["original"].string
    if (url.isEmpty()) throw IllegalStateException(tr("Máy chủ không trả về đường dẫn ảnh"))
    return url
}

/** Uploaded document metadata from `POST /upload/document`. */
data class UploadedDocument(val name: String, val url: String, val size: Int, val mimeType: String)

suspend fun uploadPickedDocument(context: Context, uri: Uri): UploadedDocument {
    val filename = displayNameOf(context, uri).ifEmpty { "document-${System.currentTimeMillis()}.pdf" }
    val bytes = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException(tr("Không thể truy cập tệp đã chọn"))
    }
    val mimeType = context.contentResolver.getType(uri) ?: when (filename.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "application/pdf"
    }
    val res = APIClient.get().upload(data = bytes, filename = filename, mimeType = mimeType, path = "/upload/document", field = "document")
    val payload = if (res["data"].isNull) res else res["data"]
    val url = payload["url"].string
    if (url.isEmpty()) throw IllegalStateException(tr("Máy chủ không trả về đường dẫn tệp"))
    return UploadedDocument(
        name = payload["name"].string.ifEmpty { filename },
        url = url,
        size = payload["size"].int.takeIf { it > 0 } ?: bytes.size,
        mimeType = payload["mimeType"].string.ifEmpty { mimeType }
    )
}

fun formatFileSize(size: Int): String = when {
    size <= 0 -> ""
    size >= 1024 * 1024 -> "%.1f MB".format(Locale.US, size / (1024.0 * 1024.0))
    else -> "${(size + 1023) / 1024} KB"
}

// ============================================================================
// Screen chrome
// ============================================================================

/** Admin page header: round back button, title (+ optional subtitle), trailing actions. */
@Composable
fun SalesAdminTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = FutaColors.PageBg, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FutaHeaderIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Quay lại"), onClick = onBack)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Header icon button with an optional count badge (filter button). */
@Composable
fun BadgedHeaderButton(icon: ImageVector, contentDescription: String, badge: Int = 0, tint: Color = FutaColors.BrandGreen, onClick: () -> Unit) {
    Box {
        FutaHeaderIconButton(icon = icon, contentDescription = contentDescription, onClick = onClick, tint = tint)
        if (badge > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(17.dp).clip(CircleShape).background(FutaColors.BrandGreen),
                contentAlignment = Alignment.Center
            ) {
                Text("$badge", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** Sort button opening a menu of options; the active option is check-marked. */
@Composable
fun <T> SortMenuButton(options: List<T>, selected: T, titleFor: (T) -> String, onSelect: (T) -> Unit, isDefault: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FutaHeaderIconButton(
            icon = Icons.Default.SwapVert,
            contentDescription = tr("Sắp xếp"),
            onClick = { expanded = true },
            tint = if (isDefault) FutaColors.Navy else FutaColors.BrandGreen
        )
        FutaPopover(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = options,
            onItemSelected = onSelect,
            itemTrailingIcon = { option ->
                if (option == selected) Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
            }
        ) { option ->
            Text(
                titleFor(option),
                fontSize = 13.sp,
                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Medium,
                color = if (option == selected) FutaColors.BrandGreen else FutaColors.Navy
            )
        }
    }
}

/** Round green "+" header button for "Add new". */
@Composable
fun AddHeaderButton(contentDescription: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = FutaColors.BrandGreen, modifier = Modifier.size(40.dp).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Pill search field used at the top of every listing. */
@Composable
fun AdminSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    FutaInput(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = Icons.Default.Search,
        trailingIcon = if (value.isNotEmpty()) {
            { Icon(Icons.Default.Close, tr("Xóa"), tint = FutaColors.Slate, modifier = Modifier.size(18.dp).clickable { onValueChange("") }) }
        } else null,
        modifier = modifier.fillMaxWidth()
    )
}

/** Overview metric tile (iOS `SalesMetricCard`). */
@Composable
fun SalesMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) color else FutaColors.LightBlueBorder),
        modifier = modifier.heightIn(min = 96.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.weight(1f))
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy, maxLines = 1)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FutaColors.Slate, maxLines = 2)
        }
    }
}

/** 2-column grid of metric tiles. */
@Composable
fun MetricGrid(cards: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                row.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Capsule quick-filter chip. */
@Composable
fun QuickChip(title: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) FutaColors.Navy else Color.White,
        border = BorderStroke(1.dp, if (selected) FutaColors.Navy else Color(0xFFE2E8F0)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, null, tint = if (selected) Color.White else FutaColors.Navy, modifier = Modifier.size(13.dp))
            Text(title, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color.White else FutaColors.Navy)
        }
    }
}

@Composable
fun QuickChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** One applied filter: label shown in the chip + how to clear it. */
data class AppliedFilter(val label: String, val clear: () -> Unit)

/** Applied filter chips with per-chip remove and "Clear all". */
@Composable
fun AppliedFilterChips(filters: List<AppliedFilter>, onClearAll: () -> Unit) {
    if (filters.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { filter ->
            Surface(shape = CircleShape, color = FutaColors.MintBg, border = BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.25f))) {
                Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(filter.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
                    Icon(Icons.Default.Close, tr("Xóa"), tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp).clickable { filter.clear() })
                }
            }
        }
        Text("Xóa tất cả", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.clickable(onClick = onClearAll).padding(horizontal = 4.dp))
    }
}

/** Previous / next pagination bar with "Trang x / y" and the visible range. */
@Composable
fun PaginationBar(currentPage: Int, totalPages: Int, rangeText: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FutaButton(
            text = "Trang trước",
            icon = Icons.Default.ChevronLeft,
            variant = FutaButtonVariant.OUTLINE,
            enabled = currentPage > 1,
            height = 38.dp,
            onClick = onPrevious,
            modifier = Modifier.alphaIf(currentPage <= 1)
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Trang {0} / {1}", currentPage, totalPages), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            Text(rangeText, fontSize = 10.5.sp, color = FutaColors.Slate, maxLines = 1)
        }
        FutaButton(
            text = "Trang sau",
            icon = Icons.Default.ChevronRight,
            variant = FutaButtonVariant.OUTLINE,
            enabled = currentPage < totalPages,
            height = 38.dp,
            onClick = onNext,
            modifier = Modifier.alphaIf(currentPage >= totalPages)
        )
    }
}

private fun Modifier.alphaIf(dim: Boolean): Modifier = if (dim) this.alpha(0.4f) else this

/** Page math for client-side pagination. */
data class PageSlice<T>(val items: List<T>, val page: Int, val totalPages: Int, val start: Int, val end: Int, val total: Int)

fun <T> List<T>.pageSlice(page: Int, pageSize: Int): PageSlice<T> {
    val totalPages = maxOf(1, (size + pageSize - 1) / pageSize)
    val safePage = page.coerceIn(1, totalPages)
    if (isEmpty()) return PageSlice(emptyList(), 1, 1, 0, 0, 0)
    val start = (safePage - 1) * pageSize
    val end = minOf(start + pageSize, size)
    return PageSlice(subList(start, end), safePage, totalPages, start + 1, end, size)
}

/** Error state with retry (iOS `ErrorStateView`). */
@Composable
fun AdminErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FutaEmptyState(
        title = "Không thể tải dữ liệu",
        message = message.ifEmpty { tr("Đã có lỗi xảy ra. Vui lòng thử lại.") },
        icon = Icons.Default.CloudOff,
        modifier = modifier,
        actionButton = { FutaButton(text = "Thử lại", icon = Icons.Default.Refresh, onClick = onRetry) }
    )
}

/** Card with an icon header used for every detail section (iOS `WebDetailCard`). */
@Composable
fun DetailSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = FutaColors.BrandGreen,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
                }
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            HorizontalDivider(color = FutaColors.PanelDivider)
            content()
        }
    }
}

/** Icon + label + value tile used in 2-column spec grids. */
@Composable
fun SpecTile(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, FutaColors.PanelDivider, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(FutaColors.MintBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = FutaColors.Slate)
            Text(value.ifBlank { "Chưa cập nhật" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Lays out [tiles] two per row with equal heights. */
@Composable
fun TwoColumnGrid(tiles: List<@Composable (Modifier) -> Unit>, spacing: Dp = 10.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Label/value row; long values wrap under the label (iOS `webRow`). */
@Composable
fun InfoRow(title: String, value: String, valueColor: Color = FutaColors.Navy, verbatim: Boolean = false) {
    val clean = value.trim().ifEmpty { "-" }
    val long = clean.length > 30 || clean.contains('\n')
    @Composable
    fun ValueText(mod: Modifier = Modifier) {
        if (verbatim) VerbatimText(clean, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor, modifier = mod)
        else Text(clean, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor, modifier = mod)
    }
    if (long) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 12.sp, color = FutaColors.Slate)
            ValueText()
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 12.5.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
            ValueText()
        }
    }
}

/** Colored capsule badge. */
@Composable
fun StatusPill(text: String, fg: Color, bg: Color = fg.copy(alpha = 0.12f), solid: Boolean = false, dot: Boolean = false) {
    Surface(shape = CircleShape, color = if (solid) fg else bg, border = if (solid) null else BorderStroke(0.5.dp, fg.copy(alpha = 0.3f))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(if (solid) Color.White else fg))
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (solid) Color.White else fg, maxLines = 1)
        }
    }
}

/** "ERP Khóa" badge (iOS `ErpBadge`). */
@Composable
fun ErpLockBadge() {
    Surface(shape = CircleShape, color = Color(0xFFF97316).copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFFF97316), modifier = Modifier.size(10.dp))
            Text("ERP Khóa", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
        }
    }
}

/** Remote image with a neutral placeholder background. */
@Composable
fun AdminRemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val resolved = PropertyFormatters.resolveImageUrl(url)
    Box(modifier.background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
        if (resolved.isEmpty()) {
            Icon(Icons.Default.Image, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(28.dp))
        } else {
            AsyncImage(model = resolved, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

// ============================================================================
// Forms
// ============================================================================

/** Option for select fields: wire value + display label. */
data class SelectOption(val value: String, val label: String)

/** Dropdown select (iOS `AppSelectField`). Labels are translated at display time. */
@Composable
fun AdminSelectField(
    title: String?,
    value: String,
    options: List<SelectOption>,
    onSelect: (String) -> Unit,
    placeholder: String = "Chọn...",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.value == value }?.label ?: value
    Box(modifier.fillMaxWidth()) {
        FutaSelectField(title = title, displayValue = label, placeholder = placeholder, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White,
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label, fontSize = 13.sp,
                            fontWeight = if (option.value == value) FontWeight.Bold else FontWeight.Medium,
                            color = if (option.value == value) FutaColors.BrandGreen else FutaColors.Navy
                        )
                    },
                    trailingIcon = if (option.value == value) {
                        { Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Labeled text input (stacked label, optional required marker). */
@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    required: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
    error: String? = null
) {
    FutaFormSectionField(label = label, required = required) {
        if (multiline) {
            FutaTextArea(value = value, onValueChange = onValueChange, placeholder = placeholder, keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
        } else {
            FutaInput(value = value, onValueChange = onValueChange, placeholder = placeholder, keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
        }
        if (!error.isNullOrEmpty()) {
            Text(error, fontSize = 11.5.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** − value + stepper (iOS `Stepper`). */
@Composable
fun FormStepper(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, FutaColors.LightBlueBorder, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }, enabled = value > range.first, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Remove, tr("Giảm"), tint = FutaColors.Navy, modifier = Modifier.size(16.dp))
            }
            Text("$value", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.widthIn(min = 28.dp))
            IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }, enabled = value < range.last, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Add, tr("Tăng"), tint = FutaColors.Navy, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Toggle row with title + helper text. */
@Composable
fun FormToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, helper: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            if (!helper.isNullOrEmpty()) Text(helper, fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 16.sp)
        }
        FutaSwitch(checked = checked, onCheckedChange = onCheckedChange, activeColor = FutaColors.BrandGreen)
    }
}

/** White rounded section for forms with an uppercase-ish header. */
@Composable
fun FormSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate, modifier = Modifier.padding(start = 4.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** Red banner listing form validation / server errors. */
@Composable
fun FormErrorBanner(message: String?) {
    if (message.isNullOrEmpty()) return
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFEF2F2), border = BorderStroke(1.dp, Color(0xFFFECACA)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            Text(message, fontSize = 13.sp, color = Color(0xFFB91C1C))
        }
    }
}

/**
 * Image field with preview, upload (pick from gallery), replace and remove.
 * [onPick] starts the picker; the caller performs the upload and updates [url].
 */
@Composable
fun ImageUploadField(label: String, url: String, uploading: Boolean, onPick: () -> Unit, onRemove: () -> Unit, previewHeight: Dp = 140.dp) {
    FutaFormSectionField(label = label) {
        if (url.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(12.dp))) {
                AdminRemoteImage(url, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FutaButton(
                text = when {
                    uploading -> "Đang tải ảnh lên…"
                    url.isEmpty() -> "Tải ảnh lên"
                    else -> "Thay ảnh"
                },
                icon = Icons.Default.AddPhotoAlternate,
                variant = FutaButtonVariant.MINT,
                enabled = !uploading,
                height = 38.dp,
                onClick = onPick
            )
            if (url.isNotEmpty() && !uploading) {
                FutaButton(text = "Xóa ảnh", icon = Icons.Default.Delete, variant = FutaButtonVariant.OUTLINE, height = 38.dp, onClick = onRemove)
            }
            if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = FutaColors.BrandGreen)
        }
    }
}

/** Sticky bottom bar for forms: Back/Cancel + primary Save with loading state. */
@Composable
fun FormActionBar(saveTitle: String, saving: Boolean, enabled: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    FutaStickyActionBar {
        FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = onCancel, modifier = Modifier.weight(1f), enabled = !saving)
        FutaButton(
            text = if (saving) "Đang lưu…" else saveTitle,
            icon = if (saving) null else Icons.Default.Check,
            onClick = onSave,
            enabled = enabled && !saving,
            modifier = Modifier.weight(1.6f)
        )
    }
}

/** "Discard changes?" confirmation used by every form's Cancel/Back. */
@Composable
fun DiscardChangesDialog(visible: Boolean, onDismiss: () -> Unit, onDiscard: () -> Unit) {
    FutaDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = "Hủy bỏ thay đổi?",
        confirmText = "Bỏ thay đổi",
        confirmVariant = FutaButtonVariant.DANGER,
        onConfirm = onDiscard,
        cancelText = "Tiếp tục chỉnh sửa"
    ) {
        Text("Bạn có thay đổi chưa lưu. Nếu rời đi bây giờ, mọi thay đổi sẽ bị mất.", fontSize = 13.5.sp, color = FutaColors.Slate)
    }
}

/** Simple confirmation dialog with a message. */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    FutaDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        confirmText = confirmText,
        confirmVariant = if (destructive) FutaButtonVariant.DANGER else FutaButtonVariant.PRIMARY,
        onConfirm = onConfirm
    ) {
        Text(message, fontSize = 13.5.sp, color = FutaColors.Slate, lineHeight = 19.sp)
    }
}

/** Confirmation dialog with a reason text box. */
@Composable
fun ReasonDialog(
    visible: Boolean,
    title: String,
    message: String,
    placeholder: String,
    confirmText: String,
    destructive: Boolean = true,
    reasonRequired: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    var reason by remember { mutableStateOf("") }
    FutaDialog(
        visible = true,
        onDismiss = onDismiss,
        title = title,
        confirmText = confirmText,
        confirmVariant = if (destructive) FutaButtonVariant.DANGER else FutaButtonVariant.PRIMARY,
        onConfirm = {
            if (reasonRequired && reason.isBlank()) {
                ToastCenter.show(tr("Vui lòng nhập lý do"), isError = true)
            } else {
                onConfirm(reason.trim())
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, fontSize = 13.5.sp, color = FutaColors.Slate, lineHeight = 19.sp)
            FutaTextArea(value = reason, onValueChange = { reason = it }, placeholder = placeholder, minLines = 2, maxLines = 4)
        }
    }
}

/** Bottom sheet for list filters: Apply / Reset / Close (iOS filter sheets). */
@Composable
fun FilterSheet(
    visible: Boolean,
    title: String,
    applyTitle: String,
    canReset: Boolean,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    FutaBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Đặt lại", icon = Icons.Default.RestartAlt, variant = FutaButtonVariant.OUTLINE, enabled = canReset, onClick = onReset, modifier = Modifier.weight(1f))
                FutaButton(text = applyTitle, icon = Icons.Default.Check, onClick = onApply, modifier = Modifier.weight(1.6f))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/**
 * Overlay stack used by each sales admin module: the listing stays composed underneath
 * (keeping search/filters/scroll), detail/create/edit screens are pushed on top and the
 * system back gesture pops them.
 */
class ScreenStack<T : Any> {
    val entries = mutableStateListOf<T>()
    fun push(entry: T) { entries.add(entry) }
    fun pop() { if (entries.isNotEmpty()) entries.removeAt(entries.lastIndex) }
    fun replaceTop(entry: T) { pop(); push(entry) }
}

@Composable
fun <T : Any> ScreenStackHost(stack: ScreenStack<T>, base: @Composable () -> Unit, render: @Composable (T) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        base()
        stack.entries.forEachIndexed { index, entry ->
            key(index, entry) {
                // Surface swallows touches so the layer below cannot be tapped through.
                Surface(color = FutaColors.PageBg, modifier = Modifier.fillMaxSize()) {
                    BackHandler(enabled = index == stack.entries.lastIndex) { stack.pop() }
                    render(entry)
                }
            }
        }
    }
}
