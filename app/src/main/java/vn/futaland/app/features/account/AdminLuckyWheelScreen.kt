package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.designsystem.*

data class WheelPrizeConfig(
    val name: String,
    val value: String,
    val probability: Float,
    val active: Boolean
)

@Composable
fun AdminLuckyWheelScreen(
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Cài đặt, 1: Cấp lượt, 2: Tra cứu

    var globalWinRate by remember { mutableFloatStateOf(45f) }
    var dailySpins by remember { mutableFloatStateOf(3f) }

    var targetPhone by remember { mutableStateOf("") }
    var grantSpinsCount by remember { mutableStateOf("3") }
    var grantReason by remember { mutableStateOf("Tri ân khách hàng thân thiết") }

    val tabs = remember {
        listOf(
            "Cài đặt & Giải thưởng",
            "Cấp lượt & VIP",
            "Tra cứu trúng thưởng"
        )
    }

    val prizes = remember {
        mutableStateListOf(
            WheelPrizeConfig("Chiết khấu 1%", "CK 1%", 0.20f, true),
            WheelPrizeConfig("Voucher 50 Triệu", "Voucher 50tr", 0.05f, true),
            WheelPrizeConfig("Chỉ Vàng 9999", "1 Chỉ Vàng", 0.02f, true),
            WheelPrizeConfig("Gói Nội Thất", "Nội thất 20tr", 0.10f, true),
            WheelPrizeConfig("Quà lưu niệm FUTA", "Quà tặng FUTA", 0.35f, true),
            WheelPrizeConfig("May mắn lần sau", "May mắn", 0.28f, true)
        )
    }

    Scaffold(
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = FutaColors.Navy)
                        }
                        Text(
                            text = "Quản trị vòng quay may mắn",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 3 Tabs matching iOS AdminLuckyWheelView
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEachIndexed { idx, label ->
                            val isSelected = selectedTab == idx
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.Navy else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { selectedTab = idx }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else FutaColors.Navy,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
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
            when (selectedTab) {
                0 -> {
                    // TAB 1: Cài đặt & Giải thưởng
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("THÔNG SỐ VẬN HÀNG TOÀN HỆ THỐNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tỷ lệ trúng quà chung", fontSize = 12.5.sp, color = FutaColors.Slate)
                                    Text("${globalWinRate.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                                }
                                Slider(
                                    value = globalWinRate,
                                    onValueChange = { globalWinRate = it },
                                    valueRange = 10f..90f,
                                    colors = SliderDefaults.colors(activeTrackColor = FutaColors.BrandGreen)
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Số lượt quay miễn phí mỗi ngày", fontSize = 12.5.sp, color = FutaColors.Slate)
                                    Text("${dailySpins.toInt()} lượt/ngày", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                }
                                Slider(
                                    value = dailySpins,
                                    onValueChange = { dailySpins = it },
                                    valueRange = 1f..10f,
                                    colors = SliderDefaults.colors(activeTrackColor = FutaColors.Navy)
                                )
                            }
                        }
                    }

                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("DANH SÁCH GIẢI THƯỞNG HIỆN HÀNH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                prizes.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(p.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                            Text("Tỷ lệ xác suất: ${(p.probability * 100).toInt()}%", fontSize = 11.5.sp, color = FutaColors.Slate)
                                        }
                                        FutaSwitch(checked = p.active, onCheckedChange = {})
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }

                                FutaButton(
                                    text = "Lưu cấu hình giải thưởng",
                                    variant = FutaButtonVariant.PRIMARY,
                                    onClick = { ToastCenter.show("Đã lưu cấu hình vòng quay may mắn!") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 2: Cấp lượt quay & VIP
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("CẤP THÊM LƯỢT QUAY CHO KHÁCH HÀNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Số điện thoại khách hàng *", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                    FutaInput(value = targetPhone, onValueChange = { targetPhone = it }, placeholder = "Nhập SĐT nhận lượt quay (ví dụ: 0901234567)")
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Số lượt cấp thêm", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                    FutaInput(value = grantSpinsCount, onValueChange = { grantSpinsCount = it }, placeholder = "Số lượt (ví dụ: 5)")
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Lý do cấp lượt", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                    FutaInput(value = grantReason, onValueChange = { grantReason = it }, placeholder = "Nhập lý do...")
                                }

                                FutaButton(
                                    text = "Xác nhận cấp lượt quay",
                                    variant = FutaButtonVariant.SECONDARY,
                                    onClick = {
                                        if (targetPhone.isNotEmpty()) {
                                            ToastCenter.show("Đã cấp thành công $grantSpinsCount lượt quay cho $targetPhone!")
                                            targetPhone = ""
                                        } else {
                                            ToastCenter.show("Vui lòng nhập số điện thoại khách hàng", isError = true)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                else -> {
                    // TAB 3: Tra cứu & Đổi quà
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("LỊCH SỬ TRÚNG THƯỞNG VỪA PHÁT SINH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                listOf(
                                    Triple("0905123456", "Voucher 50 Triệu VNĐ", "Hôm nay, 10:45"),
                                    Triple("0914987654", "Chỉ Vàng 9999", "Hôm qua"),
                                    Triple("0858606168", "Chiết khấu 1% hợp đồng", "14/05")
                                ).forEach { (phone, gift, time) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(gift, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                            Text("SĐT: $phone · $time", fontSize = 11.5.sp, color = FutaColors.Slate)
                                        }
                                        Surface(shape = CircleShape, color = FutaColors.MintBg) {
                                            Text("Đã trao", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
