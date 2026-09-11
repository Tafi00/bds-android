package vn.futaland.app.features.properties

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

@Composable
fun PropertySearchScreen(
    initialPropertyType: String? = null,
    initialKeyword: String? = null,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf(initialKeyword.orEmpty()) }
    var listingType by remember { mutableStateOf("") } // "", "sell", "rent"
    var propertyType by remember { mutableStateOf(initialPropertyType.orEmpty()) }
    var zone by remember { mutableStateOf("") }
    var furniture by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("newest") } // "newest", "price_asc", "price_desc", "area_desc"
    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var bedrooms by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("") }
    var minArea by remember { mutableStateOf("") }
    var maxArea by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                keyword = spoken
            }
        }
    }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortPopover by remember { mutableStateOf(false) }

    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val hasActiveFilters = listingType.isNotEmpty() || propertyType.isNotEmpty() ||
            minPrice.isNotEmpty() || maxPrice.isNotEmpty() || bedrooms.isNotEmpty() ||
            direction.isNotEmpty() || minArea.isNotEmpty() || maxArea.isNotEmpty() ||
            zone.isNotEmpty() || furniture.isNotEmpty() || sort != "newest"

    fun search(targetPage: Int = 1) {
        scope.launch {
            loading = true
            page = targetPage
            try {
                val query = mutableMapOf<String, String>()
                if (keyword.isNotEmpty()) {
                    query["q"] = keyword
                    query["search"] = keyword
                }
                if (listingType.isNotEmpty()) query["listingType"] = listingType
                if (propertyType.isNotEmpty()) query["propertyType"] = propertyType
                if (minPrice.isNotEmpty()) {
                    val p = minPrice.filter { it.isDigit() }
                    query["priceMin"] = p
                    query["minPrice"] = p
                }
                if (maxPrice.isNotEmpty()) {
                    val p = maxPrice.filter { it.isDigit() }
                    query["priceMax"] = p
                    query["maxPrice"] = p
                }
                if (bedrooms.isNotEmpty()) query["bedrooms"] = bedrooms
                if (direction.isNotEmpty()) query["direction"] = direction
                if (minArea.isNotEmpty()) {
                    query["areaMin"] = minArea
                    query["minArea"] = minArea
                }
                if (maxArea.isNotEmpty()) {
                    query["areaMax"] = maxArea
                    query["maxArea"] = maxArea
                }
                if (zone.isNotEmpty()) query["zone"] = zone
                if (furniture.isNotEmpty()) query["furniture"] = furniture
                query["sort"] = sort
                query["page"] = page.toString()
                query["limit"] = "20"

                val res = APIClient.get().request("/apartments", query = query)
                results = res["data"].array
                totalCount = res["total"].int.takeIf { it > 0 } ?: results.size
                totalPages = (totalCount + 11) / 12
            } catch (_: Exception) {
                results = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(listingType, sort) {
        search(1)
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Search bar row with Filter icon button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FutaInput(
                            value = keyword,
                            onValueChange = {
                                keyword = it
                                search(1)
                            },
                            placeholder = "Tìm theo tên dự án, địa chỉ, mã căn...",
                            leadingIcon = Icons.Default.Search,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (keyword.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xóa từ khóa",
                                            tint = FutaColors.Slate,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { keyword = ""; search(1) }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Tìm kiếm giọng nói",
                                        tint = FutaColors.BrandGreen,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Tìm kiếm bất động sản FUTA Land...")
                                                }
                                                try {
                                                    speechLauncher.launch(intent)
                                                } catch (_: Exception) {
                                                    ToastCenter.show("Thiết bị không hỗ trợ nhận diện giọng nói", isError = true)
                                                }
                                            }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(Modifier.width(10.dp))

                        // Filter Button with Badge indicator
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (hasActiveFilters) FutaColors.BrandGreen else Color(0xFFF1F5F9))
                                .clickable { showFilterSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Bộ lọc",
                                tint = if (hasActiveFilters) Color.White else FutaColors.Navy,
                                modifier = Modifier.size(20.dp)
                            )
                            if (hasActiveFilters) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF97316))
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Quick Filter Bar matching iOS PropertiesViews.swift
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickChip(title = "Tất cả", isSelected = listingType.isEmpty()) {
                                listingType = ""
                            }
                            QuickChip(title = "Mua bán", isSelected = listingType == "sell") {
                                listingType = if (listingType == "sell") "" else "sell"
                            }
                            QuickChip(title = "Cho thuê", isSelected = listingType == "rent") {
                                listingType = if (listingType == "rent") "" else "rent"
                            }
                        }

                        // Sort pill trigger with anchored popover
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { showSortPopover = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (sort) {
                                            "price_asc" -> "Giá tăng dần"
                                            "price_desc" -> "Giá giảm dần"
                                            "area_desc" -> "Diện tích"
                                            else -> "Mới nhất"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FutaColors.Navy
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, null, tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                                }
                            }

                            FutaPopover(
                                expanded = showSortPopover,
                                onDismissRequest = { showSortPopover = false },
                                items = listOf(
                                    "newest" to "Mới nhất",
                                    "price_asc" to "Giá tăng dần",
                                    "price_desc" to "Giá giảm dần",
                                    "area_desc" to "Diện tích lớn nhất"
                                ),
                                onItemSelected = { (key, _) ->
                                    sort = key
                                    showSortPopover = false
                                    search(1)
                                },
                                itemTrailingIcon = { (key, _) ->
                                    if (sort == key) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = FutaColors.BrandGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            ) { (key, label) ->
                                Text(
                                    text = label,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (sort == key) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sort == key) FutaColors.BrandGreen else FutaColors.Navy
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FutaColors.PageBg)
                .padding(padding)
        ) {
            if (loading && results.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(3) {
                        FutaPropertyCardSkeleton()
                    }
                }
            } else if (results.isEmpty()) {
                FutaEmptyState(
                    title = "Không tìm thấy bất động sản",
                    message = "Thử điều chỉnh từ khóa hoặc bộ lọc để tìm kiếm kết quả phù hợp."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(results, key = { idx, apt -> (apt.id.ifEmpty { "search" }) + "-$idx" }) { _, apt ->
                        FutaPropertyCard(
                            apartment = apt,
                            isFavorited = false,
                            onFavoriteClick = {
                                if (AppSession.shared.isAuthenticated) {
                                    scope.launch {
                                        try {
                                            APIClient.get().request("/favorites/${apt.id}", method = "POST")
                                            ToastCenter.show("Đã cập nhật yêu thích")
                                        } catch (_: Exception) {}
                                    }
                                } else {
                                    onNavigate(FutaDestinations.AUTH)
                                }
                            },
                            onShareClick = {
                                val shareUrl = PropertyFormatters.shareUrl(apt)
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    putExtra(Intent.EXTRA_SUBJECT, PropertyFormatters.propertyTitle(apt))
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ sản phẩm"))
                            },
                            onCallClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:02838386852"))
                                context.startActivity(intent)
                            },
                            onChatClick = { onNavigate(FutaDestinations.INBOX) },
                            onClick = { onNavigate(FutaDestinations.propertyDetail(apt.id)) }
                        )
                    }

                    // Pagination Bar matching iOS
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FutaButton(
                                text = "Trang trước",
                                variant = FutaButtonVariant.OUTLINE,
                                enabled = page > 1,
                                height = 36.dp,
                                onClick = { search(page - 1) }
                            )

                            Text(
                                text = "Trang $page / $totalPages ($totalCount BĐS)",
                                fontSize = 12.sp,
                                color = FutaColors.Slate,
                                fontWeight = FontWeight.Medium
                            )

                            FutaButton(
                                text = "Trang sau",
                                variant = FutaButtonVariant.OUTLINE,
                                enabled = page < totalPages,
                                height = 36.dp,
                                onClick = { search(page + 1) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Comprehensive Filter Bottom Sheet matching iOS PropertiesViews.swift
    FutaBottomSheet(
        visible = showFilterSheet,
        onDismiss = { showFilterSheet = false },
        title = "Bộ lọc bất động sản"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Price Range with Slider & Presets (Matching iOS)
            var priceSliderRange by remember(minPrice, maxPrice) {
                val low = (minPrice.toDoubleOrNull() ?: 0.0) / 1_000_000_000.0
                val high = (maxPrice.toDoubleOrNull() ?: 20_000_000_000.0) / 1_000_000_000.0
                mutableStateOf(low.toFloat().coerceIn(0f, 20f)..high.toFloat().coerceIn(0f, 20f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("KHOẢNG GIÁ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                val minLabel = if (priceSliderRange.start <= 0.1f) "0" else String.format(java.util.Locale.US, "%.1f", priceSliderRange.start) + " tỷ"
                val maxLabel = if (priceSliderRange.endInclusive >= 19.9f) "Trên 20 tỷ" else String.format(java.util.Locale.US, "%.1f", priceSliderRange.endInclusive) + " tỷ"
                Text("$minLabel - $maxLabel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
            }

            RangeSlider(
                value = priceSliderRange,
                onValueChange = { range ->
                    priceSliderRange = range
                    minPrice = if (range.start > 0.1f) (range.start * 1_000_000_000L).toLong().toString() else ""
                    maxPrice = if (range.endInclusive < 19.9f) (range.endInclusive * 1_000_000_000L).toLong().toString() else ""
                },
                valueRange = 0f..20f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = FutaColors.BrandGreen,
                    activeTrackColor = FutaColors.BrandGreen,
                    inactiveTrackColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Price Quick Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Tất cả" to ("" to ""),
                    "< 2 tỷ" to ("" to "2000000000"),
                    "2 - 5 tỷ" to ("2000000000" to "5000000000"),
                    "5 - 10 tỷ" to ("5000000000" to "10000000000"),
                    "> 10 tỷ" to ("10000000000" to "")
                ).forEach { (label, range) ->
                    val isSelected = minPrice == range.first && maxPrice == range.second
                    QuickChip(title = label, isSelected = isSelected) {
                        minPrice = range.first
                        maxPrice = range.second
                    }
                }
            }

            // 2. Area Range with Slider & Presets
            var areaSliderRange by remember(minArea, maxArea) {
                val low = (minArea.toFloatOrNull() ?: 0f).coerceIn(0f, 300f)
                val high = (maxArea.toFloatOrNull() ?: 300f).coerceIn(0f, 300f)
                mutableStateOf(low..high)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DIỆN TÍCH (M²)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                val minAreaStr = if (areaSliderRange.start <= 5f) "0" else "${areaSliderRange.start.toInt()}"
                val maxAreaStr = if (areaSliderRange.endInclusive >= 295f) "Trên 300 m²" else "${areaSliderRange.endInclusive.toInt()} m²"
                Text("$minAreaStr - $maxAreaStr", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
            }

            RangeSlider(
                value = areaSliderRange,
                onValueChange = { range ->
                    areaSliderRange = range
                    minArea = if (range.start > 5f) range.start.toInt().toString() else ""
                    maxArea = if (range.endInclusive < 295f) range.endInclusive.toInt().toString() else ""
                },
                valueRange = 0f..300f,
                steps = 29,
                colors = SliderDefaults.colors(
                    thumbColor = FutaColors.BrandGreen,
                    activeTrackColor = FutaColors.BrandGreen,
                    inactiveTrackColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Tất cả" to ("" to ""),
                    "< 50 m²" to ("" to "50"),
                    "50 - 80 m²" to ("50" to "80"),
                    "80 - 120 m²" to ("80" to "120"),
                    "> 120 m²" to ("120" to "")
                ).forEach { (label, range) ->
                    val isSelected = minArea == range.first && maxArea == range.second
                    QuickChip(title = label, isSelected = isSelected) {
                        minArea = range.first
                        maxArea = range.second
                    }
                }
            }
            // Bedrooms
            Text("SỐ PHÒNG NGỦ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "Tất cả", "1" to "1 PN", "2" to "2 PN", "3" to "3 PN", "4" to "4+ PN").forEach { (valStr, label) ->
                    val isSelected = bedrooms == valStr
                    QuickChip(title = label, isSelected = isSelected) {
                        bedrooms = valStr
                    }
                }
            }

            // Property Types
            Text("LOẠI BẤT ĐỘNG SẢN", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("" to "Tất cả", "can-ho" to "Căn hộ", "nha-pho" to "Nhà phố", "biet-thu" to "Biệt thự", "dat-nen" to "Đất nền").forEach { (valStr, label) ->
                    val isSelected = propertyType == valStr
                    QuickChip(title = label, isSelected = isSelected) {
                        propertyType = valStr
                    }
                }
            }

            // Direction
            Text("HƯỚNG NHÀ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("", "Đông", "Tây", "Nam", "Bắc", "Đông Nam", "Đông Bắc", "Tây Nam", "Tây Bắc").forEach { dir ->
                    val isSelected = direction == dir
                    QuickChip(title = dir.ifEmpty { "Tất cả" }, isSelected = isSelected) {
                        direction = dir
                    }
                }
            }
            // Zone / Project Filter (Matching iOS availableZones)
            Text("DỰ ÁN / PHÂN KHU", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("", "Times Square", "Khu Đô Thị C5B", "Hilton Phan Thiết", "Thuận Phước", "FUTA Kim Long", "Bến Tre Riverside").forEach { z ->
                    val isSelected = zone == z
                    QuickChip(title = z.ifEmpty { "Tất cả" }, isSelected = isSelected) {
                        zone = z
                    }
                }
            }

            // Furniture Filter (Matching iOS)
            Text("TÌNH TRẠNG NỘI THẤT", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("" to "Tất cả", "Nhà trống" to "Nhà trống", "Bếp rèm" to "Bếp rèm", "Cơ bản" to "Cơ bản cao cấp", "Đầy đủ" to "Đầy đủ nội thất").forEach { (fVal, fLabel) ->
                    val isSelected = furniture == fVal
                    QuickChip(title = fLabel, isSelected = isSelected) {
                        furniture = fVal
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FutaButton(
                    text = "Đặt lại",
                    variant = FutaButtonVariant.OUTLINE,
                    onClick = {
                        listingType = ""
                        propertyType = ""
                        zone = ""
                        furniture = ""
                        minPrice = ""
                        maxPrice = ""
                        bedrooms = ""
                        direction = ""
                        minArea = ""
                        maxArea = ""
                        sort = "newest"
                        showFilterSheet = false
                        search(1)
                    },
                    modifier = Modifier.weight(1f)
                )
                FutaButton(
                    text = if (totalCount > 0) "Xem $totalCount bất động sản" else "Áp dụng bộ lọc",
                    variant = FutaButtonVariant.PRIMARY,
                    onClick = {
                        showFilterSheet = false
                        search(1)
                    },
                    modifier = Modifier.weight(1.5f)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun QuickChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, if (isSelected) Color.Transparent else FutaColors.LightBlueBorder)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else FutaColors.Navy,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
