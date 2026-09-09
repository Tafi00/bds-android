package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.FutaCard
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.FutaInput
import vn.futaland.app.navigation.FutaDestinations

data class WorkspaceModuleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val badgeColor: Color,
    val group: String,
    val route: String
)

@Composable
fun WorkspaceScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val session = AppSession.shared
    var search by remember { mutableStateOf("") }

    val allModules = remember {
        listOf(
            // QUẢN TRỊ (13 modules)
            WorkspaceModuleItem("dashboard", "Bảng điều khiển", "Số liệu doanh thu & phân tích", R.drawable.sf_adm_dashboard, Color(0xFF2563EB), "Quản trị", FutaDestinations.ADMIN_DASHBOARD),
            WorkspaceModuleItem("projects", "Quản lý dự án", "Danh sách dự án & sơ đồ tổng thể", R.drawable.sf_quick_projects, FutaColors.BrandGreen, "Quản trị", FutaDestinations.ADMIN_PROJECTS),
            WorkspaceModuleItem("campaigns", "Chương trình bán hàng", "Chính sách chiết khấu & bảng giá", R.drawable.sf_adm_campaigns, Color(0xFFF97316), "Quản trị", FutaDestinations.ADMIN_CAMPAIGNS),
            WorkspaceModuleItem("inventory", "Quản lý sản phẩm", "Kho căn & trạng thái khóa căn ERP", R.drawable.sf_adm_inventory, Color(0xFF0EA5E9), "Quản trị", FutaDestinations.ADMIN_INVENTORY),
            WorkspaceModuleItem("registrations", "Duyệt đăng ký bán", "Xử lý duyệt cọc & giữ chỗ", R.drawable.sf_adm_registrations, Color(0xFFD97706), "Quản trị", FutaDestinations.ADMIN_REGISTRATIONS),
            WorkspaceModuleItem("transactions", "Quản lý giao dịch", "Báo cáo đối soát tài chính", R.drawable.sf_adm_transactions, Color(0xFF10B981), "Quản trị", FutaDestinations.ADMIN_TRANSACTIONS),
            WorkspaceModuleItem("cms", "CMS Bài viết", "Tin tức thị trường & truyền thông", R.drawable.sf_adm_cms, Color(0xFF6366F1), "Quản trị", FutaDestinations.ADMIN_CMS),
            WorkspaceModuleItem("settings", "Thiết lập hệ thống", "Site settings, Telegram, Resend", R.drawable.sf_adm_settings, Color(0xFF64748B), "Quản trị", FutaDestinations.ADMIN_SETTINGS),
            WorkspaceModuleItem("users", "Quản lý người dùng", "Tài khoản nhân sự & khách hàng", R.drawable.sf_adm_users, Color(0xFF8B5CF6), "Quản trị", FutaDestinations.ADMIN_USERS),
            WorkspaceModuleItem("roles", "Vai trò & Phân quyền", "Phân quyền tính năng theo nhóm", R.drawable.sf_adm_roles, Color(0xFF0D9488), "Quản trị", FutaDestinations.ADMIN_ROLES),
            WorkspaceModuleItem("advisors", "Duyệt hồ sơ TVV", "Hồ sơ môi giới & chứng chỉ", R.drawable.sf_adm_advisors, Color(0xFF0284C7), "Quản trị", FutaDestinations.ADMIN_ADVISOR_PROFILES),
            WorkspaceModuleItem("ai", "Training AI", "Kiến thức & kịch bản bot tư vấn", R.drawable.sf_adm_ai, Color(0xFFA855F7), "Quản trị", FutaDestinations.ADMIN_AI),
            WorkspaceModuleItem("zalo", "Marketing Zalo", "Chiến dịch gửi tin nhắn OA", R.drawable.sf_adm_zalo, Color(0xFF0284C7), "Quản trị", FutaDestinations.ADMIN_ZALO),

            // KINH DOANH (7 modules)
            WorkspaceModuleItem("customers", "Khách hàng", "Danh bạ khách hàng tiềm năng", R.drawable.sf_adm_users, Color(0xFF2563EB), "Kinh doanh", FutaDestinations.ADMIN_CUSTOMERS),
            WorkspaceModuleItem("crm", "Chăm sóc KH (CRM)", "Kanban pipeline khách hàng", R.drawable.sf_adm_crm, Color(0xFFF97316), "Kinh doanh", FutaDestinations.CRM),
            WorkspaceModuleItem("contracts", "Hợp đồng giao dịch", "Hợp đồng cọc & mua bán BĐS", R.drawable.sf_adm_contracts, Color(0xFF0D9488), "Kinh doanh", FutaDestinations.ADMIN_CONTRACTS),
            WorkspaceModuleItem("reports", "Báo cáo doanh số", "Báo cáo kinh doanh & hiệu suất", R.drawable.sf_adm_reports, Color(0xFF6366F1), "Kinh doanh", FutaDestinations.ADMIN_REPORTS),
            WorkspaceModuleItem("advisor_products", "Sản phẩm kinh doanh", "Giỏ hàng căn hộ dành cho TVV", R.drawable.sf_quick_projects, FutaColors.BrandGreen, "Kinh doanh", FutaDestinations.SEARCH),
            WorkspaceModuleItem("advisor_registrations", "Đăng ký của tôi", "Lịch sử đăng ký bán căn", R.drawable.sf_adm_registrations, Color(0xFF10B981), "Kinh doanh", FutaDestinations.ADMIN_REGISTRATIONS),
            WorkspaceModuleItem("lucky_wheel", "Vòng quay may mắn", "Game thưởng tri ân khách hàng", R.drawable.sf_quick_wheel, Color(0xFFD97706), "Kinh doanh", FutaDestinations.LUCKY_WHEEL)
        )
    }

    val groups = if (session.role == "admin") listOf("Quản trị", "Kinh doanh") else listOf("Kinh doanh", "Quản trị")

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.sf_chevron_left),
                            contentDescription = "Quay lại",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "Không gian quản trị",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = if (session.role == "admin") "QUẢN TRỊ VIÊN" else "TƯ VẤN VIÊN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0E7643),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F9FC))
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Search field
            item {
                FutaInput(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Tìm mục quản trị...",
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            groups.forEach { grp ->
                val itemsInGroup = allModules.filter {
                    it.group == grp && (search.isEmpty() || it.title.contains(search, ignoreCase = true) || it.subtitle.contains(search, ignoreCase = true))
                }

                if (itemsInGroup.isNotEmpty()) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = grp.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy,
                                    letterSpacing = 0.8.sp
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE2E8F0).copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${itemsInGroup.size} mục",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Slate,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsInGroup.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            WorkspaceModuleCard(
                                                item = item,
                                                onClick = { onNavigate(item.route) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
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
}

@Composable
private fun WorkspaceModuleCard(
    item: WorkspaceModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FutaCard(
        modifier = modifier.height(136.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = item.badgeColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = item.iconRes),
                            contentDescription = null,
                            tint = item.badgeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Icon(
                    painter = painterResource(id = R.drawable.sf_chevron_right_light),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(11.dp)
                )
            }
            Column {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.subtitle,
                    fontSize = 11.sp,
                    color = FutaColors.Slate,
                    maxLines = 2,
                    lineHeight = 15.sp,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
