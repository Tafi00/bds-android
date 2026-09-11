package vn.futaland.app.features.properties

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations
import kotlin.math.pow

@Composable
fun PropertyDetailScreen(
    propertyId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var property by remember { mutableStateOf<JSONValue?>(null) }
    var similarApartments by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // Media mode tab: "photos", "video", "flycam", "tour"
    var selectedMediaTab by remember { mutableStateOf("photos") }

    // Loan Calculator State
    var loanPercent by remember { mutableFloatStateOf(70f) }
    var loanYears by remember { mutableFloatStateOf(20f) }

    // Booking & Holding Sheet States
    var showBookingSheet by remember { mutableStateOf(false) }
    var showHoldingSheet by remember { mutableStateOf(false) }
    var showAdvisorContactSheet by remember { mutableStateOf(false) }
    var bookingDate by remember { mutableStateOf("Ngày mai (09:00)") }
    var bookingSlot by remember { mutableStateOf("Sáng (09:00 - 11:30)") }
    var bookingName by remember { mutableStateOf(AppSession.shared.user?.get("name")?.string.orEmpty()) }
    var bookingPhone by remember { mutableStateOf(AppSession.shared.user?.get("phone")?.string.orEmpty()) }
    var holdingName by remember { mutableStateOf(AppSession.shared.user?.get("name")?.string.orEmpty()) }
    var holdingPhone by remember { mutableStateOf(AppSession.shared.user?.get("phone")?.string.orEmpty()) }
    var holdingCccd by remember { mutableStateOf("") }
    var holdingBusy by remember { mutableStateOf(false) }
    LaunchedEffect(propertyId) {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/apartments/$propertyId")
                property = res["data"]
                val zone = property?.get("zone")?.string.orEmpty()
                if (zone.isNotEmpty()) {
                    val simRes = APIClient.get().request("/apartments", query = mapOf("zone" to zone, "limit" to "6"))
                    similarApartments = simRes["data"].array.filter { it.id != propertyId }
                }
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.sf_chevron_left),
                            contentDescription = "Quay lại",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    val code = property?.get("propertyCode")?.string?.ifEmpty { property?.get("code")?.string.orEmpty() } ?: ""
                    Text(
                        text = if (code.isNotEmpty()) "Mã căn: $code" else (property?.get("title")?.string?.ifEmpty { "Chi tiết sản phẩm" } ?: "Chi tiết"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val shareUrl = PropertyFormatters.shareUrl(property, propertyId)
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                            putExtra(Intent.EXTRA_SUBJECT, PropertyFormatters.propertyTitle(property))
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ sản phẩm"))
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.sf_card_share),
                            contentDescription = "Chia sẻ",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            property?.let { apt ->
                StickyContactBottomBar(
                    apt = apt,
                    onCallClick = { showAdvisorContactSheet = true },
                    onChatClick = { showAdvisorContactSheet = true },
                    onHoldClick = { showHoldingSheet = true }
                )
            }
        }
    ) { padding ->
        if (loading || property == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FutaSkeletonBlock(height = 250.dp, radius = 18.dp)
                FutaSkeletonBlock(height = 26.dp, width = 220.dp)
                FutaSkeletonBlock(height = 120.dp, radius = 14.dp)
            }
        } else {
            val apt = property!!
            val price = apt["price"].double
            val area = apt["areaM2"].double.takeIf { it > 0 }
                ?: apt["size_m2"].double.takeIf { it > 0 }
                ?: apt["area"].double.takeIf { it > 0 } ?: 107.5

            val rawImagesList = mutableListOf<String>()
            fun cleanImgUrl(raw: String): String {
                val t = raw.trim()
                if (t.isEmpty() || t == "null") return ""
                if (t.startsWith("http://") || t.startsWith("https://")) return t
                val c = if (t.startsWith("/")) t else "/$t"
                return "https://bds.futaland.vn$c"
            }
            val mainImg = cleanImgUrl(apt["image"].string)
            if (mainImg.isNotEmpty()) rawImagesList.add(mainImg)
            val bannerImg = cleanImgUrl(apt["bannerImage"].string)
            if (bannerImg.isNotEmpty()) rawImagesList.add(bannerImg)
            apt["images"].array.forEach { imgItem ->
                val orig = cleanImgUrl(imgItem["original"].string.ifEmpty { imgItem["url"].string.ifEmpty { imgItem.string } })
                if (orig.isNotEmpty()) rawImagesList.add(orig)
            }
            val images = if (rawImagesList.isEmpty()) listOf(PropertyFormatters.resolveImage(apt)) else rawImagesList.distinct()
            val pagerState = rememberPagerState(pageCount = { images.size })

            val videoUrl = apt["videoUrl"].string.ifEmpty { apt["youtubeUrl"].string }
            val tour360Url = apt["virtualTourUrl"].string.ifEmpty { apt["tour360Url"].string }
            val flycamUrl = apt["flycamUrl"].string.ifEmpty { apt["projectFlycamVideoUrl"].string }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7F9FC))
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Media Gallery (Segment Tabs + Pager + Thumbnails)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Mode Switcher Tabs
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFE7EEF5),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    Triple("photos", "Ảnh", true),
                                    Triple("video", "Video", videoUrl.isNotEmpty()),
                                    Triple("flycam", "Flycam", flycamUrl.isNotEmpty()),
                                    Triple("tour", "View 360°", tour360Url.isNotEmpty())
                                ).forEach { (key, label, available) ->
                                    val isSelected = selectedMediaTab == key
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shadowElevation = if (isSelected) 2.dp else 0.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { selectedMediaTab = key }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) FutaColors.Navy else if (available) Color(0xFF405269) else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Main Viewport (250dp height)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF061D3D))
                        ) {
                            when (selectedMediaTab) {
                                "video" -> {
                                    MediaPlaceholderCard(
                                        title = if (videoUrl.isNotEmpty()) "Xem Video Thực Tế" else "Video đang được cập nhật",
                                        subtitle = if (videoUrl.isNotEmpty()) "Nhấn để mở video giới thiệu" else "Vui lòng quay lại sau",
                                        buttonLabel = "Xem Video",
                                        url = videoUrl,
                                        badgeColor = Color(0xFFEF4444),
                                        context = context
                                    )
                                }
                                "flycam" -> {
                                    MediaPlaceholderCard(
                                        title = if (flycamUrl.isNotEmpty()) "Flycam Toàn Cảnh" else "Flycam đang được cập nhật",
                                        subtitle = if (flycamUrl.isNotEmpty()) "Góc nhìn toàn cảnh từ trên cao" else "Vui lòng quay lại sau",
                                        buttonLabel = "Mở Flycam",
                                        url = flycamUrl,
                                        badgeColor = Color(0xFFF97316),
                                        context = context
                                    )
                                }
                                "tour" -> {
                                    MediaPlaceholderCard(
                                        title = if (tour360Url.isNotEmpty()) "Virtual Tour 360°" else "Tour 360° đang được cập nhật",
                                        subtitle = if (tour360Url.isNotEmpty()) "Không gian tương tác 360 độ" else "Vui lòng quay lại sau",
                                        buttonLabel = "Trải nghiệm ngay",
                                        url = tour360Url,
                                        badgeColor = Color(0xFF0E7643),
                                        context = context
                                    )
                                }
                                else -> {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                        val url = if (images.isNotEmpty()) images[page] else ""
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Counter badge at bottom right
                                    if (images.isNotEmpty()) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.sf_acc_news),
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "${pagerState.currentPage + 1} / ${images.size}",
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Thumbnail preview strip
                        if (selectedMediaTab == "photos" && images.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(images) { idx, imgUrl ->
                                    val isSelected = pagerState.currentPage == idx
                                    Box(
                                        modifier = Modifier
                                            .size(width = 68.dp, height = 52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFFF97316) else Color(0xFFCBD5E1),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                scope.launch { pagerState.animateScrollToPage(idx) }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = imgUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Product Summary Card (Matching Web & iOS)
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Top Row: Badges, Title, Favorite & Share
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val isSell = apt["listingType"].string.lowercase() != "rent"
                                        Surface(
                                            shape = CircleShape,
                                            color = FutaColors.MintBg
                                        ) {
                                            Text(
                                                text = if (isSell) "MUA BÁN" else "CHO THUÊ",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0E7643),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                            )
                                        }
                                        val zone = apt["zone"].string
                                        if (zone.isNotEmpty()) {
                                            Surface(
                                                shape = CircleShape,
                                                color = FutaColors.CreamBg
                                            ) {
                                                Text(
                                                    text = zone,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFF97316),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    val codeText = apt["propertyCode"].string.ifEmpty { apt["recordId"].string }
                                    Text(
                                        text = if (codeText.isNotEmpty()) "Mã căn: $codeText" else PropertyFormatters.propertyTitle(apt),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Navy
                                    )

                                    val titleText = apt["title"].string
                                    if (titleText.isNotEmpty() && titleText.length > 8) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = titleText,
                                            fontSize = 13.5.sp,
                                            color = FutaColors.Slate,
                                            lineHeight = 18.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // Favorite & Share buttons
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                            .clickable { isFavorite = !isFavorite },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = if (isFavorite) R.drawable.sf_card_heart_fill else R.drawable.sf_card_heart),
                                            contentDescription = "Yêu thích",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                            .clickable {
                                                val shareUrl = PropertyFormatters.shareUrl(apt, propertyId)
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                                    putExtra(Intent.EXTRA_SUBJECT, PropertyFormatters.propertyTitle(apt))
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ sản phẩm"))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sf_card_share),
                                            contentDescription = "Chia sẻ",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            // Info Rows (Dự án, Block, Tầng)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val zoneVal = apt["zone"].string
                                if (zoneVal.isNotEmpty()) {
                                    SummaryInfoRow(iconRes = R.drawable.sf_mappin_circle_green, label = "Dự án / Phân khu", value = zoneVal)
                                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
                                }
                                SummaryInfoRow(iconRes = R.drawable.sf_quick_projects, label = "Tòa / Block", value = apt["building"].string.ifEmpty { "Đang cập nhật" })
                                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)
                                SummaryInfoRow(iconRes = R.drawable.sf_spec_area, label = "Tầng", value = apt["floor"].string.ifEmpty { "Đang cập nhật" })
                            }

                            // Highlighted Price Card (Matching iOS)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFEDF1F5)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val isSell = apt["listingType"].string.lowercase() != "rent"
                                        Text(
                                            text = if (isSell) "Giá bán dự kiến" else "Giá thuê",
                                            fontSize = 11.5.sp,
                                            color = FutaColors.Slate
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = PropertyFormatters.listingPrice(apt),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFF97316)
                                        )
                                    }
                                    if (area > 0 && price > 0) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Đơn giá diện tích",
                                                fontSize = 11.5.sp,
                                                color = FutaColors.Slate
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = "~${"%.1f".format(price / area / 1_000_000)} tr/m²",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0E7643)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Technical Specifications 2-Column Grid (Matching Web & iOS)
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "THÔNG SỐ KỸ THUẬT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF97316),
                                letterSpacing = 0.6.sp
                            )
                            Spacer(Modifier.height(12.dp))

                            val specsList = mutableListOf(
                                Triple(R.drawable.sf_spec_area, "Diện tích sử dụng", "${area} m²"),
                                Triple(R.drawable.sf_spec_bed, "Phòng ngủ", "${apt["bedrooms"].int.coerceAtLeast(1)} PN"),
                                Triple(R.drawable.sf_spec_bath, "Phòng tắm / WC", "${apt["bathrooms"].int.coerceAtLeast(1)} WC")
                            )
                            val dir = apt["direction"].string
                            if (dir.isNotEmpty()) specsList.add(Triple(R.drawable.sf_spec_compass, "Hướng cửa chính", dir))
                            val balcony = apt["balconyDirection"].string
                            if (balcony.isNotEmpty()) specsList.add(Triple(R.drawable.sf_spec_compass, "Hướng ban công", balcony))
                            val legalText = apt["legalStatus"].string.ifEmpty { apt["legal"].string }.ifEmpty { "Sổ hồng" }
                            specsList.add(Triple(R.drawable.sf_acc_policies, "Pháp lý", legalText))
                            specsList.add(Triple(R.drawable.sf_quick_house, "Nội thất", apt["furniture"].string.ifEmpty { "Cơ bản cao cấp" }))

                            // 2-column grid rows
                            for (i in specsList.indices step 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SpecItemCard(spec = specsList[i], modifier = Modifier.weight(1f))
                                    if (i + 1 < specsList.size) {
                                        SpecItemCard(spec = specsList[i + 1], modifier = Modifier.weight(1f))
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Financial Loan Calculator (Matching Web Calculator)
                item {
                    FutaCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = FutaColors.PeachBorder
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "DỰ TÍNH TÀI CHÍNH & VAY NGÂN HÀNG",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "Tỷ lệ vay: ${loanPercent.toInt()}% (${PropertyFormatters.formatPrice(price * loanPercent / 100.0)})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FutaColors.Navy
                            )
                            Slider(
                                value = loanPercent,
                                onValueChange = { loanPercent = it },
                                valueRange = 10f..80f,
                                thumb = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        shadowElevation = 3.dp,
                                        border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.size(24.dp)
                                    ) {}
                                },
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        thumbTrackGapSize = 0.dp,
                                        trackInsideCornerSize = 0.dp,
                                        drawStopIndicator = null,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color(0xFF0E7643),
                                            inactiveTrackColor = Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier.height(6.dp)
                                    )
                                }
                            )

                            Text(
                                text = "Thời hạn: ${loanYears.toInt()} năm",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FutaColors.Navy
                            )
                            Slider(
                                value = loanYears,
                                onValueChange = { loanYears = it },
                                valueRange = 5f..35f,
                                thumb = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        shadowElevation = 3.dp,
                                        border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.size(24.dp)
                                    ) {}
                                },
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        thumbTrackGapSize = 0.dp,
                                        trackInsideCornerSize = 0.dp,
                                        drawStopIndicator = null,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color(0xFF0E7643),
                                            inactiveTrackColor = Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier.height(6.dp)
                                    )
                                }
                            )

                            val loanAmount = price * (loanPercent / 100.0)
                            val monthlyRate = 0.08 / 12.0
                            val totalMonths = loanYears * 12.0
                            val monthlyPayment = if (totalMonths > 0 && monthlyRate > 0) {
                                (loanAmount * monthlyRate * (1 + monthlyRate).pow(totalMonths)) /
                                        ((1 + monthlyRate).pow(totalMonths) - 1)
                            } else 0.0

                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Ước tính trả hàng tháng:", fontSize = 12.5.sp, color = FutaColors.Navy)
                                    Text("~${"%,d".format(monthlyPayment.toLong())} đ/tháng", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643))
                                }
                            }
                        }
                    }
                }
                // 4B. Townhouse Floor Breakdown (Matching Web & iOS)
                val isTownhouse = run {
                    val type = apt["propertyType"].string.lowercase()
                    val code = apt["propertyCode"].string.lowercase()
                    type.contains("nha-pho") || type.contains("biet-thu") || type.contains("townhouse") ||
                            type.contains("villa") || code.startsWith("np") || code.startsWith("bt") ||
                            apt["floorAreas"].array.isNotEmpty() || !apt["townhouseSpecs"].isNull
                }
                if (isTownhouse) {
                    item {
                        TownhouseFloorsCard(apt = apt)
                    }
                }


                // 5. Commission Panel (Sale & Admin View Only - Matching iOS)
                val isSaleView = AppSession.shared.isAuthenticated && listOf("admin", "sale", "advisor", "agent", "telesale").contains(AppSession.shared.role)
                if (isSaleView) {
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Percent, null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "HOA HỒNG DỰ KIẾN",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Navy,
                                        letterSpacing = 0.6.sp
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Tỷ lệ hoa hồng", fontSize = 11.5.sp, color = FutaColors.Slate)
                                        Text("2.0%", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Tiền hoa hồng ước tính", fontSize = 11.5.sp, color = FutaColors.Slate)
                                        val commAmount = price * 0.02
                                        Text(
                                            text = PropertyFormatters.formatPrice(commAmount),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF97316)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFEDF1F5)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Thưởng nóng chiến dịch", fontSize = 12.sp, color = FutaColors.Slate)
                                        Text("+10.000.000 VNĐ / căn", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643))
                                    }
                                }

                                FutaButton(
                                    text = "Đăng ký bán căn này (còn 3 suất)",
                                    variant = FutaButtonVariant.SECONDARY,
                                    height = 42.dp,
                                    onClick = { ToastCenter.show("Đã gửi yêu cầu đăng ký bán căn hộ này") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // 5. Amenities & Highlights (Web Parity)
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "TIỆN ÍCH NỔI BẬT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(12.dp))

                            val aptAmenities = apt["amenities"].array.map { it.string }.filter { it.isNotEmpty() }
                            val projAmenities = apt["projectProfile"]["amenities"].array.map { it.string }.filter { it.isNotEmpty() }
                                .ifEmpty { apt["project"]["amenities"].array.map { it.string }.filter { it.isNotEmpty() } }
                                .ifEmpty { apt["projectAmenities"].array.map { it.string }.filter { it.isNotEmpty() } }
                            val amenities = if (aptAmenities.isNotEmpty()) aptAmenities else if (projAmenities.isNotEmpty()) projAmenities else listOf("Hồ bơi tràn bờ", "Công viên cây xanh", "Phòng Gym & Yoga", "Bảo vệ 24/7", "Chỗ đỗ xe ô tô", "Khu BBQ ngoài trời")
                            for (i in amenities.indices step 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AmenityPill(label = amenities[i], modifier = Modifier.weight(1f))
                                    if (i + 1 < amenities.size) {
                                        AmenityPill(label = amenities[i + 1], modifier = Modifier.weight(1f))
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Detailed Description
                val desc = apt["description"].string
                if (desc.isNotEmpty()) {
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "MÔ TẢ CHI TIẾT",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = desc,
                                    fontSize = 13.5.sp,
                                    color = FutaColors.Slate,
                                    lineHeight = 20.sp,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (desc.length > 150) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (isDescriptionExpanded) "Thu gọn" else "Xem thêm",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.BrandGreen
                                        )
                                        Icon(
                                            painter = painterResource(R.drawable.sf_chevron_down),
                                            contentDescription = null,
                                            tint = FutaColors.BrandGreen,
                                            modifier = Modifier.size(12.dp).graphicsLayer {
                                                rotationZ = if (isDescriptionExpanded) 180f else 0f
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. Project Info Panel (Matching Web & iOS)
                val zoneName = apt["zone"].string
                if (zoneName.isNotEmpty()) {
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "THÔNG TIN DỰ ÁN",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF97316),
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = zoneName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                val addr = apt["address"].string
                                if (addr.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.sf_mappin_circle_green),
                                            contentDescription = null,
                                            tint = FutaColors.BrandGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = addr,
                                            fontSize = 13.sp,
                                            color = FutaColors.Slate
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val query = Uri.encode(addr.ifEmpty { zoneName })
                                            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))
                                            context.startActivity(mapIntent)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Place, null, tint = Color(0xFF0E7643), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Mở bản đồ chỉ đường", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643))
                                        }
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color(0xFF0E7643), modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                // 8. Legal Documents Section (Matching iOS)
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "HỒ SƠ PHÁP LÝ & TÀI LIỆU DỰ ÁN",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy,
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFECFDF5)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF0E7643), modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Đã xác minh", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643))
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFD7DCE2)),
                                modifier = Modifier
                                    .clickable {
                                        val legalDocs = apt["legalDocuments"].array
                                        val docUrl = legalDocs.firstOrNull()?.get("url")?.string.orEmpty()
                                            .ifEmpty { apt["documentUrl"].string }
                                        if (docUrl.isNotEmpty()) {
                                            try {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(docUrl))
                                                context.startActivity(browserIntent)
                                            } catch (_: Exception) {
                                                ToastCenter.show("Không thể mở tài liệu: $docUrl", isError = true)
                                            }
                                        } else {
                                            ToastCenter.show("Tài liệu pháp lý đang được cập nhật bản scan số.")
                                        }
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFFF1F0),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Icon(Icons.Default.Description, null, tint = Color(0xFFDF5D57), modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "PDF",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                modifier = Modifier
                                                    .background(Color(0xFFDF5D57), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = apt["legal"].string.ifEmpty { "Sổ hồng sở hữu lâu dài" },
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.Navy
                                        )
                                        Text("Tài liệu tham khảo do FUTA Land xác minh (2.4 MB)", fontSize = 11.5.sp, color = FutaColors.Slate)
                                    }
                                    Icon(Icons.Default.Visibility, "Xem", tint = Color(0xFF0E7643), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // 8. Advisor Contact Panel (Matching iOS & Web)
                item {
                    val availableList = apt["availableAdvisors"].array
                    val advisors = if (availableList.isNotEmpty()) {
                        availableList.distinctBy { it["id"].string.ifEmpty { it["advisorId"].string } }
                    } else {
                        val advId = apt["advisorId"].string.ifEmpty { apt["advisor"]["id"].string.ifEmpty { apt["createdBy"]["id"].string } }
                        val advName = apt["advisor"]["name"].string.ifEmpty { apt["createdBy"]["name"].string }.ifEmpty { apt["ownerName"].string.ifEmpty { "Chuyên viên tư vấn FUTA Land" } }
                        val advPhone = apt["advisor"]["phone"].string.ifEmpty { apt["createdBy"]["phone"].string }.ifEmpty { apt["ownerPhone"].string.ifEmpty { "02838386852" } }
                        val advAvatar = apt["advisor"]["avatar"].string.ifEmpty { apt["createdBy"]["avatar"].string }
                        listOf(
                            JSONValue.parse("""{"id":"$advId","name":"$advName","phone":"$advPhone","avatar":"$advAvatar"}""")
                        )
                    }

                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TƯ VẤN SẢN PHẨM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF97316),
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Text(
                                        text = if (advisors.size > 1) "${advisors.size} TVV sẵn sàng" else "Chuyên viên sẵn sàng",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0E7643),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                advisors.forEachIndexed { idx, adv ->
                                    val advName = adv["name"].string.ifEmpty { "Chuyên viên tư vấn FUTA Land" }
                                    val advPhone = adv["phone"].string.ifEmpty { "02838386852" }
                                    val advAvatar = adv["avatar"].string
                                    val advId = adv["id"].string.ifEmpty { adv["advisorId"].string }

                                    if (idx > 0) {
                                        HorizontalDivider(color = Color(0xFFE2E8F0))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFE8F5E9),
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            if (advAvatar.isNotEmpty()) {
                                                coil3.compose.AsyncImage(
                                                    model = advAvatar,
                                                    contentDescription = advName,
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                )
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = advName.take(2).uppercase(),
                                                        color = Color(0xFF0E7643),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = advName,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FutaColors.Navy
                                            )
                                            Text(
                                                text = if (advisors.size > 1) "TVV phụ trách căn hộ" else "Sẵn sàng hỗ trợ 24/7",
                                                fontSize = 11.5.sp,
                                                color = FutaColors.Slate
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // 1. Phone Call Button
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFFDF6EE),
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clickable {
                                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$advPhone")))
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.sf_btn_phone),
                                                        contentDescription = "Gọi",
                                                        tint = Color(0xFFF97316),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }

                                            // 2. Zalo Deep Link Button
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFEFF6FF),
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clickable {
                                                        val zaloUrl = "https://zalo.me/$advPhone"
                                                        try {
                                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(zaloUrl)))
                                                        } catch (_: Exception) {
                                                            ToastCenter.show("Không thể mở Zalo: $zaloUrl")
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("Zalo", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF0068FF))
                                                }
                                            }

                                            // 3. Native Chat Button
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFE8F5E9),
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clickable {
                                                        onNavigate(
                                                            FutaDestinations.chat(
                                                                advisorId = advId,
                                                                advisorName = advName,
                                                                apartmentId = apt.id.ifEmpty { propertyId }
                                                            )
                                                        )
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.sf_btn_chat),
                                                        contentDescription = "Chat",
                                                        tint = Color(0xFF0E7643),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // AI Consult Button (Matching iOS)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.5.dp, Color(0xFF0E7643)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigate(
                                            FutaDestinations.chat(
                                                apartmentId = apt.id.ifEmpty { propertyId },
                                                isAi = true
                                            )
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 11.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.sf_tab_chat_active),
                                        contentDescription = null,
                                        tint = Color(0xFF0E7643),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Tư vấn với AI (24/7)",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0E7643)
                                    )
                                }
                            }
                        }
                    }
                }

                // 9. Similar Properties Section
                if (similarApartments.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "BẤT ĐỘNG SẢN CÙNG PHÂN KHU",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Slate,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(similarApartments, key = { idx, sim -> (sim.id.ifEmpty { "sim" }) + "-$idx" }) { _, sim ->
                                    Box(modifier = Modifier.width(260.dp)) {
                                        FutaPropertyCard(
                                            apartment = sim,
                                            onClick = { onNavigate(FutaDestinations.propertyDetail(sim.id)) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(60.dp))
                }
            }
        }
        // =========================================================================
        // 0. ADVISOR CONTACT BOTTOM SHEET (Matching Web & iOS)
        // =========================================================================
        if (showAdvisorContactSheet) {
            property?.let { apt ->
                AdvisorContactBottomSheet(
                    apt = apt,
                    onDismiss = { showAdvisorContactSheet = false },
                    onCallAdvisor = { phone ->
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    },
                    onChatAdvisor = { advId, advName ->
                        showAdvisorContactSheet = false
                        onNavigate(FutaDestinations.chat(advisorId = advId, advisorName = advName, apartmentId = apt.id.ifEmpty { propertyId }))
                    },
                    onChatAi = {
                        showAdvisorContactSheet = false
                        onNavigate(FutaDestinations.chat(apartmentId = apt.id.ifEmpty { propertyId }, isAi = true))
                    }
                )
            }
        }

        // =========================================================================
        // 1. VISIT BOOKING BOTTOM SHEET (Matching iOS)
        // =========================================================================
        if (showBookingSheet) {
            property?.let { apt ->
                FutaBottomSheet(
                    visible = true,
                    onDismiss = { showBookingSheet = false },
                    title = "Đặt lịch xem nhà thực tế"
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
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(apt["title"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text("Mã căn: ${apt["code"].string.ifEmpty { apt["propertyCode"].string }}", fontSize = 11.5.sp, color = FutaColors.BrandGreen)
                            }
                        }

                        Text("CHỌN NGÀY XEM", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Hôm nay", "Ngày mai", "Thứ Bảy", "Chủ Nhật").forEach { d ->
                                val isSelected = bookingDate == d
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                    modifier = Modifier.clickable { bookingDate = d }
                                ) {
                                    Text(d, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.White else FutaColors.Navy, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                                }
                            }
                        }

                        Text("KHUNG GIỜ THUẬN TIỆN", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Sáng (09:00 - 11:30)", "Chiều (14:00 - 16:30)", "Tối (18:00 - 20:00)").forEach { slot ->
                                val isSelected = bookingSlot == slot
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFFE8F5E9) else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth().clickable { bookingSlot = slot }
                                ) {
                                    Text(slot, fontSize = 12.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) FutaColors.BrandGreen else FutaColors.Navy, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                                }
                            }
                        }

                        Text("THÔNG TIN KHÁCH HÀNG", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        FutaInput(
                            value = bookingName,
                            onValueChange = { bookingName = it },
                            placeholder = "Họ và tên của bạn",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        FutaInput(
                            value = bookingPhone,
                            onValueChange = { bookingPhone = it },
                            placeholder = "Số điện thoại liên hệ",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
                        )

                        Spacer(Modifier.height(4.dp))
                        FutaButton(
                            text = "Xác nhận gửi yêu cầu",
                            variant = FutaButtonVariant.PRIMARY,
                            onClick = {
                                showBookingSheet = false
                                ToastCenter.show("Đã gửi lịch xem nhà! Chuyên viên FUTA sẽ gọi xác nhận trong 15 phút.")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        // =========================================================================
        // 2. HOLDING DEPOSIT BOTTOM SHEET (Matching iOS)
        // =========================================================================
        if (showHoldingSheet) {
            property?.let { apt ->
                FutaBottomSheet(
                    visible = true,
                    onDismiss = { showHoldingSheet = false },
                    title = "Giữ chỗ căn hộ FUTA Land"
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("TIỀN GIỮ CHỖ THƯỜNG NIÊN", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                    Text("50.000.000 VNĐ", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                                    Text("Hoàn 100% trong 24h nếu khách đổi ý không giao dịch", fontSize = 11.sp, color = Color(0xFF92400E))
                                }
                            }
                        }

                        Text("THÔNG TIN ĐỨNG TÊN HỢP ĐỒNG", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        FutaInput(
                            value = holdingName,
                            onValueChange = { holdingName = it },
                            placeholder = "Họ và tên người đứng tên cọc",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        FutaInput(
                            value = holdingPhone,
                            onValueChange = { holdingPhone = it },
                            placeholder = "Số điện thoại nhận hợp đồng điện tử",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                        )
                        FutaInput(
                            value = holdingCccd,
                            onValueChange = { holdingCccd = it },
                            placeholder = "Số CCCD / Hộ chiếu",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                        )

                        FutaButton(
                            text = if (holdingBusy) "Đang xử lý..." else "Xác nhận đặt cọc giữ chỗ",
                            variant = FutaButtonVariant.SECONDARY,
                            enabled = !holdingBusy,
                            onClick = {
                                scope.launch {
                                    holdingBusy = true
                                    try {
                                        val body = "{\"apartmentId\":\"${apt.id}\",\"customerName\":\"$holdingName\",\"customerPhone\":\"$holdingPhone\",\"amount\":50000000}"
                                        APIClient.get().request("/sales/holding", method = "POST", bodyJson = body)
                                    } catch (_: Exception) {}
                                    holdingBusy = false
                                    showHoldingSheet = false
                                    ToastCenter.show("Đã gửi yêu cầu giữ chỗ căn thành công! Bộ phận pháp chế sẽ gọi bàn giao thỏa thuận.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryInfoRow(iconRes: Int, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.5.sp, color = Color(0xFF64748B))
        }
        Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy)
    }
}

@Composable
private fun SpecItemCard(spec: Triple<Int, String, String>, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFEDF1F5)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFDF6EE),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = spec.first),
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                Text(
                    text = spec.third,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = spec.second,
                fontSize = 11.5.sp,
                color = FutaColors.Slate,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AmenityPill(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFEDF1F5)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Check, null, tint = Color(0xFF0E7643), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy, maxLines = 1)
        }
    }
}

@Composable
private fun MediaPlaceholderCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    url: String,
    badgeColor: Color,
    context: android.content.Context
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            if (url.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(buttonLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StickyContactBottomBar(
    apt: JSONValue,
    onCallClick: () -> Unit,
    onChatClick: () -> Unit,
    onHoldClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Price & Details on the LEFT
            Column(modifier = Modifier.weight(1f)) {
                val isSell = apt["listingType"].string.lowercase() != "rent"
                Text(
                    text = if (isSell) "Giá bán dự kiến" else "Giá thuê",
                    fontSize = 10.5.sp,
                    color = FutaColors.Slate
                )
                Text(
                    text = PropertyFormatters.listingPrice(apt),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF97316),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Action Buttons: Giữ chỗ + Gọi + Chat
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Giữ chỗ cọc căn hộ
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0E7643),
                    shadowElevation = 2.dp,
                    modifier = Modifier.clickable(onClick = onHoldClick)
                ) {
                    Text(
                        text = "Giữ chỗ ngay",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                // Quick phone icon
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF97316),
                    modifier = Modifier.size(38.dp).clickable(onClick = onCallClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.sf_btn_phone),
                            contentDescription = "Gọi ngay",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Quick chat icon
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0E7643),
                    modifier = Modifier.size(38.dp).clickable(onClick = onChatClick)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.sf_tab_chat_active),
                            contentDescription = "Chat tư vấn",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TownhouseFloorsCard(apt: JSONValue) {
    val landArea = apt["landArea"].string.ifEmpty { apt["townhouseSpecs"]["landArea"].string }
    val totalFloor = apt["totalFloorArea"].string.ifEmpty { apt["townhouseSpecs"]["totalFloorArea"].string }
    val block = apt["building"].string.ifEmpty { apt["townhouseSpecs"]["block"].string }.ifEmpty { "Chưa cập nhật" }
    val totalFloorsVal = when {
        apt["totalFloors"].int > 0 -> "${apt["totalFloors"].int}"
        apt["totalFloors"].string.isNotEmpty() -> apt["totalFloors"].string
        apt["townhouseSpecs"]["totalFloors"].int > 0 -> "${apt["townhouseSpecs"]["totalFloors"].int}"
        apt["townhouseSpecs"]["totalFloors"].string.isNotEmpty() -> apt["townhouseSpecs"]["totalFloors"].string
        else -> ""
    }
    val rawFloors = if (apt["floorAreas"].array.isNotEmpty()) apt["floorAreas"].array else apt["townhouseSpecs"]["floorAreas"].array

    FutaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "DIỆN TÍCH SÀN THEO TẦNG",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF97316),
                letterSpacing = 0.6.sp
            )
            if (landArea.isNotEmpty()) {
                TownhouseInfoRow("Diện tích đất", if (landArea.contains("m")) landArea else "$landArea m²")
            }
            if (totalFloor.isNotEmpty()) {
                TownhouseInfoRow("Tổng diện tích sàn", if (totalFloor.contains("m")) totalFloor else "$totalFloor m²")
            }
            TownhouseInfoRow("Block", block)
            if (totalFloorsVal.isNotEmpty()) {
                TownhouseInfoRow("Tổng số tầng", totalFloorsVal)
            } else if (rawFloors.isNotEmpty()) {
                TownhouseInfoRow("Tổng số tầng", "${rawFloors.size}")
            }

            if (rawFloors.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 4.dp))
                rawFloors.forEachIndexed { idx, item ->
                    val floorNum = when {
                        item["floor"].int > 0 -> "Tầng ${item["floor"].int}"
                        item["floor"].string.isNotEmpty() -> {
                            val f = item["floor"].string
                            if (f.lowercase().contains("tầng")) f else "Tầng $f"
                        }
                        else -> "Tầng ${idx + 1}"
                    }
                    val areaVal = when {
                        item["area_m2"].string.isNotEmpty() -> item["area_m2"].string
                        item["area_m2"].double > 0 -> String.format(java.util.Locale.US, "%.2f", item["area_m2"].double)
                        else -> ""
                    }
                    if (areaVal.isNotEmpty()) {
                        TownhouseInfoRow(floorNum, if (areaVal.contains("m")) areaVal else "$areaVal m²")
                    }
                }
            }
        }
    }
}

@Composable
private fun TownhouseInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = FutaColors.Slate)
        Text(text = value, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
    }
}

// =========================================================================
// ADVISOR CONTACT BOTTOM SHEET (Matching iOS AdvisorContactSheetView)
// =========================================================================
@Composable
private fun AdvisorContactBottomSheet(
    apt: JSONValue,
    onDismiss: () -> Unit,
    onCallAdvisor: (String) -> Unit,
    onChatAdvisor: (String, String) -> Unit,
    onChatAi: () -> Unit
) {
    val availableList = apt["availableAdvisors"].array
    val advisors = if (availableList.isNotEmpty()) {
        availableList.distinctBy { it["id"].string.ifEmpty { it["advisorId"].string } }
    } else {
        val advId = apt["advisorId"].string.ifEmpty { apt["advisor"]["id"].string.ifEmpty { apt["createdBy"]["id"].string } }
        val advName = apt["advisor"]["name"].string.ifEmpty { apt["createdBy"]["name"].string }.ifEmpty { apt["ownerName"].string.ifEmpty { "Chuyên viên tư vấn FUTA Land" } }
        val advPhone = apt["advisor"]["phone"].string.ifEmpty { apt["createdBy"]["phone"].string }.ifEmpty { apt["ownerPhone"].string.ifEmpty { "02838386852" } }
        val advAvatar = apt["advisor"]["avatar"].string.ifEmpty { apt["createdBy"]["avatar"].string }
        listOf(
            JSONValue.parse("""{"id":"$advId","name":"$advName","phone":"$advPhone","avatar":"$advAvatar"}""")
        )
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Tư vấn sản phẩm"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Apartment Summary Card
            val code = apt["propertyCode"].string.ifEmpty { apt["code"].string.ifEmpty { "Căn hộ" } }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mã: $code",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        if (apt["zone"].string.isNotEmpty()) {
                            Text(
                                text = apt["zone"].string,
                                fontSize = 12.sp,
                                color = FutaColors.BrandGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = PropertyFormatters.listingPrice(apt),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandOrange
                        )
                        val size = apt["size_m2"].string.toDoubleOrNull() ?: 0.0
                        if (size > 0) {
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", size)} m²",
                                fontSize = 13.sp,
                                color = FutaColors.Slate
                            )
                        }
                    }
                }
            }

            // 1. AI Consultation Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lucide_bot),
                                    contentDescription = "AI",
                                    tint = FutaColors.BrandGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Trợ lý AI FUTA Land",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Text(
                                        text = "24/7",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.BrandGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Hỏi đáp nhanh về pháp lý, giá, chính sách & ưu đãi căn hộ.",
                                fontSize = 12.sp,
                                color = FutaColors.Slate
                            )
                        }
                    }

                    Button(
                        onClick = onChatAi,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.sf_tab_chat_active),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Tư vấn với AI ngay",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 2. Advisor List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ĐỘI NGŨ CHUYÊN VIÊN PHỤ TRÁCH (${advisors.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF97316),
                    letterSpacing = 0.5.sp
                )

                advisors.forEach { adv ->
                    val name = adv["name"].string.ifEmpty { "Chuyên viên FUTA Land" }
                    val phone = adv["phone"].string.ifEmpty { "02838386852" }
                    val avatar = adv["avatar"].string
                    val advId = adv["id"].string.ifEmpty { adv["advisorId"].string }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.size(46.dp)
                            ) {
                                if (avatar.isNotEmpty()) {
                                    coil3.compose.AsyncImage(
                                        model = avatar,
                                        contentDescription = name,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = name.take(2).uppercase(),
                                            color = Color(0xFF0E7643),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                Text(
                                    text = phone,
                                    fontSize = 12.sp,
                                    color = FutaColors.Slate
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Call
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFFDF6EE),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable { onCallAdvisor(phone) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sf_btn_phone),
                                            contentDescription = "Gọi",
                                            tint = Color(0xFFF97316),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // Chat
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable { onChatAdvisor(advId, name) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.sf_btn_chat),
                                            contentDescription = "Chat",
                                            tint = Color(0xFF0E7643),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. FutaLand Hotline Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.sf_btn_phone),
                        contentDescription = null,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tổng đài FUTA Land",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        Text(
                            text = "028 3838 6852 · Hỗ trợ toàn diện",
                            fontSize = 11.5.sp,
                            color = FutaColors.Slate
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFDF6EE),
                        modifier = Modifier.clickable { onCallAdvisor("02838386852") }
                    ) {
                        Text(
                            text = "Gọi tổng đài",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF97316),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

