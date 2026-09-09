package vn.futaland.app.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Web-parity Status Badge with colored capsule background and distinct font weight.
 */
@Composable
fun FutaStatusBadge(
    title: String,
    modifier: Modifier = Modifier
) {
    val normalized = title.trim().lowercase()
    val (bg, fg, label) = when (normalized) {
        "selling", "active", "published", "hoạt động", "đang mở bán", "đã duyệt", "hoàn thành", "thành công", "paid", "đã thanh toán" ->
            Triple(FutaColors.BrandGreen.copy(alpha = 0.14f), FutaColors.BrandGreen, "Đang mở bán")

        "upcoming", "sắp mở bán" ->
            Triple(Color(0xFF2563EB).copy(alpha = 0.14f), Color(0xFF2563EB), "Sắp mở bán")

        "pending", "draft", "chờ duyệt", "bản nháp", "đang xử lý", "giữ chỗ", "holding" ->
            Triple(FutaColors.BrandOrange.copy(alpha = 0.14f), FutaColors.BrandOrange, "Đang giữ chỗ")

        "sold_out", "đã bán hết", "cancelled", "rejected", "inactive", "hết hạn", "từ chối", "khoá", "đã xoá", "hủy", "thất bại" ->
            Triple(Color(0xFFDC2626).copy(alpha = 0.14f), Color(0xFFDC2626), "Đã bán hết")

        else -> Triple(Color(0xFF6B7280).copy(alpha = 0.14f), Color(0xFF374151), title)
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(0.5.dp, fg.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * ERP Locked/Holding indicator badge.
 */
@Composable
fun FutaErpBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = Color(0xFFB45309)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "ERP",
                color = Color(0xFFB45309),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
