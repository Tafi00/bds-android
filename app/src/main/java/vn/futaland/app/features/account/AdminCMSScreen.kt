package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.*

// Mirrors iOS Features/CMS/AdminCMSView.swift: news (/cms/admin/news), homepage blocks and legal
// documents (both stored in CMS settings: GET /cms/admin → PUT /cms/settings) and the taxonomy
// editors (AdminCMSTaxonomyScreen.kt).

private sealed interface CmsRoute {
    data object News : CmsRoute
    data class NewsForm(val article: JSONValue?) : CmsRoute
    data object Home : CmsRoute
    data object Legal : CmsRoute
    data class PolicyForm(val docIndex: Int?, val isNew: Boolean = false) : CmsRoute
    data object Taxonomy : CmsRoute
    data class TaxonomyForm(val kind: TaxonomyKind, val item: JSONValue?) : CmsRoute
}

/** Loads the full CMS settings (with systemConfig) like iOS: `/cms/admin`, falling back to `/cms/settings`. */
private suspend fun loadCmsSettings(): JsonElement {
    val res = APIClient.get().request("/cms/admin")
    val settings = res["data"]["settings"]
    if (!settings.isNull) return settings.element
    return APIClient.get().request("/cms/settings")["data"].element
}

/**
 * `PUT /cms/settings`. The backend merges `systemConfig` key by key, so each editor only sends the
 * keys it changed — never the whole systemConfig (which also holds other modules' data).
 */
private suspend fun saveCmsSettings(body: JsonObject) {
    APIClient.get().request("/cms/settings", method = "PUT", bodyJson = body.toString())
}

@Composable
fun AdminCMSScreen(
    onBack: () -> Unit
) {
    val stack = remember { ScreenStack<CmsRoute>() }
    // Bumped after a save so the listing below the form reloads.
    var newsVersion by remember { mutableIntStateOf(0) }
    var legalVersion by remember { mutableIntStateOf(0) }
    var taxonomyVersion by remember { mutableIntStateOf(0) }
    var legalSettings by remember { mutableStateOf<JsonElement?>(null) }

    ScreenStackHost(
        stack = stack,
        base = { CmsHubContent(onBack = onBack, onOpen = { stack.push(it) }) }
    ) { route ->
        when (route) {
            CmsRoute.News -> NewsListScreen(
                version = newsVersion,
                onBack = { stack.pop() },
                onOpen = { stack.push(CmsRoute.NewsForm(it)) },
                onCreate = { stack.push(CmsRoute.NewsForm(null)) }
            )
            is CmsRoute.NewsForm -> NewsFormScreen(route.article, onClose = { stack.pop() }, onSaved = { stack.pop(); newsVersion++ })
            CmsRoute.Home -> HomepageContentScreen(onBack = { stack.pop() })
            CmsRoute.Legal -> LegalDocumentsScreen(
                version = legalVersion,
                onLoaded = { legalSettings = it },
                onBack = { stack.pop() },
                onOpen = { index -> stack.push(CmsRoute.PolicyForm(index)) },
                onCreate = { stack.push(CmsRoute.PolicyForm(null, isNew = true)) }
            )
            is CmsRoute.PolicyForm -> PolicyDocumentFormScreen(
                settings = legalSettings,
                docIndex = route.docIndex,
                isNewDoc = route.isNew,
                onClose = { stack.pop() },
                onSaved = { stack.pop(); legalVersion++ }
            )
            CmsRoute.Taxonomy -> AdminTaxonomiesScreen(
                version = taxonomyVersion,
                onBack = { stack.pop() },
                onOpen = { kind, item -> stack.push(CmsRoute.TaxonomyForm(kind, item)) }
            )
            is CmsRoute.TaxonomyForm -> TaxonomyFormScreen(route.kind, route.item, onClose = { stack.pop() }, onSaved = { stack.pop(); taxonomyVersion++ })
        }
    }
}

@Composable
private fun CmsHubContent(onBack: () -> Unit, onOpen: (CmsRoute) -> Unit) {
    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = "Quản trị nội dung", onBack = onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormSection("Nội dung & Xuất bản") {
                HubRow(Icons.Default.Newspaper, "Quản lý bài viết & tin tức", "Tạo, sửa, xuất bản bài viết") { onOpen(CmsRoute.News) }
                HorizontalDivider(color = FutaColors.PanelDivider)
                HubRow(Icons.Default.Home, "Trang chủ & khối nội dung", "Hero banner, khối AI, popup, liên hệ") { onOpen(CmsRoute.Home) }
                HorizontalDivider(color = FutaColors.PanelDivider)
                HubRow(Icons.AutoMirrored.Filled.MenuBook, "Quy chế & chính sách pháp lý", "Quy chế hoạt động và các trang chính sách") { onOpen(CmsRoute.Legal) }
            }
            FormSection("Danh mục & Phân loại BĐS") {
                HubRow(Icons.Default.Category, "Danh mục hệ thống (Taxonomy)", "Tỉnh thành, loại BĐS, phân khu, nhãn") { onOpen(CmsRoute.Taxonomy) }
            }
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(FutaColors.MintBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            Text(subtitle, fontSize = 11.5.sp, color = FutaColors.Slate)
        }
        Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate, modifier = Modifier.size(18.dp))
    }
}

// ============================================================================
// News
// ============================================================================

private val newsCategories = listOf("Thị trường", "Dự án", "Phong thuỷ", "Kiến thức", "Chính sách", "Sự kiện")

private enum class NewsSort(val title: String) { UPDATED("Mới cập nhật"), TITLE("Tên: A → Z"), PRIORITY("Thứ tự ưu tiên") }

@Composable
private fun NewsListScreen(version: Int, onBack: () -> Unit, onOpen: (JSONValue) -> Unit, onCreate: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var articles by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("all") }
    var category by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf(NewsSort.UPDATED) }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var showFilter by remember { mutableStateOf(false) }
    var draftStatus by remember { mutableStateOf("all") }
    var draftCategory by remember { mutableStateOf("all") }
    var deleting by remember { mutableStateOf<JSONValue?>(null) }

    suspend fun fetch() {
        loading = true
        try {
            val query = mutableMapOf("page" to "$page", "limit" to "20")
            if (status != "all") query["status"] = status
            if (category != "all") query["category"] = category
            if (search.isNotBlank()) query["search"] = search.trim()
            val res = APIClient.get().request("/cms/admin/news", query = query)
            articles = res["data"].array
            totalPages = maxOf(1, res["pagination"]["totalPages"].int)
            total = res["pagination"]["total"].int
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải bài viết")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(search, status, category, page, version) {
        if (search.isNotEmpty()) delay(300)
        fetch()
    }

    val sorted = remember(articles, sort) {
        when (sort) {
            NewsSort.UPDATED -> articles
            NewsSort.TITLE -> articles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["title"].string })
            NewsSort.PRIORITY -> articles.sortedBy { it["sortOrder"].int }
        }
    }
    val filterCount = listOf(status, category).count { it != "all" }
    val applied = buildList {
        if (status != "all") add(AppliedFilter(if (status == "published") tr("Đã xuất bản") else tr("Bản nháp")) { status = "all"; page = 1 })
        if (category != "all") add(AppliedFilter(tr(category)) { category = "all"; page = 1 })
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Bài viết & Tin tức", subtitle = tr("{0} bài viết", total), onBack = onBack) {
                SortMenuButton(NewsSort.entries, sort, { it.title }, { sort = it }, sort == NewsSort.UPDATED)
                BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), filterCount) { draftStatus = status; draftCategory = category; showFilter = true }
                AddHeaderButton(tr("Tạo bài viết"), onCreate)
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { AdminSearchField(search, { search = it; page = 1 }, "Tìm kiếm tiêu đề, chuyên mục…") }
            if (applied.isNotEmpty()) item { AppliedFilterChips(applied) { status = "all"; category = "all"; page = 1 } }
            when {
                loading && articles.isEmpty() -> items(5) { FutaAdminRowSkeleton() }
                loadError != null && articles.isEmpty() -> item { AdminErrorState(loadError.orEmpty(), { scope.launch { fetch() } }) }
                articles.isEmpty() -> item {
                    AdminListEmpty(
                        hasRecords = search.isNotBlank() || filterCount > 0,
                        emptyTitle = "Chưa có bài viết",
                        emptyMessage = "Bấm nút + để tạo bài viết đầu tiên.",
                        onClearFilters = { search = ""; status = "all"; category = "all"; page = 1 },
                        icon = Icons.Default.Newspaper
                    )
                }
                else -> {
                    items(sorted, key = { "news-" + it.id }) { article -> NewsArticleRow(article, onClick = { onOpen(article) }, onDelete = { deleting = article }) }
                    item {
                        PaginationBar(page, totalPages, tr("{0} bài viết", total),
                            { if (page > 1) { page--; scope.launch { listState.scrollToItem(0) } } },
                            { if (page < totalPages) { page++; scope.launch { listState.scrollToItem(0) } } })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    FilterSheet(
        visible = showFilter, title = "Bộ lọc", applyTitle = "Áp dụng",
        canReset = draftStatus != "all" || draftCategory != "all",
        onReset = { draftStatus = "all"; draftCategory = "all" },
        onApply = { status = draftStatus; category = draftCategory; page = 1; showFilter = false },
        onDismiss = { showFilter = false }
    ) {
        FilterChipGroup("Trạng thái", listOf(SelectOption("all", "Tất cả"), SelectOption("published", "Đã xuất bản"), SelectOption("draft", "Bản nháp")), draftStatus) { draftStatus = it }
        FilterChipGroup("Chuyên mục", listOf(SelectOption("all", "Tất cả")) + newsCategories.map { SelectOption(it, it) }, draftCategory) { draftCategory = it }
    }

    val target = deleting
    ConfirmDialog(
        visible = target != null,
        title = "Xác nhận xoá bài viết?",
        message = "Hành động này sẽ xoá hoàn toàn bài viết và không thể khôi phục.",
        confirmText = "Xoá bài viết",
        destructive = true,
        onDismiss = { deleting = null },
        onConfirm = {
            val t = target ?: return@ConfirmDialog
            deleting = null
            scope.launch {
                try {
                    APIClient.get().request("/cms/admin/news/${t.id}", method = "DELETE")
                    ToastCenter.show(tr("Đã xoá bài viết thành công!"))
                    fetch()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể xoá bài viết"), isError = true)
                }
            }
        }
    )
}

@Composable
private fun NewsArticleRow(article: JSONValue, onClick: () -> Unit, onDelete: () -> Unit) {
    val published = article["status"].string == "published"
    AdminRowCard(onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AdminRemoteImage(article["coverImageUrl"].string, Modifier.size(width = 80.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                VerbatimText(article["title"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(article["category"].string.ifEmpty { "Thị trường" }, Color(0xFF2563EB))
                    StatusPill(if (published) "Xuất bản" else "Bản nháp", if (published) Color(0xFF16A34A) else Color(0xFFF97316))
                    if (article["isFeatured"].bool) Icon(Icons.Default.Star, tr("Bài viết nổi bật"), tint = Color(0xFFEAB308), modifier = Modifier.size(14.dp))
                }
                Text(tr("Cập nhật {0}", SalesFormatters.dateTime(article["updatedAt"].string)), fontSize = 11.sp, color = FutaColors.Slate)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, tr("Xoá bài"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NewsFormScreen(article: JSONValue?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isEdit = article != null
    val init = remember {
        listOf(
            article?.get("title")?.string.orEmpty(),
            article?.get("slug")?.string.orEmpty(),
            article?.get("category")?.string?.ifEmpty { null } ?: "Thị trường",
            article?.get("excerpt")?.string.orEmpty(),
            article?.get("content")?.string.orEmpty(),
            article?.get("coverImageUrl")?.string.orEmpty(),
            article?.get("status")?.string?.ifEmpty { null } ?: "draft",
            (article?.get("isFeatured")?.bool ?: false).toString(),
            "${article?.get("sortOrder")?.int ?: 0}"
        )
    }
    var title by remember { mutableStateOf(init[0]) }
    var slug by remember { mutableStateOf(init[1]) }
    var category by remember { mutableStateOf(init[2]) }
    var excerpt by remember { mutableStateOf(init[3]) }
    var content by remember { mutableStateOf(init[4]) }
    var cover by remember { mutableStateOf(init[5]) }
    var status by remember { mutableStateOf(init[6]) }
    var featured by remember { mutableStateOf(init[7] == "true") }
    var sortOrder by remember { mutableStateOf(init[8]) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showStatusConfirm by remember { mutableStateOf(false) }
    val uploader = rememberImageUploader("news_cover") { cover = it }

    val dirty = listOf(title, slug, category, excerpt, content, cover, status, featured.toString(), sortOrder) != init
    fun close() { if (dirty && !saving) showDiscard = true else onClose() }
    BackHandler { close() }

    fun submit() {
        val body = buildJsonObject {
            put("title", title.trim())
            val cleanSlug = slug.trim().lowercase().replace(Regex("\\s+"), "-")
            if (cleanSlug.isNotEmpty()) put("slug", cleanSlug)
            put("category", category)
            put("excerpt", excerpt.trim())
            put("content", content.trim())
            put("coverImageUrl", cover.trim())
            put("status", status)
            put("isFeatured", featured)
            put("sortOrder", sortOrder.toIntOrNull() ?: 0)
        }.toString()
        scope.launch {
            saving = true
            try {
                if (article != null) APIClient.get().request("/cms/admin/news/${article.id}", method = "PUT", bodyJson = body)
                else APIClient.get().request("/cms/admin/news", method = "POST", bodyJson = body)
                ToastCenter.show(if (isEdit) tr("Đã cập nhật bài viết thành công!") else tr("Đã tạo bài viết thành công!"))
                onSaved()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu bài viết"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    fun save() {
        error = when {
            title.trim().length < 3 -> tr("Tiêu đề phải có ít nhất 3 ký tự")
            excerpt.trim().length < 10 -> tr("Mô tả tóm tắt cần ít nhất 10 ký tự")
            content.trim().length < 20 -> tr("Nội dung cần ít nhất 20 ký tự")
            cover.isBlank() -> tr("Vui lòng tải lên ảnh bìa")
            uploader.uploading -> tr("Ảnh đang được tải lên, vui lòng đợi")
            else -> null
        }
        if (error != null) return
        // Publishing / unpublishing changes what the public sees: confirm first.
        if (status != init[6] && (isEdit || status == "published")) showStatusConfirm = true else submit()
    }

    val published = status == "published"
    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(
                title = if (isEdit) "Sửa bài viết" else "Thêm bài viết",
                subtitle = if (isEdit) tr("Cập nhật {0}", SalesFormatters.dateTime(article!!["updatedAt"].string)) else null,
                onBack = { close() }
            ) { StatusPill(if (published) "Đã xuất bản" else "Bản nháp", if (published) Color(0xFF16A34A) else Color(0xFFF97316)) }
        },
        bottomBar = { FormActionBar(if (isEdit) "Lưu thay đổi" else "Tạo bài viết", saving, enabled = !deleting && (dirty || !isEdit), onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            FormSection("Thông tin cơ bản") {
                FormTextField("Tiêu đề bài viết", title, { title = it }, placeholder = "Nhập tiêu đề bài viết...", required = true)
                FormTextField("Đường dẫn (Slug)", slug, { slug = it }, placeholder = "Tự động tạo theo tiêu đề nếu để trống")
                AdminSelectField("Chuyên mục", category, (newsCategories + listOf(category)).distinct().map { SelectOption(it, it) }, { category = it })
                AdminSelectField("Trạng thái", status, listOf(SelectOption("draft", "Bản nháp"), SelectOption("published", "Đã xuất bản")), { status = it })
                FormToggle("Bài viết nổi bật", featured, { featured = it })
                FormTextField("Thứ tự ưu tiên", sortOrder, { sortOrder = it.filter(Char::isDigit).take(5) }, placeholder = "0", keyboardType = KeyboardType.Number)
            }
            FormSection("Ảnh bìa") {
                ImageUploadField("Ảnh bìa (*)", cover, uploader.uploading, onPick = uploader.pick, onRemove = { cover = "" }, previewHeight = 170.dp)
            }
            FormSection("Tóm tắt nội dung (*)") {
                FormTextField("Mô tả ngắn hiển thị ở danh sách bài viết", excerpt, { excerpt = it }, placeholder = "Nhập mô tả tóm tắt nội dung bài viết…", required = true, multiline = true)
            }
            FormSection("Nội dung bài viết (*)") {
                FutaTextArea(
                    value = content, onValueChange = { content = it },
                    placeholder = "Nhập nội dung chi tiết bài viết, phân tích thị trường hoặc thông tin dự án…",
                    minLines = 10, maxLines = 30
                )
            }
            if (isEdit) {
                DetailSection("Thông tin hệ thống", Icons.Default.Info) {
                    InfoRow("Tác giả", article!!["author"]["name"].string, verbatim = true)
                    InfoRow("Ngày tạo", SalesFormatters.dateTime(article["createdAt"].string))
                    if (article["publishedAt"].string.isNotEmpty()) InfoRow("Ngày xuất bản", SalesFormatters.dateTime(article["publishedAt"].string))
                }
                DangerZoneCard(
                    title = "Xoá bài viết",
                    message = "Hành động này sẽ xoá hoàn toàn bài viết và không thể khôi phục.",
                    buttonText = if (deleting) "Đang xoá…" else "Xoá bài viết",
                    enabled = !deleting && !saving,
                    onClick = { showDelete = true }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
    ConfirmDialog(
        visible = showStatusConfirm,
        title = if (published) "Xuất bản bài viết?" else "Gỡ xuất bản bài viết?",
        message = if (published) "Bài viết sẽ hiển thị công khai trên website và ứng dụng." else "Bài viết sẽ chuyển về bản nháp và không còn hiển thị công khai.",
        confirmText = if (published) "Xuất bản" else "Gỡ xuất bản",
        destructive = !published,
        onDismiss = { showStatusConfirm = false },
        onConfirm = { showStatusConfirm = false; submit() }
    )
    ConfirmDialog(
        visible = showDelete,
        title = "Xác nhận xoá bài viết?",
        message = "Hành động này sẽ xoá hoàn toàn bài viết và không thể khôi phục.",
        confirmText = "Xoá bài viết",
        destructive = true,
        onDismiss = { showDelete = false },
        onConfirm = {
            showDelete = false
            scope.launch {
                deleting = true
                try {
                    APIClient.get().request("/cms/admin/news/${article!!.id}", method = "DELETE")
                    ToastCenter.show(tr("Đã xoá bài viết thành công!"))
                    onSaved()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể xoá bài viết"), isError = true)
                } finally {
                    deleting = false
                }
            }
        }
    )
}

// ============================================================================
// Homepage content
// ============================================================================

private val homeTextKeys = listOf("heroTitle", "heroSubtitle", "heroDescription", "aiSectionTitle", "aiSectionSubtitle", "footerDescription")
private val homeImageKeys = listOf("heroImageUrl", "aiSectionImageUrl")

@Composable
private fun HomepageContentScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<JsonElement?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var top by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var homepage by remember { mutableStateOf<JsonElement>(JsonObject(emptyMap())) }
    var initialTop by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var initialHomepage by remember { mutableStateOf<JsonElement>(JsonObject(emptyMap())) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    suspend fun fetch() {
        try {
            val s = loadCmsSettings()
            val t = (homeTextKeys + homeImageKeys).associateWith { s.stringAt(it) }
            val hc = s.at("systemConfig.homepageContent") as? JsonObject ?: JsonObject(emptyMap())
            top = t; initialTop = t; homepage = hc; initialHomepage = hc
            loaded = s
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        }
    }
    LaunchedEffect(Unit) { fetch() }

    val dirty = loaded != null && (top != initialTop || homepage != initialHomepage)
    fun close() { if (dirty && !saving) showDiscard = true else onBack() }
    BackHandler { close() }

    fun text(key: String) = top[key].orEmpty()
    fun setText(key: String, v: String) { top = top + (key to v) }
    fun hc(path: String) = homepage.stringAt(path)
    fun setHc(path: String, v: JsonElement) { homepage = homepage.withValueAt(path, v) }
    fun setHcText(path: String, v: String) = setHc(path, JsonPrimitive(v))

    val heroUploader = rememberImageUploader("hero") { setText("heroImageUrl", it) }
    val aiUploader = rememberImageUploader("ai_section") { setText("aiSectionImageUrl", it) }
    val project0Uploader = rememberImageUploader("project_section") { setHcText("projectSections.0.bannerImageUrl", it) }
    val project1Uploader = rememberImageUploader("project_section") { setHcText("projectSections.1.bannerImageUrl", it) }
    val popupUploader = rememberImageUploader("popup_banner") { setHcText("popupBanner.imageUrl", it) }

    fun save() {
        // Text fields can't be sent empty (backend min length 1); a field that had a value can't be cleared.
        val cleared = homeTextKeys.firstOrNull { initialTop[it].orEmpty().isNotBlank() && text(it).isBlank() }
        val link = hc("popupBanner.linkUrl").trim()
        error = when {
            cleared != null -> tr("Vui lòng không để trống các trường đã có nội dung")
            link.isNotEmpty() && !(link.startsWith("/") || link.startsWith("http://") || link.startsWith("https://")) -> tr("Liên kết phải là URL http(s) hoặc đường dẫn nội bộ")
            listOf(heroUploader, aiUploader, project0Uploader, project1Uploader, popupUploader).any { it.uploading } -> tr("Ảnh đang được tải lên, vui lòng đợi")
            else -> null
        }
        if (error != null) return

        // Normalize the blocks the strict backend schema checks.
        var hcOut = homepage
        if (hcOut.at("projectSections") != null) {
            for (i in 0..1) {
                hcOut = hcOut.withValueAt("projectSections.$i.featuredTitle", JsonPrimitive(hcOut.stringAt("projectSections.$i.featuredTitle")))
                hcOut = hcOut.withValueAt("projectSections.$i.bannerImageUrl", JsonPrimitive(hcOut.stringAt("projectSections.$i.bannerImageUrl")))
            }
            hcOut = hcOut.withValueAt("projectSections", JsonArray((hcOut.at("projectSections") as JsonArray).take(2)))
        }
        if (hcOut.at("contactInfo") != null) {
            for (k in listOf("phone", "email", "address")) hcOut = hcOut.withValueAt("contactInfo.$k", JsonPrimitive(hcOut.stringAt("contactInfo.$k")))
        }
        if (hcOut.at("popupBanner") != null) {
            val max = hcOut.stringAt("popupBanner.maxDisplaysPerDay").toIntOrNull() ?: 3
            hcOut = hcOut.withValueAt("popupBanner.maxDisplaysPerDay", JsonPrimitive(max.coerceIn(0, 100)))
        }

        val body = buildJsonObject {
            homeTextKeys.forEach { k -> text(k).trim().takeIf { it.isNotEmpty() }?.let { put(k, it) } }
            homeImageKeys.forEach { k -> put(k, text(k).trim()) }
            if (homepage != initialHomepage) {
                put("systemConfig", buildJsonObject { put("homepageContent", hcOut) })
            }
        }
        scope.launch {
            saving = true
            try {
                saveCmsSettings(body)
                ToastCenter.show(tr("Đã lưu cài đặt khối trang chủ thành công!"))
                fetch()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu cài đặt"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = "Trang chủ & Khối nội dung", onBack = { close() }) },
        bottomBar = { if (loaded != null) FormActionBar("Lưu thay đổi", saving, enabled = dirty, onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        when {
            loaded == null && loadError == null -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { FutaSkeletonBlock(height = 140.dp, radius = 16.dp) }
            }
            loaded == null -> AdminErrorState(loadError.orEmpty(), { loadError = null; scope.launch { fetch() } }, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormErrorBanner(error)
                FormSection("Hero Banner (Trang chủ)") {
                    FormTextField("Tiêu đề Hero", text("heroTitle"), { setText("heroTitle", it) })
                    FormTextField("Phụ đề Hero", text("heroSubtitle"), { setText("heroSubtitle", it) })
                    FormTextField("Mô tả Hero", text("heroDescription"), { setText("heroDescription", it) }, multiline = true)
                    ImageUploadField("Ảnh Hero Banner", text("heroImageUrl"), heroUploader.uploading, heroUploader.pick, { setText("heroImageUrl", "") })
                }
                FormSection("Khối Trí tuệ nhân tạo (AI Section)") {
                    FormTextField("Tiêu đề khối AI", text("aiSectionTitle"), { setText("aiSectionTitle", it) })
                    FormTextField("Phụ đề khối AI", text("aiSectionSubtitle"), { setText("aiSectionSubtitle", it) })
                    ImageUploadField("Ảnh khối AI", text("aiSectionImageUrl"), aiUploader.uploading, aiUploader.pick, { setText("aiSectionImageUrl", "") })
                }
                FormSection("Khối dự án nổi bật (Project Sections)") {
                    FormTextField("Dự án 1 - Tiêu đề", hc("projectSections.0.featuredTitle"), { setHcText("projectSections.0.featuredTitle", it) })
                    ImageUploadField("Dự án 1 - Ảnh banner", hc("projectSections.0.bannerImageUrl"), project0Uploader.uploading, project0Uploader.pick, { setHcText("projectSections.0.bannerImageUrl", "") })
                    HorizontalDivider(color = FutaColors.PanelDivider)
                    FormTextField("Dự án 2 - Tiêu đề", hc("projectSections.1.featuredTitle"), { setHcText("projectSections.1.featuredTitle", it) })
                    ImageUploadField("Dự án 2 - Ảnh banner", hc("projectSections.1.bannerImageUrl"), project1Uploader.uploading, project1Uploader.pick, { setHcText("projectSections.1.bannerImageUrl", "") })
                }
                FormSection("Banner quảng cáo Popup") {
                    FormToggle("Bật popup banner", hc("popupBanner.enabled") == "true", { setHc("popupBanner.enabled", JsonPrimitive(it)) })
                    ImageUploadField("Ảnh popup", hc("popupBanner.imageUrl"), popupUploader.uploading, popupUploader.pick, { setHcText("popupBanner.imageUrl", "") })
                    FormTextField("Liên kết khi click", hc("popupBanner.linkUrl"), { setHcText("popupBanner.linkUrl", it) }, placeholder = "https://… hoặc /du-an/…")
                    FormTextField(
                        "Số lần hiển thị tối đa / ngày", hc("popupBanner.maxDisplaysPerDay"),
                        { v -> setHc("popupBanner.maxDisplaysPerDay", JsonPrimitive(v.filter(Char::isDigit).take(3))) },
                        keyboardType = KeyboardType.Number
                    )
                }
                FormSection("Thông tin liên hệ & Chân trang") {
                    FormTextField("Số điện thoại hotline", hc("contactInfo.phone"), { setHcText("contactInfo.phone", it) }, keyboardType = KeyboardType.Phone)
                    FormTextField("Email hỗ trợ", hc("contactInfo.email"), { setHcText("contactInfo.email", it) }, keyboardType = KeyboardType.Email)
                    FormTextField("Địa chỉ văn phòng", hc("contactInfo.address"), { setHcText("contactInfo.address", it) })
                    FormTextField("Mô tả chân trang", text("footerDescription"), { setText("footerDescription", it) }, multiline = true)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onBack() })
}

// ============================================================================
// Legal documents & operating policy (systemConfig.salesPolicy / systemConfig.legalDocuments)
// ============================================================================

@Composable
private fun LegalDocumentsScreen(
    version: Int,
    onLoaded: (JsonElement) -> Unit,
    onBack: () -> Unit,
    onOpen: (Int?) -> Unit,
    onCreate: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<JsonElement?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("all") }
    var sortByName by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var draftVisibility by remember { mutableStateOf("all") }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun fetch() {
        try {
            val s = loadCmsSettings()
            settings = s
            onLoaded(s)
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        }
    }
    LaunchedEffect(version) { fetch() }

    val docs = (settings.at("systemConfig.legalDocuments") as? JsonArray)?.toList().orEmpty()
    val policy = settings.at("systemConfig.salesPolicy")
    val indexed = docs.withIndex().filter { (_, d) ->
        val name = d.stringAt("name").ifEmpty { d.stringAt("title") }
        (search.isBlank() || "$name ${d.stringAt("slug")} ${d.stringAt("title")}".contains(search.trim(), ignoreCase = true)) &&
            (visibility == "all" || (visibility == "published") == (d.stringAt("isPublished") == "true"))
    }.let { list -> if (sortByName) list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value.stringAt("name") }) else list }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Quy chế & Chính sách", onBack = onBack) {
                SortMenuButton(listOf(false, true), sortByName, { if (it) "Tên: A → Z" else "Thứ tự hiển thị" }, { sortByName = it }, !sortByName)
                BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), if (visibility != "all") 1 else 0) { draftVisibility = visibility; showFilter = true }
                if (settings != null) AddHeaderButton(tr("Thêm tài liệu mới"), onCreate)
            }
        }
    ) { padding ->
        when {
            settings == null && loadError == null -> AdminListSkeleton(modifier = Modifier.padding(padding))
            settings == null -> AdminErrorState(loadError.orEmpty(), { loadError = null; scope.launch { fetch() } }, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Quy chế hoạt động", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate, modifier = Modifier.padding(start = 4.dp))
                }
                item {
                    AdminRowCard({ onOpen(null) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Gavel, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                if (policy.stringAt("title").isNotEmpty()) VerbatimText(policy.stringAt("title"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                else Text("Chưa cấu hình quy chế", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text(tr("Phiên bản {0} • {1} điều", policy.stringAt("version").ifEmpty { "-" }, (policy.at("sections") as? JsonArray)?.size ?: 0), fontSize = 12.sp, color = FutaColors.Slate)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate)
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.height(4.dp))
                        Text("Danh sách chính sách & tài liệu pháp lý", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate, modifier = Modifier.padding(start = 4.dp))
                        AdminSearchField(search, { search = it }, "Tìm theo tên, đường dẫn…")
                        if (visibility != "all") {
                            AppliedFilterChips(listOf(AppliedFilter(if (visibility == "published") tr("Đã bật") else tr("Đã tắt")) { visibility = "all" })) { visibility = "all" }
                        }
                    }
                }
                if (indexed.isEmpty()) item {
                    AdminListEmpty(docs.isNotEmpty(), "Chưa có tài liệu", "Bấm nút + để thêm trang chính sách.", { search = ""; visibility = "all" }, Icons.Default.Description)
                }
                items(indexed, key = { "doc-" + it.index + it.value.stringAt("id") }) { (index, doc) ->
                    val on = doc.stringAt("isPublished") == "true"
                    AdminRowCard({ onOpen(index) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                VerbatimText(doc.stringAt("name").ifEmpty { doc.stringAt("title") }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                VerbatimText("/chinh-sach/" + doc.stringAt("slug"), fontSize = 12.sp, color = FutaColors.Slate)
                            }
                            StatusPill(if (on) "Đã bật" else "Đã tắt", if (on) Color(0xFF16A34A) else FutaColors.Slate)
                            IconButton(onClick = { deletingIndex = index }, enabled = !busy && docs.size > 1, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.DeleteOutline, tr("Xóa"), tint = if (docs.size > 1) Color(0xFFDC2626) else FutaColors.Slate.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    FilterSheet(
        visible = showFilter, title = "Bộ lọc", applyTitle = "Áp dụng",
        canReset = draftVisibility != "all", onReset = { draftVisibility = "all" },
        onApply = { visibility = draftVisibility; showFilter = false }, onDismiss = { showFilter = false }
    ) {
        FilterChipGroup("Truy cập công khai", listOf(SelectOption("all", "Tất cả"), SelectOption("published", "Đã bật"), SelectOption("hidden", "Đã tắt")), draftVisibility) { draftVisibility = it }
    }

    val index = deletingIndex
    ConfirmDialog(
        visible = index != null,
        title = "Xóa tài liệu?",
        message = tr("Trang chính sách \"{0}\" sẽ bị gỡ khỏi website.", index?.let { docs.getOrNull(it)?.stringAt("name") }.orEmpty()),
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { deletingIndex = null },
        onConfirm = {
            val i = index ?: return@ConfirmDialog
            deletingIndex = null
            scope.launch {
                busy = true
                try {
                    val remaining = docs.filterIndexed { idx, _ -> idx != i }
                    saveCmsSettings(buildJsonObject { put("systemConfig", buildJsonObject { put("legalDocuments", JsonArray(remaining)) }) })
                    ToastCenter.show(tr("Đã xóa tài liệu"))
                    fetch()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể xóa tài liệu"), isError = true)
                } finally {
                    busy = false
                }
            }
        }
    )
}

private data class LegalSectionDraft(val number: String, val title: String, val content: String, val extra: JsonObject)

private val policyTextKeys = listOf("eyebrow", "title", "description", "version", "principleTitle", "principleContent", "contactTitle", "contactDescription", "contactButtonLabel")

/**
 * Editor for the operating policy ([docIndex] == null, not new) or one legal document. Saves
 * immediately through `PUT /cms/settings` with only `systemConfig.salesPolicy` or the full
 * `systemConfig.legalDocuments` list.
 */
@Composable
private fun PolicyDocumentFormScreen(settings: JsonElement?, docIndex: Int?, isNewDoc: Boolean, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isPolicy = docIndex == null && !isNewDoc
    val docs = remember { (settings.at("systemConfig.legalDocuments") as? JsonArray)?.toList().orEmpty() }
    val source: JsonObject = remember {
        (if (isPolicy) settings.at("systemConfig.salesPolicy") else docIndex?.let { docs.getOrNull(it) }) as? JsonObject ?: JsonObject(emptyMap())
    }
    val initFields = remember {
        val defaults = mapOf("version" to "1.0", "eyebrow" to "Chính sách FUTA Land")
        policyTextKeys.associateWith { k -> source.stringAt(k).ifEmpty { if (source.isEmpty()) defaults[k].orEmpty() else "" } } +
            mapOf("name" to source.stringAt("name"), "slug" to source.stringAt("slug"))
    }
    val initSections = remember {
        ((source["sections"] as? JsonArray)?.map { el ->
            val o = el as? JsonObject ?: JsonObject(emptyMap())
            LegalSectionDraft(o.stringAt("number"), o.stringAt("title"), o.stringAt("content"), o)
        }).orEmpty().ifEmpty { listOf(LegalSectionDraft("1", "Điều khoản chung", "", JsonObject(emptyMap()))) }
    }
    val initPublished = remember { if (source.isEmpty()) true else source.stringAt("isPublished") == "true" }
    var fields by remember { mutableStateOf(initFields) }
    var sections by remember { mutableStateOf(initSections) }
    var isPublished by remember { mutableStateOf(initPublished) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var removingSection by remember { mutableStateOf<Int?>(null) }

    val dirty = fields != initFields || sections != initSections || isPublished != initPublished
    fun close() { if (dirty && !saving) showDiscard = true else onClose() }
    BackHandler { close() }
    fun f(k: String) = fields[k].orEmpty()
    fun set(k: String, v: String) { fields = fields + (k to v) }
    fun setSection(i: Int, s: LegalSectionDraft) { sections = sections.toMutableList().also { it[i] = s } }

    fun save() {
        val slug = f("slug").trim().lowercase()
        val slugTaken = docs.withIndex().any { (i, d) -> i != docIndex && d.stringAt("slug") == slug }
        error = when {
            !isPolicy && f("name").isBlank() -> tr("Vui lòng nhập tên tài liệu")
            !isPolicy && !Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(slug) -> tr("Đường dẫn chỉ gồm chữ thường, số và dấu gạch ngang")
            !isPolicy && slugTaken -> tr("Đường dẫn trang chính sách không được trùng nhau")
            f("title").isBlank() -> tr("Vui lòng nhập tiêu đề hiển thị")
            sections.isEmpty() -> tr("Cần ít nhất một điều khoản")
            sections.any { it.number.isBlank() || it.title.isBlank() || it.content.isBlank() } -> tr("Mỗi điều khoản cần có số thứ tự, tiêu đề và nội dung")
            else -> null
        }
        if (error != null) return
        val obj = buildJsonObject {
            if (!isPolicy) {
                put("id", source.stringAt("id").ifEmpty { java.util.UUID.randomUUID().toString() })
                put("name", f("name").trim())
                put("slug", slug)
                put("isPublished", isPublished)
            }
            policyTextKeys.forEach { k -> put(k, f(k).trim()) }
            put("sections", JsonArray(sections.map { s ->
                JsonObject(s.extra + mapOf(
                    "number" to JsonPrimitive(s.number.trim()),
                    "title" to JsonPrimitive(s.title.trim()),
                    "content" to JsonPrimitive(s.content.trim())
                ))
            }))
        }
        val systemConfig = buildJsonObject {
            if (isPolicy) put("salesPolicy", obj)
            else put("legalDocuments", JsonArray(if (docIndex != null) docs.toMutableList().also { it[docIndex] = obj } else docs + obj))
        }
        scope.launch {
            saving = true
            try {
                saveCmsSettings(buildJsonObject { put("systemConfig", systemConfig) })
                ToastCenter.show(tr("Đã lưu quy chế & chính sách thành công!"))
                onSaved()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(
                title = when { isPolicy -> "Quy chế hoạt động"; isNewDoc -> "Thêm chính sách"; else -> "Sửa chính sách" },
                subtitle = if (!isPolicy && !isNewDoc) "/chinh-sach/" + source.stringAt("slug") else null,
                onBack = { close() }
            ) { if (!isPolicy) StatusPill(if (isPublished) "Đã bật" else "Đã tắt", if (isPublished) Color(0xFF16A34A) else FutaColors.Slate) }
        },
        bottomBar = { FormActionBar(if (isNewDoc) "Thêm tài liệu" else "Lưu thay đổi", saving, enabled = dirty || isNewDoc, onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            if (!isPolicy) {
                FormSection("Tài liệu chính sách") {
                    FormTextField("Tên tài liệu", f("name"), { set("name", it) }, placeholder = "Ví dụ: Chính sách bảo mật", required = true)
                    FormTextField("Đường dẫn (Slug)", f("slug"), { set("slug", it) }, placeholder = "Ví dụ: bao-mat-thong-tin", required = true)
                    FormToggle("Cho phép truy cập công khai", isPublished, { isPublished = it })
                }
            }
            FormSection("Nội dung trang") {
                FormTextField("Tiêu đề nhỏ (eyebrow)", f("eyebrow"), { set("eyebrow", it) })
                FormTextField("Tiêu đề hiển thị", f("title"), { set("title", it) }, required = true)
                FormTextField("Phiên bản", f("version"), { set("version", it) }, placeholder = "1.0")
                FormTextField("Mô tả tổng quát", f("description"), { set("description", it) }, multiline = true)
                FormTextField("Tiêu đề nguyên tắc", f("principleTitle"), { set("principleTitle", it) })
                FormTextField("Nội dung nguyên tắc", f("principleContent"), { set("principleContent", it) }, multiline = true)
            }
            FormSection(tr("Các điều khoản ({0})", sections.size)) {
                sections.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = FutaColors.PanelDivider)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Điều {0}", i + 1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                        if (sections.size > 1) {
                            IconButton(onClick = { removingSection = i }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.DeleteOutline, tr("Xóa"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    FormTextField("Số thứ tự", s.number, { setSection(i, s.copy(number = it)) })
                    FormTextField("Tiêu đề điều khoản", s.title, { setSection(i, s.copy(title = it)) }, required = true)
                    FormTextField("Nội dung", s.content, { setSection(i, s.copy(content = it)) }, required = true, multiline = true)
                }
                if (sections.size < 50) {
                    FutaButton(
                        text = "Thêm điều khoản", icon = Icons.Default.Add, variant = FutaButtonVariant.OUTLINE, height = 38.dp,
                        onClick = { sections = sections + LegalSectionDraft("${sections.size + 1}", "", "", JsonObject(emptyMap())) }
                    )
                }
            }
            FormSection("Khối liên hệ") {
                FormTextField("Tiêu đề liên hệ", f("contactTitle"), { set("contactTitle", it) })
                FormTextField("Mô tả liên hệ", f("contactDescription"), { set("contactDescription", it) }, multiline = true)
                FormTextField("Nhãn nút liên hệ", f("contactButtonLabel"), { set("contactButtonLabel", it) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
    val removing = removingSection
    ConfirmDialog(
        visible = removing != null,
        title = "Xóa điều khoản?",
        message = "Điều khoản sẽ bị xóa khỏi tài liệu khi bạn lưu.",
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { removingSection = null },
        onConfirm = {
            val i = removing ?: return@ConfirmDialog
            sections = sections.filterIndexed { idx, _ -> idx != i }
            removingSection = null
        }
    )
}
