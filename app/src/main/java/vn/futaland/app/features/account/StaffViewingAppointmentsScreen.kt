package vn.futaland.app.features.account

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.core.sales.ProductContext
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

/** Roles the backend lets manage viewing appointments (admin sees all, TVV only their own). */
val STAFF_APPOINTMENT_ROLES = setOf("admin", "sale", "telesale", "advisor_trainee")

/** Bumped after any change in the detail screen so the list reloads on return. */
object ViewingAppointmentSync {
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    fun bump() {
        _revision.value++
    }
}

private val VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val PageBackground = Color(0xFFF6F8FB)
private val SoftBorder = Color(0xFFE2E8F0)
private val DangerRed = Color(0xFFDC2626)

private enum class ApptStatus(val wire: String, val label: String, val fg: Color, val bg: Color, val icon: ImageVector) {
    PENDING("pending", "Chờ xác nhận", Color(0xFFD97706), Color(0xFFFEF3C7), Icons.Default.HourglassTop),
    CONFIRMED("confirmed", "Đã xác nhận", Color(0xFF2563EB), Color(0xFFEFF6FF), Icons.Default.EventAvailable),
    COMPLETED("completed", "Đã xem nhà", Color(0xFF207446), Color(0xFFECFDF5), Icons.Default.TaskAlt),
    CANCELLED("cancelled", "Đã hủy", Color(0xFF64748B), Color(0xFFF1F5F9), Icons.Default.EventBusy);

    val isOpen: Boolean get() = this == PENDING || this == CONFIRMED

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire } ?: PENDING
    }
}

private enum class ApptSort(val wire: String, val label: String) {
    SCHEDULED_ASC("scheduled_asc", "Giờ hẹn sớm nhất"),
    SCHEDULED_DESC("scheduled_desc", "Giờ hẹn muộn nhất"),
    NEWEST("newest", "Mới đặt gần đây")
}

// region Time helpers

private fun parseTime(raw: String): ZonedDateTime? =
    if (raw.isBlank()) null else runCatching { Instant.parse(raw).atZone(VN_ZONE) }.getOrNull()

private fun weekdayName(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "Thứ Hai"
    DayOfWeek.TUESDAY -> "Thứ Ba"
    DayOfWeek.WEDNESDAY -> "Thứ Tư"
    DayOfWeek.THURSDAY -> "Thứ Năm"
    DayOfWeek.FRIDAY -> "Thứ Sáu"
    DayOfWeek.SATURDAY -> "Thứ Bảy"
    DayOfWeek.SUNDAY -> "Chủ Nhật"
}

private fun weekdayShort(day: DayOfWeek) = if (day == DayOfWeek.SUNDAY) "CN" else "T${day.value + 1}"

private fun formatPattern(time: ZonedDateTime, pattern: String): String = time.format(DateTimeFormatter.ofPattern(pattern))

private fun dayHeader(date: LocalDate, today: LocalDate): String {
    val base = date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "dd/MM" else "dd/MM/yyyy"))
    return when (date) {
        today -> "Hôm nay · $base"
        today.plusDays(1) -> "Ngày mai · $base"
        today.minusDays(1) -> "Hôm qua · $base"
        else -> "${weekdayName(date.dayOfWeek)} · $base"
    }
}

private fun fullDate(time: ZonedDateTime) = "${weekdayName(time.dayOfWeek)}, ${formatPattern(time, "dd/MM/yyyy")}"

private fun shortDateTime(raw: String): String =
    parseTime(raw)?.let { formatPattern(it, "HH:mm dd/MM/yyyy") } ?: "—"

private fun relativeLabel(time: ZonedDateTime, now: ZonedDateTime): String {
    val minutes = Duration.between(now, time).toMinutes()
    val span = abs(minutes)
    val text = when {
        span < 60 -> "${max(span, 1)} phút"
        span < 60 * 24 -> "${span / 60} giờ"
        else -> "${span / (60 * 24)} ngày"
    }
    return if (minutes >= 0) "Còn $text" else "Đã qua $text"
}

private fun isOverdue(item: JSONValue, now: ZonedDateTime): Boolean {
    val status = ApptStatus.of(item["status"].string)
    val time = parseTime(item["scheduledAt"].string) ?: return false
    return status.isOpen && time.isBefore(now)
}

private fun pickDateTime(context: Context, initial: ZonedDateTime?, onPicked: (ZonedDateTime) -> Unit) {
    val base = initial ?: ZonedDateTime.now(VN_ZONE).plusDays(1).withHour(9).withMinute(0)
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            onPicked(ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, VN_ZONE))
        }, base.hour, base.minute, true).show()
    }, base.year, base.monthValue - 1, base.dayOfMonth).show()
}

// endregion

private suspend fun patchAppointment(id: String, body: JSONObject): JSONValue =
    APIClient.get().request("/viewing-appointments/$id", method = "PATCH", bodyJson = body.toString())["data"]

private fun dial(context: Context, phone: String) {
    if (phone.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
}

private fun sms(context: Context, phone: String) {
    if (phone.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))) }
}

private fun statusToast(status: ApptStatus, name: String) = when (status) {
    ApptStatus.CONFIRMED -> "Đã xác nhận lịch hẹn với $name"
    ApptStatus.COMPLETED -> "Đã ghi nhận $name đã xem nhà"
    ApptStatus.CANCELLED -> "Đã hủy lịch hẹn"
    ApptStatus.PENDING -> "Đã mở lại lịch hẹn"
}

private sealed interface ApptRow {
    val key: String

    data class Header(val title: String, val count: Int, val isToday: Boolean) : ApptRow {
        override val key get() = "h_$title"
    }

    data class Item(val value: JSONValue) : ApptRow {
        override val key get() = value.id
    }
}

// region List screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffViewingAppointmentsScreen(onBack: () -> Unit, onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by AppSession.shared.currentUser.collectAsState()
    val role = user?.get("role")?.string.orEmpty()
    val isAdmin = role == "admin"
    val allowed = AppSession.shared.isAuthenticated && role in STAFF_APPOINTMENT_ROLES
    val revision by ViewingAppointmentSync.revision.collectAsState()

    var status by rememberSaveable { mutableStateOf("all") }
    var sortChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val sort = sortChoice?.let { ApptSort.valueOf(it) }
        ?: if (status == "pending" || status == "confirmed") ApptSort.SCHEDULED_ASC else ApptSort.SCHEDULED_DESC
    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf(query.trim()) }
    LaunchedEffect(query) {
        delay(350)
        debouncedQuery = query.trim()
    }

    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var updatingId by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var lastSignature by remember { mutableStateOf("") }

    suspend fun fetch(target: Int) {
        val params = mutableMapOf("page" to "$target", "limit" to "20", "sort" to sort.wire, "status" to status)
        if (debouncedQuery.isNotEmpty()) params["q"] = debouncedQuery
        val response = APIClient.get().request("/viewing-appointments", query = params)
        val data = response["data"].array
        items = if (target == 1) data else (items + data).distinctBy { it.id }
        page = target
        totalPages = max(1, response["pagination"]["totalPages"].int)
        counts = ApptStatus.entries.associate { it.wire to response["statusCounts"][it.wire].int }
    }

    LaunchedEffect(status, sort, debouncedQuery, revision, reloadKey, allowed) {
        if (!allowed) {
            loading = false
            return@LaunchedEffect
        }
        val signature = "$status|${sort.wire}|$debouncedQuery"
        if (signature != lastSignature) {
            items = emptyList()
            lastSignature = signature
        }
        loading = true
        try {
            fetch(1)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Không thể tải lịch hẹn"
        } finally {
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        if (loadingMore || loading || page >= totalPages) return
        loadingMore = true
        scope.launch {
            try {
                fetch(page + 1)
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: "Không thể tải thêm lịch hẹn", isError = true)
            } finally {
                loadingMore = false
            }
        }
    }

    fun updateStatus(item: JSONValue, next: ApptStatus, scheduledAt: ZonedDateTime? = null) {
        val previous = item["status"].string
        updatingId = item.id
        scope.launch {
            try {
                val body = JSONObject().put("status", next.wire)
                scheduledAt?.let { body.put("scheduledAt", it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) }
                val updated = patchAppointment(item.id, body)
                counts = counts.toMutableMap().apply {
                    this[previous] = max(0, (this[previous] ?: 1) - 1)
                    this[next.wire] = (this[next.wire] ?: 0) + 1
                }
                items = if (status == "all") {
                    items.map { if (it.id == item.id && !updated.isNull) updated else it }
                } else {
                    items.filterNot { it.id == item.id }
                }
                ToastCenter.show(statusToast(next, item["customerName"].string))
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: "Không thể cập nhật lịch hẹn", isError = true)
            } finally {
                updatingId = null
            }
        }
    }

    val now = remember(items) { ZonedDateTime.now(VN_ZONE) }
    val rows: List<ApptRow> = remember(items, sort) {
        if (sort == ApptSort.NEWEST) {
            items.map { ApptRow.Item(it) }
        } else {
            val today = now.toLocalDate()
            val grouped = items.groupBy { parseTime(it["scheduledAt"].string)?.toLocalDate() }
            buildList<ApptRow> {
                grouped.forEach { (date, group) ->
                    add(
                        ApptRow.Header(
                            title = date?.let { dayHeader(it, today) } ?: "Chưa chốt giờ hẹn",
                            count = group.size,
                            isToday = date == today
                        )
                    )
                    group.forEach { add(ApptRow.Item(it)) }
                }
            }
        }
    }
    val totalForTab = if (status == "all") counts.values.sum() else counts[status] ?: 0

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, items.size) {
        if (nearEnd) loadMore()
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            AppointmentTopBar(
                title = "Lịch hẹn xem nhà",
                subtitle = if (isAdmin) "Toàn bộ lịch hẹn trên hệ thống" else "Lịch hẹn khách đặt với bạn",
                onBack = onBack,
                trailing = if (allowed) {
                    { IconButton(onClick = { reloadKey++ }) { Icon(Icons.Default.Refresh, "Làm mới", tint = FutaColors.Navy) } }
                } else null
            )
        }
    ) { padding ->
        if (!allowed) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                FutaEmptyState(
                    title = "Không có quyền truy cập",
                    message = "Chỉ tư vấn viên và quản trị viên mới quản lý được lịch hẹn xem nhà.",
                    icon = Icons.Default.Lock
                )
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 10.dp, bottom = 4.dp)
            ) {
                FutaInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Tìm tên, SĐT khách hoặc mã căn",
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Xóa tìm kiếm",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp).clickable { query = "" }
                            )
                        }
                    } else null,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusFilterChip("Tất cả", counts.values.sum(), status == "all", FutaColors.Navy) { status = "all" }
                    ApptStatus.entries.forEach { entry ->
                        StatusFilterChip(entry.label, counts[entry.wire] ?: 0, status == entry.wire, entry.fg) { status = entry.wire }
                    }
                }
            }
            HorizontalDivider(color = SoftBorder)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (loading && items.isEmpty()) "Đang tải…" else "$totalForTab lịch hẹn",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FutaColors.Slate,
                    modifier = Modifier.weight(1f)
                )
                SortMenu(sort) { sortChoice = it.name }
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (loading && items.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxSize(), color = FutaColors.BrandGreen, trackColor = Color.Transparent)
                }
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    reloadKey++
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when {
                        loading && items.isEmpty() -> items(4) { FutaSkeletonBlock(height = 148.dp, radius = 16.dp) }
                        error != null && items.isEmpty() -> item {
                            FutaEmptyState(
                                title = "Không tải được lịch hẹn",
                                message = error.orEmpty(),
                                icon = Icons.Default.CloudOff,
                                actionButton = { FutaButton("Thử lại", onClick = { reloadKey++ }, icon = Icons.Default.Refresh) }
                            )
                        }
                        items.isEmpty() -> item {
                            val (title, message) = emptyCopy(status, debouncedQuery.isNotEmpty(), isAdmin)
                            FutaEmptyState(title = title, message = message, icon = Icons.AutoMirrored.Filled.EventNote)
                        }
                        else -> {
                            items(rows, key = { it.key }) { row ->
                                when (row) {
                                    is ApptRow.Header -> DayHeader(row)
                                    is ApptRow.Item -> {
                                        val item = row.value
                                        AppointmentCard(
                                            item = item,
                                            showAdvisor = isAdmin,
                                            now = now,
                                            updating = updatingId == item.id,
                                            onClick = { onOpenDetail(item.id) },
                                            onCall = { dial(context, item["customerPhone"].string) },
                                            onAdvance = { next ->
                                                if (next == ApptStatus.CONFIRMED && parseTime(item["scheduledAt"].string) == null) {
                                                    ToastCenter.show("Chọn giờ hẹn đã chốt với khách")
                                                    pickDateTime(context, null) { updateStatus(item, next, it) }
                                                } else {
                                                    updateStatus(item, next)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            item(key = "footer") {
                                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    when {
                                        loadingMore -> CircularProgressIndicator(Modifier.size(22.dp), color = FutaColors.BrandGreen, strokeWidth = 2.dp)
                                        page >= totalPages && items.size > 4 -> Text(
                                            "Đã hiển thị tất cả ${items.size} lịch hẹn",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun emptyCopy(status: String, searching: Boolean, isAdmin: Boolean): Pair<String, String> = when {
    searching -> "Không tìm thấy lịch hẹn" to "Thử tìm bằng tên, số điện thoại hoặc mã căn khác."
    status == "pending" -> "Không có lịch chờ xác nhận" to "Bạn đã xử lý hết các yêu cầu hẹn xem nhà mới."
    status == "confirmed" -> "Chưa có lịch đã xác nhận" to "Lịch đã chốt giờ với khách sẽ hiển thị tại đây."
    status == "completed" -> "Chưa có buổi xem nhà hoàn tất" to "Đánh dấu \"Đã xem nhà\" sau mỗi buổi dẫn khách để theo dõi tại đây."
    status == "cancelled" -> "Không có lịch đã hủy" to "Các lịch hẹn bị hủy sẽ được lưu lại tại đây."
    isAdmin -> "Chưa có lịch hẹn nào" to "Lịch khách đặt từ trang sản phẩm hoặc khung chat sẽ hiển thị tại đây."
    else -> "Chưa có lịch hẹn nào" to "Khi khách đặt hẹn xem nhà với bạn, lịch sẽ hiển thị tại đây."
}

@Composable
private fun AppointmentTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun StatusFilterChip(label: String, count: Int, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) color else Color.White,
        border = if (selected) null else BorderStroke(1.dp, SoftBorder),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else FutaColors.Navy
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else Color(0xFFF1F5F9))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    "$count",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else if (count > 0) color else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun SortMenu(current: ApptSort, onSelect: (ApptSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(current.label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
            Icon(Icons.Default.ExpandMore, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
            ApptSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontSize = 14.sp, color = FutaColors.Navy) },
                    trailingIcon = if (option == current) {
                        { Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun DayHeader(header: ApptRow.Header) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (header.isToday) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(FutaColors.BrandGreen))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            header.title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (header.isToday) FutaColors.BrandGreen else FutaColors.Navy
        )
        Text("  ·  ${header.count} lịch", fontSize = 12.sp, color = FutaColors.Slate)
    }
}

@Composable
private fun StatusPill(status: ApptStatus, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(status.bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(status.icon, null, tint = status.fg, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(status.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = status.fg)
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, color: Color = FutaColors.SubLabel, weight: FontWeight = FontWeight.Normal) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun propertyLabel(item: JSONValue): String? {
    val property = item["property"]
    val code = property["propertyCode"].string
    val detail = property["projectName"].string.ifBlank { property["title"].string }
    return when {
        code.isNotBlank() && detail.isNotBlank() -> "$code · $detail"
        code.isNotBlank() -> code
        detail.isNotBlank() -> detail
        else -> null
    }
}

@Composable
private fun AppointmentCard(
    item: JSONValue,
    showAdvisor: Boolean,
    now: ZonedDateTime,
    updating: Boolean,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onAdvance: (ApptStatus) -> Unit
) {
    val status = ApptStatus.of(item["status"].string)
    val time = parseTime(item["scheduledAt"].string)
    val overdue = isOverdue(item, now)
    val next = when (status) {
        ApptStatus.PENDING -> ApptStatus.CONFIRMED
        ApptStatus.CONFIRMED -> ApptStatus.COMPLETED
        else -> null
    }

    FutaCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (overdue) Color(0xFFFECACA) else SoftBorder,
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (overdue) Color(0xFFFEF2F2) else status.bg)
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val tone = if (overdue) DangerRed else status.fg
                    Text(time?.let { formatPattern(it, "HH:mm") } ?: "--:--", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = tone)
                    Text(
                        time?.let { "${weekdayShort(it.dayOfWeek)} ${formatPattern(it, "dd/MM")}" } ?: "Chưa chốt",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tone.copy(alpha = 0.8f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item["customerName"].string.ifBlank { "Khách hàng" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusPill(status)
                    }
                    InfoLine(Icons.Default.Phone, item["customerPhone"].string, FutaColors.Body, FontWeight.Medium)
                    val property = propertyLabel(item)
                    InfoLine(Icons.Default.Apartment, property ?: "Chưa gắn sản phẩm", if (property == null) Color(0xFF94A3B8) else FutaColors.SubLabel)
                    if (showAdvisor) {
                        val advisor = item["advisor"]["name"].string
                        InfoLine(
                            Icons.Default.SupportAgent,
                            if (advisor.isBlank()) "Chưa phân công TVV" else "TVV: $advisor",
                            if (advisor.isBlank()) Color(0xFFD97706) else FutaColors.SubLabel,
                            if (advisor.isBlank()) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    val note = item["note"].string
                    if (note.isNotBlank()) {
                        Text(
                            "“${note.trim()}”",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = FutaColors.Slate,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (overdue && time != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF2F2))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Quá giờ hẹn (${relativeLabel(time, now).lowercase()}) — cập nhật kết quả",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed
                    )
                }
            } else if (time != null && status.isOpen && Duration.between(now, time).toHours() < 48) {
                Spacer(Modifier.height(8.dp))
                Text(
                    relativeLabel(time, now),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = status.fg
                )
            }

            if (next != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutaButton(
                        text = "Gọi khách",
                        onClick = onCall,
                        variant = FutaButtonVariant.OUTLINE,
                        icon = Icons.Default.Phone,
                        height = 38.dp,
                        modifier = Modifier.weight(1f)
                    )
                    FutaButton(
                        text = when {
                            updating -> "Đang lưu…"
                            next == ApptStatus.CONFIRMED -> "Xác nhận lịch"
                            else -> "Đã xem nhà"
                        },
                        onClick = { if (!updating) onAdvance(next) },
                        variant = if (next == ApptStatus.CONFIRMED) FutaButtonVariant.PRIMARY else FutaButtonVariant.MINT,
                        icon = if (next == ApptStatus.CONFIRMED) Icons.Default.Check else Icons.Default.TaskAlt,
                        enabled = !updating,
                        height = 38.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// endregion

// region Detail screen

@Composable
fun StaffViewingAppointmentDetailScreen(
    appointmentId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by AppSession.shared.currentUser.collectAsState()
    val role = user?.get("role")?.string.orEmpty()
    val isAdmin = role == "admin"
    val allowed = AppSession.shared.isAuthenticated && role in STAFF_APPOINTMENT_ROLES

    var appointment by remember { mutableStateOf<JSONValue?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var draftTime by remember { mutableStateOf<ZonedDateTime?>(null) }
    var timeTouched by remember { mutableStateOf(false) }
    var draftNote by remember { mutableStateOf("") }
    var draftAdvisor by remember { mutableStateOf<JSONValue?>(null) }
    var advisorTouched by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmTarget by remember { mutableStateOf<ApptStatus?>(null) }
    var showAdvisorPicker by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    fun resetDraft(value: JSONValue) {
        draftTime = parseTime(value["scheduledAt"].string)
        timeTouched = false
        draftNote = value["note"].string
        draftAdvisor = value["advisor"].takeIf { it.id.isNotEmpty() }
        advisorTouched = false
    }

    LaunchedEffect(appointmentId, reloadKey, allowed) {
        if (!allowed) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val value = APIClient.get().request("/viewing-appointments/$appointmentId")["data"]
            appointment = value
            resetDraft(value)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Không thể tải lịch hẹn"
        } finally {
            loading = false
        }
    }

    val current = appointment
    val originalTime = current?.let { parseTime(it["scheduledAt"].string) }
    val timeChanged = timeTouched && draftTime?.toInstant() != originalTime?.toInstant()
    val noteChanged = current != null && draftNote.trim() != current["note"].string.trim()
    val advisorChanged = advisorTouched && current != null && (draftAdvisor?.id ?: "") != current["advisorId"].string
    val dirty = timeChanged || noteChanged || advisorChanged

    fun applyUpdate(updated: JSONValue, message: String) {
        if (!updated.isNull) {
            appointment = updated
            resetDraft(updated)
        }
        ViewingAppointmentSync.bump()
        ToastCenter.show(message)
    }

    fun save() {
        val target = current ?: return
        saving = true
        scope.launch {
            try {
                val body = JSONObject()
                if (timeChanged) body.put("scheduledAt", draftTime?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) ?: JSONObject.NULL)
                if (noteChanged) body.put("note", draftNote.trim())
                if (advisorChanged) body.put("advisorId", draftAdvisor?.id?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
                applyUpdate(patchAppointment(target.id, body), "Đã lưu thay đổi lịch hẹn")
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: "Không thể lưu thay đổi", isError = true)
            } finally {
                saving = false
            }
        }
    }

    fun changeStatus(next: ApptStatus, scheduledAt: ZonedDateTime? = null) {
        val target = current ?: return
        saving = true
        scope.launch {
            try {
                val body = JSONObject().put("status", next.wire)
                scheduledAt?.let { body.put("scheduledAt", it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) }
                applyUpdate(patchAppointment(target.id, body), statusToast(next, target["customerName"].string))
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: "Không thể cập nhật trạng thái", isError = true)
            } finally {
                saving = false
            }
        }
    }

    fun requestBack() {
        if (dirty) showDiscard = true else onBack()
    }
    BackHandler(enabled = dirty) { showDiscard = true }

    Scaffold(
        containerColor = PageBackground,
        topBar = { AppointmentTopBar("Chi tiết lịch hẹn", current?.get("customerName")?.string, ::requestBack) },
        bottomBar = {
            if (current != null) {
                DetailActionBar(
                    status = ApptStatus.of(current["status"].string),
                    dirty = dirty,
                    saving = saving,
                    onDiscard = { resetDraft(current) },
                    onSave = ::save,
                    onRequestStatus = { next ->
                        when (next) {
                            ApptStatus.CANCELLED, ApptStatus.PENDING -> confirmTarget = next
                            ApptStatus.CONFIRMED -> if (draftTime == null) {
                                ToastCenter.show("Chọn giờ hẹn đã chốt với khách")
                                pickDateTime(context, null) { changeStatus(next, it) }
                            } else {
                                changeStatus(next)
                            }
                            ApptStatus.COMPLETED -> changeStatus(next)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !allowed -> FutaEmptyState(
                    title = "Không có quyền truy cập",
                    message = "Chỉ tư vấn viên và quản trị viên mới quản lý được lịch hẹn xem nhà.",
                    icon = Icons.Default.Lock,
                    modifier = Modifier.align(Alignment.Center)
                )
                loading && current == null -> Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FutaSkeletonBlock(height = 170.dp, radius = 16.dp)
                    FutaSkeletonBlock(height = 72.dp, radius = 14.dp)
                    FutaSkeletonBlock(height = 120.dp, radius = 16.dp)
                    FutaSkeletonBlock(height = 120.dp, radius = 16.dp)
                }
                current == null -> FutaEmptyState(
                    title = "Không tải được lịch hẹn",
                    message = error ?: "Lịch hẹn không tồn tại hoặc bạn không có quyền xem.",
                    icon = Icons.Default.CloudOff,
                    modifier = Modifier.align(Alignment.Center),
                    actionButton = { FutaButton("Thử lại", onClick = { reloadKey++ }, icon = Icons.Default.Refresh) }
                )
                else -> AppointmentDetailContent(
                    item = current,
                    isAdmin = isAdmin,
                    draftTime = draftTime,
                    timeChanged = timeChanged,
                    draftNote = draftNote,
                    onNoteChange = { draftNote = it },
                    draftAdvisor = draftAdvisor,
                    advisorChanged = advisorChanged,
                    onPickTime = {
                        pickDateTime(context, draftTime) {
                            draftTime = it
                            timeTouched = true
                        }
                    },
                    onPickAdvisor = { showAdvisorPicker = true },
                    onCall = { dial(context, current["customerPhone"].string) },
                    onSms = { sms(context, current["customerPhone"].string) },
                    onNavigate = onNavigate
                )
            }
        }
    }

    val customerName = current?.get("customerName")?.string.orEmpty()
    FutaDialog(
        visible = confirmTarget == ApptStatus.CANCELLED,
        onDismiss = { confirmTarget = null },
        title = "Hủy lịch hẹn này?",
        confirmText = "Hủy lịch",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Giữ lại",
        onConfirm = { changeStatus(ApptStatus.CANCELLED) }
    ) {
        Text(
            "Lịch hẹn xem nhà của $customerName sẽ chuyển sang \"Đã hủy\" và các bên liên quan sẽ nhận được thông báo.",
            fontSize = 14.sp,
            color = FutaColors.SubLabel,
            lineHeight = 20.sp
        )
    }
    FutaDialog(
        visible = confirmTarget == ApptStatus.PENDING,
        onDismiss = { confirmTarget = null },
        title = "Mở lại lịch hẹn?",
        confirmText = "Mở lại",
        onConfirm = { changeStatus(ApptStatus.PENDING) }
    ) {
        Text(
            "Lịch hẹn sẽ quay về trạng thái \"Chờ xác nhận\" để bạn chốt lại giờ với khách.",
            fontSize = 14.sp,
            color = FutaColors.SubLabel,
            lineHeight = 20.sp
        )
    }
    FutaDialog(
        visible = showDiscard,
        onDismiss = { showDiscard = false },
        title = "Bỏ thay đổi chưa lưu?",
        confirmText = "Bỏ thay đổi",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Tiếp tục sửa",
        onConfirm = onBack
    ) {
        Text("Giờ hẹn, ghi chú hoặc tư vấn viên bạn vừa sửa sẽ không được lưu.", fontSize = 14.sp, color = FutaColors.SubLabel)
    }

    AdvisorPickerSheet(
        visible = showAdvisorPicker,
        selectedId = draftAdvisor?.id.orEmpty(),
        onDismiss = { showAdvisorPicker = false },
        onSelect = { advisor ->
            draftAdvisor = advisor
            advisorTouched = true
            showAdvisorPicker = false
        }
    )
}

@Composable
private fun AppointmentDetailContent(
    item: JSONValue,
    isAdmin: Boolean,
    draftTime: ZonedDateTime?,
    timeChanged: Boolean,
    draftNote: String,
    onNoteChange: (String) -> Unit,
    draftAdvisor: JSONValue?,
    advisorChanged: Boolean,
    onPickTime: () -> Unit,
    onPickAdvisor: () -> Unit,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val status = ApptStatus.of(item["status"].string)
    val now = remember(item) { ZonedDateTime.now(VN_ZONE) }
    val savedTime = parseTime(item["scheduledAt"].string)
    val overdue = isOverdue(item, now)
    val conversationId = item["conversationId"].string

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero: who + when
        FutaCard(Modifier.fillMaxWidth(), borderColor = SoftBorder) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(status)
                    if (overdue) {
                        Spacer(Modifier.width(6.dp))
                        Row(
                            Modifier.clip(CircleShape).background(Color(0xFFFEF2F2)).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = DangerRed, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quá giờ hẹn", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    item["customerName"].string.ifBlank { "Khách hàng" },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FutaColors.Navy
                )
                Text(item["customerPhone"].string, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
                Spacer(Modifier.height(14.dp))
                val tone = if (overdue) DangerRed else status.fg
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (overdue) Color(0xFFFEF2F2) else status.bg)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = tone, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (savedTime != null) {
                            Text(formatPattern(savedTime, "HH:mm"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = tone)
                            Text(fullDate(savedTime), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FutaColors.Body)
                        } else {
                            Text("Chưa chốt giờ hẹn", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tone)
                            Text("Gọi cho khách để thống nhất thời gian xem nhà", fontSize = 12.5.sp, color = FutaColors.SubLabel)
                        }
                    }
                    if (savedTime != null && status.isOpen) {
                        Text(relativeLabel(savedTime, now), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tone)
                    }
                }
            }
        }

        // Contact shortcuts
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ContactShortcut("Gọi điện", Icons.Default.Call, FutaColors.BrandGreen, onCall, Modifier.weight(1f))
            ContactShortcut("Nhắn SMS", Icons.Default.Sms, Color(0xFF2563EB), onSms, Modifier.weight(1f))
            if (conversationId.isNotBlank()) {
                ContactShortcut(
                    "Mở chat",
                    Icons.Default.Forum,
                    FutaColors.BrandOrange,
                    { onNavigate(FutaDestinations.chat(conversationId = conversationId, context = ProductContext.ADVISOR)) },
                    Modifier.weight(1f)
                )
            }
        }

        // Progress
        DetailSection("Tiến trình xử lý", Icons.Default.Timeline) {
            if (status == ApptStatus.CANCELLED) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EventBusy, null, tint = Color(0xFF64748B))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Lịch hẹn đã bị hủy", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Bấm \"Mở lại lịch hẹn\" nếu khách muốn hẹn lại.", fontSize = 12.5.sp, color = FutaColors.SubLabel)
                    }
                }
            } else {
                StatusStepper(status)
                Spacer(Modifier.height(10.dp))
                Text(
                    when (status) {
                        ApptStatus.PENDING -> "Gọi cho khách để chốt giờ, sau đó bấm \"Xác nhận lịch\"."
                        ApptStatus.CONFIRMED -> "Sau buổi dẫn khách xem nhà, bấm \"Khách đã xem nhà\" để hoàn tất."
                        else -> "Buổi xem nhà đã hoàn tất. Tiếp tục chăm sóc khách trong khung chat."
                    },
                    fontSize = 12.5.sp,
                    color = FutaColors.SubLabel,
                    lineHeight = 18.sp
                )
            }
        }

        // Schedule
        DetailSection(
            "Thời gian hẹn",
            Icons.Default.Schedule,
            trailing = { SectionAction(if (draftTime == null) "Chốt giờ" else "Đổi giờ", Icons.Default.EditCalendar, onPickTime) }
        ) {
            Text(
                draftTime?.let { "${formatPattern(it, "HH:mm")} · ${fullDate(it)}" } ?: "Chưa chốt giờ hẹn",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (draftTime == null) Color(0xFF94A3B8) else FutaColors.Navy
            )
            if (timeChanged) UnsavedHint("Giờ hẹn mới — nhớ bấm Lưu thay đổi")
            val reminder = item["reminderSentAt"].string
            if (reminder.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                InfoLine(Icons.Default.NotificationsActive, "Đã gửi nhắc hẹn lúc ${shortDateTime(reminder)}")
            }
        }

        // Property
        DetailSection("Sản phẩm quan tâm", Icons.Default.Apartment) {
            val property = item["property"]
            if (property.id.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(FutaDestinations.propertyDetail(property.id, ProductContext.ADVISOR)) }
                        .background(Color(0xFFF8FAFC))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        if (property["propertyCode"].string.isNotBlank()) {
                            Text(
                                property["propertyCode"].string,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FutaColors.BrandGreen
                            )
                        }
                        Text(
                            property["title"].string.ifBlank { "Xem chi tiết sản phẩm" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FutaColors.Navy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (property["projectName"].string.isNotBlank()) {
                            Text(property["projectName"].string, fontSize = 12.5.sp, color = FutaColors.SubLabel)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF94A3B8))
                }
            } else {
                Text("Chưa gắn sản phẩm cụ thể", fontSize = 14.sp, color = Color(0xFF94A3B8), fontStyle = FontStyle.Italic)
            }
        }

        // Advisor
        DetailSection(
            "Tư vấn viên phụ trách",
            Icons.Default.SupportAgent,
            trailing = if (isAdmin) {
                { SectionAction(if (draftAdvisor == null) "Phân công" else "Đổi", Icons.Default.SwapHoriz, onPickAdvisor) }
            } else null
        ) {
            if (draftAdvisor != null) {
                PersonRow(draftAdvisor["name"].string, draftAdvisor["phone"].string)
            } else {
                Text("Chưa phân công tư vấn viên", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD97706))
            }
            if (advisorChanged) UnsavedHint("Đã đổi tư vấn viên — nhớ bấm Lưu thay đổi")
        }

        // Note
        DetailSection("Ghi chú", Icons.AutoMirrored.Filled.StickyNote2) {
            FutaTextArea(
                value = draftNote,
                onValueChange = onNoteChange,
                placeholder = "Yêu cầu của khách, điểm đón, lưu ý khi dẫn xem nhà…",
                minLines = 3,
                maxLines = 8
            )
        }

        // Meta
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, SoftBorder, RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetaRow("Người tạo lịch", item["createdBy"]["name"].string.ifBlank { "Khách hàng tự đặt" })
            MetaRow("Đặt lúc", shortDateTime(item["createdAt"].string))
            MetaRow("Cập nhật lần cuối", shortDateTime(item["updatedAt"].string))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailSection(
    title: String,
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    FutaCard(Modifier.fillMaxWidth(), borderColor = SoftBorder) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SectionAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(FutaColors.MintBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
    }
}

@Composable
private fun UnsavedHint(text: String) {
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFD97706)))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309))
    }
}

@Composable
private fun ContactShortcut(label: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SoftBorder),
        onClick = onClick
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
        }
    }
}

@Composable
private fun StatusStepper(status: ApptStatus) {
    val steps = listOf(ApptStatus.PENDING, ApptStatus.CONFIRMED, ApptStatus.COMPLETED)
    val currentIndex = steps.indexOf(status)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, step ->
            val done = index < currentIndex || status == ApptStatus.COMPLETED
            val active = index == currentIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (index == 0) Color.Transparent else if (index <= currentIndex) FutaColors.BrandGreen else SoftBorder)
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> FutaColors.BrandGreen
                                    active -> step.fg
                                    else -> Color.White
                                }
                            )
                            .border(1.5.dp, if (done || active) Color.Transparent else SoftBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (index == steps.lastIndex) Color.Transparent else if (index < currentIndex) FutaColors.BrandGreen else SoftBorder)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    step.label,
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active || done) FutaColors.Navy else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun PersonRow(name: String, phone: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(FutaColors.MintBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().split(" ").lastOrNull()?.take(1)?.uppercase().orEmpty().ifBlank { "?" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.BrandGreen
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { "Chưa có tên" }, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            if (phone.isNotBlank()) Text(phone, fontSize = 12.5.sp, color = FutaColors.SubLabel)
        }
        trailing?.invoke()
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.5.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
    }
}

@Composable
private fun DetailActionBar(
    status: ApptStatus,
    dirty: Boolean,
    saving: Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onRequestStatus: (ApptStatus) -> Unit
) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                dirty -> {
                    FutaButton("Hoàn tác", onClick = onDiscard, variant = FutaButtonVariant.OUTLINE, enabled = !saving, modifier = Modifier.weight(1f))
                    FutaButton(
                        if (saving) "Đang lưu…" else "Lưu thay đổi",
                        onClick = onSave,
                        icon = Icons.Default.Save,
                        enabled = !saving,
                        modifier = Modifier.weight(1.4f)
                    )
                }
                status == ApptStatus.PENDING -> {
                    FutaButton("Hủy lịch", onClick = { onRequestStatus(ApptStatus.CANCELLED) }, variant = FutaButtonVariant.OUTLINE, enabled = !saving, modifier = Modifier.weight(1f))
                    FutaButton(
                        if (saving) "Đang lưu…" else "Xác nhận lịch",
                        onClick = { onRequestStatus(ApptStatus.CONFIRMED) },
                        icon = Icons.Default.Check,
                        enabled = !saving,
                        modifier = Modifier.weight(1.4f)
                    )
                }
                status == ApptStatus.CONFIRMED -> {
                    FutaButton("Hủy lịch", onClick = { onRequestStatus(ApptStatus.CANCELLED) }, variant = FutaButtonVariant.OUTLINE, enabled = !saving, modifier = Modifier.weight(1f))
                    FutaButton(
                        if (saving) "Đang lưu…" else "Khách đã xem nhà",
                        onClick = { onRequestStatus(ApptStatus.COMPLETED) },
                        icon = Icons.Default.TaskAlt,
                        enabled = !saving,
                        modifier = Modifier.weight(1.4f)
                    )
                }
                else -> FutaButton(
                    "Mở lại lịch hẹn",
                    onClick = { onRequestStatus(ApptStatus.PENDING) },
                    variant = FutaButtonVariant.OUTLINE,
                    icon = Icons.Default.Replay,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AdvisorPickerSheet(
    visible: Boolean,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (JSONValue?) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var advisors by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(search) {
        delay(300)
        debounced = search.trim()
    }
    LaunchedEffect(visible, debounced) {
        if (!visible) return@LaunchedEffect
        loading = true
        try {
            advisors = coroutineScope {
                listOf("sale", "telesale").map { role ->
                    async {
                        val params = mutableMapOf("roleFilter" to role, "statusFilter" to "active", "limit" to "50")
                        if (debounced.isNotEmpty()) params["searchQuery"] = debounced
                        APIClient.get().request("/users", query = params)["data"].array
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.id }.sortedBy { it["name"].string.lowercase() }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Không thể tải danh sách tư vấn viên"
        } finally {
            loading = false
        }
    }

    FutaBottomSheet(visible = visible, onDismiss = onDismiss, title = "Phân công tư vấn viên") {
        FutaInput(
            value = search,
            onValueChange = { search = it },
            placeholder = "Tìm theo tên hoặc số điện thoại",
            leadingIcon = Icons.Default.Search
        )
        Spacer(Modifier.height(10.dp))
        if (selectedId.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(null) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PersonOff, null, tint = DangerRed)
                Spacer(Modifier.width(10.dp))
                Text("Bỏ phân công", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DangerRed)
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))
        }
        when {
            loading && advisors.isEmpty() -> repeat(4) {
                FutaSkeletonBlock(height = 48.dp, radius = 12.dp, modifier = Modifier.padding(vertical = 5.dp))
            }
            error != null -> Text(error.orEmpty(), color = DangerRed, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            advisors.isEmpty() -> Text(
                "Không tìm thấy tư vấn viên phù hợp",
                color = FutaColors.Slate,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            else -> advisors.forEach { advisor ->
                val selected = advisor.id == selectedId
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) FutaColors.MintBg else Color.Transparent)
                        .clickable { onSelect(advisor) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    PersonRow(
                        name = advisor["name"].string,
                        phone = listOf(
                            advisor["phone"].string,
                            if (advisor["role"].string == "telesale") "Telesale" else "Tư vấn viên"
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        trailing = if (selected) {
                            { Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen) }
                        } else null
                    )
                }
            }
        }
    }
}

// endregion
