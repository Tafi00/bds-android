package vn.futaland.app.features.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.FutaCard
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.FutaDialog
import vn.futaland.app.designsystem.FutaButtonVariant
import vn.futaland.app.navigation.FutaDestinations

@Composable
fun AccountScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val session = AppSession.shared
    val user by session.currentUser.collectAsState()

    val isAuthenticated = user != null && session.isAuthenticated
    val role = user?.get("role")?.string ?: session.role
    val isInternalStaff = isAuthenticated && role != "customer" && role.isNotEmpty()
    val canManageListings = session.hasPermission("apartments:create") ||
        session.hasPermission("apartments:edit") ||
        (isInternalStaff && role != "telesale")
    val canAccessBilling = isInternalStaff || session.hasPermission("pricing:subscribe")

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F4F7)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Large iOS Navigation Title
        item {
            Text(
                text = "Tài khoản",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Navy,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 2.dp)
            )
        }

        // 2. User Header Section
        item {
            if (!isAuthenticated) {
                // GUEST STATE HEADER (Matching iOS Image #2)
                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { onNavigate(FutaDestinations.AUTH) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.sf_acc_user_plus),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(44.dp)
                        )

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Đăng nhập / Đăng ký",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Quản lý tin đăng, yêu thích và giao dịch",
                                fontSize = 12.5.sp,
                                color = FutaColors.Slate
                            )
                        }

                        Icon(
                            painter = painterResource(id = R.drawable.sf_chevron_right_light),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            } else {
                // AUTHENTICATED STATE HEADER
                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { onNavigate(FutaDestinations.PROFILE) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avatarUrl = user?.get("avatar")?.string
                        val name = user?.get("name")?.string?.ifEmpty { "Người dùng FUTA" } ?: "Người dùng FUTA"
                        val phone = user?.get("phone")?.string.orEmpty()
                        val email = user?.get("email")?.string.orEmpty()
                        val contactInfo = phone.ifEmpty { email }.ifEmpty { "Chưa cập nhật thông tin liên hệ" }

                        Surface(
                            shape = CircleShape,
                            color = FutaColors.MintBg,
                            border = BorderStroke(1.5.dp, FutaColors.BrandGreen),
                            modifier = Modifier.size(54.dp)
                        ) {
                            if (!avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = FutaColors.BrandGreen
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = contactInfo,
                                fontSize = 12.sp,
                                color = FutaColors.Slate
                            )
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEAF5EF)
                            ) {
                                val roleBadgeText = when (role) {
                                    "admin" -> "Quản trị viên"
                                    "advisor", "agent" -> "Chuyên viên tư vấn"
                                    "sale", "staff" -> "Nhân viên kinh doanh"
                                    else -> "Khách hàng thành viên"
                                }
                                Text(
                                    text = roleBadgeText,
                                    color = FutaColors.BrandGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                }
            }
        }

        // 3. Workspace Section (Admin & Internal Staff Only)
        if (isAuthenticated && role != "customer" && role.isNotEmpty()) {
            item {
                Column {
                    SectionTitle(title = if (role == "admin") "Quản trị hệ thống" else "Bàn làm việc TVV")
                    FutaCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = FutaColors.BrandGreen.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onNavigate(FutaDestinations.WORKSPACE) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEAF5EF),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_sf_briefcase_fill),
                                        contentDescription = null,
                                        tint = FutaColors.BrandGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (role == "admin") "Bảng điều khiển quản trị" else "Bàn làm việc TVV",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (role == "admin") "Bảng điều khiển, dự án, kho căn, CMS, người dùng" else "Quản lý kinh doanh, khách hàng, giao dịch",
                                    fontSize = 12.sp,
                                    color = FutaColors.Slate,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.sf_chevron_right_light),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Personal Features Section ("Quản lý của tôi" - Matching iOS Image #2)
        item {
            Column {
                SectionTitle(title = "Quản lý của tôi")
                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_heart,
                            title = "Tin đã lưu & Thư mục",
                            onClick = { onNavigate(FutaDestinations.SAVED) }
                        )

                        AccountDivider()
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_history,
                            title = "Lịch sử đã xem",
                            onClick = { onNavigate(FutaDestinations.VIEW_HISTORY) }
                        )

                        if (isAuthenticated && canManageListings) {
                            AccountDivider()
                            AccountMenuItem(
                                iconRes = R.drawable.sf_acc_listings,
                                title = "Tin đăng của tôi",
                                onClick = { onNavigate(FutaDestinations.MY_LISTINGS) }
                            )
                        }

                        AccountDivider()
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_pricing,
                            title = "Bảng giá dịch vụ FUTA",
                            onClick = { onNavigate(FutaDestinations.PRICING) }
                        )

                        AccountDivider()
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_notifications,
                            title = "Thông báo hệ thống",
                            onClick = { onNavigate(FutaDestinations.NOTIFICATIONS) }
                        )

                        if (!isInternalStaff) {
                            AccountDivider()
                            AccountMenuItem(
                                iconRes = R.drawable.sf_acc_advisor,
                                title = "Trở thành tư vấn viên",
                                onClick = {
                                    if (isAuthenticated) {
                                        onNavigate(FutaDestinations.ADVISOR)
                                    } else {
                                        onNavigate(FutaDestinations.AUTH)
                                    }
                                }
                            )
                        }

                        if (isAuthenticated && canAccessBilling) {
                            AccountDivider()
                            AccountMenuItem(
                                iconRes = R.drawable.sf_acc_billing,
                                title = "Gói dịch vụ & Hạn mức",
                                onClick = { onNavigate(FutaDestinations.BILLING) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Help and Info Section ("Thông tin & Trợ giúp" - Matching iOS Image #2)
        item {
            Column {
                SectionTitle(title = "Thông tin & Trợ giúp")
                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_news,
                            title = "Tin tức thị trường",
                            onClick = { onNavigate(FutaDestinations.NEWS) }
                        )
                        AccountDivider()
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_guide,
                            title = "Hướng dẫn sử dụng",
                            onClick = { onNavigate(FutaDestinations.GUIDE) }
                        )
                        AccountDivider()
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_contact,
                            title = "Liên hệ hỗ trợ",
                            onClick = { onNavigate(FutaDestinations.CONTACT) }
                        )
                        AccountDivider()
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_policies,
                            title = "Chính sách & Quy chế",
                            onClick = { onNavigate(FutaDestinations.POLICIES) }
                        )
                        AccountDivider()
                        AccountMenuItem(
                            iconRes = R.drawable.sf_acc_about,
                            title = "Về FUTA Land",
                            onClick = { onNavigate(FutaDestinations.ABOUT) }
                        )
                    }
                }
            }
        }

        // 6. Sign Out Section (Visible ONLY when authenticated)
        if (isAuthenticated) {
            item {
                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { showSignOutDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Đăng xuất tài khoản",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(80.dp))
        }
    }

    // Dialog: Đăng xuất tài khoản
    FutaDialog(
        visible = showSignOutDialog,
        onDismiss = { showSignOutDialog = false },
        title = "Đăng xuất tài khoản?",
        confirmText = "Đăng xuất",
        confirmVariant = FutaButtonVariant.DANGER,
        cancelText = "Hủy",
        onConfirm = {
            showSignOutDialog = false
            session.logout()
        },
        onCancel = { showSignOutDialog = false }
    ) {
        Text(
            text = "Bạn sẽ cần đăng nhập lại để tiếp tục sử dụng các tính năng cá nhân.",
            fontSize = 14.sp,
            color = FutaColors.Slate,
            lineHeight = 20.sp
        )
    }

    // Dialog: Về FUTA Land
    FutaDialog(
        visible = showAboutDialog,
        onDismiss = { showAboutDialog = false },
        title = "Về FUTA Land",
        confirmText = "Đóng",
        cancelText = null,
        onConfirm = { showAboutDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "FUTA Land - Thành viên Tập đoàn FUTA (Phương Trang)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = FutaColors.Navy
            )
            Text(
                text = "Tiên phong kiến tạo chuẩn mực bất động sản công nghệ, mang đến giải pháp giao dịch minh bạch, tiện lợi và an tâm tuyệt đối cho khách hàng và nhà đầu tư.",
                fontSize = 13.sp,
                color = FutaColors.Slate,
                lineHeight = 18.sp
            )
            Text(
                text = "Hotline: 028 3838 6852\nĐịa chỉ: TP. Hồ Chí Minh & Đà Nẵng",
                fontSize = 12.sp,
                color = FutaColors.BrandGreen
            )
        }
    }

    // Dialog: Điều khoản & Quy chế hoạt động
    FutaDialog(
        visible = showTermsDialog,
        onDismiss = { showTermsDialog = false },
        title = "Điều khoản & Quy chế",
        confirmText = "Đồng ý",
        cancelText = null,
        onConfirm = { showTermsDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Quy chế hoạt động sàn TMĐT BĐS FUTA Land",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = FutaColors.Navy
            )
            Text(
                text = "1. Mọi thông tin bất động sản, giá bán và tình trạng pháp lý được kiểm duyệt minh bạch trước khi niêm yết.\n2. Thông tin khách hàng và giao dịch được bảo mật tuyệt đối theo tiêu chuẩn an toàn dữ liệu.\n3. Thành viên tham gia tuân thủ đúng quy định pháp luật hiện hành và chính sách của FUTA Land.",
                fontSize = 13.sp,
                color = FutaColors.Slate,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun AccountDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = Color(0xFFF1F5F9),
        thickness = 0.8.dp
    )
}

@Composable
private fun AccountMenuItem(
    iconRes: Int,
    title: String,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = FutaColors.Navy,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = R.drawable.sf_chevron_right_light),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(11.dp)
        )
    }
}
