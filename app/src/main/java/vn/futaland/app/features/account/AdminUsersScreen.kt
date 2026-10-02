package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.LocalizedRole
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.*

// Mirrors iOS AdminPeopleViews.swift (AdminUsersView / AdminUserDetailView):
// GET /users (searchQuery, roleFilter, accountTypeFilter, customRoleFilter, statusFilter, page, limit),
// PATCH /users/:id (profile), PATCH /users/:id/role, POST /users/:id/block|unblock|suspend-holding|unsuspend-holding.

private sealed interface UserRoute {
    data class Detail(val user: JSONValue) : UserRoute
    data class Edit(val user: JSONValue) : UserRoute
}

private data class UserFilters(
    val role: String = "all",
    val accountType: String = "all",
    val customRole: String = "all",
    val status: String = "all"
) {
    val activeCount: Int get() = listOf(role, accountType, customRole, status).count { it != "all" }
}

private enum class UserSort(val title: String) { NEWEST("Mới nhất"), NAME("Tên: A → Z"), LAST_LOGIN("Đăng nhập gần nhất") }

private val systemRoleOptions = listOf(
    SelectOption("admin", "Quản trị viên"),
    SelectOption("sale", "Kinh doanh"),
    SelectOption("telesale", "Telesale"),
    SelectOption("advisor_trainee", "Đại lý"),
    SelectOption("customer", "Khách hàng")
)
private val accountTypeOptions = listOf(
    SelectOption("advisor", "Tư vấn viên"),
    SelectOption("advisor_expired", "TVV hết hạn gói"),
    SelectOption("user", "Người dùng"),
    SelectOption("admin", "Quản trị")
)

private fun roleColor(role: String): Color = when (role) {
    "admin" -> Color(0xFFDC2626)
    "sale" -> Color(0xFF2563EB)
    "telesale" -> Color(0xFF7C3AED)
    "advisor_trainee" -> FutaColors.BrandGreen
    "customer" -> FutaColors.Slate
    else -> Color(0xFFF97316)
}

private fun isHoldingSuspended(user: JSONValue): Boolean {
    if (user["isHoldingSuspended"].bool) return true
    val until = user["holdingSuspendedUntil"].string
    if (until.isEmpty()) return false
    return runCatching { java.time.Instant.parse(until).isAfter(java.time.Instant.now()) }.getOrDefault(true)
}

/** Effective role code: the custom role when one is assigned, else the system role. */
private fun effectiveRole(user: JSONValue) = user["customRoleCode"].string.ifEmpty { user["role"].string.ifEmpty { "customer" } }

@Composable
fun AdminUsersScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<UserRoute>() }
    val listState = rememberLazyListState()
    var users by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var roles by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(UserFilters()) }
    var sort by remember { mutableStateOf(UserSort.NEWEST) }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showFilter by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(UserFilters()) }

    suspend fun fetch() {
        loading = true
        try {
            val query = mutableMapOf("page" to "$page", "limit" to "25")
            search.trim().takeIf { it.isNotEmpty() }?.let { query["searchQuery"] = it }
            if (filters.role != "all") query["roleFilter"] = filters.role
            if (filters.accountType != "all") query["accountTypeFilter"] = filters.accountType
            if (filters.customRole != "all") query["customRoleFilter"] = filters.customRole
            if (filters.status != "all") query["statusFilter"] = filters.status
            val res = APIClient.get().request("/users", query = query)
            users = res["data"].array
            totalPages = maxOf(1, res["pagination"]["totalPages"].int)
            total = res["pagination"]["total"].int
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải người dùng")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        roles = runCatching { APIClient.get().request("/users/roles")["data"].array }.getOrDefault(emptyList())
    }
    LaunchedEffect(search, filters, page, reloadKey) {
        if (search.isNotEmpty()) delay(300)
        fetch()
    }

    val customRoles = roles.filter { !it["isSystem"].bool }
    fun roleName(code: String): String =
        roles.firstOrNull { it["code"].string == code && !it["isSystem"].bool }?.get("name")?.string ?: LocalizedRole.name(code)

    val sorted = remember(users, sort) {
        when (sort) {
            UserSort.NEWEST -> users
            UserSort.NAME -> users.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["name"].string })
            UserSort.LAST_LOGIN -> users.sortedByDescending { it["lastLoginAt"].string }
        }
    }
    val applied = buildList {
        if (filters.role != "all") add(AppliedFilter(tr(systemRoleOptions.first { it.value == filters.role }.label)) { filters = filters.copy(role = "all"); page = 1 })
        if (filters.accountType != "all") add(AppliedFilter(tr(accountTypeOptions.first { it.value == filters.accountType }.label)) { filters = filters.copy(accountType = "all"); page = 1 })
        if (filters.customRole != "all") add(AppliedFilter(roleName(filters.customRole)) { filters = filters.copy(customRole = "all"); page = 1 })
        if (filters.status != "all") add(AppliedFilter(if (filters.status == "blocked") tr("Đã bị chặn") else tr("Đang hoạt động")) { filters = filters.copy(status = "all"); page = 1 })
    }

    ScreenStackHost(
        stack = stack,
        base = {
            Scaffold(
                containerColor = FutaColors.PageBg,
                topBar = {
                    SalesAdminTopBar(title = "Người dùng", subtitle = tr("{0} tài khoản", total), onBack = onBack) {
                        SortMenuButton(UserSort.entries, sort, { it.title }, { sort = it }, sort == UserSort.NEWEST)
                        BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), filters.activeCount) { draft = filters; showFilter = true }
                    }
                }
            ) { padding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { AdminSearchField(search, { search = it; page = 1 }, "Tìm theo tên, email, SĐT…") }
                    if (applied.isNotEmpty()) item { AppliedFilterChips(applied) { filters = UserFilters(); page = 1 } }
                    when {
                        loading && users.isEmpty() -> items(6) { FutaAdminRowSkeleton() }
                        loadError != null && users.isEmpty() -> item { AdminErrorState(loadError.orEmpty(), { scope.launch { fetch() } }) }
                        users.isEmpty() -> item {
                            AdminListEmpty(
                                hasRecords = search.isNotBlank() || filters.activeCount > 0,
                                emptyTitle = "Không có người dùng",
                                emptyMessage = "Chưa có tài khoản nào trong hệ thống.",
                                onClearFilters = { search = ""; filters = UserFilters(); page = 1 },
                                icon = Icons.Default.People
                            )
                        }
                        else -> {
                            items(sorted, key = { "u-" + it.id }) { user -> AdminUserRow(user, roleName(effectiveRole(user))) { stack.push(UserRoute.Detail(user)) } }
                            item {
                                PaginationBar(page, totalPages, tr("{0} tài khoản", total),
                                    { if (page > 1) { page--; scope.launch { listState.scrollToItem(0) } } },
                                    { if (page < totalPages) { page++; scope.launch { listState.scrollToItem(0) } } })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    ) { route ->
        when (route) {
            is UserRoute.Detail -> AdminUserDetailScreen(
                user = route.user,
                roles = roles,
                roleName = ::roleName,
                onBack = { stack.pop() },
                onEdit = { stack.push(UserRoute.Edit(route.user)) },
                onChanged = { updated ->
                    stack.replaceTop(UserRoute.Detail(updated))
                    reloadKey++
                }
            )
            is UserRoute.Edit -> AdminUserEditScreen(
                user = route.user,
                onClose = { stack.pop() },
                onSaved = { updated ->
                    stack.pop()
                    val detail = stack.entries.lastOrNull()
                    if (detail is UserRoute.Detail) stack.replaceTop(UserRoute.Detail(updated))
                    reloadKey++
                }
            )
        }
    }

    FilterSheet(
        visible = showFilter, title = "Bộ lọc", applyTitle = "Áp dụng",
        canReset = draft.activeCount > 0, onReset = { draft = UserFilters() },
        onApply = { filters = draft; page = 1; showFilter = false }, onDismiss = { showFilter = false }
    ) {
        FilterChipGroup("Vai trò hệ thống", listOf(SelectOption("all", "Tất cả")) + systemRoleOptions, draft.role) { draft = draft.copy(role = it) }
        FilterChipGroup("Loại tài khoản", listOf(SelectOption("all", "Tất cả")) + accountTypeOptions, draft.accountType) { draft = draft.copy(accountType = it) }
        if (customRoles.isNotEmpty()) {
            AdminSelectField(
                "Vai trò tuỳ chỉnh", draft.customRole,
                listOf(SelectOption("all", "Tất cả")) + customRoles.map { SelectOption(it["code"].string, it["name"].string) },
                { draft = draft.copy(customRole = it) }
            )
        }
        FilterChipGroup("Trạng thái", listOf(SelectOption("all", "Tất cả"), SelectOption("active", "Đang hoạt động"), SelectOption("blocked", "Đã bị chặn")), draft.status) { draft = draft.copy(status = it) }
    }
}

@Composable
private fun AdminUserRow(user: JSONValue, roleLabel: String, onClick: () -> Unit) {
    val role = effectiveRole(user)
    val color = roleColor(role)
    AdminRowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = color, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val name = user["name"].string
                    if (name.isNotEmpty()) VerbatimText(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    else Text("Chưa đặt tên", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    Spacer(Modifier.weight(1f))
                    StatusPill(roleLabel, color)
                }
                val phone = user["phone"].string
                val email = user["email"].string
                val sub = listOf(phone, email).filter { it.isNotEmpty() }.joinToString(" • ")
                if (sub.isNotEmpty()) VerbatimText(sub, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (user["isBlocked"].bool || isHoldingSuspended(user)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (user["isBlocked"].bool) StatusPill("Đã bị chặn", Color(0xFFDC2626))
                        if (isHoldingSuspended(user)) StatusPill("Đình chỉ giữ chỗ", Color(0xFFF97316))
                    }
                }
            }
        }
    }
}

// ============================================================================
// Detail
// ============================================================================

private val suspendDurations = listOf(
    SelectOption("15", "15 phút"),
    SelectOption("30", "30 phút"),
    SelectOption("60", "1 giờ"),
    SelectOption("1440", "24 giờ (1 ngày)"),
    SelectOption("10080", "7 ngày")
)

@Composable
private fun AdminUserDetailScreen(
    user: JSONValue,
    roles: List<JSONValue>,
    roleName: (String) -> String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onChanged: (JSONValue) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var showRoleSheet by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf(effectiveRole(user)) }
    var showRoleConfirm by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    var showUnblock by remember { mutableStateOf(false) }
    var showSuspend by remember { mutableStateOf(false) }
    var showUnsuspend by remember { mutableStateOf(false) }
    var suspendMinutes by remember { mutableStateOf("60") }
    var suspendReason by remember { mutableStateOf("") }

    val blocked = user["isBlocked"].bool
    val suspended = isHoldingSuspended(user)
    val role = effectiveRole(user)

    /** Runs an action; the response's user (when any) is merged into the shown record. */
    fun act(key: String, path: String, body: String?, success: String, localUpdate: Map<String, Any?> = emptyMap()) {
        if (busy != null) return
        scope.launch {
            busy = key
            try {
                val res = APIClient.get().request(path, method = if (path.endsWith("/role")) "PATCH" else "POST", bodyJson = body ?: "{}")
                var updated = user.withUpdates(localUpdate)
                val data = res["data"]
                (data.element as? kotlinx.serialization.json.JsonObject)?.forEach { (k, v) -> updated = updated.with(k, JSONValue(v)) }
                ToastCenter.show(tr(success))
                onChanged(updated)
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Đã có lỗi xảy ra. Vui lòng thử lại."), isError = true)
            } finally {
                busy = null
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Chi tiết người dùng", subtitle = user["phone"].string.ifEmpty { null }, onBack = onBack) {
                StatusPill(if (blocked) "Đã bị chặn" else "Đang hoạt động", if (blocked) Color(0xFFDC2626) else Color(0xFF16A34A), dot = true)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DetailSection("Thông tin cá nhân", Icons.Default.AccountCircle, trailing = {
                FutaButton(text = "Sửa", icon = Icons.Default.Edit, variant = FutaButtonVariant.MINT, height = 32.dp, onClick = onEdit)
            }) {
                InfoRow("Họ và tên", user["name"].string, verbatim = true)
                InfoRow("Số điện thoại", user["phone"].string, verbatim = true)
                InfoRow("Email", user["email"].string, verbatim = true)
                InfoRow("Mã giới thiệu (Referral)", user["referralCode"].string, verbatim = true)
            }
            DetailSection("Vai trò hệ thống", Icons.Default.Key, trailing = { StatusPill(roleName(role), roleColor(role)) }) {
                InfoRow("Loại tài khoản", tr(accountTypeOptions.firstOrNull { it.value == user["accountType"].string }?.label ?: "-"))
                val ws = user["advisorWorkspace"]
                if (!ws.isNull) {
                    val agency = ws["agency"]["name"].string.ifEmpty { ws["agencyOtherName"].string }
                    if (agency.isNotEmpty()) InfoRow("Đại lý", agency, verbatim = true)
                    if (ws["packageExpiresAt"].string.isNotEmpty()) InfoRow("Gói TVV hết hạn", SalesFormatters.dateTime(ws["packageExpiresAt"].string))
                }
                FutaButton(
                    text = if (busy == "role") "Đang cập nhật…" else "Đổi vai trò",
                    icon = Icons.Default.ManageAccounts, variant = FutaButtonVariant.OUTLINE, enabled = busy == null,
                    onClick = { pendingRole = role; showRoleSheet = true }, modifier = Modifier.fillMaxWidth()
                )
            }
            DetailSection("Đình chỉ giữ chỗ (Holding)", Icons.Default.PauseCircle, iconTint = Color(0xFFF97316)) {
                if (suspended) {
                    StatusPill("Đang bị đình chỉ tính năng giữ chỗ", Color(0xFFF97316))
                    if (user["holdingSuspendedUntil"].string.isNotEmpty()) InfoRow("Thời hạn đến", SalesFormatters.dateTime(user["holdingSuspendedUntil"].string))
                    if (user["holdingSuspendedReason"].string.isNotEmpty()) InfoRow("Lý do", user["holdingSuspendedReason"].string, verbatim = true)
                    FutaButton(text = if (busy == "unsuspend") "Đang xử lý…" else "Gỡ đình chỉ giữ chỗ", enabled = busy == null, onClick = { showUnsuspend = true }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Tài khoản đang được phép giữ chỗ BĐS.", fontSize = 12.5.sp, color = FutaColors.Slate)
                    FutaButton(text = if (busy == "suspend") "Đang xử lý…" else "Đình chỉ giữ chỗ BĐS", variant = FutaButtonVariant.CREAM, enabled = busy == null, onClick = { showSuspend = true }, modifier = Modifier.fillMaxWidth())
                }
            }
            DetailSection("Thông tin hệ thống", Icons.Default.Info) {
                InfoRow("Mã", user.id, verbatim = true)
                InfoRow("Ngày tạo", SalesFormatters.dateTime(user["createdAt"].string))
                InfoRow("Đăng nhập gần nhất", SalesFormatters.dateTime(user["lastLoginAt"].string))
            }
            if (blocked) {
                DetailSection("Trạng thái khoá tài khoản", Icons.Default.Lock, iconTint = Color(0xFFDC2626)) {
                    StatusPill("Tài khoản đang bị chặn", Color(0xFFDC2626))
                    if (user["blockedReason"].string.isNotEmpty()) InfoRow("Lý do", user["blockedReason"].string, verbatim = true)
                    if (user["blockedAt"].string.isNotEmpty()) InfoRow("Thời điểm chặn", SalesFormatters.dateTime(user["blockedAt"].string))
                    FutaButton(text = if (busy == "unblock") "Đang xử lý…" else "Bỏ chặn tài khoản", icon = Icons.Default.LockOpen, enabled = busy == null, onClick = { showUnblock = true }, modifier = Modifier.fillMaxWidth())
                }
            } else {
                DangerZoneCard(
                    title = "Chặn tài khoản",
                    message = "Người dùng bị chặn sẽ không thể đăng nhập hoặc thực hiện giao dịch.",
                    buttonText = if (busy == "block") "Đang xử lý…" else "Chặn tài khoản người dùng",
                    enabled = busy == null,
                    onClick = { showBlock = true }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    FutaBottomSheet(
        visible = showRoleSheet,
        onDismiss = { showRoleSheet = false },
        title = "Đổi vai trò",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = { showRoleSheet = false }, modifier = Modifier.weight(1f))
                FutaButton(text = "Cập nhật vai trò", enabled = pendingRole != role, onClick = { showRoleSheet = false; showRoleConfirm = true }, modifier = Modifier.weight(1.6f))
            }
        }
    ) {
        val options = roles.map { SelectOption(it["code"].string, it["name"].string.ifEmpty { LocalizedRole.name(it["code"].string) }) }
            .ifEmpty { systemRoleOptions }
        AdminSelectField("Vai trò", pendingRole, options, { pendingRole = it })
        Text("Quyền truy cập của người dùng sẽ thay đổi theo vai trò mới.", fontSize = 12.sp, color = FutaColors.Slate)
    }

    ConfirmDialog(
        visible = showRoleConfirm,
        title = "Đổi vai trò người dùng?",
        message = tr("Vai trò sẽ chuyển từ \"{0}\" sang \"{1}\".", roleName(role), roleName(pendingRole)),
        confirmText = "Xác nhận",
        onDismiss = { showRoleConfirm = false },
        onConfirm = {
            showRoleConfirm = false
            act("role", "/users/${user.id}/role", buildJsonObject { put("role", pendingRole) }.toString(), "Đã thay đổi vai trò người dùng thành công!",
                localUpdate = if (roles.any { it["code"].string == pendingRole && !it["isSystem"].bool }) mapOf("customRoleCode" to pendingRole) else mapOf("customRoleCode" to null, "role" to pendingRole))
        }
    )
    ReasonDialog(
        visible = showBlock,
        title = "Chặn tài khoản",
        message = "Người dùng bị chặn sẽ không thể đăng nhập hoặc thực hiện giao dịch.",
        placeholder = "Nhập lý do chặn…",
        confirmText = "Xác nhận chặn",
        reasonRequired = true,
        onDismiss = { showBlock = false },
        onConfirm = { reason ->
            showBlock = false
            act("block", "/users/${user.id}/block", buildJsonObject { put("reason", reason) }.toString(), "Đã chặn người dùng!")
        }
    )
    ConfirmDialog(
        visible = showUnblock,
        title = "Bỏ chặn tài khoản?",
        message = "Người dùng sẽ có thể đăng nhập và giao dịch trở lại.",
        confirmText = "Bỏ chặn",
        onDismiss = { showUnblock = false },
        onConfirm = { showUnblock = false; act("unblock", "/users/${user.id}/unblock", null, "Đã mở khoá tài khoản người dùng!") }
    )
    ConfirmDialog(
        visible = showUnsuspend,
        title = "Gỡ đình chỉ giữ chỗ?",
        message = "Người dùng sẽ có thể giữ chỗ BĐS trở lại.",
        confirmText = "Gỡ đình chỉ",
        onDismiss = { showUnsuspend = false },
        onConfirm = {
            showUnsuspend = false
            act("unsuspend", "/users/${user.id}/unsuspend-holding", null, "Đã gỡ đình chỉ giữ chỗ!", mapOf("holdingSuspendedUntil" to null, "isHoldingSuspended" to false))
        }
    )
    FutaDialog(
        visible = showSuspend,
        onDismiss = { showSuspend = false },
        title = "Đình chỉ giữ chỗ",
        confirmText = "Xác nhận",
        confirmVariant = FutaButtonVariant.SECONDARY,
        onConfirm = {
            showSuspend = false
            act(
                "suspend", "/users/${user.id}/suspend-holding",
                buildJsonObject {
                    put("durationMinutes", suspendMinutes.toIntOrNull() ?: 60)
                    put("reason", suspendReason.trim().ifEmpty { "Đình chỉ theo chỉ đạo quản trị" })
                }.toString(),
                "Đã đình chỉ quyền giữ chỗ của người dùng!"
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminSelectField("Thời lượng đình chỉ", suspendMinutes, suspendDurations, { suspendMinutes = it })
            FormTextField("Lý do đình chỉ", suspendReason, { suspendReason = it })
        }
    }
}

// ============================================================================
// Profile edit (PATCH /users/:id)
// ============================================================================

@Composable
private fun AdminUserEditScreen(user: JSONValue, onClose: () -> Unit, onSaved: (JSONValue) -> Unit) {
    val scope = rememberCoroutineScope()
    val init = listOf(user["name"].string, user["phone"].string, user["email"].string, user["referralCode"].string)
    var name by remember { mutableStateOf(init[0]) }
    var phone by remember { mutableStateOf(init[1]) }
    var email by remember { mutableStateOf(init[2]) }
    var referral by remember { mutableStateOf(init[3]) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    val dirty = listOf(name, phone, email, referral) != init
    fun close() { if (dirty && !saving) showDiscard = true else onClose() }
    BackHandler { close() }

    fun save() {
        error = when {
            name.isBlank() -> tr("Tên hiển thị không được để trống")
            phone.trim().length < 6 -> tr("Số điện thoại không hợp lệ")
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> tr("Email không hợp lệ")
            else -> null
        }
        if (error != null) return
        scope.launch {
            saving = true
            try {
                val res = APIClient.get().request(
                    "/users/${user.id}", method = "PATCH",
                    bodyJson = buildJsonObject {
                        put("name", name.trim())
                        put("phone", phone.trim())
                        put("email", email.trim())
                        if (referral.isBlank()) put("referralCode", JsonNull) else put("referralCode", referral.trim())
                    }.toString()
                )
                var updated = user.withUpdates(mapOf("name" to name.trim(), "phone" to phone.trim(), "email" to email.trim(), "referralCode" to referral.trim().ifEmpty { null }))
                (res["data"].element as? kotlinx.serialization.json.JsonObject)?.forEach { (k, v) -> updated = updated.with(k, JSONValue(v)) }
                ToastCenter.show(tr("Đã cập nhật thông tin thành công!"))
                onSaved(updated)
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể cập nhật thông tin"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = "Sửa thông tin người dùng", subtitle = user["name"].string.ifEmpty { null }, onBack = { close() }) },
        bottomBar = { FormActionBar("Cập nhật thông tin", saving, enabled = dirty, onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            FormSection("Thông tin cá nhân") {
                FormTextField("Họ và tên", name, { name = it }, required = true)
                FormTextField("Số điện thoại", phone, { phone = it }, required = true, keyboardType = KeyboardType.Phone)
                FormTextField("Email", email, { email = it }, required = true, keyboardType = KeyboardType.Email)
                FormTextField("Mã giới thiệu (Referral)", referral, { referral = it })
            }
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
}
