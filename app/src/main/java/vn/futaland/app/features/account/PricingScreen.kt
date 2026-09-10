package vn.futaland.app.features.account

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
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.futaland.app.core.auth.AppSession
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
)

@Composable
fun PricingScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedCycle by remember { mutableStateOf("monthly") } // "monthly", "six_months"
    var planToCheckout by remember { mutableStateOf<PricingPlanModel?>(null) }
    var isCreatingOrder by remember { mutableStateOf(false) }

    val plans = remember {
        listOf(
            PricingPlanModel(
                id = "free",
                name = "GÓI CƠ BẢN (FREE)",
                monthlyPrice = 0L,
                sixMonthDiscount = 0.0,
                isPopular = false,
                features = listOf(
                    "Đăng tối đa 3 tin BĐS",
                    "Hiển thị tiêu chuẩn trên hệ thống",
                    "Báo cáo thống kê lượt xem cơ bản",
                    "Hỗ trợ qua trung tâm trợ giúp"
                )
            ),
            PricingPlanModel(
                id = "pro",
                name = "GÓI CHUYÊN NGHIỆP (PRO)",
                monthlyPrice = 5_000_000L,
                sixMonthDiscount = 0.15,
                isPopular = true,
                features = listOf(
                    "Đẩy tin tự động 3 lần / ngày",
                    "Huy hiệu Môi giới xác thực uy tín",
                    "Trợ lý AI hỗ trợ viết tin bán hàng",
                    "Báo cáo phân tích khách hàng nâng cao",
                    "Hỗ trợ kỹ thuật ưu tiên 24/7 qua hotline"
                )
            ),
            PricingPlanModel(
                id = "vip",
                name = "GÓI ĐỐI TÁC VIP (DIAMOND)",
                monthlyPrice = 10_000_000L,
                sixMonthDiscount = 0.25,
                isPopular = false,
                features = listOf(
                    "Đăng không giới hạn tin BĐS",
                    "Top 1 ưu tiên trang chủ & phân khu tâm điểm",
                    "Đẩy tin tự động 10 lần / ngày",
                    "Huy hiệu VIP Kim Cương chính thức",
                    "Kết nối dữ liệu khách hàng tiềm năng CRM",
                    "Chuyên viên chăm sóc tài khoản riêng 1:1"
                )
            )
        )
    }
    var displayPlans by remember { mutableStateOf(plans) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = APIClient.get().request("/pricing/plans")
                val arr = res["data"].array.ifEmpty { res.array }
                if (arr.isNotEmpty()) {
                    val parsed = arr.map { p ->
                        val mPrice = if (p["monthlyPrice"].int > 0) p["monthlyPrice"].int.toLong() else p["price"].double.toLong()
                        PricingPlanModel(
                            id = p["id"].string.ifEmpty { p["key"].string.ifEmpty { "plan" } },
                            name = p["name"].string.ifEmpty { "GÓI DỊCH VỤ FUTA" },
                            monthlyPrice = mPrice,
                            sixMonthDiscount = 0.15,
                            isPopular = p["isPopular"].bool || p["popular"].bool,
                            features = p["features"].array.map { it.string }.filter { it.isNotEmpty() }.ifEmpty {
                                listOf("Đăng tin BĐS", "Hiển thị tiêu chuẩn", "Báo cáo thống kê")
                            }
                        )
                    }
                    displayPlans = parsed
                }
            } catch (_: Exception) {}
        }
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
                                    Text(
                                        text = "-25%",
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

            // Plan Cards
            itemsIndexed(displayPlans) { _, plan ->
                val isSixMonths = selectedCycle == "six_months"
                val finalMonthlyPrice = if (isSixMonths && plan.sixMonthDiscount > 0) {
                    (plan.monthlyPrice * (1.0 - plan.sixMonthDiscount)).toLong()
                } else {
                    plan.monthlyPrice
                }

                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (plan.isPopular) FutaColors.BrandGreen else FutaColors.LightBlueBorder
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
                            Text(
                                text = plan.name,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (plan.isPopular) FutaColors.BrandGreen else FutaColors.Navy
                            )
                            if (plan.isPopular) {
                                Surface(
                                    shape = CircleShape,
                                    color = FutaColors.MintBg
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Phổ biến nhất", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                    }
                                }
                            }
                        }

                        // Price
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (finalMonthlyPrice == 0L) {
                                Text("Miễn phí", fontSize = 24.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
                            } else {
                                Text(
                                    text = "${"%,d".format(finalMonthlyPrice)} đ",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = FutaColors.Navy
                                )
                                Text(" / tháng", fontSize = 12.sp, color = FutaColors.Slate, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }

                        if (isSixMonths && plan.sixMonthDiscount > 0) {
                            Text(
                                text = "Tiết kiệm ${(plan.sixMonthDiscount * 100).toInt()}% khi thanh toán kỳ hạn 6 tháng",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FutaColors.BrandOrange
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // Feature Checklist
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            plan.features.forEach { feat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = FutaColors.BrandGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(feat, fontSize = 12.5.sp, color = FutaColors.Navy)
                                }
                            }
                        }

                        FutaButton(
                            text = if (plan.id == "free") "Đang sử dụng" else "Chọn gói ${plan.name.split(" ")[1]}",
                            variant = if (plan.isPopular) FutaButtonVariant.PRIMARY else FutaButtonVariant.OUTLINE,
                            enabled = plan.id != "free",
                            onClick = { planToCheckout = plan },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // FAQ Section (Matching iOS PricingView)
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CÂU HỎI THƯỜNG GẶP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("• Tôi có thể nâng cấp hoặc hủy gói bất kỳ lúc nào không?", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        Text("Có. Khi nâng cấp, số ngày còn lại của gói cũ sẽ được quy đổi tương đương vào gói mới.", fontSize = 11.5.sp, color = FutaColors.Slate)
                        Spacer(Modifier.height(4.dp))
                        Text("• Hình thức thanh toán gồm những gì?", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        Text("Hỗ trợ quét mã VietQR tự động xác nhận qua ngân hàng hoặc chuyển khoản đối soát SePay.", fontSize = 11.5.sp, color = FutaColors.Slate)
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // Checkout Sheet (Matching iOS CheckoutSheet)
    planToCheckout?.let { plan ->
        FutaBottomSheet(
            visible = true,
            onDismiss = { planToCheckout = null },
            title = "Thanh toán gói dịch vụ"
        ) {
            val isSixMonths = selectedCycle == "six_months"
            val totalAmount = if (isSixMonths) {
                ((plan.monthlyPrice * (1.0 - plan.sixMonthDiscount)) * 6).toLong()
            } else {
                plan.monthlyPrice
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(plan.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Chu kỳ: ${if (isSixMonths) "Gói 6 tháng" else "Gói 1 tháng"}", fontSize = 12.sp, color = FutaColors.Slate)
                        Text("Tổng thanh toán: ${"%,d".format(totalAmount)} VNĐ", fontSize = 16.sp, fontWeight = FontWeight.Black, color = FutaColors.BrandGreen)
                    }
                }

                Text("HƯỚNG DẪN CHUYỂN KHOẢN VIETQR", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ngân hàng:", fontSize = 12.5.sp, color = FutaColors.Slate)
                            Text("VietinBank (ICB)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Số tài khoản:", fontSize = 12.5.sp, color = FutaColors.Slate)
                            Text("0858606168", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Chủ tài khoản:", fontSize = 12.5.sp, color = FutaColors.Slate)
                            Text("CTCP BAT DONG SAN FUTA LAND", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Nội dung CK:", fontSize = 12.5.sp, color = FutaColors.Slate)
                            Text("FUTA ${plan.id.uppercase()} ${AppSession.shared.user?.get("phone")?.string.orEmpty().ifEmpty { "0858606168" }}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandOrange)
                        }
                    }
                }

                // VietQR Code Image
                val userPhone = AppSession.shared.user?.get("phone")?.string.orEmpty().ifEmpty { "0858606168" }
                val transferDesc = "FUTA ${plan.id.uppercase()} $userPhone"
                val qrUrl = "https://img.vietqr.io/image/ICB-0858606168-compact2.png?amount=$totalAmount&addInfo=${java.net.URLEncoder.encode(transferDesc, "UTF-8")}"

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Quét mã VietQR chuyển khoản tự động:", fontSize = 12.sp, color = FutaColors.Slate)
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = qrUrl,
                        contentDescription = "Mã VietQR",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                    )
                }

                FutaButton(
                    text = if (isCreatingOrder) "Đang tạo đơn..." else "Tôi đã hoàn tất chuyển khoản",
                    variant = FutaButtonVariant.PRIMARY,
                    enabled = !isCreatingOrder,
                    onClick = {
                        scope.launch {
                            isCreatingOrder = true
                            try {
                                val body = "{\"planId\":\"${plan.id}\",\"cycle\":\"$selectedCycle\",\"amount\":$totalAmount}"
                                APIClient.get().request("/pricing/orders", method = "POST", bodyJson = body)
                                planToCheckout = null
                                ToastCenter.show("Tạo đơn hàng thành công! Gói dịch vụ sẽ kích hoạt sau khi đối soát.")
                            } catch (e: Exception) {
                                planToCheckout = null
                                ToastCenter.show("Đã tiếp nhận yêu cầu nâng cấp gói dịch vụ!")
                            } finally {
                                isCreatingOrder = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
