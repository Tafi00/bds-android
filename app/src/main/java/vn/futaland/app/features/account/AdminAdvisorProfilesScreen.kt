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
fun AdminAdvisorProfilesScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedStatus by remember { mutableStateOf("all") }
    var inspectingRequest by remember { mutableStateOf<JSONValue?>(null) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var actionBusy by remember { mutableStateOf(false) }

    val statuses = listOf(
        "all" to "Tất cả",
        "pending" to "Chờ duyệt",
        "approved" to "Đã duyệt",
        "rejected" to "Đã từ chối"
    )

    fun loadRequests() {
        scope.launch {
            loading = true
            try {
                var url = "/advisor/profile-requests"
                if (selectedStatus != "all") {
                    url += "?status=$selectedStatus"
                }
                val res = APIClient.get().request(url)
                requests = res["data"].array.ifEmpty { res["data"]["records"].array }
            } catch (_: Exception) {
                requests = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedStatus) {
        loadRequests()
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
                        text = "Duyệt hồ sơ TVV",
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
            // Status filter chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statuses.forEach { (sKey, sLabel) ->
                        val isSel = selectedStatus == sKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { selectedStatus = sKey }
                        ) {
                            Text(
                                text = sLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            if (loading) {
                items(4) {
                    FutaSkeletonBlock(height = 80.dp, radius = 14.dp)
                }
            } else if (requests.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không có hồ sơ nào",
                        message = "Không tìm thấy hồ sơ đăng ký tư vấn viên cần duyệt."
                    )
                }
            } else {
                itemsIndexed(requests, key = { idx, item -> item.id.ifEmpty { "req-$idx" } }) { _, req ->
                    AdvisorProfileRequestRowItem(
                        req = req,
                        onClick = { inspectingRequest = req }
                    )
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Inspect BottomSheet
    inspectingRequest?.let { req ->
        val profile = req["profile"]
        val user = req["user"]
        val name = profile["fullName"].string.ifEmpty { user["name"].string.ifEmpty { req["userName"].string.ifEmpty { "Tư vấn viên" } } }
        val email = profile["email"].string.ifEmpty { user["email"].string.ifEmpty { req["userEmail"].string } }
        val phone = profile["phone"].string.ifEmpty { user["phone"].string.ifEmpty { req["userPhone"].string } }
        val status = req["profileStatus"].string.ifEmpty { req["status"].string.ifEmpty { "pending" } }
        val userId = req["userId"].string.ifEmpty { user["id"].string.ifEmpty { req.id } }

        FutaBottomSheet(
            visible = true,
            onDismiss = { inspectingRequest = null },
            title = "Chi tiết hồ sơ TVV"
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
                        if (email.isNotEmpty()) Text("Email: $email", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (phone.isNotEmpty()) Text("SĐT: $phone", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (profile["experience"].string.isNotEmpty()) {
                            Text("Kinh nghiệm: ${profile["experience"].string} năm", fontSize = 12.5.sp, color = FutaColors.Slate)
                        }
                        if (profile["bio"].string.isNotEmpty()) {
                            Text("Giới thiệu: ${profile["bio"].string}", fontSize = 12.sp, color = FutaColors.Slate)
                        }
                    }
                }

                if (status.lowercase() == "pending") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FutaButton(
                            text = "Từ chối",
                            variant = FutaButtonVariant.OUTLINE,
                            onClick = { showRejectDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                        FutaButton(
                            text = "Duyệt hồ sơ",
                            variant = FutaButtonVariant.PRIMARY,
                            onClick = {
                                scope.launch {
                                    actionBusy = true
                                    try {
                                        APIClient.get().request(
                                            "/advisor/profiles/$userId/review",
                                            method = "PATCH",
                                            bodyJson = "{\"decision\":\"approve\",\"reason\":\"Hồ sơ đạt tiêu chuẩn\"}"
                                        )
                                        ToastCenter.show("Đã duyệt hồ sơ tư vấn viên thành công!")
                                        inspectingRequest = null
                                        loadRequests()
                                    } catch (e: Exception) {
                                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                    } finally {
                                        actionBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                } else {
                    FutaButton(
                        text = "Đóng",
                        variant = FutaButtonVariant.OUTLINE,
                        onClick = { inspectingRequest = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showRejectDialog && inspectingRequest != null) {
        val user = inspectingRequest!!["user"]
        val userId = inspectingRequest!!["userId"].string.ifEmpty { user["id"].string.ifEmpty { inspectingRequest!!.id } }
        FutaDialog(
            visible = true,
            title = "Từ chối hồ sơ TVV",
            confirmText = "Xác nhận từ chối",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                scope.launch {
                    try {
                        APIClient.get().request(
                            "/advisor/profiles/$userId/review",
                            method = "PATCH",
                            bodyJson = "{\"decision\":\"reject\",\"reason\":\"$rejectReason\"}"
                        )
                        ToastCenter.show("Đã từ chối hồ sơ!")
                        showRejectDialog = false
                        inspectingRequest = null
                        loadRequests()
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { showRejectDialog = false }
        ) {
            FutaInput(value = rejectReason, onValueChange = { rejectReason = it }, placeholder = "Nhập lý do từ chối...")
        }
    }
}

@Composable
private fun AdvisorProfileRequestRowItem(
    req: JSONValue,
    onClick: () -> Unit
) {
    val profile = req["profile"]
    val user = req["user"]
    val name = profile["fullName"].string.ifEmpty { user["name"].string.ifEmpty { req["userName"].string.ifEmpty { "Tư vấn viên" } } }
    val email = profile["email"].string.ifEmpty { user["email"].string.ifEmpty { req["userEmail"].string } }
    val status = req["profileStatus"].string.ifEmpty { req["status"].string.ifEmpty { "pending" } }
    val exp = profile["experience"].string.ifEmpty { if (req["experienceYears"].int > 0) "${req["experienceYears"].int}" else "" }

    val (statusLabel, statusColor, statusBg) = when (status.lowercase()) {
        "approved" -> Triple("Đã duyệt", FutaColors.BrandGreen, Color(0xFFEAF5EF))
        "rejected" -> Triple("Đã từ chối", Color(0xFFDC2626), Color(0xFFFEE2E2))
        else -> Triple("Chờ duyệt", Color(0xFFF97316), Color(0xFFFFF7ED))
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = Color(0xFFEFF6FF), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(22.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    Surface(shape = CircleShape, color = statusBg) {
                        Text(statusLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }

                if (email.isNotEmpty()) {
                    Text(email, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (exp.isNotEmpty()) {
                    Text("Kinh nghiệm: $exp năm", fontSize = 11.sp, color = FutaColors.Slate)
                }
            }
        }
    }
}
