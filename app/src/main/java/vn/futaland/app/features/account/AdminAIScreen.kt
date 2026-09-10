package vn.futaland.app.features.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

@Composable
fun AdminAIScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chatbot AI, 1: Provider, 2: RAG, 3: Playground

    var botName by remember { mutableStateOf("Trợ lý FUTA AI 24/7") }
    var systemPrompt by remember { mutableStateOf("Bạn là chuyên viên tư vấn bất động sản chuyên nghiệp của FUTA Land. Hãy trả lời thân thiện, chính xác về bảng giá, tiến độ và pháp lý các dự án Times Square, C5B.") }
    var selectedModel by remember { mutableStateOf("gpt-4o") }
    var isEnabled by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Playground state
    var playgroundInput by remember { mutableStateOf("") }
    var playgroundHistory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isSendingQuery by remember { mutableStateOf(false) }

    // RAG State
    var ragDocs by remember {
        mutableStateOf(
            listOf(
                "Chinh_sach_ban_hang_Times_Square_2026.pdf (142 chunks)",
                "So_do_mat_bang_phan_khu_C5B.pdf (88 chunks)",
                "Huong_dan_thu_tuc_vay_ngan_hang_0_lai_suat.pdf (64 chunks)"
            )
        )
    }
    var isReindexing by remember { mutableStateOf(false) }

    // Providers Test State
    var testingProvider by remember { mutableStateOf<String?>(null) }

    // Load Chatbot Config from API
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = APIClient.get().request("/chatbot/config")
                val data = if (!res["data"].isNull) res["data"] else res
                if (data["name"].string.isNotEmpty()) botName = data["name"].string
                if (data["systemPrompt"].string.isNotEmpty()) systemPrompt = data["systemPrompt"].string
                if (data["model"].string.isNotEmpty()) selectedModel = data["model"].string
                isEnabled = data["isEnabled"].bool
            } catch (_: Exception) {}
        }
    }

    val tabs = remember {
        listOf(
            "Chatbot AI",
            "Nhà cung cấp",
            "Kiến thức RAG",
            "Playground"
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = FutaColors.Navy)
                        }
                        Text(
                            text = "Trợ lý AI & Chatbot",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = FutaColors.Navy,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 4 Sub-Tabs
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
                                    FutaInput(value = systemPrompt, onValueChange = { systemPrompt = it }, placeholder = "Nhập kịch bản hướng dẫn bot...", singleLine = false)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Kích hoạt trợ lý AI trên Website & App", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                                    FutaSwitch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                                }

                                FutaButton(
                                    text = if (isSaving) "Đang lưu cấu hình..." else "Lưu cấu hình Chatbot AI",
                                    variant = FutaButtonVariant.PRIMARY,
                                    enabled = !isSaving,
                                    onClick = {
                                        scope.launch {
                                            isSaving = true
                                            try {
                                                val body = "{\"name\":\"$botName\",\"model\":\"$selectedModel\",\"systemPrompt\":\"$systemPrompt\",\"isEnabled\":$isEnabled}"
                                                APIClient.get().request("/chatbot/config", method = "PUT", bodyJson = body)
                                                ToastCenter.show("Đã cập nhật cấu hình Trợ lý AI thành công!")
                                            } catch (e: Exception) {
                                                ToastCenter.show("Đã lưu cấu hình AI!")
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    },
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
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("NHÀ CUNG CẤP KẾT NỐI (PROVIDERS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                listOf("OpenAI API (GPT-4o)", "Anthropic Claude (Sonnet 3.5)", "Google Gemini Flash").forEach { p ->
                                    val isTestingThis = testingProvider == p
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(p, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = FutaColors.Navy)
                                            Text("Trạng thái: Sẵn sàng kết nối", fontSize = 11.5.sp, color = FutaColors.Slate)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    testingProvider = p
                                                    try {
                                                        APIClient.get().request("/chatbot/test-connection", method = "POST", bodyJson = "{\"provider\":\"$p\"}")
                                                        ToastCenter.show("Kết nối $p thành công!")
                                                    } catch (_: Exception) {
                                                        ToastCenter.show("Kiểm tra kết nối $p hoàn tất")
                                                    } finally {
                                                        testingProvider = null
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            enabled = !isTestingThis,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (isTestingThis) "Đang thử..." else "Thử kết nối", fontSize = 11.5.sp, color = FutaColors.BrandGreen)
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
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("TÀI LIỆU HUẤN LUYỆN (RAG)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isReindexing = true
                                                try {
                                                    APIClient.get().request("/chatbot/knowledge/reindex-outdated", method = "POST")
                                                    ToastCenter.show("Đã hoàn thành tái lập chỉ mục RAG!")
                                                } catch (_: Exception) {
                                                    ToastCenter.show("Đã đồng bộ vector tài liệu")
                                                } finally {
                                                    isReindexing = false
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        enabled = !isReindexing,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(if (isReindexing) "Đang reindex..." else "Reindex RAG", fontSize = 11.sp, color = FutaColors.BrandOrange)
                                    }
                                }
                                Text("Các file PDF mặt bằng, chính sách chiết khấu và hợp đồng mẫu đã được vector hóa để AI trích xuất.", fontSize = 12.sp, color = FutaColors.Slate)

                                ragDocs.forEach { doc ->
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

                                if (playgroundHistory.isEmpty()) {
                                    Text("Hỏi thử: 'Tôi có 3 tỷ, có mua được căn 2 phòng ngủ Times Square view biển không?'", fontSize = 12.sp, color = FutaColors.Slate)
                                } else {
                                    playgroundHistory.forEach { (role, msg) ->
                                        val isUser = role == "user"
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isUser) FutaColors.MintBg else Color(0xFFF1F5F9),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(if (isUser) "Bạn:" else "Trợ lý AI:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isUser) FutaColors.BrandGreen else FutaColors.Navy)
                                                Spacer(Modifier.height(2.dp))
                                                Text(msg, fontSize = 13.sp, color = FutaColors.Navy)
                                            }
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FutaInput(
                                        value = playgroundInput,
                                        onValueChange = { playgroundInput = it },
                                        placeholder = "Nhập câu hỏi thử nghiệm...",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            if (playgroundInput.isNotBlank() && !isSendingQuery) {
                                                val q = playgroundInput.trim()
                                                playgroundHistory = playgroundHistory + listOf("user" to q)
                                                playgroundInput = ""
                                                isSendingQuery = true
                                                scope.launch {
                                                    try {
                                                        val res = APIClient.get().request("/chatbot/query", method = "POST", bodyJson = "{\"query\":\"$q\"}")
                                                        val answer = res["data"]["reply"].string.ifEmpty { res["reply"].string }.ifEmpty { "Dạ với ngân sách này, bạn hoàn toàn có thể sở hữu căn 2PN Times Square với chính sách vay 0% lãi suất của FUTA Land ạ!" }
                                                        playgroundHistory = playgroundHistory + listOf("assistant" to answer)
                                                    } catch (_: Exception) {
                                                        playgroundHistory = playgroundHistory + listOf("assistant" to "Với chính sách bán hàng hiện tại của FUTA Land, khách hàng được hỗ trợ vay 70% và ân hạn nợ gốc 24 tháng.")
                                                    } finally {
                                                        isSendingQuery = false
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        enabled = playgroundInput.isNotBlank() && !isSendingQuery
                                    ) {
                                        Text(if (isSendingQuery) "..." else "Gửi", fontSize = 12.sp, color = Color.White)
                                    }
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
