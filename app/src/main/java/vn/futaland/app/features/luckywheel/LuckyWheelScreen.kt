package vn.futaland.app.features.luckywheel

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.i18n.AppLanguage
import vn.futaland.app.core.i18n.I18n
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.account.AdvisorLabeledRow
import vn.futaland.app.features.account.AdvisorTopBar
import vn.futaland.app.features.account.copyToClipboard
import java.util.UUID

// Design tokens aligned with web v3 / iOS `LwColors`.
private object LwColors {
    val GreenDark = Color(0xFF023C31)
    val ForestSegment = Color(0xFF005442)
    val IvorySegment = Color(0xFFFFF8ED)
    val PageBg = Color(0xFFFFFDF9)
    val CardBg = Color(0xFFFFFAF3)
    val Border = Color(0xFFE7DDD0)
    val Orange = Color(0xFFF36F21)
    val OrangeDark = Color(0xFFBD4D12)
    val BrassRim = Color(0xFFB9792D)
    val BrassTicks = Color(0xFFA87130)
    val TicksBg = Color(0xFFF8E6CA)
    val BrassSeparator = Color(0xFFD7B16A)
    val BrassDiscBorder = Color(0xFFA56C28)
    val DiscRimBg = Color(0xFFFBF0DD)
    val Ink = Color(0xFF10293F)
    val Muted = Color(0xFF5F6E7D)
}

private data class VisualSlot(val primary: String, val secondary: String, val icon: ImageVector)

/** Voucher amount printed on a slice: the Vietnamese text, or the amount in the app language's currency style. */
private fun voucherAmount(value: Double, vi: String): String =
    if (I18n.language == AppLanguage.VI) vi else LocalizedPrice.compact(value)

private fun makeVisualSlots(prizes: List<JSONValue>): List<VisualSlot> = prizes.map { prize ->
    val id = prize["id"].string.lowercase()
    val label = prize["label"].string
    val shortLabel = prize["shortLabel"].string
    val value = prize["value"].string
    val ticket = Icons.Default.ConfirmationNumber
    val gift = Icons.Default.CardGiftcard
    when {
        id.contains("100k") -> VisualSlot(voucherAmount(100_000.0, "100.000đ"), "Voucher", ticket)
        id.contains("500k") -> VisualSlot(voucherAmount(500_000.0, "500.000đ"), "Voucher", gift)
        id.contains("50k") -> VisualSlot(voucherAmount(50_000.0, "50.000đ"), "Voucher", ticket)
        id.contains("1m") || id.contains("1trieu") -> VisualSlot(voucherAmount(1_000_000.0, "1 TRIỆU"), "Voucher", ticket)
        id.contains("good-luck") || id.contains("may-man") || label.lowercase().contains("may mắn") -> VisualSlot("Chúc mừng", "lần sau", gift)
        id.contains("discount") || label.lowercase().contains("giảm giá") ->
            VisualSlot("Giảm giá", value.ifEmpty { shortLabel.ifEmpty { "10%" } }, Icons.Default.Percent)
        id.contains("200k") -> VisualSlot(voucherAmount(200_000.0, "200.000đ"), "Voucher", ticket)
        else -> {
            val icon = when {
                prize["fulfillmentType"].string == "none" -> gift
                label.lowercase().contains("giảm") -> Icons.Default.Percent
                label.lowercase().contains("voucher") -> ticket
                else -> gift
            }
            VisualSlot(shortLabel.ifEmpty { label.ifEmpty { "Quà tặng" } }, if (label.lowercase().contains("voucher")) "Voucher" else "", icon)
        }
    }
}

/**
 * The backend composes rule progress lines in Vietnamese with numbers embedded
 * (lucky-wheel-quota.service.ts); numbers are matched and put back into the translated template.
 */
private fun localizedProgress(text: String): String {
    if (I18n.language == AppLanguage.VI || text.isEmpty()) return text
    val templates = listOf(
        "Hôm nay: Còn {0}/{1} lượt",
        "Đã kích hoạt TVV (+{0} lượt)",
        "Trở thành TVV để nhận ngay +{0} lượt",
        "Đã tích lũy {0} giao dịch (+{1} lượt)",
        "Phát sinh giao dịch để nhận +{0} lượt / giao dịch",
        "Thành viên FUTA Land (+{0} lượt)",
        "Đã nhận +{0} lượt quay đặc quyền"
    )
    for (template in templates) {
        val pattern = Regex("\\{\\d\\}").split(template).joinToString("(\\d+)") { Regex.escape(it) }
        val match = Regex("^$pattern$").find(text) ?: continue
        return tr(template, *match.groupValues.drop(1).toTypedArray())
    }
    return tr(text)
}

private fun fulfillmentInfo(status: String): Pair<String, Color> = when (status) {
    "pending_details" -> "Chờ thông tin" to LwColors.Orange
    "pending_fulfillment" -> "Chờ xử lý" to Color(0xFF2563EB)
    "shipping" -> "Đang giao" to Color(0xFF7C3AED)
    "received" -> "Đã nhận" to Color(0xFF16A34A)
    "delivered" -> "Hoàn tất" to Color(0xFF16A34A)
    "not_applicable" -> "Không áp dụng" to Color(0xFF6B7280)
    else -> status.ifEmpty { "Thành công" } to Color(0xFF6B7280)
}

private fun historyItems(data: JSONValue): List<JSONValue> =
    data["items"].array.ifEmpty { data.array.ifEmpty { data["records"].array } }

/**
 * User lucky wheel (iOS `LuckyWheelView`): GET /lucky-wheel + /lucky-wheel/history,
 * POST /lucky-wheel/spin (server-selected prize), PUT /lucky-wheel/spins/:id/recipient.
 */
@Composable
fun LuckyWheelScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<JSONValue?>(null) }
    var history by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var historyTotal by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSpinning by remember { mutableStateOf(false) }
    var wonPrize by remember { mutableStateOf<JSONValue?>(null) }
    var selectedSpin by remember { mutableStateOf<JSONValue?>(null) }
    var showAllHistory by remember { mutableStateOf(false) }
    var recipientSpin by remember { mutableStateOf<JSONValue?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val rotation = remember { Animatable(0f) }

    suspend fun loadAll() {
        isLoading = true
        try {
            coroutineScope {
                val s = async { APIClient.get().request("/lucky-wheel") }
                val h = async { APIClient.get().request("/lucky-wheel/history") }
                state = s.await()["data"]
                val hData = h.await()["data"]
                history = historyItems(hData)
                historyTotal = hData["total"].int
            }
            loadError = null
        } catch (e: Exception) {
            if (state == null) loadError = e.message ?: tr("Không tải được dữ liệu") else errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    fun performSpin() {
        val current = state ?: return
        if (isSpinning) return
        scope.launch {
            isSpinning = true
            try {
                val body = buildJsonObject {
                    put("requestId", UUID.randomUUID().toString())
                    current["activeEventCode"].string.takeIf { it.isNotEmpty() }?.let { put("eventCode", it) }
                }.toString()
                val spinResult = APIClient.get().request("/lucky-wheel/spin", method = "POST", bodyJson = body)["data"]
                // Land exactly on the server-selected prize: slice i is centered at i * slice from 12 o'clock.
                val prizes = current["prizes"].array
                val winningIndex = prizes.indexOfFirst { it["id"].string == spinResult["prizeId"].string }.coerceAtLeast(0)
                val sliceAngle = 360f / maxOf(prizes.size, 1)
                val targetNormalized = (360f - (winningIndex * sliceAngle) % 360f) % 360f
                val currentNormalized = rotation.value % 360f
                val diff = (targetNormalized - currentNormalized + 360f) % 360f
                rotation.animateTo(
                    targetValue = rotation.value + 360f * 5 + diff,
                    animationSpec = tween(durationMillis = 3600, easing = CubicBezierEasing(0.15f, 0.85f, 0.2f, 1f))
                )
                delay(200)
                wonPrize = spinResult
                loadAll()
            } catch (e: Exception) {
                // Never invent a prize: report what the server said.
                errorMessage = e.message ?: tr("Không thể quay lúc này")
            } finally {
                isSpinning = false
            }
        }
    }

    fun handleRuleAction(rule: JSONValue) {
        if (rule["type"].string == "daily_checkin") {
            scope.launch {
                loadAll()
                infoMessage = tr("Đã cập nhật tiến độ điểm danh hôm nay!")
            }
        } else {
            infoMessage = tr("Hãy thực hiện theo thể lệ: {0}", tr(rule["title"].string))
        }
    }

    Scaffold(
        topBar = {
            AdvisorTopBar("Vòng quay may mắn", onBack) {
                IconButton(onClick = { scope.launch { loadAll() } }, enabled = !isSpinning) {
                    Icon(Icons.Default.Refresh, tr("Làm mới"), tint = LwColors.GreenDark)
                }
            }
        },
        containerColor = LwColors.PageBg
    ) { padding ->
        val current = state
        when {
            current == null && isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LwColors.ForestSegment)
            }
            current == null -> FutaEmptyState(
                title = "Không tải được dữ liệu",
                message = loadError.orEmpty(),
                icon = Icons.Default.ErrorOutline,
                modifier = Modifier.padding(padding),
                actionButton = { FutaButton(text = "Thử lại", variant = FutaButtonVariant.OUTLINE, onClick = { scope.launch { loadAll() } }) }
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                HeaderSection(current)
                val remaining = current["remainingSpins"].int
                val active = current["active"].bool
                WheelGraphic(
                    prizes = current["prizes"].array,
                    rotation = rotation.value,
                    isSpinning = isSpinning,
                    isActive = active,
                    remainingSpins = remaining,
                    onSpin = { performSpin() }
                )
                if (remaining > 0 && active && !isSpinning) {
                    Button(
                        onClick = { performSpin() },
                        colors = ButtonDefaults.buttonColors(containerColor = LwColors.Orange),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 13.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Autorenew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Quay ngay ({0} lượt khả dụng)", remaining), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                if (!active) {
                    Surface(shape = CircleShape, color = Color(0xFFF97316).copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                current["unavailableReason"].string.ifEmpty { "Vòng quay hiện đang tạm đóng." },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LwColors.Muted
                            )
                        }
                    }
                }
                QuotaCard(current)
                val rules = current["rules"].array
                if (rules.isNotEmpty()) RulesSection(rules, onPerform = { handleRuleAction(it) })
                TermsSection(current["terms"].string)
                HistorySection(
                    history = history,
                    onSelect = { selectedSpin = it },
                    onClaim = { recipientSpin = it },
                    onViewAll = { showAllHistory = true }
                )
            }
        }
    }

    wonPrize?.let { spin ->
        CelebrationSheet(
            spin = spin,
            remainingSpins = state?.get("remainingSpins")?.int ?: 0,
            onDismiss = { wonPrize = null },
            onClaimNow = {
                wonPrize = null
                if (spin["fulfillmentType"].string == "physical") recipientSpin = spin
            },
            onSpinAgain = {
                wonPrize = null
                scope.launch {
                    delay(300)
                    performSpin()
                }
            }
        )
    }

    selectedSpin?.let { spin ->
        RewardDetailSheet(
            spin = spin,
            onDismiss = { selectedSpin = null },
            onEnterRecipient = {
                selectedSpin = null
                recipientSpin = spin
            }
        )
    }

    if (showAllHistory) {
        FullHistorySheet(
            initial = history,
            total = historyTotal,
            onDismiss = { showAllHistory = false },
            onSelect = {
                showAllHistory = false
                selectedSpin = it
            }
        )
    }

    recipientSpin?.let { spin ->
        RecipientSheet(
            spin = spin,
            onDismiss = { recipientSpin = null },
            onSaved = {
                recipientSpin = null
                loadAll()
            }
        )
    }

    FutaDialog(
        visible = infoMessage != null,
        onDismiss = { infoMessage = null },
        title = "Thông báo",
        confirmText = "Đóng",
        onConfirm = {},
        cancelText = null
    ) { VerbatimText(infoMessage.orEmpty(), fontSize = 13.5.sp, color = FutaColors.Slate) }

    FutaDialog(
        visible = errorMessage != null,
        onDismiss = { errorMessage = null },
        title = "Lỗi",
        confirmText = "Đóng",
        onConfirm = {},
        cancelText = null
    ) { Text(errorMessage.orEmpty(), fontSize = 13.5.sp, color = FutaColors.Slate) }
}

// -------------------------------------------------------------
// Header, wheel, quota, missions, terms, history
// -------------------------------------------------------------

@Composable
private fun HeaderSection(state: JSONValue) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            state["title"].string.ifEmpty { "Vòng quay may mắn" },
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = LwColors.GreenDark
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = LwColors.Orange.copy(alpha = 0.12f)) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ConfirmationNumber, null, tint = LwColors.Orange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("{0} lượt quay", state["remainingSpins"].int), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LwColors.Orange)
                }
            }
            val eventCode = state["activeEventCode"].string
            if (eventCode.isNotEmpty()) {
                Surface(shape = CircleShape, color = LwColors.ForestSegment) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🎟️ " + tr("Sự kiện:"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.width(4.dp))
                        VerbatimText(eventCode.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        if (state["description"].string.isNotEmpty()) {
            Text(state["description"].string, fontSize = 13.5.sp, color = LwColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WheelGraphic(
    prizes: List<JSONValue>,
    rotation: Float,
    isSpinning: Boolean,
    isActive: Boolean,
    remainingSpins: Int,
    onSpin: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val diameter = minOf(308.dp, screenWidth - 48.dp)
    val slots = remember(prizes) { makeVisualSlots(prizes) }
    val count = maxOf(slots.size, 1)
    val sliceAngle = 360f / count

    Box(modifier = Modifier.size(width = diameter, height = diameter + 20.dp), contentAlignment = Alignment.Center) {
        // Guide ring + brass rim
        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(8.dp, CircleShape, ambientColor = Color(0x294A381F), spotColor = Color(0x294A381F))
                .clip(CircleShape)
                .background(LwColors.DiscRimBg)
                .border(2.5.dp, LwColors.BrassRim, CircleShape)
        )
        // Ticks layer
        Canvas(modifier = Modifier.size(diameter - 14.dp)) {
            val r = size.minDimension / 2
            drawCircle(LwColors.TicksBg, r)
            drawCircle(LwColors.BrassDiscBorder, r, style = Stroke(1.dp.toPx()))
            for (i in 0 until 48) {
                val angle = Math.toRadians(i * 7.5 - 90)
                val outer = r - 5.dp.toPx()
                val inner = outer - 5.dp.toPx()
                val c = center
                drawLine(
                    LwColors.BrassTicks,
                    Offset(c.x + (inner * kotlin.math.cos(angle)).toFloat(), c.y + (inner * kotlin.math.sin(angle)).toFloat()),
                    Offset(c.x + (outer * kotlin.math.cos(angle)).toFloat(), c.y + (outer * kotlin.math.sin(angle)).toFloat()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
        // Rotating disc: slices + labels
        val discSize = diameter - 32.dp
        Box(
            modifier = Modifier
                .size(discSize)
                .graphicsLayer { rotationZ = rotation }
                .clip(CircleShape)
                .border(1.5.dp, LwColors.BrassDiscBorder, CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                for (i in 0 until count) {
                    val start = -90f + i * sliceAngle - sliceAngle / 2
                    val color = if (i % 2 == 0) LwColors.IvorySegment else LwColors.ForestSegment
                    drawArc(color, start, sliceAngle, useCenter = true, topLeft = Offset.Zero, size = Size(r * 2, r * 2))
                    drawArc(LwColors.BrassSeparator, start, sliceAngle, useCenter = true, topLeft = Offset.Zero, size = Size(r * 2, r * 2), style = Stroke(1.5.dp.toPx()))
                }
            }
            slots.forEachIndexed { i, slot ->
                val isIvory = i % 2 == 0
                val textColor = if (isIvory) Color(0xFF102D27) else Color.White
                val iconColor = if (isIvory) Color(0xFFA96D27) else Color(0xFFF2CA80)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(i * sliceAngle),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .width(80.dp)
                            .offset(y = -(discSize / 2) * 0.64f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(slot.icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
                        Text(slot.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, textAlign = TextAlign.Center)
                        if (slot.secondary.isNotEmpty()) {
                            Text(slot.secondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = textColor.copy(alpha = 0.9f), maxLines = 1)
                        }
                    }
                }
            }
        }
        // Center spin button
        val enabled = !isSpinning && isActive && remainingSpins >= 1
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onSpin),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(LwColors.GreenDark.copy(alpha = 0.32f), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                drawCircle(Color(0xFFE8F0DB), 49.dp.toPx() - 2.5.dp.toPx(), style = Stroke(5.dp.toPx()))
                drawCircle(LwColors.BrassTicks, 44.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                drawCircle(Brush.verticalGradient(listOf(LwColors.Orange, Color(0xFFE45F14))), 42.dp.toPx())
                drawCircle(Color(0xFFFFF3DF), 40.dp.toPx(), style = Stroke(4.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val (l1, l2, l3) = when {
                    isSpinning -> Triple("ĐANG", "QUAY", tr("Chúc may mắn"))
                    !isActive -> Triple("TẠM", "ĐÓNG", tr("Hẹn dịp sau"))
                    remainingSpins < 1 -> Triple("HẾT", "LƯỢT", tr("Làm nhiệm vụ"))
                    else -> Triple("QUAY", "NGAY", tr("Còn {0} lượt", remainingSpins))
                }
                Text(l1, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 15.sp)
                Text(l2, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 15.sp)
                VerbatimText(l3, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f))
            }
        }
        // Top pointer
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
                .size(width = 38.dp, height = 52.dp)
        ) {
            val topRadius = size.width / 2
            val path = Path().apply {
                arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.width), 180f, 180f, false)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(LwColors.Orange, Color(0xFFE45F14))))
            drawPath(path, LwColors.OrangeDark, style = Stroke(1.5.dp.toPx()))
            drawCircle(Color(0xFFF7D59C), 7.dp.toPx(), Offset(size.width / 2, topRadius - 10.dp.toPx() + topRadius / 2))
            drawCircle(Color.White, 7.dp.toPx(), Offset(size.width / 2, topRadius - 10.dp.toPx() + topRadius / 2), style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun LwCard(modifier: Modifier = Modifier, background: Color = LwColors.CardBg, radius: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.dp))
            .background(background)
            .border(1.dp, LwColors.Border, RoundedCornerShape(radius.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun QuotaCard(state: JSONValue) {
    LwCard(radius = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("LƯỢT QUAY KHẢ DỤNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LwColors.Muted)
                VerbatimText("${state["remainingSpins"].int}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LwColors.GreenDark)
            }
            val limit = state["dailySpinLimit"].int
            if (limit > 0) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("HẠN MỨC HÔM NAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LwColors.Muted)
                    Text(tr("{0} lượt / ngày", limit), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LwColors.Ink)
                }
            }
        }
    }
}

private fun ruleIcon(type: String): ImageVector = when (type) {
    "daily_checkin" -> Icons.Default.EventAvailable
    "become_advisor" -> Icons.Default.WorkspacePremium
    "transaction_success" -> Icons.Default.CreditCard
    "account_registered" -> Icons.Default.PhoneIphone
    else -> Icons.Default.CardGiftcard
}

@Composable
private fun RulesSection(rules: List<JSONValue>, onPerform: (JSONValue) -> Unit) {
    val completed = rules.count { it["completed"].bool }
    val green = Color(0xFF16A34A)
    LwCard(background = Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nhiệm vụ nhận lượt", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = LwColors.GreenDark, modifier = Modifier.weight(1f))
            Text(tr("{0}/{1} nhiệm vụ", completed, rules.size), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LwColors.Muted)
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { completed.toFloat() / maxOf(rules.size, 1) },
            color = LwColors.ForestSegment,
            trackColor = Color(0xFFE9E3D8),
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rules.forEach { r ->
                val isCompleted = r["completed"].bool
                val spins = r["earnedSpins"].int.takeIf { it > 0 } ?: r["spins"].int
                val tint = if (isCompleted) green else LwColors.Orange
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LwColors.CardBg)
                        .border(1.dp, LwColors.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(ruleIcon(r["type"].string), null, tint = tint, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(r["title"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = LwColors.Ink)
                        val progress = r["progressText"].string.ifEmpty { r["description"].string }
                        if (progress.isNotEmpty()) VerbatimText(localizedProgress(progress), fontSize = 12.sp, color = LwColors.Muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = CircleShape, color = tint.copy(alpha = 0.15f)) {
                            Text(
                                if (isCompleted) tr("Đã nhận") else tr("+{0} lượt", spins),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = tint,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (isCompleted) {
                            Icon(Icons.Default.CheckCircle, null, tint = green, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                "Thực hiện",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(LwColors.ForestSegment)
                                    .clickable { onPerform(r) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = LwColors.Muted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("Mỗi nhiệm vụ chỉ được tính theo thể lệ chương trình.", fontSize = 11.sp, color = LwColors.Muted)
        }
    }
}

@Composable
private fun TermsSection(termsString: String) {
    val raw = termsString.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val terms = if (raw.size > 1 || (raw.isNotEmpty() && !raw[0].startsWith("Mỗi tài khoản được quay"))) raw else listOf(
        "Mỗi lượt quay tương ứng 1 cơ hội trúng thưởng.",
        "Lượt quay được cộng khi hoàn thành nhiệm vụ hoặc theo chương trình.",
        "Giải thưởng là voucher có giá trị sử dụng theo điều kiện của FUTA Land.",
        "Quyết định của FUTA Land là quyết định cuối cùng."
    )
    LwCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(LwColors.BrassTicks))
            Spacer(Modifier.width(8.dp))
            Text("Thể lệ chương trình", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = LwColors.GreenDark)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            terms.forEach { term ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LwColors.BrassTicks)
                    Spacer(Modifier.width(8.dp))
                    Text(term, fontSize = 12.sp, color = LwColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun FulfillmentBadge(status: String) {
    val (label, color) = fulfillmentInfo(status)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun RewardCodeChip(code: String, onClick: () -> Unit) {
    VerbatimText(
        code,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = LwColors.GreenDark,
        maxLines = 1,
        style = TextStyle(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LwColors.ForestSegment.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
    )
}

@Composable
private fun HistorySection(
    history: List<JSONValue>,
    onSelect: (JSONValue) -> Unit,
    onClaim: (JSONValue) -> Unit,
    onViewAll: () -> Unit
) {
    LwCard(background = Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lịch sử nhận quà", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = LwColors.GreenDark, modifier = Modifier.weight(1f))
            if (history.size > 4) {
                Text("Xem tất cả", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LwColors.Orange, modifier = Modifier.clickable(onClick = onViewAll))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            Text(
                "Bạn chưa có lượt quay nào. Hãy quay ngay!",
                fontSize = 13.5.sp,
                color = LwColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.take(4).forEach { spin ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LwColors.CardBg)
                            .border(1.dp, LwColors.Border, RoundedCornerShape(12.dp))
                            .clickable { onSelect(spin) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CardGiftcard, null, tint = LwColors.BrassTicks, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(spin["prizeLabel"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = LwColors.Ink)
                            if (spin["rewardCode"].string.isNotEmpty()) RewardCodeChip(spin["rewardCode"].string) { onSelect(spin) }
                        }
                        val status = spin["fulfillmentStatus"].string
                        if (status == "pending_details") {
                            Text(
                                "Xác nhận",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(LwColors.Orange)
                                    .clickable { onClaim(spin) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        } else {
                            FulfillmentBadge(status)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sheets: celebration, reward detail, full history, recipient form
// -------------------------------------------------------------

private fun generateQrBitmap(content: String): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val size = 400
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun RewardQr(code: String, size: Int) {
    val bitmap = remember(code) { generateQrBitmap(code) } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = tr("Mã QR đối soát"),
        modifier = Modifier
            .size(size.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(10.dp)
    )
}

@Composable
private fun CelebrationSheet(
    spin: JSONValue,
    remainingSpins: Int,
    onDismiss: () -> Unit,
    onClaimNow: () -> Unit,
    onSpinAgain: () -> Unit
) {
    val context = LocalContext.current
    val isConsolation = spin["fulfillmentType"].string == "none"
    val isPhysical = spin["fulfillmentType"].string == "physical"
    val rewardCode = spin["rewardCode"].string
    var copied by remember { mutableStateOf(false) }

    FutaBottomSheet(visible = true, onDismiss = onDismiss, title = tr("Kết quả vòng quay")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(160.dp).padding(top = 8.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(LwColors.BrassSeparator))
                Icon(Icons.Default.Diamond, null, tint = LwColors.BrassTicks, modifier = Modifier.size(10.dp).padding(horizontal = 2.dp))
                Box(Modifier.weight(1f).height(1.dp).background(LwColors.BrassSeparator))
            }
            Text("KẾT QUẢ VÒNG QUAY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LwColors.BrassTicks, letterSpacing = 1.5.sp)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isConsolation) Color(0xFFF4EDE1) else LwColors.ForestSegment.copy(alpha = 0.12f))
                    .border(2.dp, if (isConsolation) LwColors.Border else LwColors.BrassSeparator, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isConsolation) Icons.Default.CardGiftcard else Icons.Default.EmojiEvents,
                    null,
                    tint = if (isConsolation) LwColors.Muted else LwColors.Orange,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                if (isConsolation) "Hẹn bạn ở lượt tiếp theo" else "Chúc mừng bạn!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = LwColors.GreenDark
            )
            Text(
                when {
                    isConsolation -> "May mắn vẫn đang chờ ở những lượt quay tiếp theo."
                    isPhysical -> "Phần quà hiện vật đã được giữ cho bạn. Hãy xác nhận thông tin để ban tổ chức giao quà."
                    else -> "Phần thưởng điện tử đã được phát hành vào tài khoản của bạn."
                },
                fontSize = 13.5.sp,
                color = LwColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LwCard(radius = 14) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isConsolation) "KẾT QUẢ" else "PHẦN THƯỞNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LwColors.Muted, letterSpacing = 1.sp)
                    Text(spin["prizeLabel"].string, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LwColors.GreenDark, textAlign = TextAlign.Center)
                }
            }
            if (!isConsolation && rewardCode.isNotEmpty()) {
                LwCard(radius = 14) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Mã quà tặng & QR đối soát", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LwColors.Ink, modifier = Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    copyToClipboard(context, rewardCode, "Đã chép")
                                    copied = true
                                }
                            ) {
                                Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, tint = LwColors.Orange, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (copied) "Đã chép" else "Sao chép", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LwColors.Orange)
                            }
                        }
                        RewardQr(rewardCode, 150)
                        Text("Đưa mã này cho nhân viên quét đối soát", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = LwColors.Muted)
                        VerbatimText(
                            rewardCode,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = LwColors.GreenDark,
                            style = TextStyle(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, LwColors.Border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        Text(
                            if (isPhysical) "Đưa mã QR hoặc mã kèm SĐT cho nhân viên/admin khi nhận quà để quét xác nhận trực tiếp."
                            else "Mã dùng để tra cứu phần thưởng đã phát hành cho tài khoản của bạn.",
                            fontSize = 11.sp,
                            color = LwColors.Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (isPhysical && spin["fulfillmentStatus"].string == "pending_details") {
                    Button(
                        onClick = onClaimNow,
                        colors = ButtonDefaults.buttonColors(containerColor = LwColors.Orange),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Điền địa chỉ nhận quà ngay", fontWeight = FontWeight.Bold)
                    }
                }
                if (remainingSpins > 0) {
                    Button(
                        onClick = onSpinAgain,
                        colors = ButtonDefaults.buttonColors(containerColor = LwColors.ForestSegment),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Quay tiếp (Còn {0} lượt)", remainingSpins), fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Đóng", fontWeight = FontWeight.Bold, color = LwColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun RewardDetailSheet(spin: JSONValue, onDismiss: () -> Unit, onEnterRecipient: () -> Unit) {
    val context = LocalContext.current
    val rewardCode = spin["rewardCode"].string
    var copied by remember { mutableStateOf(false) }
    FutaBottomSheet(visible = true, onDismiss = onDismiss, title = tr("Chi tiết phần thưởng")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CardGiftcard, null, tint = LwColors.BrassTicks, modifier = Modifier.size(48.dp))
                Text(spin["prizeLabel"].string, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LwColors.GreenDark, textAlign = TextAlign.Center)
                FulfillmentBadge(spin["fulfillmentStatus"].string)
            }
            if (rewardCode.isNotEmpty()) {
                LwCard {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RewardQr(rewardCode, 170)
                        Text("Đưa mã này cho nhân viên/admin quét xác nhận", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LwColors.Muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VerbatimText(rewardCode, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LwColors.GreenDark, style = TextStyle(fontFamily = FontFamily.Monospace))
                            IconButton(onClick = {
                                copyToClipboard(context, rewardCode, "Đã chép")
                                copied = true
                            }) {
                                Icon(
                                    if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                    tr("Sao chép"),
                                    tint = if (copied) Color(0xFF16A34A) else LwColors.Orange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (spin["recipientName"].string.isNotEmpty()) {
                LwCard(radius = 14) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Thông tin người nhận quà", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LwColors.GreenDark)
                        AdvisorLabeledRow("Họ tên", spin["recipientName"].string)
                        AdvisorLabeledRow("SĐT", spin["recipientPhone"].string)
                        AdvisorLabeledRow("Địa chỉ", spin["recipientAddress"].string)
                    }
                }
            } else if (spin["fulfillmentType"].string == "physical") {
                Button(
                    onClick = onEnterRecipient,
                    colors = ButtonDefaults.buttonColors(containerColor = LwColors.Orange),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.EditLocationAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cập nhật địa chỉ nhận quà", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** All spins, paged through GET /lucky-wheel/history?page=N (10 per page). */
@Composable
private fun FullHistorySheet(initial: List<JSONValue>, total: Int, onDismiss: () -> Unit, onSelect: (JSONValue) -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(initial) }
    var page by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(total) }
    var loadingMore by remember { mutableStateOf(false) }

    FutaBottomSheet(visible = true, onDismiss = onDismiss, title = tr("Lịch sử vòng quay")) {
        if (items.isEmpty()) {
            Text("Chưa có lượt quay nào.", fontSize = 13.5.sp, color = FutaColors.Slate, modifier = Modifier.padding(vertical = 16.dp))
        }
        items.forEach { spin ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(spin) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CardGiftcard, null, tint = LwColors.BrassTicks, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(spin["prizeLabel"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    if (spin["rewardCode"].string.isNotEmpty()) {
                        VerbatimText(spin["rewardCode"].string, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = LwColors.ForestSegment, maxLines = 1, style = TextStyle(fontFamily = FontFamily.Monospace))
                    }
                    val createdAt = spin["createdAt"].string
                    if (createdAt.isNotEmpty()) VerbatimText(createdAt.take(16).replace("T", " "), fontSize = 11.sp, color = FutaColors.Slate)
                }
                FulfillmentBadge(spin["fulfillmentStatus"].string)
            }
            HorizontalDivider(color = FutaColors.PanelDivider)
        }
        if (items.size < totalCount) {
            Spacer(Modifier.height(10.dp))
            FutaButton(
                text = if (loadingMore) "Đang tải..." else "Xem thêm",
                variant = FutaButtonVariant.OUTLINE,
                enabled = !loadingMore,
                onClick = {
                    scope.launch {
                        loadingMore = true
                        try {
                            val data = APIClient.get().request("/lucky-wheel/history", query = mapOf("page" to "${page + 1}"))["data"]
                            val next = historyItems(data)
                            items = items + next.filter { n -> items.none { it["id"].string == n["id"].string } }
                            totalCount = data["total"].int
                            page += 1
                            if (next.isEmpty()) totalCount = items.size
                        } catch (e: Exception) {
                            ToastCenter.show(e.message ?: tr("Không tải được dữ liệu"), isError = true)
                        } finally {
                            loadingMore = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RecipientSheet(spin: JSONValue, onDismiss: () -> Unit, onSaved: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(spin["recipientName"].string) }
    var phone by remember { mutableStateOf(spin["recipientPhone"].string) }
    var address by remember { mutableStateOf(spin["recipientAddress"].string) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        errorMessage = when {
            name.trim().length < 2 -> "Vui lòng nhập họ tên người nhận"
            phone.trim().length < 8 -> "Vui lòng nhập số điện thoại hợp lệ"
            address.trim().length < 5 -> "Vui lòng nhập địa chỉ nhận hàng chi tiết"
            else -> null
        }
        if (errorMessage != null) return
        scope.launch {
            isSaving = true
            try {
                val body = buildJsonObject {
                    put("recipientName", name.trim())
                    put("recipientPhone", phone.trim())
                    put("recipientAddress", address.trim())
                }.toString()
                APIClient.get().request("/lucky-wheel/spins/${spin.id}/recipient", method = "PUT", bodyJson = body)
                ToastCenter.show(tr("Đã gửi thông tin nhận quà"))
                onSaved()
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isSaving = false
            }
        }
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = { if (!isSaving) onDismiss() },
        title = tr("Địa chỉ nhận quà"),
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Huỷ", variant = FutaButtonVariant.OUTLINE, enabled = !isSaving, onClick = onDismiss, modifier = Modifier.weight(1f))
                FutaButton(text = if (isSaving) "Đang gửi..." else "Gửi thông tin", enabled = !isSaving, onClick = { submit() }, modifier = Modifier.weight(1.4f))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Thông tin nhận quà hiện vật", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
            FutaInput(value = name, onValueChange = { name = it }, placeholder = tr("Họ và tên người nhận (*)"), modifier = Modifier.fillMaxWidth())
            FutaInput(
                value = phone,
                onValueChange = { phone = it },
                placeholder = tr("Số điện thoại liên hệ (*)"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            FutaTextArea(value = address, onValueChange = { address = it }, placeholder = tr("Địa chỉ nhận hàng chi tiết (*)"), minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
            errorMessage?.let { Text(it, fontSize = 13.sp, color = Color(0xFFDC2626)) }
        }
    }
}
