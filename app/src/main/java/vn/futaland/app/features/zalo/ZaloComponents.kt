package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.*
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Shared building blocks of the messaging screens.

/** Messaging permissions, as gated on iOS (the backend treats `zalo:manage` as a superset). */
object ZaloAccess {
    private val session get() = AppSession.shared
    private fun any(vararg permissions: String): Boolean =
        session.isAuthenticated && (session.role == "admin" || permissions.any { session.hasPermission(it) })

    val canView: Boolean get() = any("zalo:view", "zalo:manage")
    val canSend: Boolean get() = any("zalo:send", "zalo:manage")
    val canManageAccounts: Boolean get() = any("zalo:manage", "zalo:accounts")
    val canUseCampaigns: Boolean get() = any("zalo:manage", "zalo:campaigns")
    val canManageSuggestions: Boolean get() = any("zalo:manage")
}

val ZaloBlue = Color(0xFF0068FF)
val ZaloBlueSoft = Color(0xFFE8F1FF)

// MARK: - Layout

/** Full-screen page of the in-module stack; it absorbs touches so the page below stays inert. */
@Composable
fun ZaloPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FutaColors.PageBg)
            // A pointer handler makes this page the hit target (no semantics merge, unlike clickable).
            .pointerInput(Unit) { detectTapGestures { } },
        content = content
    )
}

@Composable
fun ZaloTopBar(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    verbatimTitle: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = FutaColors.PageBg, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onBack != null) {
                FutaHeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), onBack)
            }
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                if (verbatimTitle) {
                    VerbatimText(title, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(title, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!subtitle.isNullOrEmpty()) {
                    VerbatimText(subtitle, fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}

@Composable
fun ZaloSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            VerbatimText(tr(title).uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content)
        }
        if (footer != null) {
            Text(footer, fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 16.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp))
        }
    }
}

// MARK: - Controls

/** FutaButton with a spinner while [loading]; disabled while loading. */
@Composable
fun ZaloButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FutaButtonVariant = FutaButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 44.dp
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        FutaButton(
            text = if (loading) "" else text,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            variant = variant,
            icon = if (loading) null else icon,
            enabled = enabled && !loading,
            height = height
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                color = if (variant == FutaButtonVariant.OUTLINE || variant == FutaButtonVariant.GHOST) FutaColors.BrandGreen else Color.White
            )
        }
    }
}

@Composable
fun ZaloChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = ZaloBlue,
    dotColor: Color? = null,
    verbatim: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) color else Color(0xFFF1F5F9))
            .border(1.dp, if (selected) color else FutaColors.LightBlueBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (dotColor != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color.White else dotColor))
        }
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else FutaColors.Slate, modifier = Modifier.size(13.dp))
        }
        val fg = if (selected) Color.White else FutaColors.Navy
        if (verbatim) VerbatimText(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
        else Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

@Composable
fun ZaloStatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f))
            .border(BorderStroke(0.5.dp, color.copy(alpha = 0.3f)), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
fun ZaloAvatar(url: String, name: String, size: Dp, color: Color = ZaloBlue, modifier: Modifier = Modifier) {
    val fallback: @Composable () -> Unit = {
        Box(
            Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            VerbatimText(name.trim().take(1).uppercase(), fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold, color = color)
        }
    }
    if (url.isEmpty()) {
        Box(modifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
            loading = { fallback() },
            error = { fallback() }
        )
    }
}

// MARK: - States

@Composable
fun ZaloErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FutaEmptyState(
        title = "Không thể tải dữ liệu",
        message = message,
        icon = Icons.Default.ErrorOutline,
        modifier = modifier,
        actionButton = {
            FutaButton(text = "Thử lại", onClick = onRetry, variant = FutaButtonVariant.OUTLINE, height = 40.dp)
        }
    )
}

@Composable
fun ZaloInlineError(message: String, onRetry: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFEF2F2))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, fontSize = 12.sp, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text("Thử lại", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZaloBlue,
                modifier = Modifier.clickable(onClick = onRetry).padding(start = 8.dp))
        }
    }
}

@Composable
fun ZaloSpinner(modifier: Modifier = Modifier, size: Dp = 18.dp, color: Color = ZaloBlue) {
    CircularProgressIndicator(modifier = modifier.size(size), strokeWidth = 2.dp, color = color)
}

/** Destructive confirmation (delete, cancel, disconnect): red confirm button, separate cancel. */
@Composable
fun ZaloConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelText: String = "Huỷ",
    destructive: Boolean = true
) {
    FutaDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        confirmText = confirmText,
        confirmVariant = if (destructive) FutaButtonVariant.DANGER else FutaButtonVariant.PRIMARY,
        onConfirm = onConfirm,
        cancelText = cancelText
    ) {
        Text(message, fontSize = 14.sp, color = FutaColors.Body, lineHeight = 20.sp, textAlign = TextAlign.Start)
    }
}

// MARK: - Dates

object ZaloDates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val dm = DateTimeFormatter.ofPattern("dd/MM")
    private val dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val hmsdm = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM")
    private val dmyhm = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun parse(iso: String): Instant? {
        if (iso.isBlank()) return null
        return runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
            ?: iso.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
    }

    private fun local(iso: String) = parse(iso)?.atZone(zone)

    /** Conversation list time: HH:mm today, "Hôm qua", else dd/MM. */
    fun listTime(iso: String): String {
        val date = local(iso) ?: return ""
        val today = LocalDate.now(zone)
        return when (date.toLocalDate()) {
            today -> hm.format(date)
            today.minusDays(1) -> tr("Hôm qua")
            else -> dm.format(date)
        }
    }

    fun dayKey(iso: String): String = local(iso)?.let { dmy.format(it) } ?: iso

    fun dayLabel(iso: String): String {
        val date = local(iso) ?: return iso
        val today = LocalDate.now(zone)
        return when (date.toLocalDate()) {
            today -> tr("Hôm nay")
            today.minusDays(1) -> tr("Hôm qua")
            else -> dmy.format(date)
        }
    }

    fun time(iso: String): String = local(iso)?.let { hm.format(it) } ?: ""

    /** Campaign detail timestamps (iOS "HH:mm:ss dd/MM"). */
    fun detail(iso: String): String = local(iso)?.let { hmsdm.format(it) } ?: ""

    fun dateTime(iso: String): String = local(iso)?.let { dmyhm.format(it) } ?: ""

    fun epoch(iso: String): Long = parse(iso)?.toEpochMilli() ?: 0L
}
