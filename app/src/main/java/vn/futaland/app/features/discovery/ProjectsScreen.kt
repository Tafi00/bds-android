package vn.futaland.app.features.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.PropertyFormatters
import vn.futaland.app.navigation.FutaDestinations

/**
 * Dedicated Public Projects Listing Screen matching Web /projects and iOS ProjectsView.
 */
@Composable
fun ProjectsScreen(
    initialLocation: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf(initialLocation.ifEmpty { "Tất cả" }) }
    var projects by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun loadProjects() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/projects")
                projects = res["data"].array.filter { !it["hidden"].bool }
            } catch (_: Exception) {
                projects = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadProjects()
    }

    val availableLocations = remember(projects) {
        val list = mutableListOf("Tất cả")
        val found = projects.map { it["province"].string }.filter { it.isNotEmpty() }.distinct().sorted()
        list.addAll(if (found.isEmpty()) listOf("Đà Nẵng", "TP. Hồ Chí Minh", "Khánh Hòa", "Bến Tre") else found)
        list
    }

    val filteredProjects = remember(projects, search, selectedLocation) {
        projects.filter { p ->
            val name = p["displayName"].string.ifEmpty { p["name"].string }
            val loc = p["location"].string.ifEmpty { p["address"].string }
            val prov = p["province"].string

            val matchesSearch = search.isEmpty() ||
                    name.contains(search, ignoreCase = true) ||
                    loc.contains(search, ignoreCase = true)

            val matchesLoc = selectedLocation == "Tất cả" ||
                    prov.contains(selectedLocation, ignoreCase = true) ||
                    loc.contains(selectedLocation, ignoreCase = true)

            matchesSearch && matchesLoc
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ArrowBack, null, tint = FutaColors.Navy)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Dự án FUTA Land",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    FutaInput(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "Tìm theo tên dự án, vị trí, phân khu…",
                        leadingIcon = Icons.Default.Search,
                        trailingIcon = if (search.isNotEmpty()) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Xóa",
                                    tint = FutaColors.Slate,
                                    modifier = Modifier.size(18.dp).clickable { search = "" }
                                )
                            }
                        } else null
                    )

                    Spacer(Modifier.height(10.dp))

                    // Location Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableLocations.forEach { loc ->
                            val isSelected = selectedLocation == loc
                            Surface(
                                onClick = { selectedLocation = loc },
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF8F9FA),
                                border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0xFFE2E8F0))
                            ) {
                                Text(
                                    text = loc,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else FutaColors.Navy,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (loading && projects.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(3) {
                    FutaSkeletonBlock(height = 240.dp, radius = 18.dp)
                }
            }
        } else if (filteredProjects.isEmpty()) {
            FutaEmptyState(
                title = "Không tìm thấy dự án",
                message = "Vui lòng thử tìm kiếm với từ khóa hoặc khu vực khác.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(filteredProjects, key = { idx, p -> (p.id.ifEmpty { "proj" }) + "-$idx" }) { _, project ->
                    PublicProjectCard(
                        project = project,
                        onClick = { onNavigate(FutaDestinations.projectDetail(project.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicProjectCard(
    project: JSONValue,
    onClick: () -> Unit
) {
    val title = project["displayName"].string.ifEmpty { project["name"].string }
    val banner = PropertyFormatters.resolveProjectBanner(project)
    val location = project["location"].string.ifEmpty { project["address"].string }
    val totalUnits = project["totalUnits"].int
    val developer = project["developer"].string.ifEmpty { "Tập đoàn Phương Trang (FUTA Group)" }
    val status = project["status"].string.ifEmpty { "Đang mở bán" }

    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.5.dp,
                shape = cardShape,
                ambientColor = Color(0x08000000),
                spotColor = Color(0x0C000000)
            )
            .clip(cardShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE8ECEF), cardShape)
            .clickable(onClick = onClick)
    ) {
        Column {
            // Image Section 16:9
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFFE2E8F0))
            ) {
                AsyncImage(
                    model = banner,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-left status badge
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
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
                            text = if (status == "selling") "Đang mở bán" else status,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Details Section
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = developer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FutaColors.Slate
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy
                )
                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.sf_mappin_circle_green),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = location,
                        fontSize = 12.5.sp,
                        color = FutaColors.Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(12.dp))

                // Footer with Units count and CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (totalUnits > 0) "$totalUnits sản phẩm" else "Quy mô lớn",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.BrandGreen
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Xem chi tiết",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FutaColors.Navy
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.sf_chevron_right_slate),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}
