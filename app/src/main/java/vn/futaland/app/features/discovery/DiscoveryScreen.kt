package vn.futaland.app.features.discovery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.compose.LocalPlatformContext
import vn.futaland.app.R
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.features.properties.FutaPropertyCard
import vn.futaland.app.features.properties.PropertyFormatters

@Composable
fun DiscoveryScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val loading by viewModel.loading.collectAsState()
    val selectedCity by viewModel.selectedCity.collectAsState()
    val selectedSegment by viewModel.selectedSegment.collectAsState()

    LaunchedEffect(Unit) {
        if (viewModel.projects.value.isEmpty()) {
            viewModel.loadData()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // 1. Top Branded Header
        item {
            TopBrandedHeader(
                selectedCity = selectedCity,
                availableCities = viewModel.availableCities,
                onSelectCity = { viewModel.selectCity(it) },
                onNotificationClick = { onNavigate(FutaDestinations.NOTIFICATIONS) }
            )
            Spacer(Modifier.height(20.dp))
        }

        // 2. Floating Search Bar
        item {
            FloatingSearchBar(
                selectedCity = selectedCity,
                onClick = { onNavigate(FutaDestinations.SEARCH) }
            )
            Spacer(Modifier.height(20.dp))
        }

        // 3. Quick Real Estate Actions (4 items)
        item {
            QuickActionsGrid(
                onNavigate = onNavigate
            )
            Spacer(Modifier.height(20.dp))
        }

        // 4. Hero Carousel
        item {
            val heroProjects = viewModel.heroProjects
            if (heroProjects.isNotEmpty()) {
                HeroCarouselSection(
                    projects = heroProjects,
                    onProjectClick = { onNavigate(FutaDestinations.projectDetail(it)) },
                    onViewAllProjects = { onNavigate(FutaDestinations.PROJECTS_LIST) }
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // 4B. Featured Secondary Projects Section (Matching iOS DiscoveryView)
        item {
            val secProjects = viewModel.secondaryProjects
            if (secProjects.isNotEmpty()) {
                FeaturedSecondaryProjectsSection(
                    projects = secProjects,
                    onProjectClick = { onNavigate(FutaDestinations.projectDetail(it)) },
                    onViewAll = { onNavigate(FutaDestinations.PROJECTS_LIST) }
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // 5. Featured Cities Cards
        item {
            FeaturedCitiesSection(
                cities = viewModel.featuredCityItems,
                onSelectCity = { viewModel.selectCity(it) },
                onViewMap = { onNavigate(FutaDestinations.PROJECTS_LIST) }
            )
            Spacer(Modifier.height(20.dp))
        }


        // 7. Segment Pills for Apartments
        item {
            ApartmentSegmentFilter(
                selectedSegment = selectedSegment,
                onSelect = { viewModel.selectSegment(it) },
                onViewAll = { onNavigate(FutaDestinations.SEARCH) }
            )
            Spacer(Modifier.height(16.dp))
        }

        // 7. Apartments List
        val apartments = viewModel.filteredApartments.take(8)
        if (loading && apartments.isEmpty()) {
            items(3) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    FutaSkeletonBlock(height = 280.dp, radius = 16.dp)
                }
            }
        } else if (apartments.isEmpty()) {
            item {
                FutaEmptyState(
                    title = "Chưa có sản phẩm phù hợp",
                    message = "Vui lòng chọn phân khúc hoặc khu vực khác."
                )
            }
        } else {
            itemsIndexed(apartments, key = { index, it -> (it.id.ifEmpty { "apt" }) + "-$index" }) { _, apt ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    FutaPropertyCard(
                        apartment = apt,
                        isFavorited = false,
                        onFavoriteClick = {
                            if (AppSession.shared.isAuthenticated) {
                                viewModel.toggleFavorite(apt.id)
                            } else {
                                onNavigate(FutaDestinations.AUTH)
                            }
                        },
                        onShareClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Xem bất động sản: ${PropertyFormatters.propertyTitle(apt)}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        },
                        onCallClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:02838386852"))
                            context.startActivity(intent)
                        },
                        onChatClick = { onNavigate(FutaDestinations.INBOX) },
                        onClick = { onNavigate(FutaDestinations.propertyDetail(apt.id)) }
                    )
                }
            }
        }

        // 9. 24/7 AI Hotline Card
        item {
            Spacer(Modifier.height(16.dp))
            AiHotlineCard(
                onChatClick = { onNavigate(FutaDestinations.INBOX) },
                onCallClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:02838386852"))
                    context.startActivity(intent)
                }
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TopBrandedHeader(
    selectedCity: String,
    availableCities: List<String>,
    onSelectCity: (String) -> Unit,
    onNotificationClick: () -> Unit
) {
    var showCitySheet by remember { mutableStateOf(false) }
    var citySearch by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.futaland_logo),
            contentDescription = "FUTA Land",
            modifier = Modifier.height(34.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.weight(1f))

        // Market Switcher Pill
        Surface(
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
            modifier = Modifier.clickable { showCitySheet = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCity == "Tất cả") "Toàn quốc" else selectedCity,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FutaColors.Navy
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.sf_chevron_down),
                    contentDescription = null,
                    modifier = Modifier.size(8.dp),
                    tint = Color.Unspecified
                )
            }
        }

        if (showCitySheet) {
            FutaBottomSheet(
                visible = true,
                onDismiss = { showCitySheet = false },
                title = "Chọn khu vực thị trường"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FutaInput(
                        value = citySearch,
                        onValueChange = { citySearch = it },
                        placeholder = "Tìm tỉnh, thành phố...",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    val filtered = availableCities.filter {
                        citySearch.isEmpty() || it.contains(citySearch, ignoreCase = true)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        filtered.forEach { city ->
                            val isSelected = (city == "Tất cả" && (selectedCity == "Tất cả" || selectedCity.isEmpty())) || city == selectedCity
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectCity(city)
                                        showCitySheet = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        null,
                                        tint = if (isSelected) FutaColors.BrandGreen else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (city == "Tất cả") "Toàn quốc (Tất cả khu vực)" else city,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) FutaColors.BrandGreen else FutaColors.Navy
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = FutaColors.BrandGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        Spacer(Modifier.width(10.dp))

        // Notification Bell
        Surface(
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onNotificationClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.sf_header_bell),
                    contentDescription = "Thông báo",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Unspecified
                )
                // Red unread badge only when user is authenticated
                if (AppSession.shared.isAuthenticated) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .background(Color(0xFFEF4444), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingSearchBar(
    selectedCity: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE8ECEF)),
        shadowElevation = 1.5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.sf_search_glass),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Tìm dự án, căn hộ, khu vực...",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = FutaColors.Navy
                )
                Text(
                    text = if (selectedCity == "Tất cả") "Đà Nẵng · TP.HCM · Hà Nội · Bến Tre" else "Đang xem thị trường $selectedCity",
                    fontSize = 11.sp,
                    color = FutaColors.Slate
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = FutaColors.BrandGreen.copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.sf_search_filter),
                        contentDescription = "Bộ lọc",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        listOf(
            Triple("Dự án FUTA", R.drawable.sf_quick_projects, FutaDestinations.PROJECTS_LIST),
            Triple("Căn hộ", R.drawable.sf_quick_apartment, FutaDestinations.search("can-ho-chung-cu")),
            Triple("Nhà phố", R.drawable.sf_quick_house, FutaDestinations.search("biet-thu-lien-ke")),
            Triple("Vòng quay", R.drawable.sf_quick_wheel, FutaDestinations.LUCKY_WHEEL)
        ).forEach { (title, iconRes, dest) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onNavigate(dest) }
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f)),
                    shadowElevation = 1.5.dp,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = title,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(if (title == "Căn hộ") 28.dp else 26.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeroCarouselSection(
    projects: List<JSONValue>,
    onProjectClick: (String) -> Unit,
    onViewAllProjects: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { projects.size })

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DỰ ÁN TÂM ĐIỂM FUTA",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Slate
            )
            Row(
                modifier = Modifier.clickable(onClick = onViewAllProjects),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Xem tất cả",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.BrandGreen
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.sf_chevron_right_green),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(9.dp)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp))
        ) { page ->
            val proj = projects[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onProjectClick(proj.id) }
            ) {
                val banner = proj["bannerImage"].string
                val displayImg = if (banner.isNotEmpty()) banner else proj["image"].string
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(displayImg)
                        .transformations(ProjectBannerTransformation())
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxSize().scale(1.18f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.35f to Color.Black.copy(alpha = 0.25f),
                                0.60f to Color.Black.copy(alpha = 0.85f),
                                0.85f to Color.Black.copy(alpha = 0.96f),
                                1.0f to Color.Black
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top row: Status on left, counter on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Đang mở bán",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (projects.size > 1) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "${page + 1}/${projects.size}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Bottom row: Title + Discount Tag + Location + CTA
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val dispName = proj["displayName"].string
                        val name = if (dispName.isNotEmpty()) dispName else proj["name"].string

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF97316).copy(alpha = 0.95f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.sf_tag_white),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(8.5.dp)
                                    )
                                    Spacer(Modifier.width(3.5.dp))
                                    Text(
                                        text = "CK 12%",
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        val loc = proj["location"].string
                        val address = if (loc.isNotEmpty()) loc else proj["address"].string

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.sf_mappin_circle_green),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = address,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Surface(
                                shape = CircleShape,
                                color = FutaColors.BrandGreen.copy(alpha = 0.92f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Xem dự án",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.sf_chevron_right_white),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun FeaturedSecondaryProjectsSection(
    projects: List<JSONValue>,
    onProjectClick: (String) -> Unit,
    onViewAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DỰ ÁN NỔI BẬT KHÁC",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Slate
            )
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Xem tất cả",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.BrandGreen
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.sf_chevron_right_green),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(projects, key = { idx, proj -> (proj.id.ifEmpty { "sec" }) + "-$idx" }) { _, proj ->
                val title = proj["displayName"].string.ifEmpty { proj["name"].string }
                val banner = PropertyFormatters.resolveProjectBanner(proj)
                val location = proj["location"].string.ifEmpty { proj["address"].string }
                val totalUnits = proj["totalUnits"].int
                FutaCard(
                    modifier = Modifier
                        .width(220.dp)
                        .height(232.dp)
                        .clickable { onProjectClick(proj.id) }
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(125.dp).background(Color(0xFFE2E8F0))) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(banner)
                                    .transformations(ProjectBannerTransformation())
                                    .build(),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Đang mở bán",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.height(34.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(painter = painterResource(id = R.drawable.sf_mappin_circle_green), null, tint = Color.Unspecified, modifier = Modifier.padding(top = 2.dp).size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = location,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = FutaColors.Slate,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (totalUnits > 0) {
                                    Text(
                                        text = "$totalUnits sản phẩm",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.BrandGreen
                                    )
                                } else {
                                    Text(
                                        text = "Quy mô lớn",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Slate
                                    )
                                }
                                Icon(
                                    painter = painterResource(id = R.drawable.sf_arrow_right_circle_green),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                }
            }
        }
    }
}
}
}

@Composable
private fun FeaturedCitiesSection(
    cities: List<FeaturedCity>,
    onSelectCity: (String) -> Unit,
    onViewMap: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KHU VỰC TRỌNG ĐIỂM",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Slate
            )
            Text(
                text = "Xem bản đồ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.BrandGreen,
                modifier = Modifier.clickable(onClick = onViewMap)
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cities) { city ->
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(175.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .clickable { onSelectCity(city.name) }
                ) {
                    AsyncImage(
                        model = city.image,
                        contentDescription = city.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.88f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.sf_chart_uptrend_white),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(9.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = city.growth,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = city.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${city.projectCount} dự án",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "•",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = city.averagePrice,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCD34D)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApartmentSegmentFilter(
    selectedSegment: HomePropertySegment,
    onSelect: (HomePropertySegment) -> Unit,
    onViewAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BẤT ĐỘNG SẢN ĐỀ XUẤT",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Slate
            )
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Xem tất cả",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.BrandGreen
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(id = R.drawable.sf_chevron_right_green),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple(HomePropertySegment.ALL, R.drawable.sf_chip_all_active, R.drawable.sf_chip_all_inactive),
                Triple(HomePropertySegment.APARTMENT, R.drawable.sf_chip_apt_active, R.drawable.sf_chip_apt_inactive),
                Triple(HomePropertySegment.TOWNHOUSE, R.drawable.sf_chip_house_active, R.drawable.sf_chip_house_inactive),
                Triple(HomePropertySegment.UNDER_3B, R.drawable.sf_chip_tag_active, R.drawable.sf_chip_tag_inactive),
                Triple(HomePropertySegment.SELLING, R.drawable.sf_chip_sparkles_active, R.drawable.sf_chip_sparkles_inactive)
            ).forEach { (seg, activeIcon, inactiveIcon) ->
                val isSelected = seg == selectedSegment
                Surface(
                    onClick = { onSelect(seg) },
                    shape = CircleShape,
                    color = if (isSelected) FutaColors.BrandGreen else Color.White,
                    border = BorderStroke(
                        if (isSelected) 1.dp else 1.5.dp,
                        if (isSelected) FutaColors.BrandGreen else Color(0xFFE1D9CB)
                    ),
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSelected) activeIcon else inactiveIcon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = seg.title,
                            color = if (isSelected) Color.White else FutaColors.Navy,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiHotlineCard(
    onChatClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = FutaColors.BrandGreen
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Chat, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trợ lý ảo FUTA AI", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = CircleShape, color = Color.White) {
                            Text("24/7", color = FutaColors.BrandGreen, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text("Tư vấn chọn căn, tính dòng tiền và chính sách ngân hàng", color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(
                    text = "Chat tư vấn ngay",
                    variant = FutaButtonVariant.OUTLINE,
                    onClick = onChatClick,
                    modifier = Modifier.weight(1f)
                )
                FutaButton(
                    text = "Hotline",
                    variant = FutaButtonVariant.CREAM,
                    onClick = onCallClick
                )
            }
        }
    }
}

