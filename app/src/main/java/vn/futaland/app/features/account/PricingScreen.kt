package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.i18n.translated
import vn.futaland.app.core.network.JSONValue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import vn.futaland.app.designsystem.*

data class PricingPlanModel(
    val id: String,
    val name: String,
    val monthlyPrice: Long,
    val sixMonthDiscount: Double,
    val isPopular: Boolean = false,
    val features: List<String>
) {
    /** Display price for the selected cycle; the payable amount always comes from the server order. */
    fun price(cycle: String): Long =
        if (cycle == "six_months") (monthlyPrice * 6 * (1.0 - sixMonthDiscount)).toLong() else monthlyPrice
}

// Grounded catalog matching backend DEFAULT_PLAN_CATALOG (used only when /pricing/plans fails).
private val fallbackPlans = listOf(
    PricingPlanModel("free", "FREE", 0L, 0.0, features = listOf("standard_display", "basic_management", "basic_stats", "email_support")),
    PricingPlanModel("pro", "PRO", 5_000_000L, 0.2, features = listOf("priority_boost_3_per_day", "ai_content", "advanced_stats", "priority_support")),
    PricingPlanModel("vip", "VIP", 10_000_000L, 0.25, isPopular = true, features = listOf("featured_homepage", "priority_boost_10_per_day", "advanced_ai", "crm", "dedicated_support"))
)

/** Backend plan feature keys → Vietnamese labels (same as iOS PricingView.localizeFeature). */
internal fun pricingFeatureLabel(key: String): String = when (key) {
    "standard_display" -> "Hiển thị tiêu chuẩn trên hệ thống"
    "basic_management" -> "Quản lý tin đăng cơ bản"
    "basic_stats" -> "Thống kê lượt xem cơ bản"
    "email_support" -> "Hỗ trợ khách hàng qua email"
    "priority_boost_3_per_day" -> "3 lượt đẩy tin ưu tiên mỗi ngày"
    "ai_content" -> "Hỗ trợ soạn thảo nội dung AI"
    "advanced_stats" -> "Thống kê phân tích chuyên sâu"
    "priority_support" -> "Hỗ trợ ưu tiên qua kênh riêng"
    "featured_homepage" -> "Nổi bật trên trang chủ FUTA Land"
    "priority_boost_10_per_day" -> "10 lượt đẩy tin VIP mỗi ngày"
    "advanced_ai" -> "Trợ lý ảo AI cao cấp"
    "crm" -> "Tích hợp quản lý khách hàng CRM"
    "dedicated_support" -> "Chuyên viên chăm sóc 24/7 riêng biệt"
    else -> key.translated("pricing")
}

@Composable
fun PricingScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedCycle by remember { mutableStateOf("monthly") } // "monthly", "six_months"
    var planToCheckout by remember { mutableStateOf<PricingPlanModel?>(null) }
    var checkoutCycle by remember { mutableStateOf("monthly") }
    var createdOrder by remember { mutableStateOf<JSONValue?>(null) }
    var checkoutError by remember { mutableStateOf<String?>(null) }
    var isCreatingOrder by remember { mutableStateOf(false) }
    var loadingPlans by remember { mutableStateOf(true) }
    var displayPlans by remember { mutableStateOf<List<PricingPlanModel>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val res = APIClient.get().request("/pricing/plans")
            val arr = res["data"].array.ifEmpty { res.array }
            displayPlans = arr.map { p ->
                val discount = p["sixMonthDiscount"].double
                PricingPlanModel(
                    id = p.id,
                    name = p["name"].string.ifEmpty { p.id.uppercase() },
                    monthlyPrice = p["monthlyPrice"].double.toLong(),
                    sixMonthDiscount = if (discount > 0) discount else if (p.id == "vip") 0.25 else 0.2,
                    isPopular = p.id == "vip",
                    features = p["features"].array.map { it.string }.filter { it.isNotEmpty() }
                )
            }.ifEmpty { fallbackPlans }
        } catch (_: Exception) {
            displayPlans = fallbackPlans
        }
        loadingPlans = false
    }

    fun createOrder(plan: PricingPlanModel) {
        scope.launch {
            isCreatingOrder = true
            checkoutError = null
            try {
                val body = buildJsonObject {
                    put("planId", plan.id)
                    put("billingCycle", checkoutCycle)
                    put("paymentMethod", "bank_transfer")
                    put("source", "pricing")
                }.toString()
                val res = APIClient.get().request("/pricing/checkout", method = "POST", bodyJson = body)
                createdOrder = if (res["data"].isNull) res else res["data"]
            } catch (e: Exception) {
                checkoutError = e.message ?: tr("Không tạo được đơn hàng, vui lòng thử lại")
            } finally {
                isCreatingOrder = false
            }
        }
    }

    val maxDiscount = (displayPlans.maxOfOrNull { it.sixMonthDiscount } ?: 0.0)

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Bảng giá dịch vụ FUTA",
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
            // Hero Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CHỌN GIẢI PHÁP PHÙ HỢP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, letterSpacing = 0.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Bảng Giá Gói Dịch Vụ Môi Giới", fontSize = 20.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Đẩy mạnh hiệu quả bán hàng và mở rộng tệp khách hàng với các tính năng chuyên biệt từ FUTA Land.",
                        fontSize = 12.5.sp,
                        color = FutaColors.Slate,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    // Billing Cycle Selector (Matching iOS Segmented Picker)
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = if (selectedCycle == "monthly") Color.White else Color.Transparent,
                                shadowElevation = if (selectedCycle == "monthly") 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCycle = "monthly" }
                            ) {
                                Text(
                                    text = "Theo tháng",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selectedCycle == "monthly") FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedCycle == "monthly") FutaColors.Navy else FutaColors.Slate,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (selectedCycle == "six_months") Color.White else Color.Transparent,
                                shadowElevation = if (selectedCycle == "six_months") 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clickable { selectedCycle = "six_months" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Gói 6 tháng",
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selectedCycle == "six_months") FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedCycle == "six_months") FutaColors.Navy else FutaColors.Slate
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    if (maxDiscount > 0) Text(
                                        text = tr("-{0}%", (maxDiscount * 100).toInt()),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(FutaColors.BrandOrange, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (loadingPlans) {
                items(3) {
                    FutaSkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 260.dp, radius = 16.dp)
                }
            }

            // Plan Cards
            itemsIndexed(displayPlans) { _, plan ->
                val isSixMonths = selectedCycle == "six_months"
                val cyclePrice = plan.price(selectedCycle)
                val isPro = plan.id == "pro"
                val isVip = plan.id == "vip"
                val accent = if (isVip) FutaColors.BrandOrange else if (isPro) FutaColors.BrandGreen else FutaColors.Navy

                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (isVip || isPro) accent else FutaColors.LightBlueBorder,
                    borderWidth = if (isVip || isPro) 2.dp else 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                VerbatimText(
                                    text = plan.name.translated("pricing").uppercase(),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = if (isVip) "Dành cho môi giới chuyên nghiệp" else if (isPro) "Tăng tốc hiệu quả bán hàng" else "Bắt đầu trải nghiệm",
                                    fontSize = 11.sp,
                                    color = FutaColors.Slate
                                )
                            }
                            if (plan.isPopular) {
                                Surface(shape = CircleShape, color = FutaColors.BrandOrange) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("PHỔ BIẾN NHẤT", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        // Price
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (cyclePrice == 0L) {
                                Text("Miễn phí", fontSize = 24.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
                            } else {
                                VerbatimText(
                                    text = LocalizedPrice.full(cyclePrice.toDouble()),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = FutaColors.Navy
                                )
                                Text(
                                    if (isSixMonths) " / 6 tháng" else " / tháng",
                                    fontSize = 12.sp,
                                    color = FutaColors.Slate,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }

                        if (isSixMonths && plan.sixMonthDiscount > 0 && plan.monthlyPrice > 0) {
                            Text(
                                text = tr("Tiết kiệm {0}% khi thanh toán kỳ hạn 6 tháng", (plan.sixMonthDiscount * 100).toInt()),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FutaColors.BrandOrange
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // Feature Checklist
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            plan.features.forEach { feat ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = FutaColors.BrandGreen,
                                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(pricingFeatureLabel(feat), fontSize = 12.5.sp, color = FutaColors.Slate)
                                }
                            }
                        }

                        FutaButton(
                            text = if (cyclePrice == 0L) tr("Bắt đầu miễn phí") else tr("Nâng cấp gói {0}", plan.name.translated("pricing")),
                            variant = if (isVip) FutaButtonVariant.SECONDARY else if (isPro) FutaButtonVariant.PRIMARY else FutaButtonVariant.OUTLINE,
                            onClick = {
                                checkoutCycle = selectedCycle
                                createdOrder = null
                                checkoutError = null
                                planToCheckout = plan
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // FAQ Section (Matching iOS PricingView.faqSection)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text("Câu hỏi thường gặp", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    PricingFaqItem(
                        q = "Tôi có thể thanh toán bằng phương thức nào?",
                        a = "Hiện tại FUTA Land hỗ trợ hình thức Chuyển khoản ngân hàng qua mã VietQR chuẩn NAPAS 24/7."
                    )
                    PricingFaqItem(
                        q = "Gói dịch vụ có được kích hoạt ngay không?",
                        a = "Sau khi bạn thực hiện chuyển khoản với đúng nội dung mã đơn hàng, hệ thống sẽ đối soát và kích hoạt gói cho tài khoản của bạn."
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Checkout Sheet (Matching iOS CheckoutSheet): confirm → POST /pricing/checkout → server order QR.
    planToCheckout?.let { plan ->
        FutaBottomSheet(
            visible = true,
            onDismiss = { if (!isCreatingOrder) planToCheckout = null },
            title = "Thanh toán dịch vụ"
        ) {
            val order = createdOrder
            if (order != null) {
                if (order["status"].string == "paid") {
                    FutaEmptyState(
                        title = "Kích hoạt thành công",
                        message = tr("Gói {0} đã được kích hoạt cho tài khoản của bạn.", plan.name.translated("pricing")),
                        icon = Icons.Default.CheckCircle
                    )
                } else {
                    PricingPaymentDetails(order)
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Tóm tắt gói dịch vụ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            SummaryRow("Gói đã chọn", plan.name.translated("pricing").uppercase())
                            SummaryRow(
                                "Chu kỳ thanh toán",
                                if (checkoutCycle == "six_months") tr("6 tháng (Giảm {0}%)", (plan.sixMonthDiscount * 100).toInt()) else tr("1 tháng")
                            )
                            SummaryRow("Tạm tính", LocalizedPrice.full(plan.price(checkoutCycle).toDouble()))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            SummaryRow("Phương thức thanh toán", tr("Chuyển khoản VietQR"), valueColor = FutaColors.BrandGreen)
                        }
                    }

                    checkoutError?.let {
                        VerbatimText(it, fontSize = 12.sp, color = Color(0xFFDC2626))
                    }

                    FutaButton(
                        text = if (isCreatingOrder) "Đang tạo đơn hàng..." else "Tiến hành lấy mã thanh toán QR",
                        variant = FutaButtonVariant.PRIMARY,
                        enabled = !isCreatingOrder,
                        onClick = { createOrder(plan) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = FutaColors.Navy) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.5.sp, color = FutaColors.Slate)
        VerbatimText(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun PricingFaqItem(q: String, a: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(q, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
            Text(a, fontSize = 12.sp, color = FutaColors.Slate, lineHeight = 17.sp)
        }
    }
}
