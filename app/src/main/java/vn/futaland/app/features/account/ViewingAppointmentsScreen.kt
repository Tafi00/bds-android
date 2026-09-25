package vn.futaland.app.features.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.navigation.FutaDestinations
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun viewingTime(value: String): String = try {
    Instant.parse(value).atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
} catch (_: Exception) {
    "Chưa chốt giờ"
}

@Composable
fun ViewingAppointmentsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val user by AppSession.shared.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    var appointments by remember(user?.id) { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember(user?.id) { mutableStateOf(true) }
    var error by remember(user?.id) { mutableStateOf("") }
    var page by remember(user?.id) { mutableIntStateOf(1) }
    var hasMore by remember(user?.id) { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(user?.id, page, revision) {
        if (!AppSession.shared.isAuthenticated || AppSession.shared.role == "guest") {
            loading = false
            appointments = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val response = APIClient.get().request("/viewing-appointments", query = mapOf("page" to page.toString(), "limit" to "20", "sort" to "scheduled_asc"))
            val data = response["data"]["data"].array.ifEmpty { response["data"].array }
            appointments = if (page == 1) data else appointments + data
            hasMore = data.size == 20
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "Không thể tải lịch hẹn"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Quay lại") }
            TextButton(onClick = { page = 1; revision++ }) { Text("Làm mới") }
        }
        Text("Lịch hẹn xem nhà", style = MaterialTheme.typography.headlineSmall)
        if (!AppSession.shared.isAuthenticated || AppSession.shared.role == "guest") {
            Text("Đăng nhập để xem lịch hẹn của bạn")
            Button(onClick = { onNavigate(FutaDestinations.AUTH) }) { Text("Đăng nhập") }
        } else {
            if (loading && appointments.isEmpty()) CircularProgressIndicator()
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (!loading && error.isBlank() && appointments.isEmpty()) Text("Bạn chưa có lịch hẹn xem nhà")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(appointments, key = { it.id }) { item ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(item["property"]["title"].string.ifBlank { item["property"]["propertyCode"].string.ifBlank { "Lịch xem nhà" } }, style = MaterialTheme.typography.titleMedium)
                            Text("Thời gian: ${viewingTime(item["scheduledAt"].string)}")
                            Text("Trạng thái: ${when (item["status"].string) { "confirmed" -> "Đã xác nhận"; "completed" -> "Đã hoàn thành"; "cancelled" -> "Đã hủy"; else -> "Chờ xác nhận" }}")
                            val advisorName = item["advisor"]["name"].string
                            if (advisorName.isNotBlank()) Text("Tư vấn viên: $advisorName")
                            if (item["note"].string.isNotBlank()) Text("Ghi chú: ${item["note"].string}")
                            if (item["status"].string == "pending" || item["status"].string == "confirmed") {
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            APIClient.get().request("/viewing-appointments/${item.id}", method = "PATCH", bodyJson = "{\"status\":\"cancelled\"}")
                                            page = 1
                                            revision++
                                        } catch (e: Exception) { error = e.message ?: "Không thể hủy lịch" }
                                    }
                                }) { Text("Hủy lịch hẹn") }
                            }
                        }
                    }
                }
                if (hasMore) item { TextButton(onClick = { page++ }) { Text("Xem thêm") } }
            }
        }
    }
}
