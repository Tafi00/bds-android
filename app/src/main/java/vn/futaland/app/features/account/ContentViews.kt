package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
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
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
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
        "Quy hoạch" to "Quy hoạch",
        "Tài chính" to "Tài chính",
        "Cẩm nang" to "Cẩm nang",
        "Kinh nghiệm" to "Kinh nghiệm"
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

private fun resolveNewsImage(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "null") return "https://bds.futaland.vn/images/futa/news-sample.png"
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://bds.futaland.vn/${trimmed.removePrefix("/")}"
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
    val rawCover = article["coverImageUrl"].string.ifEmpty { article["image"].string }
    val cover = resolveNewsImage(rawCover)
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
            coil3.compose.AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(cover)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
                            val slug = a["slug"].string.ifEmpty { a.id }
                            val shareUrl = if (slug.isNotEmpty()) "https://bds.futaland.vn/news/$slug" else "https://bds.futaland.vn/news"
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                                putExtra(Intent.EXTRA_SUBJECT, a["title"].string)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "Chia sẻ bài viết"))
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
            val rawCover = a["coverImageUrl"].string.ifEmpty { a["image"].string }
            val cover = resolveNewsImage(rawCover)
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
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(cover)
                                    .crossfade(true)
                                    .build(),
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
                                    Text(tr("{0} lượt xem", views), fontSize = 11.5.sp, color = FutaColors.Slate)
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
// 2. GUIDE SCREEN (Matching iOS GuideView: modules → expandable tasks + search)
// ============================================================================
private data class GuideTask(val title: String, val summary: String, val steps: List<String>, val notes: List<String>)
private data class GuideModule(val order: Int, val title: String, val description: String, val audience: String, val tasks: List<GuideTask>)

private val guideModules = listOf(
    GuideModule(
        1, "Đăng ký và đăng nhập",
        "Tạo tài khoản, đăng nhập bằng số điện thoại và xử lý các trạng thái xác thực thường gặp.",
        "Tất cả người dùng",
        listOf(
            GuideTask(
                "Tạo tài khoản mới", "Đăng ký tài khoản khách hàng bằng số điện thoại hợp lệ.",
                listOf(
                    "Mở tab Tài khoản và chọn Đăng ký.",
                    "Nhập họ tên, số điện thoại, email và mật khẩu.",
                    "Đọc điều khoản, xác nhận đồng ý rồi gửi biểu mẫu.",
                    "Đăng nhập sau khi hệ thống thông báo tạo tài khoản thành công."
                ),
                listOf("Số điện thoại được dùng để đối soát giao dịch và liên hệ chăm sóc.")
            ),
            GuideTask(
                "Đăng nhập và bảo mật", "Truy cập các chức năng theo vai trò được cấp.",
                listOf(
                    "Chọn Đăng nhập trên màn hình tài khoản.",
                    "Nhập số điện thoại và mật khẩu.",
                    "Sau khi đăng nhập, kiểm tra thông tin hiển thị trên trang cá nhân."
                ),
                listOf("Không chia sẻ mã xác thực hoặc mật khẩu cho người khác.")
            )
        )
    ),
    GuideModule(
        2, "Hồ sơ cá nhân",
        "Cập nhật thông tin liên hệ, ảnh đại diện và theo dõi trạng thái tài khoản.",
        "Người dùng đã đăng nhập",
        listOf(
            GuideTask(
                "Cập nhật hồ sơ", "Giữ thông tin liên hệ luôn chính xác.",
                listOf(
                    "Vào mục Hồ sơ cá nhân trong tab Tài khoản.",
                    "Chạm vào ảnh đại diện để tải ảnh mới.",
                    "Cập nhật họ tên, email, địa chỉ và giới thiệu.",
                    "Bấm Lưu thay đổi để lưu dữ liệu lên hệ thống."
                ),
                listOf("Vai trò và quyền hạn do quản trị viên phân bổ.")
            )
        )
    ),
    GuideModule(
        3, "Tìm kiếm bất động sản",
        "Tìm tin bằng từ khóa, bộ lọc khoảng giá, diện tích, vị trí và hình ảnh.",
        "Tất cả người dùng",
        listOf(
            GuideTask(
                "Tìm và lọc tin", "Thu hẹp danh sách theo tiêu chí mong muốn.",
                listOf(
                    "Mở tab Tìm kiếm.",
                    "Nhập từ khóa dự án, khu vực vào thanh tìm kiếm.",
                    "Bấm biểu tượng Bộ lọc để chọn khoảng giá, diện tích, số phòng ngủ."
                ),
                listOf("Có thể đặt lại bộ lọc bất cứ lúc nào.")
            ),
            GuideTask(
                "Đánh giá chi tiết một tin", "Xem hình ảnh, đặc điểm và thông tin liên hệ.",
                listOf(
                    "Chạm vào tin đăng để mở màn hình chi tiết.",
                    "Vuốt gallery ảnh để xem các góc chụp và thông số căn hộ.",
                    "Bấm Virtual Tour 360° hoặc Video nếu có.",
                    "Bấm Gọi điện hoặc Liên hệ tư vấn để kết nối chuyên viên."
                ),
                listOf("Luôn xác minh tính pháp lý trước khi tiến hành đặt cọc.")
            )
        )
    ),
    GuideModule(
        4, "Đăng tin bất động sản",
        "Tạo tin mới với thông tin, hình ảnh, vị trí và gói hiển thị phù hợp.",
        "Người dùng có quyền đăng tin",
        listOf(
            GuideTask(
                "Tạo tin đăng mới", "Nhập đủ dữ liệu để tin đăng được duyệt nhanh.",
                listOf(
                    "Vào tab Tài khoản -> Tin đăng của tôi.",
                    "Bấm nút '+' ở góc trên bên phải.",
                    "Điền tiêu đề, loại giao dịch, giá, diện tích và vị trí.",
                    "Tải ảnh căn hộ sắc nét từ thiết bị.",
                    "Bấm Lưu để gửi tin duyệt lên hệ thống."
                ),
                listOf("Không sử dụng hình ảnh vi phạm bản quyền hoặc sai lệch thực tế.")
            )
        )
    ),
    GuideModule(
        5, "Quản lý và đẩy tin",
        "Theo dõi trạng thái duyệt, chỉnh sửa, gia hạn và đẩy tin lên đầu.",
        "Chủ tin đăng",
        listOf(
            GuideTask(
                "Đẩy tin lên đầu danh sách", "Gia tăng lượt tiếp cận khách hàng tiềm năng.",
                listOf(
                    "Vào Tin đăng của tôi.",
                    "Tìm tin cần làm nổi bật.",
                    "Bấm nút 'Đẩy tin' (biểu tượng tia sét).",
                    "Hệ thống sẽ trừ 1 lượt đẩy trong ngày và đưa tin lên đầu."
                ),
                listOf("Gói PRO có 3 lượt/ngày, VIP có 10 lượt/ngày.")
            )
        )
    ),
    GuideModule(
        6, "Tin yêu thích & Thư mục",
        "Lưu trữ tin quan tâm và phân loại theo dự án hoặc nhu cầu cá nhân.",
        "Người dùng đã đăng nhập",
        listOf(
            GuideTask(
                "Lưu và quản lý thư mục", "Tổ chức tin khoa học và tiện theo dõi.",
                listOf(
                    "Bấm biểu tượng Trái tim trên thẻ tin để lưu vào Yêu thích.",
                    "Bấm biểu tượng Thư mục ở góc trên chi tiết tin để lưu vào Thư mục riêng.",
                    "Mở tab Đã lưu để xem danh sách hoặc tạo thư mục mới."
                ),
                listOf("Xoá thư mục sẽ không làm mất tin đăng gốc.")
            )
        )
    ),
    GuideModule(
        7, "Gói dịch vụ & Thanh toán VietQR",
        "Tìm hiểu quyền lợi các gói FREE, PRO, VIP và cách thanh toán tự động.",
        "Khách hàng và môi giới",
        listOf(
            GuideTask(
                "Nâng cấp gói qua VietQR", "Kích hoạt nhanh chóng bằng chuẩn QR NAPAS 24/7.",
                listOf(
                    "Vào Bảng giá dịch vụ trong menu Tài khoản.",
                    "Chọn gói dịch vụ mong muốn (PRO hoặc VIP) và chu kỳ.",
                    "Bấm 'Nâng cấp' để tạo đơn hàng.",
                    "Mở ứng dụng ngân hàng, quét mã VietQR trên màn hình.",
                    "Xác nhận chuyển khoản và giữ nguyên nội dung mã đơn."
                ),
                listOf("Hệ thống sẽ đối soát tự động và nâng cấp tài khoản của bạn.")
            )
        )
    )
)

@Composable
fun GuideScreen(
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var expandedTask by remember { mutableStateOf<String?>(null) }

    val modules = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) guideModules else guideModules.filter { m ->
            fun hit(s: String) = s.contains(q, ignoreCase = true) || tr(s).contains(q, ignoreCase = true)
            hit(m.title) || m.tasks.any { hit(it.title) || hit(it.summary) }
        }
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), tint = FutaColors.Navy)
                        }
                        Text("Hướng dẫn sử dụng", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                    }
                    FutaInput(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Tìm chủ đề hướng dẫn...",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (modules.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không tìm thấy hướng dẫn",
                        message = "Thử tìm với từ khóa khác.",
                        icon = Icons.Default.Search
                    )
                }
            }
            itemsIndexed(modules) { _, module ->
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerbatimText("${module.order}. ${tr(module.title)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text(module.description, fontSize = 12.5.sp, color = FutaColors.Slate, lineHeight = 17.sp)
                        Text(tr("Đối tượng: {0}", tr(module.audience)), fontSize = 11.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.SemiBold)

                        module.tasks.forEach { task ->
                            val key = "${module.order}-${task.title}"
                            val expanded = expandedTask == key
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedTask = if (expanded) null else key }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(task.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                Icon(
                                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    null,
                                    tint = FutaColors.Slate,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (expanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                    Text(task.summary, fontSize = 12.sp, color = FutaColors.Slate)
                                    task.steps.forEachIndexed { idx, step ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            VerbatimText("${idx + 1}.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                            Spacer(Modifier.width(8.dp))
                                            Text(step, fontSize = 12.sp, color = FutaColors.Body, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    if (task.notes.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(FutaColors.BrandOrange.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            task.notes.forEach { note ->
                                                Row(verticalAlignment = Alignment.Top) {
                                                    Icon(Icons.Default.Info, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(note, fontSize = 11.sp, color = FutaColors.Slate)
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
        }
    }
}

// ============================================================================
// 3. CONTACT SCREEN (Matching iOS ContactView, CMS sourced)
// ============================================================================

/** Company contact details from CMS Site Settings (systemConfig.contactInfo) with safe defaults. */
private data class ContactInfo(
    val phone: String = "0903715757",
    val email: String = "info@futaland.vn",
    val address: String = "486-486A Lê Văn Lương, P. Tân Phong, Quận 7, TP. Hồ Chí Minh",
    val workingHours: String = "Thứ Hai - Thứ Bảy: 08:00 - 18:00\nChủ Nhật: 08:30 - 12:00"
)

private val contactNeeds = listOf(
    "project" to "Tư vấn dự án",
    "product" to "Sản phẩm chuyển nhượng",
    "policy" to "Chính sách & Pháp lý",
    "investment" to "Cơ hội đầu tư",
    "other" to "Hỗ trợ khác"
)

@Composable
fun ContactScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = vn.futaland.app.core.auth.AppSession.shared.user
    var info by remember { mutableStateOf(ContactInfo()) }
    var name by remember { mutableStateOf(user?.get("name")?.string.orEmpty()) }
    var phone by remember { mutableStateOf(user?.get("phone")?.string.orEmpty()) }
    var email by remember { mutableStateOf(user?.get("email")?.string.orEmpty()) }
    var need by remember { mutableStateOf("project") }
    var message by remember { mutableStateOf("") }
    var showNeedPicker by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val res = APIClient.get().request("/cms/settings")
            val data = if (res["data"].isNull) res else res["data"]
            val ci = data["systemConfig"]["contactInfo"].takeIf { !it.isNull } ?: data["contactInfo"]
            info = ContactInfo(
                phone = ci["phone"].string.ifEmpty { info.phone },
                email = ci["email"].string.ifEmpty { info.email },
                address = ci["address"].string.ifEmpty { info.address },
                workingHours = ci["workingHours"].string.ifEmpty { info.workingHours }
            )
        } catch (_: Exception) {
            // Keep the defaults.
        }
    }

    fun sendInquiry() {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        val cleanEmail = email.trim()
        if (cleanName.isEmpty() || cleanPhone.isEmpty()) {
            ToastCenter.show(tr("Vui lòng nhập họ tên và số điện thoại"), isError = true)
            return
        }
        if (cleanEmail.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            ToastCenter.show(tr("Email không hợp lệ"), isError = true)
            return
        }
        scope.launch {
            sending = true
            try {
                // Stored as a "Yêu cầu tư vấn" for the admin team (same endpoint as the web /lien-he form).
                val body = buildJsonObject {
                    put("name", cleanName)
                    put("phone", cleanPhone)
                    if (cleanEmail.isNotEmpty()) put("email", cleanEmail)
                    put("need", need)
                    put("message", message.trim())
                    put("source", "android-app-contact")
                }.toString()
                APIClient.get().request("/contact-requests", method = "POST", bodyJson = body)

                // Web/iOS parity: an anonymous visitor also becomes a guest lead in the CRM.
                if (!vn.futaland.app.core.auth.AppSession.shared.isAuthenticated && cleanName.length >= 2 && cleanPhone.length >= 10) {
                    try {
                        val guest = buildJsonObject {
                            put("name", cleanName)
                            put("phone", cleanPhone)
                            if (cleanEmail.isNotEmpty()) put("email", cleanEmail)
                        }.toString()
                        APIClient.get().request("/auth/guest", method = "POST", bodyJson = guest)
                    } catch (_: Exception) {}
                }
                ToastCenter.show(tr("Đã tiếp nhận yêu cầu! Đội ngũ FUTA Land sẽ liên hệ lại sớm nhất."))
                message = ""
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không gửi được yêu cầu, vui lòng thử lại"), isError = true)
            } finally {
                sending = false
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), tint = FutaColors.Navy)
                    }
                    Text("Liên hệ", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding).clearFocusOnTap(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Direct contact channels
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("KÊNH HỖ TRỢ TRỰC TIẾP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        ContactChannelRow(Icons.Default.Phone, FutaColors.BrandGreen, "Tổng đài hỗ trợ 24/7", info.phone) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${info.phone.filter { it.isDigit() || it == '+' }}")))
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ContactChannelRow(Icons.Default.Email, Color(0xFF2563EB), "Email tiếp nhận", info.email) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${info.email}")))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            // Inquiry Form
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GỬI YÊU CẦU TƯ VẤN", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        FutaInput(value = name, onValueChange = { name = it }, placeholder = "Họ và tên *")
                        FutaInput(value = phone, onValueChange = { phone = it }, placeholder = "Số điện thoại *")
                        FutaInput(value = email, onValueChange = { email = it }, placeholder = "Email liên hệ")
                        FutaSelectField(
                            title = "Nhu cầu",
                            displayValue = tr(contactNeeds.first { it.first == need }.second),
                            onClick = { showNeedPicker = true }
                        )
                        FutaTextArea(value = message, onValueChange = { message = it }, placeholder = "Nội dung cần hỗ trợ...")

                        FutaButton(
                            text = if (sending) "Đang gửi..." else "Gửi yêu cầu",
                            icon = Icons.AutoMirrored.Filled.Send,
                            variant = FutaButtonVariant.PRIMARY,
                            enabled = !sending && name.isNotBlank() && phone.isNotBlank(),
                            onClick = { sendInquiry() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Headquarters
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TRỤ SỞ CÔNG TY", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                        Text("Trụ sở chính", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        VerbatimText(info.address.translated("cms"), fontSize = 12.5.sp, color = FutaColors.Slate, lineHeight = 18.sp)
                        Row(
                            modifier = Modifier.clickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(info.address)}")))
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(info.address)}")))
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Map, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Xem vị trí trên bản đồ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 4.dp))
                        Text("Giờ làm việc", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text(info.workingHours, fontSize = 12.sp, color = FutaColors.Slate, lineHeight = 18.sp)
                    }
                }
            }
        }
    }

    FutaBottomSheet(visible = showNeedPicker, onDismiss = { showNeedPicker = false }, title = "Nhu cầu") {
        contactNeeds.forEach { (key, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { need = key; showNeedPicker = false }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 14.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                if (need == key) Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ContactChannelRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            VerbatimText(value, fontSize = 11.5.sp, color = FutaColors.Slate)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = FutaColors.Slate, modifier = Modifier.size(15.dp))
    }
}

// ============================================================================
// 4. ABOUT SCREEN (Matching iOS AboutView)
// ============================================================================
@Composable
fun AboutScreen(
    onOpenProjects: () -> Unit = {},
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), tint = FutaColors.Navy)
                    }
                    Text("Về FUTA Land", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF086338), Color(0xFF054024))),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f)) {
                        Text("TẬP ĐOÀN FUTA", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.9f), letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    Text("Kiến tạo chuẩn mực\nbất động sản công nghệ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 29.sp)
                    Text(
                        text = "FUTA Land là đơn vị phát triển và phân phối bất động sản thuộc hệ sinh thái FUTA Group, ứng dụng nền tảng số hoá và AI để mang lại trải nghiệm minh bạch nhất cho khách hàng.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        lineHeight = 19.sp
                    )
                }
            }

            // Metrics
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("18+" to "Dự án\nquy hoạch", "12" to "Tỉnh thành\ntrọng điểm", "25.000+" to "Sản phẩm\ngiao dịch").forEach { (value, label) ->
                        FutaCard(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                VerbatimText(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, maxLines = 1)
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = FutaColors.Slate, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }

            // Core Values
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Giá trị cốt lõi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    listOf(
                        Triple(Icons.Default.Shield, "Minh bạch tuyệt đối", "Mọi thông tin quy hoạch, pháp lý và tiến độ xây dựng đều được cập nhật chuẩn xác, rõ ràng."),
                        Triple(Icons.Default.Groups, "Chuyên nghiệp & Tận tâm", "Đội ngũ chuyên viên tư vấn được đào tạo bài bản, đồng hành từ khâu tìm kiếm tới khi nhận nhà."),
                        Triple(Icons.Default.Memory, "Công nghệ tiên phong", "Ứng dụng trí tuệ nhân tạo (AI), Virtual Tour 360° và thanh toán tự động tiện lợi.")
                    ).forEach { (icon, valTitle, valDesc) ->
                        FutaCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(valTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    Spacer(Modifier.height(3.dp))
                                    Text(valDesc, fontSize = 12.sp, color = FutaColors.Slate, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Story / development journey
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Hành trình phát triển", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text(
                            "Thừa hưởng nền tảng uy tín và tiềm lực từ Tập đoàn FUTA (Phương Trang), FUTA Land hướng đến việc xây dựng những cộng đồng đô thị văn minh, bền vững và hiện đại trên khắp cả nước.",
                            fontSize = 13.sp,
                            color = FutaColors.Slate,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            // CTA
            item {
                FutaButton(
                    text = "Khám phá các dự án FUTA",
                    icon = Icons.Default.Apartment,
                    variant = FutaButtonVariant.PRIMARY,
                    height = 50.dp,
                    onClick = onOpenProjects,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
