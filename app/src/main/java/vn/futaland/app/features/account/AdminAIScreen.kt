package vn.futaland.app.features.account

import androidx.compose.animation.*
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

enum class AITrainingTab(val label: String) {
    KNOWLEDGE("Kiến thức"),
    PROMPTS("Prompt"),
    PLAYGROUND("Playground"),
    FEEDBACK("Feedback")
}

@Composable
fun AdminAIScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(AITrainingTab.KNOWLEDGE) }
    var loading by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }

    // Training Overview Stats
    var overviewData by remember { mutableStateOf<JSONValue>(JSONValue.Null) }
    var botConfig by remember { mutableStateOf<JSONValue>(JSONValue.Null) }

    // 1. Knowledge Documents
    var documents by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var knowledgeSearch by remember { mutableStateOf("") }
    var isReindexingAll by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }

    // 2. Prompt Versions
    var prompts by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var selectedPromptId by remember { mutableStateOf("") }
    var promptContent by remember { mutableStateOf("") }
    var promptNote by remember { mutableStateOf("") }
    var isPromptWorking by remember { mutableStateOf(false) }

    // 3. Playground
    var playgroundQuestion by remember { mutableStateOf("") }
    var playgroundAnswer by remember { mutableStateOf("") }
    var playgroundSources by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var testCases by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var isRunningPlayground by remember { mutableStateOf(false) }
    var playgroundElapsed by remember { mutableIntStateOf(0) }
    var playgroundProviderInfo by remember { mutableStateOf("") }

    // 4. Feedback
    var feedbackItems by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var selectedFeedbackId by remember { mutableStateOf("") }
    var correctedAnswer by remember { mutableStateOf("") }

    val quickQuestions = listOf(
        "Căn 2 phòng ngủ ở dự án X còn không?",
        "Giá và chính sách thanh toán mới nhất là gì?",
        "Dự án có những tiện ích nổi bật nào?"
    )

    fun loadOverview() {
        scope.launch {
            try {
                val o = APIClient.get().request("/chatbot/training/overview")
                overviewData = o["data"]
                val c = APIClient.get().request("/chatbot/config")
                botConfig = c["data"]
            } catch (_: Exception) {}
        }
    }

    fun loadData() {
        scope.launch {
            loading = true
            try {
                when (currentTab) {
                    AITrainingTab.KNOWLEDGE -> {
                        val res = APIClient.get().request("/chatbot/knowledge")
                        documents = res["data"].array
                    }
                    AITrainingTab.PROMPTS -> {
                        val res = APIClient.get().request("/chatbot/prompts")
                        prompts = res["data"].array
                        if (prompts.isNotEmpty()) {
                            val target = prompts.firstOrNull { it.id == selectedPromptId } ?: prompts.first()
                            selectedPromptId = target.id
                            promptContent = target["content"].string
                            promptNote = target["note"].string
                        }
                    }
                    AITrainingTab.PLAYGROUND -> {
                        val res = APIClient.get().request("/chatbot/test-cases")
                        testCases = res["data"].array
                    }
                    AITrainingTab.FEEDBACK -> {
                        val res = APIClient.get().request("/chatbot/feedback")
                        feedbackItems = res["data"].array
                        if (feedbackItems.isNotEmpty()) {
                            val target = feedbackItems.firstOrNull { it.id == selectedFeedbackId } ?: feedbackItems.first()
                            selectedFeedbackId = target.id
                            val c = target["correctedAnswer"].string
                            correctedAnswer = if (c.isNotEmpty()) c else target["answer"].string
                        }
                    }
                }
                loadOverview()
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(currentTab) {
        loadData()
    }

    val isBotEnabled = botConfig["isEnabled"].bool
    val readyCount = overviewData["knowledge"]["ready"].int.takeIf { it > 0 } ?: documents.size
    val testCount = overviewData["testCases"].int
    val posRate = overviewData["positiveFeedbackRate"].int
    val pendingFeed = overviewData["pendingFeedback"].int
    val activeProvidersCount = botConfig["providers"].array.filter { it["isEnabled"].bool }.size

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Surface(
                color = FutaColors.PageBg,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
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
                        text = "Training AI",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    FutaHeaderIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Cấu hình Provider",
                        tint = FutaColors.BrandGreen,
                        onClick = { showSettings = true }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Unified Status & Overview Stats Card (Matching iOS & Web)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isBotEnabled) FutaColors.BrandGreen else Color(0xFFF97316),
                                    modifier = Modifier.size(7.dp)
                                ) {}
                                Text(
                                    text = if (isBotEnabled) "Bot đang hoạt động" else "Bot tạm dừng",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBotEnabled) FutaColors.BrandGreen else Color(0xFFF97316)
                                )
                            }
                            Text("$activeProvidersCount provider đang bật", fontSize = 11.5.sp, color = FutaColors.Slate)
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatColumn(label = "Đã index", value = "$readyCount")
                            StatColumn(label = "Test case", value = "$testCount")
                            StatColumn(label = "Hài lòng", value = if (posRate > 0) "$posRate%" else "—")
                            StatColumn(label = "Cần xử lý", value = "$pendingFeed", isAccent = pendingFeed > 0)
                        }
                    }
                }
            }

            // 2. Navigation Tabs (Matching iOS Segmented Control)
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        AITrainingTab.values().forEach { t ->
                            val isSel = currentTab == t
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color.White else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { currentTab = t }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 7.dp)) {
                                    Text(
                                        text = t.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) FutaColors.Navy else FutaColors.Slate
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Tab Body
            when (currentTab) {
                AITrainingTab.KNOWLEDGE -> {
                    // Full width search
                    item {
                        FutaInput(
                            value = knowledgeSearch,
                            onValueChange = { knowledgeSearch = it },
                            placeholder = "Tìm tài liệu, dự án...",
                            leadingIcon = Icons.Default.Search,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Action Buttons Row (Matching iOS Button Structure)
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                FutaButton(
                                    text = "Nạp tài liệu mới",
                                    icon = Icons.Default.UploadFile,
                                    variant = FutaButtonVariant.PRIMARY,
                                    onClick = { showUploadDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FutaButton(
                                    text = if (isReindexingAll) "Đang index..." else "Đánh chỉ mục cũ",
                                    icon = Icons.Default.Refresh,
                                    variant = FutaButtonVariant.OUTLINE,
                                    enabled = !isReindexingAll,
                                    onClick = {
                                        scope.launch {
                                            isReindexingAll = true
                                            try {
                                                APIClient.get().request("/chatbot/knowledge/reindex-outdated", method = "POST")
                                                ToastCenter.show("Đã index lại toàn bộ tài liệu!")
                                                loadData()
                                            } catch (e: Exception) {
                                                ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                            } finally {
                                                isReindexingAll = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Document Cards List
                    val filtered = documents.filter {
                        val q = knowledgeSearch.trim().lowercase()
                        q.isEmpty() || it["name"].string.lowercase().contains(q) || it["project"].string.lowercase().contains(q)
                    }

                    if (loading && documents.isEmpty()) {
                        items(4) { FutaAdminRowSkeleton() }
                    } else if (filtered.isEmpty()) {
                        item {
                            FutaEmptyState(title = "Chưa có tài liệu", message = "Nạp tài liệu mới để xây dựng kho RAG.")
                        }
                    } else {
                        itemsIndexed(filtered, key = { idx, d -> d.id.ifEmpty { "doc-$idx" } }) { _, doc ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                shadowElevation = 1.dp,
                                border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFEAF5EF), modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Description, contentDescription = null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(doc["name"].string.ifEmpty { doc["title"].string }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            val st = doc["status"].string
                                            Surface(shape = CircleShape, color = if (st == "ready") Color(0xFFEAF5EF) else Color(0xFFFFF7ED)) {
                                                Text(
                                                    if (st == "ready") "Đã index" else if (st == "processing") "Đang xử lý" else "Cần cập nhật",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (st == "ready") FutaColors.BrandGreen else Color(0xFFF97316),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            val chunks = doc["chunkCount"].int
                                            if (chunks > 0) {
                                                Text("$chunks chunks", fontSize = 11.sp, color = FutaColors.Slate)
                                            }
                                            val proj = doc["project"].string
                                            if (proj.isNotEmpty()) {
                                                Text("• $proj", fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                try {
                                                    APIClient.get().request("/chatbot/knowledge/${doc.id}/reindex", method = "POST")
                                                    ToastCenter.show("Đã index lại tài liệu!")
                                                    loadData()
                                                } catch (_: Exception) {}
                                            }
                                        }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Index lại", tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                try {
                                                    APIClient.get().request("/chatbot/knowledge/${doc.id}", method = "DELETE")
                                                    ToastCenter.show("Đã xóa tài liệu!")
                                                    loadData()
                                                } catch (_: Exception) {}
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AITrainingTab.PROMPTS -> {
                    // Version horizontal selector
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            prompts.forEach { p ->
                                val isSel = p.id == selectedPromptId
                                val isPub = p["status"].string == "published"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) FutaColors.BrandGreen else Color.White,
                                    border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                    modifier = Modifier.clickable {
                                        selectedPromptId = p.id
                                        promptContent = p["content"].string
                                        promptNote = p["note"].string
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("v${p["version"].int}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else FutaColors.Navy)
                                        if (isPub) {
                                            Surface(shape = CircleShape, color = if (isSel) Color.White.copy(alpha = 0.25f) else Color(0xFFEAF5EF)) {
                                                Text("Active", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Prompt Editor Card
                    item {
                        val cur = prompts.firstOrNull { it.id == selectedPromptId }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Phiên bản v${cur?.get("version")?.int ?: 1} (${cur?.get("status")?.string ?: "published"})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                    if (cur?.get("status")?.string != "published" && cur != null) {
                                        FutaButton(
                                            text = "Rollback & Publish",
                                            variant = FutaButtonVariant.OUTLINE,
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        APIClient.get().request("/chatbot/prompts/${cur.id}/rollback", method = "POST")
                                                        ToastCenter.show("Đã rollback về phiên bản này!")
                                                        loadData()
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        )
                                    }
                                }

                                FutaInput(
                                    value = promptNote,
                                    onValueChange = { promptNote = it },
                                    placeholder = "Ghi chú thay đổi (note)...",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = promptContent,
                                    onValueChange = { promptContent = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = FutaColors.BrandGreen,
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        FutaButton(
                                            text = "Lưu nháp (Draft)",
                                            variant = FutaButtonVariant.OUTLINE,
                                            enabled = !isPromptWorking && promptContent.isNotBlank(),
                                            onClick = {
                                                scope.launch {
                                                    isPromptWorking = true
                                                    try {
                                                        val cleanContent = promptContent.replace("\"", "\\\"").replace("\n", "\\n")
                                                        val cleanNote = promptNote.replace("\"", "\\\"")
                                                        val body = """{"content":"$cleanContent","note":"$cleanNote"}"""
                                                        APIClient.get().request("/chatbot/prompts", method = "POST", bodyJson = body)
                                                        ToastCenter.show("Đã lưu nháp phiên bản mới!")
                                                        loadData()
                                                    } catch (_: Exception) {}
                                                    finally { isPromptWorking = false }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        FutaButton(
                                            text = "Publish",
                                            variant = FutaButtonVariant.PRIMARY,
                                            enabled = !isPromptWorking && cur != null,
                                            onClick = {
                                                scope.launch {
                                                    isPromptWorking = true
                                                    try {
                                                        APIClient.get().request("/chatbot/prompts/${cur!!.id}/publish", method = "POST")
                                                        ToastCenter.show("Đã publish prompt thành công!")
                                                        loadData()
                                                    } catch (_: Exception) {}
                                                    finally { isPromptWorking = false }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AITrainingTab.PLAYGROUND -> {
                    // Quick chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Câu hỏi kiểm thử nhanh", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickQuestions.forEach { q ->
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.clickable { playgroundQuestion = q }
                                    ) {
                                        Text(q, fontSize = 11.sp, color = FutaColors.Navy, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Question Input & Run Button
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = playgroundQuestion,
                                    onValueChange = { playgroundQuestion = it },
                                    placeholder = { Text("Nhập câu hỏi kiểm thử trợ lý AI...", fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = FutaColors.BrandGreen,
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    FutaButton(
                                        text = "Lưu Test Case",
                                        variant = FutaButtonVariant.OUTLINE,
                                        enabled = playgroundQuestion.isNotBlank(),
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val body = """{"question":"${playgroundQuestion.trim().replace("\"", "\\\"")}"}"""
                                                    APIClient.get().request("/chatbot/test-cases", method = "POST", bodyJson = body)
                                                    ToastCenter.show("Đã lưu thành Test Case!")
                                                    loadData()
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    )

                                    FutaButton(
                                        text = if (isRunningPlayground) "Đang chạy..." else "Chạy thử",
                                        variant = FutaButtonVariant.PRIMARY,
                                        enabled = !isRunningPlayground && playgroundQuestion.isNotBlank(),
                                        onClick = {
                                            scope.launch {
                                                isRunningPlayground = true
                                                playgroundAnswer = ""
                                                playgroundSources = emptyList()
                                                try {
                                                    val start = System.currentTimeMillis()
                                                    val cleanQ = playgroundQuestion.trim().replace("\"", "\\\"").replace("\n", "\\n")
                                                    val body = """{"messages":[{"role":"user","content":"$cleanQ"}]}"""
                                                    val res = APIClient.get().request("/chatbot/playground", method = "POST", bodyJson = body)
                                                    playgroundElapsed = (System.currentTimeMillis() - start).toInt()
                                                    val data = res["data"]
                                                    playgroundAnswer = data["reply"].string.ifEmpty { data["answer"].string.ifEmpty { "Không nhận được phản hồi." } }
                                                    playgroundProviderInfo = "${data["provider"].string} · ${data["model"].string}"
                                                    playgroundSources = data["sources"].array
                                                } catch (e: Exception) {
                                                    playgroundAnswer = "Lỗi: ${e.message}"
                                                } finally {
                                                    isRunningPlayground = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Response Card with RAG Sources
                    if (playgroundAnswer.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Kết quả phản hồi", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                        if (playgroundElapsed > 0) {
                                            Text("$playgroundProviderInfo · ${playgroundElapsed}ms", fontSize = 11.sp, color = FutaColors.Slate)
                                        }
                                    }
                                    Text(playgroundAnswer, fontSize = 13.sp, color = FutaColors.Navy)

                                    if (playgroundSources.isNotEmpty()) {
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                        Text("Context RAG trích xuất (${playgroundSources.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                                        playgroundSources.forEach { s ->
                                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                                                Text(s["content"].string.ifEmpty { s["text"].string }, fontSize = 11.sp, color = FutaColors.Slate, modifier = Modifier.padding(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Saved Test Cases List
                    if (testCases.isNotEmpty()) {
                        item {
                            Text("Danh sách Test Cases đã lưu (${testCases.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        }
                        itemsIndexed(testCases) { _, tc ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(tc["question"].string, fontSize = 12.5.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        playgroundQuestion = tc["question"].string
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Dùng câu hỏi này", tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                AITrainingTab.FEEDBACK -> {
                    if (feedbackItems.isEmpty()) {
                        item {
                            FutaEmptyState(
                                title = "Chưa có feedback cần xử lý",
                                message = "Feedback sẽ được tự động ghi nhận từ phản hồi đánh giá của khách hàng."
                            )
                        }
                    } else {
                        // Horizontal selector
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                feedbackItems.forEach { fb ->
                                    val isSel = fb.id == selectedFeedbackId
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) FutaColors.BrandGreen else Color.White,
                                        border = BorderStroke(1.dp, if (isSel) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                        modifier = Modifier.clickable {
                                            selectedFeedbackId = fb.id
                                            val c = fb["correctedAnswer"].string
                                            correctedAnswer = if (c.isNotEmpty()) c else fb["answer"].string
                                        }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(fb["question"].string, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(fb["reason"].string.ifEmpty { "Khác" }, fontSize = 10.sp, color = if (isSel) Color.White.copy(alpha = 0.8f) else Color(0xFFF97316))
                                        }
                                    }
                                }
                            }
                        }

                        // Detail & Correction Card
                        val curFb = feedbackItems.firstOrNull { it.id == selectedFeedbackId }
                        if (curFb != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE1D9CB)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Câu hỏi từ khách:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                                        Text(curFb["question"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)

                                        Text("AI đã trả lời:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                                            Text(curFb["answer"].string, fontSize = 12.5.sp, color = FutaColors.Navy, modifier = Modifier.padding(10.dp))
                                        }

                                        Text("Sửa câu trả lời chuẩn (đưa lại vào RAG):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                                        OutlinedTextField(
                                            value = correctedAnswer,
                                            onValueChange = { correctedAnswer = it },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = FutaColors.BrandGreen,
                                                unfocusedBorderColor = Color(0xFFE2E8F0)
                                            )
                                        )

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                FutaButton(
                                                    text = "Đưa vào RAG",
                                                    variant = FutaButtonVariant.PRIMARY,
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                val clean = correctedAnswer.replace("\"", "\\\"").replace("\n", "\\n")
                                                                APIClient.get().request(
                                                                    "/chatbot/feedback/${curFb.id}/add-to-knowledge",
                                                                    method = "POST",
                                                                    bodyJson = """{"correctedAnswer":"$clean"}"""
                                                                )
                                                                ToastCenter.show("Đã đưa câu trả lời chuẩn vào FAQ/RAG!")
                                                                loadData()
                                                            } catch (_: Exception) {}
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Box(modifier = Modifier.weight(1f)) {
                                                FutaButton(
                                                    text = "Chuyển sales",
                                                    variant = FutaButtonVariant.OUTLINE,
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                APIClient.get().request("/chatbot/feedback/${curFb.id}/handoff", method = "POST")
                                                                ToastCenter.show("Đã chuyển sales xử lý!")
                                                                loadData()
                                                            } catch (_: Exception) {}
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
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

    // Provider Settings BottomSheet (Full Web Parity with Fallbacks & Test Connection)
    if (showSettings) {
        AIProviderSettingsSheet(
            config = botConfig,
            onDismiss = { showSettings = false },
            onSaved = {
                showSettings = false
                loadOverview()
            }
        )
    }

    // Simple Upload Text Document Dialog
    if (showUploadDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var isUploading by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Nạp tài liệu mới vào RAG", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutaInput(value = title, onValueChange = { title = it }, placeholder = "Tiêu đề tài liệu *")
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Nhập nội dung tài liệu...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                FutaButton(
                    text = if (isUploading) "Đang nạp..." else "Nạp & Index",
                    enabled = title.isNotBlank() && content.isNotBlank() && !isUploading,
                    onClick = {
                        scope.launch {
                            isUploading = true
                            try {
                                val cleanT = title.trim().replace("\"", "\\\"")
                                val cleanC = content.trim().replace("\"", "\\\"").replace("\n", "\\n")
                                val body = """{"name":"$cleanT","content":"$cleanC","contentType":"project"}"""
                                APIClient.get().request("/chatbot/knowledge", method = "POST", bodyJson = body)
                                ToastCenter.show("Đã nạp và index tài liệu thành công!")
                                showUploadDialog = false
                                loadData()
                            } catch (e: Exception) {
                                ToastCenter.show("Lỗi: ${e.message}", isError = true)
                            } finally {
                                isUploading = false
                            }
                        }
                    }
                )
            },
            dismissButton = {
                FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = { showUploadDialog = false })
            }
        )
    }
}

@Composable
private fun AIProviderSettingsSheet(
    config: JSONValue,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isEnabled by remember { mutableStateOf(config["isEnabled"].bool) }
    var providers by remember {
        mutableStateOf(
            config["providers"].array.mapIndexed { idx, p ->
                mutableStateMapOf<String, Any>(
                    "id" to p["id"].string.ifEmpty { java.util.UUID.randomUUID().toString() },
                    "name" to p["name"].string.ifEmpty { if (idx == 0) "Provider chính" else "Fallback $idx" },
                    "provider" to p["provider"].string.ifEmpty { "openai" },
                    "baseUrl" to p["baseUrl"].string.ifEmpty { "https://api.openai.com/v1" },
                    "model" to p["model"].string.ifEmpty { "gpt-4o" },
                    "isEnabled" to p["isEnabled"].bool,
                    "apiKey" to "",
                    "apiKeyHint" to p["apiKeyHint"].string,
                    "isExpanded" to (idx == 0),
                    "testState" to "idle",
                    "testMessage" to ""
                )
            }.toMutableList()
        )
    }
    var isSaving by remember { mutableStateOf(false) }

    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Cấu hình API & Nhà cung cấp"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Global status toggle card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Kích hoạt trợ lý AI", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Ưu tiên từ trên xuống · ${providers.filter { it["isEnabled"] == true }.size} provider đang bật", fontSize = 11.sp, color = FutaColors.Slate)
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FutaColors.BrandGreen)
                    )
                }
            }

            // Providers List
            Text("Danh sách nhà cung cấp (Thứ tự ưu tiên)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)

            providers.forEachIndexed { index, p ->
                val expanded = p["isExpanded"] as? Boolean ?: false
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, if (expanded) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (index == 0) FutaColors.BrandGreen else Color(0xFFE2E8F0),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        if (index == 0) "P" else "F$index",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (index == 0) Color.White else FutaColors.Navy
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f).clickable { p["isExpanded"] = !expanded }) {
                                Text(p["name"] as? String ?: "Provider", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                Text("${p["provider"]} · ${p["model"]}", fontSize = 11.sp, color = FutaColors.Slate)
                            }

                            val testState = p["testState"] as? String ?: "idle"
                            if (testState == "success") {
                                Text("✓ ${p["testMessage"]}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                            } else if (testState == "error") {
                                Text("Lỗi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                            }

                            Switch(
                                checked = p["isEnabled"] as? Boolean ?: true,
                                onCheckedChange = { p["isEnabled"] = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = FutaColors.BrandGreen)
                            )
                        }

                        // Expanded Details Form
                        if (expanded) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            FutaInput(
                                value = p["name"] as? String ?: "",
                                onValueChange = { p["name"] = it },
                                placeholder = "Tên hiển thị (ví dụ: Provider chính)"
                            )

                            FutaInput(
                                value = p["baseUrl"] as? String ?: "",
                                onValueChange = { p["baseUrl"] = it },
                                placeholder = "Base URL (https://api.openai.com/v1)"
                            )

                            FutaInput(
                                value = p["model"] as? String ?: "",
                                onValueChange = { p["model"] = it },
                                placeholder = "Mã Model (gpt-4o, qwen...)"
                            )

                            val hint = p["apiKeyHint"] as? String ?: ""
                            FutaInput(
                                value = p["apiKey"] as? String ?: "",
                                onValueChange = { p["apiKey"] = it },
                                placeholder = if (hint.isNotEmpty()) "Để trống để giữ key ($hint)" else "Nhập API Key"
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                FutaButton(
                                    text = if (p["testState"] == "testing") "Đang test..." else "Test API",
                                    variant = FutaButtonVariant.OUTLINE,
                                    onClick = {
                                        scope.launch {
                                            p["testState"] = "testing"
                                            try {
                                                val payload = """
                                                    {
                                                        "id": "${p["id"]}",
                                                        "provider": "${p["provider"]}",
                                                        "baseUrl": "${(p["baseUrl"] as String).trim()}",
                                                        "model": "${(p["model"] as String).trim()}"
                                                        ${if ((p["apiKey"] as String).isNotBlank()) ",\"apiKey\":\"${(p["apiKey"] as String).trim()}\"" else ""}
                                                    }
                                                """.trimIndent()
                                                val res = APIClient.get().request("/chatbot/test-connection", method = "POST", bodyJson = payload)
                                                val elapsed = res["data"]["elapsed"].int
                                                p["testState"] = "success"
                                                p["testMessage"] = if (elapsed > 0) "${elapsed}ms" else "OK"
                                                ToastCenter.show("Kết nối API thành công!")
                                            } catch (e: Exception) {
                                                p["testState"] = "error"
                                                ToastCenter.show("Lỗi: ${e.message}", isError = true)
                                            }
                                        }
                                    }
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (index > 0) {
                                        IconButton(onClick = {
                                            val item = providers.removeAt(index)
                                            providers.add(index - 1, item)
                                        }) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Lên", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (index < providers.size - 1) {
                                        IconButton(onClick = {
                                            val item = providers.removeAt(index)
                                            providers.add(index + 1, item)
                                        }) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Xuống", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (providers.size > 1) {
                                        IconButton(onClick = { providers.removeAt(index) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Add Fallback Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, FutaColors.BrandGreen.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val idx = providers.size
                        providers.add(
                            mutableStateMapOf(
                                "id" to java.util.UUID.randomUUID().toString(),
                                "name" to "Fallback $idx",
                                "provider" to "openai-compatible",
                                "baseUrl" to "https://api.openai.com/v1",
                                "model" to "gpt-4o-mini",
                                "isEnabled" to true,
                                "apiKey" to "",
                                "apiKeyHint" to "",
                                "isExpanded" to true,
                                "testState" to "idle",
                                "testMessage" to ""
                            )
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("+ Thêm fallback provider", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                }
            }

            // Save Config Button
            FutaButton(
                text = if (isSaving) "Đang lưu..." else "Lưu cấu hình",
                enabled = !isSaving && providers.isNotEmpty(),
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val provJsonList = providers.map { p ->
                                val key = (p["apiKey"] as? String)?.trim().orEmpty()
                                """
                                    {
                                        "id": "${p["id"]}",
                                        "name": "${(p["name"] as String).trim()}",
                                        "provider": "${p["provider"]}",
                                        "baseUrl": "${(p["baseUrl"] as String).trim()}",
                                        "model": "${(p["model"] as String).trim()}",
                                        "isEnabled": ${p["isEnabled"] == true}
                                        ${if (key.isNotEmpty()) ",\"apiKey\":\"$key\"" else ""}
                                    }
                                """.trimIndent()
                            }
                            val body = """
                                {
                                    "isEnabled": $isEnabled,
                                    "providers": [${provJsonList.joinToString(",")}]
                                }
                            """.trimIndent()
                            APIClient.get().request("/chatbot/config", method = "PUT", bodyJson = body)
                            ToastCenter.show("Đã lưu cấu hình API thành công!")
                            onSaved()
                        } catch (e: Exception) {
                            ToastCenter.show("Lỗi: ${e.message}", isError = true)
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

@Composable
private fun StatColumn(label: String, value: String, isAccent: Bool = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (isAccent) Color(0xFFF97316) else FutaColors.Navy)
        Text(label, fontSize = 10.5.sp, color = FutaColors.Slate)
    }
}
