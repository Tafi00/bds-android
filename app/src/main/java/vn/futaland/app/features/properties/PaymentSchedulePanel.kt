package vn.futaland.app.features.properties


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.ToastCenter

@Composable
fun PaymentSchedulePanel(
    property: JSONValue,
    modifier: Modifier = Modifier,
    unitLabelOverride: String? = null,
    showHeaderControls: Boolean = true,
    showExportButton: Boolean = true
) {
    val context = LocalContext.current
    val unitCode = remember(property.id, unitLabelOverride) {
        unitLabelOverride?.ifEmpty { null } ?: PaymentScheduleEngine.resolveUnitCode(property)
    }

    var remotePolicies by remember(property.id) { mutableStateOf<List<JSONValue>>(emptyList()) }

    LaunchedEffect(property.id) {
        val directPolicies = property["paymentPolicies"].array
        if (directPolicies.isEmpty()) {
            val projectName = property["zone"].string.trim().ifEmpty { property["projectName"].string.trim() }
            val projectId = property["projectId"].string.trim()
            if (projectName.isNotEmpty() || projectId.isNotEmpty()) {
                try {
                    val query = mutableMapOf<String, String>()
                    if (projectName.isNotEmpty()) query["projectName"] = projectName
                    if (projectId.isNotEmpty()) query["projectId"] = projectId
                    val res = APIClient.get().request("/payment-policies", query = query)
                    val data = res["data"].array
                    if (data.isNotEmpty()) {
                        remotePolicies = data
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val policies = remember(property.id, remotePolicies) {
        PaymentScheduleEngine.parsePolicies(property, externalPolicies = remotePolicies)
    }

    if (policies.isEmpty()) return

    val defaultBasePrice = remember(property.id, policies) {
        PaymentScheduleEngine.resolveDefaultBasePrice(property, policies)
    }

    var customBasePrice by remember(property.id, defaultBasePrice) { mutableStateOf<Long?>(null) }
    val activeBasePrice = customBasePrice ?: defaultBasePrice

    var priceInputText by remember(activeBasePrice) {
        mutableStateOf(PaymentScheduleEngine.formatNumberOnly(activeBasePrice))
    }
    var isEditingPrice by remember { mutableStateOf(false) }

    var selectedPolicyIndex by remember(property.id) { mutableIntStateOf(0) }
    val activePolicy = policies.getOrNull(selectedPolicyIndex) ?: policies[0]

    val scheduleResult = remember(activePolicy, activeBasePrice) {
        PaymentScheduleEngine.calculate(activePolicy, activeBasePrice)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Policy tabs
        if (policies.size > 1) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                policies.forEachIndexed { index, policy ->
                    val isSelected = selectedPolicyIndex == index
                    Column(
                        modifier = Modifier
                            .clickable { selectedPolicyIndex = index }
                            .widthIn(max = 180.dp)
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = policy.name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) FutaColors.Navy else Color(0xFF8492A2),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .height(2.5.dp)
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF238451) else Color.Transparent,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
        }

        // 2. Main Simulation Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFDFE6ED)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEAF8F1)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = Color(0xFF238451),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "BỘ MÔ PHỎNG DỮ LIỆU THỰC TẾ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF238451),
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                // Heading
                Text(
                    text = "Bảng Tính Minh Họa Giá Trị Thanh Toán Theo Đợt",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = FutaColors.Navy,
                    lineHeight = 22.sp
                )

                // Controls: Unit code, Price edit, Export
                if (showHeaderControls) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (unitCode.isNotEmpty() && unitCode != "—") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Giá căn",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF68788B)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFDFE6ED))
                                    ) {
                                        Text(
                                            text = unitCode,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.Navy,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Giá niêm yết",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF68788B)
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFCFD9E3)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = if (isEditingPrice) priceInputText else PaymentScheduleEngine.formatNumberOnly(activeBasePrice),
                                            onValueChange = { newStr ->
                                                isEditingPrice = true
                                                priceInputText = newStr
                                                val digits = newStr.filter { it.isDigit() }
                                                val parsed = digits.toLongOrNull()
                                                if (parsed != null && parsed > 0) {
                                                    customBasePrice = parsed
                                                } else if (digits.isEmpty()) {
                                                    customBasePrice = 0L
                                                }
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FutaColors.Navy
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "VND",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8492A2)
                                        )
                                    }
                                }
                            }
                        }

                        if (showExportButton) {
                            OutlinedButton(
                                onClick = {
                                    // Share a real .xlsx file: plain text made chat apps such as
                                    // Zalo reject the attachment ("File bị lỗi").
                                    try {
                                        PaymentScheduleExcelExporter.share(
                                            context = context,
                                            policy = activePolicy,
                                            result = scheduleResult,
                                            unitLabel = unitCode,
                                            projectName = property["projectName"].string.ifBlank { property["project"]["name"].string }
                                        )
                                    } catch (e: Exception) {
                                        ToastCenter.show("Không tạo được file bảng tính. Vui lòng thử lại.", isError = true)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFDFE6ED)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = FutaColors.Navy,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Xuất bảng tính (Excel / Chia sẻ)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                            }
                        }
                    }
                }

                // 3. Four Overview Metric Cards (2x2 Grid)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Tổng giá niêm yết
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFDFE6ED)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "TỔNG GIÁ NIÊM YẾT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF68788B),
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = PaymentScheduleEngine.formatVnd(scheduleResult.basePrice),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = FutaColors.Navy,
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 2: Chiết khấu
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFDFE6ED)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val pctLabel = if (scheduleResult.discountPercent > 0) "%.1f%%".format(scheduleResult.discountPercent) else "0%"
                                Text(
                                    text = "CHIẾT KHẤU ($pctLabel)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF68788B),
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "- " + PaymentScheduleEngine.formatVnd(scheduleResult.discountAmount),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFDC2626),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 3: Tiền đặt cọc quy định
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFDFE6ED)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "TIỀN ĐẶT CỌC QUY ĐỊNH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF68788B),
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = PaymentScheduleEngine.formatVnd(scheduleResult.depositAmount),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = FutaColors.Navy,
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 4: Giá thanh toán thực tế (Orange Highlight)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF7ED),
                            border = BorderStroke(1.dp, Color(0xFFFDBA74)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "GIÁ THANH TOÁN THỰC TẾ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC2740C),
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = PaymentScheduleEngine.formatVnd(scheduleResult.netPrice),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFEA580C),
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Có thể chỉnh theo các đợt thực tế",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFC2740C),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // 4. Installments list
                if (scheduleResult.rows.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        scheduleResult.rows.forEach { row ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row Header: Circle order + Name + Percent badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = FutaColors.Navy,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${row.order}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
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
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        row.percent?.let { pct ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFFFF7ED)
                                            ) {
                                                Text(
                                                    text = "%.1f%%".format(pct).replace(".0%", "%"),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFEA580C),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)

                                    // Row amounts: Needed vs Cumulative
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            Text(
                                                text = "Số tiền thực tế cần đóng:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF64748B)
                                            )
                                            Text(
                                                text = PaymentScheduleEngine.formatVnd(row.amount),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FutaColors.Navy
                                            )
                                            if (row.includesDeposit) {
                                                Text(
                                                    text = "Bao gồm ${PaymentScheduleEngine.formatVnd(scheduleResult.depositAmount)} tiền đặt cọc",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF8492A2)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "Tích lũy đã đóng:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF64748B),
                                                textAlign = TextAlign.End
                                            )
                                            Text(
                                                text = PaymentScheduleEngine.formatVnd(row.cumulative),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF238451),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }

                                    // Condition note
                                    if (row.note.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Điều kiện:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF68788B)
                                            )
                                            Text(
                                                text = row.note,
                                                fontSize = 11.sp,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Disclaimer
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Đây là dữ liệu tham khảo, sẽ điều chỉnh tùy thuộc vào điều kiện thực tế bàn giao, thời điểm giải ngân và các chính sách ưu đãi được cập nhật.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
