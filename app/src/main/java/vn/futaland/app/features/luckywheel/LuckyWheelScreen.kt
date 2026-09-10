package vn.futaland.app.features.luckywheel

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import vn.futaland.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.designsystem.*
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.cos
import kotlin.math.sin
private object LwColors {
    val Green = Color(0xFF064D3D)
    val ForestSegment = Color(0xFF005442)
    val IvorySegment = Color(0xFFFFF8ED)
    val BrassRim = Color(0xFFB9792D)
    val BrassTicks = Color(0xFFA87130)
    val PageBg = Color(0xFFFFFDF9)
    val Orange = Color(0xFFF36F21)
}

data class WheelPrize(
    val id: String,
    val name: String,
    val value: String
)

@Composable
fun LuckyWheelScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<JSONValue?>(null) }
    var loading by remember { mutableStateOf(true) }
    var remainingSpins by remember { mutableStateOf(3) }
    var isSpinning by remember { mutableStateOf(false) }
    var winningPrize by remember { mutableStateOf<String?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    val rotation = remember { Animatable(0f) }

    val defaultPrizes = remember {
        listOf(
            WheelPrize("1", "Voucher 50Tr", "50M"),
            WheelPrize("2", "IPhone 16", "IP16"),
            WheelPrize("3", "Thêm 1 Lượt", "+1"),
            WheelPrize("4", "Vàng SJC 1 Chỉ", "1 SJC"),
            WheelPrize("5", "Chúc may mắn", "GL"),
            WheelPrize("6", "Voucher 10Tr", "10M"),
            WheelPrize("7", "Thêm 2 Lượt", "+2"),
            WheelPrize("8", "Quà FUTA VIP", "VIP")
        )
    }

    LaunchedEffect(Unit) {
        try {
            val res = APIClient.get().request("/lucky-wheel/state")
            state = res["data"]
            val spins = res["data"]["remainingSpins"].int
            if (spins > 0) remainingSpins = spins
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    fun spinWheel() {
        if (isSpinning || remainingSpins <= 0) return
        scope.launch {
            isSpinning = true
            var prizeIndex = (0 until defaultPrizes.size).random()
            try {
                val spinRes = APIClient.get().request("/lucky-wheel/spin", method = "POST")
                val serverPrizeId = spinRes["data"]["prizeId"].string.ifEmpty { spinRes["prizeId"].string }
                val serverPrizeName = spinRes["data"]["prizeName"].string.ifEmpty { spinRes["prizeName"].string }
                val foundIdx = defaultPrizes.indexOfFirst { it.id == serverPrizeId || it.name == serverPrizeName }
                if (foundIdx >= 0) prizeIndex = foundIdx
                val serverRemaining = spinRes["data"]["remainingSpins"].int
                if (serverRemaining >= 0) remainingSpins = serverRemaining
            } catch (_: Exception) {}

            val sectorAngle = 360f / defaultPrizes.size
            val targetRotation = rotation.value + (360f * 5) + (360f - (prizeIndex * sectorAngle) - sectorAngle / 2)

            rotation.animateTo(
                targetValue = targetRotation,
                animationSpec = tween(
                    durationMillis = 4200,
                    easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
                )
            )

            isSpinning = false
            remainingSpins = (remainingSpins - 1).coerceAtLeast(0)
            winningPrize = defaultPrizes[prizeIndex].name
        }
    }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.sf_chevron_left),
                            contentDescription = "Quay lại",
                            tint = FutaColors.Navy,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "Vòng quay may mắn",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LwColors.PageBg)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Text(
                    text = "FUTA LUCKY WHEEL",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = LwColors.Green
                )
                Text(
                    text = "Quay là trúng - Quà tặng bất động sản hấp dẫn",
                    fontSize = 12.sp,
                    color = FutaColors.Slate
                )
                Spacer(Modifier.height(20.dp))
            }

            // Custom Canvas Wheel
            item {
                Box(
                    modifier = Modifier
                        .size(310.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.minDimension / 2 - 12.dp.toPx()
                        val currentRot = rotation.value

                        // Outer Brass Rim
                        drawCircle(
                            color = LwColors.BrassRim,
                            radius = radius + 10.dp.toPx(),
                            center = center,
                            style = Stroke(width = 8.dp.toPx())
                        )

                        val numSectors = defaultPrizes.size
                        val sweepAngle = 360f / numSectors

                        for (i in 0 until numSectors) {
                            val startAngle = (i * sweepAngle) + currentRot
                            val sectorColor = if (i % 2 == 0) LwColors.ForestSegment else LwColors.IvorySegment
                            drawArc(
                                color = sectorColor,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Fill
                            )

                            // Sector text
                            val midAngleRad = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                            val textDist = radius * 0.65f
                            val textX = center.x + (textDist * cos(midAngleRad)).toFloat()
                            val textY = center.y + (textDist * sin(midAngleRad)).toFloat()

                            val textColor = if (i % 2 == 0) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#064D3D")
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = textColor
                                    textSize = 28f
                                    isFakeBoldText = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawText(defaultPrizes[i].value, textX, textY, paint)
                            }
                        }

                        // Center Hub
                        drawCircle(color = LwColors.BrassRim, radius = 32.dp.toPx(), center = center)
                        drawCircle(color = Color.White, radius = 24.dp.toPx(), center = center)
                    }

                    // Pointer Top Needle with Dynamic Tick Animation
                    val pointerWobble = if (isSpinning) (kotlin.math.sin(rotation.value * 0.15f) * 10f) else 0f
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(28.dp)
                            .graphicsLayer {
                                rotationZ = pointerWobble
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            }
                    ) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2, size.height)
                            lineTo(0f, 0f)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(path, color = LwColors.Orange)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // Spin Action Button
            item {
                FutaButton(
                    text = if (remainingSpins > 0) "QUAY NGAY ($remainingSpins LƯỢT KHẢ DỤNG)" else "HẾT LƯỢT QUAY HÔM NAY",
                    variant = FutaButtonVariant.SECONDARY,
                    enabled = !isSpinning && remainingSpins > 0,
                    height = 48.dp,
                    onClick = { spinWheel() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
            }

            // Quota & Daily Limit Card
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Lượt hôm nay", fontSize = 11.5.sp, color = FutaColors.Slate)
                            Text("$remainingSpins lượt", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hạn mức ngày", fontSize = 11.5.sp, color = FutaColors.Slate)
                            Text("5 lượt", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Missions Section
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("NHIỆM VỤ NHẬN LƯỢT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Spacer(Modifier.height(10.dp))
                        MissionRow("Đăng nhập mỗi ngày", "+1 lượt", true)
                        MissionRow("Xem chi tiết 3 căn hộ BĐS", "+1 lượt", false)
                        MissionRow("Chia sẻ sản phẩm lên mạng xã hội", "+1 lượt", false)
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // Win Dialog with Confetti Celebration Particles (Matching iOS)
    if (winningPrize != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Falling Confetti Particle Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val confettiColors = listOf(Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFFEC4899))
                for (i in 0 until 50) {
                    val x = (i * 37) % size.width
                    val y = ((i * 53) + (rotation.value * 0.5f)) % size.height
                    val color = confettiColors[i % confettiColors.size]
                    drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(x, y))
                }
            }

            FutaDialog(
                visible = true,
                onDismiss = { winningPrize = null },
                title = "Chúc mừng bạn trúng thưởng!",
                confirmText = "Nhận quà ngay",
                confirmVariant = FutaButtonVariant.PRIMARY,
                onConfirm = { winningPrize = null },
                cancelText = null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.sf_quick_wheel),
                                contentDescription = null,
                                tint = FutaColors.BrandGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = winningPrize ?: "",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = FutaColors.Navy,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Phần quà đã được lưu vào mục 'Gói tin & Voucher' trong tài khoản FUTA Land của bạn.",
                        fontSize = 12.5.sp,
                        color = FutaColors.Slate,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionRow(title: String, reward: String, isDone: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 13.sp, color = FutaColors.Navy)
            Text(reward, fontSize = 11.sp, color = FutaColors.BrandOrange, fontWeight = FontWeight.SemiBold)
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isDone) FutaColors.MintBg else FutaColors.PageBg,
            border = BorderStroke(1.dp, if (isDone) FutaColors.BrandGreen else FutaColors.LightBlueBorder)
        ) {
            Text(
                text = if (isDone) "Đã nhận" else "Làm ngay",
                color = if (isDone) FutaColors.BrandGreen else FutaColors.Slate,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private fun generateQrBitmap(content: String): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300)
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.RGB_565)
        for (x in 0 until 300) {
            for (y in 0 until 300) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}
