package vn.futaland.app.features.account

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

data class WorkspaceModuleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int? = null,
    val vectorIcon: ImageVector? = null,
    val badgeColor: Color,
    val group: String,
    val route: String,
    val requiredPermissions: List<String> = emptyList(),
    val requiresAdmin: Boolean = false,
    val requiresStaff: Boolean = false,
    val hideIfStaff: Boolean = false
) {
    fun isVisible(session: AppSession): Boolean {
        if (requiresAdmin && session.role != "admin") return false
        if (requiresStaff && !session.isInternalStaff && session.role != "admin") return false
        if (hideIfStaff && session.isInternalStaff) return false
        if (requiredPermissions.isNotEmpty()) {
            if (!session.isAuthenticated) return false
            if (session.role == "admin") return true
            return requiredPermissions.any { session.hasPermission(it) }
        }
        return true
    }
}

@Composable
fun WorkspaceScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val session = AppSession.shared
    val permissions by session.permissions.collectAsState()

    LaunchedEffect(Unit) {
        session.fetchPermissions()
    }

    // Exactly matching iOS WorkspaceModule (labels, icons, colors, descriptions, routes)
    val allModules = remember(session.role) {
        listOf(
            // TỔNG QUAN
            WorkspaceModuleItem(
                id = "dashboard",
                title = "Bảng điều khiển",
                subtitle = if (session.role == "admin") "Số liệu doanh thu, đơn hàng & người dùng" else "Bảng điều khiển & hiệu suất tư vấn viên",
                iconRes = R.drawable.sf_adm_dashboard,
                badgeColor = Color(0xFF2563EB),
                group = "Tổng quan",
                route = if (session.role == "admin") FutaDestinations.ADMIN_DASHBOARD else FutaDestinations.ADVISOR
            ),

            // QUẢN TRỊ VIÊN (Chỉ dành cho Admin - Thứ tự chuẩn Web 100%)
            WorkspaceModuleItem(
                id = "users",
                title = "Quản lý người dùng",
                subtitle = "Danh sách tài khoản & phân quyền người dùng",
                iconRes = R.drawable.sf_adm_users,
                badgeColor = Color(0xFF8B5CF6),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_USERS,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "roles",
                title = "Vai trò & Phân quyền",
                subtitle = "Ma trận phân quyền & chức năng hệ thống",
                iconRes = R.drawable.sf_adm_roles,
                badgeColor = Color(0xFF0D9488),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_ROLES,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "projects",
                title = "Quản lý dự án",
                subtitle = "Danh sách & thông tin các dự án mở bán",
                iconRes = R.drawable.sf_quick_projects,
                badgeColor = FutaColors.BrandGreen,
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_PROJECTS,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "campaigns",
                title = "Chương trình bán hàng",
                subtitle = "Chính sách bán hàng & sự kiện mở bán",
                iconRes = R.drawable.sf_adm_campaigns,
                badgeColor = Color(0xFFF97316),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_CAMPAIGNS,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "game_admin",
                title = "Quản trị vòng quay",
                subtitle = "Cấu hình giải thưởng & tỷ lệ vòng quay",
                iconRes = R.drawable.sf_quick_wheel,
                badgeColor = Color(0xFFD97706),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_LUCKY_WHEEL,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "inventory",
                title = "Kho sản phẩm",
                subtitle = "Quản lý bảng hàng, giỏ căn & giá ERP",
                iconRes = R.drawable.sf_adm_inventory,
                badgeColor = Color(0xFF0EA5E9),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_INVENTORY,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "registrations",
                title = "Duyệt đăng ký bán & Giữ chỗ",
                subtitle = "Xử lý duyệt giữ chỗ & khóa căn tự động",
                iconRes = R.drawable.sf_adm_registrations,
                badgeColor = Color(0xFFD97706),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_REGISTRATIONS,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "transactions",
                title = "Quản lý giao dịch",
                subtitle = "Báo cáo doanh số & đối soát giao dịch",
                iconRes = R.drawable.sf_adm_transactions,
                badgeColor = Color(0xFF10B981),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_TRANSACTIONS,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "advisors",
                title = "Duyệt hồ sơ TVV",
                subtitle = "Duyệt hồ sơ & thông tin chuyên viên",
                iconRes = R.drawable.sf_adm_advisors,
                badgeColor = Color(0xFF0284C7),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_ADVISOR_PROFILES,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "exams",
                title = "Quản lý bài thi",
                subtitle = "Ngân hàng câu hỏi & sát hạch chứng chỉ",
                vectorIcon = Icons.Default.School,
                badgeColor = Color(0xFFBF3359),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_EXAMS,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "ai",
                title = "Training AI",
                subtitle = "Trợ lý ảo thông minh & kho tri thức AI",
                iconRes = R.drawable.sf_adm_ai,
                badgeColor = Color(0xFFA855F7),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_AI,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "settings",
                title = "Thiết lập hệ thống",
                subtitle = "Cấu hình ngân hàng & quy chế giao dịch",
                iconRes = R.drawable.sf_adm_settings,
                badgeColor = Color(0xFF64748B),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_SETTINGS,
                requiresAdmin = true
            ),
            WorkspaceModuleItem(
                id = "cms",
                title = "CMS Quản trị nội dung",
                subtitle = "Tin tức thị trường, tài liệu & trang chủ",
                iconRes = R.drawable.sf_adm_cms,
                badgeColor = Color(0xFF6366F1),
                group = "Quản trị viên",
                route = FutaDestinations.ADMIN_CMS,
                requiresAdmin = true
            ),
            // QUẢN LÝ BÁN HÀNG (TVV & Admin - Chuẩn Web)
            WorkspaceModuleItem(
                id = "advisor_products",
                title = "Rổ hàng của tôi",
                subtitle = "Kho giỏ căn dành cho tư vấn viên",
                iconRes = R.drawable.ic_sf_building_circle_fill,
                badgeColor = FutaColors.BrandGreen,
                group = "Quản lý bán hàng",
                route = FutaDestinations.ADVISOR_PRODUCTS,
                requiresStaff = true
            ),
            WorkspaceModuleItem(
                id = "my_listings",
                title = "Đăng ký sản phẩm",
                subtitle = "Theo dõi và đăng ký quyền bán sản phẩm",
                iconRes = R.drawable.ic_sf_checkmark_rectangle_stack_fill,
                badgeColor = Color(0xFF10B981),
                group = "Quản lý bán hàng",
                route = FutaDestinations.MY_LISTINGS,
                requiredPermissions = listOf("apartments:create", "apartments:edit")
            ),
            WorkspaceModuleItem(
                id = "customers",
                title = "Khách hàng của tôi",
                subtitle = "Quản lý dữ liệu khách hàng tiềm năng",
                iconRes = R.drawable.sf_adm_users,
                badgeColor = Color(0xFF2563EB),
                group = "Quản lý bán hàng",
                route = FutaDestinations.ADMIN_CUSTOMERS,
                requiredPermissions = listOf("customers:view")
            ),
            WorkspaceModuleItem(
                id = "crm",
                title = "CRM chăm sóc KH",
                subtitle = "Quy trình chăm sóc & phân phối cơ hội",
                iconRes = R.drawable.ic_sf_person_crop_rectangle_stack_fill,
                badgeColor = Color(0xFFF97316),
                group = "Quản lý bán hàng",
                route = FutaDestinations.CRM,
                requiredPermissions = listOf("customers:view")
            ),
            WorkspaceModuleItem(
                id = "contracts",
                title = "Hợp đồng giao dịch",
                subtitle = "Quản lý hợp đồng dịch vụ & môi giới",
                iconRes = R.drawable.sf_adm_contracts,
                badgeColor = Color(0xFF0D9488),
                group = "Quản lý bán hàng",
                route = FutaDestinations.ADMIN_CONTRACTS,
                requiredPermissions = listOf("contracts:view")
            ),
            WorkspaceModuleItem(
                id = "proposals",
                title = "Đề xuất của tôi",
                subtitle = "Đề xuất sản phẩm & chính sách bán hàng",
                vectorIcon = Icons.Default.Description,
                badgeColor = Color(0xFFD97706),
                group = "Quản lý bán hàng",
                route = FutaDestinations.ADVISOR_PROPOSALS,
                requiresStaff = true
            ),
            WorkspaceModuleItem(
                id = "reports",
                title = "Báo cáo tháng",
                subtitle = "Báo cáo hiệu suất kinh doanh cá nhân",
                iconRes = R.drawable.ic_sf_chart_xyaxis_line,
                badgeColor = Color(0xFF6366F1),
                group = "Quản lý bán hàng",
                route = FutaDestinations.ADMIN_REPORTS,
                requiredPermissions = listOf("reports:view")
            ),
            WorkspaceModuleItem(
                id = "zalo",
                title = "Marketing Zalo OA",
                subtitle = "Đồng bộ khách hàng & chiến dịch Zalo OA",
                iconRes = R.drawable.sf_adm_zalo,
                badgeColor = Color(0xFF0073E6),
                group = "Quản lý bán hàng",
                route = FutaDestinations.ADMIN_ZALO,
                requiredPermissions = listOf("zalo:view", "zalo:manage")
            ),
            WorkspaceModuleItem(
                id = "chat",
                title = "Trung tâm trò chuyện",
                subtitle = "Hộp thư tư vấn & trao đổi khách hàng",
                vectorIcon = Icons.Default.Forum,
                badgeColor = Color(0xFF059669),
                group = "Quản lý bán hàng",
                route = FutaDestinations.INBOX,
                requiredPermissions = listOf("chat:view")
            ),

            // TÀI KHOẢN & TIỆN ÍCH (Tất cả người dùng)
            WorkspaceModuleItem(
                id = "lucky_wheel",
                title = "Vòng quay may mắn",
                subtitle = "Tham gia vòng quay may mắn nhận quà",
                vectorIcon = Icons.Default.CardGiftcard,
                badgeColor = Color(0xFFD97706),
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.LUCKY_WHEEL
            ),
            WorkspaceModuleItem(
                id = "pricing",
                title = "Gói dịch vụ & Hạn mức",
                subtitle = "Bảng giá gói tin & quyền lợi tài khoản",
                vectorIcon = Icons.Default.Stars,
                badgeColor = Color(0xFFD97706),
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.PRICING
            ),
            WorkspaceModuleItem(
                id = "profile",
                title = "Hồ sơ cá nhân",
                subtitle = "Cập nhật thông tin cá nhân & mật khẩu",
                vectorIcon = Icons.Default.AccountCircle,
                badgeColor = Color(0xFF2563EB),
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.PROFILE
            ),
            WorkspaceModuleItem(
                id = "billing",
                title = "Thanh toán & Đơn hàng",
                subtitle = "Lịch sử thanh toán & đơn hàng dịch vụ",
                vectorIcon = Icons.Default.CreditCard,
                badgeColor = Color(0xFF10B981),
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.BILLING
            ),
            WorkspaceModuleItem(
                id = "saved",
                title = "Tin đã lưu & Thư mục",
                subtitle = "Bất động sản và thư mục đã lưu",
                vectorIcon = Icons.Default.Favorite,
                badgeColor = Color(0xFFE11D48),
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.SAVED
            ),
            WorkspaceModuleItem(
                id = "history",
                title = "Lịch sử đã xem",
                subtitle = "Lịch sử các sản phẩm bạn đã xem",
                vectorIcon = Icons.Default.History,
                badgeColor = Color(0xFF64748B),
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.VIEW_HISTORY
            ),
            WorkspaceModuleItem(
                id = "notifications",
                title = "Thông báo hệ thống",
                subtitle = "Cập nhật giao dịch & tin nhắn hệ thống",
                vectorIcon = Icons.Default.Notifications,
                badgeColor = Color(0xFFF97316),
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.NOTIFICATIONS
            ),
            WorkspaceModuleItem(
                id = "advisor",
                title = "Trở thành tư vấn viên",
                subtitle = "Đăng ký trở thành chuyên viên FUTA",
                vectorIcon = Icons.Default.VerifiedUser,
                badgeColor = FutaColors.BrandGreen,
                group = "Tài khoản & Tiện ích",
                route = FutaDestinations.ADVISOR,
                hideIfStaff = true
            )
        )
    }

    val groups = remember(session.role) {
        if (session.role == "admin") {
            listOf("Tổng quan", "Quản trị viên", "Quản lý bán hàng", "Tài khoản & Tiện ích")
        } else if (session.isInternalStaff) {
            listOf("Tổng quan", "Quản lý bán hàng", "Tài khoản & Tiện ích")
        } else {
            listOf("Tài khoản & Tiện ích")
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
                        text = if (session.role == "admin") "Quản trị hệ thống" else "Bàn làm việc TVV",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEAF5EF)
                    ) {
                        Text(
                            text = if (session.role == "admin") "QUẢN TRỊ VIÊN" else "TƯ VẤN VIÊN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.BrandGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            groups.forEach { grp ->
                val itemsInGroup = allModules.filter { it.group == grp && it.isVisible(session) }

                if (itemsInGroup.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .height(140.dp)
            .clickable(onClick = onClick)
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
                        if (item.vectorIcon != null) {
                            Icon(
                                imageVector = item.vectorIcon,
                                contentDescription = null,
                                tint = item.badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        } else if (item.iconRes != null) {
                            Icon(
                                painter = painterResource(id = item.iconRes),
                                contentDescription = null,
                                tint = item.badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FutaColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    fontSize = 11.sp,
                    color = FutaColors.Slate,
                    maxLines = 2,
                    lineHeight = 15.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
