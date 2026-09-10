package vn.futaland.app.designsystem

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

/**
 * Digital Signature Pad component for Jetpack Compose matching iOS DigitalSignaturePadModal.
 * Captures touch gesture strokes and converts them into a base64 PNG data URL (<= 250KB).
 */
@Composable
fun FutaSignaturePad(
    modifier: Modifier = Modifier,
    title: String = "Ký xác nhận điện tử",
    subtitle: String = "Vui lòng ký tên vào khung bên dưới bằng ngón tay hoặc bút cảm ứng",
    onSave: (dataUrl: String) -> Unit,
    onCancel: () -> Unit
) {
    var lines by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasWidth by remember { mutableIntStateOf(600) }
    var canvasHeight by remember { mutableIntStateOf(300) }

    fun exportSignature(): String {
        val w = canvasWidth.coerceAtLeast(300)
        val h = canvasHeight.coerceAtLeast(150)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint().apply {
            color = android.graphics.Color.rgb(13, 38, 89)
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val allLines = if (currentLine.isNotEmpty()) lines + listOf(currentLine) else lines
        for (line in allLines) {
            if (line.isNotEmpty()) {
                val aPath = AndroidPath()
                aPath.moveTo(line.first().x, line.first().y)
                for (pt in line.drop(1)) {
                    aPath.lineTo(pt.x, pt.y)
                }
                canvas.drawPath(aPath, paint)
            }
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val byteArray = stream.toByteArray()
        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = FutaColors.Navy
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = FutaColors.Slate,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Signature Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFAFAFA))
                .border(BorderStroke(1.5.dp, Color(0xFFE2E8F0)), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentLine = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentLine = currentLine + listOf(change.position)
                        },
                        onDragEnd = {
                            if (currentLine.isNotEmpty()) {
                                lines = lines + listOf(currentLine)
                                currentLine = emptyList()
                            }
                        },
                        onDragCancel = {
                            currentLine = emptyList()
                        }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                canvasWidth = size.width.toInt()
                canvasHeight = size.height.toInt()

                // Baseline dashed indicator
                val baselineY = size.height * 0.78f
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(20f, baselineY),
                    end = Offset(size.width - 20f, baselineY),
                    strokeWidth = 1.5f
                )

                val all = if (currentLine.isNotEmpty()) lines + listOf(currentLine) else lines
                for (line in all) {
                    if (line.size > 1) {
                        val path = Path()
                        path.moveTo(line.first().x, line.first().y)
                        for (i in 1 until line.size) {
                            path.lineTo(line[i].x, line[i].y)
                        }
                        drawPath(
                            path = path,
                            color = FutaColors.Navy,
                            style = Stroke(
                                width = 3.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            if (lines.isEmpty() && currentLine.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ký tên tại đây",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    lines = emptyList()
                    currentLine = emptyList()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(42.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
                Icon(Icons.Default.Clear, null, tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ký lại", fontSize = 13.sp, color = FutaColors.Slate)
            }

            FutaButton(
                text = "Xác nhận ký",
                variant = FutaButtonVariant.PRIMARY,
                enabled = lines.isNotEmpty() || currentLine.isNotEmpty(),
                onClick = {
                    val dataUrl = exportSignature()
                    onSave(dataUrl)
                },
                modifier = Modifier.weight(1.5f).height(42.dp)
            )
        }
    }
}
