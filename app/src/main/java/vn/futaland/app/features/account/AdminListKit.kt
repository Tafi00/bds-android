package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.QuickChip
import vn.futaland.app.features.salesadmin.SelectOption

// Small building blocks shared by the account/admin listings (exams, CMS taxonomies, users…),
// on top of the sales admin components.

/** Row skeletons shown while a listing loads for the first time. */
@Composable
internal fun AdminListSkeleton(rows: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FutaSkeletonBlock(height = 46.dp, radius = 12.dp)
        repeat(rows) { FutaAdminRowSkeleton() }
    }
}

/** Empty state that tells "no records yet" apart from "nothing matches the filters". */
@Composable
internal fun AdminListEmpty(
    hasRecords: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    onClearFilters: () -> Unit,
    icon: ImageVector = Icons.Default.Inbox
) {
    if (hasRecords) {
        FutaEmptyState(
            title = "Không có kết quả phù hợp",
            message = "Thử đổi từ khóa tìm kiếm hoặc bộ lọc.",
            icon = Icons.Default.FilterListOff,
            actionButton = { FutaButton(text = "Xóa bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = onClearFilters) }
        )
    } else {
        FutaEmptyState(title = emptyTitle, message = emptyMessage, icon = icon)
    }
}

/** Chip group inside a filter sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterChipGroup(title: String, options: List<SelectOption>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> QuickChip(option.label, option.value == selected) { onSelect(option.value) } }
        }
    }
}

/** White bordered row card used by listings. */
@Composable
internal fun AdminRowCard(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Destructive action card kept apart from the primary actions at the bottom of edit pages. */
@Composable
internal fun DangerZoneCard(title: String, message: String, buttonText: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFEF2F2),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
            }
            Text(message, fontSize = 12.5.sp, color = Color(0xFF7F1D1D), lineHeight = 17.sp)
            FutaButton(text = buttonText, icon = Icons.Default.Delete, variant = FutaButtonVariant.DANGER, enabled = enabled, height = 40.dp, onClick = onClick)
        }
    }
}
