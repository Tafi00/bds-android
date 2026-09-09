package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.designsystem.*

data class PermissionItem(
    val code: String,
    val name: String,
    val group: String
)

@Composable
fun AdminRolesScreen(
    onBack: () -> Unit
) {
    var selectedRole by remember { mutableStateOf("sale") } // "admin", "sale", "advisor", "customer"

    val roles = remember {
        listOf(
            "admin" to "Quản trị viên (Admin)",
            "sale" to "Nhân viên kinh doanh (Sale)",
            "advisor" to "Chuyên viên tư vấn (TVV)",
            "customer" to "Khách hàng thành viên"
        )
    }

    val permissionsCatalog = remember {
        listOf(
            PermissionItem("apartments:manage", "Quản lý kho căn & Khóa căn ERP", "Bất động sản"),
            PermissionItem("apartments:create", "Tạo mới & Biên tập tin BĐS", "Bất động sản"),
            PermissionItem("sales:hold", "Đăng ký cọc & giữ chỗ căn", "Giao dịch"),
            PermissionItem("sales:approve", "Phê duyệt phiếu đặt cọc bán", "Giao dịch"),
            PermissionItem("crm:access", "Truy cập Pipeline khách hàng CRM", "Khách hàng"),
            PermissionItem("leads:distribute", "Phân bổ số lượng lead cho nhân sự", "Khách hàng"),
            PermissionItem("cms:manage", "Quản trị bài viết CMS & Banner", "Truyền thông"),
            PermissionItem("zalo:campaign", "Tạo chiến dịch Zalo Marketing", "Truyền thông"),
            PermissionItem("users:manage", "Quản lý tài khoản & Phân quyền", "Hệ thống")
        )
    }

    val activePermissions = remember {
        mutableStateMapOf(
            "admin" to mutableSetOf("apartments:manage", "apartments:create", "sales:hold", "sales:approve", "crm:access", "leads:distribute", "cms:manage", "zalo:campaign", "users:manage"),
            "sale" to mutableSetOf("apartments:create", "sales:hold", "crm:access"),
            "advisor" to mutableSetOf("sales:hold", "crm:access"),
            "customer" to mutableSetOf<String>()
        )
    }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Vai trò & Phân quyền",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FutaColors.PageBg)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Role Selection
            item {
                Text("CHỌN NHÓM VAI TRÒ ĐỂ THIẾT LẬP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    roles.forEach { (rKey, rName) ->
                        val isSelected = selectedRole == rKey
                        FutaCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isSelected) FutaColors.BrandGreen else FutaColors.LightBlueBorder,
                            onClick = { selectedRole = rKey }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rName,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) FutaColors.BrandGreen else FutaColors.Navy
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 2. Permissions Matrix
            item {
                val currentSet = activePermissions.getOrPut(selectedRole) { mutableSetOf() }
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("MA TRẬN QUYỀN HẠN CỦA NHÓM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                        permissionsCatalog.groupBy { it.group }.forEach { (groupName, perms) ->
                            Text(groupName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                            perms.forEach { perm ->
                                val isChecked = currentSet.contains(perm.code)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (selectedRole == "admin") {
                                                ToastCenter.show("Tài khoản Quản trị viên luôn có đủ mọi quyền")
                                                return@clickable
                                            }
                                            if (isChecked) currentSet.remove(perm.code)
                                            else currentSet.add(perm.code)
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(perm.name, fontSize = 13.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (selectedRole == "admin") return@Checkbox
                                            if (checked) currentSet.add(perm.code)
                                            else currentSet.remove(perm.code)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = FutaColors.BrandGreen)
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        FutaButton(
                            text = "Lưu phân quyền nhóm $selectedRole",
                            variant = FutaButtonVariant.PRIMARY,
                            onClick = { ToastCenter.show("Đã cập nhật phân quyền nhóm $selectedRole thành công!") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
