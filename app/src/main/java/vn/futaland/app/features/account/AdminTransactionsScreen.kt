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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PropertyFormatters

@Composable
fun AdminTransactionsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var transactions by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var stats by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var loading by remember { mutableStateOf(true) }
    var period by remember { mutableStateOf("month") }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var typeFilter by remember { mutableStateOf("all") }
    var modeFilter by remember { mutableStateOf("all") }

    // Detail Sheet
    var selectedTransaction by remember { mutableStateOf<JSONValue?>(null) }
    var showApproveConfirm by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var actionBusy by remember { mutableStateOf(false) }

    val periods = listOf(
        "day" to "Hôm nay",
        "week" to "Tuần này",
        "month" to "Tháng này",
        "year" to "Năm nay",
        "all" to "Tất cả"
    )

    fun loadData(searchQuery: String = search) {
        scope.launch {
            loading = true
            try {
                val q = searchQuery.trim()
                var url = "/advisor/transactions?period=all"
                if (q.isNotEmpty()) {
                    url += "&search=${java.net.URLEncoder.encode(q, "UTF-8")}"
                }
                val res = APIClient.get().request(url)
                transactions = res["data"].array

                val statRes = APIClient.get().request("/advisor/transactions/revenue-stats?period=$period")
                stats = statRes["data"]
            } catch (_: Exception) {
                transactions = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(search) {
        kotlinx.coroutines.delay(300)
        loadData(search)
    }

    LaunchedEffect(period) {
        scope.launch {
            try {
                val statRes = APIClient.get().request("/advisor/transactions/revenue-stats?period=$period")
                stats = statRes["data"]
            } catch (_: Exception) {}
        }
    }

    val totalRevenueVal = stats["summary"]["totalRevenue"].double
    val periodRevenueVal = stats["summary"]["periodRevenue"].double
    val effectivePendingCount = stats["summary"]["pendingCount"].int

    val pendingCount = remember(transactions) {
        transactions.count { it["status"].string.lowercase() == "pending" }
    }
    val approvedCount = remember(transactions) {
        transactions.count { it["status"].string.lowercase() in listOf("approved", "completed") }
    }
    val rejectedCount = remember(transactions) {
        transactions.count { it["status"].string.lowercase() in listOf("rejected", "cancelled") }
    }

    val filteredTransactions = remember(transactions, search, statusFilter, typeFilter, modeFilter) {
        transactions.filter { tx ->
            val matchesStatus = statusFilter == "all" || tx["status"].string.lowercase() == statusFilter.lowercase()
            val matchesType = when (typeFilter) {
                "all" -> true
                "advisor_package", "package" -> tx["type"].string.contains("package") || tx["type"].string == "advisor_package"
                "pricing_order" -> tx["type"].string == "pricing_order"
                else -> tx["type"].string == typeFilter
            }
            val matchesMode = modeFilter == "all" || tx["mode"].string.lowercase() == modeFilter.lowercase()
            matchesStatus && matchesType && matchesMode
        }
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
                        text = "Quản lý giao dịch",
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
            // Pinned Search Bar (API Debounced)
            item {
                FutaInput(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Tìm theo mã GD, tên TVV, SĐT…",
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Xóa",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { search = "" },
                                tint = FutaColors.Slate
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Period Selector Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    periods.forEach { (pKey, pLabel) ->
                        val isSelected = period == pKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { period = pKey }
                        ) {
                            Text(
                                text = pLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Revenue Metrics 2x2 Grid matching iOS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TransactionMetricCard(
                            title = "Tổng doanh thu",
                            value = formatCompactCurrency(totalRevenueVal),
                            icon = Icons.Default.MonetizationOn,
                            iconColor = FutaColors.BrandGreen,
                            iconBg = Color(0xFFEAF5EF),
                            modifier = Modifier.weight(1f)
                        )
                        TransactionMetricCard(
                            title = "Doanh thu trong kỳ",
                            value = formatCompactCurrency(periodRevenueVal),
                            icon = Icons.Default.DateRange,
                            iconColor = FutaColors.BrandGreen,
                            iconBg = Color(0xFFEAF5EF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TransactionMetricCard(
                            title = "Tổng giao dịch",
                            value = "${transactions.size}",
                            icon = Icons.AutoMirrored.Filled.CompareArrows,
                            iconColor = Color(0xFF2563EB),
                            iconBg = Color(0xFFEFF6FF),
                            modifier = Modifier.weight(1f)
                        )
                        TransactionMetricCard(
                            title = "Chờ duyệt",
                            value = "$effectivePendingCount",
                            icon = Icons.Default.HourglassEmpty,
                            iconColor = Color(0xFFF97316),
                            iconBg = Color(0xFFFFF7ED),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TransactionMetricCard(
                            title = "Đã duyệt",
                            value = "$approvedCount",
                            icon = Icons.Default.CheckCircle,
                            iconColor = Color(0xFF0D9488),
                            iconBg = Color(0xFFCCFBF1),
                            modifier = Modifier.weight(1f)
                        )
                        TransactionMetricCard(
                            title = "Từ chối / Hủy",
                            value = "$rejectedCount",
                            icon = Icons.Default.Cancel,
                            iconColor = Color(0xFFEF4444),
                            iconBg = Color(0xFFFEF2F2),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Status Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransactionFilterPill("Tất cả (${transactions.size})", "all", statusFilter == "all") { statusFilter = "all" }
                    TransactionFilterPill("Chờ duyệt ($pendingCount)", "pending", statusFilter == "pending") { statusFilter = "pending" }
                    TransactionFilterPill("Đã duyệt ($approvedCount)", "approved", statusFilter == "approved") { statusFilter = "approved" }
                    TransactionFilterPill("Từ chối ($rejectedCount)", "rejected", statusFilter == "rejected") { statusFilter = "rejected" }
                }
            }

            // Sub-filter row: Type & Mode
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Type Filter Menu
                    FilterDropdownButton(
                        title = when (typeFilter) {
                            "advisor_package" -> "Gói TVV"
                            "pricing_order" -> "Gói tin"
                            "deposit" -> "Đặt cọc"
                            "commission" -> "Hoa hồng"
                            else -> "Tất cả loại"
                        },
                        options = listOf(
                            "all" to "Tất cả loại",
                            "advisor_package" to "Gói TVV",
                            "pricing_order" to "Gói tin",
                            "deposit" to "Đặt cọc",
                            "commission" to "Hoa hồng"
                        ),
                        selectedKey = typeFilter,
                        onSelect = { typeFilter = it },
                        modifier = Modifier.weight(1f)
                    )

                    // Mode Filter Menu
                    FilterDropdownButton(
                        title = when (modeFilter) {
                            "auto" -> "Tự động (SePay)"
                            "manual" -> "Thủ công"
                            else -> "Tất cả chế độ"
                        },
                        options = listOf(
                            "all" to "Tất cả chế độ",
                            "auto" to "Tự động (SePay)",
                            "manual" to "Thủ công"
                        ),
                        selectedKey = modeFilter,
                        onSelect = { modeFilter = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Transactions List with Skeleton Loading
            if (loading) {
                items(3) {
                    FutaSkeletonBlock(height = 110.dp, radius = 16.dp)
                }
            } else if (filteredTransactions.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không có giao dịch",
                        message = "Không tìm thấy giao dịch nào phù hợp với điều kiện tìm kiếm hoặc bộ lọc."
                    )
                }
            } else {
                itemsIndexed(filteredTransactions, key = { idx, item -> (item.id.ifEmpty { "tx" }) + "-$idx" }) { _, tx ->
                    TransactionCardRow(
                        transaction = tx,
                        onClick = { selectedTransaction = tx }
                    )
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Detail & Actions BottomSheet matching iOS
    selectedTransaction?.let { tx ->
        val status = tx["status"].string
        val isPending = status.lowercase() == "pending"
        val code = tx["code"].string.ifEmpty { "GD-${tx.id.take(8)}" }
        val userName = tx["user"]["name"].string.ifEmpty { "Khách hàng" }
        val userPhone = tx["user"]["phone"].string
        val amount = tx["amount"].double
        val amountStr = if (amount > 0) "%,.0f đ".format(amount).replace(',', '.') else "0 đ"

        FutaBottomSheet(
            visible = true,
            onDismiss = { selectedTransaction = null },
            title = "Chi tiết giao dịch $code"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Số tiền: $amountStr", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                        Text("Người thực hiện: $userName", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (userPhone.isNotEmpty()) {
                            Text("SĐT: $userPhone", fontSize = 12.5.sp, color = FutaColors.Slate)
                        }
                        Text("Nội dung: ${tx["description"].string.ifEmpty { tx["packageName"].string }}", fontSize = 12.5.sp, color = FutaColors.Slate)
                        Text("Cú pháp CK: ${tx["transferSyntax"].string.ifEmpty { tx["code"].string }}", fontSize = 12.sp, color = Color(0xFFF97316), fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isPending) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FutaButton(
                            text = "Từ chối",
                            variant = FutaButtonVariant.OUTLINE,
                            onClick = { showRejectDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                        FutaButton(
                            text = "Duyệt giao dịch",
                            variant = FutaButtonVariant.PRIMARY,
                            onClick = { showApproveConfirm = true },
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                } else {
                    FutaButton(
                        text = "Đóng",
                        variant = FutaButtonVariant.OUTLINE,
                        onClick = { selectedTransaction = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showApproveConfirm && selectedTransaction != null) {
        FutaDialog(
            visible = true,
            title = "Duyệt giao dịch?",
            confirmText = "Duyệt ngay",
            onConfirm = {
                scope.launch {
                    actionBusy = true
                    try {
                        val txId = selectedTransaction!!.id
                        APIClient.get().request("/advisor/transactions/$txId/approve", method = "POST")
                        ToastCenter.show("Đã duyệt giao dịch thành công!")
                        showApproveConfirm = false
                        selectedTransaction = null
                        loadData(search)
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    } finally {
                        actionBusy = false
                    }
                }
            },
            onDismiss = { showApproveConfirm = false }
        ) {
            Text("Bạn có chắc chắn muốn xác nhận duyệt đơn thanh toán này không?", fontSize = 13.5.sp, color = FutaColors.Slate)
        }
    }

    if (showRejectDialog && selectedTransaction != null) {
        FutaDialog(
            visible = true,
            title = "Từ chối giao dịch",
            confirmText = "Xác nhận từ chối",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                scope.launch {
                    try {
                        val txId = selectedTransaction!!.id
                        APIClient.get().request(
                            "/advisor/transactions/$txId/reject",
                            method = "POST",
                            bodyJson = "{\"reason\":\"$rejectReason\"}"
                        )
                        ToastCenter.show("Đã từ chối giao dịch!")
                        showRejectDialog = false
                        selectedTransaction = null
                        loadData(search)
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { showRejectDialog = false }
        ) {
            FutaInput(
                value = rejectReason,
                onValueChange = { rejectReason = it },
                placeholder = "Nhập lý do từ chối..."
            )
        }
    }
}

@Composable
private fun TransactionMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBg,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = FutaColors.Navy
                )
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    color = FutaColors.Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TransactionFilterPill(
    title: String,
    id: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) FutaColors.BrandGreen else Color.White,
        border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else FutaColors.Navy,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FilterDropdownButton(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = FutaColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = FutaColors.Slate)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (key == selectedKey) FontWeight.Bold else FontWeight.Normal,
                            color = if (key == selectedKey) FutaColors.BrandGreen else FutaColors.Navy
                        )
                    },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionCardRow(
    transaction: JSONValue,
    onClick: () -> Unit
) {
    val code = transaction["code"].string.ifEmpty { "GD-${transaction.id.take(8)}" }
    val isAuto = transaction["mode"].string.lowercase() == "auto"
    val status = transaction["status"].string.lowercase()
    val (statusLabel, statusColor, statusBg) = when (status) {
        "approved", "completed" -> Triple("Đã duyệt", FutaColors.BrandGreen, Color(0xFFEAF5EF))
        "pending" -> Triple("Chờ duyệt", Color(0xFFF97316), Color(0xFFFFF7ED))
        "rejected", "cancelled" -> Triple("Từ chối", Color(0xFFEF4444), Color(0xFFFEF2F2))
        else -> Triple(status.ifEmpty { "Chưa rõ" }, FutaColors.Slate, Color(0xFFF1F5F9))
    }
    val userName = transaction["user"]["name"].string.ifEmpty { "Khách hàng" }
    val userPhone = transaction["user"]["phone"].string
    val amount = transaction["amount"].double
    val amountStr = if (amount > 0) "%,.0f đ".format(amount).replace(',', '.') else "0 đ"
    val bankOrMethod = transaction["bankName"].string.ifEmpty { transaction["paymentMethod"].string }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEAF5EF)
                    ) {
                        Text(
                            text = code,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isAuto) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEFF6FF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(10.dp))
                                Text("Tự động", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = userName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )
                    if (userPhone.isNotEmpty()) {
                        Text(
                            text = userPhone,
                            fontSize = 12.sp,
                            color = FutaColors.Slate
                        )
                    }
                }

                Text(
                    text = amountStr,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.BrandGreen
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (bankOrMethod.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = FutaColors.Slate, modifier = Modifier.size(13.dp))
                        Text(bankOrMethod, fontSize = 11.5.sp, color = FutaColors.Slate)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                val rawCreated = transaction["createdAt"].string
                val createdDisplay = if (rawCreated.length >= 16) {
                    rawCreated.substring(0, 10) + " " + rawCreated.substring(11, 16)
                } else rawCreated
                Text(
                    text = createdDisplay,
                    fontSize = 11.sp,
                    color = FutaColors.Slate
                )
            }
        }
    }
}

private fun formatCompactCurrency(value: Double): String {
    return when {
        value >= 1_000_000_000 -> "%.1f tỷ".format(value / 1_000_000_000.0)
        value >= 1_000_000 -> "%.1f tr".format(value / 1_000_000.0)
        value > 0 -> "%,.0f đ".format(value).replace(',', '.')
        else -> "0 đ"
    }
}
