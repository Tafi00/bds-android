package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun AdminAIScreen(
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chatbot AI, 1: Provider, 2: RAG, 3: Playground

    var botName by remember { mutableStateOf("Trợ lý FUTA AI 24/7") }
    var systemPrompt by remember { mutableStateOf("Bạn là chuyên viên tư vấn bất động sản chuyên nghiệp của FUTA Land. Hãy trả lời thân thiện, chính xác về bảng giá, tiến độ và pháp lý các dự án Times Square, C5B.") }
    var selectedModel by remember { mutableStateOf("gpt-4o") }
    var isEnabled by remember { mutableStateOf(true) }

    val tabs = remember {
        listOf(
            "Chatbot AI",
            "Nhà cung cấp",
            "Kiến thức RAG",
            "Thử nghiệm"
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
                            text = "Trợ lý AI & Chatbot",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 4 Sub-Tabs (Matching iOS AdminAIView)
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
                    // TAB 1: Chatbot Config
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("CẤU HÌNH BOT TƯ VẤN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Tên hiển thị bot", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                    FutaInput(value = botName, onValueChange = { botName = it }, placeholder = "Nhập tên bot")
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Model trí tuệ nhân tạo", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("gpt-4o" to "GPT-4o", "claude-3-5" to "Claude 3.5", "llama-3" to "Llama 3").forEach { (mKey, mLabel) ->
                                            val isSel = selectedModel == mKey
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSel) FutaColors.MintBg else Color(0xFFF8FAFC),
                                                border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                                modifier = Modifier.clickable { selectedModel = mKey }
                                            ) {
                                                Text(mLabel, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) FutaColors.BrandGreen else FutaColors.Navy, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                                            }
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("System Prompt (Kịch bản tư vấn)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Slate)
                                    FutaInput(value = systemPrompt, onValueChange = { systemPrompt = it }, placeholder = "Nhập kịch bản hướng dẫn bot...", modifier = Modifier.height(100.dp))
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Kích hoạt trợ lý AI trên Website & App", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                                    FutaSwitch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                                }

                                FutaButton(
                                    text = "Lưu cấu hình Chatbot AI",
                                    variant = FutaButtonVariant.PRIMARY,
                                    onClick = { ToastCenter.show("Đã cập nhật cấu hình Trợ lý AI thành công!") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 2: AI Providers
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("NHÀ CUNG CẤP KẾT NỐI (PROVIDERS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                listOf("OpenAI API (GPT-4o)", "Anthropic Claude (Sonnet 3.5)", "Google Gemini Flash").forEach { p ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(p, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy)
                                        Surface(shape = CircleShape, color = FutaColors.MintBg) {
                                            Text("Sẵn sàng", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 3: Knowledge Base RAG
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("TÀI LIỆU HUẤN LUYỆN (RAG EMBEDDINGS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text("Các file PDF mặt bằng, chính sách chiết khấu và hợp đồng mẫu đã được vector hóa để AI trích xuất.", fontSize = 12.sp, color = FutaColors.Slate)
                                listOf(
                                    "Chinh_sach_ban_hang_Times_Square_2026.pdf (142 chunks)",
                                    "So_do_mat_bang_phan_khu_C5B.pdf (88 chunks)",
                                    "Huong_dan_thu_tuc_vay_ngan_hang_0_lai_suat.pdf (64 chunks)"
                                ).forEach { doc ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Article, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(doc, fontSize = 12.sp, color = FutaColors.Navy)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // TAB 4: Playground
                    item {
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("THỬ NGHIỆM TRÒ CHUYỆN (PLAYGROUND)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text("Hỏi thử: 'Tôi có 3 tỷ, có mua được căn 2 phòng ngủ Times Square view biển không?'", fontSize = 12.sp, color = FutaColors.Slate)
                                FutaButton(
                                    text = "Gửi câu hỏi thử nghiệm",
                                    variant = FutaButtonVariant.OUTLINE,
                                    onClick = { ToastCenter.show("AI: Với 3 tỷ, bạn có thể thanh toán trước 30% và vay 70% với lãi suất 0% trong 24 tháng!") },
                                    modifier = Modifier.fillMaxWidth()
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
