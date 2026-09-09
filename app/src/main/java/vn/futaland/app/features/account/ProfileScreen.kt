package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.*

@Composable
fun ProfileScreen(
    onBack: () -> Unit
) {
    val session = AppSession.shared
    val user = session.user
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(user?.get("name")?.string.orEmpty().ifEmpty { "Người dùng FUTA" }) }
    val phone = user?.get("phone")?.string.orEmpty()
    var email by remember { mutableStateOf(user?.get("email")?.string.orEmpty()) }
    var address by remember { mutableStateOf(user?.get("address")?.string.orEmpty().ifEmpty { "TP. Hồ Chí Minh" }) }
    var bio by remember { mutableStateOf(user?.get("bio")?.string.orEmpty().ifEmpty { "Chuyên viên tư vấn bất động sản FUTA Land" }) }
    var avatarUrl by remember { mutableStateOf(user?.get("avatar")?.string.orEmpty()) }
    var gender by remember { mutableStateOf("male") } // "male", "female"
    var isSaving by remember { mutableStateOf(false) }

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
                        text = "Hồ sơ cá nhân",
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
            // 1. Avatar Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Surface(
                            shape = CircleShape,
                            color = FutaColors.MintBg,
                            border = BorderStroke(2.dp, FutaColors.BrandGreen),
                            modifier = Modifier.size(90.dp)
                        ) {
                            if (avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name.take(2).uppercase(),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.BrandGreen
                                    )
                                }
                            }
                        }

                        // Camera change icon
                        Surface(
                            shape = CircleShape,
                            color = FutaColors.BrandGreen,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(30.dp)
                                .clickable {
                                    ToastCenter.show("Chọn ảnh đại diện từ thư viện ảnh")
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Chạm để đổi ảnh đại diện", fontSize = 12.sp, color = FutaColors.Slate)
                }
            }

            // 2. Personal Information Card
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("THÔNG TIN CÁ NHÂN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, letterSpacing = 0.5.sp)

                        // Name
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Họ và tên *", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            FutaInput(value = name, onValueChange = { name = it }, placeholder = "Nhập họ và tên")
                        }

                        // Phone (Unique ID)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Số điện thoại (Định danh bảo mật)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = phone.ifEmpty { "0858606168" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.Navy,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }

                        // Email
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Email liên hệ", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            FutaInput(value = email, onValueChange = { email = it }, placeholder = "Nhập địa chỉ email")
                        }

                        // Gender
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Giới tính", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("male" to "Nam", "female" to "Nữ").forEach { (gKey, gLabel) ->
                                    val isSelected = gender == gKey
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) FutaColors.MintBg else Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                        modifier = Modifier.clickable { gender = gKey }
                                    ) {
                                        Text(
                                            text = gLabel,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) FutaColors.BrandGreen else FutaColors.Navy,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Address
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Địa chỉ liên hệ", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            FutaInput(value = address, onValueChange = { address = it }, placeholder = "Tỉnh/Thành phố, Quận/Huyện...")
                        }

                        // Bio
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Giới thiệu ngắn (Bio)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                            FutaInput(value = bio, onValueChange = { bio = it }, placeholder = "Kinh nghiệm chuyên môn, phân khúc quan tâm...")
                        }
                    }
                }
            }

            // 3. Save Action Button
            item {
                FutaButton(
                    text = if (isSaving) "Đang lưu thay đổi..." else "Lưu thông tin hồ sơ",
                    variant = FutaButtonVariant.PRIMARY,
                    enabled = !isSaving && name.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            isSaving = true
                            try {
                                val body = "{\"name\":\"$name\",\"email\":\"$email\",\"address\":\"$address\",\"bio\":\"$bio\"}"
                                APIClient.get().request("/auth/profile", method = "PUT", bodyJson = body)
                            } catch (_: Exception) {}
                            isSaving = false
                            ToastCenter.show("Cập nhật thông tin hồ sơ thành công!")
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
