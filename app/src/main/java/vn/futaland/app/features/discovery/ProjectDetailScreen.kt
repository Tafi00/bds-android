package vn.futaland.app.features.discovery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.FutaPropertyCard
import vn.futaland.app.navigation.FutaDestinations

@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<JSONValue?>(null) }
    var apartments by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedSubNav by remember { mutableStateOf("overview") }
    var showZoomPlan by remember { mutableStateOf(false) }
    var inventoryFilterBed by remember { mutableStateOf("all") }
    val listState = rememberLazyListState()

    LaunchedEffect(projectId) {
        scope.launch {
            loading = true
            try {
                val pRes = APIClient.get().request("/projects/$projectId")
                project = pRes["data"]
                val aptRes = APIClient.get().request("/apartments", query = mapOf("projectId" to projectId, "limit" to "50"))
                apartments = aptRes["data"].array
            } catch (_: Exception) {
            } finally {
                loading = false
            }
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
                        Icon(Icons.Default.ArrowBack, null, tint = FutaColors.Navy)
                    }
                    Text(
                        text = project?.get("displayName")?.string?.ifEmpty { project?.get("name")?.string } ?: "Chi tiết dự án",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Xem dự án: ${project?.get("name")?.string}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }) {
                        Icon(Icons.Default.Share, null, tint = FutaColors.Navy)
                    }
                }
            }
        },
        bottomBar = {
            FutaStickyActionBar {
                FutaButton(
                    text = "Hotline",
                    variant = FutaButtonVariant.CREAM,
                    icon = Icons.Default.Phone,
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:02838386852"))
                        context.startActivity(intent)
                    }
                )
                FutaButton(
                    text = "TƯ VẤN DỰ ÁN",
                    variant = FutaButtonVariant.PRIMARY,
                    icon = Icons.Default.Chat,
                    onClick = { onNavigate(FutaDestinations.INBOX) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        if (loading || project == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FutaSkeletonBlock(height = 220.dp, radius = 18.dp)
                FutaSkeletonBlock(height = 24.dp, width = 220.dp)
                FutaSkeletonBlock(height = 80.dp, radius = 14.dp)
            }
        } else {
            val p = project!!
            val title = p["displayName"].string.ifEmpty { p["name"].string }
            val banner = p["bannerImage"].string.ifEmpty { p["image"].string }
            val location = p["address"].string.ifEmpty { p["location"].string }.ifEmpty { p["province"].string }
            val developer = p["developer"].string
            val totalUnits = p["totalUnits"].int
            val desc = p["description"].string.ifEmpty { p["overview"].string }
            val listState = rememberLazyListState()
            val filteredApartments = remember(apartments, inventoryFilterBed) {
                if (inventoryFilterBed == "all") apartments
                else apartments.filter { it["bedrooms"].int.toString() == inventoryFilterBed || it["bedroomCount"].int.toString() == inventoryFilterBed }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(FutaColors.PageBg)
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(230.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFE2E8F0))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data(banner)
                                .transformations(ProjectBannerTransformation())
                                .build(),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Sticky Sub-Nav Tabs (Matching iOS ProjectsView)
                item {
                    val tabs = listOf(
                        "overview" to "Tổng quan",
                        "masterplan" to "Mặt bằng",
                        "inventory" to "Bảng hàng",
                        "amenities" to "Tiện ích"
                    )
                    Surface(
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tabs.forEach { (tabKey, tabLabel) ->
                                val isSelected = selectedSubNav == tabKey
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF8FAFC),
                                    modifier = Modifier.clickable {
                                        selectedSubNav = tabKey
                                        scope.launch {
                                            val targetIdx = when (tabKey) {
                                                "overview" -> 1
                                                "masterplan" -> 3
                                                "inventory" -> 5
                                                "amenities" -> 6
                                                else -> 0
                                            }
                                            listState.animateScrollToItem(targetIdx)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = tabLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else FutaColors.Navy,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    FutaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FutaStatusBadge(title = p["status"].string.ifEmpty { "Đang mở bán" })
                                Text(developer, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(location, fontSize = 12.5.sp, color = FutaColors.Slate)
                            }

                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = FutaColors.RowDivider)
                            Spacer(Modifier.height(14.dp))

                            // Key Metrics
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Quy mô", fontSize = 11.5.sp, color = FutaColors.Slate)
                                    Text(if (totalUnits > 0) "$totalUnits căn" else "Nhiều phân khu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Loại hình", fontSize = 11.5.sp, color = FutaColors.Slate)
                                    Text(p["projectType"].string.ifEmpty { "Căn hộ & Liền kề" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Pháp lý", fontSize = 11.5.sp, color = FutaColors.Slate)
                                    Text("Sổ hồng lâu dài", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 3. Description Section
                if (desc.isNotEmpty()) {
                    item {
                        FutaCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("GIỚI THIỆU DỰ ÁN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Spacer(Modifier.height(8.dp))
                                Text(desc, fontSize = 13.sp, color = FutaColors.Body, lineHeight = 19.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                // 4. Master Plan / Sơ đồ tổng thể
                item {
                    FutaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("MẶT BẰNG TỔNG THỂ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                TextButton(onClick = { showZoomPlan = true }) {
                                    Text("Phóng to", fontSize = 12.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .clickable { showZoomPlan = true }
                            ) {
                                AsyncImage(
                                    model = p["masterPlanUrl"].string.ifEmpty { banner },
                                    contentDescription = "Mặt bằng",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 5. Amenities Section (Tiện ích chuẩn 5 sao)
                item {
                    FutaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("TIỆN ÍCH DỰ ÁN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            Spacer(Modifier.height(12.dp))
                            val amenities = listOf(
                                "Bể bơi vô cực", "Công viên cây xanh", "Trung tâm thương mại",
                                "Phòng Gym & Yoga", "Nhà trẻ quốc tế", "An ninh 24/7"
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    amenities.take(3).forEach { a ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(a, fontSize = 12.5.sp, color = FutaColors.Navy)
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    amenities.drop(3).forEach { a ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(a, fontSize = 12.5.sp, color = FutaColors.Navy)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 6. Linked Apartments Section with Filter
                if (filteredApartments.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BẢNG HÀNG (${filteredApartments.size} CĂN)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Slate
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("all" to "Tất cả", "1" to "1 PN", "2" to "2 PN", "3" to "3 PN").forEach { (bedKey, bedLabel) ->
                                        val isSelected = inventoryFilterBed == bedKey
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                            modifier = Modifier.clickable { inventoryFilterBed = bedKey }
                                        ) {
                                            Text(
                                                text = bedLabel,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else FutaColors.Navy,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    itemsIndexed(filteredApartments, key = { idx, item -> (item.id.ifEmpty { "proj-apt" }) + "-$idx" }) { _, apt ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            FutaPropertyCard(
                                apartment = apt,
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
            }

            // Full-screen Zoom Plan Dialog
            if (showZoomPlan) {
                Dialog(onDismissRequest = { showZoomPlan = false }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black,
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = p["masterPlanUrl"].string.ifEmpty { banner },
                                contentDescription = "Mặt bằng phóng to",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { showZoomPlan = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
