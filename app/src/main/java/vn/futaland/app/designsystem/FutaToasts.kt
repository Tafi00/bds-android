package vn.futaland.app.designsystem

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false
)

object ToastCenter {
    private val _currentToast = MutableStateFlow<ToastMessage?>(null)
    val currentToast = _currentToast.asStateFlow()

    private var dismissJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun show(message: String, isError: Boolean = false) {
        dismissJob?.cancel()
        _currentToast.value = ToastMessage(text = message, isError = isError)
        dismissJob = scope.launch {
            delay(3500)
            _currentToast.value = null
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        _currentToast.value = null
    }
}

@Composable
fun FutaToastOverlay(
    modifier: Modifier = Modifier
) {
    val toast by ToastCenter.currentToast.collectAsState()

    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        toast?.let { msg ->
            val iconTint = if (msg.isError) Color(0xFFDC2626) else FutaColors.BrandGreen
            val bgTint = if (msg.isError) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
            val borderTint = if (msg.isError) Color(0xFFFECACA) else Color(0xFFBBF7D0)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bgTint,
                border = BorderStroke(1.dp, borderTint),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (msg.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = msg.text,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )
                    IconButton(
                        onClick = { ToastCenter.dismiss() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = FutaColors.Slate,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
