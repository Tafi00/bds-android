package vn.futaland.app.features.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import vn.futaland.app.R
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.features.properties.PropertyFormatters
import kotlin.math.roundToInt

data class AndroidProjectPin(
    val id: String,
    val name: String,
    val address: String,
    val city: String,
    val lat: Double,
    val lng: Double,
    val totalUnits: Int,
    val bannerUrl: String,
    val raw: JSONValue
)

@Composable
fun ProjectMapScreen(
    onBack: () -> Unit,
    onProjectClick: (String) -> Unit
) {
    var projects by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<AndroidProjectPin?>(null) }
    var selectedCity by remember { mutableStateOf("Tất cả") }

    // Map gesture state (pan & zoom)
    var zoom by remember { mutableFloatStateOf(1.2f) }
    var panOffset by remember { mutableStateOf(Offset(0f, 0f)) }

    fun resolveGps(proj: JSONValue, name: String): Pair<Double, Double> {
        var lat = proj["latitude"].double
        var lng = proj["longitude"].double
        if (lat != 0.0 && lng != 0.0) return lat to lng

        val n = name.lowercase()
        val c = proj["code"].string.lowercase()
        return when {
            n.contains("times square") || c.contains("time") -> 16.0678 to 108.2435
            n.contains("c5b") || c.contains("c5b") -> 16.0592 to 108.1498
            n.contains("mũi né") || n.contains("hilton") || n.contains("phan thiết") -> 10.9333 to 108.2833
            n.contains("vinhomes central") || n.contains("landmark") -> 10.7937 to 106.7218
            n.contains("masteri") || n.contains("thảo điền") -> 10.8035 to 106.7431
            n.contains("đà nẵng") -> 16.0544 to 108.2022
            n.contains("hồ chí minh") || n.contains("sài gòn") -> 10.7769 to 106.7009
            else -> 16.0544 to 108.2022
        }
    }

    val allPins = remember(projects) {
        projects.mapNotNull { proj ->
            val name = proj["displayName"].string.ifEmpty { proj["name"].string }
            if (name.isEmpty()) return@mapNotNull null

            val address = proj["address"].string.ifEmpty { proj["location"].string }.ifEmpty { "Vị trí dự án FUTA Land" }
            val province = proj["province"].string
            val city = province.ifEmpty { if (address.contains("Đà Nẵng")) "Đà Nẵng" else "TP. Hồ Chí Minh" }
            val (lat, lng) = resolveGps(proj, name)
            val units = proj["totalUnits"].int.takeIf { it > 0 } ?: 62
            val banner = PropertyFormatters.resolveProjectBanner(proj)

            AndroidProjectPin(
                id = proj.id,
                name = name,
                address = address,
                city = city,
                lat = lat,
                lng = lng,
                totalUnits = units,
                bannerUrl = banner,
                raw = proj
            )
        }
    }

    val availableCities = remember(allPins) {
        listOf("Tất cả") + allPins.map { it.city }.distinct().sorted()
    }

    val filteredPins = remember(allPins, selectedCity) {
        if (selectedCity == "Tất cả") allPins else allPins.filter { it.city.contains(selectedCity, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        try {
            val res = APIClient.get().request("/projects")
            val list = res["data"].array.filter { !it["hidden"].bool }
            projects = list
        } catch (_: Exception) {}
    }

    LaunchedEffect(filteredPins) {
        if (selectedProject == null && filteredPins.isNotEmpty()) {
            selectedProject = filteredPins.first()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8ECEF))
    ) {
        // =========================================================================
        // 1. Native Interactive Map Canvas (Vietnam Terrain + Coastline + Roads)
        // =========================================================================
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.7f, 4.0f)
                        panOffset += pan
                    }
                }
        ) {
            val screenW = maxWidth.value
            val screenH = maxHeight.value
            val centerX = screenW / 2f
            val centerY = screenH / 2f

            // Vietnam projection parameters: lat ~8.5..23.5, lng ~102..110
            // Reference anchor: Da Nang (16.0544, 108.2022) placed at center
            val refLat = 16.0544
            val refLng = 108.2022
            val scaleFactor = 110f * zoom

            fun projectToScreen(lat: Double, lng: Double): Offset {
                val dx = (lng - refLng).toFloat() * scaleFactor
                val dy = -(lat - refLat).toFloat() * scaleFactor * 1.08f // Mercator correction
                val x = (centerX + dx) + panOffset.x
                val y = (centerY + dy) + panOffset.y
                return Offset(x, y)
            }

            // Draw Background Map Vector Grid & Landmarks
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Map Water & Land tones
                drawRect(Color(0xFFEBF2F7))

                // Subtle Grid lines
                val gridStep = 60f * zoom
                var gx = (panOffset.x % gridStep)
                while (gx < w) {
                    drawLine(Color(0xFFDEE5EC), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                    gx += gridStep
                }
                var gy = (panOffset.y % gridStep)
                while (gy < h) {
                    drawLine(Color(0xFFDEE5EC), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                    gy += gridStep
                }

                // Vietnam Coastal Curve (Approximate geographic ribbon)
                val coastalPath = Path().apply {
                    val pNorth = projectToScreen(21.03, 105.85) // Hanoi
                    val pHue = projectToScreen(16.46, 107.59)   // Hue
                    val pDanang = projectToScreen(16.05, 108.20)// Da Nang
                    val pNhaTrang = projectToScreen(12.24, 109.19)// Nha Trang
                    val pPhanThiet = projectToScreen(10.93, 108.28)// Phan Thiet
                    val pHcm = projectToScreen(10.78, 106.70)   // HCMC
                    val pCaMau = projectToScreen(9.18, 105.15)  // Ca Mau

                    moveTo(pNorth.x, pNorth.y)
                    quadraticBezierTo(pNorth.x + 30f, pNorth.y + 80f, pHue.x, pHue.y)
                    quadraticBezierTo(pHue.x + 20f, pHue.y + 40f, pDanang.x, pDanang.y)
                    quadraticBezierTo(pDanang.x + 40f, pDanang.y + 120f, pNhaTrang.x, pNhaTrang.y)
                    quadraticBezierTo(pNhaTrang.x - 20f, pNhaTrang.y + 60f, pPhanThiet.x, pPhanThiet.y)
                    quadraticBezierTo(pPhanThiet.x - 30f, pPhanThiet.y + 30f, pHcm.x, pHcm.y)
                    quadraticBezierTo(pHcm.x - 40f, pHcm.y + 50f, pCaMau.x, pCaMau.y)
                }

                drawPath(coastalPath, color = Color(0xFFD4E2EC), style = Stroke(width = 24f * zoom))
                drawPath(coastalPath, color = Color(0xFFC0D5E5), style = Stroke(width = 3f))
            }

            // Pinned Logo Markers for each Project
            filteredPins.forEach { pin ->
                val pos = projectToScreen(pin.lat, pin.lng)
                val isSelected = selectedProject?.id == pin.id

                Box(
                    modifier = Modifier
                        .offset { IntOffset(pos.x.roundToInt() - 60, pos.y.roundToInt() - 65) }
                        .width(130.dp)
                        .clickable {
                            selectedProject = pin
                            // Smoothly center the map on tapped pin
                            panOffset = Offset(
                                -(pin.lng - refLng).toFloat() * scaleFactor,
                                (pin.lat - refLat).toFloat() * scaleFactor * 1.08f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Project Name Chip
                        Surface(
                            shape = CapsuleShape,
                            color = if (isSelected) FutaColors.BrandGreen else Color.White,
                            shadowElevation = 3.dp,
                            border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = pin.name,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF0F172A),
                                    maxLines = 1
                                )
                                Text(
                                    text = "• ${pin.totalUnits} sp",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else FutaColors.BrandGreen
                                )
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        // Branded Circular FUTA Logo Pin Bubble
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 46.dp else 38.dp)
                                .shadow(if (isSelected) 8.dp else 4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(
                                    width = if (isSelected) 2.5.dp else 2.dp,
                                    color = if (isSelected) Color(0xFFF97316) else FutaColors.BrandGreen,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.futaland_logo),
                                contentDescription = pin.name,
                                modifier = Modifier
                                    .size(if (isSelected) 28.dp else 24.dp)
                                    .padding(2.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Pointer tip
                        Canvas(modifier = Modifier.size(width = 10.dp, height = 5.dp)) {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(path, color = if (isSelected) Color(0xFFF97316) else FutaColors.BrandGreen)
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. Top Header & City Filter Chips (Sticky at top)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "Bản đồ dự án FUTA",
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(FutaColors.BrandGreen)
                        )
                        Text(
                            text = "${filteredPins.size} dự án",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // City Filter Chips
            if (availableCities.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCities.forEach { city ->
                        val isSelected = selectedCity == city
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) FutaColors.BrandGreen else Color.White.copy(alpha = 0.96f),
                            shadowElevation = 2.dp,
                            modifier = Modifier.clickable {
                                selectedCity = city
                                if (city != "Tất cả") {
                                    filteredPins.firstOrNull()?.let {
                                        selectedProject = it
                                        panOffset = Offset(
                                            -(it.lng - 108.2022).toFloat() * (110f * zoom),
                                            (it.lat - 16.0544).toFloat() * (110f * zoom) * 1.08f
                                        )
                                    }
                                } else {
                                    panOffset = Offset(0f, 0f)
                                    zoom = 1.2f
                                }
                            }
                        ) {
                            Text(
                                text = city,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF1E293B),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. Zoom Controls (+ / - buttons on middle-right)
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { zoom = (zoom * 1.3f).coerceAtMost(4.0f) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, "Phóng to", tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { zoom = (zoom / 1.3f).coerceAtLeast(0.7f) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, "Thu nhỏ", tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                }
            }
        }

        // =========================================================================
        // 4. Floating Bottom Project Card (When Pin Selected)
        // =========================================================================
        AnimatedVisibility(
            visible = selectedProject != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            selectedProject?.let { pin ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProjectClick(pin.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = pin.bannerUrl,
                            contentDescription = pin.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(82.dp)
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pin.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Đóng",
                                    tint = FutaColors.Slate,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { selectedProject = null }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = FutaColors.BrandGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = pin.address,
                                    fontSize = 12.sp,
                                    color = FutaColors.Slate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${pin.totalUnits} sản phẩm",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.BrandGreen
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = FutaColors.BrandGreen,
                                    modifier = Modifier.clickable { onProjectClick(pin.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Xem chi tiết",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp)
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
}

val CapsuleShape = RoundedCornerShape(percent = 50)
