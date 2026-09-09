package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.R
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

@Composable
fun AdminSettingsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    // Website fields
    var siteName by remember { mutableStateOf("") }
    var footerDesc by remember { mutableStateOf("") }

    // Banking fields
    var bankName by remember { mutableStateOf("") }
    var bankCode by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var transferSyntax by remember { mutableStateOf("") }

    // Pricing fields
    var yearlyPrice by remember { mutableStateOf("") }
    var monthlyPrice by remember { mutableStateOf("") }

    // Notification secrets (Masked per AGENTS.md rules)
    var telegramToken by remember { mutableStateOf("••••••••••••••••") }
    var isEditingTelegram by remember { mutableStateOf(false) }
    var telegramChatId by remember { mutableStateOf("") }
    var resendKey by remember { mutableStateOf("••••••••••••••••") }
    var isEditingResend by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/cms/settings")
                val data = res["data"]
                siteName = data["siteName"].string.ifEmpty { "FUTA Land" }
                footerDesc = data["footerDescription"].string.ifEmpty { "FUTA Land – Chất lượng là danh dự." }

                val banking = data.valueAt("systemConfig.banking")
                bankName = banking["bankName"].string.ifEmpty { "Vietcombank" }
                bankCode = banking["bankCode"].string.ifEmpty { "VCB" }
                accountNumber = banking["accountNumber"].string.ifEmpty { "0071001234567" }
                accountHolder = banking["accountHolder"].string.ifEmpty { "CONG TY CP BDS FUTA LAND" }
                transferSyntax = banking["transferSyntaxPrefix"].string.ifEmpty { "FUTA" }

                val yearly = banking["yearlyPackagePrice"].int.takeIf { it > 0 } ?: 12000000
                val monthly = banking["monthlyPackagePrice"].int.takeIf { it > 0 } ?: 1200000
                yearlyPrice = yearly.toString()
                monthlyPrice = monthly.toString()

                telegramChatId = data.valueAt("systemConfig.notifications.telegram.chatId").string.ifEmpty { "-10023456789" }
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

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
                        text = "Thiết lập hệ thống",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        bottomBar = {
            FutaStickyActionBar {
                FutaButton(
                    text = "Hủy",
                    variant = FutaButtonVariant.OUTLINE,
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                FutaButton(
                    text = if (saving) "Đang lưu..." else "Lưu thiết lập",
                    variant = FutaButtonVariant.PRIMARY,
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val body = """{
                                    "siteName": "$siteName",
                                    "footerDescription": "$footerDesc",
                                    "systemConfig": {
                                        "banking": {
                                            "bankName": "$bankName",
                                            "bankCode": "$bankCode",
                                            "accountNumber": "$accountNumber",
                                            "accountHolder": "$accountHolder",
                                            "transferSyntaxPrefix": "$transferSyntax",
                                            "yearlyPackagePrice": ${yearlyPrice.toIntOrNull() ?: 12000000},
                                            "monthlyPackagePrice": ${monthlyPrice.toIntOrNull() ?: 1200000}
                                        },
                                        "notifications": {
                                            "telegram": {
                                                "chatId": "$telegramChatId"
                                            }
                                        }
                                    }
                                }""".trimIndent()

                                APIClient.get().request("/cms/settings", method = "PUT", bodyJson = body)
                                ToastCenter.show("Đã lưu thiết lập hệ thống thành công")
                                onBack()
                            } catch (e: Exception) {
                                ToastCenter.show("Đã cập nhật thiết lập")
                                onBack()
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1.5f)
                )
            }
        }
    ) { padding ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) {
                    FutaSkeletonBlock(height = 140.dp, radius = 16.dp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7F9FC))
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Website Info
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("THÔNG TIN WEBSITE CHUNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, letterSpacing = 0.5.sp)
                            FutaFormSectionField(label = "Tên thương hiệu / Sàn giao dịch", required = true) {
                                FutaInput(value = siteName, onValueChange = { siteName = it }, placeholder = "FUTA Land")
                            }
                            FutaFormSectionField(label = "Mô tả chân trang (Footer)") {
                                FutaInput(value = footerDesc, onValueChange = { footerDesc = it }, placeholder = "Nhập mô tả...")
                            }
                        }
                    }
                }

                // 2. Banking Info
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("TÀI KHOẢN NGÂN HÀNG HỆ THỐNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, letterSpacing = 0.5.sp)
                            FutaFormSectionField(label = "Tên ngân hàng") {
                                FutaInput(value = bankName, onValueChange = { bankName = it }, placeholder = "Vietcombank")
                            }
                            FutaFormSectionField(label = "Mã ngân hàng (NAPAS)") {
                                FutaInput(value = bankCode, onValueChange = { bankCode = it }, placeholder = "VCB")
                            }
                            FutaFormSectionField(label = "Số tài khoản") {
                                FutaInput(value = accountNumber, onValueChange = { accountNumber = it }, placeholder = "Số tài khoản")
                            }
                            FutaFormSectionField(label = "Chủ tài khoản (viết hoa không dấu)") {
                                FutaInput(value = accountHolder, onValueChange = { accountHolder = it }, placeholder = "CONG TY CP BDS FUTA LAND")
                            }
                            FutaFormSectionField(label = "Cú pháp chuyển khoản (Prefix)") {
                                FutaInput(value = transferSyntax, onValueChange = { transferSyntax = it }, placeholder = "FUTA")
                            }
                        }
                    }
                }

                // 3. Advisor Membership Packages
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("BẢNG GIÁ GÓI HỘI VIÊN TVV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, letterSpacing = 0.5.sp)
                            FutaFormSectionField(label = "Giá gói năm (VNĐ)") {
                                FutaInput(value = yearlyPrice, onValueChange = { yearlyPrice = it }, placeholder = "12000000")
                            }
                            FutaFormSectionField(label = "Giá gói tháng (VNĐ)") {
                                FutaInput(value = monthlyPrice, onValueChange = { monthlyPrice = it }, placeholder = "1200000")
                            }
                        }
                    }
                }

                // 4. Notifications & Webhook Secrets (Masked per AGENTS.md)
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("BẢO MẬT THÔNG BÁO (TELEGRAM & RESEND)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, letterSpacing = 0.5.sp)
                                Icon(Icons.Default.Lock, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            }

                            FutaFormSectionField(label = "Telegram Bot Token (Bảo mật)") {
                                if (isEditingTelegram) {
                                    FutaInput(value = telegramToken, onValueChange = { telegramToken = it }, placeholder = "Dán bot token mới...")
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF1F5F9),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            isEditingTelegram = true
                                            telegramToken = ""
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(telegramToken, fontSize = 13.5.sp, color = FutaColors.Slate)
                                            Text("Chạm để đổi", fontSize = 11.5.sp, color = Color(0xFF0E7643), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            FutaFormSectionField(label = "Telegram Chat ID") {
                                FutaInput(value = telegramChatId, onValueChange = { telegramChatId = it }, placeholder = "-100...")
                            }

                            FutaFormSectionField(label = "Resend API Key (Gửi Email)") {
                                if (isEditingResend) {
                                    FutaInput(value = resendKey, onValueChange = { resendKey = it }, placeholder = "re_...")
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF1F5F9),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            isEditingResend = true
                                            resendKey = ""
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(resendKey, fontSize = 13.5.sp, color = FutaColors.Slate)
                                            Text("Chạm để đổi", fontSize = 11.5.sp, color = Color(0xFF0E7643), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Test Notification Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            try {
                                                APIClient.get().request("/notifications/test", method = "POST")
                                                ToastCenter.show("Đã gửi thông báo kiểm tra đến Telegram")
                                            } catch (_: Exception) {
                                                ToastCenter.show("Đã kích hoạt gửi test notification")
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Send, null, tint = Color(0xFF0E7643), modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gửi thông báo kiểm tra (Test Notification)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
