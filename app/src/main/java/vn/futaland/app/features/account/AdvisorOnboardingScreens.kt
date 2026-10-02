package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.DocumentUpload
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

// Advisor onboarding steps, one page each (iOS AdvisorPackageView, AdvisorProfileView,
// AdvisorExamView, AdvisorVerificationView).

private suspend fun loadBanking(): JSONValue {
    val settings = APIClient.get().request("/cms/settings")
    val data = if (settings["data"].isNull) settings else settings["data"]
    val fromSystem = data["systemConfig"]["banking"]
    return if (fromSystem.isNull) data["banking"] else fromSystem
}

// =============================================================
// Step 1: package (GET /advisor/me, POST /advisor/me/package)
// =============================================================

@Composable
fun AdvisorPackageScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var workspace by remember { mutableStateOf(JSONValue.EmptyObject) }
    var banking by remember { mutableStateOf(JSONValue.EmptyObject) }
    var loading by remember { mutableStateOf(true) }
    var showPayment by remember { mutableStateOf(false) }

    suspend fun load() {
        runCatching { loadBanking() }.onSuccess { banking = it }
        try {
            workspace = APIClient.get().request("/advisor/me")["data"]
        } catch (e: Exception) {
            ToastCenter.show(e.message ?: tr("Không tải được dữ liệu"), isError = true)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    val account = PackageBankAccount.from(banking)
    val pendingAmount = workspace["packagePaymentAmount"].double.takeIf { it > 0 } ?: banking["yearlyPackagePrice"].double
    val isPending = workspace["packagePaymentStatus"].string == "pending"
    val isPurchased = workspace["packagePurchased"].bool
    val canStartPurchase = !isPending && !isPurchased
    // Same content the backend stores on purchase: `<PREFIX> <user id>`.
    val transferReference = run {
        val userId = AppSession.shared.user?.id.orEmpty()
        if (userId.isEmpty()) "" else {
            val prefix = banking["transferSyntaxPrefix"].string.trim().uppercase().ifEmpty { "FUTAPACK" }
            "$prefix $userId"
        }
    }

    if (showPayment) {
        BackHandler { showPayment = false }
        AdvisorPackagePaymentPage(
            account = account,
            amount = pendingAmount,
            reference = transferReference,
            onBack = { showPayment = false },
            onSubmitted = { updated ->
                if (!updated.isNull) workspace = updated
                showPayment = false
            }
        )
        return
    }

    val benefits = listOf(
        "Toàn quyền truy cập bảng hàng và giỏ hàng FutaLand",
        "Đặt giữ chỗ trực tuyến 24/7 với hệ thống ERP tự động",
        "Nhận khách hàng tiềm năng phân bổ từ chiến dịch tiếp thị",
        "Công cụ quản lý hợp đồng và ký xác thực điện tử an toàn",
        "Hỗ trợ chuyên viên quản trị giỏ hàng ưu tiên"
    )

    Scaffold(
        topBar = { AdvisorTopBar("Gói tư vấn viên", onBack) },
        containerColor = Color(0xFFF7F8FA),
        bottomBar = {
            if (!loading && canStartPurchase) {
                FutaStickyActionBar {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        FutaButton(
                            text = "Đăng ký mua gói ngay",
                            onClick = { showPayment = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Thanh toán an toàn qua chuyển khoản ngân hàng VietQR.", fontSize = 11.5.sp, color = FutaColors.Slate)
                    }
                }
            }
        }
    ) { padding ->
        if (loading) {
            AdvisorLoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdvisorSection {
                Text("Gói FutaLand Pro", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                Text("Dành riêng cho đối tác môi giới BĐS", fontSize = 13.sp, color = FutaColors.Slate)
                val (statusTitle, statusColor) = when {
                    isPurchased -> "Đã kích hoạt" to FutaColors.BrandGreen
                    isPending -> "Chờ duyệt" to FutaColors.BrandOrange
                    else -> "Chưa đăng ký" to FutaColors.Slate
                }
                AdvisorLabeledRow("Trạng thái", tr(statusTitle), valueColor = statusColor)
                AdvisorLabeledRow(
                    "Phí thành viên",
                    if (pendingAmount > 0) tr("{0} / năm", LocalizedPrice.full(pendingAmount)) else tr("Đang cập nhật")
                )
                val expires = workspace["packageExpiresAt"].string
                if (expires.isNotEmpty()) AdvisorLabeledRow("Hết hạn vào", shortDate(expires))
            }

            if (isPending) {
                AdvisorSection(footer = "Vui lòng chuyển khoản đúng số tiền và nội dung bên dưới. Quản trị viên sẽ kích hoạt gói trong vòng 2-4 giờ làm việc.") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = FutaColors.BrandOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yêu cầu thanh toán đang chờ duyệt", fontSize = 13.5.sp, color = FutaColors.Navy)
                    }
                }
                val reference = workspace["packagePaymentReference"].string
                PackageTransferSection(account, pendingAmount, reference, showsHint = false)
                if (reference.isNotEmpty() && account.isConfigured) PackageCopyReferenceButton(reference)
            }

            AdvisorSection(header = "Quyền lợi dành riêng cho Gói TVV Pro") {
                benefits.forEach { benefit ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(benefit, fontSize = 13.sp, color = FutaColors.Navy)
                    }
                }
            }
        }
    }
}

/** Transfer details only; the request is sent by "Tôi đã chuyển khoản" after a confirmation. */
@Composable
private fun AdvisorPackagePaymentPage(
    account: PackageBankAccount,
    amount: Double,
    reference: String,
    onBack: () -> Unit,
    onSubmitted: (JSONValue) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val canSubmit = account.isConfigured && amount > 0 && reference.isNotEmpty()

    Scaffold(
        topBar = { AdvisorTopBar("Chuyển khoản đăng ký gói TVV", onBack) },
        containerColor = Color(0xFFF7F8FA)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PackageTransferSection(account, amount, reference)
            if (canSubmit) {
                PackageCopyReferenceButton(reference)
                FutaButton(
                    text = if (busy) "Đang gửi..." else "Tôi đã chuyển khoản",
                    enabled = !busy,
                    onClick = { confirming = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    FutaDialog(
        visible = confirming,
        onDismiss = { confirming = false },
        title = "Bạn đã chuyển khoản chưa?",
        confirmText = "Đã chuyển khoản",
        cancelText = "Chưa chuyển khoản",
        onConfirm = {
            scope.launch {
                busy = true
                try {
                    val res = APIClient.get().request("/advisor/me/package", method = "POST")
                    ToastCenter.show(tr("Đã gửi xác nhận chuyển khoản. Vui lòng chờ Admin kiểm tra giao dịch."))
                    onSubmitted(res["data"])
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không gửi được yêu cầu"), isError = true)
                } finally {
                    busy = false
                }
            }
        }
    ) {
        Text(
            "Admin sẽ đối chiếu giao dịch. Chỉ xác nhận khi bạn đã chuyển đúng số tiền và nội dung.",
            fontSize = 13.5.sp,
            color = FutaColors.Slate
        )
    }
}

// =============================================================
// Step 2: profile (PUT /advisor/me/profile, PATCH /advisor/me/profile-asset,
// PATCH /advisor/me/commission-account, GET /agencies/options)
// =============================================================

private const val OTHER_AGENCY_ID = "other"
private val vietnamPhoneRegex = Regex("^(?:\\+84|84|0)(?:3|5|7|8|9)\\d{8}$")
private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

@Composable
fun AdvisorProfileScreen(onBack: () -> Unit, onOpenAgreements: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var uploadingCount by remember { mutableIntStateOf(0) }

    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var advisorCode by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Nam") }
    var dateOfBirth by remember { mutableStateOf("") }
    var identityNumber by remember { mutableStateOf("") }
    var identityIssuedAt by remember { mutableStateOf("") }
    var identityIssuedPlace by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var agencyId by remember { mutableStateOf("") }
    var agencyName by remember { mutableStateOf("") }
    var agencyOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var avatarUrl by remember { mutableStateOf("") }
    var identityFrontUrl by remember { mutableStateOf("") }
    var identityBackUrl by remember { mutableStateOf("") }
    var brokerCertificateUrl by remember { mutableStateOf("") }
    var brokerCertificateExempt by remember { mutableStateOf("true") }
    var introduction by remember { mutableStateOf("") }
    var bankAccount by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var taxCode by remember { mutableStateOf("") }
    var savedCommission by remember { mutableStateOf(Triple("", "", "")) }
    var profileStatus by remember { mutableStateOf("draft") }
    var reviewNote by remember { mutableStateOf("") }
    var showAgencyMenu by remember { mutableStateOf(false) }

    val isApproved = profileStatus == "approved"
    val commission = Triple(bankAccount.trim(), bankName.trim(), taxCode.trim())
    val commissionDirty = commission != savedCommission

    suspend fun loadProfile() {
        loadError = null
        try {
            coroutineScope {
                val agencies = async {
                    runCatching { APIClient.get().request("/agencies/options")["data"].array }.getOrDefault(emptyList())
                }
                val d = APIClient.get().request("/advisor/me")["data"]
                val p = d["profile"]
                profileStatus = d["profileStatus"].string
                reviewNote = p["reviewNote"].string
                fullName = p["fullName"].string.ifEmpty { d["user"]["name"].string }
                phone = p["phone"].string.ifEmpty { d["user"]["phone"].string }
                email = p["email"].string.ifEmpty { d["user"]["email"].string }
                advisorCode = p["advisorCode"].string
                gender = p["gender"].string.ifEmpty { "Nam" }
                dateOfBirth = p["dateOfBirth"].string
                identityNumber = p["identityNumber"].string.ifEmpty { p["cccd"].string }
                identityIssuedAt = p["identityIssuedAt"].string.ifEmpty { p["issuedAt"].string }
                identityIssuedPlace = p["identityIssuedPlace"].string.ifEmpty { p["issuedPlace"].string }
                address = p["address"].string
                agencyId = p["agencyId"].string
                agencyName = p["agencyName"].string
                avatarUrl = p["avatar"].string
                identityFrontUrl = p["identityFrontUrl"].string
                identityBackUrl = p["identityBackUrl"].string
                brokerCertificateUrl = p["brokerCertificateUrl"].string
                brokerCertificateExempt = p["brokerCertificateExempt"].string.ifEmpty { if (brokerCertificateUrl.isEmpty()) "true" else "false" }
                introduction = p["introduction"].string.ifEmpty { p["bio"].string }
                bankAccount = p["bankAccount"].string
                bankName = p["bankName"].string
                taxCode = p["taxCode"].string
                savedCommission = Triple(bankAccount.trim(), bankName.trim(), taxCode.trim())
                agencyOptions = agencies.await().mapNotNull { item ->
                    val id = item["id"].string
                    val name = item["name"].string
                    if (id.isEmpty() || name.isEmpty()) null else id to name
                }
            }
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không tải được dữ liệu")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadProfile() }

    /** Agencies to pick from, plus the saved one if it is no longer active, plus "Đại lý khác". */
    val agencyChoices = buildList {
        addAll(agencyOptions)
        if (agencyId.isNotEmpty() && agencyId != OTHER_AGENCY_ID && agencyOptions.none { it.first == agencyId }) {
            add(agencyId to agencyName.ifEmpty { tr("Đại lý đã chọn") })
        }
        add(OTHER_AGENCY_ID to tr("Đại lý khác"))
    }

    fun selectAgency(id: String) {
        agencyId = id
        agencyName = if (id == OTHER_AGENCY_ID) "" else agencyOptions.firstOrNull { it.first == id }?.second.orEmpty()
    }

    suspend fun saveAsset(field: String, url: String) {
        when (field) {
            "avatar" -> avatarUrl = url
            "identityFrontUrl" -> identityFrontUrl = url
            "identityBackUrl" -> identityBackUrl = url
            "brokerCertificateUrl" -> {
                brokerCertificateUrl = url
                brokerCertificateExempt = "false"
            }
        }
        val body = buildJsonObject {
            put("field", field)
            put("url", url)
        }.toString()
        runCatching { APIClient.get().request("/advisor/me/profile-asset", method = "PATCH", bodyJson = body) }
    }

    fun validationError(): String? {
        fun blank(v: String) = v.trim().isEmpty()
        return when {
            blank(avatarUrl) -> "Vui lòng tải lên ảnh đại diện"
            blank(fullName) -> "Vui lòng nhập họ và tên"
            blank(advisorCode) -> "Vui lòng nhập mã tư vấn viên"
            blank(gender) -> "Vui lòng chọn giới tính"
            blank(dateOfBirth) -> "Vui lòng nhập ngày sinh"
            blank(identityNumber) -> "Vui lòng nhập số CMND/CCCD"
            blank(identityIssuedAt) -> "Vui lòng nhập ngày cấp CMND/CCCD"
            blank(identityIssuedPlace) -> "Vui lòng nhập nơi cấp CMND/CCCD"
            blank(phone) -> "Vui lòng nhập số điện thoại"
            blank(email) -> "Vui lòng nhập email"
            blank(address) -> "Vui lòng nhập địa chỉ liên hệ"
            blank(agencyId) -> "Vui lòng chọn đại lý của bạn"
            agencyId == OTHER_AGENCY_ID && blank(agencyName) -> "Vui lòng nhập tên đại lý"
            !vietnamPhoneRegex.matches(phone.filterNot { it in " .()-" }) -> "Số điện thoại không đúng định dạng Việt Nam"
            !emailRegex.matches(email.trim()) -> "Email không đúng định dạng"
            blank(identityFrontUrl) -> "Vui lòng tải lên ảnh CCCD mặt trước"
            blank(identityBackUrl) -> "Vui lòng tải lên ảnh CCCD mặt sau"
            brokerCertificateExempt != "true" && blank(brokerCertificateUrl) -> "Vui lòng tải lên chứng chỉ môi giới hoặc xác nhận miễn nộp"
            else -> null
        }
    }

    fun saveProfile(submitForReview: Boolean) {
        if (submitForReview) {
            validationError()?.let {
                ToastCenter.show(tr(it), isError = true)
                return
            }
        }
        scope.launch {
            isSaving = true
            try {
                val body = buildJsonObject {
                    putJsonObject("profile") {
                        put("avatar", avatarUrl)
                        put("fullName", fullName)
                        put("advisorCode", advisorCode)
                        put("gender", gender)
                        put("dateOfBirth", dateOfBirth)
                        put("identityNumber", identityNumber)
                        put("identityIssuedAt", identityIssuedAt)
                        put("identityIssuedPlace", identityIssuedPlace)
                        put("phone", phone.replace(" ", ""))
                        put("email", email)
                        put("address", address)
                        // The web keeps `areas` equal to the contact address.
                        put("areas", address)
                        put("agencyId", agencyId)
                        put("agencyName", agencyName)
                        put("identityFrontUrl", identityFrontUrl)
                        put("identityBackUrl", identityBackUrl)
                        put("brokerCertificateUrl", brokerCertificateUrl)
                        put("brokerCertificateExempt", brokerCertificateExempt)
                        put("introduction", introduction)
                        put("bankAccount", bankAccount)
                        put("bankName", bankName)
                        put("taxCode", taxCode)
                    }
                    put("submitForReview", submitForReview)
                }.toString()
                APIClient.get().request("/advisor/me/profile", method = "PUT", bodyJson = body)
                if (submitForReview) {
                    ToastCenter.show(tr("Đã gửi duyệt hồ sơ. Vui lòng ký thỏa thuận để hoàn tất."))
                    onOpenAgreements()
                } else {
                    ToastCenter.show(tr("Đã lưu bản nháp hồ sơ"))
                    onBack()
                }
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không lưu được hồ sơ"), isError = true)
            } finally {
                isSaving = false
            }
        }
    }

    fun saveCommissionAccount() {
        scope.launch {
            isSaving = true
            try {
                val body = buildJsonObject {
                    put("bankAccount", bankAccount.trim())
                    put("bankName", bankName.trim())
                    put("taxCode", taxCode.trim())
                }.toString()
                APIClient.get().request("/advisor/me/commission-account", method = "PATCH", bodyJson = body)
                savedCommission = commission
                ToastCenter.show(tr("Đã cập nhật tài khoản hoa hồng"))
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể lưu tài khoản hoa hồng"), isError = true)
            } finally {
                isSaving = false
            }
        }
    }

    val editable = !isApproved && !isSaving
    val busy = isSaving || uploadingCount > 0

    Scaffold(
        topBar = { AdvisorTopBar("Hồ sơ tư vấn viên", onBack) },
        containerColor = Color(0xFFF7F8FA),
        bottomBar = {
            if (!loading && loadError == null) {
                FutaStickyActionBar {
                    if (isApproved) {
                        FutaButton(
                            text = if (isSaving) "Đang lưu..." else "Lưu tài khoản hoa hồng",
                            enabled = commissionDirty && !isSaving,
                            onClick = { saveCommissionAccount() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        FutaButton(
                            text = "Lưu bản nháp",
                            variant = FutaButtonVariant.OUTLINE,
                            enabled = fullName.isNotEmpty() && !busy,
                            onClick = { saveProfile(false) },
                            modifier = Modifier.weight(1f)
                        )
                        FutaButton(
                            text = if (isSaving) "Đang gửi..." else "Gửi duyệt & ký thỏa thuận",
                            enabled = fullName.isNotEmpty() && phone.isNotEmpty() && !busy,
                            onClick = { saveProfile(true) },
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        when {
            loading -> AdvisorLoadingState(Modifier.padding(padding))
            loadError != null -> AdvisorErrorState(loadError.orEmpty(), onRetry = { loading = true; scope.launch { loadProfile() } }, modifier = Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .clearFocusOnTap()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (profileStatus == "rejected" && reviewNote.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFEF2F2), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Yêu cầu bổ sung hồ sơ:", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            VerbatimText(reviewNote, fontSize = 12.5.sp, color = FutaColors.Navy)
                        }
                    }
                }
                if (isApproved) {
                    Surface(shape = RoundedCornerShape(12.dp), color = FutaColors.MintBg, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Hồ sơ đã được duyệt. Bạn chỉ có thể cập nhật tài khoản hoa hồng.", fontSize = 12.5.sp, color = FutaColors.Navy)
                        }
                    }
                }

                AdvisorSection(header = "Ảnh đại diện & Nhận diện") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatar = DocumentUpload.absoluteUrl(avatarUrl)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(2.dp, FutaColors.BrandGreen, CircleShape)
                                .background(FutaColors.MintBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatar.isNotEmpty()) {
                                AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.Person, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(36.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            DocumentUploadRow(
                                title = "Tải ảnh đại diện *",
                                url = "",
                                enabled = editable,
                                uploadName = "avatar",
                                onUploadingChange = { uploadingCount += if (it) 1 else -1 },
                                onUploaded = { saveAsset("avatar", it) }
                            )
                            Text("Ảnh rõ mặt, phong cách chuyên nghiệp", fontSize = 11.sp, color = FutaColors.Slate)
                        }
                    }
                }

                AdvisorSection(header = "Thông tin cá nhân (Bắt buộc)") {
                    ProfileField("Họ và tên *", fullName, editable) { fullName = it }
                    ProfileField("Mã tư vấn viên *", advisorCode, editable) { advisorCode = it }
                    FutaFormSectionField(label = "Giới tính *") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Nam", "Nữ", "Khác").forEach { option ->
                                ChoiceChip(option, gender == option, editable) { gender = option }
                            }
                        }
                    }
                    ProfileField("Ngày sinh (DD/MM/YYYY) *", dateOfBirth, editable) { dateOfBirth = it }
                    ProfileField("Số điện thoại *", phone, editable, KeyboardType.Phone) { phone = it }
                    ProfileField("Email *", email, editable, KeyboardType.Email) { email = it }
                    ProfileField("Số CCCD / Hộ chiếu *", identityNumber, editable) { identityNumber = it }
                    ProfileField("Ngày cấp CCCD *", identityIssuedAt, editable) { identityIssuedAt = it }
                    ProfileField("Nơi cấp CCCD *", identityIssuedPlace, editable) { identityIssuedPlace = it }
                }

                AdvisorSection(
                    header = "Thông tin liên hệ",
                    footer = "Giúp FUTA Land biết bạn đến từ đại lý nào. Không có trong danh sách? Chọn \"Đại lý khác\" và nhập tên."
                ) {
                    ProfileField("Địa chỉ liên hệ *", address, editable) { address = it }
                    Box {
                        val selectedName = agencyChoices.firstOrNull { it.first == agencyId }?.second.orEmpty()
                        FutaSelectField(
                            title = tr("Đại lý *"),
                            displayValue = selectedName,
                            placeholder = tr("Chọn đại lý bạn đang làm việc"),
                            onClick = { if (editable) showAgencyMenu = true }
                        )
                        FutaPopover(
                            expanded = showAgencyMenu,
                            onDismissRequest = { showAgencyMenu = false },
                            items = agencyChoices,
                            onItemSelected = { selectAgency(it.first) }
                        ) { VerbatimText(it.second, fontSize = 13.5.sp, color = FutaColors.Navy) }
                    }
                    if (agencyId == OTHER_AGENCY_ID) {
                        ProfileField("Tên đại lý *", agencyName, editable) { agencyName = it }
                    }
                }

                AdvisorSection(header = "Tài liệu xác thực (Bắt buộc ảnh CCCD)") {
                    DocumentUploadRow(
                        title = "CCCD Mặt trước *",
                        url = identityFrontUrl,
                        enabled = editable,
                        uploadName = "identityFrontUrl",
                        onUploadingChange = { uploadingCount += if (it) 1 else -1 },
                        onUploaded = { saveAsset("identityFrontUrl", it) }
                    )
                    HorizontalDivider(color = FutaColors.PanelDivider)
                    DocumentUploadRow(
                        title = "CCCD Mặt sau *",
                        url = identityBackUrl,
                        enabled = editable,
                        uploadName = "identityBackUrl",
                        onUploadingChange = { uploadingCount += if (it) 1 else -1 },
                        onUploaded = { saveAsset("identityBackUrl", it) }
                    )
                    HorizontalDivider(color = FutaColors.PanelDivider)
                    DocumentUploadRow(
                        title = "Chứng chỉ hành nghề môi giới (nếu có)",
                        url = brokerCertificateUrl,
                        enabled = editable,
                        uploadName = "brokerCertificateUrl",
                        onUploadingChange = { uploadingCount += if (it) 1 else -1 },
                        onUploaded = { saveAsset("brokerCertificateUrl", it) }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Chưa có chứng chỉ môi giới (miễn nộp)", fontSize = 13.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                        FutaSwitch(
                            checked = brokerCertificateExempt == "true",
                            onCheckedChange = { if (editable) brokerCertificateExempt = if (it) "true" else "false" }
                        )
                    }
                }

                AdvisorSection(
                    header = "Tài khoản hoa hồng & Giới thiệu",
                    footer = if (isApproved) "Bạn có thể cập nhật tài khoản hoa hồng bất cứ lúc nào, không cần duyệt lại hồ sơ." else null
                ) {
                    // The commission account stays editable at every status.
                    ProfileField("Số tài khoản nhận hoa hồng", bankAccount, !isSaving, KeyboardType.Number) { bankAccount = it }
                    ProfileField("Tên ngân hàng thụ hưởng", bankName, !isSaving) { bankName = it }
                    ProfileField("Mã số thuế cá nhân", taxCode, !isSaving, KeyboardType.Number) { taxCode = it }
                    FutaFormSectionField(label = tr("Giới thiệu bản thân")) {
                        FutaTextArea(value = introduction, onValueChange = { introduction = it }, enabled = editable, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    FutaFormSectionField(label = tr(label)) {
        FutaInput(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) FutaColors.BrandGreen else Color.White,
        border = BorderStroke(1.dp, if (selected) FutaColors.BrandGreen else FutaColors.LightBlueBorder),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else FutaColors.Navy,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

// =============================================================
// Step 3: exam (GET /advisor/me/exam, GET /advisor/questions, POST /advisor/me/exam)
// =============================================================

private fun questionText(q: JSONValue): String =
    q["content"].string.ifEmpty { q["question"].string.ifEmpty { q["text"].string } }

@Composable
fun AdvisorExamScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var workspace by remember { mutableStateOf(JSONValue.EmptyObject) }
    var examInfo by remember { mutableStateOf(JSONValue.EmptyObject) }
    var questions by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var attempts by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var answers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var secondsRemaining by remember { mutableIntStateOf(1800) }
    var timerRunning by remember { mutableStateOf(false) }
    var examResult by remember { mutableStateOf<JSONValue?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }

    val passingScore = maxOf(70, examInfo["passingScore"].int.takeIf { it > 0 } ?: 85)
    val isPassed = examInfo["examStatus"].string == "passed" ||
        workspace["examStatus"].string == "passed" ||
        attempts.any { it["status"].string == "passed" || it["score"].int >= passingScore }
    val bestScore = maxOf(attempts.maxOfOrNull { it["score"].int } ?: 0, workspace["lastScore"].int)
    val duration = maxOf(15, examInfo["duration"].int)
    val used = maxOf(examInfo["attemptsUsed"].int, attempts.size)
    val limit = examInfo["attemptLimit"].int
    val hasRemaining = limit <= 0 || used < limit

    suspend fun loadExam() {
        loading = true
        try {
            val d = APIClient.get().request("/advisor/me/exam")["data"]
            examInfo = d
            questions = d["questions"].array
            if (questions.isEmpty()) {
                questions = APIClient.get().request("/advisor/questions")["data"].array
            }
            val ws = APIClient.get().request("/advisor/me")["data"]
            workspace = ws
            attempts = ws["attempts"].array
        } catch (e: Exception) {
            ToastCenter.show(e.message ?: tr("Không tải được bài kiểm tra"), isError = true)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadExam() }

    suspend fun submitExam(reason: String) {
        if (isPassed) {
            ToastCenter.show(tr("Bài kiểm tra đã đạt, không cần nộp lại."), isError = true)
            return
        }
        isSubmitting = true
        try {
            val elapsed = duration * 60 - secondsRemaining
            val body = buildJsonObject {
                put("answers", JsonObject(answers.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }))
                put("durationSeconds", maxOf(1, elapsed).coerceAtMost(14_400))
                put("submissionReason", reason)
                examInfo["sessionToken"].string.takeIf { it.isNotEmpty() }?.let { put("sessionToken", it) }
            }.toString()
            val res = APIClient.get().request("/advisor/me/exam", method = "POST", bodyJson = body)
            examResult = res["data"]
            timerRunning = false
            ToastCenter.show(tr("Đã nộp bài kiểm tra"))
        } catch (e: Exception) {
            ToastCenter.show(e.message ?: tr("Không nộp được bài kiểm tra"), isError = true)
        } finally {
            isSubmitting = false
        }
    }

    LaunchedEffect(timerRunning) {
        if (!timerRunning) return@LaunchedEffect
        while (timerRunning && secondsRemaining > 0) {
            delay(1000)
            secondsRemaining -= 1
        }
        if (timerRunning && secondsRemaining <= 0) submitExam("timeout")
    }

    fun startExam() {
        if (isPassed) {
            ToastCenter.show(tr("Bạn đã đạt bài kiểm tra này, không cần thi lại."), isError = true)
            return
        }
        if (!hasRemaining) {
            ToastCenter.show(tr("Bạn đã sử dụng hết số lần thi."), isError = true)
            return
        }
        answers = emptyMap()
        secondsRemaining = duration * 60
        timerRunning = true
    }

    Scaffold(
        topBar = { AdvisorTopBar("Kiểm tra năng lực", onBack) },
        containerColor = Color(0xFFF7F8FA)
    ) { padding ->
        if (loading) {
            AdvisorLoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val result = examResult
            when {
                result != null -> {
                    val passed = result["examStatus"].string == "passed"
                    val tint = if (passed) FutaColors.BrandGreen else Color(0xFFDC2626)
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = tint, modifier = Modifier.size(64.dp))
                        Text(if (passed) "CHÚC MỪNG BẠN ĐÃ ĐẠT!" else "BẠN CHƯA ĐẠT ĐIỂM YÊU CẦU", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
                        Text(
                            if (passed) "Bạn đã vượt qua bài kiểm tra năng lực tư vấn viên FutaLand." else "Vui lòng ôn tập lại quy chế và thử lại.",
                            fontSize = 12.5.sp,
                            color = FutaColors.Slate,
                            textAlign = TextAlign.Center
                        )
                    }
                    AdvisorSection {
                        AdvisorLabeledRow("Điểm số đạt được", "${result["score"].int}%")
                        AdvisorLabeledRow("Số câu trả lời đúng", "${result["correct"].int} / ${result["total"].int}")
                    }
                    FutaButton(
                        text = "Quay lại",
                        onClick = {
                            examResult = null
                            timerRunning = false
                            answers = emptyMap()
                            scope.launch { loadExam() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                timerRunning -> {
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, tint = if (secondsRemaining < 300) Color(0xFFDC2626) else FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            VerbatimText(
                                "${secondsRemaining / 60}:${"%02d".format(secondsRemaining % 60)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (secondsRemaining < 300) Color(0xFFDC2626) else FutaColors.BrandGreen
                            )
                            Spacer(Modifier.weight(1f))
                            Text(tr("Đã làm: {0} / {1}", answers.size, questions.size), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                        }
                    }
                    questions.forEachIndexed { index, q ->
                        AdvisorSection {
                            Text(tr("Câu {0}: {1}", index + 1, questionText(q).translated("policy")), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            val opts = q["answers"].array.ifEmpty { q["options"].array }
                            opts.forEachIndexed { optIndex, opt ->
                                val selected = answers[q.id] == optIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) FutaColors.BrandGreen.copy(alpha = 0.08f) else Color(0xFFF7F8FA))
                                        .clickable { answers = answers + (q.id to optIndex) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        null,
                                        tint = if (selected) FutaColors.BrandGreen else FutaColors.Slate,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(opt.string.translated("policy"), fontSize = 13.sp, color = FutaColors.Navy)
                                }
                            }
                        }
                    }
                    FutaButton(
                        text = if (isSubmitting) "Đang nộp bài..." else "Nộp bài thi",
                        enabled = !isSubmitting,
                        onClick = {
                            if (answers.size < questions.size) {
                                ToastCenter.show(tr("Vui lòng trả lời toàn bộ câu hỏi trước khi nộp bài"), isError = true)
                            } else {
                                confirmSubmit = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    if (isPassed) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = FutaColors.BrandGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Chúc mừng! Bạn đã đạt bài kiểm tra", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                    Text(tr("Điểm số cao nhất: {0}% • Đủ điều kiện tư vấn viên FutaLand", bestScore), fontSize = 12.sp, color = FutaColors.Slate)
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            examInfo["title"].string.takeIf { it.isNotEmpty() }?.translated("policy") ?: tr("Bài kiểm tra quy chế tư vấn viên FutaLand"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        Text(
                            "Bài kiểm tra đánh giá mức độ hiểu biết về chính sách bán hàng, quy trình chốt cọc và tiêu chuẩn đạo đức nghề nghiệp.",
                            fontSize = 12.5.sp,
                            color = FutaColors.Slate
                        )
                    }
                    AdvisorSection {
                        AdvisorLabeledRow("Thời gian làm bài", tr("{0} phút", duration))
                        AdvisorLabeledRow("Số câu hỏi", tr("{0} câu trắc nghiệm", questions.size))
                        AdvisorLabeledRow("Điểm đạt yêu cầu", "${maxOf(70, examInfo["passingScore"].int)}%")
                        if (limit > 0) AdvisorLabeledRow("Số lần thi", tr("{0} / {1} lần", used, limit))
                    }
                    when {
                        isPassed -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = FutaColors.BrandGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Đã hoàn thành bài kiểm tra", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                }
                            }
                            FutaButton(text = "Quay lại Bảng điều khiển", onClick = onBack, modifier = Modifier.fillMaxWidth())
                        }
                        hasRemaining -> FutaButton(
                            text = "Bắt đầu làm bài thi",
                            enabled = questions.isNotEmpty(),
                            onClick = { startExam() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFEEF1F4), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                tr("Đã hết số lần làm bài thi ({0}/{1} lần)", limit, limit),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Slate,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    if (attempts.isNotEmpty()) {
                        Text("Lịch sử các lần thi trước", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        attempts.forEach { att ->
                            val passed = att["status"].string == "passed"
                            val tint = if (passed) FutaColors.BrandGreen else Color(0xFFDC2626)
                            FutaCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tr("Điểm: {0}%", att["score"].int), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = tint)
                                        VerbatimText(shortDate(att["createdAt"].string), fontSize = 11.sp, color = FutaColors.Slate)
                                    }
                                    Text(if (passed) "ĐẠT" else "CHƯA ĐẠT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    FutaDialog(
        visible = confirmSubmit,
        onDismiss = { confirmSubmit = false },
        title = "Nộp bài thi?",
        confirmText = "Xác nhận nộp bài",
        cancelText = "Tiếp tục làm bài",
        onConfirm = { scope.launch { submitExam("manual") } }
    ) {
        Text(
            tr("Bạn đã trả lời {0} / {1} câu hỏi. Bài thi nộp thủ công yêu cầu hoàn thành tất cả câu hỏi.", answers.size, questions.size),
            fontSize = 13.5.sp,
            color = FutaColors.Slate
        )
    }
}

// =============================================================
// Step 4: agreements (GET /advisor/me, GET /cms/settings, PATCH /advisor/me/signature)
// =============================================================

/** CMS templates override these defaults (same JSON as iOS `AdvisorContract.defaultSettings`). */
private fun defaultContractSettings(contract: AdvisorContract): JSONValue = JSONValue.parse(
    when (contract) {
        AdvisorContract.BROKER -> """{"title": "HỢP ĐỒNG CỘNG TÁC VIÊN KINH DOANH", "code": "Mau HD CTV FUTALAND - MUA BAN.docx", "companyName": "CÔNG TY CỔ PHẦN BẤT ĐỘNG SẢN FUTALAND", "companyAddress": "80-82 Trần Hưng Đạo, Phường Bến Thành, TP Hồ Chí Minh, Việt Nam", "companyContactAddress": "Tầng 2, Tòa nhà Danang Plaza, Số 16 Trần Phú, Phường Hải Châu, TP Đà Nẵng, Việt Nam", "companyTaxCode": "0316784953", "companyPhone": "0903715757", "companyRepresentative": "Bà Trần Thị Hoa Xim", "companyRepresentativeTitle": "Tổng Giám đốc Phụ trách Kinh doanh", "clausesText": "ĐIỀU 1. ĐỊNH NGHĨA CÁC THUẬT NGỮ\n- “Cộng tác viên kinh doanh”: Cá nhân có chứng chỉ hành nghề môi giới BĐS.\n- “Giao dịch thành công”: Khách hàng đã ký HĐMB và thanh toán đủ đợt 1.\n\nĐIỀU 2. ĐỐI TƯỢNG VÀ NỘI DUNG HỢP ĐỒNG\n- Bên A nhận Bên B làm CTV kinh doanh thực hiện nghiệp vụ tìm kiếm, tư vấn, giới thiệu khách hàng mua sản phẩm BĐS thuộc các dự án do Bên A phát triển/phân phối.\n\nĐIỀU 3. PHÍ DỊCH VỤ VÀ THANH TOÁN\n- Phí dịch vụ: 2% x Giá trị Sản phẩm sau chiết khấu (chưa gồm VAT và KPBT).\n- Đối chiếu từ ngày 25 hàng tháng, chi trả trong vòng 15 ngày bằng chuyển khoản.\n\nĐIỀU 4. BẢO MẬT VÀ HIỆU LỰC\n- Thời hạn hợp đồng: 12 tháng kể từ ngày ký."}"""
        AdvisorContract.SERVICE -> """{"title": "HỢP ĐỒNG DỊCH VỤ (Nghiên cứu phát triển thị trường)", "code": "HOP DONG DICH VU- NGHIEN CUU THI TRUONG.doc", "companyName": "CÔNG TY CỔ PHẦN BẤT ĐỘNG SẢN FUTALAND", "companyAddress": "80-82 Trần Hưng Đạo, Phường Bến Thành, TP Hồ Chí Minh, Việt Nam", "companyContactAddress": "Tầng 2, Tòa nhà Danang Plaza, Số 16 Trần Phú, Phường Hải Châu, TP Đà Nẵng, Việt Nam", "companyTaxCode": "0316784953", "companyPhone": "0903715757", "companyRepresentative": "Bà Trần Thị Hoa Xim", "companyRepresentativeTitle": "Tổng Giám đốc Phụ trách Kinh doanh", "clausesText": "ĐIỀU 1: ĐỐI TƯỢNG CỦA HỢP ĐỒNG\nBên A đồng ý thuê và Bên B đồng ý cung cấp dịch vụ nghiên cứu phát triển thị trường, bao gồm:\n1. Rà soát, đánh giá và hoạch định chiến lược Marketing.\n2. Chiến lược kinh doanh và thương mại theo từng dự án của FUTA Land.\n3. Chiến lược phát triển thương hiệu và truyền thông tích hợp (IMC).\n4. Digital Marketing, CRM và quản trị Lead.\n5. PR và quản trị danh tiếng.\n\nĐIỀU 2: QUY ĐỊNH VỀ THỰC HIỆN VÀ NGHIỆM THU\n- Thời gian thực hiện: 12 tháng kể từ ngày ký kết.\n- Nghiệm thu định kỳ theo đợt/tháng dựa trên Biên bản nghiệm thu khối lượng hoàn thành.\n\nĐIỀU 3: GIÁ TRỊ VÀ PHƯƠNG THỨC THANH TOÁN\n- Xác định theo Biên bản nghiệm thu có đầy đủ chữ ký Hai Bên. Chi trả trong 15 ngày."}"""
        AdvisorContract.ACCEPTANCE -> """{"title": "BIÊN BẢN NGHIỆM THU KHỐI LƯỢNG HOÀN THÀNH", "code": "BBNT- NGHIEN CUU THI TRUONG.docx", "companyName": "CÔNG TY CỔ PHẦN BẤT ĐỘNG SẢN FUTALAND", "companyRepresentative": "Bà Trần Thị Hoa Xim", "companyRepresentativeTitle": "Tổng Giám đốc Phụ trách Kinh doanh", "notes": "Biên bản này là cơ sở số liệu để xác định khối lượng và giá trị thanh toán cho Bên B theo Hợp đồng dịch vụ.", "items": [{"id": "item-1", "category": "Rà soát & Hoạch định Marketing", "details": "Rà soát hiện trạng chiến lược, phân tích dữ liệu thị trường và xây dựng Master Plan Marketing.", "status": "Đạt yêu cầu 100%"}, {"id": "item-2", "category": "Chiến lược thương mại dự án", "details": "Đánh giá tác động chính sách tới doanh thu và đề xuất phương án điều chỉnh bán hàng.", "status": "Đạt yêu cầu 100%"}, {"id": "item-3", "category": "Phát triển thương hiệu & IMC", "details": "Xây dựng kế hoạch truyền thông tích hợp, định vị truyền thông và Key Message dự án trọng điểm.", "status": "Đạt yêu cầu 100%"}, {"id": "item-4", "category": "Digital Marketing & Quản trị Lead", "details": "Tối ưu hóa các kênh Digital, đánh giá chất lượng lead và phối hợp chuyển đổi kinh doanh.", "status": "Đạt yêu cầu 100%"}]}"""
    }
)

private fun contractSettings(contract: AdvisorContract, config: JSONValue): JSONValue {
    var result = defaultContractSettings(contract)
    (config[contract.settingsKey].element as? JsonObject)?.forEach { (key, value) ->
        result = result.with(key, JSONValue(value))
    }
    return result
}

private fun formatSignedAt(raw: String): String = runCatching {
    val instant = java.time.Instant.parse(raw)
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(raw)

@Composable
fun AdvisorVerificationScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var contractType by remember { mutableStateOf(AdvisorContract.SERVICE) }
    var workspace by remember { mutableStateOf(JSONValue.Null) }
    var config by remember { mutableStateOf(JSONValue.Null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        loadError = null
        try {
            coroutineScope {
                val profile = async { APIClient.get().request("/advisor/me") }
                val templates = async { APIClient.get().request("/cms/settings") }
                workspace = profile.await()["data"]
                config = templates.await()["data"]["systemConfig"]
            }
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không tải được dữ liệu")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    val state = AdvisorContractState(workspace)
    val settings = contractSettings(contractType, config)

    Scaffold(
        topBar = { AdvisorTopBar("Đào tạo & Thỏa thuận", onBack) },
        containerColor = Color(0xFFF7F8FA)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!loading && loadError == null && !state.needsProfileSubmission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 10.dp)
                ) {
                    FutaSegmentTabs(
                        items = AdvisorContract.entries.toList(),
                        selectedItem = contractType,
                        onSelect = { if (!isSaving) contractType = it },
                        titleFor = { it.tabTitle }
                    )
                }
            }
            when {
                loading -> AdvisorLoadingState()
                loadError != null -> AdvisorErrorState(loadError.orEmpty(), onRetry = { scope.launch { load() } })
                state.needsProfileSubmission -> FutaEmptyState(
                    title = "Cần nộp hồ sơ trước",
                    message = "Loại hợp đồng cần ký phụ thuộc vào việc bạn có chứng chỉ môi giới hay không, nên bạn cần nộp hồ sơ cá nhân trước.",
                    icon = Icons.Default.Lock
                )
                else -> key(contractType) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        AgreementTermsCard(contractType, settings, workspace["profile"]["fullName"].string)
                        when {
                            state.isSigned(contractType) -> SavedSignatureCard(state.signature(contractType))
                            state.isReadOnly -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                Icon(Icons.Default.Lock, null, tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Hồ sơ đã được duyệt. Chưa có chữ ký được lưu cho tài liệu này.", fontSize = 13.sp, color = FutaColors.Navy)
                            }
                            else -> FutaCard(modifier = Modifier.fillMaxWidth()) {
                                Box {
                                    FutaSignaturePad(
                                        title = tr("Chữ ký điện tử của tư vấn viên"),
                                        subtitle = tr("Dùng ngón tay hoặc bút cảm ứng ký vào ô bên dưới"),
                                        onCancel = {},
                                        onSave = { dataUrl ->
                                            val selected = contractType
                                            if (isSaving || !state.canSign(selected)) return@FutaSignaturePad
                                            if (dataUrl.length > 250_000) {
                                                ToastCenter.show(tr("Ảnh chữ ký không được vượt quá 250KB"), isError = true)
                                                return@FutaSignaturePad
                                            }
                                            scope.launch {
                                                isSaving = true
                                                try {
                                                    val body = buildJsonObject {
                                                        put("contractType", selected.wire)
                                                        put("signatureDataUrl", dataUrl)
                                                    }.toString()
                                                    val res = APIClient.get().request("/advisor/me/signature", method = "PATCH", bodyJson = body)
                                                    workspace = res["data"]
                                                    ToastCenter.show(tr("Đã lưu chữ ký"))
                                                } catch (e: Exception) {
                                                    ToastCenter.show(e.message ?: tr("Không lưu được chữ ký"), isError = true)
                                                } finally {
                                                    isSaving = false
                                                }
                                            }
                                        }
                                    )
                                    if (isSaving) {
                                        Box(modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = FutaColors.BrandGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgreementTermsCard(contract: AdvisorContract, settings: JSONValue, advisorName: String) {
    FutaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(settings["title"].string.translated("policy"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            settings["companyName"].string.takeIf { it.isNotEmpty() }?.let {
                VerbatimText(it, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            }
            settings["companyAddress"].string.takeIf { it.isNotEmpty() }?.let {
                VerbatimText(it, fontSize = 12.sp, color = FutaColors.Slate)
            }
            settings["companyRepresentative"].string.takeIf { it.isNotEmpty() }?.let {
                Text(tr("Đại diện: {0}", it), fontSize = 12.sp, color = FutaColors.Navy)
            }
            if (advisorName.isNotEmpty()) Text(tr("Tư vấn viên: {0}", advisorName), fontSize = 13.sp, color = FutaColors.Navy)
            HorizontalDivider(color = FutaColors.PanelDivider)
            if (contract == AdvisorContract.ACCEPTANCE) {
                settings["items"].array.forEachIndexed { index, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${index + 1}. " + item["category"].string.translated("policy"), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text(item["details"].string.translated("policy"), fontSize = 13.sp, color = FutaColors.Navy)
                        Text(item["status"].string.translated("policy"), fontSize = 12.sp, color = FutaColors.Slate)
                    }
                }
                Text(settings["notes"].string.translated("policy"), fontSize = 13.sp, color = FutaColors.Navy)
            } else {
                Text(settings["clausesText"].string.translated("policy"), fontSize = 13.sp, color = FutaColors.Navy, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun SavedSignatureCard(signature: JSONValue) {
    val dataUrl = signature["signatureDataUrl"].string
    val bitmap = remember(dataUrl) {
        val prefix = "data:image/png;base64,"
        if (!dataUrl.startsWith(prefix)) null else runCatching {
            val bytes = Base64.decode(dataUrl.removePrefix(prefix), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    FutaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Đã ký", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
            }
            Text(tr("Ngày ký: {0}", formatSignedAt(signature["signedAt"].string)), fontSize = 12.sp, color = FutaColors.Slate)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = tr("Chữ ký đã lưu"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(Color.White)
                )
            } else {
                Text("Không thể hiển thị ảnh chữ ký đã lưu. Vui lòng liên hệ bộ phận hỗ trợ.", fontSize = 13.sp, color = FutaColors.Navy)
            }
        }
    }
}
