package vn.futaland.app.features.properties

import androidx.compose.animation.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingApartment by remember { mutableStateOf<JSONValue?>(null) }
    var deletingApartment by remember { mutableStateOf<JSONValue?>(null) }
    var boostingId by remember { mutableStateOf<String?>(null) }

    fun loadData(searchQuery: String = search) {
        scope.launch {
            loading = true
            try {
                val q = searchQuery.trim()
                var url = "/apartments?mine=true&limit=100"
                if (q.isNotEmpty()) {
                    url += "&q=${java.net.URLEncoder.encode(q, "UTF-8")}"
                }
                val res = APIClient.get().request(url)
                var list = res["data"].array
                // Fallback to managed listings if user has zero personal listings yet
                if (list.isEmpty()) {
                    val fallbackUrl = if (q.isNotEmpty()) "/apartments?limit=100&q=${java.net.URLEncoder.encode(q, "UTF-8")}" else "/apartments?limit=100"
                    val fallbackRes = APIClient.get().request(fallbackUrl)
                    list = fallbackRes["data"].array
                }
                items = list
            } catch (_: Exception) {
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(search) {
        kotlinx.coroutines.delay(300)
        loadData(search)
    }

    val filteredItems = remember(items, selectedTab) {
        items.filter { apt ->
            val statuses = if (apt["status"].array.isNotEmpty()) {
                apt["status"].array.map { it.string.lowercase() }
            } else {
                listOf(apt["status"].string.lowercase())
            }

            when (selectedTab) {
                "visible" -> statuses.any { it.contains("mở bán") || it.contains("available") || it.contains("published") || it.contains("hiển thị") }
                "pending" -> statuses.any { it.contains("chờ duyệt") || it.contains("pending") || it.contains("review") || it.contains("draft") }
                "expired" -> statuses.any { it.contains("hết hạn") || it.contains("expired") || it.contains("inactive") || it.contains("đã bán") || it.contains("đã cho thuê") }
                else -> true
            }
        }
    }

    val tabs = listOf(
        "all" to "Tất cả (${items.size})",
        "visible" to "Đang hiển thị",
        "pending" to "Chờ duyệt",
        "expired" to "Hết hạn"
    )

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Surface(
                color = FutaColors.PageBg,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FutaHeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        onClick = onBack
                    )

                    Text(
                        text = "Tin đăng của tôi",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    FutaHeaderIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Đăng tin",
                        tint = FutaColors.BrandGreen,
                        onClick = { showCreateSheet = true }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Pinned Search Bar (API Debounced) matching Web
            item {
                FutaInput(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Tìm theo tiêu đề, mã căn, vị trí…",
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Xóa",
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { search = "" },
                                tint = FutaColors.Slate
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Status Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEach { (tabKey, tabLabel) ->
                        val isSelected = selectedTab == tabKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { selectedTab = tabKey }
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else FutaColors.Navy,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Listings Cards List with Skeletons
            if (loading && items.isEmpty()) {
                items(4) {
                    FutaSkeletonBlock(height = 140.dp, radius = 16.dp)
                }
            } else if (filteredItems.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Chưa có tin đăng",
                        message = "Bấm nút '+' góc trên để tạo tin đăng mới."
                    )
                }
            } else {
                itemsIndexed(filteredItems, key = { idx, item -> item.id.ifEmpty { "my-apt-$idx" } }) { _, apt ->
                    val title = PropertyFormatters.propertyTitle(apt)
                    val imgUrl = PropertyFormatters.resolveImage(apt)
                    val code = apt["propertyCode"].string.ifEmpty { apt["code"].string }
                    val zone = apt["zone"].string.ifEmpty { apt["project"]["displayName"].string }
                    val priceStr = PropertyFormatters.listingPrice(apt)
                    val rawStatus = apt["status"].array.firstOrNull()?.string ?: apt["status"].string
                    val status = rawStatus.ifEmpty { "Đang hiển thị" }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            onNavigate(FutaDestinations.propertyDetail(apt.id))
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Thumbnail Image
                                Box(
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 76.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE2E8F0))
                                ) {
                                    if (imgUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = imgUrl,
                                            contentDescription = title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Apartment,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                // Details
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Navy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = priceStr,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.BrandGreen
                                    )
                                    Text(
                                        text = "$code · $zone",
                                        fontSize = 11.5.sp,
                                        color = FutaColors.Slate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(shape = CircleShape, color = Color(0xFFEAF5EF)) {
                                        Text(
                                            text = status,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FutaColors.BrandGreen,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // Actions row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            boostingId = apt.id
                                            try {
                                                APIClient.get().request("/apartments/${apt.id}/boost", method = "POST")
                                                ToastCenter.show("Đã đẩy tin lên đầu danh sách!")
                                                loadData(search)
                                            } catch (e: Exception) {
                                                ToastCenter.show("Lỗi đẩy tin: ${e.message}", isError = true)
                                            } finally {
                                                boostingId = null
                                            }
                                        }
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = if (boostingId == apt.id) "Đang đẩy tin..." else "Đẩy tin Top",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.BrandOrange
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(
                                        modifier = Modifier.clickable { editingApartment = apt },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                                        Text("Sửa", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                    }

                                    Row(
                                        modifier = Modifier.clickable { deletingApartment = apt },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                                        Text("Xóa", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Dialog xác nhận xóa tin đăng
    if (deletingApartment != null) {
        val aptToDelete = deletingApartment!!
        FutaDialog(
            visible = true,
            title = "Xóa tin đăng?",
            confirmText = "Xóa ngay",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                scope.launch {
                    try {
                        APIClient.get().request("/apartments/${aptToDelete.id}", method = "DELETE")
                        ToastCenter.show("Đã xóa tin đăng thành công")
                        deletingApartment = null
                        loadData(search)
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi xóa tin: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { deletingApartment = null }
        ) {
            Text("Thao tác này không thể hoàn tác. Bạn có chắc chắn muốn xóa tin '${aptToDelete["title"].string}' không?", fontSize = 13.sp, color = FutaColors.Slate)
        }
    }
}
