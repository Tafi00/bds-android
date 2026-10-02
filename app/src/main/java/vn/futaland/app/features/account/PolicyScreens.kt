package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

/** Slug of the built-in sales policy page (systemConfig.salesPolicy), as on iOS/web. */
const val SALES_POLICY_SLUG = "chinh-sach-ban-hang"

private suspend fun loadCmsSettings(): JSONValue {
    val res = APIClient.get().request("/cms/settings")
    return if (res["data"].isNull) res else res["data"]
}

// ============================================================================
// 5. POLICIES INDEX (Matching iOS PoliciesIndexView)
// ============================================================================
@Composable
fun PoliciesScreen(
    onOpenPolicy: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var documents by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            documents = loadCmsSettings().valueAt("systemConfig.legalDocuments").array.filter { it["isPublished"].bool }
        } catch (e: Exception) {
            error = e.message ?: tr("Không thể tải dữ liệu")
        }
        loading = false
    }

    Scaffold(topBar = { PolicyTopBar("Điều khoản & Chính sách", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                loading -> items(4) { FutaSkeletonBlock(height = 58.dp, radius = 14.dp) }
                error != null -> item {
                    FutaEmptyState(
                        title = "Không thể tải dữ liệu",
                        message = error ?: "",
                        icon = Icons.Default.ErrorOutline,
                        actionButton = { FutaButton(text = "Thử lại", onClick = { reloadKey++ }, variant = FutaButtonVariant.OUTLINE) }
                    )
                }
                else -> {
                    item {
                        PolicyRow(Icons.Default.Description, tr("Chính sách bán hàng")) { onOpenPolicy(SALES_POLICY_SLUG) }
                    }
                    items(documents, key = { it["slug"].string.ifEmpty { it.id } }) { doc ->
                        PolicyRow(
                            Icons.Default.Description,
                            doc["name"].string.ifEmpty { doc["title"].string }.translated("policy")
                        ) { onOpenPolicy(doc["slug"].string) }
                    }
                    // Google Play requires the privacy policy to stay reachable from inside the app.
                    item {
                        PolicyRow(Icons.Default.Language, tr("Chính sách bảo mật (website)")) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APIClient.privacyPolicyUrl)))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    FutaCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = onClick) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            VerbatimText(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PolicyTopBar(title: String, onBack: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại"), tint = FutaColors.Navy)
            }
            Text(title, fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
        }
    }
}

// ============================================================================
// 6. POLICY DETAIL (Matching iOS PolicyView: a published legal document or the sales policy)
// ============================================================================
private data class PolicySection(val number: String, val title: String, val summary: String, val rules: List<String>, val content: String)

private data class PolicyDocument(
    val title: String,
    val version: String,
    val principleTitle: String,
    val principleContent: String,
    val sections: List<PolicySection>
)

/** iOS PolicyView defaults, shown for the sales policy only when the CMS has none. */
private val defaultSalesPolicy = PolicyDocument(
    title = "Chính sách bán hàng & Quy chế hoạt động",
    version = "2026.08.26",
    principleTitle = "Nguyên tắc đối soát cốt lõi",
    principleContent = "Hệ thống ưu tiên hồ sơ hợp lệ được ghi nhận trước. Mọi trường hợp trùng khách hoặc tranh chấp giữ chỗ được xác minh bằng mốc thời gian, trạng thái phê duyệt và lịch sử thao tác lưu trên FUTA Land.",
    sections = listOf(
        PolicySection(
            "01", "Chính sách bảo vệ khách hàng",
            "Xác lập quyền chăm sóc khách hàng minh bạch và hạn chế tranh chấp trùng khách giữa các tư vấn viên.",
            listOf(
                "Tư vấn viên phải ghi nhận khách hàng trên hệ thống trước khi khai thác hoặc tư vấn sản phẩm.",
                "Không tiếp cận, lôi kéo hoặc nhận khách đang trong thời gian được hệ thống ghi nhận cho tư vấn viên khác.",
                "Khi trùng khách, hệ thống đối soát theo thời điểm ghi nhận hợp lệ, lịch sử chăm sóc và dữ liệu liên quan; không căn cứ vào thỏa thuận miệng.",
                "Thông tin khách hàng và lịch sử chăm sóc phải đầy đủ, trung thực, có thể kiểm chứng."
            ), ""
        ),
        PolicySection(
            "02", "Chính sách đăng ký & giữ chỗ",
            "Quy định thứ tự ưu tiên và điều kiện giữ sản phẩm để tránh phát sinh yêu cầu chồng chéo.",
            listOf(
                "Chỉ được đăng ký sản phẩm khi đáp ứng điều kiện bán hàng của dự án và được hệ thống xác nhận quyền bán.",
                "Quyền ưu tiên giữ chỗ được xác định theo hồ sơ hợp lệ được hệ thống ghi nhận trước, không theo tin nhắn hoặc cam kết bên ngoài hệ thống.",
                "Cùng một khách hàng không thể có đồng thời nhiều yêu cầu giữ chỗ còn hiệu lực cho cùng một căn.",
                "Tư vấn viên phải hoàn tất đúng hạn các thủ tục, chứng từ và thông tin khách hàng; quá hạn có thể làm mất quyền ưu tiên."
            ), ""
        ),
        PolicySection(
            "03", "Chính sách bán hàng & hoa hồng",
            "Áp dụng đúng bảng giá, ưu đãi và điều kiện ghi nhận hoa hồng tại thời điểm giao dịch.",
            listOf(
                "Chỉ sử dụng bảng giá, chính sách và chương trình ưu đãi chính thức đang có hiệu lực.",
                "Không tự ý thay đổi giá bán, chiết khấu hoặc cam kết thêm quyền lợi khi chưa được phê duyệt.",
                "Hoa hồng được xác định theo sản phẩm, giao dịch và phiên bản chính sách có hiệu lực tại thời điểm phát sinh.",
                "Tư vấn viên phải cung cấp đầy đủ hồ sơ và dữ liệu đối soát để được ghi nhận quyền hưởng hoa hồng."
            ), ""
        ),
        PolicySection(
            "04", "Tư vấn & xử lý vi phạm",
            "Đảm bảo hoạt động tư vấn minh bạch, đúng thông tin và có cơ chế xử lý khi phát sinh tranh chấp.",
            listOf(
                "Không cung cấp thông tin sai lệch, tài liệu chưa công bố hoặc cam kết ngoài phạm vi được dự án phê duyệt.",
                "Khi có trùng khách hoặc khiếu nại, tư vấn viên phải phối hợp cung cấp lịch sử giao dịch và bằng chứng trên hệ thống.",
                "Tùy mức độ vi phạm, hệ thống có thể cảnh báo, thu hồi quyền bảo vệ khách hàng, hủy quyền đăng ký sản phẩm hoặc tạm ngừng tài khoản."
            ), ""
        )
    )
)

/** CMS content may be HTML (rich-text editor); render it as plain text like iOS. */
private fun policyText(content: String): String =
    if (content.contains("<")) HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() else content

private fun parsePolicy(raw: JSONValue, fallback: PolicyDocument?): PolicyDocument? {
    if (raw.isNull) return fallback
    val sections = raw["sections"].array.map { sec ->
        PolicySection(
            number = sec["number"].string.ifEmpty { "01" },
            title = sec["title"].string.ifEmpty { "Quy định" },
            summary = sec["summary"].string,
            rules = sec["rules"].array.map { it.string }.filter { it.isNotEmpty() },
            content = policyText(sec["content"].string)
        )
    }
    return PolicyDocument(
        title = raw["title"].string.ifEmpty { raw["name"].string.ifEmpty { fallback?.title.orEmpty() } },
        version = raw["version"].string.ifEmpty { fallback?.version.orEmpty() },
        principleTitle = raw["principleTitle"].string.ifEmpty { if (fallback != null) fallback.principleTitle else "" },
        principleContent = raw["principleContent"].string.ifEmpty { if (fallback != null) fallback.principleContent else "" },
        sections = sections.ifEmpty { fallback?.sections.orEmpty() }
    )
}

@Composable
fun PolicyDetailScreen(
    slug: String,
    onContact: () -> Unit,
    onBack: () -> Unit
) {
    var policy by remember { mutableStateOf<PolicyDocument?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(slug, reloadKey) {
        loading = true
        val isSalesPolicy = slug == SALES_POLICY_SLUG
        policy = try {
            val data = loadCmsSettings()
            val sys = data["systemConfig"]
            val document = sys["legalDocuments"].array.firstOrNull { it["slug"].string == slug && it["isPublished"].bool }
            when {
                document != null -> parsePolicy(document, null)
                isSalesPolicy -> parsePolicy(sys["salesPolicy"].takeIf { !it.isNull } ?: data["salesPolicy"], defaultSalesPolicy)
                else -> null
            }
        } catch (_: Exception) {
            if (isSalesPolicy) defaultSalesPolicy else null
        }
        loading = false
    }

    Scaffold(topBar = { PolicyTopBar("Chính sách & Quy chế", onBack) }) { padding ->
        val doc = policy
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                loading -> {
                    item { FutaSkeletonBlock(height = 70.dp, radius = 12.dp) }
                    items(3) { FutaSkeletonBlock(height = 160.dp, radius = 16.dp) }
                }
                doc == null -> item {
                    FutaEmptyState(
                        title = "Không tìm thấy chính sách",
                        message = "Văn bản này chưa được công bố hoặc đã bị gỡ.",
                        icon = Icons.Default.Description,
                        actionButton = { FutaButton(text = "Thử lại", onClick = { reloadKey++ }, variant = FutaButtonVariant.OUTLINE) }
                    )
                }
                else -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Article, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("VĂN BẢN QUY ĐỊNH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                            }
                            VerbatimText(doc.title.translated("policy"), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, lineHeight = 27.sp)
                            if (doc.version.isNotEmpty()) {
                                Text(tr("Phiên bản áp dụng: {0}", doc.version), fontSize = 12.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                    if (doc.principleContent.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FutaColors.BrandOrange.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Balance, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    VerbatimText(doc.principleTitle.translated("policy"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                }
                                VerbatimText(doc.principleContent.translated("policy"), fontSize = 13.sp, color = FutaColors.Slate, lineHeight = 19.sp)
                            }
                        }
                    }
                    items(doc.sections) { sec ->
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(32.dp).background(FutaColors.BrandGreen, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        VerbatimText(sec.number, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    VerbatimText(sec.title.translated("policy"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                }
                                if (sec.summary.isNotEmpty()) {
                                    VerbatimText(sec.summary.translated("policy"), fontSize = 12.5.sp, color = FutaColors.Slate, lineHeight = 18.sp)
                                }
                                if (sec.content.isNotEmpty()) {
                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                        VerbatimText(sec.content.translated("policy"), fontSize = 14.sp, color = FutaColors.Body, lineHeight = 21.sp)
                                    }
                                }
                                if (sec.rules.isNotEmpty()) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    sec.rules.forEach { rule ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                            Spacer(Modifier.width(8.dp))
                                            VerbatimText(rule.translated("policy"), fontSize = 12.5.sp, color = FutaColors.Navy, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Cần làm rõ các quy định?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text("Liên hệ bộ phận Pháp chế & Vận hành FUTA Land để được giải đáp chi tiết.", fontSize = 12.sp, color = FutaColors.Slate)
                                FutaButton(text = "Liên hệ bộ phận hỗ trợ", onClick = onContact, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
