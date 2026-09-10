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
fun AdminContractsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var contracts by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }

    var selectedContract by remember { mutableStateOf<JSONValue?>(null) }

    val statusOptions = listOf(
        "" to "Tất cả",
        "draft" to "Bản nháp",
        "deposited" to "Đã cọc",
        "signed" to "Đã ký",
        "active" to "Hiệu lực",
        "expired" to "Hết hạn",
        "cancelled" to "Đã hủy"
    )

    fun loadContracts(searchQuery: String = search) {
        scope.launch {
            loading = true
            try {
                val q = searchQuery.trim()
                var url = "/contracts?page=$page&limit=20"
                if (q.isNotEmpty()) {
                    url += "&search=${java.net.URLEncoder.encode(q, "UTF-8")}"
                }
                if (statusFilter.isNotEmpty()) {
                    url += "&status=$statusFilter"
                }
                val res = APIClient.get().request(url)
                contracts = res["data"].array
                totalPages = maxOf(1, res["pagination"]["totalPages"].int.takeIf { it > 0 } ?: 1)
            } catch (_: Exception) {
                contracts = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(search, statusFilter, page) {
        kotlinx.coroutines.delay(300)
        loadContracts(search)
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
                        text = "Hợp đồng giao dịch",
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
                    placeholder = "Tìm theo mã căn, khách hàng, SĐT...",
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

            // Status Filter Pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statusOptions.forEach { (stKey, stLabel) ->
                        val isSel = statusFilter == stKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable {
                                statusFilter = stKey
                                page = 1
                            }
                        ) {
                            Text(
                                text = stLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Contracts List
            if (loading) {
                items(5) {
                    FutaSkeletonBlock(height = 92.dp, radius = 14.dp)
                }
            } else if (contracts.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không có hợp đồng",
                        message = "Không tìm thấy hợp đồng giao dịch nào phù hợp với bộ lọc."
                    )
                }
            } else {
                itemsIndexed(contracts, key = { idx, item -> item.id.ifEmpty { "ct-$idx" } }) { _, contract ->
                    ContractCardRowItem(
                        contract = contract,
                        onClick = { selectedContract = contract }
                    )
                }

                if (totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trang $page / $totalPages", fontSize = 12.sp, color = FutaColors.Slate)
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

    // Detail BottomSheet
    selectedContract?.let { ct ->
        val code = ct["apartmentCode"].string.ifEmpty { "Hợp đồng BĐS" }
        val customerName = ct["customerName"].string.ifEmpty { "Khách hàng" }
        val customerPhone = ct["customerPhone"].string
        val status = ct["status"].string.ifEmpty { "draft" }
        val price = ct["price"].double
        val priceStr = if (price > 0) "%,.0f đ".format(price).replace(',', '.') else "Đang cập nhật"

        FutaBottomSheet(
            visible = true,
            onDismiss = { selectedContract = null },
            title = "Chi tiết hợp đồng $code"
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
                        Text("Căn hộ: $code", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Giá trị hợp đồng: $priceStr", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                        Text("Khách hàng: $customerName ($customerPhone)", fontSize = 12.5.sp, color = FutaColors.Slate)
                        Text("Trạng thái: $status", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                    }
                }

                FutaButton(
                    text = "Đóng",
                    variant = FutaButtonVariant.OUTLINE,
                    onClick = { selectedContract = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ContractCardRowItem(
    contract: JSONValue,
    onClick: () -> Unit
) {
    val code = contract["apartmentCode"].string.ifEmpty { "Căn hộ" }
    val customerName = contract["customerName"].string.ifEmpty { "Khách hàng" }
    val customerPhone = contract["customerPhone"].string
    val status = contract["status"].string.lowercase()
    val price = contract["price"].double
    val priceStr = if (price > 0) "%,.0f đ".format(price).replace(',', '.') else "0 đ"

    val (statusLabel, statusColor, statusBg) = when (status) {
        "signed" -> Triple("Đã ký", FutaColors.BrandGreen, Color(0xFFEAF5EF))
        "deposited" -> Triple("Đã cọc", Color(0xFFF97316), Color(0xFFFFF7ED))
        "active" -> Triple("Hiệu lực", Color(0xFF2563EB), Color(0xFFEFF6FF))
        "cancelled" -> Triple("Đã hủy", Color(0xFFEF4444), Color(0xFFFEF2F2))
        else -> Triple("Bản nháp", FutaColors.Slate, Color(0xFFF1F5F9))
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
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEFF6FF)
                ) {
                    Text(
                        text = code,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }

                Surface(shape = CircleShape, color = statusBg) {
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(customerName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    if (customerPhone.isNotEmpty()) {
                        Text(customerPhone, fontSize = 11.5.sp, color = FutaColors.Slate)
                    }
                }
                Text(priceStr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
            }
        }
    }
}
