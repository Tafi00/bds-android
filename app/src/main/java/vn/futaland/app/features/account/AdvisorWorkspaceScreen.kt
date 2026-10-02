package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaDestinations

/**
 * Advisor workspace (iOS `AdvisorView`): activation status, the four onboarding steps,
 * business shortcuts and performance metrics. Data: `GET /advisor/me` + `GET /advisor/dashboard`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvisorWorkspaceScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var workspace by remember { mutableStateOf<JSONValue>(JSONValue.EmptyObject) }
    var dashboard by remember { mutableStateOf<JSONValue>(JSONValue.EmptyObject) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasData by remember { mutableStateOf(false) }
    var showNotAdvisorAlert by remember { mutableStateOf(false) }

    suspend fun load() {
        error = null
        try {
            coroutineScope {
                val ws = async { APIClient.get().request("/advisor/me") }
                val dash = async { APIClient.get().request("/advisor/dashboard") }
                workspace = ws.await()["data"]
                dashboard = dash.await()["data"]
            }
            hasData = true
        } catch (e: Exception) {
            error = e.message ?: tr("Không tải được dữ liệu")
        } finally {
            loading = false
        }
    }

    // Runs again when returning from a step page, so statuses stay current.
    LaunchedEffect(Unit) { load() }

    val contract = AdvisorContractState(workspace)
    val agreementsLocked = contract.needsProfileSubmission
    val isActivated = workspace["isActivated"].bool

    fun openActivatedOnly(route: String) {
        if (isActivated) onNavigate(route) else showNotAdvisorAlert = true
    }

    Scaffold(
        topBar = { AdvisorTopBar(title = "Tư vấn viên FutaLand", onBack = onBack) },
        containerColor = Color(0xFFF7F8FA)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && !hasData -> AdvisorLoadingState()
                error != null && !hasData -> AdvisorErrorState(
                    message = error.orEmpty(),
                    onRetry = { loading = true; scope.launch { load() } },
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        scope.launch { load(); refreshing = false }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ActivationBanner(workspace)
                        if (workspace["isHoldingSuspended"].bool) HoldingSuspendedAlert(workspace)

                        // Progress card
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Tiến độ xác thực tài khoản", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Spacer(Modifier.height(6.dp))
                                StatusStepRow(
                                    title = "1. Đăng ký gói tư vấn viên",
                                    statusText = packageStatusText(workspace),
                                    isDone = workspace["packagePurchased"].bool,
                                    onClick = { onNavigate(FutaDestinations.ADVISOR_PACKAGE) }
                                )
                                StatusStepRow(
                                    title = "2. Hồ sơ cá nhân & CCCD",
                                    statusText = profileStatusText(workspace),
                                    isDone = workspace["profileStatus"].string == "approved",
                                    onClick = { onNavigate(FutaDestinations.ADVISOR_PROFILE) }
                                )
                                StatusStepRow(
                                    title = "3. Bài kiểm tra năng lực",
                                    statusText = examStatusText(workspace),
                                    isDone = hasPassedExam(workspace),
                                    onClick = { onNavigate(FutaDestinations.ADVISOR_EXAM) }
                                )
                                StatusStepRow(
                                    title = "4. Ký thỏa thuận & Xác minh",
                                    statusText = when {
                                        agreementsLocked -> "Cần nộp hồ sơ trước"
                                        contract.hasCompletedSigning -> "Đã ký"
                                        else -> "Chưa hoàn tất"
                                    },
                                    isDone = contract.hasCompletedSigning,
                                    isLocked = agreementsLocked,
                                    onClick = { onNavigate(FutaDestinations.ADVISOR_VERIFICATION) }
                                )
                            }
                        }

                        // Onboarding tiles
                        SectionTitle("Lộ trình kích hoạt & Hồ sơ")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MenuTile("Gói TVV", packageStatusText(workspace), Icons.Default.CardGiftcard, FutaColors.BrandOrange, Modifier.weight(1f)) {
                                onNavigate(FutaDestinations.ADVISOR_PACKAGE)
                            }
                            MenuTile("Hồ sơ cá nhân", profileStatusText(workspace), Icons.Default.AccountBox, Color(0xFF2563EB), Modifier.weight(1f)) {
                                onNavigate(FutaDestinations.ADVISOR_PROFILE)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MenuTile("Bài kiểm tra", examStatusText(workspace), Icons.Default.Quiz, Color(0xFF7C3AED), Modifier.weight(1f)) {
                                onNavigate(FutaDestinations.ADVISOR_EXAM)
                            }
                            MenuTile(
                                "Thỏa thuận & Ký",
                                if (agreementsLocked) "Cần nộp hồ sơ trước" else "Xác thực số",
                                Icons.Default.Draw,
                                FutaColors.BrandGreen,
                                Modifier.weight(1f),
                                isLocked = agreementsLocked
                            ) { onNavigate(FutaDestinations.ADVISOR_VERIFICATION) }
                        }

                        // Business shortcuts. Products and registrations are for activated advisors only.
                        SectionTitle("Nghiệp vụ kinh doanh")
                        ActionRow("Sản phẩm & Giữ chỗ", "Kho hàng BĐS, đặt giữ chỗ trực tuyến", Icons.Default.Apartment, Color(0xFF2563EB)) {
                            openActivatedOnly(FutaDestinations.ADVISOR_PRODUCTS)
                        }
                        ActionRow("Đăng ký bán của tôi", "Theo dõi căn hộ đăng ký bán, cọc giữ chỗ", Icons.Default.Assignment, FutaColors.BrandGreen) {
                            openActivatedOnly(FutaDestinations.ADVISOR_REGISTRATIONS)
                        }
                        if (AppSession.shared.role in STAFF_APPOINTMENT_ROLES) {
                            ActionRow("Lịch hẹn xem nhà", "Xác nhận, đổi giờ & theo dõi buổi dẫn khách", Icons.Default.EventAvailable, Color(0xFF2563EB)) {
                                onNavigate(FutaDestinations.STAFF_APPOINTMENTS)
                            }
                        }
                        ActionRow("Đề xuất & Ý kiến", "Gửi đề xuất kinh doanh tới quản trị", Icons.Default.Lightbulb, Color(0xFFF29900)) {
                            onNavigate(FutaDestinations.ADVISOR_PROPOSALS)
                        }

                        // Performance metrics
                        SectionTitle("Hiệu suất hoạt động")
                        val m = dashboard["metrics"]
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("Khách hàng", m["customers"]["value"].int, Modifier.weight(1f))
                            MetricBox("Lượt xem", m["views"]["value"].int, Modifier.weight(1f))
                            MetricBox("Tư vấn", m["consultations"]["value"].int, Modifier.weight(1f))
                            MetricBox("Hợp đồng", m["contracts"]["value"].int, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    FutaDialog(
        visible = showNotAdvisorAlert,
        onDismiss = { showNotAdvisorAlert = false },
        title = "Bạn chưa là tư vấn viên",
        confirmText = "Đã hiểu",
        onConfirm = {},
        cancelText = null
    ) {
        Text(
            "Hoàn tất gói tư vấn viên, hồ sơ cá nhân và bài kiểm tra để kích hoạt tài khoản.",
            fontSize = 13.5.sp,
            color = FutaColors.Slate
        )
    }
}

// -------------------------------------------------------------
// Status helpers (same wording as iOS)
// -------------------------------------------------------------

internal fun packageStatusText(ws: JSONValue): String {
    if (ws["packagePurchased"].bool) return "Đã đăng ký"
    return when (ws["packagePaymentStatus"].string) {
        "pending" -> "Chờ duyệt TT"
        "approved" -> "Đã kích hoạt"
        "rejected" -> "Bị từ chối"
        else -> "Chưa mua gói"
    }
}

internal fun profileStatusText(ws: JSONValue): String = when (ws["profileStatus"].string) {
    "approved" -> "Đã duyệt"
    "pending" -> "Đang xét duyệt"
    "rejected" -> "Cần bổ sung"
    else -> "Chưa hoàn tất"
}

internal fun hasPassedExam(ws: JSONValue): Boolean =
    ws["examStatus"].string == "passed" || ws["attempts"].array.any { it["status"].string == "passed" }

internal fun examStatusText(ws: JSONValue): String {
    val attempts = ws["attempts"].array
    if (hasPassedExam(ws)) {
        val maxScore = maxOf(ws["lastScore"].int, attempts.maxOfOrNull { it["score"].int } ?: 0)
        return tr("Đạt ({0}đ)", maxScore)
    }
    return when (ws["examStatus"].string) {
        "failed" -> tr("Chưa đạt ({0}đ)", ws["lastScore"].int)
        else -> "Chưa làm bài"
    }
}

private fun activationDescription(act: String): String = when (act) {
    "active" -> "Bạn có toàn quyền truy cập các tính năng bán hàng và giữ chỗ."
    "package_required" -> "Vui lòng đăng ký gói tư vấn viên để bắt đầu."
    "package_pending" -> "Hệ thống đang xác thực khoản thanh toán gói của bạn."
    "profile_pending" -> "Hồ sơ của bạn đang được ban quản trị xét duyệt."
    "exam_pending" -> "Vui lòng hoàn thành bài kiểm tra năng lực tư vấn."
    "expired" -> "Gói tư vấn viên đã hết hạn. Vui lòng gia hạn."
    else -> "Hoàn tất các bước xác thực bên dưới để kích hoạt tài khoản."
}

/**
 * Signed agreements (iOS `AdvisorContractState`). `profile.signedContracts` is either an object
 * or a JSON string keyed by contract type.
 */
internal class AdvisorContractState(private val workspace: JSONValue) {
    val signatures: JSONValue = run {
        val raw = workspace["profile"]["signedContracts"]
        if (raw.element is kotlinx.serialization.json.JsonObject) raw else JSONValue.parse(raw.string)
    }

    fun signature(contract: AdvisorContract): JSONValue = signatures[contract.wire]
    fun isSigned(contract: AdvisorContract): Boolean {
        val saved = signature(contract)
        return saved["signatureDataUrl"].string.isNotEmpty() && saved["signedAt"].string.isNotEmpty()
    }

    val isReadOnly: Boolean get() = workspace["profileStatus"].string == "approved"

    /** The contract to sign depends on the profile (broker certificate or not). */
    val needsProfileSubmission: Boolean get() = workspace["profileStatus"].string == "draft"

    val requiredContracts: List<AdvisorContract>
        get() = if (workspace["profile"]["brokerCertificateExempt"].string == "true")
            listOf(AdvisorContract.SERVICE, AdvisorContract.ACCEPTANCE) else listOf(AdvisorContract.BROKER)

    val hasCompletedSigning: Boolean get() = requiredContracts.all { isSigned(it) }
    fun canSign(contract: AdvisorContract): Boolean = !isReadOnly && !isSigned(contract)
}

internal enum class AdvisorContract(val wire: String, val tabTitle: String, val settingsKey: String) {
    SERVICE("service_contract", "Hợp đồng dịch vụ", "serviceContract"),
    BROKER("broker_contract", "Hợp đồng môi giới", "brokerContract"),
    ACCEPTANCE("acceptance_report", "Biên bản nghiệm thu", "acceptanceReport")
}

// -------------------------------------------------------------
// Building blocks
// -------------------------------------------------------------

@Composable
private fun ActivationBanner(ws: JSONValue) {
    val isAct = ws["isActivated"].bool
    val tint = if (isAct) FutaColors.BrandGreen else FutaColors.BrandOrange
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isAct) Icons.Default.Verified else Icons.Default.Warning, null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isAct) "Tài khoản tư vấn viên đã kích hoạt" else "Chưa hoàn tất kích hoạt tài khoản",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FutaColors.Navy
                )
                Spacer(Modifier.height(2.dp))
                Text(activationDescription(ws["activationStatus"].string), fontSize = 12.sp, color = FutaColors.Slate)
            }
        }
    }
}

@Composable
private fun HoldingSuspendedAlert(ws: JSONValue) {
    val red = Color(0xFFDC2626)
    Surface(shape = RoundedCornerShape(12.dp), color = red.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.PanTool, null, tint = red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Tạm dừng quyền giữ chỗ bất động sản", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = red)
                val reason = ws["holdingSuspendedReason"].string
                if (reason.isNotEmpty()) Text(tr("Lý do: {0}", reason), fontSize = 12.sp, color = FutaColors.Navy)
                val until = ws["holdingSuspendedUntil"].string
                if (until.isNotEmpty()) Text(tr("Hiệu lực đến: {0}", shortDate(until)), fontSize = 11.sp, color = FutaColors.Slate)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
}

private fun showAgreementsLockedMessage() {
    ToastCenter.show(tr("Vui lòng nộp hồ sơ cá nhân trước khi ký thỏa thuận."))
}

@Composable
private fun StatusStepRow(
    title: String,
    statusText: String,
    isDone: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { if (isLocked) showAgreementsLockedMessage() else onClick() }
            .alpha(if (isLocked) 0.55f else 1f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                isLocked -> Icons.Default.Lock
                isDone -> Icons.Default.CheckCircle
                else -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (isDone) FutaColors.BrandGreen else FutaColors.Slate,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 13.5.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
        Text(
            statusText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDone) FutaColors.BrandGreen else FutaColors.Slate,
            textAlign = TextAlign.End
        )
        Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .alpha(if (isLocked) 0.55f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable { if (isLocked) showAgreementsLockedMessage() else onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.weight(1f))
                if (isLocked) Icon(Icons.Default.Lock, null, tint = FutaColors.Slate, modifier = Modifier.size(14.dp))
            }
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            Text(subtitle, fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1)
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                Text(subtitle, fontSize = 12.sp, color = FutaColors.Slate)
            }
            Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MetricBox(title: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            vn.futaland.app.core.i18n.VerbatimText("$value", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            Text(title, fontSize = 10.5.sp, color = FutaColors.Slate, maxLines = 1)
        }
    }
}
