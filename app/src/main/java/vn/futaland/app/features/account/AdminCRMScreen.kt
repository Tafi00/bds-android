package vn.futaland.app.features.account

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

data class CrmLead(
    val id: String,
    val name: String,
    val phone: String,
    val demand: String,
    val budget: String,
    val stage: String,
    val advisor: String
)

@Composable
fun AdminCRMScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedStage by remember { mutableStateOf("lead") }
    var search by remember { mutableStateOf("") }

    val stages = remember {
        listOf(
            "lead" to "Mới (Lead)",
            "contacted" to "Đang liên hệ",
            "viewing" to "Hẹn xem nhà",
            "holding" to "Đặt cọc",
            "closed" to "Thành công"
        )
    }

    val leads = remember {
        mutableStateListOf(
            CrmLead("1", "Trần Văn Bình", "0912345678", "Căn 2PN View biển Times Square", "3.5 - 4 Tỷ", "lead", "Nguyễn Văn Tuấn"),
            CrmLead("2", "Lê Thị Hồng", "0908765432", "Liền kề Khu C5B Hòa Khánh", "16 Tỷ", "contacted", "Admin Pro"),
            CrmLead("3", "Hoàng Minh Trí", "0987654321", "Căn 1PN Masteri Thảo Điền", "2.8 Tỷ", "viewing", "Trần Thị Mai"),
            CrmLead("4", "Phạm Hải Đăng", "0934567890", "Biệt thự view sông Sài Gòn", "25 Tỷ", "holding", "Admin FutaLand")
        )
    }

    val filteredLeads = remember(leads, selectedStage, search) {
        leads.filter { lead ->
            val matchStage = selectedStage == "all" || lead.stage == selectedStage
            val matchSearch = search.isEmpty() || lead.name.contains(search, ignoreCase = true) || lead.phone.contains(search) || lead.demand.contains(search, ignoreCase = true)
            matchStage && matchSearch
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = FutaColors.PageBg,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Column {
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
                            text = "Chăm sóc khách hàng",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy
                        )
                        FutaHeaderIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Thêm mới",
                            tint = FutaColors.BrandGreen,
                            onClick = { ToastCenter.show("Tạo hồ sơ khách hàng mới") }
                        )
                    }

                    // Kanban Stage Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stages.forEach { (stKey, stLabel) ->
                            val isSelected = selectedStage == stKey
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) FutaColors.BrandGreen else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { selectedStage = stKey }
                            ) {
                                Text(
                                    text = stLabel,
                                    fontSize = 12.sp,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FutaInput(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "Tìm kiếm theo tên khách, SĐT, nhu cầu...",
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (filteredLeads.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Không có khách hàng",
                        message = "Chưa có dữ liệu khách hàng trong giai đoạn này."
                    )
                }
            } else {
                itemsIndexed(filteredLeads, key = { _, l -> l.id }) { _, lead ->
                    FutaCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(lead.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    Text(lead.phone, fontSize = 12.sp, color = FutaColors.Slate)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFDF6EE),
                                        modifier = Modifier.size(36.dp).clickable {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phone}")))
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Phone, null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.size(36.dp).clickable {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://zalo.me/${lead.phone}")))
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("Zalo", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF0068FF))
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Nhu cầu BĐS", fontSize = 11.sp, color = FutaColors.Slate)
                                    Text(lead.demand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Ngân sách", fontSize = 11.sp, color = FutaColors.Slate)
                                    Text(lead.budget, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E7643))
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("TVV phụ trách: ${lead.advisor}", fontSize = 11.5.sp, color = FutaColors.Slate)
                                Text(
                                    text = "Chuyển giai đoạn ▶",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FutaColors.BrandGreen,
                                    modifier = Modifier.clickable {
                                        ToastCenter.show("Đã chuyển khách ${lead.name} sang bước tiếp theo")
                                    }
                                )
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
