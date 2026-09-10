package vn.futaland.app.features.account

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class CMSSectionTab(val label: String, val icon: ImageVector) {
    NEWS("Bài viết & Tin tức", Icons.Default.Newspaper),
    HOMEPAGE("Trang chủ", Icons.Default.Home),
    LEGAL("Chính sách & Quy chế", Icons.AutoMirrored.Filled.MenuBook),
    TAXONOMY("Danh mục BĐS", Icons.Default.Category)
}

@Composable
fun AdminCMSScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(CMSSectionTab.NEWS) }

    // News Section States
    var articles by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var newsLoading by remember { mutableStateOf(true) }
    var newsSearch by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("all") }
    var newsPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }

    // Create & Edit State
    var editingArticle by remember { mutableStateOf<JSONValue?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var deletingArticle by remember { mutableStateOf<JSONValue?>(null) }

    // Homepage Section States
    var homepageSettings by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var homepageLoading by remember { mutableStateOf(false) }
    var isSavingHomepage by remember { mutableStateOf(false) }

    // Legal Documents States
    var legalDocs by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var legalLoading by remember { mutableStateOf(false) }

    // Taxonomies States
    var taxonomies by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var taxonomyLoading by remember { mutableStateOf(false) }

    fun loadArticles(searchQuery: String = newsSearch) {
        scope.launch {
            newsLoading = true
            try {
                val q = searchQuery.trim()
                var url = "/cms/admin/news?page=$newsPage&limit=15"
                if (q.isNotEmpty()) {
                    url += "&search=${java.net.URLEncoder.encode(q, "UTF-8")}"
                }
                if (selectedStatus != "all") {
                    url += "&status=$selectedStatus"
                }
                val res = APIClient.get().request(url)
                articles = res["data"].array
                totalPages = maxOf(1, res["pagination"]["totalPages"].int)
            } catch (_: Exception) {
                articles = emptyList()
            } finally {
                newsLoading = false
            }
        }
    }

    fun loadHomepageContent() {
        scope.launch {
            homepageLoading = true
            try {
                val res = APIClient.get().request("/cms/settings")
                homepageSettings = res["data"]
            } catch (_: Exception) {}
            finally {
                homepageLoading = false
            }
        }
    }

    fun loadLegalDocs() {
        scope.launch {
            legalLoading = true
            try {
                val res = APIClient.get().request("/cms/legal-documents")
                legalDocs = res["data"].array
            } catch (_: Exception) {}
            finally {
                legalLoading = false
            }
        }
    }

    fun loadTaxonomies() {
        scope.launch {
            taxonomyLoading = true
            try {
                val res = APIClient.get().request("/cms/taxonomies")
                taxonomies = res["data"].array.ifEmpty { res["data"]["categories"].array }
            } catch (_: Exception) {}
            finally {
                taxonomyLoading = false
            }
        }
    }

    LaunchedEffect(newsSearch, selectedStatus, newsPage) {
        kotlinx.coroutines.delay(300)
        loadArticles(newsSearch)
    }

    LaunchedEffect(currentTab) {
        when (currentTab) {
            CMSSectionTab.NEWS -> loadArticles(newsSearch)
            CMSSectionTab.HOMEPAGE -> loadHomepageContent()
            CMSSectionTab.LEGAL -> loadLegalDocs()
            CMSSectionTab.TAXONOMY -> loadTaxonomies()
        }
    }

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
                        text = "Quản trị nội dung",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    if (currentTab == CMSSectionTab.NEWS) {
                        FutaHeaderIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Thêm bài viết",
                            tint = FutaColors.BrandGreen,
                            onClick = { showCreateSheet = true }
                        )
                    } else {
                        Spacer(Modifier.width(40.dp))
                    }
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
            // Main Module Navigation Tabs (Matching iOS Sections)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CMSSectionTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) FutaColors.BrandGreen else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable { currentTab = tab }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else FutaColors.Slate,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else FutaColors.Navy
                                )
                            }
                        }
                    }
                }
            }

            // Tab 1: NEWS & ARTICLES MANAGEMENT
            if (currentTab == CMSSectionTab.NEWS) {
                // Pinned Search Bar (API Debounced)
                item {
                    FutaInput(
                        value = newsSearch,
                        onValueChange = {
                            newsSearch = it
                            newsPage = 1
                        },
                        placeholder = "Tìm kiếm tiêu đề, chuyên mục…",
                        leadingIcon = Icons.Default.Search,
                        trailingIcon = if (newsSearch.isNotEmpty()) {
                            {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Xóa",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            newsSearch = ""
                                            newsPage = 1
                                        },
                                    tint = FutaColors.Slate
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Status Filter Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("all", "Tất cả", Icons.Default.Inbox),
                            Triple("published", "Đã xuất bản", Icons.Default.CheckCircle),
                            Triple("draft", "Bản nháp", Icons.Default.Description)
                        ).forEach { (sKey, sLabel, sIcon) ->
                            val isSel = selectedStatus == sKey
                            Surface(
                                shape = CircleShape,
                                color = if (isSel) FutaColors.BrandGreen else Color.White,
                                border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                modifier = Modifier.clickable {
                                    selectedStatus = sKey
                                    newsPage = 1
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(sIcon, null, tint = if (isSel) Color.White else FutaColors.Slate, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = sLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else FutaColors.Navy
                                    )
                                }
                            }
                        }
                    }
                }

                // Articles Feed
                if (newsLoading) {
                    items(4) {
                        FutaSkeletonBlock(height = 95.dp, radius = 14.dp)
                    }
                } else if (articles.isEmpty()) {
                    item {
                        FutaEmptyState(
                            title = "Chưa có bài viết",
                            message = "Không tìm thấy bài viết nào phù hợp với điều kiện tìm kiếm."
                        )
                    }
                } else {
                    itemsIndexed(articles, key = { idx, item -> item.id.ifEmpty { "art-$idx" } }) { _, article ->
                        NewsArticleRowItem(
                            article = article,
                            onClick = { editingArticle = article },
                            onDelete = { deletingArticle = article }
                        )
                    }

                    // Pagination Bar
                    if (totalPages > 1) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Trang $newsPage / $totalPages",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FutaColors.Slate
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FutaButton(
                                        text = "Trước",
                                        variant = FutaButtonVariant.OUTLINE,
                                        enabled = newsPage > 1,
                                        onClick = { if (newsPage > 1) newsPage-- }
                                    )
                                    FutaButton(
                                        text = "Sau",
                                        variant = FutaButtonVariant.OUTLINE,
                                        enabled = newsPage < totalPages,
                                        onClick = { if (newsPage < totalPages) newsPage++ }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab 2: HOMEPAGE CONTENT SECTION
            if (currentTab == CMSSectionTab.HOMEPAGE) {
                if (homepageLoading) {
                    items(3) {
                        FutaSkeletonBlock(height = 120.dp, radius = 16.dp)
                    }
                } else {
                    item {
                        HomepageContentEditorCard(
                            settings = homepageSettings,
                            isSaving = isSavingHomepage,
                            onSave = { updated ->
                                scope.launch {
                                    isSavingHomepage = true
                                    try {
                                        APIClient.get().request("/cms/settings", method = "PUT", bodyJson = updated)
                                        ToastCenter.show("Đã lưu cấu hình Trang chủ thành công!")
                                        loadHomepageContent()
                                    } catch (e: Exception) {
                                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                    } finally {
                                        isSavingHomepage = false
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Tab 3: LEGAL DOCUMENTS & POLICIES
            if (currentTab == CMSSectionTab.LEGAL) {
                if (legalLoading) {
                    items(3) {
                        FutaSkeletonBlock(height = 80.dp, radius = 14.dp)
                    }
                } else if (legalDocs.isEmpty()) {
                    item {
                        FutaEmptyState(
                            title = "Chưa có quy chế",
                            message = "Hệ thống đang đồng bộ văn bản pháp lý & chính sách."
                        )
                    }
                } else {
                    itemsIndexed(legalDocs, key = { idx, item -> item.id.ifEmpty { "leg-$idx" } }) { _, doc ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEFF6FF), modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Article, null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(doc["title"].string.ifEmpty { "Văn bản pháp lý" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    Text(doc["code"].string.ifEmpty { "Chính sách FUTA Land" }, fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                                Surface(shape = CircleShape, color = Color(0xFFEAF5EF)) {
                                    Text("Hiệu lực", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Tab 4: TAXONOMIES / CATEGORIES
            if (currentTab == CMSSectionTab.TAXONOMY) {
                if (taxonomyLoading) {
                    items(4) {
                        FutaSkeletonBlock(height = 65.dp, radius = 12.dp)
                    }
                } else if (taxonomies.isEmpty()) {
                    item {
                        FutaEmptyState(
                            title = "Chưa có danh mục",
                            message = "Không có danh mục phân loại bất động sản."
                        )
                    }
                } else {
                    itemsIndexed(taxonomies, key = { idx, item -> item.id.ifEmpty { "tax-$idx" } }) { _, tax ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tax["name"].string.ifEmpty { tax["label"].string }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9)) {
                                    Text(tax["code"].string.ifEmpty { tax["value"].string }, fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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

    // Article Edit / Create BottomSheet
    if (showCreateSheet || editingArticle != null) {
        val target = editingArticle
        ArticleEditorSheet(
            initial = target,
            onDismiss = {
                showCreateSheet = false
                editingArticle = null
            },
            onSave = { body ->
                scope.launch {
                    try {
                        if (target != null) {
                            APIClient.get().request("/cms/admin/news/${target.id}", method = "PUT", bodyJson = body)
                            ToastCenter.show("Cập nhật bài viết thành công!")
                        } else {
                            APIClient.get().request("/cms/admin/news", method = "POST", bodyJson = body)
                            ToastCenter.show("Tạo bài viết mới thành công!")
                        }
                        showCreateSheet = false
                        editingArticle = null
                        loadArticles(newsSearch)
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi lưu bài viết: ${e.message}", isError = true)
                    }
                }
            }
        )
    }

    // Confirm Delete Dialog
    if (deletingArticle != null) {
        FutaDialog(
            visible = true,
            title = "Xóa bài viết?",
            confirmText = "Xóa bài viết",
            confirmVariant = FutaButtonVariant.DANGER,
            onConfirm = {
                scope.launch {
                    try {
                        val id = deletingArticle!!.id
                        APIClient.get().request("/cms/admin/news/$id", method = "DELETE")
                        ToastCenter.show("Đã xóa bài viết!")
                        deletingArticle = null
                        loadArticles(newsSearch)
                    } catch (e: Exception) {
                        ToastCenter.show("Lỗi: ${e.message}", isError = true)
                    }
                }
            },
            onDismiss = { deletingArticle = null }
        ) {
            Text("Bạn có chắc chắn muốn xóa bài viết '${deletingArticle!!["title"].string}' không?", fontSize = 13.sp, color = FutaColors.Slate)
        }
    }
}

@Composable
private fun NewsArticleRowItem(
    article: JSONValue,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val title = article["title"].string.ifEmpty { "Bài viết FUTA" }
    val category = article["category"].string.ifEmpty { "Tin tức" }
    val status = article["status"].string.lowercase()
    val isPublished = status == "published"
    val coverUrl = article["coverImageUrl"].string
    val views = article["views"].int

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                if (coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Article Details
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FutaColors.Slate,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isPublished) Color(0xFFEAF5EF) else Color(0xFFFFF7ED)
                    ) {
                        Text(
                            text = if (isPublished) "Đã xuất bản" else "Bản nháp",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPublished) FutaColors.BrandGreen else Color(0xFFF97316),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }

                    if (views > 0) {
                        Text(
                            text = "· $views lượt xem",
                            fontSize = 10.sp,
                            color = FutaColors.Slate
                        )
                    }
                }
            }

            // Delete Action
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ArticleEditorSheet(
    initial: JSONValue?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var title by remember { mutableStateOf(initial?.get("title")?.string.orEmpty()) }
    var category by remember { mutableStateOf(initial?.get("category")?.string.orEmpty().ifEmpty { "Tin tức" }) }
    var excerpt by remember { mutableStateOf(initial?.get("excerpt")?.string.orEmpty()) }
    var content by remember { mutableStateOf(initial?.get("content")?.string.orEmpty()) }
    var coverImageUrl by remember { mutableStateOf(initial?.get("coverImageUrl")?.string.orEmpty()) }
    var status by remember { mutableStateOf(initial?.get("status")?.string.orEmpty().ifEmpty { "published" }) }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = if (initial != null) "Chỉnh sửa bài viết" else "Thêm bài viết mới"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tiêu đề bài viết *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = title,
                onValueChange = { title = it },
                placeholder = "Nhập tiêu đề bài viết"
            )

            Text("Chuyên mục *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = category,
                onValueChange = { category = it },
                placeholder = "VD: Tin tức, Thị trường..."
            )

            Text("Đường dẫn ảnh bìa (URL) *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = coverImageUrl,
                onValueChange = { coverImageUrl = it },
                placeholder = "https://..."
            )

            Text("Mô tả tóm tắt (Excerpt) *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = excerpt,
                onValueChange = { excerpt = it },
                placeholder = "Tóm tắt ngắn gọn bài viết"
            )

            Text("Nội dung bài viết *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = content,
                onValueChange = { content = it },
                placeholder = "Nội dung chi tiết..."
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trạng thái:", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                listOf("published" to "Xuất bản", "draft" to "Lưu nháp").forEach { (stKey, stLabel) ->
                    val isSel = status == stKey
                    Surface(
                        shape = CircleShape,
                        color = if (isSel) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                        modifier = Modifier.clickable { status = stKey }
                    ) {
                        Text(
                            text = stLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else FutaColors.Navy,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            FutaButton(
                text = "Lưu bài viết",
                variant = FutaButtonVariant.PRIMARY,
                onClick = {
                    val body = """
                        {
                            "title": "${title.trim()}",
                            "category": "${category.trim()}",
                            "excerpt": "${excerpt.trim()}",
                            "content": "${content.trim()}",
                            "coverImageUrl": "${coverImageUrl.trim()}",
                            "status": "$status"
                        }
                    """.trimIndent()
                    onSave(body)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomepageContentEditorCard(
    settings: JSONValue,
    isSaving: Boolean,
    onSave: (String) -> Unit
) {
    var heroTitle by remember(settings) { mutableStateOf(settings["heroTitle"].string) }
    var heroSubtitle by remember(settings) { mutableStateOf(settings["heroSubtitle"].string) }
    var heroImageUrl by remember(settings) { mutableStateOf(settings["heroImageUrl"].string) }
    var footerDescription by remember(settings) { mutableStateOf(settings["footerDescription"].string) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cấu hình Hero Banner & Chân trang", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

            Text("Tiêu đề Hero Trang chủ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = heroTitle,
                onValueChange = { heroTitle = it },
                placeholder = "Tiêu đề lớn trên trang chủ"
            )

            Text("Phụ đề Hero", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = heroSubtitle,
                onValueChange = { heroSubtitle = it },
                placeholder = "Phụ đề banner"
            )

            Text("Đường dẫn Banner Hero", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = heroImageUrl,
                onValueChange = { heroImageUrl = it },
                placeholder = "https://..."
            )

            Text("Mô tả Chân trang (Footer)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            FutaInput(
                value = footerDescription,
                onValueChange = { footerDescription = it },
                placeholder = "Giới thiệu ngắn dưới footer"
            )

            FutaButton(
                text = if (isSaving) "Đang lưu..." else "Lưu cấu hình trang chủ",
                variant = FutaButtonVariant.PRIMARY,
                enabled = !isSaving,
                onClick = {
                    val body = """
                        {
                            "heroTitle": "$heroTitle",
                            "heroSubtitle": "$heroSubtitle",
                            "heroImageUrl": "$heroImageUrl",
                            "footerDescription": "$footerDescription"
                        }
                    """.trimIndent()
                    onSave(body)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
