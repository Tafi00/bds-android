package vn.futaland.app.features.properties

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.FutaCard
import vn.futaland.app.designsystem.FutaColors

@Composable
fun PaymentSchedulePanel(
    property: JSONValue,
    modifier: Modifier = Modifier
) {
    val policies = remember(property.id) {
        PaymentScheduleEngine.parsePolicies(property)
    }

    if (policies.isEmpty()) return

    val defaultBasePrice = remember(property.id, policies) {
        PaymentScheduleEngine.resolveDefaultBasePrice(property, policies)
    }

    var selectedPolicyIndex by remember(property.id) { mutableIntStateOf(0) }
    val activePolicy = policies.getOrNull(selectedPolicyIndex) ?: policies[0]

    val scheduleResult = remember(activePolicy, defaultBasePrice) {
        PaymentScheduleEngine.calculate(activePolicy, defaultBasePrice)
    }

    FutaCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = FutaColors.PeachBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = FutaColors.BrandGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "BẢNG TÍNH MINH HỌA GIÁ TRỊ THANH TOÁN THEO ĐỢT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "Tra cứu các đợt thanh toán theo chính sách đang áp dụng.",
                fontSize = 12.5.sp,
                color = FutaColors.Slate
            )

            // Policy tabs (if > 1)
            if (policies.size > 1) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE7EEF5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        policies.forEachIndexed { index, policy ->
                            val isSelected = selectedPolicyIndex == index
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selectedPolicyIndex = index }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = policy.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) FutaColors.Navy else Color(0xFF405269),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Summary Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFEDF1F5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Giá thanh toán thực tế", fontSize = 11.5.sp, color = FutaColors.Slate)
                        Text(
                            text = PaymentScheduleEngine.formatVnd(scheduleResult.netPrice),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen
                        )
                    }
                    if (scheduleResult.rows.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = FutaColors.MintBg
                        ) {
                            Text(
                                text = "${scheduleResult.rows.size} đợt thanh toán",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FutaColors.BrandGreenDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (scheduleResult.rows.isNotEmpty()) {
                // Table header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Đợt / Tiến độ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Slate,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = "Tỷ lệ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Slate,
                        modifier = Modifier.weight(0.6f),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "Số tiền cần đóng",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Slate,
                        modifier = Modifier.weight(1.3f),
                        textAlign = TextAlign.End
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // Rows
                scheduleResult.rows.forEachIndexed { idx, row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = row.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                if (row.timing.isNotEmpty()) {
                                    Text(
                                        text = row.timing,
                                        fontSize = 11.5.sp,
                                        color = FutaColors.Slate
                                    )
                                }
                                if (row.note.isNotEmpty()) {
                                    Text(
                                        text = row.note,
                                        fontSize = 11.sp,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }
                            Text(
                                text = row.percent?.let { "%.1f%%".format(it).replace(".0%", "%") } ?: "—",
                                fontSize = 12.5.sp,
                                color = FutaColors.Slate,
                                modifier = Modifier.weight(0.6f),
                                textAlign = TextAlign.End
                            )
                            Column(
                                modifier = Modifier.weight(1.3f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = PaymentScheduleEngine.formatVnd(row.amount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = "Lũy kế: ${PaymentScheduleEngine.formatVnd(row.cumulative)}",
                                    fontSize = 10.5.sp,
                                    color = FutaColors.Muted,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                    if (idx < scheduleResult.rows.size - 1) {
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
                    }
                }
            }

            // Disclaimer
            Text(
                text = "* Bảng tính mang tính chất minh họa tham khảo theo chính sách bán hàng áp dụng của chủ đầu tư.",
                fontSize = 11.sp,
                color = FutaColors.Muted,
                lineHeight = 15.sp
            )
        }
    }
}
