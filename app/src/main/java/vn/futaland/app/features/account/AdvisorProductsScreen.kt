package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PropertyFormatters
import vn.futaland.app.navigation.FutaDestinations

@Composable
fun AdvisorProductsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("registered") }

    // Product Filter States
    var projectFilter by remember { mutableStateOf("") }
    var campaignFilter by remember { mutableStateOf("") }
    var blockFilter by remember { mutableStateOf("") }
    var productTypeFilter by remember { mutableStateOf("") }
    var propertyTypeFilter by remember { mutableStateOf("") }
    var floorFilter by remember { mutableStateOf("") }
    var directionFilter by remember { mutableStateOf("") }
    var balconyDirectionFilter by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }

    val activeFilterCount = listOf(
        projectFilter,
        campaignFilter,
        blockFilter,
        productTypeFilter,
        propertyTypeFilter,
        floorFilter,
        directionFilter,
        balconyDirectionFilter
    ).count { it.isNotEmpty() }

    val tabs = listOf(
        "registered" to "Đã đăng ký bán",
        "online_holding" to "Đang giữ chỗ",
        "pending" to "Chờ xác nhận",
        "holding" to "ERP đã khóa căn",
        "completed" to "GD thành công"
    )

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/sales/registrations")
                items = res["data"].array
            } catch (_: Exception) {
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    // Web parity: Only show approved registrations with active sales permission in the basket
    val activeRegistrations = remember(items) {
        items.filter { reg ->
            val status = reg["status"].string.lowercase()
            status == "active" || status == "approved"
        }
    }

    val tabCounts = remember(activeRegistrations) {
        val counts = mutableMapOf(
            "registered" to 0,
            "online_holding" to 0,
            "pending" to 0,
            "holding" to 0,
            "completed" to 0
        )
        for (reg in activeRegistrations) {
            val bStatus = reg["bookingStatus"].string.lowercase()
            when {
                bStatus == "online_holding" -> counts["online_holding"] = (counts["online_holding"] ?: 0) + 1
                bStatus == "pending_booking" || bStatus == "cancel_requested" -> counts["pending"] = (counts["pending"] ?: 0) + 1
                bStatus == "holding_success" -> counts["holding"] = (counts["holding"] ?: 0) + 1
                listOf("deposited", "commission_pending", "commission_paid", "purchased").contains(bStatus) -> counts["completed"] = (counts["completed"] ?: 0) + 1
                else -> counts["registered"] = (counts["registered"] ?: 0) + 1
            }
        }
        counts
    }

    fun getRegProject(reg: JSONValue) = reg["projectName"].string.ifEmpty { reg["apartment"]["zone"].string.ifEmpty { reg["zone"].string } }
    fun getRegCampaign(reg: JSONValue) = reg["campaignName"].string.ifEmpty { reg["salesCampaignName"].string.ifEmpty { reg["apartment"]["salesCampaignName"].string } }
    fun getRegBlock(reg: JSONValue) = reg["block"].string.ifEmpty { reg["apartment"]["building"].string.ifEmpty { reg["building"].string } }
    fun getRegProductType(reg: JSONValue) = reg["apartmentType"].string.ifEmpty { reg["apartment"]["apartmentType"].string }
    fun getRegPropertyType(reg: JSONValue) = reg["propertyType"].string.ifEmpty { reg["apartment"]["propertyType"].string }
    fun getRegFloor(reg: JSONValue): String {
        val f = reg["floor"].string
        if (f.isNotEmpty()) return f
        val fl = reg["apartment"]["floor"].int
        return if (fl > 0) "$fl" else ""
    }
    fun getRegDirection(reg: JSONValue) = reg["direction"].string.ifEmpty { reg["apartment"]["direction"].string }
    fun getRegBalconyDirection(reg: JSONValue) = reg["balconyDirection"].string.ifEmpty { reg["apartment"]["balconyDirection"].string }

    fun filterRegistrations(exclude: String): List<JSONValue> {
        return activeRegistrations.filter { reg ->
            (exclude == "project" || projectFilter.isEmpty() || getRegProject(reg) == projectFilter) &&
            (exclude == "campaign" || campaignFilter.isEmpty() || getRegCampaign(reg) == campaignFilter) &&
            (exclude == "block" || blockFilter.isEmpty() || getRegBlock(reg) == blockFilter) &&
            (exclude == "productType" || productTypeFilter.isEmpty() || getRegProductType(reg) == productTypeFilter) &&
            (exclude == "propertyType" || propertyTypeFilter.isEmpty() || getRegPropertyType(reg) == propertyTypeFilter) &&
            (exclude == "floor" || floorFilter.isEmpty() || getRegFloor(reg) == floorFilter) &&
            (exclude == "direction" || directionFilter.isEmpty() || getRegDirection(reg) == directionFilter) &&
            (exclude == "balconyDirection" || balconyDirectionFilter.isEmpty() || getRegBalconyDirection(reg) == balconyDirectionFilter)
        }
    }

    val projectOptions = remember(activeRegistrations, campaignFilter, blockFilter, productTypeFilter, propertyTypeFilter, floorFilter, directionFilter, balconyDirectionFilter) {
        filterRegistrations("project").mapNotNull {
            val p = getRegProject(it)
            if (p.isNotEmpty() && p != "Dự án chưa cập nhật") p else null
        }.distinct().sorted()
    }
    val campaignOptions = remember(activeRegistrations, projectFilter, blockFilter, productTypeFilter, propertyTypeFilter, floorFilter, directionFilter, balconyDirectionFilter) {
        filterRegistrations("campaign").mapNotNull {
            val c = getRegCampaign(it)
            if (c.isNotEmpty()) c else null
        }.distinct().sorted()
    }
    val blockOptions = remember(activeRegistrations, projectFilter, campaignFilter, productTypeFilter, propertyTypeFilter, floorFilter, directionFilter, balconyDirectionFilter) {
        filterRegistrations("block").mapNotNull {
            val b = getRegBlock(it)
            if (b.isNotEmpty() && b != "-") b else null
        }.distinct().sorted()
    }
    val productTypeOptions = remember(activeRegistrations, projectFilter, campaignFilter, blockFilter, propertyTypeFilter, floorFilter, directionFilter, balconyDirectionFilter) {
        val list = filterRegistrations("productType").mapNotNull {
            val t = getRegProductType(it)
            if (t.isNotEmpty()) t else null
        } + listOf("Căn hộ", "Shophouse", "Penthouse", "Duplex", "Villa")
        list.distinct().sorted()
    }
    val propertyTypeOptions = remember(activeRegistrations, projectFilter, campaignFilter, blockFilter, productTypeFilter, floorFilter, directionFilter, balconyDirectionFilter) {
        filterRegistrations("propertyType").mapNotNull {
            val t = getRegPropertyType(it)
            if (t.isNotEmpty()) t else null
        }.distinct().sorted()
    }
    val floorOptions = remember(activeRegistrations, projectFilter, campaignFilter, blockFilter, productTypeFilter, propertyTypeFilter, directionFilter, balconyDirectionFilter) {
        filterRegistrations("floor").mapNotNull {
            val f = getRegFloor(it)
            if (f.isNotEmpty() && f != "-") f else null
        }.distinct().sortedBy { it.toIntOrNull() ?: 0 }
    }
    val directionOptions = remember(activeRegistrations, projectFilter, campaignFilter, blockFilter, productTypeFilter, propertyTypeFilter, floorFilter, balconyDirectionFilter) {
        val list = filterRegistrations("direction").mapNotNull {
            val d = getRegDirection(it)
            if (d.isNotEmpty()) d else null
        } + listOf("Đông", "Tây", "Nam", "Bắc", "Đông Nam", "Đông Bắc", "Tây Nam", "Tây Bắc")
        list.distinct().sorted()
    }
    val balconyDirectionOptions = remember(activeRegistrations, projectFilter, campaignFilter, blockFilter, productTypeFilter, propertyTypeFilter, floorFilter, directionFilter) {
        val list = filterRegistrations("balconyDirection").mapNotNull {
            val d = getRegBalconyDirection(it)
            if (d.isNotEmpty()) d else null
        } + listOf("Đông", "Tây", "Nam", "Bắc", "Đông Nam", "Đông Bắc", "Tây Nam", "Tây Bắc")
        list.distinct().sorted()
    }

    LaunchedEffect(projectOptions, campaignOptions, blockOptions, productTypeOptions, propertyTypeOptions, floorOptions, directionOptions, balconyDirectionOptions) {
        if (projectFilter.isNotEmpty() && !projectOptions.contains(projectFilter)) projectFilter = ""
        if (campaignFilter.isNotEmpty() && !campaignOptions.contains(campaignFilter)) campaignFilter = ""
        if (blockFilter.isNotEmpty() && !blockOptions.contains(blockFilter)) blockFilter = ""
        if (productTypeFilter.isNotEmpty() && !productTypeOptions.contains(productTypeFilter)) productTypeFilter = ""
        if (propertyTypeFilter.isNotEmpty() && !propertyTypeOptions.contains(propertyTypeFilter)) propertyTypeFilter = ""
        if (floorFilter.isNotEmpty() && !floorOptions.contains(floorFilter)) floorFilter = ""
        if (directionFilter.isNotEmpty() && !directionOptions.contains(directionFilter)) directionFilter = ""
        if (balconyDirectionFilter.isNotEmpty() && !balconyDirectionOptions.contains(balconyDirectionFilter)) balconyDirectionFilter = ""
    }

    val filteredItems = remember(
        activeRegistrations, search, selectedTab,
        projectFilter, campaignFilter, blockFilter,
        productTypeFilter, propertyTypeFilter, floorFilter,
        directionFilter, balconyDirectionFilter
    ) {
        activeRegistrations.filter { reg ->
            val unitCode = reg["unitCode"].string.ifEmpty { reg["apartment"]["propertyCode"].string }
            val projectName = reg["projectName"].string.ifEmpty { reg["apartment"]["zone"].string.ifEmpty { reg["zone"].string } }
            val campaign = reg["campaignName"].string.ifEmpty { reg["salesCampaignName"].string.ifEmpty { reg["apartment"]["salesCampaignName"].string } }
            val block = reg["block"].string.ifEmpty { reg["apartment"]["building"].string.ifEmpty { reg["building"].string } }
            val productType = reg["apartmentType"].string.ifEmpty { reg["apartment"]["apartmentType"].string }
            val propertyType = reg["propertyType"].string.ifEmpty { reg["apartment"]["propertyType"].string }
            val floor = reg["floor"].string.ifEmpty {
                val fl = reg["apartment"]["floor"].int
                if (fl > 0) "$fl" else ""
            }
            val direction = reg["direction"].string.ifEmpty { reg["apartment"]["direction"].string }
            val balconyDirection = reg["balconyDirection"].string.ifEmpty { reg["apartment"]["balconyDirection"].string }

            val q = search.trim().lowercase()
            val matchSearch = q.isEmpty() || unitCode.lowercase().contains(q)
                || projectName.lowercase().contains(q)
                || block.lowercase().contains(q)

            val bStatus = reg["bookingStatus"].string.lowercase()

            val matchTab = when (selectedTab) {
                "online_holding" -> bStatus == "online_holding"
                "pending" -> bStatus == "pending_booking" || bStatus == "cancel_requested"
                "holding" -> bStatus == "holding_success"
                "completed" -> listOf("deposited", "commission_pending", "commission_paid", "purchased").contains(bStatus)
                "registered" -> bStatus.isEmpty() || bStatus == "none" || bStatus == "available"
                else -> true
            }

            matchSearch && matchTab
                && (projectFilter.isEmpty() || projectName == projectFilter)
                && (campaignFilter.isEmpty() || campaign == campaignFilter)
                && (blockFilter.isEmpty() || block == blockFilter)
                && (productTypeFilter.isEmpty() || productType == productTypeFilter)
                && (propertyTypeFilter.isEmpty() || propertyType == propertyTypeFilter)
                && (floorFilter.isEmpty() || floor == floorFilter)
                && (directionFilter.isEmpty() || direction == directionFilter)
                && (balconyDirectionFilter.isEmpty() || balconyDirection == balconyDirectionFilter)
        }
    }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Rổ hàng của tôi",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onNavigate(FutaDestinations.MY_LISTINGS) }) {
                        Icon(Icons.Default.Add, "Đăng ký bán", tint = FutaColors.BrandGreen)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
        ) {
            // Search Input & Filter Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, "Tìm kiếm", tint = FutaColors.Slate, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (search.isEmpty()) {
                                    Text("Mã căn, toà nhà, dự án...", color = Color.Gray, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (search.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                "Xóa tìm kiếm",
                                tint = FutaColors.Slate,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { search = "" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Filter Button with Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeFilterCount > 0) FutaColors.BrandGreen else Color.White)
                        .border(
                            BorderStroke(1.dp, if (activeFilterCount > 0) Color.Transparent else Color(0xFFE2E8F0)),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { showFilterSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Bộ lọc",
                        tint = if (activeFilterCount > 0) Color.White else FutaColors.BrandGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    if (activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$activeFilterCount",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Tabs with Counts
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { (key, label) ->
                    val isSelected = selectedTab == key
                    val count = tabCounts[key] ?: 0
                    Surface(
                        color = if (isSelected) FutaColors.BrandGreen else Color.White,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { selectedTab = key }
                    ) {
                        Text(
                            text = "$label ($count)",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else FutaColors.Navy,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (loading && items.isEmpty()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) {
                        FutaSkeletonBlock(height = 120.dp, radius = 16.dp)
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Chưa có sản phẩm trong rổ hàng",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        Text(
                            text = "Bạn chỉ thấy các căn đã đăng ký bán và được duyệt quyền bán tại đây.",
                            fontSize = 13.5.sp,
                            color = FutaColors.Slate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onNavigate(FutaDestinations.MY_LISTINGS) },
                            colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đăng ký bán sản phẩm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems, key = { it["id"].string.ifEmpty { it["unitCode"].string } }) { reg ->
                        AdvisorProductCartCard(
                            reg = reg,
                            onHold = {
                                val targetId = reg["apartmentId"].string.ifEmpty { reg["apartment"]["recordId"].string.ifEmpty { reg["apartment"]["id"].string.ifEmpty { reg.id } } }
                                if (targetId.isNotEmpty()) {
                                    onNavigate(FutaDestinations.propertyDetail(targetId))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FutaBottomSheet(
            visible = true,
            onDismiss = { showFilterSheet = false },
            title = "Bộ lọc rổ hàng",
            headerTrailing = if (activeFilterCount > 0) {
                {
                    Text(
                        text = "Đặt lại",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FutaColors.BrandGreen,
                        modifier = Modifier.clickable {
                            projectFilter = ""
                            campaignFilter = ""
                            blockFilter = ""
                            productTypeFilter = ""
                            propertyTypeFilter = ""
                            floorFilter = ""
                            directionFilter = ""
                            balconyDirectionFilter = ""
                        }
                    )
                }
            } else null
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (projectOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "DỰ ÁN",
                        selected = projectFilter,
                        options = projectOptions,
                        onSelect = { projectFilter = it }
                    )
                }

                if (campaignOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "CHƯƠNG TRÌNH BÁN HÀNG",
                        selected = campaignFilter,
                        options = campaignOptions,
                        onSelect = { campaignFilter = it }
                    )
                }

                if (blockOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "TÒA / BLOCK",
                        selected = blockFilter,
                        options = blockOptions,
                        onSelect = { blockFilter = it }
                    )
                }

                if (productTypeOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "LOẠI SẢN PHẨM",
                        selected = productTypeFilter,
                        options = productTypeOptions,
                        onSelect = { productTypeFilter = it }
                    )
                }

                if (propertyTypeOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "LOẠI BẤT ĐỘNG SẢN",
                        selected = propertyTypeFilter,
                        options = propertyTypeOptions,
                        onSelect = { propertyTypeFilter = it }
                    )
                }

                if (floorOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "TẦNG",
                        selected = floorFilter,
                        options = floorOptions,
                        displayTransform = { "Tầng $it" },
                        onSelect = { floorFilter = it }
                    )
                }

                if (directionOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "HƯỚNG CỬA CHÍNH",
                        selected = directionFilter,
                        options = directionOptions,
                        onSelect = { directionFilter = it }
                    )
                }

                if (balconyDirectionOptions.isNotEmpty()) {
                    FilterChipSection(
                        title = "HƯỚNG BAN CÔNG",
                        selected = balconyDirectionFilter,
                        options = balconyDirectionOptions,
                        onSelect = { balconyDirectionFilter = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipSection(
    title: String,
    selected: String,
    options: List<String>,
    displayTransform: (String) -> String = { it },
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = FutaColors.Slate
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected.isEmpty()) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                modifier = Modifier.clickable { onSelect("") }
            ) {
                Text(
                    text = "Tất cả",
                    fontSize = 12.sp,
                    fontWeight = if (selected.isEmpty()) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected.isEmpty()) Color.White else FutaColors.Navy,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            options.forEach { opt ->
                val isSel = selected == opt
                Surface(
                    shape = CircleShape,
                    color = if (isSel) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable { onSelect(opt) }
                ) {
                    Text(
                        text = displayTransform(opt),
                        fontSize = 12.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) Color.White else FutaColors.Navy,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvisorProductCartCard(
    reg: JSONValue,
    onHold: () -> Unit
) {
    val unitCode = reg["unitCode"].string.ifEmpty { reg["apartment"]["propertyCode"].string }
    val projectName = reg["projectName"].string.ifEmpty { reg["apartment"]["zone"].string }
    val block = reg["block"].string.ifEmpty { reg["apartment"]["building"].string }
    val floor = reg["floor"].string.ifEmpty {
        val fl = reg["apartment"]["floor"].int
        if (fl > 0) "$fl" else ""
    }
    val rawPrice = if (reg["price"].double > 0) reg["price"].double else (if (reg["apartment"]["sellPrice"].double > 0) reg["apartment"]["sellPrice"].double else reg["apartment"]["price"].double)
    val area = if (reg["area"].double > 0) reg["area"].double else reg["apartment"]["size_m2"].double
    val bStatus = reg["bookingStatus"].string.lowercase()
    val status = reg["status"].string.lowercase()

    val canBook = (status == "active" || status == "approved") && (bStatus.isEmpty() || listOf("none", "rejected", "cancelled", "available").contains(bStatus))

    val rawImg = reg["image"].string.ifEmpty {
        reg["thumbnail"].string.ifEmpty {
            reg["apartment"]["thumbnail"].string.ifEmpty {
                reg["apartment"]["image"].string
            }
        }
    }
    val imgUrl = when {
        rawImg.isEmpty() -> "https://bds.futaland.vn/images/futa/news-sample.png"
        rawImg.startsWith("http://") || rawImg.startsWith("https://") -> rawImg
        else -> "https://bds.futaland.vn${if (rawImg.startsWith("/")) "" else "/"}$rawImg"
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = unitCode,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE2E8F0))
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = unitCode,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        // Status badge
                        when {
                            bStatus == "online_holding" -> StatusBadge("Đang giữ chỗ (15p)", Color(0xFFF97316), Color(0xFFFFF7ED))
                            bStatus == "pending_booking" || bStatus == "cancel_requested" -> StatusBadge("Chờ xác nhận cọc", Color(0xFFD97706), Color(0xFFFEF3C7))
                            bStatus == "holding_success" -> StatusBadge("ERP đã khóa căn", FutaColors.BrandGreen, Color(0xFFECFDF5))
                            listOf("deposited", "commission_pending", "commission_paid", "purchased").contains(bStatus) -> StatusBadge("GD thành công", Color(0xFF2563EB), Color(0xFFEFF6FF))
                            status == "active" || status == "approved" -> StatusBadge("Đang mở quyền bán", FutaColors.BrandGreen, Color(0xFFECFDF5))
                            status == "pending" -> StatusBadge("Chờ duyệt quyền bán", Color(0xFFD97706), Color(0xFFFEF3C7))
                            else -> StatusBadge("Đã hết hạn", Color.Gray, Color(0xFFF1F5F9))
                        }
                    }
                    Text(
                        text = projectName + (if (block.isNotEmpty()) " · Toà $block" else "") + (if (floor.isNotEmpty()) " · Tầng $floor" else ""),
                        fontSize = 12.5.sp,
                        color = FutaColors.Slate
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMoney(rawPrice),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen
                        )
                        if (area > 0) {
                            Text(
                                text = "${area.toInt()} m²",
                                fontSize = 12.sp,
                                color = FutaColors.Slate
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val exp = reg["expiresAt"].string
                if (exp.isNotEmpty() && (status == "active" || status == "approved")) {
                    Text(
                        text = "Hạn quyền bán: ${exp.take(10)}",
                        fontSize = 11.5.sp,
                        color = FutaColors.Slate
                    )
                } else if (reg["registeredAt"].string.isNotEmpty()) {
                    Text(
                        text = "Đăng ký: ${reg["registeredAt"].string.take(10)}",
                        fontSize = 11.5.sp,
                        color = FutaColors.Slate
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (bStatus == "online_holding") {
                    Text(
                        text = "Đang giữ cọc 15p",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF97316)
                    )
                } else if (bStatus == "holding_success") {
                    Text(
                        text = "ERP đã khóa căn",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.BrandGreen
                    )
                } else if (listOf("deposited", "commission_pending", "commission_paid", "purchased").contains(bStatus)) {
                    Text(
                        text = "Giao dịch thành công",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                } else if (bStatus == "pending_booking" || bStatus == "cancel_requested") {
                    Text(
                        text = "Chờ xác nhận cọc",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                } else if (canBook) {
                    Button(
                        onClick = onHold,
                        colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đăng ký giữ chỗ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color, bg: Color) {
    Surface(
        color = bg,
        shape = CircleShape
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun formatMoney(value: Double): String {
    if (value <= 0) return "Liên hệ"
    if (value >= 1_000_000_000) {
        return String.format("%.2f tỷ", value / 1_000_000_000).replace(".00", "").replace(".", ",")
    }
    return String.format("%,.0f đ", value)
}
