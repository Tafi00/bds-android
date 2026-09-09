package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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

@Composable
fun BillingScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Gói dịch vụ & Hạn mức",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onUpgradeClick) {
                        Text("Nâng cấp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FutaColors.PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Active Plan Card (Matching iOS activePlanCard)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF064D3D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("GÓI DỊCH VỤ HIỆN TẠI", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                                Text("GÓI CHUYÊN NGHIỆP (PRO)", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Surface(shape = CircleShape, color = Color.White) {
                                Text("Đang hoạt động", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Hạn sử dụng: 30/12/2026", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                            Text("Gia hạn tự động: Bật", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }
            }

            // 2. Quota Usage Section
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("HẠN MỨC & QUYỀN LỢI ĐÃ DÙNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                        // Quota 1
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tin đăng BĐS hoạt động", fontSize = 12.5.sp, color = FutaColors.Slate)
                                Text("14 / 20 tin (70%)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            }
                            LinearProgressIndicator(
                                progress = { 0.7f },
                                color = FutaColors.BrandGreen,
                                trackColor = Color(0xFFE2E8F0),
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Quota 2
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Lượt đẩy tin tự động", fontSize = 12.5.sp, color = FutaColors.Slate)
                                Text("18 / 30 lượt", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            }
                            LinearProgressIndicator(
                                progress = { 0.6f },
                                color = FutaColors.BrandOrange,
                                trackColor = Color(0xFFE2E8F0),
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Quota 3
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Trợ lý AI & Báo cáo nâng cao", fontSize = 12.5.sp, color = FutaColors.Slate)
                            Surface(shape = CircleShape, color = FutaColors.MintBg) {
                                Text("Không giới hạn", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }

            // 3. Invoice History Section
            item {
                FutaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("LỊCH SỬ GIAO DỊCH & HÓA ĐƠN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                        listOf(
                            Triple("ORD-9842", "Gói Chuyên Nghiệp (PRO) - 6 tháng", "25.500.000 đ · 14/05/2026"),
                            Triple("ORD-6120", "Gói Cơ Bản (FREE)", "0 đ · 01/01/2026")
                        ).forEach { (code, title, meta) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    Text("Mã: $code · $meta", fontSize = 11.5.sp, color = FutaColors.Slate)
                                }
                                Surface(shape = CircleShape, color = FutaColors.MintBg) {
                                    Text("Đã thanh toán", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }

            // Upgrade CTA
            item {
                FutaButton(
                    text = "Nâng cấp lên gói VIP (Kim Cương)",
                    variant = FutaButtonVariant.SECONDARY,
                    onClick = onUpgradeClick,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
