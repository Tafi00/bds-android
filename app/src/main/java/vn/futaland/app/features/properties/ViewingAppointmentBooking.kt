package vn.futaland.app.features.properties

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ViewingAppointmentBooking(
    propertyId: String?,
    projectName: String,
    advisorId: String?,
    advisorName: String,
    conversationId: String?,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by AppSession.shared.currentUser.collectAsState()
    var name by remember(user?.id) { mutableStateOf(user?.get("name")?.string.orEmpty()) }
    var phone by remember(user?.id) { mutableStateOf(user?.get("phone")?.string.orEmpty()) }
    var note by remember { mutableStateOf("") }
    var selectedAt by remember { mutableStateOf<Calendar?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Đặt hẹn xem nhà") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Họ và tên") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Số điện thoại") }, singleLine = true)
                if (projectName.isNotBlank()) Text("Dự án: $projectName")
                if (advisorName.isNotBlank()) Text("Tư vấn viên: $advisorName")
                OutlinedButton(onClick = {
                    val initial = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                    DatePickerDialog(context, { _, year, month, day ->
                        val chosen = Calendar.getInstance().apply { set(year, month, day, 9, 0, 0); set(Calendar.MILLISECOND, 0) }
                        TimePickerDialog(context, { _, hour, minute ->
                            chosen.set(Calendar.HOUR_OF_DAY, hour)
                            chosen.set(Calendar.MINUTE, minute)
                            selectedAt = chosen
                        }, 9, 0, true).show()
                    }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.minDate = System.currentTimeMillis()
                    }.show()
                }) {
                    Text(selectedAt?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(it.time) } ?: "Chọn ngày và giờ xem")
                }
                OutlinedTextField(note, { note = it }, label = { Text("Ghi chú") }, minLines = 2)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!AppSession.shared.isAuthenticated || AppSession.shared.role == "guest") {
                    error = "Vui lòng đăng nhập trước khi đặt hẹn"
                } else if (name.isBlank() || phone.trim().length < 6 || selectedAt == null) {
                    error = "Vui lòng nhập họ tên, số điện thoại và thời gian hẹn"
                } else if (selectedAt!!.timeInMillis <= System.currentTimeMillis()) {
                    error = "Vui lòng chọn thời gian trong tương lai"
                } else {
                    busy = true
                    error = ""
                    scope.launch {
                        try {
                            val payload = JSONObject().apply {
                                put("customerName", name.trim())
                                put("customerPhone", phone.trim())
                                put("scheduledAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(selectedAt!!.time))
                                put("note", note.trim())
                                if (!propertyId.isNullOrBlank()) put("propertyId", propertyId)
                                if (!advisorId.isNullOrBlank()) put("advisorId", advisorId)
                                if (!conversationId.isNullOrBlank() && conversationId != "ai_agent") put("conversationId", conversationId)
                            }
                            APIClient.get().request("/viewing-appointments", method = "POST", bodyJson = payload.toString())
                            onCreated()
                        } catch (e: Exception) {
                            error = e.message ?: "Không thể gửi lịch hẹn. Vui lòng thử lại."
                        } finally {
                            busy = false
                        }
                    }
                }
            }) { Text(if (busy) "Đang gửi..." else "Gửi yêu cầu") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Đóng") } }
    )
}
