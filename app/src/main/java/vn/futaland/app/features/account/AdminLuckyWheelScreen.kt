package vn.futaland.app.features.account

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
@Composable
fun AdminLuckyWheelScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Cài đặt, 1: Cấp lượt, 2: Đối soát

    var adminState by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var spinsSummary by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var recentGrants by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var usersWithSpins by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Tab 0 states
    var isWheelEnabled by remember { mutableStateOf(true) }
    var campaignTitle by remember { mutableStateOf("") }
    var campaignDesc by remember { mutableStateOf("") }
    var dailyLimit by remember { mutableStateOf("1") }
    var prizesList by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var isSavingSettings by remember { mutableStateOf(false) }

    // Tab 1 states
    var grantSearchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var selectedUserForGrant by remember { mutableStateOf<JSONValue?>(null) }
    var grantSpinsCount by remember { mutableStateOf("5") }
    var grantNote by remember { mutableStateOf("") }
    var isGranting by remember { mutableStateOf(false) }

    // Tab 2 states
    var rewardCodeQuery by remember { mutableStateOf("") }
    var lookupSpinResult by remember { mutableStateOf<JSONValue?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var isUpdatingFulfillment by remember { mutableStateOf(false) }

    val tabs = remember {
        listOf(
            "1. Cài đặt",
            "2. Cấp lượt & VIP",
            "3. Đối soát quà"
        )
    }

    fun loadAllData() {
        scope.launch {
            loading = true
            try {
                val sRes = APIClient.get().request("/lucky-wheel/admin/settings")
                adminState = sRes["data"]
                val settings = sRes["data"]["settings"]
                isWheelEnabled = settings["enabled"].bool
                campaignTitle = settings["title"].string
                campaignDesc = settings["description"].string
                dailyLimit = "${settings["dailySpinLimit"].int.takeIf { it > 0 } ?: 1}"
                prizesList = settings["prizes"].array

                val sumRes = APIClient.get().request("/lucky-wheel/admin/users/spins-summary")
                spinsSummary = sumRes["data"]
                usersWithSpins = sumRes["data"]["users"].array

                val grRes = APIClient.get().request("/lucky-wheel/admin/grants")
                recentGrants = grRes["data"].array
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllData()
    }

    val totalUnused = spinsSummary["totalUnusedSpins"].int
    val usersWithSpinsCount = spinsSummary["totalUsersWithSpins"].int.takeIf { it > 0 } ?: usersWithSpins.size
    val playedSpins = adminState["totalSpins"].int.takeIf { it > 0 } ?: adminState["recentSpins"].array.size
    val manualGrantsCount = recentGrants.sumOf { it["spins"].int }

    Scaffold(
        topBar = {
            Surface(
                color = FutaColors.PageBg,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Column {
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
                            text = "Quản trị vòng quay",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        FutaHeaderIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Làm mới",
                            tint = FutaColors.BrandGreen,
                            onClick = { loadAllData() }
                        )
                    }

                    // Native Segmented Control
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            tabs.forEachIndexed { idx, label ->
                                val isSelected = selectedTab == idx
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedTab = idx }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) FutaColors.Navy else FutaColors.Slate
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FutaColors.PageBg)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Overview 4-Metric Strip (Web & iOS Parity)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetricItem(label = "Lượt tồn kho", value = "$totalUnused")
                        HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color(0xFFE2E8F0))
                        MetricItem(label = "Có lượt quay", value = "$usersWithSpinsCount")
                        HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color(0xFFE2E8F0))
                        MetricItem(label = "Đã quay", value = "$playedSpins")
                        HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color(0xFFE2E8F0))
                        MetricItem(label = "Cấp thủ công", value = "$manualGrantsCount")
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: Cài đặt & Giải thưởng
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("CẤU HÌNH CHIẾN DỊCH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Kích hoạt vòng quay", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                                    FutaSwitch(checked = isWheelEnabled, onCheckedChange = { isWheelEnabled = it })
                                }

                                FutaInput(value = campaignTitle, onValueChange = { campaignTitle = it }, placeholder = "Tiêu đề chiến dịch")
                                FutaInput(value = campaignDesc, onValueChange = { campaignDesc = it }, placeholder = "Mô tả hiển thị")
                                FutaInput(value = dailyLimit, onValueChange = { dailyLimit = it }, placeholder = "Giới hạn lượt quay / ngày")
                            }
                        }
                    }

                    // Danh sách giải thưởng
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("DANH SÁCH GIẢI THƯỞNG TRÊN VÒNG QUAY (${prizesList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                for (p in prizesList) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFEAF5EF),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(p["label"].string.ifEmpty { p["name"].string }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                                Text("Tỷ lệ: ${(p["probability"].double * 100).toInt()}% · Loại: ${if (p["fulfillmentType"].string == "physical") "Hiện vật" else "Voucher"}", fontSize = 11.5.sp, color = FutaColors.Slate)
                                            }
                                        }
                                        Surface(shape = CircleShape, color = if (p["enabled"].bool) Color(0xFFEAF5EF) else Color(0xFFFFF7ED)) {
                                            Text(
                                                if (p["enabled"].bool) "Bật" else "Tắt",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (p["enabled"].bool) FutaColors.BrandGreen else Color(0xFFF97316),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }

                                FutaButton(
                                    text = if (isSavingSettings) "Đang lưu..." else "Lưu cài đặt vòng quay",
                                    variant = FutaButtonVariant.PRIMARY,
                                    enabled = !isSavingSettings,
                                    onClick = {
                                        scope.launch {
                                            isSavingSettings = true
                                            try {
                                                val limit = dailyLimit.toIntOrNull() ?: 1
                                                val body = """
                                                    {
                                                        "enabled": $isWheelEnabled,
                                                        "title": "${campaignTitle.replace("\"", "\\\"")}",
                                                        "description": "${campaignDesc.replace("\"", "\\\"")}",
                                                        "dailySpinLimit": $limit
                                                    }
                                                """.trimIndent()
                                                APIClient.get().request("/lucky-wheel/admin/settings", method = "PUT", bodyJson = body)
                                                ToastCenter.show("Đã cập nhật cài đặt vòng quay thành công!")
                                                loadAllData()
                                            } catch (e: Exception) {
                                                ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                            } finally {
                                                isSavingSettings = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: Cấp lượt quay & VIP
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("TÌM KIẾM & CẤP LƯỢT THỦ CÔNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                FutaInput(
                                    value = grantSearchQuery,
                                    onValueChange = {
                                        grantSearchQuery = it
                                        if (it.trim().length >= 2) {
                                            scope.launch {
                                                try {
                                                    val res = APIClient.get().request("/lucky-wheel/admin/users/search", query = mapOf("q" to it.trim()))
                                                    searchResults = res["data"].array
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    placeholder = "Tìm theo tên, SĐT (ví dụ: 090...)",
                                    leadingIcon = Icons.Default.Search
                                )

                                if (searchResults.isNotEmpty()) {
                                    Text("Kết quả tìm kiếm (${searchResults.size})", fontSize = 11.5.sp, color = FutaColors.Slate)
                                    for (u in searchResults) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(u["name"].string.ifEmpty { "Khách hàng" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                                Text("${u["phone"].string.ifEmpty { u["phoneNumber"].string }} · Lượt: ${u["manualBonusSpins"].int}", fontSize = 11.5.sp, color = FutaColors.Slate)
                                            }
                                            FutaButton(
                                                text = "Chọn",
                                                variant = FutaButtonVariant.OUTLINE,
                                                onClick = {
                                                    selectedUserForGrant = u
                                                    searchResults = emptyList()
                                                }
                                            )
                                        }
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                    }
                                }

                                selectedUserForGrant?.let { u ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFEAF5EF), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Đang chọn: ${u["name"].string.ifEmpty { "Khách hàng" }} (${u["phone"].string.ifEmpty { u["phoneNumber"].string }})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                            FutaInput(value = grantSpinsCount, onValueChange = { grantSpinsCount = it }, placeholder = "Số lượt tặng (ví dụ: 5)")
                                            FutaInput(value = grantNote, onValueChange = { grantNote = it }, placeholder = "Ghi chú cấp lượt")
                                            FutaButton(
                                                text = if (isGranting) "Đang cấp..." else "Xác nhận cấp lượt ngay",
                                                variant = FutaButtonVariant.PRIMARY,
                                                enabled = !isGranting && grantSpinsCount.isNotBlank(),
                                                onClick = {
                                                    scope.launch {
                                                        isGranting = true
                                                        try {
                                                            val spins = grantSpinsCount.toIntOrNull() ?: 1
                                                            val body = """{"userId":"${u.id}","spins":$spins,"note":"${grantNote.replace("\"", "\\\"")}"}"""
                                                            APIClient.get().request("/lucky-wheel/admin/grant-spins", method = "POST", bodyJson = body)
                                                            ToastCenter.show("Đã cấp $spins lượt quay cho người dùng!")
                                                            selectedUserForGrant = null
                                                            grantNote = ""
                                                            loadAllData()
                                                        } catch (e: Exception) {
                                                            ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                                        } finally {
                                                            isGranting = false
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tài khoản có lượt quay tồn
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("TÀI KHOẢN CÒN LƯỢT QUAY TỒN (${usersWithSpins.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                if (usersWithSpins.isEmpty()) {
                                    Text("Không có tài khoản nào còn lượt tồn.", fontSize = 12.sp, color = FutaColors.Slate)
                                } else {
                                    for (u in usersWithSpins) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(u["name"].string.ifEmpty { "Khách hàng" }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                                Text("${u["phone"].string.ifEmpty { u["phoneNumber"].string }} · ${u["manualBonusSpins"].int} lượt tồn", fontSize = 11.5.sp, color = FutaColors.BrandGreen)
                                            }
                                            FutaButton(
                                                text = "+ Lượt",
                                                variant = FutaButtonVariant.OUTLINE,
                                                onClick = { selectedUserForGrant = u }
                                            )
                                        }
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // TAB 2: Đối soát trao quà
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("TRA CỨU & XÁC NHẬN TRAO THƯỞNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        FutaInput(
                                            value = rewardCodeQuery,
                                            onValueChange = { rewardCodeQuery = it.uppercase() },
                                            placeholder = "Nhập mã quà (FUTA-XXXX...)"
                                        )
                                    }
                                    FutaButton(
                                        text = if (isLookingUp) "Đang tìm..." else "Kiểm tra",
                                        variant = FutaButtonVariant.PRIMARY,
                                        enabled = !isLookingUp && rewardCodeQuery.isNotBlank(),
                                        onClick = {
                                            scope.launch {
                                                isLookingUp = true
                                                try {
                                                    val clean = rewardCodeQuery.trim()
                                                    val res = APIClient.get().request("/lucky-wheel/admin/rewards/$clean")
                                                    lookupSpinResult = res["data"]
                                                } catch (e: Exception) {
                                                    ToastCenter.show("Không tìm thấy mã quà tặng này", isError = true)
                                                    lookupSpinResult = null
                                                } finally {
                                                    isLookingUp = false
                                                }
                                            }
                                        }
                                    )
                                }

                                lookupSpinResult?.let { spin ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Giải thưởng: ${spin["prizeLabel"].string}", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                            Text("Người trúng: ${spin["user"]["name"].string.ifEmpty { "Khách hàng" }} (${spin["user"]["phone"].string.ifEmpty { spin["user"]["phoneNumber"].string }})", fontSize = 12.sp, color = FutaColors.Slate)
                                            Text("Trạng thái: ${spin["fulfillmentStatus"].string}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (spin["fulfillmentStatus"].string == "received") FutaColors.BrandGreen else Color(0xFFF97316))

                                            if (spin["fulfillmentStatus"].string != "received") {
                                                FutaButton(
                                                    text = if (isUpdatingFulfillment) "Đang cập nhật..." else "Xác nhận đã trao quà",
                                                    variant = FutaButtonVariant.PRIMARY,
                                                    enabled = !isUpdatingFulfillment,
                                                    onClick = {
                                                        scope.launch {
                                                            isUpdatingFulfillment = true
                                                            try {
                                                                val body = """{"fulfillmentStatus":"received","recipientVerified":true}"""
                                                                APIClient.get().request("/lucky-wheel/admin/spins/${spin.id}/fulfillment", method = "PATCH", bodyJson = body)
                                                                ToastCenter.show("Đã xác nhận trao quà thành công!")
                                                                lookupSpinResult = null
                                                                rewardCodeQuery = ""
                                                                loadAllData()
                                                            } catch (e: Exception) {
                                                                ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                                            } finally {
                                                                isUpdatingFulfillment = false
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Danh sách lượt trúng gần đây
                    val winningSpins = adminState["recentSpins"].array.filter { it["isWin"].bool }
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("LƯỢT TRÚNG THƯỞNG PHÁT SINH (${winningSpins.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                if (winningSpins.isEmpty()) {
                                    Text("Chưa có lượt trúng thưởng nào.", fontSize = 12.sp, color = FutaColors.Slate)
                                } else {
                                    for (spin in winningSpins) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(spin["prizeLabel"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                                val uName = spin["user"]["name"].string.ifEmpty { "Khách hàng" }
                                                val uPhone = spin["user"]["phone"].string.ifEmpty { spin["user"]["phoneNumber"].string }
                                                Text("$uName · $uPhone", fontSize = 11.5.sp, color = FutaColors.Slate)
                                                val rCode = spin["rewardCode"].string
                                                if (rCode.isNotEmpty()) {
                                                    Text("Mã: $rCode", fontSize = 10.5.sp, color = FutaColors.BrandGreen)
                                                }
                                            }
                                            val st = spin["fulfillmentStatus"].string
                                            Surface(shape = CircleShape, color = if (st == "received") Color(0xFFEAF5EF) else Color(0xFFFFF7ED)) {
                                                Text(
                                                    if (st == "received") "Đã trao" else "Chờ trao",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (st == "received") FutaColors.BrandGreen else Color(0xFFF97316),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                    }
                                }
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
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
        Text(label, fontSize = 10.5.sp, color = FutaColors.Slate)
    }
}
