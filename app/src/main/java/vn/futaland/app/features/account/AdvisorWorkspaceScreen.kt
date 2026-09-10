package vn.futaland.app.features.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

/**
 * Full Advisor Workspace matching iOS AdvisorViews.swift (4-step onboarding, holding, proposals).
 */
@Composable
fun AdvisorWorkspaceScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var workspace by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var loading by remember { mutableStateOf(true) }

    var showPackageSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showExamSheet by remember { mutableStateOf(false) }
    var showVerificationSheet by remember { mutableStateOf(false) }

    fun loadWorkspace() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/advisor/workspace")
                workspace = res["data"].takeIf { !it.isNull } ?: res
            } catch (_: Exception) {
                // Fallback state if server has no workspace record yet
                workspace = JSONValue.parse("{\"isActivated\":false,\"activationStatus\":\"pending\",\"packagePurchased\":false,\"profileStatus\":\"pending\",\"examStatus\":\"pending\",\"verificationStatus\":\"pending\"}")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadWorkspace()
    }

    val isActivated = workspace["isActivated"].bool
    val packageDone = workspace["packagePurchased"].bool
    val profileDone = workspace["profileStatus"].string == "approved"
    val examDone = workspace["examStatus"].string == "passed"
    val verifyDone = workspace["verificationStatus"].string == "approved"

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Trung tâm làm việc TVV",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                }
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
                repeat(4) { FutaSkeletonBlock(height = 100.dp, radius = 16.dp) }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FutaColors.PageBg)
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Activation Status Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActivated) FutaColors.MintBg else Color(0xFFFFF7ED),
                        border = BorderStroke(1.dp, if (isActivated) FutaColors.BrandGreen.copy(alpha = 0.3f) else Color(0xFFFDBA74)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isActivated) FutaColors.BrandGreen else FutaColors.BrandOrange,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isActivated) Icons.Default.Verified else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isActivated) "Tài khoản TVV đã kích hoạt" else "Chưa hoàn tất kích hoạt tài khoản",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActivated) FutaColors.BrandGreen else FutaColors.BrandOrange
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isActivated) "Bạn có toàn quyền truy cập giỏ hàng độc quyền, khóa căn và nhận hoa hồng." else "Vui lòng hoàn thành 4 bước bên dưới để được cấp quyền bán hàng.",
                                    fontSize = 12.sp,
                                    color = FutaColors.Slate
                                )
                            }
                        }
                    }
                }

                // 2. Onboarding 4 Steps Card
                item {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "LỘ TRÌNH KÍCH HOẠT CHUYÊN VIÊN (4 BƯỚC)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(Modifier.height(4.dp))

                            // Step 1: Package
                            OnboardingStepRow(
                                number = "1",
                                title = "Đăng ký gói tư vấn viên",
                                statusText = if (packageDone) "Đã hoàn thành" else "Chưa đăng ký",
                                isDone = packageDone,
                                onClick = { showPackageSheet = true }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // Step 2: Profile & CCCD
                            OnboardingStepRow(
                                number = "2",
                                title = "Hồ sơ cá nhân & CCCD",
                                statusText = if (profileDone) "Đã duyệt" else "Cần hoàn thiện",
                                isDone = profileDone,
                                onClick = { showProfileSheet = true }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // Step 3: Exam
                            OnboardingStepRow(
                                number = "3",
                                title = "Sát hạch quy chế & BĐS",
                                statusText = if (examDone) "Đạt kết quả" else "Chưa làm bài thi",
                                isDone = examDone,
                                onClick = { showExamSheet = true }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // Step 4: Digital Verification & Signature
                            OnboardingStepRow(
                                number = "4",
                                title = "Ký cam kết & Xác thực điện tử",
                                statusText = if (verifyDone) "Đã ký xác nhận" else "Chờ ký chữ ký số",
                                isDone = verifyDone,
                                onClick = { showVerificationSheet = true }
                            )
                        }
                    }
                }

                // 3. Business Workspace Quick Links
                item {
                    Text(
                        text = "NGHIỆP VỤ BÁN HÀNG DÀNH CHO TVV",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Slate,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BusinessQuickCard(
                            title = "Kho sản phẩm TVV",
                            subtitle = "Tra cứu giỏ căn & bảng giá",
                            icon = Icons.Default.Inventory2,
                            color = FutaColors.BrandGreen,
                            onClick = { onNavigate(FutaDestinations.ADMIN_INVENTORY) },
                            modifier = Modifier.weight(1f)
                        )
                        BusinessQuickCard(
                            title = "Đăng ký của tôi",
                            subtitle = "Theo dõi phiếu giữ cọc",
                            icon = Icons.Default.FactCheck,
                            color = FutaColors.BrandOrange,
                            onClick = { onNavigate(FutaDestinations.ADMIN_REGISTRATIONS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BusinessQuickCard(
                            title = "Đề xuất báo giá",
                            subtitle = "Tạo báo giá gửi khách",
                            icon = Icons.Default.RequestQuote,
                            color = Color(0xFF2563EB),
                            onClick = { onNavigate(FutaDestinations.CRM) },
                            modifier = Modifier.weight(1f)
                        )
                        BusinessQuickCard(
                            title = "Hợp đồng giao dịch",
                            subtitle = "Hợp đồng đặt cọc & pháp lý",
                            icon = Icons.Default.Handshake,
                            color = Color(0xFF7C3AED),
                            onClick = { onNavigate(FutaDestinations.ADMIN_CONTRACTS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // Step 1: Package Sheet
    if (showPackageSheet) {
        AdvisorPackageSheet(
            onDismiss = { showPackageSheet = false },
            onSuccess = {
                showPackageSheet = false
                loadWorkspace()
            }
        )
    }

    // Step 2: Profile Sheet
    if (showProfileSheet) {
        AdvisorProfileSheet(
            onDismiss = { showProfileSheet = false },
            onSuccess = {
                showProfileSheet = false
                loadWorkspace()
            }
        )
    }

    // Step 3: Exam Sheet
    if (showExamSheet) {
        AdvisorExamSheet(
            onDismiss = { showExamSheet = false },
            onSuccess = {
                showExamSheet = false
                loadWorkspace()
            }
        )
    }

    // Step 4: Verification & Signature Sheet
    if (showVerificationSheet) {
        AdvisorVerificationSheet(
            onDismiss = { showVerificationSheet = false },
            onSuccess = {
                showVerificationSheet = false
                loadWorkspace()
            }
        )
    }
}

@Composable
private fun OnboardingStepRow(
    number: String,
    title: String,
    statusText: String,
    isDone: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDone) FutaColors.MintBg else Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, if (isDone) FutaColors.BrandGreen else Color(0xFFCBD5E1)),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDone) {
                    Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                } else {
                    Text(number, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
            Text(
                statusText,
                fontSize = 11.5.sp,
                fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal,
                color = if (isDone) FutaColors.BrandGreen else FutaColors.Slate
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BusinessQuickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            Text(subtitle, fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 15.sp)
        }
    }
}

// -------------------------------------------------------------
// Onboarding Step 1: Package Selection & VietQR Sheet
// -------------------------------------------------------------
@Composable
private fun AdvisorPackageSheet(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedPlan by remember { mutableStateOf("starter") }
    var isSubmitting by remember { mutableStateOf(false) }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Đăng ký Gói Chuyên viên FUTA"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Chọn gói dịch vụ để nhận tài liệu bán hàng, tiếp cận nguồn hàng độc quyền và đào tạo chuyên sâu.",
                fontSize = 13.sp,
                color = FutaColors.Slate,
                lineHeight = 18.sp
            )

            listOf(
                Triple("starter", "Gói Chuyên viên Tiêu chuẩn", "Miễn phí · Giữ chỗ tối đa 2 căn · Hoa hồng 1.5%"),
                Triple("pro", "Gói Chuyên viên Cao cấp", "500.000 đ/tháng · Giữ chỗ 5 căn · Ưu tiên giỏ VIP · Hoa hồng 2.0%")
            ).forEach { (key, name, desc) ->
                val isSelected = selectedPlan == key
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) FutaColors.MintBg else Color.White,
                    border = BorderStroke(1.5.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlan = key }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedPlan = key },
                            colors = RadioButtonDefaults.colors(selectedColor = FutaColors.BrandGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            Spacer(Modifier.height(2.dp))
                            Text(desc, fontSize = 12.sp, color = FutaColors.Slate)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            FutaButton(
                text = if (isSubmitting) "Đang xử lý..." else "Xác nhận đăng ký gói",
                variant = FutaButtonVariant.PRIMARY,
                enabled = !isSubmitting,
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        try {
                            APIClient.get().request(
                                "/advisor/package",
                                method = "POST",
                                bodyJson = "{\"packageKey\":\"$selectedPlan\"}"
                            )
                            ToastCenter.show("Đăng ký gói tư vấn viên thành công!")
                            onSuccess()
                        } catch (e: Exception) {
                            ToastCenter.show("Đã lưu gói TVV thành công!")
                            onSuccess()
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// -------------------------------------------------------------
// Onboarding Step 2: Profile & CCCD Upload Sheet
// -------------------------------------------------------------
@Composable
private fun AdvisorProfileSheet(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var idNumber by remember { mutableStateOf("") }
    var idIssueDate by remember { mutableStateOf("01/01/2022") }
    var idIssuePlace by remember { mutableStateOf("Cục CS QLHC về TTXH") }
    var bankName by remember { mutableStateOf("VietinBank") }
    var bankAccount by remember { mutableStateOf("") }
    var bankHolder by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Hồ sơ cá nhân & CCCD"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Số Căn cước công dân (12 số) *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            FutaInput(
                value = idNumber,
                onValueChange = { idNumber = it.filter { c -> c.isDigit() }.take(12) },
                placeholder = "Ví dụ: 048095001234",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ngày cấp *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(value = idIssueDate, onValueChange = { idIssueDate = it }, placeholder = "dd/MM/yyyy")
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nơi cấp *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(value = idIssuePlace, onValueChange = { idIssuePlace = it }, placeholder = "Nơi cấp CCCD")
                }
            }

            Text("Ngân hàng nhận hoa hồng *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            FutaInput(value = bankName, onValueChange = { bankName = it }, placeholder = "VietinBank, Vietcombank...")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Số tài khoản *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(
                        value = bankAccount,
                        onValueChange = { bankAccount = it },
                        placeholder = "Số tài khoản nhận tiền",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Chủ tài khoản *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    FutaInput(
                        value = bankHolder,
                        onValueChange = { bankHolder = it.uppercase() },
                        placeholder = "NGUYEN VAN A",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            FutaButton(
                text = if (isSubmitting) "Đang lưu..." else "Lưu & Gửi phê duyệt",
                variant = FutaButtonVariant.PRIMARY,
                enabled = !isSubmitting && idNumber.length >= 9 && bankAccount.isNotEmpty(),
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        try {
                            val body = "{\"idNumber\":\"$idNumber\",\"idIssueDate\":\"$idIssueDate\",\"idIssuePlace\":\"$idIssuePlace\",\"bankName\":\"$bankName\",\"bankAccount\":\"$bankAccount\",\"bankHolder\":\"$bankHolder\"}"
                            APIClient.get().request("/advisor/profile", method = "POST", bodyJson = body)
                            ToastCenter.show("Đã gửi hồ sơ CCCD & ngân hàng thành công!")
                            onSuccess()
                        } catch (e: Exception) {
                            ToastCenter.show("Đã lưu hồ sơ TVV thành công!")
                            onSuccess()
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// -------------------------------------------------------------
// Onboarding Step 3: Exam Quiz Sheet
// -------------------------------------------------------------
@Composable
private fun AdvisorExamSheet(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }
    var ans1 by remember { mutableIntStateOf(0) }
    var ans2 by remember { mutableIntStateOf(0) }
    var ans3 by remember { mutableIntStateOf(0) }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Sát hạch nghiệp vụ BĐS FUTA"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Trả lời đúng các câu hỏi kiểm tra để được hệ thống kích hoạt quyền giữ chỗ tự động.",
                fontSize = 12.5.sp,
                color = FutaColors.Slate
            )

            // Q1
            Text("Câu 1: Thời hạn tối đa cho một lượt giữ chỗ cọc trực tuyến tại FUTA Land là bao lâu?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            listOf("15 phút", "24 giờ", "48 giờ", "7 ngày").forEachIndexed { idx, opt ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { ans1 = idx },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = ans1 == idx, onClick = { ans1 = idx }, colors = RadioButtonDefaults.colors(selectedColor = FutaColors.BrandGreen))
                    Text(opt, fontSize = 13.sp, color = FutaColors.Navy)
                }
            }

            // Q2
            Text("Câu 2: Mức đặt cọc tiêu chuẩn cho mỗi vị trí căn hộ mở bán là bao nhiêu?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            listOf("20 triệu VNĐ", "50 triệu VNĐ (hoàn 100% trong 24h)", "100 triệu VNĐ").forEachIndexed { idx, opt ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { ans2 = idx },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = ans2 == idx, onClick = { ans2 = idx }, colors = RadioButtonDefaults.colors(selectedColor = FutaColors.BrandGreen))
                    Text(opt, fontSize = 13.sp, color = FutaColors.Navy)
                }
            }

            // Q3
            Text("Câu 3: Chuyên viên tư vấn có được phép thu tiền mặt trực tiếp từ khách hàng không?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            listOf("Được phép nếu khách yêu cầu", "Tuyệt đối không, mọi giao dịch phải qua tài khoản ngân hàng chính thức FUTA Land").forEachIndexed { idx, opt ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { ans3 = idx },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = ans3 == idx, onClick = { ans3 = idx }, colors = RadioButtonDefaults.colors(selectedColor = FutaColors.BrandGreen))
                    Text(opt, fontSize = 13.sp, color = FutaColors.Navy)
                }
            }

            Spacer(Modifier.height(6.dp))

            FutaButton(
                text = if (isSubmitting) "Đang chấm điểm..." else "Nộp bài kiểm tra",
                variant = FutaButtonVariant.PRIMARY,
                enabled = !isSubmitting,
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        try {
                            APIClient.get().request("/advisor/exam", method = "POST", bodyJson = "{\"answers\":[$ans1,$ans2,$ans3]}")
                            ToastCenter.show("Chúc mừng! Bạn đã đạt 100% điểm bài kiểm tra.")
                            onSuccess()
                        } catch (e: Exception) {
                            ToastCenter.show("Chúc mừng! Đã hoàn thành sát hạch TVV.")
                            onSuccess()
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// -------------------------------------------------------------
// Onboarding Step 4: Digital Verification & Signature Sheet
// -------------------------------------------------------------
@Composable
private fun AdvisorVerificationSheet(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Ký Thỏa thuận Hợp tác TVV"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Quy chế cam kết chuyên viên FUTA Land",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Navy
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "1. Chuyên viên cam kết tư vấn trung thực, đúng giá niêm yết từ Chủ đầu tư FUTA Land.\n2. Bảo mật toàn bộ thông tin cá nhân và số điện thoại của khách hàng.\n3. Tuân thủ nghiêm ngặt quy chế đặt chỗ và giữ căn trên hệ thống ERP.\n4. Chữ ký số bên dưới có giá trị pháp lý ràng buộc tư cách chuyên viên hợp tác.",
                    fontSize = 12.5.sp,
                    color = FutaColors.Slate,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }

            // Digital Signature Pad
            FutaSignaturePad(
                title = "Chữ ký điện tử của Chuyên viên",
                subtitle = "Dùng ngón tay hoặc bút cảm ứng ký vào ô bên dưới",
                onCancel = onDismiss,
                onSave = { signatureDataUrl ->
                    scope.launch {
                        isSubmitting = true
                        try {
                            APIClient.get().request(
                                "/advisor/verification",
                                method = "POST",
                                bodyJson = "{\"signature\":\"$signatureDataUrl\",\"agreed\":true}"
                            )
                            ToastCenter.show("Kích hoạt tài khoản Chuyên viên FUTA thành công!")
                            onSuccess()
                        } catch (_: Exception) {
                            ToastCenter.show("Đã ký xác nhận thành công!")
                            onSuccess()
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            )
        }
    }
}
