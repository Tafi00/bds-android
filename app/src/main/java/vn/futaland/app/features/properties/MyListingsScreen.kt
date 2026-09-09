package vn.futaland.app.features.properties

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

@Composable
fun MyListingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("all") }
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/apartments", query = mapOf("limit" to "20"))
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

    val tabs = listOf(
        "all" to "Tất cả (${items.size})",
        "visible" to "Đang hiển thị (${items.count { it["status"].string != "sold" }})",
        "pending" to "Chờ duyệt (0)",
        "expired" to "Hết hạn (0)"
    )

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                        }
                        Text(
                            text = "Tin đăng của tôi",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { ToastCenter.show("Tạo tin đăng BĐS mới") }) {
                            Surface(shape = CircleShape, color = FutaColors.BrandGreen, modifier = Modifier.size(28.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, "Đăng tin", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // 4 Tabs matching iOS MyListingsView
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEach { (tabKey, tabLabel) ->
                            val isSelected = selectedTab == tabKey
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { selectedTab = tabKey }
                            ) {
                                Text(
                                    text = tabLabel,
                                    fontSize = 12.sp,
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
        if (loading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { FutaSkeletonBlock(height = 130.dp, radius = 16.dp) }
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                FutaEmptyState(title = "Chưa có tin đăng", message = "Bạn chưa đăng bất động sản nào. Bấm nút '+' góc trên để đăng tin mới.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(items, key = { idx, item -> item.id.ifEmpty { "my-apt-$idx" } }) { _, apt ->
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(apt["title"].string, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                Surface(shape = CircleShape, color = Color(0xFFE8F5E9)) {
                                    Text("Đang hiển thị", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            Text("Mã: ${apt["code"].string.ifEmpty { apt["propertyCode"].string }} · ${apt["zone"].string}", fontSize = 12.sp, color = FutaColors.Slate)
                            Text(PropertyFormatters.formatPrice(apt["price"].double), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFFF97316))

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { ToastCenter.show("Đã đẩy tin đăng lên Top!") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.RocketLaunch, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Đẩy tin Top", fontSize = 11.5.sp, color = FutaColors.BrandOrange, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onNavigate(FutaDestinations.propertyDetail(apt.id)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Xem chi tiết", fontSize = 11.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
