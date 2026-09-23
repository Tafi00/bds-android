package vn.futaland.app.features.properties

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

/**
 * Public advisor profile — deep link target for /listing/agents/{id}.
 * Mirrors iOS AgentDetailView: header, call/Zalo actions, bio, active listings.
 */
@Composable
fun AgentDetailScreen(
    agentId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var agent by remember { mutableStateOf<JSONValue?>(null) }
    var listings by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            failed = false
            try {
                val res = APIClient.get().request("/agents/${Uri.encode(agentId)}")
                agent = if (res["data"].isNull) res else res["data"]
                val aptRes = APIClient.get().request("/agents/${Uri.encode(agentId)}/apartments")
                listings = aptRes["data"].array
            } catch (_: Exception) {
                failed = agent == null
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(agentId) { load() }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = agent?.get("name")?.string?.ifEmpty { "Chuyên viên tư vấn" } ?: "Chuyên viên tư vấn",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        when {
            loading && agent == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FutaSkeletonBlock(height = 120.dp, radius = 16.dp)
                FutaSkeletonBlock(height = 90.dp, radius = 16.dp)
                FutaSkeletonBlock(height = 200.dp, radius = 16.dp)
            }
            failed || agent == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FutaEmptyState(
                        title = "Không tải được hồ sơ",
                        message = "Chuyên viên này không còn khả dụng hoặc kết nối bị gián đoạn."
                    )
                    FutaButton(text = "Thử lại", variant = FutaButtonVariant.OUTLINE, onClick = { load() })
                }
            }
            else -> {
                val a = agent!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header card
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                val avatarUrl = a["avatar"].string
                                if (avatarUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF1F5F9))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(FutaColors.MintBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = a["name"].string.take(1).uppercase().ifEmpty { "F" },
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.BrandGreen
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = a["name"].string.ifEmpty { "Chuyên viên FUTA" },
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.Navy
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = a["title"].string.ifEmpty { "Chuyên viên tư vấn BĐS" },
                                        fontSize = 12.sp,
                                        color = FutaColors.Slate
                                    )
                                    if (a["slogan"].string.isNotEmpty()) {
                                        Text(
                                            text = "\"${a["slogan"].string}\"",
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = FutaColors.BrandGreen
                                        )
                                    }
                                    if (a["rating"].double > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("%.1f".format(a["rating"].double), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                            if (a["experienceYears"].int > 0) {
                                                Text(" • ${a["experienceYears"].int} năm kinh nghiệm", fontSize = 11.sp, color = FutaColors.Slate)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Call / Zalo actions
                    item {
                        val phone = a["publicPhone"].string.ifEmpty { a["phone"].string }
                        val zalo = a["zaloPhone"].string.ifEmpty { phone }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            if (phone.isNotEmpty()) {
                                FutaButton(
                                    text = "Gọi $phone",
                                    variant = FutaButtonVariant.PRIMARY,
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (zalo.isNotEmpty()) {
                                FutaButton(
                                    text = "Zalo",
                                    variant = FutaButtonVariant.SECONDARY,
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://zalo.me/$zalo")))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Bio
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Giới thiệu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text(
                                    text = a["bio"].string.ifEmpty {
                                        "Chuyên viên tư vấn bất động sản tại hệ thống FUTA Land, sẵn sàng hỗ trợ khách hàng tìm kiếm tổ ấm và cơ hội đầu tư phù hợp nhất."
                                    },
                                    fontSize = 13.sp,
                                    color = FutaColors.Slate,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }

                    // Active listings
                    item {
                        Text(
                            "Bất động sản đang phân phối (${listings.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                    }
                    if (listings.isEmpty()) {
                        item {
                            Text(
                                "Hiện chưa có sản phẩm công khai nào.",
                                fontSize = 12.sp,
                                color = FutaColors.Slate
                            )
                        }
                    } else {
                        itemsIndexed(listings, key = { idx, item -> item.id.ifEmpty { "agent-prop-$idx" } }) { _, property ->
                            FutaPropertyCard(
                                property = property,
                                isFavorited = false,
                                onFavoriteClick = {},
                                onShareClick = {},
                                onCallClick = {
                                    val phone = a["publicPhone"].string.ifEmpty { a["phone"].string }
                                    if (phone.isNotEmpty()) {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                    }
                                },
                                onChatClick = { onNavigate(FutaDestinations.INBOX) },
                                onClick = { onNavigate(FutaDestinations.propertyDetail(property.id)) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}
