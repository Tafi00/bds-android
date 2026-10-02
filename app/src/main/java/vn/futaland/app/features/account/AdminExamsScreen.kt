package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.*

// Mirrors iOS Features/AdminPeople/AdminPeopleViews.swift (AdminExamsView):
// exams (GET/POST/PUT/DELETE /advisor/exams), question bank (GET/POST/PUT/DELETE /advisor/questions,
// PATCH /advisor/questions/:id/toggle, POST /advisor/questions/import) and attempts (GET /advisor/exam-attempts).

private sealed interface ExamRoute {
    data class ExamForm(val exam: JSONValue?) : ExamRoute
    data class QuestionForm(val question: JSONValue?) : ExamRoute
    data object Import : ExamRoute
}

private enum class ExamTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EXAMS("Đề thi sát hạch", Icons.Default.Description),
    QUESTIONS("Ngân hàng câu hỏi", Icons.Default.Folder),
    ATTEMPTS("Lịch sử thi", Icons.Default.History)
}

private enum class ExamListSort(val title: String) { NEWEST("Mới nhất"), OLDEST("Cũ nhất"), TITLE("Tên: A → Z") }
private enum class AttemptSort(val title: String) { NEWEST("Mới nhất"), SCORE_DESC("Điểm: Cao → Thấp"), SCORE_ASC("Điểm: Thấp → Cao") }

private val difficultyOptions = listOf("Dễ", "Trung bình", "Khó")

private fun difficultyColor(d: String): Color = when (d) {
    "Dễ" -> Color(0xFF16A34A)
    "Khó" -> Color(0xFFDC2626)
    else -> Color(0xFFF97316)
}

private fun examStatusInfo(status: String): Pair<String, Color> = when (status) {
    "published" -> "Đã xuất bản" to Color(0xFF16A34A)
    "inactive" -> "Tạm dừng" to FutaColors.Slate
    else -> "Bản nháp" to Color(0xFFF97316)
}

private fun targetRoleLabel(role: String): String = when (role) {
    "advisor_trainee" -> "Đại lý (đào tạo)"
    "all" -> "Tất cả"
    else -> "Tư vấn viên"
}

private fun isAttemptPassed(a: JSONValue) = a["status"].string == "passed" || a["examStatus"].string == "passed"

/** Question body for the backend (`advisorQuestionBody`): only the keys it accepts. */
private fun questionBody(q: JSONValue): JsonObject = buildJsonObject {
    put("content", q["content"].string.trim())
    put("answers", JsonArray(q["answers"].array.map { JsonPrimitive(it.string.trim()) }))
    put("correctAnswer", q["correctAnswer"].int)
    put("category", q["category"].string.trim().ifEmpty { "Kiến thức BĐS" })
    put("difficulty", q["difficulty"].string.takeIf { it in difficultyOptions } ?: "Trung bình")
    put("active", if (q["active"].isNull) true else q["active"].bool)
}

@Composable
fun AdminExamsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<ExamRoute>() }
    var tab by remember { mutableStateOf(ExamTab.EXAMS) }
    var exams by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var questions by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var attempts by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun fetch() {
        try {
            val api = APIClient.get()
            val ex = scope.async { api.request("/advisor/exams")["data"].array }
            val qs = scope.async { api.request("/advisor/questions")["data"].array }
            val at = scope.async { api.request("/advisor/exam-attempts")["data"].array }
            exams = ex.await()
            questions = qs.await()
            attempts = at.await()
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        } finally {
            loading = false
        }
    }

    fun reload() { scope.launch { fetch() } }

    LaunchedEffect(Unit) { fetch() }

    ScreenStackHost(
        stack = stack,
        base = {
            ExamsListContent(
                tab = tab,
                onTab = { tab = it },
                exams = exams,
                questions = questions,
                attempts = attempts,
                loading = loading,
                loadError = loadError,
                onBack = onBack,
                onRetry = { loading = true; reload() },
                onOpenExam = { stack.push(ExamRoute.ExamForm(it)) },
                onOpenQuestion = { stack.push(ExamRoute.QuestionForm(it)) },
                onCreate = { stack.push(if (tab == ExamTab.QUESTIONS) ExamRoute.QuestionForm(null) else ExamRoute.ExamForm(null)) },
                onImport = { stack.push(ExamRoute.Import) },
                onToggled = { reload() }
            )
        }
    ) { route ->
        when (route) {
            is ExamRoute.ExamForm -> ExamFormScreen(
                exam = route.exam,
                questions = questions,
                onClose = { stack.pop() },
                onSaved = { stack.pop(); reload() }
            )
            is ExamRoute.QuestionForm -> QuestionFormScreen(
                question = route.question,
                onClose = { stack.pop() },
                onSaved = { stack.pop(); reload() }
            )
            ExamRoute.Import -> QuestionImportScreen(onClose = { stack.pop() }, onImported = { stack.pop(); reload() })
        }
    }
}

// ============================================================================
// Listings
// ============================================================================

@Composable
private fun ExamsListContent(
    tab: ExamTab,
    onTab: (ExamTab) -> Unit,
    exams: List<JSONValue>,
    questions: List<JSONValue>,
    attempts: List<JSONValue>,
    loading: Boolean,
    loadError: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenExam: (JSONValue) -> Unit,
    onOpenQuestion: (JSONValue) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onToggled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var search by remember(tab) { mutableStateOf("") }
    var page by remember(tab) { mutableIntStateOf(1) }
    var showFilter by remember { mutableStateOf(false) }

    // Exams
    var examSort by remember { mutableStateOf(ExamListSort.NEWEST) }
    var examStatus by remember { mutableStateOf("all") }
    var examDifficulty by remember { mutableStateOf("all") }
    // Questions
    var questionSort by remember { mutableStateOf(ExamListSort.NEWEST) }
    var questionActive by remember { mutableStateOf("all") }
    var questionDifficulty by remember { mutableStateOf("all") }
    var questionCategory by remember { mutableStateOf("all") }
    // Attempts
    var attemptSort by remember { mutableStateOf(AttemptSort.NEWEST) }
    var attemptResult by remember { mutableStateOf("all") }
    var attemptExam by remember { mutableStateOf("all") }

    // Draft filter values edited inside the sheet, applied on "Áp dụng".
    var draft by remember { mutableStateOf(listOf<String>()) }

    var toggleTarget by remember { mutableStateOf<JSONValue?>(null) }
    var toggling by remember { mutableStateOf(false) }

    val q = search.trim()
    val filteredExams = remember(exams, q, examSort, examStatus, examDifficulty) {
        exams.filter { e ->
            (q.isEmpty() || "${e["title"].string} ${e["description"].string}".contains(q, ignoreCase = true)) &&
                (examStatus == "all" || e["status"].string.ifEmpty { "draft" } == examStatus) &&
                (examDifficulty == "all" || e["difficulty"].string == examDifficulty)
        }.let { list ->
            when (examSort) {
                ExamListSort.NEWEST -> list.sortedByDescending { it["createdAt"].string }
                ExamListSort.OLDEST -> list.sortedBy { it["createdAt"].string }
                ExamListSort.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["title"].string })
            }
        }
    }
    val categories = remember(questions) { questions.map { it["category"].string }.filter { it.isNotBlank() }.distinct().sorted() }
    val filteredQuestions = remember(questions, q, questionSort, questionActive, questionDifficulty, questionCategory) {
        questions.filter { item ->
            (q.isEmpty() || "${item["content"].string} ${item["category"].string}".contains(q, ignoreCase = true)) &&
                (questionActive == "all" || (questionActive == "active") == item["active"].bool) &&
                (questionDifficulty == "all" || item["difficulty"].string == questionDifficulty) &&
                (questionCategory == "all" || item["category"].string == questionCategory)
        }.let { list ->
            when (questionSort) {
                ExamListSort.NEWEST -> list.sortedByDescending { it["createdAt"].string }
                ExamListSort.OLDEST -> list.sortedBy { it["createdAt"].string }
                ExamListSort.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it["content"].string })
            }
        }
    }
    val examTitles = remember(attempts) { attempts.map { it["exam"]["title"].string }.filter { it.isNotBlank() }.distinct().sorted() }
    val filteredAttempts = remember(attempts, q, attemptSort, attemptResult, attemptExam) {
        attempts.filter { a ->
            (q.isEmpty() || listOf(a["user"]["name"].string, a["user"]["phone"].string, a["user"]["email"].string, a["exam"]["title"].string)
                .joinToString(" ").contains(q, ignoreCase = true)) &&
                (attemptResult == "all" || (attemptResult == "passed") == isAttemptPassed(a)) &&
                (attemptExam == "all" || a["exam"]["title"].string == attemptExam)
        }.let { list ->
            when (attemptSort) {
                AttemptSort.NEWEST -> list.sortedByDescending { it["createdAt"].string }
                AttemptSort.SCORE_DESC -> list.sortedByDescending { it["score"].double }
                AttemptSort.SCORE_ASC -> list.sortedBy { it["score"].double }
            }
        }
    }

    val activeFilterCount = when (tab) {
        ExamTab.EXAMS -> listOf(examStatus, examDifficulty).count { it != "all" }
        ExamTab.QUESTIONS -> listOf(questionActive, questionDifficulty, questionCategory).count { it != "all" }
        ExamTab.ATTEMPTS -> listOf(attemptResult, attemptExam).count { it != "all" }
    }
    fun clearFilters() {
        search = ""
        page = 1
        when (tab) {
            ExamTab.EXAMS -> { examStatus = "all"; examDifficulty = "all" }
            ExamTab.QUESTIONS -> { questionActive = "all"; questionDifficulty = "all"; questionCategory = "all" }
            ExamTab.ATTEMPTS -> { attemptResult = "all"; attemptExam = "all" }
        }
    }
    fun openFilter() {
        draft = when (tab) {
            ExamTab.EXAMS -> listOf(examStatus, examDifficulty)
            ExamTab.QUESTIONS -> listOf(questionActive, questionDifficulty, questionCategory)
            ExamTab.ATTEMPTS -> listOf(attemptResult, attemptExam)
        }
        showFilter = true
    }

    val statusOptions = listOf(SelectOption("all", "Tất cả"), SelectOption("draft", "Bản nháp"), SelectOption("published", "Đã xuất bản"), SelectOption("inactive", "Tạm dừng"))
    val diffOptions = listOf(SelectOption("all", "Tất cả")) + difficultyOptions.map { SelectOption(it, it) }
    val activeOptions = listOf(SelectOption("all", "Tất cả"), SelectOption("active", "Đang kích hoạt"), SelectOption("inactive", "Đã tắt"))
    val resultOptions = listOf(SelectOption("all", "Tất cả"), SelectOption("passed", "Đạt"), SelectOption("failed", "Không đạt"))

    val applied = buildList {
        when (tab) {
            ExamTab.EXAMS -> {
                if (examStatus != "all") add(AppliedFilter(tr(statusOptions.first { it.value == examStatus }.label)) { examStatus = "all"; page = 1 })
                if (examDifficulty != "all") add(AppliedFilter(tr(examDifficulty)) { examDifficulty = "all"; page = 1 })
            }
            ExamTab.QUESTIONS -> {
                if (questionActive != "all") add(AppliedFilter(tr(activeOptions.first { it.value == questionActive }.label)) { questionActive = "all"; page = 1 })
                if (questionDifficulty != "all") add(AppliedFilter(tr(questionDifficulty)) { questionDifficulty = "all"; page = 1 })
                if (questionCategory != "all") add(AppliedFilter(questionCategory) { questionCategory = "all"; page = 1 })
            }
            ExamTab.ATTEMPTS -> {
                if (attemptResult != "all") add(AppliedFilter(tr(resultOptions.first { it.value == attemptResult }.label)) { attemptResult = "all"; page = 1 })
                if (attemptExam != "all") add(AppliedFilter(attemptExam) { attemptExam = "all"; page = 1 })
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = "Đề thi sát hạch", onBack = onBack) {
                when (tab) {
                    ExamTab.EXAMS -> SortMenuButton(ExamListSort.entries, examSort, { it.title }, { examSort = it; page = 1 }, examSort == ExamListSort.NEWEST)
                    ExamTab.QUESTIONS -> SortMenuButton(ExamListSort.entries, questionSort, { it.title }, { questionSort = it; page = 1 }, questionSort == ExamListSort.NEWEST)
                    ExamTab.ATTEMPTS -> SortMenuButton(AttemptSort.entries, attemptSort, { it.title }, { attemptSort = it; page = 1 }, attemptSort == AttemptSort.NEWEST)
                }
                BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), activeFilterCount) { openFilter() }
                if (tab != ExamTab.ATTEMPTS) AddHeaderButton(if (tab == ExamTab.EXAMS) tr("Tạo đề thi") else tr("Thêm câu hỏi"), onCreate)
            }
        }
    ) { padding ->
        when {
            loading && exams.isEmpty() && questions.isEmpty() -> AdminListSkeleton(modifier = Modifier.padding(padding))
            loadError != null && exams.isEmpty() && questions.isEmpty() && attempts.isEmpty() -> AdminErrorState(loadError, onRetry, Modifier.padding(padding))
            else -> {
                val pageSize = 20
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        QuickChipRow {
                            ExamTab.entries.forEach { t -> QuickChip(t.title, t == tab, t.icon) { if (t != tab) onTab(t) } }
                        }
                    }
                    item {
                        AdminSearchField(
                            search, { search = it; page = 1 },
                            when (tab) {
                                ExamTab.EXAMS -> "Tìm theo tên đề thi…"
                                ExamTab.QUESTIONS -> "Tìm theo nội dung, chuyên mục…"
                                ExamTab.ATTEMPTS -> "Tìm theo tên, SĐT, đề thi…"
                            }
                        )
                    }
                    if (tab == ExamTab.QUESTIONS) {
                        item {
                            FutaButton(text = "Nhập câu hỏi hàng loạt (Import)", icon = Icons.Default.Download, variant = FutaButtonVariant.MINT, onClick = onImport, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (applied.isNotEmpty()) item { AppliedFilterChips(applied) { clearFilters() } }

                    when (tab) {
                        ExamTab.EXAMS -> {
                            val slice = filteredExams.pageSlice(page, pageSize)
                            if (slice.items.isEmpty()) item {
                                AdminListEmpty(exams.isNotEmpty(), "Chưa có đề thi", "Bấm nút + để tạo đề thi mới.", { clearFilters() })
                            }
                            items(slice.items, key = { "exam-" + it.id }) { exam -> ExamRow(exam, questions.size) { onOpenExam(exam) } }
                            if (slice.totalPages > 1) item {
                                PaginationBar(slice.page, slice.totalPages, tr("{0}–{1} / {2} đề thi", slice.start, slice.end, slice.total),
                                    { page = slice.page - 1; scope.launch { listState.scrollToItem(0) } }, { page = slice.page + 1; scope.launch { listState.scrollToItem(0) } })
                            }
                        }
                        ExamTab.QUESTIONS -> {
                            val slice = filteredQuestions.pageSlice(page, pageSize)
                            if (slice.items.isEmpty()) item {
                                AdminListEmpty(questions.isNotEmpty(), "Chưa có câu hỏi", "Bấm thêm câu hỏi để tạo ngân hàng đề thi.", { clearFilters() })
                            }
                            items(slice.items, key = { "q-" + it.id }) { question ->
                                QuestionRow(question, onToggle = { toggleTarget = question }) { onOpenQuestion(question) }
                            }
                            if (slice.totalPages > 1) item {
                                PaginationBar(slice.page, slice.totalPages, tr("{0}–{1} / {2} câu hỏi", slice.start, slice.end, slice.total),
                                    { page = slice.page - 1; scope.launch { listState.scrollToItem(0) } }, { page = slice.page + 1; scope.launch { listState.scrollToItem(0) } })
                            }
                        }
                        ExamTab.ATTEMPTS -> {
                            val slice = filteredAttempts.pageSlice(page, pageSize)
                            if (slice.items.isEmpty()) item {
                                AdminListEmpty(attempts.isNotEmpty(), "Chưa có lượt thi", "Chưa có tư vấn viên nào tham gia thi sát hạch.", { clearFilters() })
                            }
                            items(slice.items, key = { "a-" + it.id }) { attempt -> AttemptRow(attempt) }
                            if (slice.totalPages > 1) item {
                                PaginationBar(slice.page, slice.totalPages, tr("{0}–{1} / {2} lượt thi", slice.start, slice.end, slice.total),
                                    { page = slice.page - 1; scope.launch { listState.scrollToItem(0) } }, { page = slice.page + 1; scope.launch { listState.scrollToItem(0) } })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    FilterSheet(
        visible = showFilter,
        title = "Bộ lọc",
        applyTitle = "Áp dụng",
        canReset = draft.any { it != "all" },
        onReset = { draft = draft.map { "all" } },
        onApply = {
            when (tab) {
                ExamTab.EXAMS -> { examStatus = draft[0]; examDifficulty = draft[1] }
                ExamTab.QUESTIONS -> { questionActive = draft[0]; questionDifficulty = draft[1]; questionCategory = draft[2] }
                ExamTab.ATTEMPTS -> { attemptResult = draft[0]; attemptExam = draft[1] }
            }
            page = 1
            showFilter = false
        },
        onDismiss = { showFilter = false }
    ) {
        fun set(i: Int, v: String) { draft = draft.toMutableList().also { it[i] = v } }
        if (draft.isNotEmpty()) when (tab) {
            ExamTab.EXAMS -> {
                FilterChipGroup("Trạng thái", statusOptions, draft[0]) { set(0, it) }
                FilterChipGroup("Độ khó", diffOptions, draft[1]) { set(1, it) }
            }
            ExamTab.QUESTIONS -> {
                FilterChipGroup("Trạng thái", activeOptions, draft[0]) { set(0, it) }
                FilterChipGroup("Độ khó", diffOptions, draft[1]) { set(1, it) }
                if (categories.isNotEmpty()) {
                    AdminSelectField("Chuyên mục", draft[2], listOf(SelectOption("all", "Tất cả")) + categories.map { SelectOption(it, it) }, { set(2, it) })
                }
            }
            ExamTab.ATTEMPTS -> {
                FilterChipGroup("Kết quả", resultOptions, draft[0]) { set(0, it) }
                if (examTitles.isNotEmpty()) {
                    AdminSelectField("Đề thi", draft[1], listOf(SelectOption("all", "Tất cả")) + examTitles.map { SelectOption(it, it) }, { set(1, it) })
                }
            }
        }
    }

    val target = toggleTarget
    ConfirmDialog(
        visible = target != null,
        title = if (target?.get("active")?.bool == true) "Tắt câu hỏi?" else "Kích hoạt câu hỏi?",
        message = if (target?.get("active")?.bool == true) "Câu hỏi sẽ không còn xuất hiện trong các lượt thi mới." else "Câu hỏi sẽ được dùng trong các lượt thi mới.",
        confirmText = if (target?.get("active")?.bool == true) "Tắt" else "Kích hoạt",
        destructive = target?.get("active")?.bool == true,
        onDismiss = { toggleTarget = null },
        onConfirm = {
            val t = target ?: return@ConfirmDialog
            if (toggling) return@ConfirmDialog
            toggleTarget = null
            scope.launch {
                toggling = true
                try {
                    // OkHttp needs a body for PATCH.
                    APIClient.get().request("/advisor/questions/${t.id}/toggle", method = "PATCH", bodyJson = "{}")
                    ToastCenter.show(tr("Đã cập nhật trạng thái câu hỏi"))
                    onToggled()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể cập nhật câu hỏi"), isError = true)
                } finally {
                    toggling = false
                }
            }
        }
    )
}

@Composable
private fun ExamRow(exam: JSONValue, totalQuestions: Int, onClick: () -> Unit) {
    val (statusTitle, statusColor) = examStatusInfo(exam["status"].string)
    val difficulty = exam["difficulty"].string.ifEmpty { "Trung bình" }
    AdminRowCard(onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerbatimText(exam["title"].string, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            StatusPill(statusTitle, statusColor)
        }
        Text(
            tr(
                "Điểm đạt: {0}% • Thời gian: {1} phút • Độ khó: {2}",
                exam["passingScore"].int.takeIf { it > 0 } ?: 85,
                exam["duration"].int.takeIf { it > 0 } ?: 30,
                tr(difficulty)
            ),
            fontSize = 12.sp, color = FutaColors.Slate
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(tr("{0} câu hỏi", exam["questionIds"].array.size), Color(0xFF2563EB))
            StatusPill(targetRoleLabel(exam["targetRole"].string), FutaColors.BrandGreen)
        }
    }
}

@Composable
private fun QuestionRow(q: JSONValue, onToggle: () -> Unit, onClick: () -> Unit) {
    val active = q["active"].bool
    val difficulty = q["difficulty"].string.ifEmpty { "Trung bình" }
    AdminRowCard(onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerbatimText(q["content"].string, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            FutaSwitch(checked = active, onCheckedChange = { onToggle() }, activeColor = FutaColors.BrandGreen)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(q["category"].string.ifEmpty { tr("Chung") }, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1)
            Text("•", fontSize = 12.sp, color = FutaColors.Slate)
            Text(difficulty, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = difficultyColor(difficulty))
            Text("•", fontSize = 12.sp, color = FutaColors.Slate)
            Text(tr("{0} đáp án", q["answers"].array.size), fontSize = 12.sp, color = FutaColors.Slate)
            Spacer(Modifier.weight(1f))
            if (!active) StatusPill("Đã tắt", FutaColors.Slate)
        }
    }
}

@Composable
private fun AttemptRow(a: JSONValue) {
    val passed = isAttemptPassed(a)
    val correct = a["correctAnswers"].int.takeIf { it > 0 } ?: a["correct"].int
    val total = a["totalQuestions"].int.takeIf { it > 0 } ?: a["total"].int
    val examTitle = a["exam"]["title"].string.ifEmpty { a["examTitle"].string }
    val userName = a["user"]["name"].string.ifEmpty { a["userName"].string }
    AdminRowCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (userName.isNotEmpty()) VerbatimText(userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            else Text("Tư vấn viên", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            StatusPill(if (passed) "ĐẠT" else "KHÔNG ĐẠT", if (passed) Color(0xFF16A34A) else Color(0xFFDC2626))
        }
        if (examTitle.isNotEmpty()) Text(tr("Đề thi: {0}", examTitle), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
        Text(tr("Điểm số: {0}% ({1}/{2} câu đúng)", a["score"].double.toInt(), correct, total), fontSize = 13.5.sp, color = FutaColors.Navy)
        Row {
            Text(tr("Thời gian làm bài: {0} giây", a["durationSeconds"].int), fontSize = 12.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
            Text(SalesFormatters.dateTime(a["createdAt"].string), fontSize = 12.sp, color = FutaColors.Slate)
        }
    }
}

// ============================================================================
// Exam create / edit
// ============================================================================

@Composable
private fun ExamFormScreen(exam: JSONValue?, questions: List<JSONValue>, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isEdit = exam != null
    val init = remember {
        mapOf(
            "title" to exam?.get("title")?.string.orEmpty(),
            "description" to exam?.get("description")?.string.orEmpty(),
            "passingScore" to "${exam?.get("passingScore")?.int?.takeIf { it > 0 } ?: 85}",
            "duration" to "${exam?.get("duration")?.int?.takeIf { it > 0 } ?: 30}",
            "difficulty" to (exam?.get("difficulty")?.string?.takeIf { it in difficultyOptions } ?: "Trung bình"),
            "status" to (exam?.get("status")?.string?.ifEmpty { null } ?: "draft"),
            "targetRole" to (exam?.get("targetRole")?.string?.ifEmpty { null } ?: "sale"),
            "perAttempt" to (exam?.get("questionsPerAttempt")?.takeIf { !it.isNull }?.int?.toString() ?: ""),
            "shuffleQ" to (exam?.get("shuffleQuestions")?.let { if (it.isNull) true else it.bool } ?: true).toString(),
            "shuffleO" to (exam?.get("shuffleOptions")?.let { if (it.isNull) true else it.bool } ?: true).toString()
        )
    }
    val initIds = remember { exam?.get("questionIds")?.array?.map { it.string }?.toSet() ?: emptySet() }
    var title by remember { mutableStateOf(init["title"]!!) }
    var description by remember { mutableStateOf(init["description"]!!) }
    var passingScore by remember { mutableStateOf(init["passingScore"]!!) }
    var duration by remember { mutableStateOf(init["duration"]!!) }
    var difficulty by remember { mutableStateOf(init["difficulty"]!!) }
    var status by remember { mutableStateOf(init["status"]!!) }
    var targetRole by remember { mutableStateOf(init["targetRole"]!!) }
    var perAttempt by remember { mutableStateOf(init["perAttempt"]!!) }
    var shuffleQ by remember { mutableStateOf(init["shuffleQ"] == "true") }
    var shuffleO by remember { mutableStateOf(init["shuffleO"] == "true") }
    var selectedIds by remember { mutableStateOf(initIds) }
    var questionSearch by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPublishConfirm by remember { mutableStateOf(false) }

    val current = mapOf(
        "title" to title, "description" to description, "passingScore" to passingScore, "duration" to duration,
        "difficulty" to difficulty, "status" to status, "targetRole" to targetRole, "perAttempt" to perAttempt,
        "shuffleQ" to shuffleQ.toString(), "shuffleO" to shuffleO.toString()
    )
    val dirty = current != init || selectedIds != initIds
    fun close() { if (dirty && !saving) showDiscard = true else onClose() }
    BackHandler { close() }

    fun validate(): String? {
        val score = passingScore.toIntOrNull()
        val minutes = duration.toIntOrNull()
        val per = perAttempt.toIntOrNull()
        return when {
            title.isBlank() -> tr("Vui lòng nhập tiêu đề đề thi")
            score == null || score !in 1..100 -> tr("Điểm chuẩn đạt phải từ 1 đến 100")
            minutes == null || minutes !in 1..240 -> tr("Thời gian làm bài phải từ 1 đến 240 phút")
            status == "published" && selectedIds.isEmpty() -> tr("Đề thi xuất bản phải có ít nhất một câu hỏi")
            perAttempt.isNotBlank() && (per == null || per < 1) -> tr("Số câu hỏi mỗi lượt thi không hợp lệ")
            per != null && selectedIds.isNotEmpty() && per > selectedIds.size -> tr("Số câu hỏi mỗi lượt thi không được lớn hơn tổng số câu hỏi trong bộ đề")
            else -> null
        }
    }

    fun submit() {
        val body = buildJsonObject {
            put("title", title.trim())
            put("description", description.trim())
            put("passingScore", passingScore.toInt())
            put("duration", duration.toInt())
            put("difficulty", difficulty)
            put("status", status)
            put("targetRole", targetRole)
            put("questionIds", JsonArray(selectedIds.map { JsonPrimitive(it) }))
            perAttempt.toIntOrNull()?.let { put("questionsPerAttempt", it) } ?: put("questionsPerAttempt", kotlinx.serialization.json.JsonNull)
            put("shuffleQuestions", shuffleQ)
            put("shuffleOptions", shuffleO)
        }.toString()
        scope.launch {
            saving = true
            try {
                if (exam != null) APIClient.get().request("/advisor/exams/${exam.id}", method = "PUT", bodyJson = body)
                else APIClient.get().request("/advisor/exams", method = "POST", bodyJson = body)
                ToastCenter.show(if (isEdit) tr("Đã cập nhật đề thi") else tr("Đã tạo đề thi"))
                onSaved()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu đề thi"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    fun save() {
        error = validate()
        if (error != null) return
        // Publishing retires the previously published exam for the same audience: confirm first.
        if (status == "published" && init["status"] != "published") showPublishConfirm = true else submit()
    }

    val (statusTitle, statusColor) = examStatusInfo(status)
    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(
                title = if (isEdit) "Sửa đề thi" else "Tạo đề thi",
                subtitle = if (isEdit) tr("Cập nhật {0}", SalesFormatters.dateTime(exam!!["updatedAt"].string)) else null,
                onBack = { close() }
            ) { if (isEdit) StatusPill(statusTitle, statusColor) }
        },
        bottomBar = { FormActionBar(if (isEdit) "Lưu thay đổi" else "Tạo đề thi", saving, enabled = !deleting && (dirty || !isEdit), onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            FormSection("Thông tin đề thi") {
                FormTextField("Tiêu đề đề thi", title, { title = it }, required = true)
                FormTextField("Mô tả đề thi", description, { description = it }, multiline = true)
                AdminSelectField("Độ khó", difficulty, difficultyOptions.map { SelectOption(it, it) }, { difficulty = it })
                AdminSelectField(
                    "Trạng thái", status,
                    listOf(SelectOption("draft", "Bản nháp (draft)"), SelectOption("published", "Xuất bản (published)"), SelectOption("inactive", "Ngưng hoạt động (inactive)")),
                    { status = it }
                )
                AdminSelectField(
                    "Đối tượng dự thi", targetRole,
                    listOf(SelectOption("sale", "Tư vấn viên"), SelectOption("advisor_trainee", "Đại lý (đào tạo)"), SelectOption("all", "Tất cả")),
                    { targetRole = it }
                )
                FormTextField("Điểm chuẩn đạt (%)", passingScore, { passingScore = it.filter(Char::isDigit).take(3) }, keyboardType = KeyboardType.Number)
                FormTextField("Thời gian làm bài (phút)", duration, { duration = it.filter(Char::isDigit).take(3) }, keyboardType = KeyboardType.Number)
            }
            FormSection("Cách ra đề") {
                FormTextField("Số câu hỏi mỗi lượt thi", perAttempt, { perAttempt = it.filter(Char::isDigit).take(4) }, placeholder = "Dùng toàn bộ câu hỏi", keyboardType = KeyboardType.Number)
                FormToggle("Xáo trộn câu hỏi", shuffleQ, { shuffleQ = it })
                FormToggle("Xáo trộn đáp án", shuffleO, { shuffleO = it })
            }
            FormSection(tr("Chọn câu hỏi ({0}/{1})", selectedIds.size, questions.size)) {
                AdminSearchField(questionSearch, { questionSearch = it }, "Tìm câu hỏi…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeIds = questions.filter { it["active"].bool }.map { it.id }.toSet()
                    FutaButton(text = "Chọn tất cả câu đang bật", variant = FutaButtonVariant.MINT, height = 36.dp, onClick = { selectedIds = selectedIds + activeIds })
                    if (selectedIds.isNotEmpty()) FutaButton(text = "Bỏ chọn", variant = FutaButtonVariant.OUTLINE, height = 36.dp, onClick = { selectedIds = emptySet() })
                }
                val visible = questions.filter { questionSearch.isBlank() || it["content"].string.contains(questionSearch.trim(), ignoreCase = true) }
                if (questions.isEmpty()) Text("Ngân hàng câu hỏi đang trống.", fontSize = 12.5.sp, color = FutaColors.Slate)
                visible.forEach { q ->
                    val checked = q.id in selectedIds
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { selectedIds = if (checked) selectedIds - q.id else selectedIds + q.id }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            VerbatimText(q["content"].string, fontSize = 13.sp, color = FutaColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${q["category"].string} • ${tr(q["difficulty"].string)}${if (!q["active"].bool) " • " + tr("Đã tắt") else ""}", fontSize = 11.sp, color = FutaColors.Slate)
                        }
                        Icon(
                            if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                            tint = if (checked) FutaColors.BrandGreen else FutaColors.Slate, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (isEdit) {
                InfoCard(exam!!)
                DangerZoneCard(
                    title = "Xóa đề thi",
                    message = "Đề thi sẽ bị xóa vĩnh viễn. Lịch sử các lượt thi vẫn được giữ lại.",
                    buttonText = if (deleting) "Đang xóa…" else "Xóa đề thi",
                    enabled = !deleting && !saving,
                    onClick = { showDelete = true }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
    ConfirmDialog(
        visible = showPublishConfirm,
        title = "Xuất bản đề thi?",
        message = "Đề thi đang xuất bản trước đó cho cùng đối tượng sẽ được chuyển sang tạm dừng.",
        confirmText = "Xuất bản",
        onDismiss = { showPublishConfirm = false },
        onConfirm = { showPublishConfirm = false; submit() }
    )
    ConfirmDialog(
        visible = showDelete,
        title = "Xóa đề thi?",
        message = tr("Bạn có chắc muốn xóa đề thi \"{0}\"?", exam?.get("title")?.string.orEmpty()),
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { showDelete = false },
        onConfirm = {
            showDelete = false
            scope.launch {
                deleting = true
                try {
                    APIClient.get().request("/advisor/exams/${exam!!.id}", method = "DELETE")
                    ToastCenter.show(tr("Đã xóa đề thi"))
                    onSaved()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể xóa đề thi"), isError = true)
                } finally {
                    deleting = false
                }
            }
        }
    )
}

@Composable
private fun InfoCard(item: JSONValue) {
    DetailSection("Thông tin hệ thống", Icons.Default.Info) {
        InfoRow("Mã", item.id, verbatim = true)
        InfoRow("Ngày tạo", SalesFormatters.dateTime(item["createdAt"].string))
        InfoRow("Cập nhật lần cuối", SalesFormatters.dateTime(item["updatedAt"].string))
    }
}

// ============================================================================
// Question create / edit
// ============================================================================

@Composable
private fun QuestionFormScreen(question: JSONValue?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isEdit = question != null
    val initAnswers = remember {
        val list = question?.get("answers")?.array?.map { it.string } ?: emptyList()
        (list + List(maxOf(0, 4 - list.size)) { "" })
    }
    val initContent = question?.get("content")?.string.orEmpty()
    val initCategory = question?.get("category")?.string?.ifEmpty { null } ?: "Kiến thức BĐS"
    val initDifficulty = question?.get("difficulty")?.string?.takeIf { it in difficultyOptions } ?: "Trung bình"
    val initCorrect = question?.get("correctAnswer")?.int ?: 0
    val initActive = question?.get("active")?.let { if (it.isNull) true else it.bool } ?: true

    var content by remember { mutableStateOf(initContent) }
    var category by remember { mutableStateOf(initCategory) }
    var difficulty by remember { mutableStateOf(initDifficulty) }
    var answers by remember { mutableStateOf(initAnswers) }
    var correct by remember { mutableIntStateOf(initCorrect) }
    var active by remember { mutableStateOf(initActive) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val dirty = content != initContent || category != initCategory || difficulty != initDifficulty ||
        answers != initAnswers || correct != initCorrect || active != initActive
    fun close() { if (dirty && !saving) showDiscard = true else onClose() }
    BackHandler { close() }

    fun save() {
        // Filled answers in order; the correct index follows its answer.
        val filled = answers.withIndex().filter { it.value.isNotBlank() }
        val correctIndex = filled.indexOfFirst { it.index == correct }
        error = when {
            content.isBlank() -> tr("Vui lòng nhập nội dung câu hỏi")
            category.isBlank() -> tr("Vui lòng nhập chuyên mục")
            filled.size < 2 -> tr("Cần tối thiểu 2 đáp án")
            correctIndex < 0 -> tr("Đáp án đúng phải là một đáp án đã nhập")
            else -> null
        }
        if (error != null) return
        val payload = JSONValue.EmptyObject.withUpdates(
            mapOf(
                "content" to content,
                "category" to category,
                "difficulty" to difficulty,
                "answers" to JSONValue(JsonArray(filled.map { JsonPrimitive(it.value.trim()) })),
                "correctAnswer" to correctIndex,
                "active" to active
            )
        )
        scope.launch {
            saving = true
            try {
                val body = questionBody(payload).toString()
                if (question != null) APIClient.get().request("/advisor/questions/${question.id}", method = "PUT", bodyJson = body)
                else APIClient.get().request("/advisor/questions", method = "POST", bodyJson = body)
                ToastCenter.show(if (isEdit) tr("Đã cập nhật câu hỏi") else tr("Đã thêm câu hỏi"))
                onSaved()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu câu hỏi"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = if (isEdit) "Sửa câu hỏi" else "Thêm câu hỏi", onBack = { close() }) {
                if (isEdit) StatusPill(if (active) "Đang kích hoạt" else "Đã tắt", if (active) Color(0xFF16A34A) else FutaColors.Slate)
            }
        },
        bottomBar = { FormActionBar(if (isEdit) "Lưu thay đổi" else "Thêm câu hỏi", saving, enabled = !deleting && (dirty || !isEdit), onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            FormSection("Nội dung câu hỏi") {
                FormTextField("Nội dung câu hỏi", content, { content = it }, required = true, multiline = true)
                FormTextField("Chuyên mục", category, { category = it }, required = true)
                AdminSelectField("Mức độ khó", difficulty, difficultyOptions.map { SelectOption(it, it) }, { difficulty = it })
                FormToggle("Đang kích hoạt", active, { active = it })
            }
            FormSection("Các đáp án trắc nghiệm (Tối thiểu 2 đáp án)") {
                answers.forEachIndexed { i, value ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            FormTextField(
                                tr("Đáp án {0}", i + 1) + if (i < 2) " (*)" else "",
                                value,
                                { v -> answers = answers.toMutableList().also { it[i] = v } },
                                placeholder = if (i < 2) "" else tr("tuỳ chọn")
                            )
                        }
                        if (i >= 4) {
                            IconButton(onClick = {
                                answers = answers.toMutableList().also { it.removeAt(i) }
                                if (correct == i) correct = 0 else if (correct > i) correct -= 1
                            }) { Icon(Icons.Default.RemoveCircleOutline, tr("Xóa"), tint = Color(0xFFDC2626)) }
                        }
                    }
                }
                if (answers.size < 10) {
                    FutaButton(text = "Thêm đáp án", icon = Icons.Default.Add, variant = FutaButtonVariant.OUTLINE, height = 38.dp, onClick = { answers = answers + "" })
                }
            }
            FormSection("Đáp án đúng") {
                answers.forEachIndexed { i, value ->
                    if (i < 2 || value.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { correct = i }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(selected = correct == i, onClick = { correct = i }, colors = RadioButtonDefaults.colors(selectedColor = FutaColors.BrandGreen))
                            Text(tr("Đáp án {0}", i + 1), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                            VerbatimText(value, fontSize = 12.5.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (isEdit) {
                InfoCard(question!!)
                DangerZoneCard(
                    title = "Xóa câu hỏi",
                    message = "Câu hỏi sẽ bị xóa khỏi ngân hàng câu hỏi.",
                    buttonText = if (deleting) "Đang xóa…" else "Xóa câu hỏi",
                    enabled = !deleting && !saving,
                    onClick = { showDelete = true }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
    ConfirmDialog(
        visible = showDelete,
        title = "Xóa câu hỏi?",
        message = "Bạn có chắc muốn xóa câu hỏi này? Thao tác không thể hoàn tác.",
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { showDelete = false },
        onConfirm = {
            showDelete = false
            scope.launch {
                deleting = true
                try {
                    APIClient.get().request("/advisor/questions/${question!!.id}", method = "DELETE")
                    ToastCenter.show(tr("Đã xóa câu hỏi"))
                    onSaved()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể xóa câu hỏi"), isError = true)
                } finally {
                    deleting = false
                }
            }
        }
    )
}

// ============================================================================
// Bulk import (iOS QuestionImportSheet): JSON array, pasted or picked from a file
// ============================================================================

private const val QUESTION_IMPORT_SAMPLE = """[
  {
    "content": "Điều kiện để trở thành TVV chính thức của FUTA Land là gì?",
    "answers": ["Đạt kỳ thi sát hạch", "Đóng tiền", "Có quan hệ", "Không cần"],
    "correctAnswer": 0,
    "difficulty": "Dễ",
    "category": "Quy chế",
    "active": true
  }
]"""

/** Parses the import text: a JSON array of questions, or `{ "questions": [...] }`. */
private fun parseQuestionImport(text: String): List<JSONValue>? {
    val trimmed = text.trim().removePrefix("﻿")
    if (trimmed.isEmpty()) return null
    val root = runCatching { JSONValue(Json.parseToJsonElement(trimmed)) }.getOrNull() ?: return null
    val list = if (root.element is JsonArray) root.array else root["questions"].array
    return list.takeIf { it.isNotEmpty() && it.all { q -> q.element is JsonObject } }
}

@Composable
private fun QuestionImportScreen(onClose: () -> Unit, onImported: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    val parsed = remember(input) { parseQuestionImport(input) }

    fun close() { if (input.isNotBlank() && !importing) showDiscard = true else onClose() }
    BackHandler { close() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            }
            if (text == null) {
                ToastCenter.show(tr("Không thể truy cập tệp đã chọn"), isError = true)
            } else {
                input = text
                fileName = displayNameOf(context, uri)
                error = if (parseQuestionImport(text) == null) tr("Định dạng JSON không hợp lệ hoặc danh sách câu hỏi trống") else null
            }
        }
    }

    fun submit() {
        val list = parsed
        if (list == null) {
            error = tr("Định dạng JSON không hợp lệ hoặc danh sách câu hỏi trống")
            return
        }
        if (list.size > 1000) {
            error = tr("Tối đa 1000 câu hỏi mỗi lần nhập")
            return
        }
        error = null
        scope.launch {
            importing = true
            try {
                val res = APIClient.get().request(
                    "/advisor/questions/import", method = "POST",
                    bodyJson = buildJsonObject { put("questions", JsonArray(list.map { questionBody(it) })) }.toString()
                )
                val d = res["data"]
                ToastCenter.show(tr("Đã nhập {0} câu hỏi, bỏ qua {1} câu trùng", d["created"].int, d["skipped"].int))
                onImported()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể nhập câu hỏi"), isError = true)
            } finally {
                importing = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = "Nhập câu hỏi JSON", onBack = { close() }) },
        bottomBar = {
            FutaStickyActionBar {
                FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, enabled = !importing, onClick = { close() }, modifier = Modifier.weight(1f))
                FutaButton(
                    text = if (importing) "Đang nhập…" else "Nhập dữ liệu",
                    icon = if (importing) null else Icons.Default.Download,
                    enabled = !importing && parsed != null,
                    onClick = { submit() },
                    modifier = Modifier.weight(1.6f)
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            FormSection("Cấu trúc JSON mẫu (Tham khảo)") {
                Text(
                    "Yêu cầu: mảng đối tượng với `content`, `answers` (2-10 đáp án), `correctAnswer` (0-based index), `difficulty` ('Dễ' | 'Trung bình' | 'Khó'), `category`, `active`.",
                    fontSize = 12.sp, color = FutaColors.Slate, lineHeight = 17.sp
                )
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF1F5F9)) {
                    androidx.compose.material3.Text(QUESTION_IMPORT_SAMPLE, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = FutaColors.Navy, modifier = Modifier.padding(8.dp))
                }
            }
            FormSection("Tệp dữ liệu") {
                FutaButton(
                    text = if (fileName.isEmpty()) "Chọn tệp JSON" else "Chọn tệp khác",
                    icon = Icons.Default.UploadFile,
                    variant = FutaButtonVariant.MINT,
                    enabled = !importing,
                    onClick = { picker.launch(arrayOf("application/json", "text/plain", "text/*", "application/octet-stream")) }
                )
                if (fileName.isNotEmpty()) VerbatimText(fileName, fontSize = 12.5.sp, color = FutaColors.Slate)
            }
            FormSection("Dán dữ liệu JSON cần nhập (*)") {
                FutaTextArea(value = input, onValueChange = { input = it; error = null }, placeholder = "Dán chuỗi JSON danh sách câu hỏi cần nhập vào đây…", minLines = 8, maxLines = 16)
                if (input.isNotBlank()) {
                    if (parsed != null) StatusPill(tr("Hợp lệ: {0} câu hỏi", parsed.size), Color(0xFF16A34A))
                    else StatusPill("JSON không hợp lệ", Color(0xFFDC2626))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
}
