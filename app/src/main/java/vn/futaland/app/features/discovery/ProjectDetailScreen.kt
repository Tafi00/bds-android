package vn.futaland.app.features.discovery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.properties.FutaPropertyCard
import vn.futaland.app.features.properties.PropertyFormatters
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

    LaunchedEffect(projectId) {
        scope.launch {
            loading = true
            try {
                val pRes = APIClient.get().request("/projects/$projectId")
                project = pRes["data"]
                val aRes = APIClient.get().request("/apartments", query = mapOf("projectId" to projectId, "limit" to "12"))
                apartments = aRes["data"].array
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
            val location = p["location"].string.ifEmpty { p["address"].string }
            val developer = p["developer"].string.ifEmpty { "Tập đoàn Phương Trang (FUTA Group)" }
            val totalUnits = p["totalUnits"].int
            val desc = p["description"].string.ifEmpty { p["overview"].string }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FutaColors.PageBg)
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. Hero Banner Image
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
                            model = banner,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 2. Project Info Card
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

                // 4. Linked Apartments Section
                if (apartments.isNotEmpty()) {
                    item {
                        Text(
                            text = "GIỎ HÀNG THUỘC DỰ ÁN (${apartments.size} SẢN PHẨM)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Slate,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    itemsIndexed(apartments, key = { idx, item -> (item.id.ifEmpty { "proj-apt" }) + "-$idx" }) { _, apt ->
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
        }
    }
}
