package vn.futaland.app.features.account

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

    // 1. Website fields
    var siteName by remember { mutableStateOf("") }
    var footerDesc by remember { mutableStateOf("") }

    // 2. Banking fields
    var bankName by remember { mutableStateOf("") }
    var bankCode by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var transferSyntax by remember { mutableStateOf("") }

    // 3. Pricing fields
    var yearlyPrice by remember { mutableStateOf("") }
    var monthlyPrice by remember { mutableStateOf("") }

    // 4. Broker Contract fields
    var brokerTitle by remember { mutableStateOf("") }
    var brokerCompany by remember { mutableStateOf("") }
    var brokerTaxCode by remember { mutableStateOf("") }
    var brokerRep by remember { mutableStateOf("") }
    var brokerRepTitle by remember { mutableStateOf("") }
    var brokerClauses by remember { mutableStateOf("") }

    // 5. Service Contract fields
    var serviceTitle by remember { mutableStateOf("") }
    var serviceCompany by remember { mutableStateOf("") }
    var serviceTaxCode by remember { mutableStateOf("") }
    var serviceRep by remember { mutableStateOf("") }
    var serviceRepTitle by remember { mutableStateOf("") }
    var serviceClauses by remember { mutableStateOf("") }

    // 6. Acceptance Report fields
    var acceptanceTitle by remember { mutableStateOf("") }
    var acceptanceCompany by remember { mutableStateOf("") }
    var acceptanceRep by remember { mutableStateOf("") }
    var acceptanceNotes by remember { mutableStateOf("") }

    // 7. Online Holding rules
    var holdDurationMinutes by remember { mutableStateOf("15") }
    var cooldownMinutes by remember { mutableStateOf("5") }

    // 8. Notification & Messaging Secrets (Masked per AGENTS.md rules)
    var notificationSecret by remember { mutableStateOf("••••••••••••••••") }
    var isEditingNotificationSecret by remember { mutableStateOf(false) }
    var newNotificationSecret by remember { mutableStateOf("") }

    var messagingApiKey by remember { mutableStateOf("••••••••••••••••") }
    var isEditingMessagingApiKey by remember { mutableStateOf(false) }
    var newMessagingApiKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            try {
                val res = try {
                    APIClient.get().request("/cms/admin")
                } catch (_: Exception) {
                    APIClient.get().request("/cms/settings")
                }
                val data = if (!res["data"]["settings"].isNull) res["data"]["settings"] else res["data"]
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

                // Broker Contract
                val broker = data.valueAt("systemConfig.brokerContract")
                brokerTitle = broker["title"].string.ifEmpty { "HỢP ĐỒNG DỊCH VỤ MÔI GIỚI BẤT ĐỘNG SẢN" }
                brokerCompany = broker["companyName"].string.ifEmpty { "CÔNG TY CỔ PHẦN ĐẦU TƯ FUTA LAND" }
                brokerTaxCode = broker["companyTaxCode"].string.ifEmpty { "0316784953" }
                brokerRep = broker["companyRepresentative"].string.ifEmpty { "Bà Trần Thị Hoa Xim" }
                brokerRepTitle = broker["companyRepresentativeTitle"].string.ifEmpty { "Tổng Giám đốc Phụ trách Kinh doanh" }
                val bClausesArr = broker["clauses"].array
                brokerClauses = if (bClausesArr.isNotEmpty()) bClausesArr.joinToString("\n") { it.string } else "ĐIỀU 1. ĐỊNH NGHĨA VÀ GIẢI THÍCH TỪ NGỮ\nĐIỀU 2. ĐỐI TƯỢNG VÀ NỘI DUNG HỢP ĐỒNG\nĐIỀU 3. PHÍ DỊCH VỤ VÀ PHƯƠNG THỨC THANH TOÁN"

                // Service Contract
                val service = data.valueAt("systemConfig.serviceContract")
                serviceTitle = service["title"].string.ifEmpty { "HỢP ĐỒNG DỊCH VỤ TƯ VẤN BẤT ĐỘNG SẢN" }
                serviceCompany = service["companyName"].string.ifEmpty { "CÔNG TY CỔ PHẦN ĐẦU TƯ FUTA LAND" }
                serviceTaxCode = service["companyTaxCode"].string.ifEmpty { "0316784953" }
                serviceRep = service["companyRepresentative"].string.ifEmpty { "Bà Trần Thị Hoa Xim" }
                serviceRepTitle = service["companyRepresentativeTitle"].string.ifEmpty { "Tổng Giám đốc Phụ trách Kinh doanh" }
                val sClausesArr = service["clauses"].array
                serviceClauses = if (sClausesArr.isNotEmpty()) sClausesArr.joinToString("\n") { it.string } else "ĐIỀU 1. PHẠM VI DỊCH VỤ\nĐIỀU 2. QUYỀN VÀ NGHĨA VỤ CỦA CÁC BÊN\nĐIỀU 3. BẢO MẬT THÔNG TIN"

                // Acceptance Report
                val acceptance = data.valueAt("systemConfig.acceptanceReport")
                acceptanceTitle = acceptance["title"].string.ifEmpty { "BIÊN BẢN NGHIỆM THU DỊCH VỤ TƯ VẤN" }
                acceptanceCompany = acceptance["companyName"].string.ifEmpty { "CÔNG TY CỔ PHẦN ĐẦU TƯ FUTA LAND" }
                acceptanceRep = acceptance["companyRepresentative"].string.ifEmpty { "Bà Trần Thị Hoa Xim" }
                val notesArr = acceptance["defaultNotes"].array
                acceptanceNotes = if (notesArr.isNotEmpty()) notesArr.joinToString("\n") { it.string } else "Các bên đồng ý nghiệm thu toàn bộ công việc môi giới đã thực hiện đầy đủ theo đúng hợp đồng."

                // Holding rules
                val holding = data.valueAt("systemConfig.holding")
                holdDurationMinutes = holding["holdDurationMinutes"].int.takeIf { it > 0 }?.toString() ?: "15"
                cooldownMinutes = holding["cooldownMinutes"].int.takeIf { it > 0 }?.toString() ?: "5"

            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    fun handleSave() {
        scope.launch {
            saving = true
            try {
                val bClausesList = brokerClauses.split("\n").map { "\"${it.trim()}\"" }.joinToString(",")
                val sClausesList = serviceClauses.split("\n").map { "\"${it.trim()}\"" }.joinToString(",")
                val aNotesList = acceptanceNotes.split("\n").map { "\"${it.trim()}\"" }.joinToString(",")

                val body = """
                {
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
                        "brokerContract": {
                            "title": "$brokerTitle",
                            "companyName": "$brokerCompany",
                            "companyTaxCode": "$brokerTaxCode",
                            "companyRepresentative": "$brokerRep",
                            "companyRepresentativeTitle": "$brokerRepTitle",
                            "clauses": [$bClausesList]
                        },
                        "serviceContract": {
                            "title": "$serviceTitle",
                            "companyName": "$serviceCompany",
                            "companyTaxCode": "$serviceTaxCode",
                            "companyRepresentative": "$serviceRep",
                            "companyRepresentativeTitle": "$serviceRepTitle",
                            "clauses": [$sClausesList]
                        },
                        "acceptanceReport": {
                            "title": "$acceptanceTitle",
                            "companyName": "$acceptanceCompany",
                            "companyRepresentative": "$acceptanceRep",
                            "defaultNotes": [$aNotesList]
                        },
                        "holding": {
                            "holdDurationMinutes": ${holdDurationMinutes.toIntOrNull() ?: 15},
                            "cooldownMinutes": ${cooldownMinutes.toIntOrNull() ?: 5}
                        }
                    }
                }
                """.trimIndent()

                APIClient.get().request("/cms/settings", method = "PUT", bodyJson = body)
                ToastCenter.show("Đã lưu thiết lập hệ thống thành công!")
                onBack()
            } catch (e: Exception) {
                ToastCenter.show("Lỗi lưu thiết lập: ${e.message}", isError = true)
            } finally {
                saving = false
            }
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
                        text = "Cài đặt hệ thống",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    FutaHeaderIconButton(
                        icon = Icons.Default.Check,
                        contentDescription = "Lưu thiết lập",
                        tint = FutaColors.BrandGreen,
                        onClick = { handleSave() }
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
                    onClick = { handleSave() },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(4) { FutaSkeletonBlock(height = 110.dp, radius = 16.dp) }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Thông tin website chung
                item {
                    SettingsCardSection(title = "Thông tin website chung") {
                        Text("Tên website / Thương hiệu", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = siteName, onValueChange = { siteName = it }, placeholder = "FUTA Land")

                        Text("Mô tả chân trang (Footer)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = footerDesc, onValueChange = { footerDesc = it }, placeholder = "Mô tả ngắn...")
                    }
                }

                // Section 2: Thông tin tài khoản ngân hàng
                item {
                    SettingsCardSection(title = "Thông tin tài khoản ngân hàng") {
                        Text("Tên ngân hàng (ví dụ: Vietcombank)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = bankName, onValueChange = { bankName = it }, placeholder = "Vietcombank")

                        Text("Mã ngân hàng (ví dụ: VCB, TPB)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = bankCode, onValueChange = { bankCode = it }, placeholder = "VCB")

                        Text("Số tài khoản", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = accountNumber, onValueChange = { accountNumber = it }, placeholder = "0071001234567", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))

                        Text("Chủ tài khoản (chữ hoa không dấu)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = accountHolder, onValueChange = { accountHolder = it }, placeholder = "CONG TY CP BDS FUTA LAND")

                        Text("Cú pháp chuyển khoản (Prefix)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = transferSyntax, onValueChange = { transferSyntax = it }, placeholder = "FUTA")
                    }
                }

                // Section 3: Bảng giá gói thành viên TVV
                item {
                    SettingsCardSection(title = "Bảng giá gói thành viên TVV") {
                        Text("Giá gói năm (VND)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = yearlyPrice, onValueChange = { yearlyPrice = it }, placeholder = "12000000", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))

                        Text("Giá gói tháng (VND)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = monthlyPrice, onValueChange = { monthlyPrice = it }, placeholder = "1200000", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                    }
                }

                // Section 4: Hợp đồng môi giới (Broker Contract)
                item {
                    SettingsCardSection(title = "Hợp đồng môi giới (Broker Contract)") {
                        Text("Tiêu đề hợp đồng", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = brokerTitle, onValueChange = { brokerTitle = it }, placeholder = "HỢP ĐỒNG...")

                        Text("Tên công ty", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = brokerCompany, onValueChange = { brokerCompany = it }, placeholder = "Tên công ty...")

                        Text("Mã số thuế", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = brokerTaxCode, onValueChange = { brokerTaxCode = it }, placeholder = "MST...")

                        Text("Người đại diện", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = brokerRep, onValueChange = { brokerRep = it }, placeholder = "Họ tên người đại diện")

                        Text("Chức vụ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = brokerRepTitle, onValueChange = { brokerRepTitle = it }, placeholder = "Chức vụ...")

                        Text("Các điều khoản hợp đồng (mỗi dòng một điều khoản)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = brokerClauses, onValueChange = { brokerClauses = it }, placeholder = "Điều khoản...")
                    }
                }

                // Section 5: Hợp đồng dịch vụ (Service Contract)
                item {
                    SettingsCardSection(title = "Hợp đồng dịch vụ (Service Contract)") {
                        Text("Tiêu đề hợp đồng dịch vụ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = serviceTitle, onValueChange = { serviceTitle = it }, placeholder = "HỢP ĐỒNG...")

                        Text("Tên công ty", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = serviceCompany, onValueChange = { serviceCompany = it }, placeholder = "Tên công ty...")

                        Text("Mã số thuế", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = serviceTaxCode, onValueChange = { serviceTaxCode = it }, placeholder = "MST...")

                        Text("Người đại diện", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = serviceRep, onValueChange = { serviceRep = it }, placeholder = "Họ tên người đại diện")

                        Text("Chức vụ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = serviceRepTitle, onValueChange = { serviceRepTitle = it }, placeholder = "Chức vụ...")

                        Text("Các điều khoản hợp đồng (mỗi dòng một điều khoản)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = serviceClauses, onValueChange = { serviceClauses = it }, placeholder = "Điều khoản...")
                    }
                }

                // Section 6: Biên bản nghiệm thu (Acceptance Report)
                item {
                    SettingsCardSection(title = "Biên bản nghiệm thu (Acceptance Report)") {
                        Text("Tiêu đề biên bản", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = acceptanceTitle, onValueChange = { acceptanceTitle = it }, placeholder = "BIÊN BẢN...")

                        Text("Tên công ty", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = acceptanceCompany, onValueChange = { acceptanceCompany = it }, placeholder = "Tên công ty...")

                        Text("Người đại diện", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = acceptanceRep, onValueChange = { acceptanceRep = it }, placeholder = "Người đại diện...")

                        Text("Ghi chú mặc định", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(value = acceptanceNotes, onValueChange = { acceptanceNotes = it }, placeholder = "Nội dung ghi chú...")
                    }
                }

                // Section 7: Quy chế giữ chỗ (Online Holding)
                item {
                    SettingsCardSection(title = "Quy chế giữ chỗ (Online Holding)") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thời gian giữ chỗ (phút):", fontSize = 13.sp, color = FutaColors.Navy, fontWeight = FontWeight.Medium)
                            Box(modifier = Modifier.width(90.dp)) {
                                FutaInput(value = holdDurationMinutes, onValueChange = { holdDurationMinutes = it }, placeholder = "15")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thời gian chờ giữa 2 lần (phút):", fontSize = 13.sp, color = FutaColors.Navy, fontWeight = FontWeight.Medium)
                            Box(modifier = Modifier.width(90.dp)) {
                                FutaInput(value = cooldownMinutes, onValueChange = { cooldownMinutes = it }, placeholder = "5")
                            }
                        }
                    }
                }

                // Section 8: Bảo mật thông báo (Push Notification Secret)
                item {
                    SettingsCardSection(title = "Bảo mật thông báo (Push Notification Secret)") {
                        if (!isEditingNotificationSecret) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Khóa bí mật (Provider Secret)", fontSize = 13.sp, color = FutaColors.Navy)
                                    Text("••••••••••••••••", fontSize = 12.sp, color = FutaColors.Slate)
                                }
                                FutaButton(
                                    text = "Đổi khóa",
                                    variant = FutaButtonVariant.OUTLINE,
                                    onClick = { isEditingNotificationSecret = true }
                                )
                            }
                        } else {
                            FutaInput(value = newNotificationSecret, onValueChange = { newNotificationSecret = it }, placeholder = "Nhập Secret mới")
                            FutaButton(
                                text = "Hủy đổi khóa",
                                variant = FutaButtonVariant.OUTLINE,
                                onClick = {
                                    isEditingNotificationSecret = false
                                    newNotificationSecret = ""
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsCardSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Navy
            )
            HorizontalDivider(color = Color(0xFFF1F5F9))
            content()
        }
    }
}
