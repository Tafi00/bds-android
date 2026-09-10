package vn.futaland.app.features.account

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
// ============================================================================
// 1. NEWS SCREEN (Matching iOS NewsView 100% with live /cms/news API)
// ============================================================================
@Composable
fun NewsScreen(
    onBack: () -> Unit,
    onArticleClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "all" to "Tất cả",
        "Thị trường" to "Thị trường",
        "Dự án" to "Dự án",
        "Quy hoạch" to "Quy hoạch",
        "Chính sách" to "Chính sách",
        "Phong thủy" to "Phong thủy",
        "Kiến thức" to "Kiến thức"
    )

    fun loadNews() {
        scope.launch {
            loading = true
            error = null
            try {
                val query = mutableMapOf(
                    "page" to page.toString(),
                    "limit" to "10"
                )
                if (search.isNotEmpty()) query["search"] = search
                if (selectedCategory != "all") query["category"] = selectedCategory

                val res = APIClient.get().request("/cms/news", query = query)
                items = res["data"].array
                val pagination = res["pagination"]
                totalPages = maxOf(1, pagination["totalPages"].int)
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(page, selectedCategory) {
        loadNews()
    }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                        }
                        Text(
                            text = "Tin tức thị trường",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 6 Categories Pills matching iOS categoryFilterBar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { (catKey, catLabel) ->
                            val isSelected = selectedCategory == catKey
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable {
                                    selectedCategory = catKey
                                    page = 1
                                }
                            ) {
                                Text(
                                    text = catLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else FutaColors.Navy,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FutaInput(
                    value = search,
                    onValueChange = {
                        search = it
                        page = 1
                        loadNews()
                    },
                    placeholder = "Tìm kiếm bài viết, xu hướng BĐS...",
                    leadingIcon = Icons.Default.Search,
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = FutaColors.Slate,
                                modifier = Modifier.size(18.dp).clickable {
                                    search = ""
                                    loadNews()
                                }
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (loading && items.isEmpty()) {
                items(5) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FutaSkeletonBlock(width = 106.dp, height = 80.dp, radius = 10.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FutaSkeletonBlock(height = 14.dp, width = 80.dp)
                            FutaSkeletonBlock(height = 18.dp, width = 200.dp)
                            FutaSkeletonBlock(height = 12.dp, width = 140.dp)
                        }
                    }
                }
            } else if (items.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Chưa có bài viết",
                        message = "Không tìm thấy nội dung phù hợp với tiêu chí tìm kiếm."
                    )
                }
            } else {
                itemsIndexed(items, key = { idx, it -> it["id"].string.ifEmpty { "news-$idx" } }) { _, article ->
                    NewsArticleCard(
                        article = article,
                        onClick = {
                            val slug = article["slug"].string
                            if (slug.isNotEmpty()) onArticleClick(slug)
                        }
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(top = 10.dp))
                }

                // Pagination Bar matching iOS paginationBar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { if (page > 1) page-- },
                            enabled = page > 1
                        ) {
                            Text("‹ Trang trước", fontWeight = FontWeight.Bold, color = if (page > 1) FutaColors.BrandGreen else Color(0xFFCBD5E1))
                        }
                        Text("Trang $page / $totalPages", fontSize = 12.sp, color = FutaColors.Slate)
                        TextButton(
                            onClick = { if (page < totalPages) page++ },
                            enabled = page < totalPages
                        ) {
                            Text("Trang sau ›", fontWeight = FontWeight.Bold, color = if (page < totalPages) FutaColors.BrandGreen else Color(0xFFCBD5E1))
                        }
                    }
                }
            }
        }
    }
}

// NewsArticleCard matching iOS NewsArticleRow 100%
@Composable
private fun NewsArticleCard(
    article: JSONValue,
    onClick: () -> Unit
) {
    val title = article["title"].string
    val excerpt = article["excerpt"].string
    val cover = article["coverImageUrl"].string.ifEmpty { article["image"].string }
    val category = article["category"].string
    val publishedAt = article["publishedAt"].string.take(10)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail: 106x80 rounded 10dp matching iOS exactly
        Box(
            modifier = Modifier
                .size(width = 106.dp, height = 80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF1F5F9))
        ) {
            if (cover.isNotEmpty()) {
                coil3.compose.AsyncImage(
                    model = cover,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Newspaper,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(32.dp).align(Alignment.Center)
                )
            }
        }

        // Details column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Category badge + published date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (category.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = FutaColors.MintBg
                    ) {
                        Text(
                            text = category.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (publishedAt.isNotEmpty()) {
                    Text(
                        text = publishedAt,
                        fontSize = 11.sp,
                        color = FutaColors.Muted
                    )
                }
            }

            // Title 2 lines bold
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Navy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            // Excerpt 2 lines
            if (excerpt.isNotEmpty()) {
                Text(
                    text = excerpt,
                    fontSize = 11.5.sp,
                    color = FutaColors.Slate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ============================================================================
// 1B. NEWS DETAIL SCREEN (Matching iOS NewsDetailView)
// ============================================================================
@Composable
fun NewsDetailScreen(
    slug: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var article by remember { mutableStateOf<JSONValue?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(slug) {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/cms/news/$slug")
                article = if (!res["data"].isNull) res["data"] else res
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
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Chi tiết bài viết",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    article?.let { a ->
                        IconButton(onClick = {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "${a["title"].string} - FUTA Land")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }) {
                            Icon(Icons.Default.Share, "Chia sẻ", tint = FutaColors.Navy)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (loading || article == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FutaSkeletonBlock(height = 220.dp, radius = 16.dp)
                FutaSkeletonBlock(height = 24.dp, width = 240.dp)
                FutaSkeletonBlock(height = 100.dp, radius = 12.dp)
            }
        } else {
            val a = article!!
            val title = a["title"].string
            val cover = a["coverImageUrl"].string.ifEmpty { a["image"].string }
            val category = a["category"].string
            val publishedAt = a["publishedAt"].string.take(10)
            val excerpt = a["excerpt"].string
            val content = a["content"].string

            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color.White).padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cover Image 220dp matching iOS
                if (cover.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFE2E8F0))
                        ) {
                            coil3.compose.AsyncImage(
                                model = cover,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Header metadata row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (category.isNotEmpty()) {
                            Surface(shape = CircleShape, color = FutaColors.MintBg) {
                                Text(
                                    text = category.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.BrandGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (publishedAt.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, null, tint = FutaColors.Slate, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(publishedAt, fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                            }
                            val views = a["viewCount"].int
                            if (views > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, null, tint = FutaColors.Slate, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("$views lượt xem", fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                            }
                        }
                    }
                }

                // Title
                item {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        lineHeight = 26.sp
                    )
                }

                // Excerpt
                if (excerpt.isNotEmpty()) {
                    item {
                        Text(
                            text = excerpt,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = FutaColors.Slate,
                            lineHeight = 20.sp
                        )
                    }
                }

                item {
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }

                // Content (plain text rendering or cleaned HTML)
                item {
                    val cleanedContent = content
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                    Text(
                        text = cleanedContent,
                        fontSize = 14.sp,
                        color = FutaColors.Body,
                        lineHeight = 22.sp
                    )
                }

                item {
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// ============================================================================
// 2. GUIDE SCREEN (Matching iOS GuideView)
// ============================================================================
@Composable
fun GuideScreen(
    onBack: () -> Unit
) {
    val guides = remember {
        listOf(
            Triple(
                "Quy trình mua & giao dịch căn hộ",
                "Các bước từ tìm hiểu dự án đến nhận nhà",
                listOf(
                    "Tìm kiếm sản phẩm phù hợp trên ứng dụng FUTA Land.",
                    "Đặt lịch xem nhà thực tế với chuyên viên tư vấn.",
                    "Xác nhận thỏa thuận và tiến hành đặt cọc online.",
                    "Ký hợp đồng mua bán và nhận hỗ trợ giải ngân 0% lãi suất."
                )
            ),
            Triple(
                "Hướng dẫn giữ chỗ trực tuyến",
                "Khóa căn ERP nhanh chóng và minh bạch",
                listOf(
                    "Chọn căn hộ mong muốn tại mục Bảng hàng dự án.",
                    "Bấm nút 'Giữ chỗ ngay' ở góc dưới màn hình.",
                    "Điền thông tin người đứng tên và thanh toán 50.000.000 VNĐ.",
                    "Nhận mã xác nhận và phiếu giữ chỗ điện tử qua email/SMS."
                )
            ),
            Triple(
                "Đăng ký trở thành tư vấn viên (TVV)",
                "Gia nhập mạng lưới phân phối BĐS FUTA",
                listOf(
                    "Gửi hồ sơ lý lịch và chứng chỉ hành nghề môi giới.",
                    "Tham gia khóa sát hạch online và hoàn thành bài thi.",
                    "Nhận tài khoản quản trị TVV và bắt đầu bán hàng."
                )
            )
        )
    }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text("Hướng dẫn sử dụng", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(guides) { _, (title, subtitle, steps) ->
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text(subtitle, fontSize = 12.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        steps.forEachIndexed { sIdx, step ->
                            Row(verticalAlignment = Alignment.Top) {
                                Surface(shape = CircleShape, color = FutaColors.MintBg, modifier = Modifier.size(20.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${sIdx + 1}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(step, fontSize = 12.5.sp, color = FutaColors.Body, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. CONTACT SCREEN (Matching iOS ContactView)
// ============================================================================
@Composable
fun ContactScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text("Liên hệ hỗ trợ", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Direct contact channels
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("KÊNH HỖ TRỢ TRỰC TIẾP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:02838386852")))
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Tổng đài CSKH 24/7", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    Text("028 3838 6852 (Miễn phí cước)", fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = FutaColors.Slate, modifier = Modifier.size(15.dp))
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:hotro@futaland.vn")))
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Email tiếp nhận", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    Text("hotro@futaland.vn", fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = FutaColors.Slate, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // Inquiry Form
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GỬI YÊU CẦU TƯ VẤN", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        FutaInput(value = name, onValueChange = { name = it }, placeholder = "Họ và tên của bạn")
                        FutaInput(value = phone, onValueChange = { phone = it }, placeholder = "Số điện thoại liên hệ")
                        FutaInput(value = email, onValueChange = { email = it }, placeholder = "Địa chỉ email (tùy chọn)")
                        FutaInput(value = message, onValueChange = { message = it }, placeholder = "Nội dung cần hỗ trợ...", modifier = Modifier.height(90.dp))

                        FutaButton(
                            text = "Gửi thông tin liên hệ",
                            variant = FutaButtonVariant.PRIMARY,
                            onClick = {
                                if (phone.isNotEmpty()) {
                                    ToastCenter.show("Đã tiếp nhận yêu cầu! Đội ngũ FUTA Land sẽ liên hệ lại sớm nhất.")
                                    onBack()
                                } else {
                                    ToastCenter.show("Vui lòng nhập số điện thoại", isError = true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Headquarters
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TRỤ SỞ CHÍNH FUTA LAND", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        Text("486 - 486A Lê Văn Lương, Phường Tân Phong, Quận 7, TP. Hồ Chí Minh", fontSize = 12.5.sp, color = FutaColors.Navy, lineHeight = 18.sp)
                        Text("Giờ làm việc: Thứ Hai - Thứ Bảy (08:00 - 18:00)", fontSize = 11.sp, color = FutaColors.Slate)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. ABOUT SCREEN (Matching iOS AboutView)
// ============================================================================
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text("Về FUTA Land", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF064D3D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                            Text("TẬP ĐOÀN FUTA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Text("Kiến tạo chuẩn mực\nbất động sản công nghệ", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 28.sp)
                        Text(
                            text = "FUTA Land là đơn vị phát triển và phân phối bất động sản thuộc hệ sinh thái FUTA Group, ứng dụng nền tảng số hoá và AI để mang lại trải nghiệm minh bạch nhất cho khách hàng.",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 4 Core Values
            item {
                Text("GIÁ TRỊ CỐT LÕI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Pair("Chất lượng là danh dự", "Cam kết tiến độ xây dựng chuẩn mực và bàn giao đúng chất lượng cam kết."),
                        Pair("Minh bạch & Chuẩn mực", "Pháp lý hoàn chỉnh, giá bán niêm yết rõ ràng và đối soát điện tử minh bạch."),
                        Pair("Khách hàng là trọng tâm", "Đồng hành từ giai đoạn chọn căn, hỗ trợ vay vốn đến khi trao sổ hồng."),
                        Pair("Đổi mới không ngừng", "Tiên phong ứng dụng AI, VR 360 và giải pháp số hóa toàn diện quy trình giao dịch.")
                    ).forEach { (valTitle, valDesc) ->
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(valTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    Spacer(Modifier.height(2.dp))
                                    Text(valDesc, fontSize = 12.sp, color = FutaColors.Slate, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. POLICIES SCREEN (Matching iOS PoliciesIndexView)
// ============================================================================
@Composable
fun PoliciesScreen(
    onBack: () -> Unit
) {
    var selectedPolicy by remember { mutableStateOf<Pair<String, String>?>(null) }

    val policies = remember {
        listOf(
            "Chính sách bán hàng & Quy chế giao dịch" to "Quy định đặt cọc, giữ chỗ 24h và đối soát hợp đồng điện tử.",
            "Điều khoản dịch vụ FUTA Land" to "Quy định sử dụng nền tảng và trách nhiệm giữa người mua, TVV và sàn.",
            "Chính sách bảo mật thông tin cá nhân" to "Cam kết bảo vệ dữ liệu khách hàng theo chuẩn an toàn quốc tế.",
            "Cơ chế giải quyết khiếu nại & tranh chấp" to "Quy trình tiếp nhận và xử lý thỏa đáng trong vòng 48 giờ làm việc.",
            "Chính sách hoàn tiền giữ chỗ" to "Cam kết hoàn trả 100% tiền giữ chỗ nếu khách hàng không chọn được căn ưng ý."
        )
    }
    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text("Điều khoản & Chính sách", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(policies) { _, (title, desc) ->
                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedPolicy = (title to desc) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Description, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text(desc, fontSize = 11.5.sp, color = FutaColors.Slate)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // Policy Detail Sheet
    selectedPolicy?.let { (pTitle, pDesc) ->
        FutaBottomSheet(
            visible = true,
            onDismiss = { selectedPolicy = null },
            title = pTitle
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FutaColors.MintBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = pDesc,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = FutaColors.BrandGreen,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Text("1. NGUYÊN TẮC CHUNG", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                Text(
                    text = "Quy định này áp dụng cho toàn bộ khách hàng, nhà đầu tư và chuyên viên tư vấn tham gia giao dịch bất động sản trực tuyến qua nền tảng công nghệ FUTA Land. Mọi sản phẩm niêm yết đều được thẩm định pháp lý minh bạch.",
                    fontSize = 12.5.sp,
                    color = FutaColors.Slate,
                    lineHeight = 18.sp
                )

                Text("2. QUYỀN VÀ NGHĨA VỤ KHÁCH HÀNG", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                Text(
                    text = "Khách hàng có quyền yêu cầu cung cấp đầy đủ hồ sơ pháp lý, bản vẽ quy hoạch và chính sách bán hàng chính thức. Khách hàng cam kết cung cấp thông tin trung thực khi đặt chỗ giữ cọc căn hộ.",
                    fontSize = 12.5.sp,
                    color = FutaColors.Slate,
                    lineHeight = 18.sp
                )

                Text("3. QUY TRÌNH GIỮ CHỖ & HOÀN TIỀN", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                Text(
                    text = "Khoản tiền giữ chỗ tiêu chuẩn 50.000.000 VNĐ sẽ được phong tỏa tại tài khoản chuyên dụng của FUTA Land. Khách hàng được quyền hủy giữ chỗ và nhận hoàn tiền 100% trong vòng 24 giờ kể từ thời điểm phát sinh giao dịch.",
                    fontSize = 12.5.sp,
                    color = FutaColors.Slate,
                    lineHeight = 18.sp
                )

                Text("4. HIỆU LỰC ÁP DỤNG", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                Text(
                    text = "Văn bản chính sách này có hiệu lực từ ngày 01/01/2026 và được cập nhật định kỳ theo quy định pháp luật và thông báo từ Tập đoàn FUTA.",
                    fontSize = 12.5.sp,
                    color = FutaColors.Slate,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(8.dp))
                FutaButton(
                    text = "Tôi đã hiểu & Đồng ý",
                    variant = FutaButtonVariant.PRIMARY,
                    onClick = { selectedPolicy = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
