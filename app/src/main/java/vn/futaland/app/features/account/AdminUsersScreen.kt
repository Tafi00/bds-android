package vn.futaland.app.features.account

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

@Composable
fun AdminUsersScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("all") }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }

    var selectedUser by remember { mutableStateOf<JSONValue?>(null) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var blockReason by remember { mutableStateOf("") }
    var showSuspendDialog by remember { mutableStateOf(false) }
    var suspendMinutes by remember { mutableStateOf("60") }
    var suspendReason by remember { mutableStateOf("") }

    val roles = listOf(
        "all" to "Tất cả",
        "admin" to "Quản trị viên",
        "sale" to "Kinh doanh",
        "telesale" to "Telesale",
        "advisor" to "Tư vấn viên",
        "customer" to "Khách hàng"
    )

    fun loadUsers(searchQuery: String = search) {
        scope.launch {
            loading = true
            try {
                val q = searchQuery.trim()
                var url = "/users?page=$page&limit=20"
                if (q.isNotEmpty()) {
                    url += "&search=${java.net.URLEncoder.encode(q, "UTF-8")}"
                }
                if (selectedRole != "all") {
                    url += "&role=$selectedRole"
                }
                val res = APIClient.get().request(url)
                users = res["data"].array.ifEmpty { res["users"].array }
                totalPages = maxOf(1, res["pagination"]["totalPages"].int.takeIf { it > 0 } ?: 1)
            } catch (_: Exception) {
                users = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(search, selectedRole, page) {
        kotlinx.coroutines.delay(300)
        loadUsers(search)
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Surface(
                color = FutaColors.PageBg,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FutaHeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        onClick = onBack
                    )

                    Text(
                        text = "Quản lý người dùng",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    Spacer(Modifier.width(40.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Pinned Search Bar
            item {
                FutaInput(
                    value = search,
                    onValueChange = {
                        search = it
                        page = 1
                    },
                    placeholder = "Tìm theo tên, email, SĐT…",
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Xóa",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        search = ""
                                        page = 1
                                    },
                                tint = FutaColors.Slate
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Role Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    roles.forEach { (rKey, rLabel) ->
                        val isSel = selectedRole == rKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable {
                                selectedRole = rKey
                                page = 1
                            }
                        ) {
                            Text(
                                text = rLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Users List
            if (loading) {
                items(5) {
                    FutaAdminRowSkeleton()
                }
            } else if (users.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không có người dùng",
                        message = "Không tìm thấy người dùng phù hợp với tiêu chí tìm kiếm."
                    )
                }
            } else {
                itemsIndexed(users, key = { idx, item -> item.id.ifEmpty { "usr-$idx" } }) { _, user ->
                    AdminUserItemRow(
                        user = user,
                        onClick = { selectedUser = user }
                    )
                }

                if (totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trang $page / $totalPages",
                                fontSize = 12.sp,
                                color = FutaColors.Slate
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FutaButton(
                                    text = "Trước",
                                    variant = FutaButtonVariant.OUTLINE,
                                    enabled = page > 1,
                                    onClick = { if (page > 1) page-- }
                                )
                                FutaButton(
                                    text = "Sau",
                                    variant = FutaButtonVariant.OUTLINE,
                                    enabled = page < totalPages,
                                    onClick = { if (page < totalPages) page++ }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // User Detail BottomSheet
    selectedUser?.let { user ->
        val name = user["name"].string.ifEmpty { "Chưa đặt tên" }
        val role = user["role"].string.ifEmpty { "customer" }
        val isBlocked = user["isBlocked"].bool
        val isSuspended = user["isHoldingSuspended"].bool || user["holdingSuspendedUntil"].string.isNotEmpty()

        FutaBottomSheet(
            visible = true,
            onDismiss = { selectedUser = null },
            title = "Chi tiết người dùng"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Email: ${user["email"].string.ifEmpty { "Chưa cập nhật" }}", fontSize = 12.5.sp, color = FutaColors.Slate)
                        Text("SĐT: ${user["phone"].string.ifEmpty { "Chưa cập nhật" }}", fontSize = 12.5.sp, color = FutaColors.Slate)
                        Text("Vai trò: $role", fontSize = 12.5.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold)
                    }
                }

                // Actions: Block and Suspend
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FutaButton(
                        text = if (isBlocked) "Bỏ chặn" else "Chặn tài khoản",
                        variant = if (isBlocked) FutaButtonVariant.OUTLINE else FutaButtonVariant.DANGER,
                        onClick = {
                            if (isBlocked) {
                                scope.launch {
                                    try {
                                        APIClient.get().request("/users/${user.id}/unblock", method = "POST")
                                        ToastCenter.show("Đã mở khóa người dùng!")
                                        selectedUser = null
                                        loadUsers(search)
                                    } catch (e: Exception) {
                                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                    }
                                }
                            } else {
                                showBlockDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FutaButton(
                        text = if (isSuspended) "Hủy đình chỉ" else "Đình chỉ giữ chỗ",
                        variant = FutaButtonVariant.SECONDARY,
                        onClick = {
                            if (isSuspended) {
                                scope.launch {
                                    try {
                                        APIClient.get().request("/users/${user.id}/unsuspend-holding", method = "POST")
                                        ToastCenter.show("Đã gỡ đình chỉ giữ chỗ!")
                                        selectedUser = null
                                        loadUsers(search)
                                    } catch (e: Exception) {
                                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                    }
                                }
                            } else {
                                showSuspendDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showBlockDialog && selectedUser != null) {
        FutaDialog(
            visible = true,
            title = "Chặn tài khoản",
            confirmText = "Xác nhận chặn",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                scope.launch {
                    try {
                        APIClient.get().request(
                            "/users/${selectedUser!!.id}/block",
                            method = "POST",
                            bodyJson = "{\"reason\":\"$blockReason\"}"
                        )
                        ToastCenter.show("Đã chặn tài khoản!")
                        showBlockDialog = false
                        selectedUser = null
                        loadUsers(search)
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { showBlockDialog = false }
        ) {
            FutaInput(value = blockReason, onValueChange = { blockReason = it }, placeholder = "Nhập lý do chặn...")
        }
    }

    if (showSuspendDialog && selectedUser != null) {
        FutaDialog(
            visible = true,
            title = "Đình chỉ giữ chỗ",
            confirmText = "Xác nhận đình chỉ",
            confirmVariant = FutaButtonVariant.SECONDARY,
            onConfirm = {
                scope.launch {
                    try {
                        APIClient.get().request(
                            "/users/${selectedUser!!.id}/suspend-holding",
                            method = "POST",
                            bodyJson = "{\"durationMinutes\":${suspendMinutes.toIntOrNull() ?: 60},\"reason\":\"$suspendReason\"}"
                        )
                        ToastCenter.show("Đã đình chỉ giữ chỗ!")
                        showSuspendDialog = false
                        selectedUser = null
                        loadUsers(search)
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { showSuspendDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Thời gian đình chỉ (phút)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                FutaInput(value = suspendMinutes, onValueChange = { suspendMinutes = it }, placeholder = "60")
                Text("Lý do đình chỉ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                FutaInput(value = suspendReason, onValueChange = { suspendReason = it }, placeholder = "Lý do...")
            }
        }
    }
}

@Composable
private fun AdminUserItemRow(
    user: JSONValue,
    onClick: () -> Unit
) {
    val name = user["name"].string.ifEmpty { "Chưa đặt tên" }
    val role = user["role"].string.lowercase()
    val phone = user["phone"].string
    val email = user["email"].string
    val isBlocked = user["isBlocked"].bool
    val isSuspended = user["isHoldingSuspended"].bool || user["holdingSuspendedUntil"].string.isNotEmpty()

    val (roleLabel, roleColor, roleBg) = when (role) {
        "admin" -> Triple("Quản trị viên", Color(0xFFDC2626), Color(0xFFFEE2E2))
        "sale" -> Triple("Kinh doanh", Color(0xFF2563EB), Color(0xFFEFF6FF))
        "telesale" -> Triple("Telesale", Color(0xFF7C3AED), Color(0xFFF5F3FF))
        "advisor" -> Triple("Tư vấn viên", FutaColors.BrandGreen, Color(0xFFEAF5EF))
        else -> Triple("Khách hàng", FutaColors.Slate, Color(0xFFF1F5F9))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = roleBg, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = roleColor, modifier = Modifier.size(22.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    Surface(shape = CircleShape, color = roleBg) {
                        Text(roleLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = roleColor, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }

                val sub = if (phone.isNotEmpty() && email.isNotEmpty()) "$phone • $email" else phone.ifEmpty { email }
                if (sub.isNotEmpty()) {
                    Text(sub, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (isBlocked || isSuspended) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isBlocked) {
                            Surface(shape = CircleShape, color = Color(0xFFFEE2E2)) {
                                Text("Đã bị chặn", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        if (isSuspended) {
                            Surface(shape = CircleShape, color = Color(0xFFFFF7ED)) {
                                Text("Đình chỉ giữ chỗ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
