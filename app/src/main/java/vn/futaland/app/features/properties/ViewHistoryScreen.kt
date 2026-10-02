package vn.futaland.app.features.properties

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.tr
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewHistoryScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    // GET /apartments/user/view-history returns the viewed property DTOs, newest first.
    fun loadHistory() {
        scope.launch {
            loading = true
            error = null
            try {
                val res = APIClient.get().request("/apartments/user/view-history")
                items = res["data"].array
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun clearHistory() {
        scope.launch {
            clearing = true
            try {
                APIClient.get().request("/apartments/user/view-history", method = "DELETE")
                items = emptyList()
                ToastCenter.show(tr("Đã xoá lịch sử xem"))
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể xoá lịch sử xem"), isError = true)
            } finally {
                clearing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHistory()
    }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Lịch sử đã xem",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }, enabled = !clearing) {
                            Text("Xoá lịch sử", fontSize = 12.5.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (loading && items.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) { FutaSkeletonBlock(height = 240.dp, radius = 18.dp) }
            }
        } else if (error != null && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                FutaEmptyState(
                    title = "Không thể tải dữ liệu",
                    message = error ?: "",
                    actionButton = { FutaButton(text = "Thử lại", onClick = { loadHistory() }, variant = FutaButtonVariant.OUTLINE) }
                )
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                FutaEmptyState(
                    title = "Chưa có lịch sử xem",
                    message = "Các bất động sản bạn từng xem sẽ xuất hiện tại đây."
                )
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; loadHistory() },
                modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding)
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(items, key = { idx, property -> property.id.ifEmpty { "hist-$idx" } }) { _, property ->
                    FutaPropertyCard(
                        property = property,
                        onCallClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0903715757"))
                            context.startActivity(intent)
                        },
                        onChatClick = { onNavigate(FutaDestinations.INBOX) },
                        onClick = { onNavigate(FutaDestinations.propertyDetail(property.id)) }
                    )
                }
            }
            }
        }
    }

    FutaDialog(
        visible = showClearConfirm,
        onDismiss = { showClearConfirm = false },
        title = "Xoá lịch sử xem?",
        confirmText = "Xoá lịch sử",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Hủy",
        onConfirm = {
            showClearConfirm = false
            clearHistory()
        },
        onCancel = { showClearConfirm = false }
    ) {
        Text(
            "Toàn bộ bất động sản bạn đã xem sẽ bị xoá khỏi lịch sử.",
            fontSize = 14.sp,
            color = FutaColors.Slate,
            lineHeight = 20.sp
        )
    }
}
