package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.designsystem.*

data class PricingPlan(
    val id: String,
    val name: String,
    val priceMonthly: Long,
    val priceYearly: Long,
    val isPopular: Boolean = false,
    val features: List<String>
)

@Composable
fun PricingScreen(
    onBack: () -> Unit
) {
    var isYearly by remember { mutableStateOf(false) }

    val plans = remember {
        listOf(
            PricingPlan(
                "bronze",
                "Gói Khởi Đầu",
                199_000L,
                149_000L,
                false,
                listOf("Đăng tối đa 5 tin BĐS", "Đẩy tin tự động 1 lần/tuần", "Hỗ trợ duyệt tin trong 24h", "Báo cáo lượt xem cơ bản")
            ),
            PricingPlan(
                "silver",
                "Gói Tiêu Chuẩn",
                499_000L,
                379_000L,
                false,
                listOf("Đăng tối đa 20 tin BĐS", "Đẩy tin tự động 3 lần/tuần", "Ưu tiên hiển thị trang tìm kiếm", "Huy hiệu môi giới xác thực", "Hỗ trợ 24/7 qua Zalo OA")
            ),
            PricingPlan(
                "gold",
                "Gói Chuyên Nghiệp (VIP)",
                999_000L,
                749_000L,
                true,
                listOf("Đăng không giới hạn tin BĐS", "Đẩy tin tự động hàng ngày", "Top 1 trang chủ & khu vực trọng điểm", "Huy hiệu VIP Kim Cương", "Tiếp cận nguồn khách hàng CRM", "Trợ lý ảo AI tư vấn độc quyền")
            )
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
                        text = "Bảng giá dịch vụ FUTA",
                        fontSize = 16.sp,
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
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("NÂNG TẦM HIỆU QUẢ KINH DOANH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    Spacer(Modifier.height(4.dp))
                    Text("Gói Dịch Vụ Môi Giới VIP", fontSize = 20.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
                    Spacer(Modifier.height(4.dp))
                    Text("Tiếp cận hàng triệu khách hàng tiềm năng FUTA Land", fontSize = 12.5.sp, color = FutaColors.Slate)

                    Spacer(Modifier.height(16.dp))

                    // Billing cycle switcher
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            Surface(
                                onClick = { isYearly = false },
                                shape = CircleShape,
                                color = if (!isYearly) Color.White else Color.Transparent,
                                shadowElevation = if (!isYearly) 2.dp else 0.dp
                            ) {
                                Text(
                                    text = "Theo tháng",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (!isYearly) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isYearly) FutaColors.Navy else FutaColors.Slate,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                onClick = { isYearly = true },
                                shape = CircleShape,
                                color = if (isYearly) FutaColors.BrandGreen else Color.Transparent,
                                shadowElevation = if (isYearly) 2.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Theo năm",
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isYearly) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isYearly) Color.White else FutaColors.Slate
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isYearly) Color.White else FutaColors.BrandOrange
                                    ) {
                                        Text(
                                            text = "-25%",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isYearly) FutaColors.BrandGreen else Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Plan Cards
            itemsIndexed(plans, key = { _, plan -> plan.id }) { _, plan ->
                val price = if (isYearly) plan.priceYearly else plan.priceMonthly
                FutaCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (plan.isPopular) FutaColors.BrandGreen else FutaColors.LightBlueBorder,
                    borderWidth = if (plan.isPopular) 2.dp else 1.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(plan.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            if (plan.isPopular) {
                                Surface(
                                    shape = CircleShape,
                                    color = FutaColors.MintBg,
                                    border = BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("PHỔ BIẾN NHẤT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${"%,d".format(price).replace(",", ".")} đ",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = if (plan.isPopular) FutaColors.BrandGreen else FutaColors.Navy
                            )
                            Text(" / tháng", fontSize = 12.sp, color = FutaColors.Slate, modifier = Modifier.padding(bottom = 3.dp))
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = FutaColors.RowDivider)
                        Spacer(Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            plan.features.forEach { feat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = FutaColors.MintBg,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(feat, fontSize = 12.5.sp, color = FutaColors.Navy)
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        FutaButton(
                            text = if (plan.isPopular) "Đăng ký gói VIP ngay" else "Chọn gói này",
                            variant = if (plan.isPopular) FutaButtonVariant.PRIMARY else FutaButtonVariant.OUTLINE,
                            onClick = { ToastCenter.show("Hệ thống thanh toán gói ${plan.name} đang kết nối...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
