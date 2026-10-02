package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun viewingTime(value: String): String = try {
    Instant.parse(value).atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
} catch (_: Exception) {
    tr("Chưa chốt giờ")
}

private fun statusStyle(status: String): Pair<String, Color> = when (status) {
    "confirmed" -> "Đã xác nhận" to FutaColors.BrandGreen
    "completed" -> "Đã hoàn thành" to Color(0xFF2563EB)
    "cancelled" -> "Đã hủy" to FutaColors.Slate
    else -> "Chờ xác nhận" to FutaColors.BrandOrange
}

/** Customer "Lịch hẹn xem nhà" (iOS CustomerViewingAppointmentsView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewingAppointmentsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val user by AppSession.shared.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val canView = user != null && AppSession.shared.isAuthenticated && AppSession.shared.role != "guest"
    var appointments by remember(user?.id) { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember(user?.id) { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember(user?.id) { mutableStateOf<String?>(null) }
    var page by remember(user?.id) { mutableIntStateOf(1) }
    var hasMore by remember(user?.id) { mutableStateOf(false) }
    var cancelTarget by remember { mutableStateOf<JSONValue?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }

    fun load(nextPage: Boolean = false) {
        if (!canView || loading) return
        scope.launch {
            loading = true
            val targetPage = if (nextPage) page + 1 else 1
            try {
                val response = APIClient.get().request(
                    "/viewing-appointments",
                    query = mapOf("page" to targetPage.toString(), "limit" to "20", "sort" to "scheduled_asc")
                )
                val records = response["data"].array.ifEmpty { response["data"]["data"].array }
                appointments = if (nextPage) appointments + records else records
                page = targetPage
                val totalPages = response["pagination"]["totalPages"].int
                hasMore = if (totalPages > 0) targetPage < totalPages else records.size == 20
                error = null
            } catch (e: Exception) {
                error = e.message ?: tr("Không thể tải lịch hẹn")
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun cancel(item: JSONValue) {
        scope.launch {
            cancellingId = item.id
            try {
                val body = buildJsonObject { put("status", "cancelled") }.toString()
                APIClient.get().request("/viewing-appointments/${item.id}", method = "PATCH", bodyJson = body)
                ToastCenter.show(tr("Đã hủy lịch hẹn"))
                load()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể hủy lịch"), isError = true)
            } finally {
                cancellingId = null
            }
        }
    }

    LaunchedEffect(user?.id) { load() }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), tint = FutaColors.Navy)
                    }
                    Text("Lịch hẹn xem nhà", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        if (!canView) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                FutaEmptyState(
                    title = "Đăng nhập để xem lịch hẹn của bạn",
                    message = "Lịch xem nhà của bạn sẽ hiển thị tại đây.",
                    icon = Icons.Default.CalendarMonth,
                    actionButton = { FutaButton(text = "Đăng nhập", onClick = { onNavigate(FutaDestinations.AUTH) }) }
                )
            }
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; load() },
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    loading && appointments.isEmpty() -> items(3) { FutaSkeletonBlock(height = 130.dp, radius = 16.dp) }
                    error != null && appointments.isEmpty() -> item {
                        FutaEmptyState(
                            title = "Không thể tải lịch hẹn",
                            message = error.orEmpty(),
                            icon = Icons.Default.ErrorOutline,
                            actionButton = { FutaButton(text = "Thử lại", onClick = { load() }, variant = FutaButtonVariant.OUTLINE) }
                        )
                    }
                    appointments.isEmpty() -> item {
                        FutaEmptyState(
                            title = "Chưa có lịch hẹn",
                            message = "Lịch xem nhà của bạn sẽ hiển thị tại đây.",
                            icon = Icons.Default.CalendarMonth
                        )
                    }
                    else -> {
                        items(appointments, key = { it.id }) { item ->
                            AppointmentCard(
                                item = item,
                                cancelling = cancellingId == item.id,
                                onOpenProperty = {
                                    val pid = item["property"].id.ifEmpty { item["propertyId"].string }
                                    if (pid.isNotEmpty()) onNavigate(FutaDestinations.propertyDetail(pid))
                                },
                                onCancel = { cancelTarget = item }
                            )
                        }
                        if (hasMore) {
                            item {
                                FutaButton(
                                    text = if (loading) "Đang tải…" else "Xem thêm",
                                    variant = FutaButtonVariant.OUTLINE,
                                    enabled = !loading,
                                    onClick = { load(nextPage = true) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    FutaDialog(
        visible = cancelTarget != null,
        onDismiss = { cancelTarget = null },
        title = "Hủy lịch hẹn?",
        confirmText = "Hủy lịch hẹn",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Giữ lịch",
        onConfirm = {
            cancelTarget?.let { cancel(it) }
            cancelTarget = null
        },
        onCancel = { cancelTarget = null }
    ) {
        Text(
            "Tư vấn viên sẽ được thông báo về việc hủy lịch xem nhà này.",
            fontSize = 14.sp,
            color = FutaColors.Slate,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun AppointmentCard(item: JSONValue, cancelling: Boolean, onOpenProperty: () -> Unit, onCancel: () -> Unit) {
    val status = item["status"].string
    val (statusLabel, statusColor) = statusStyle(status)
    val title = item["property"]["title"].string.takeIf { it.isNotBlank() }?.translated("property")
        ?: item["property"]["propertyCode"].string.ifBlank { tr("Lịch xem nhà") }

    FutaCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenProperty) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                VerbatimText(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    statusLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.background(statusColor.copy(alpha = 0.12f), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            InfoLine(Icons.Default.Schedule, tr("Thời gian: {0}", viewingTime(item["scheduledAt"].string)))
            val advisorName = item["advisor"]["name"].string
            if (advisorName.isNotBlank()) InfoLine(Icons.Default.Person, tr("Tư vấn viên: {0}", advisorName))
            if (item["note"].string.isNotBlank()) {
                VerbatimText(tr("Ghi chú: {0}", item["note"].string), fontSize = 12.5.sp, color = FutaColors.Slate)
            }
            if (status == "pending" || status == "confirmed") {
                HorizontalDivider(color = Color(0xFFF1F5F9))
                FutaButton(
                    text = if (cancelling) "Đang hủy…" else "Hủy lịch hẹn",
                    variant = FutaButtonVariant.OUTLINE,
                    enabled = !cancelling,
                    height = 38.dp,
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        VerbatimText(text, fontSize = 13.sp, color = FutaColors.Body)
    }
}
