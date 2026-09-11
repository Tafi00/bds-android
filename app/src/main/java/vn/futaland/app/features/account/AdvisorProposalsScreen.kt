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
fun AdvisorProposalsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var proposals by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedStatus by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val statusFilters = listOf(
        "" to "Tất cả",
        "submitted" to "Đã gửi",
        "reviewing" to "Đang xem xét",
        "approved" to "Đã duyệt",
        "rejected" to "Từ chối"
    )

    fun loadData() {
        scope.launch {
            loading = true
            try {
                val res = APIClient.get().request("/advisor/proposals")
                proposals = res["data"].array
            } catch (_: Exception) {
                proposals = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val filtered = remember(proposals, selectedStatus) {
        proposals.filter {
            selectedStatus.isEmpty() || it["status"].string == selectedStatus
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = FutaColors.Navy)
                    }
                    Text(
                        text = "Đề xuất của tôi",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = FutaColors.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "Tạo đề xuất", tint = FutaColors.BrandGreen)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statusFilters.forEach { (key, label) ->
                    val isSelected = selectedStatus == key
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) FutaColors.BrandGreen else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { selectedStatus = key }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else FutaColors.Navy,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (loading && proposals.isEmpty()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) {
                        FutaSkeletonBlock(height = 110.dp, radius = 16.dp)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                        Text("Không có đề xuất", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = FutaColors.Navy)
                        Text(
                            "Gửi ý kiến đóng góp hoặc đề xuất chính sách bán hàng tới ban quản trị.",
                            fontSize = 13.5.sp,
                            color = FutaColors.Slate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Tạo đề xuất mới", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        FutaCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item["title"].string.ifEmpty { "Đề xuất chính sách" },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FutaColors.Navy
                                    )
                                    val status = item["status"].string
                                    when (status) {
                                        "approved" -> ProposalBadge("Đã duyệt", FutaColors.BrandGreen, Color(0xFFECFDF5))
                                        "reviewing" -> ProposalBadge("Đang xem xét", Color(0xFFD97706), Color(0xFFFEF3C7))
                                        "rejected" -> ProposalBadge("Từ chối", Color(0xFFDC2626), Color(0xFFFEE2E2))
                                        else -> ProposalBadge("Đã gửi", Color(0xFF2563EB), Color(0xFFEFF6FF))
                                    }
                                }

                                if (item["content"].string.isNotEmpty()) {
                                    Text(
                                        text = item["content"].string,
                                        fontSize = 13.sp,
                                        color = FutaColors.Slate,
                                        maxLines = 2
                                    )
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item["category"].string.ifEmpty { "Kinh doanh" },
                                        fontSize = 12.sp,
                                        color = FutaColors.Slate
                                    )
                                    Text(
                                        text = item["createdAt"].string.take(10),
                                        fontSize = 11.5.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Chính sách bán hàng") }
        var content by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!submitting) showCreateDialog = false },
            title = { Text("Tạo đề xuất mới", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FutaInput(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "Tiêu đề đề xuất *",
                        modifier = Modifier.fillMaxWidth()
                    )
                    FutaInput(
                        value = category,
                        onValueChange = { category = it },
                        placeholder = "Danh mục",
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Nội dung chi tiết đề xuất...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            submitting = true
                            try {
                                val body = """{"title":"$title","category":"$category","content":"$content"}"""
                                APIClient.get().request("/advisor/proposals", method = "POST", bodyJson = body)
                                ToastCenter.show("Đã gửi đề xuất thành công!")
                                showCreateDialog = false
                                loadData()
                            } catch (e: Exception) {
                                ToastCenter.show("Lỗi: ${e.message}", isError = true)
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    enabled = title.isNotBlank() && !submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = FutaColors.BrandGreen)
                ) {
                    Text("Gửi đề xuất")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !submitting) {
                    Text("Hủy", color = FutaColors.Slate)
                }
            }
        )
    }
}

@Composable
private fun ProposalBadge(text: String, color: Color, bg: Color) {
    Surface(color = bg, shape = CircleShape) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
