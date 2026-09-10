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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
fun AdminExamsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var exams by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var questions by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var attempts by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun loadData() {
        scope.launch {
            loading = true
            try {
                when (selectedTab) {
                    0 -> {
                        val res = APIClient.get().request("/advisor/exams")
                        exams = res["data"].array.ifEmpty { res["exams"].array }
                    }
                    1 -> {
                        val res = APIClient.get().request("/advisor/exams/questions")
                        questions = res["data"].array.ifEmpty { res["questions"].array }
                    }
                    2 -> {
                        val res = APIClient.get().request("/advisor/exams/attempts")
                        attempts = res["data"].array.ifEmpty { res["attempts"].array }
                    }
                }
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        loadData()
    }

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
                        text = "Quản lý bài thi",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy
                    )

                    Spacer(Modifier.width(40.dp))
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
            // 3 Tabs matching iOS AdminExamsView
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(0, "Đề thi sát hạch", Icons.Default.Description),
                        Triple(1, "Ngân hàng câu hỏi", Icons.Default.Folder),
                        Triple(2, "Lịch sử thi", Icons.Default.History)
                    ).forEach { (idx, label, icon) ->
                        val isSel = selectedTab == idx
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) FutaColors.Navy else Color.White,
                            border = BorderStroke(1.dp, if (isSel) FutaColors.Navy else Color(0xFFE2E8F0)),
                            shadowElevation = if (isSel) 2.dp else 1.dp,
                            modifier = Modifier.clickable { selectedTab = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(icon, null, tint = if (isSel) Color.White else FutaColors.Slate, modifier = Modifier.size(14.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else FutaColors.Navy
                                )
                            }
                        }
                    }
                }
            }

            if (loading) {
                items(3) {
                    FutaSkeletonBlock(height = 80.dp, radius = 14.dp)
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        if (exams.isEmpty()) {
                            item {
                                FutaEmptyState(
                                    title = "Chưa có đề thi",
                                    message = "Danh sách đề thi sát hạch chứng chỉ đang được cập nhật."
                                )
                            }
                        } else {
                            itemsIndexed(exams, key = { idx, item -> item.id.ifEmpty { "ex-$idx" } }) { _, exam ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    shadowElevation = 1.dp,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = exam["title"].string.ifEmpty { "Đề thi sát hạch" },
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FutaColors.Navy
                                            )
                                            Surface(shape = CircleShape, color = Color(0xFFEAF5EF)) {
                                                Text("Áp dụng", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp))
                                            }
                                        }
                                        Text(
                                            text = "Điểm đạt: ${exam["passingScore"].int.takeIf { it > 0 } ?: 85}% • Thời gian: ${exam["duration"].int.takeIf { it > 0 } ?: 30} phút • Độ khó: ${exam["difficulty"].string.ifEmpty { "Tiêu chuẩn" }}",
                                            fontSize = 11.5.sp,
                                            color = FutaColors.Slate
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (questions.isEmpty()) {
                            item {
                                FutaEmptyState(
                                    title = "Ngân hàng câu hỏi",
                                    message = "Ngân hàng câu hỏi sát hạch trắc nghiệm."
                                )
                            }
                        } else {
                            itemsIndexed(questions, key = { idx, item -> item.id.ifEmpty { "q-$idx" } }) { _, q ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    shadowElevation = 1.dp,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(q["content"].string.ifEmpty { q["question"].string }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                        Text("Chủ đề: ${q["category"].string.ifEmpty { "Kiến thức BĐS" }}", fontSize = 11.5.sp, color = FutaColors.Slate)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        if (attempts.isEmpty()) {
                            item {
                                FutaEmptyState(
                                    title = "Lịch sử sát hạch",
                                    message = "Danh sách kết quả sát hạch của tư vấn viên."
                                )
                            }
                        } else {
                            itemsIndexed(attempts, key = { idx, item -> item.id.ifEmpty { "att-$idx" } }) { _, att ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    shadowElevation = 1.dp,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(att["userName"].string.ifEmpty { "Tư vấn viên" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                            Text("Điểm số: ${att["score"].int}%", fontSize = 12.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.SemiBold)
                                        }
                                        Surface(shape = CircleShape, color = Color(0xFFEAF5EF)) {
                                            Text("Đạt", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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
}
