package vn.futaland.app.core.update

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vn.futaland.app.R
import vn.futaland.app.designsystem.FutaButton
import vn.futaland.app.designsystem.FutaColors

/**
 * Plain, quiet update prompt: app icon, title, one line of context, a single
 * filled call to action and a text "later" button. Centered [Dialog] — not a
 * bottom sheet or the system AlertDialog — so the layout stays stable.
 *
 * Back means "Để sau" (snooze); like a system alert, the dimmed area does not dismiss.
 */
@Composable
fun AppUpdateDialog(
    visible: Boolean,
    installedVersionName: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val cardScale by animateFloatAsState(
            targetValue = if (appeared) 1f else 0.94f,
            animationSpec = tween(durationMillis = 220),
            label = "updateCardScale"
        )
        val cardAlpha by animateFloatAsState(
            targetValue = if (appeared) 1f else 0f,
            animationSpec = tween(durationMillis = 220),
            label = "updateCardAlpha"
        )

        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    },
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Đã có bản cập nhật mới",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        textAlign = TextAlign.Center
                    )
                    if (installedVersionName.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("Đang dùng phiên bản {0}", installedVersionName),
                            fontSize = 14.sp,
                            color = FutaColors.Slate,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Vui lòng cập nhật FutaLand lên bản mới nhất để dùng các tính năng mới và sửa lỗi.",
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = FutaColors.SubLabel,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    FutaButton(
                        text = "Cập nhật ngay",
                        onClick = onUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        height = 50.dp,
                        shape = RoundedCornerShape(25.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onLater) {
                        Text(
                            text = "Để sau",
                            fontSize = 16.sp,
                            color = FutaColors.BrandGreen
                        )
                    }
                }
            }
        }
    }
}
