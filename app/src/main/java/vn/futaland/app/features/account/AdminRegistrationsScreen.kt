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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PropertyFormatters

enum class RegistrationTab(val label: String) {
    HOLDING("Giữ chỗ trực tuyến"),
    RIGHTS("Quyền bán TVV")
}

@Composable
fun AdminRegistrationsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var registrations by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(RegistrationTab.HOLDING) }
    var holdingFilter by remember { mutableStateOf("all") }
    var rightsFilter by remember { mutableStateOf("all") }
    var projectFilter by remember { mutableStateOf("all") }
    var onlyCompeting by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Detail & Action BottomSheet
    var selectedRegistration by remember { mutableStateOf<JSONValue?>(null) }
    var actionBusy by remember { mutableStateOf(false) }

    fun loadRegistrations(searchQuery: String = search) {
        scope.launch {
            loading = true
            try {
                val q = searchQuery.trim()
                val path = if (q.isNotEmpty()) "/sales/registrations?search=${java.net.URLEncoder.encode(q, "UTF-8")}" else "/sales/registrations"
                val res = APIClient.get().request(path)
                registrations = res["data"].array
            } catch (_: Exception) {
                registrations = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(search) {
        kotlinx.coroutines.delay(300)
        loadRegistrations(search)
    }

    // Competing unit codes calculation (units with > 1 registration)
    val competingUnitCodes = remember(registrations) {
        val counts = mutableMapOf<String, Int>()
        for (r in registrations) {
            val code = r["apartment"]["propertyCode"].string.trim()
            if (code.isNotEmpty()) {
                counts[code] = (counts[code] ?: 0) + 1
            }
        }
        counts.filter { it.value > 1 }.keys.toSet()
    }

    val onlineHoldingCount = remember(registrations) {
        registrations.count { it["bookingStatus"].string.lowercase() == "online_holding" }
    }
    val pendingDepositCount = remember(registrations) {
        registrations.count { it["bookingStatus"].string.lowercase() in listOf("pending_booking", "pending") }
    }
    val activeRightsCount = remember(registrations) {
        registrations.count { it["status"].string.lowercase() == "active" }
    }

    val availableProjects = remember(registrations) {
        val set = registrations.map {
            it["apartment"]["project"]["displayName"].string.ifEmpty { it["apartment"]["zone"].string }
        }.filter { it.isNotEmpty() }.toSet()
        listOf("all") + set.toList().sorted()
    }

    val filteredRegistrations = remember(registrations, search, activeTab, holdingFilter, rightsFilter, projectFilter, onlyCompeting, competingUnitCodes) {
        registrations.filter { r ->
            val code = r["apartment"]["propertyCode"].string
            val advisorName = r["advisor"]["name"].string
            val customerName = r["customerName"].string
            val customerPhone = r["customerPhone"].string
            val projName = r["apartment"]["project"]["displayName"].string.ifEmpty { r["apartment"]["zone"].string }

            val matchSearch = if (search.trim().isEmpty()) true else {
                val query = search.trim().lowercase()
                listOf(code, advisorName, customerName, customerPhone, projName).any { it.lowercase().contains(query) }
            }

            val matchProject = projectFilter == "all" || projName == projectFilter
            val matchCompeting = !onlyCompeting || competingUnitCodes.contains(code)

            val matchTab = if (activeTab == RegistrationTab.HOLDING) {
                val bookingStatus = r["bookingStatus"].string.ifEmpty { "none" }.lowercase()
                holdingFilter == "all" || bookingStatus == holdingFilter.lowercase()
            } else {
                val rightsStatus = r["status"].string.ifEmpty { "pending" }.lowercase()
                rightsFilter == "all" || rightsStatus == rightsFilter.lowercase()
            }

            matchSearch && matchProject && matchCompeting && matchTab
        }.sortedByDescending {
            val t = it["holdingSubmittedAt"].string.ifEmpty { it["createdAt"].string }
            t
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
                        text = "Duyệt bán & Giữ chỗ",
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
                    onValueChange = { search = it },
                    placeholder = "Tìm theo mã căn, TVV, khách hàng, SĐT…",
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

            // 2x2 Metrics Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RegistrationsMetricCard(
                            title = "Tổng hồ sơ",
                            value = "${registrations.size}",
                            icon = Icons.Default.Description,
                            iconColor = FutaColors.BrandGreen,
                            iconBg = Color(0xFFEAF5EF),
                            modifier = Modifier.weight(1f)
                        )
                        RegistrationsMetricCard(
                            title = "Giữ chỗ online",
                            value = "$onlineHoldingCount",
                            icon = Icons.Default.Shield,
                            iconColor = Color(0xFF2563EB),
                            iconBg = Color(0xFFEFF6FF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RegistrationsMetricCard(
                            title = "Chờ duyệt cọc",
                            value = "$pendingDepositCount",
                            icon = Icons.Default.HourglassEmpty,
                            iconColor = Color(0xFFF97316),
                            iconBg = Color(0xFFFFF7ED),
                            modifier = Modifier.weight(1f)
                        )
                        RegistrationsMetricCard(
                            title = "Căn cạnh tranh",
                            value = "${competingUnitCodes.size}",
                            icon = Icons.Default.LocalFireDepartment,
                            iconColor = Color(0xFFEF4444),
                            iconBg = Color(0xFFFEF2F2),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Main Segmented Tab Selector
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RegistrationTab.values().forEach { tab ->
                            val isSelected = activeTab == tab
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = if (isSelected) 1.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeTab = tab }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) FutaColors.Navy else FutaColors.Slate
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sub-filter chips based on active tab
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeTab == RegistrationTab.HOLDING) {
                        RegistrationFilterChip("Tất cả (${registrations.size})", "all", holdingFilter == "all") { holdingFilter = "all" }
                        RegistrationFilterChip("Giữ chỗ online ($onlineHoldingCount)", "online_holding", holdingFilter == "online_holding") { holdingFilter = "online_holding" }
                        RegistrationFilterChip("Chờ duyệt cọc ($pendingDepositCount)", "pending_booking", holdingFilter == "pending_booking") { holdingFilter = "pending_booking" }
                        RegistrationFilterChip("Giữ chỗ thành công", "holding_success", holdingFilter == "holding_success") { holdingFilter = "holding_success" }
                        RegistrationFilterChip("Đã nộp cọc", "deposited", holdingFilter == "deposited") { holdingFilter = "deposited" }
                        RegistrationFilterChip("Đã chi hoa hồng", "commission_paid", holdingFilter == "commission_paid") { holdingFilter = "commission_paid" }
                        RegistrationFilterChip("Đã hủy", "cancelled", holdingFilter == "cancelled") { holdingFilter = "cancelled" }
                    } else {
                        RegistrationFilterChip("Tất cả (${registrations.size})", "all", rightsFilter == "all") { rightsFilter = "all" }
                        RegistrationFilterChip("Đang có quyền bán ($activeRightsCount)", "active", rightsFilter == "active") { rightsFilter = "active" }
                        RegistrationFilterChip("Chờ duyệt", "pending", rightsFilter == "pending") { rightsFilter = "pending" }
                        RegistrationFilterChip("Đã từ chối", "rejected", rightsFilter == "rejected") { rightsFilter = "rejected" }
                        RegistrationFilterChip("Đã thu hồi", "revoked", rightsFilter == "revoked") { rightsFilter = "revoked" }
                    }
                }
            }

            // Competing switch row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Căn tranh chấp (${competingUnitCodes.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (onlyCompeting) Color(0xFFEF4444) else FutaColors.Slate
                    )
                    FutaSwitch(
                        checked = onlyCompeting,
                        onCheckedChange = { onlyCompeting = it },
                        activeColor = Color(0xFFEF4444)
                    )
                }
            }

            // List of registrations
            if (loading) {
                items(4) {
                    FutaRegistrationCardSkeleton()
                }
            } else if (filteredRegistrations.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không có hồ sơ nào",
                        message = "Không tìm thấy hồ sơ đăng ký bán hoặc giữ chỗ nào phù hợp với bộ lọc."
                    )
                }
            } else {
                itemsIndexed(filteredRegistrations, key = { idx, item -> (item.id.ifEmpty { "reg" }) + "-$idx" }) { _, reg ->
                    val propertyCode = reg["apartment"]["propertyCode"].string
                    val isCompeting = competingUnitCodes.contains(propertyCode)

                    RegistrationCardRow(
                        registration = reg,
                        isCompeting = isCompeting,
                        onClick = { selectedRegistration = reg }
                    )
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Detail & Action BottomSheet
    selectedRegistration?.let { reg ->
        val code = reg["apartment"]["propertyCode"].string.ifEmpty { "Căn hộ" }
        val bookingSt = reg["bookingStatus"].string
        val rightsSt = reg["status"].string
        FutaBottomSheet(
            visible = true,
            onDismiss = { selectedRegistration = null },
            title = "Hồ sơ căn $code"
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
                        Text("Mã đăng ký: ${reg["code"].string}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Tư vấn viên: ${reg["advisor"]["name"].string} (${reg["advisor"]["phone"].string})", fontSize = 12.5.sp, color = FutaColors.Slate)
                        if (reg["customerName"].string.isNotEmpty()) {
                            Text("Khách hàng: ${reg["customerName"].string} (${reg["customerPhone"].string})", fontSize = 12.5.sp, color = FutaColors.Slate)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FutaButton(
                        text = "Đóng",
                        variant = FutaButtonVariant.OUTLINE,
                        onClick = { selectedRegistration = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Project Filter BottomSheet
    if (showFilterSheet) {
        FutaBottomSheet(
            visible = true,
            onDismiss = { showFilterSheet = false },
            title = "Bộ lọc hồ sơ"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dự án bất động sản", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableProjects.forEach { proj ->
                            val isSel = projectFilter == proj
                            val label = if (proj == "all") "Tất cả dự án" else proj
                            Surface(
                                shape = CircleShape,
                                color = if (isSel) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { projectFilter = proj }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else FutaColors.Navy,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FutaButton(
                        text = "Đặt lại",
                        variant = FutaButtonVariant.OUTLINE,
                        onClick = {
                            projectFilter = "all"
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FutaButton(
                        text = "Áp dụng (${filteredRegistrations.size})",
                        variant = FutaButtonVariant.PRIMARY,
                        onClick = { showFilterSheet = false },
                        modifier = Modifier.weight(1.5f)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RegistrationsMetricCard(
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = FutaColors.Navy
                )
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = FutaColors.Slate
                )
            }
        }
    }
}

@Composable
private fun RegistrationFilterChip(
    title: String,
    id: String,
    isSelected: Bool,
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

typealias Bool = Boolean

@Composable
private fun RegistrationCardRow(
    registration: JSONValue,
    isCompeting: Boolean,
    onClick: () -> Unit
) {
    val apt = registration["apartment"]
    val advisor = registration["advisor"]
    val bookingStatus = registration["bookingStatus"].string
    val rightsStatus = registration["status"].string

    val propertyCode = apt["propertyCode"].string.ifEmpty { "Căn hộ" }
    val projName = apt["project"]["displayName"].string.ifEmpty { apt["zone"].string.ifEmpty { "Dự án FUTA" } }
    val building = apt["building"].string.ifEmpty { "-" }
    val floor = apt["floor"].string.ifEmpty { "-" }
    val subLocation = "$projName · Tòa $building · Tầng $floor"

    val advisorName = advisor["name"].string.ifEmpty { "FUTA Land" }
    val advisorPhone = advisor["phone"].string
    val customerName = registration["customerName"].string
    val customerPhone = registration["customerPhone"].string

    val priceVal = if (apt["sellPrice"].double > 0) apt["sellPrice"].double else apt["price"].double
    val priceFormatted = if (priceVal > 0) "%,.0f đ".format(priceVal).replace(',', '.') else "Đang cập nhật"
    val deposit = registration["depositAmount"].double

    val (bookingTitle, bookingColor, bookingBg) = when (bookingStatus.lowercase()) {
        "online_holding" -> Triple("Giữ chỗ online", Color(0xFF2563EB), Color(0xFFEFF6FF))
        "pending_booking", "pending" -> Triple("Chờ duyệt cọc", Color(0xFFF97316), Color(0xFFFFF7ED))
        "holding_success" -> Triple("Giữ chỗ thành công", Color(0xFF7C3AED), Color(0xFFF5F3FF))
        "deposited" -> Triple("Đã nộp cọc", FutaColors.BrandGreen, Color(0xFFEAF5EF))
        "commission_paid" -> Triple("Đã chi hoa hồng", Color(0xFF4F46E5), Color(0xFFEEF2FF))
        "cancelled" -> Triple("Đã hủy", Color(0xFFEF4444), Color(0xFFFEF2F2))
        else -> Triple(bookingStatus, FutaColors.Slate, Color(0xFFF1F5F9))
    }

    val (rightsTitle, rightsColor, rightsBg) = when (rightsStatus.lowercase()) {
        "active" -> Triple("Đang có quyền bán", FutaColors.BrandGreen, Color(0xFFEAF5EF))
        "pending" -> Triple("Chờ duyệt", Color(0xFFF97316), Color(0xFFFFF7ED))
        "rejected" -> Triple("Đã từ chối", Color(0xFFEF4444), Color(0xFFFEF2F2))
        "revoked" -> Triple("Đã thu hồi", FutaColors.Slate, Color(0xFFF1F5F9))
        else -> Triple(rightsStatus.ifEmpty { "Chưa duyệt" }, FutaColors.Slate, Color(0xFFF1F5F9))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = if (isCompeting) 1.5.dp else 1.dp,
            color = if (isCompeting) Color(0xFFFCA5A5) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Property Code & Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = propertyCode,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        if (isCompeting) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFEE2E2)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(11.dp))
                                    Text("Tranh chấp", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                    Text(
                        text = subLocation,
                        fontSize = 12.sp,
                        color = FutaColors.Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (bookingStatus.isNotEmpty() && bookingStatus != "none") {
                        Surface(shape = CircleShape, color = bookingBg) {
                            Text(
                                text = bookingTitle,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = bookingColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(shape = CircleShape, color = rightsBg) {
                        Text(
                            text = rightsTitle,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = rightsColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Advisor & Customer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Advisor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = FutaColors.BrandGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("TVV: $advisorName", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (advisorPhone.isNotEmpty()) {
                            Text(advisorPhone, fontSize = 11.5.sp, color = FutaColors.Slate)
                        }
                    }
                }

                // Customer
                if (customerName.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(customerName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            if (customerPhone.isNotEmpty()) {
                                Text(customerPhone, fontSize = 11.5.sp, color = FutaColors.Slate)
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom Row: Price & Created Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = priceFormatted,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.BrandGreen
                    )
                    if (deposit > 0) {
                        val depFormatted = "%,.0f đ".format(deposit).replace(',', '.')
                        Text(
                            text = "· Cọc: $depFormatted",
                            fontSize = 11.5.sp,
                            color = Color(0xFFF97316)
                        )
                    }
                }

                val rawCreated = registration["createdAt"].string
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
