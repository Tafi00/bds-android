package vn.futaland.app.core.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vn.futaland.app.designsystem.FutaButton
import vn.futaland.app.designsystem.FutaColors

/**
 * Branded update card matching the app's card language: white surface, cool
 * border, emerald primary CTA. Centered [Dialog] — not bottom sheet, not
 * system AlertDialog — so the version line and CTA stay visually stable.
 */
@Composable
fun AppUpdateDialog(
    visible: Boolean,
    installedVersionName: String,
    availableVersionCode: Int,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, FutaColors.LightBlueBorder),
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(FutaColors.MintBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = FutaColors.BrandGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Đã có bản cập nhật mới",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy
                            )
                            val versionLine = if (installedVersionName.isNotBlank()) {
                                "Đang dùng $installedVersionName • Bản mới #$availableVersionCode"
                            } else {
                                "Bản mới #$availableVersionCode trên Google Play"
                            }
                            Text(
                                text = versionLine,
                                fontSize = 13.sp,
                                color = FutaColors.Slate
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = FutaColors.PanelDivider)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Vui lòng cập nhật FutaLand lên bản mới nhất để dùng các tính năng mới và sửa lỗi.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = FutaColors.Body
                    )
                    Spacer(Modifier.height(16.dp))
                    FutaButton(
                        text = "Cập nhật ngay",
                        onClick = onUpdate,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.TextButton(onClick = onLater) {
                            Text("Để sau", fontSize = 15.sp, color = FutaColors.Slate)
                        }
                    }
                    Text(
                        text = "Bạn sẽ được chuyển sang Google Play để hoàn tất cập nhật.",
                        fontSize = 12.sp,
                        color = Color(0xFF8A94A6),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
