package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.futaland.app.designsystem.*

internal const val ZALO_DEFAULT_CAMPAIGN_MESSAGE =
    "Xin chào \${name}, cảm ơn bạn đã quan tâm đến dịch vụ của chúng tôi."

internal fun ZaloCampaignStatus.icon() = when (this) {
    ZaloCampaignStatus.PENDING -> Icons.Default.Schedule
    ZaloCampaignStatus.RUNNING -> Icons.Default.PlayCircle
    ZaloCampaignStatus.PAUSED_SCHEDULE -> Icons.Default.EventBusy
    ZaloCampaignStatus.PAUSED_DAILY_LIMIT -> Icons.Default.Error
    ZaloCampaignStatus.PAUSED_MANUAL -> Icons.Default.PauseCircle
    ZaloCampaignStatus.COMPLETED -> Icons.Default.CheckCircle
    ZaloCampaignStatus.FAILED -> Icons.Default.Cancel
}

/** Filter sheet shared by the campaign screens: Apply, Reset and Close like the iOS sheet. */
@Composable
internal fun ZaloFilterSheet(
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = tr("Bộ lọc"),
        headerTrailing = {
            Text("Đặt lại", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626),
                modifier = Modifier.clickable(onClick = onReset))
        },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton("Đóng", onClick = onDismiss, variant = FutaButtonVariant.OUTLINE, modifier = Modifier.weight(1f))
                FutaButton("Áp dụng", onClick = { onApply(); onDismiss() }, icon = Icons.Default.Check, modifier = Modifier.weight(1f))
            }
        },
        content = { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
    )
}

/** Single choice among [options] (value to label) as a select field with a dropdown. */
@Composable
internal fun ZaloPickerField(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    verbatimOptions: Boolean = false,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FutaSelectField(
            title = title,
            displayValue = options.firstOrNull { it.first == selected }?.second.orEmpty(),
            placeholder = options.firstOrNull()?.second ?: tr("Chọn..."),
            onClick = { if (enabled) open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        if (verbatimOptions && value.isNotEmpty()) VerbatimText(label, fontSize = 14.sp) else Text(label, fontSize = 14.sp)
                    },
                    trailingIcon = if (value == selected) ({ Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen) }) else null,
                    onClick = { open = false; onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = FutaColors.Body)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 15.sp)
        }
        FutaSwitch(checked = checked, onCheckedChange = { if (enabled) onChange(it) }, activeColor = ZaloBlue)
    }
}

@Composable
internal fun ZaloCampaignMediaPreview(url: String) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp)),
        loading = { FutaSkeletonBlock(height = 160.dp, radius = 8.dp) },
        error = {
            Row(Modifier.fillMaxWidth().heightIn(min = 100.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Default.BrokenImage, null, tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Không thể tải ảnh đính kèm", fontSize = 12.sp, color = FutaColors.Slate)
            }
        }
    )
}

@Composable
private fun ProgressBar(value: Float) {
    LinearProgressIndicator(
        progress = { value },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
        color = ZaloBlue,
        trackColor = Color(0xFFE2E8F0),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

// MARK: - Campaign list

@Composable
private fun CampaignCard(campaign: ZaloCampaignModel, onClick: () -> Unit) {
    FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder, onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VerbatimText(campaign.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy,
                    modifier = Modifier.weight(1f))
                ZaloStatusPill(campaign.status.title, campaign.status.color)
            }
            val template = campaign.config["messageTemplate"].string
            if (template.isNotEmpty()) {
                VerbatimText(template, fontSize = 13.sp, color = FutaColors.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text(tr("Đã gửi: {0}/{1}", campaign.sentCount, campaign.totalJobs), fontSize = 12.sp, color = FutaColors.Slate,
                        modifier = Modifier.weight(1f))
                    VerbatimText("${(campaign.sentProgress * 100).toInt()}%", fontSize = 12.sp, color = FutaColors.Slate)
                }
                ProgressBar(campaign.sentProgress)
            }
            Row {
                if (campaign.failedCount > 0) {
                    Text(tr("{0} thất bại", campaign.failedCount), fontSize = 11.5.sp, color = Color(0xFFDC2626))
                }
                Spacer(Modifier.weight(1f))
                VerbatimText(ZaloDates.dateTime(campaign.createdAt), fontSize = 11.5.sp, color = FutaColors.Slate)
            }
        }
    }
}

@Composable
fun ZaloCampaignsListView(navigator: ZaloNavigator) {
    val scope = rememberCoroutineScope()
    var campaigns by remember { mutableStateOf<List<ZaloCampaignModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<ZaloCampaignStatus?>(null) }
    var sortByStatus by remember { mutableStateOf(false) }
    var ascending by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var draftStatus by remember { mutableStateOf<ZaloCampaignStatus?>(null) }
    var draftSortByStatus by remember { mutableStateOf(false) }
    var draftAscending by remember { mutableStateOf(false) }

    suspend fun load() {
        if (!ZaloAccess.canUseCampaigns) return
        loading = true
        error = null
        try {
            campaigns = ZaloService.fetchCampaigns()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: tr("Không thể tải chiến dịch")
        }
        loading = false
    }

    LaunchedEffect(navigator.campaignsVersion) { load() }
    LaunchedEffect(Unit) {
        ZaloWebSocketManager.events.collect { event ->
            if (event is ZaloEvent.CampaignProgress) {
                val d = event.data
                campaigns = campaigns.map { campaign ->
                    if (campaign.id != event.campaignId) campaign else {
                        val updates = buildMap<String, Any?> {
                            for (key in listOf("sentCount", "failedCount", "pendingCount", "totalJobs")) {
                                if (!d[key].isNull) put(key, d[key].int)
                            }
                            d["status"].string.takeIf { it.isNotEmpty() }?.let { put("status", it) }
                        }
                        ZaloCampaignModel(campaign.raw.withUpdates(updates))
                    }
                }
            }
        }
    }

    val filtered = campaigns.filter {
        (selectedStatus == null || it.status == selectedStatus) && (search.isBlank() || it.name.contains(search.trim(), ignoreCase = true))
    }.sortedWith { a, b ->
        if (sortByStatus && a.status.order != b.status.order) {
            if (ascending) a.status.order - b.status.order else b.status.order - a.status.order
        } else {
            val cmp = ZaloDates.epoch(a.createdAt).compareTo(ZaloDates.epoch(b.createdAt))
            if (ascending) cmp else -cmp
        }
    }
    val activeFilters = (if (selectedStatus != null) 1 else 0) + (if (sortByStatus || ascending) 1 else 0)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FutaInput(value = search, onValueChange = { search = it }, placeholder = tr("Tìm chiến dịch theo tên"),
                leadingIcon = Icons.Default.Search, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (activeFilters > 0) ZaloBlueSoft else Color(0xFFF1F5F9))
                    .clickable {
                        draftStatus = selectedStatus; draftSortByStatus = sortByStatus; draftAscending = ascending
                        showFilters = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FilterList, tr("Bộ lọc"), tint = if (activeFilters > 0) ZaloBlue else FutaColors.Slate)
                if (activeFilters > 0) {
                    VerbatimText("$activeFilters", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(ZaloBlue)
                            .padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ZaloBlue)
                    .clickable { navigator.push(ZaloRoute.CreateCampaign) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, tr("Tạo chiến dịch"), tint = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("{0} chiến dịch", filtered.size), fontSize = 12.5.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
            selectedStatus?.let { status ->
                ZaloChip(text = status.title, selected = true, icon = Icons.Default.Close, onClick = { selectedStatus = null })
            }
            if (activeFilters > 0) {
                Text("Xoá tất cả", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZaloBlue,
                    modifier = Modifier.clickable { selectedStatus = null; sortByStatus = false; ascending = false })
            }
        }
        error?.takeIf { campaigns.isNotEmpty() }?.let { Box(Modifier.padding(horizontal = 16.dp)) { ZaloInlineError(it) { scope.launch { load() } } } }

        when {
            loading && campaigns.isEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(5) { FutaAdminRowSkeleton() }
            }
            error != null && campaigns.isEmpty() -> ZaloErrorState(error!!, onRetry = { scope.launch { load() } })
            filtered.isEmpty() -> FutaEmptyState(
                title = if (campaigns.isEmpty()) "Chưa có chiến dịch nào" else "Không tìm thấy chiến dịch",
                message = if (campaigns.isEmpty()) "Tạo chiến dịch đầu tiên để bắt đầu gửi tin nhắn hàng loạt"
                else "Thử thay đổi từ khóa hoặc bộ lọc.",
                icon = Icons.Default.Campaign,
                actionButton = {
                    if (campaigns.isEmpty()) {
                        FutaButton("Tạo chiến dịch", onClick = { navigator.push(ZaloRoute.CreateCampaign) }, height = 40.dp)
                    } else {
                        FutaButton("Xoá bộ lọc", variant = FutaButtonVariant.OUTLINE, height = 40.dp, onClick = {
                            search = ""; selectedStatus = null; sortByStatus = false; ascending = false
                        })
                    }
                }
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { campaign ->
                    CampaignCard(campaign) { navigator.push(ZaloRoute.CampaignDetail(campaign.id)) }
                }
            }
        }
    }

    if (showFilters) {
        ZaloFilterSheet(
            onDismiss = { showFilters = false },
            onReset = { draftStatus = null; draftSortByStatus = false; draftAscending = false },
            onApply = { selectedStatus = draftStatus; sortByStatus = draftSortByStatus; ascending = draftAscending }
        ) {
            ZaloPickerField(
                title = "Trạng thái",
                options = listOf("" to tr("Tất cả")) + ZaloCampaignStatus.entries.map { it.raw to tr(it.title) },
                selected = draftStatus?.raw.orEmpty(),
                onSelect = { raw -> draftStatus = if (raw.isEmpty()) null else ZaloCampaignStatus.from(raw) }
            )
            ZaloPickerField(
                title = "Sắp xếp",
                options = listOf("date" to tr("Ngày tạo"), "status" to tr("Trạng thái")),
                selected = if (draftSortByStatus) "status" else "date",
                onSelect = { draftSortByStatus = it == "status" }
            )
            ToggleRow("Tăng dần", draftAscending, { draftAscending = it })
        }
    }
}

// MARK: - Campaign detail

private enum class CampaignTab { JOBS, LOGS, INFO }

@Composable
fun ZaloCampaignDetailScreen(campaignId: String, navigator: ZaloNavigator) {
    val scope = rememberCoroutineScope()
    var campaign by remember { mutableStateOf<ZaloCampaignModel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(CampaignTab.INFO) }
    var performing by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }

    var jobs by remember { mutableStateOf<List<ZaloCampaignJobModel>>(emptyList()) }
    var jobsTotal by remember { mutableIntStateOf(0) }
    var jobStatus by remember { mutableStateOf(ZaloJobStatus.ALL) }
    var loadingJobs by remember { mutableStateOf(false) }
    var jobsError by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf<List<ZaloCampaignLogModel>>(emptyList()) }
    var logsTotal by remember { mutableIntStateOf(0) }
    var logLevel by remember { mutableStateOf(ZaloLogLevel.ALL) }
    var loadingLogs by remember { mutableStateOf(false) }
    var logsError by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var draftJobStatus by remember { mutableStateOf(ZaloJobStatus.ALL) }
    var draftLogLevel by remember { mutableStateOf(ZaloLogLevel.ALL) }

    suspend fun loadDetail() {
        loading = true
        error = null
        try {
            campaign = ZaloService.getCampaign(campaignId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: tr("Không thể tải chiến dịch")
        }
        loading = false
    }

    suspend fun loadJobs(append: Boolean = false) {
        if (loadingJobs) return
        jobsError = null
        if (!append) jobs = emptyList()
        loadingJobs = true
        try {
            val (list, total) = ZaloService.getCampaignJobs(campaignId, jobStatus, 100, if (append) jobs.size else 0)
            jobs = if (append) jobs + list else list
            jobsTotal = total
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            jobsError = e.message ?: tr("Không thể tải người nhận")
        }
        loadingJobs = false
    }

    suspend fun loadLogs(append: Boolean = false) {
        if (loadingLogs) return
        logsError = null
        if (!append) logs = emptyList()
        loadingLogs = true
        try {
            val (list, total) = ZaloService.getCampaignLogs(campaignId, logLevel, 100, if (append) logs.size else 0)
            logs = if (append) {
                val existing = logs.map { it.id }.toSet()
                logs + list.filter { it.id !in existing }
            } else list
            logsTotal = total
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logsError = e.message ?: tr("Không thể tải nhật ký")
        }
        loadingLogs = false
    }

    suspend fun refreshAll() {
        loadDetail()
        loadJobs()
        loadLogs()
    }

    LaunchedEffect(Unit) { refreshAll() }
    LaunchedEffect(Unit) {
        ZaloWebSocketManager.events.collect { event ->
            when (event) {
                is ZaloEvent.CampaignProgress -> if (event.campaignId == campaignId) {
                    val current = campaign ?: return@collect
                    val d = event.data
                    val updates = buildMap<String, Any?> {
                        for (key in listOf("sentCount", "failedCount", "pendingCount", "totalJobs")) {
                            if (!d[key].isNull) put(key, d[key].int)
                        }
                        d["status"].string.takeIf { it.isNotEmpty() }?.let { put("status", it) }
                    }
                    campaign = ZaloCampaignModel(current.raw.withUpdates(updates))
                }
                is ZaloEvent.CampaignLog -> {
                    val log = event.log
                    if (log.campaignId == campaignId && (logLevel == ZaloLogLevel.ALL || log.level == logLevel) &&
                        logs.none { it.id == log.id }
                    ) {
                        logs = listOf(log) + logs
                        logsTotal += 1
                    }
                }
                else -> Unit
            }
        }
    }

    fun perform(action: String, block: suspend () -> Unit) {
        if (performing != null) return
        performing = action
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể cập nhật chiến dịch"), isError = true)
            }
            performing = null
        }
    }

    fun startOrResume(camp: ZaloCampaignModel) = perform("start") {
        if (camp.status == ZaloCampaignStatus.PENDING) {
            ZaloService.startCampaign(camp.id)
            ToastCenter.show(tr("Đã bắt đầu chiến dịch"))
        } else {
            ZaloService.resumeCampaign(camp.id)
            ToastCenter.show(tr("Đã tiếp tục chiến dịch"))
        }
        loadDetail()
        navigator.campaignsVersion++
    }

    fun pause() = perform("pause") {
        ZaloService.pauseCampaign(campaignId)
        ToastCenter.show(tr("Đã tạm dừng chiến dịch"))
        loadDetail()
        navigator.campaignsVersion++
    }

    fun cancel() = perform("cancel") {
        ZaloService.cancelCampaign(campaignId)
        ToastCenter.show(tr("Đã huỷ bỏ chiến dịch"))
        loadDetail()
        navigator.campaignsVersion++
    }

    fun rerun() = perform("rerun") {
        ZaloService.rerunCampaign(campaignId)
        ToastCenter.show(tr("Đã tạo chiến dịch chạy lại thành công!"))
        navigator.campaignsVersion++
        navigator.pop()
    }

    fun delete() = perform("delete") {
        val current = campaign
        if (current == null || !current.status.canDelete) return@perform
        ZaloService.deleteCampaign(campaignId)
        ToastCenter.show(tr("Đã xoá chiến dịch"))
        navigator.campaignsVersion++
        navigator.pop()
    }

    val camp = campaign
    ZaloTopBar(
        title = camp?.name ?: tr("Chi tiết chiến dịch"),
        verbatimTitle = camp != null,
        onBack = { navigator.pop() }
    ) {
        if (camp != null && camp.status.canDelete) {
            FutaHeaderIconButton(Icons.Default.Delete, tr("Xóa chiến dịch"), { if (performing == null) showDelete = true },
                tint = Color(0xFFDC2626))
        }
    }

    when {
        loading && camp == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FutaSkeletonBlock(height = 170.dp, radius = 16.dp)
            FutaSkeletonBlock(height = 40.dp, radius = 10.dp)
            repeat(4) { FutaAdminRowSkeleton() }
        }
        error != null && camp == null -> ZaloErrorState(error!!, onRetry = { scope.launch { refreshAll() } })
        camp != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { msg -> item { ZaloInlineError(msg) { scope.launch { loadDetail() } } } }
            item { OverviewCard(camp) }
            item {
                ActionBar(camp, performing, onStart = { startOrResume(camp) }, onPause = { pause() },
                    onCancel = { showCancel = true }, onRerun = { rerun() })
            }
            item {
                FutaSegmentTabs(
                    items = CampaignTab.entries.toList(),
                    selectedItem = tab,
                    onSelect = { tab = it },
                    titleFor = {
                        when (it) {
                            CampaignTab.JOBS -> tr("Người nhận ({0})", camp.totalJobs)
                            CampaignTab.LOGS -> "Nhật ký"
                            CampaignTab.INFO -> "Thông tin"
                        }
                    }
                )
            }
            when (tab) {
                CampaignTab.JOBS -> {
                    item {
                        DetailFilterBar(tr("{0} người nhận · {1}", jobsTotal, tr(jobStatus.title)), jobStatus != ZaloJobStatus.ALL,
                            enabled = !loadingJobs && !loadingLogs) {
                            draftJobStatus = jobStatus; draftLogLevel = logLevel; showFilters = true
                        }
                    }
                    when {
                        loadingJobs && jobs.isEmpty() -> items(4) { FutaAdminRowSkeleton() }
                        jobsError != null -> item { ZaloErrorState(jobsError!!, onRetry = { scope.launch { loadJobs() } }) }
                        jobs.isEmpty() -> item {
                            FutaEmptyState(
                                title = "Không có người nhận nào",
                                message = if (jobStatus == ZaloJobStatus.ALL) "Chiến dịch chưa có người nhận."
                                else "Không tìm thấy danh sách gửi phù hợp trạng thái này.",
                                icon = Icons.Default.PersonOff
                            )
                        }
                        else -> {
                            items(jobs, key = { "job-${it.id}" }) { job -> JobRow(job) }
                            if (jobs.size < jobsTotal) {
                                item {
                                    ZaloButton("Tải thêm người nhận", onClick = { scope.launch { loadJobs(append = true) } },
                                        variant = FutaButtonVariant.OUTLINE, loading = loadingJobs, height = 40.dp, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                CampaignTab.LOGS -> {
                    item {
                        DetailFilterBar(tr("{0} nhật ký · {1}", logsTotal, tr(logLevel.title)), logLevel != ZaloLogLevel.ALL,
                            enabled = !loadingJobs && !loadingLogs) {
                            draftJobStatus = jobStatus; draftLogLevel = logLevel; showFilters = true
                        }
                    }
                    when {
                        loadingLogs && logs.isEmpty() -> items(5) { FutaAdminRowSkeleton() }
                        logsError != null -> item { ZaloErrorState(logsError!!, onRetry = { scope.launch { loadLogs() } }) }
                        logs.isEmpty() -> item {
                            FutaEmptyState(
                                title = "Chưa có nhật ký",
                                message = if (logLevel == ZaloLogLevel.ALL) "Chưa có thông tin sự kiện nào được ghi nhận."
                                else "Không có nhật ký phù hợp mức đã chọn.",
                                icon = Icons.Default.Description
                            )
                        }
                        else -> {
                            items(logs, key = { "log-${it.id}" }) { log -> LogRow(log) }
                            if (logs.size < logsTotal) {
                                item {
                                    ZaloButton("Tải thêm nhật ký", onClick = { scope.launch { loadLogs(append = true) } },
                                        variant = FutaButtonVariant.OUTLINE, loading = loadingLogs, height = 40.dp, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                CampaignTab.INFO -> item { ConfigSections(camp) }
            }
        }
    }

    if (showFilters) {
        ZaloFilterSheet(
            onDismiss = { showFilters = false },
            onReset = { draftJobStatus = ZaloJobStatus.ALL; draftLogLevel = ZaloLogLevel.ALL },
            onApply = {
                jobStatus = draftJobStatus
                logLevel = draftLogLevel
                scope.launch { loadJobs(); loadLogs() }
            }
        ) {
            ZaloPickerField("Trạng thái người nhận", ZaloJobStatus.entries.map { it.raw to tr(it.title) }, draftJobStatus.raw,
                { raw -> draftJobStatus = ZaloJobStatus.entries.first { it.raw == raw } })
            ZaloPickerField("Mức nhật ký", ZaloLogLevel.entries.map { it.raw to tr(it.title) }, draftLogLevel.raw,
                { raw -> draftLogLevel = ZaloLogLevel.entries.first { it.raw == raw } })
        }
    }

    ZaloConfirmDialog(
        visible = showDelete,
        title = "Xoá chiến dịch",
        message = "Bạn có chắc muốn xóa chiến dịch này? Hành động này không thể hoàn tác.",
        confirmText = "Xoá chiến dịch",
        onConfirm = { delete() },
        onDismiss = { showDelete = false }
    )
    ZaloConfirmDialog(
        visible = showCancel,
        title = "Huỷ chiến dịch",
        message = "Bạn có chắc muốn hủy chiến dịch này?",
        confirmText = "Huỷ chiến dịch",
        cancelText = "Bỏ qua",
        onConfirm = { cancel() },
        onDismiss = { showCancel = false }
    )
}

@Composable
private fun OverviewCard(camp: ZaloCampaignModel) {
    FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    VerbatimText(camp.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    Text(tr("Tạo lúc: {0}", ZaloDates.detail(camp.createdAt)), fontSize = 12.sp, color = FutaColors.Slate)
                }
                ZaloStatusPill(camp.status.title, camp.status.color, icon = camp.status.icon())
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ProgressBar(camp.progress)
                Row {
                    Text(tr("Đã gửi: {0}/{1}", camp.sentCount, camp.totalJobs), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = FutaColors.Slate, modifier = Modifier.weight(1f))
                    VerbatimText("${(camp.progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = camp.status.color)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val processed = camp.sentCount + camp.failedCount
                val rate = if (processed > 0) Math.round(camp.sentCount * 100.0 / processed).toInt() else 0
                StatBox("Thành công", "${camp.sentCount}", Color(0xFF16A34A))
                StatDivider()
                StatBox("Thất bại", "${camp.failedCount}", Color(0xFFDC2626))
                StatDivider()
                StatBox("Chờ xử lý", "${camp.pendingCount}", FutaColors.Slate)
                StatDivider()
                StatBox("Tỷ lệ thành công", "$rate%", ZaloBlue)
            }
            val maxDaily = camp.config["maxDailyMessages"].int
            if (maxDaily > 0) {
                Row {
                    Text("Tin nhắn hôm nay:", fontSize = 12.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                    VerbatimText("${camp.dailySentCount}/$maxDaily", fontSize = 12.sp, color = FutaColors.Slate)
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatBox(title: String, value: String, color: Color) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        VerbatimText(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(title, fontSize = 10.5.sp, color = FutaColors.Slate, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(32.dp).background(FutaColors.PanelDivider))
}

@Composable
private fun ActionBar(
    camp: ZaloCampaignModel,
    performing: String?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit
) {
    val busy = performing != null
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        when (camp.status) {
            ZaloCampaignStatus.PENDING, ZaloCampaignStatus.PAUSED_MANUAL, ZaloCampaignStatus.PAUSED_SCHEDULE,
            ZaloCampaignStatus.PAUSED_DAILY_LIMIT -> {
                ZaloButton(if (camp.status == ZaloCampaignStatus.PENDING) "Bắt đầu" else "Tiếp tục", onClick = onStart,
                    icon = Icons.Default.PlayArrow, loading = performing == "start", enabled = !busy, modifier = Modifier.weight(1f))
                if (camp.status != ZaloCampaignStatus.PENDING) {
                    ZaloButton("Hủy", onClick = onCancel, variant = FutaButtonVariant.DANGER, icon = Icons.Default.Stop,
                        loading = performing == "cancel", enabled = !busy, modifier = Modifier.width(110.dp))
                }
            }
            ZaloCampaignStatus.RUNNING -> {
                ZaloButton("Tạm dừng", onClick = onPause, variant = FutaButtonVariant.SECONDARY, icon = Icons.Default.Pause,
                    loading = performing == "pause", enabled = !busy, modifier = Modifier.weight(1f))
                ZaloButton("Hủy", onClick = onCancel, variant = FutaButtonVariant.DANGER, icon = Icons.Default.Stop,
                    loading = performing == "cancel", enabled = !busy, modifier = Modifier.width(110.dp))
            }
            ZaloCampaignStatus.COMPLETED, ZaloCampaignStatus.FAILED -> {
                ZaloButton("Chạy lại chiến dịch", onClick = onRerun, icon = Icons.Default.Refresh,
                    loading = performing == "rerun", enabled = !busy, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailFilterBar(summary: String, active: Boolean, enabled: Boolean, onFilter: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VerbatimText(summary, fontSize = 12.5.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onFilter).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.FilterList, null, tint = ZaloBlue, modifier = Modifier.size(16.dp))
            Text(if (active) tr("Bộ lọc ({0})", 1) else tr("Bộ lọc"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZaloBlue)
        }
    }
}

@Composable
private fun JobRow(job: ZaloCampaignJobModel) {
    FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (job.recipientName.isEmpty()) Text("Khách hàng", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = FutaColors.Navy, modifier = Modifier.weight(1f))
                else VerbatimText(job.recipientName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy,
                    modifier = Modifier.weight(1f))
                ZaloStatusPill(job.status.title, job.status.color)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Phone, null, tint = FutaColors.Slate, modifier = Modifier.size(12.dp))
                VerbatimText(job.phone, fontSize = 12.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                if (job.sentAt.isNotEmpty()) VerbatimText(ZaloDates.detail(job.sentAt), fontSize = 11.sp, color = FutaColors.Slate)
            }
            if (job.error.isNotEmpty()) Text(tr("Lỗi: {0}", job.error), fontSize = 11.5.sp, color = Color(0xFFDC2626))
            if (job.renderedMessage.isNotEmpty()) {
                VerbatimText(job.renderedMessage, fontSize = 11.5.sp, color = FutaColors.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFFF1F5F9)).padding(6.dp))
            }
        }
    }
}

@Composable
private fun LogRow(log: ZaloCampaignLogModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(log.level.color))
            VerbatimText(log.message, fontSize = 13.5.sp, color = FutaColors.Body, modifier = Modifier.weight(1f))
            if (log.timestamp.isNotEmpty()) VerbatimText(ZaloDates.detail(log.timestamp), fontSize = 11.sp, color = FutaColors.Slate)
        }
        if (log.phone.isNotEmpty() || log.recipientName.isNotEmpty()) {
            val who = listOfNotNull(log.recipientName.ifEmpty { null }, log.phone.ifEmpty { null }?.let { "($it)" }).joinToString(" ")
            VerbatimText(who, fontSize = 11.5.sp, color = FutaColors.Slate, modifier = Modifier.padding(start = 16.dp))
        }
        HorizontalDivider(color = FutaColors.PanelDivider, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun InfoRow(title: String, value: String, verbatimValue: Boolean = true) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 13.5.sp, color = FutaColors.Body, modifier = Modifier.weight(1f))
        if (verbatimValue) VerbatimText(value, fontSize = 13.5.sp, color = FutaColors.Slate)
        else Text(value, fontSize = 13.5.sp, color = FutaColors.Slate)
    }
}

@Composable
private fun ConfigSections(camp: ZaloCampaignModel) {
    val cfg = camp.config
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ZaloSection(title = "Thông tin chiến dịch") {
            InfoRow("Ngày tạo", ZaloDates.detail(camp.createdAt))
            val source = cfg["dataSource"].string
            InfoRow("Nguồn dữ liệu", when (source) {
                "crm" -> tr("Khách CRM")
                "group" -> tr("Thành viên nhóm")
                else -> tr("Danh sách người nhận")
            })
            if (source == "group") {
                VerbatimText(cfg["groupData"]["groupName"].string.ifEmpty { cfg["groupData"]["groupId"].string }, fontSize = 13.5.sp,
                    color = FutaColors.Navy)
            }
            if (source == "crm") InfoRow("Snapshot CRM", tr("{0} người nhận", cfg["crmSource"]["count"].int))
        }
        ZaloSection(title = "Nội dung tin nhắn mẫu") {
            val template = cfg["messageTemplate"].string
            if (template.isEmpty()) Text("Không có tin nhắn mẫu", fontSize = 14.sp, color = FutaColors.Slate)
            else VerbatimText(template, fontSize = 14.sp, color = FutaColors.Body, lineHeight = 20.sp)
        }
        val image = cfg["globalImageUrl"].string
        if (image.isNotEmpty()) {
            ZaloSection(title = "Hình ảnh đính kèm") { ZaloCampaignMediaPreview(image) }
        }
        ZaloSection(title = "Thông số gửi tin") {
            InfoRow("Khoảng cách giữa các tin", tr("{0} giây", cfg["delayBetweenMessages"].int))
            InfoRow("Khung giờ cho phép gửi", cfg["allowedHours"].string.ifEmpty { tr("Cả ngày (24/7)") })
            val maxDaily = cfg["maxDailyMessages"].int
            InfoRow("Giới hạn gửi mỗi ngày", if (maxDaily == 0) tr("Không giới hạn") else tr("{0} tin", maxDaily))
            InfoRow("Số lượng tối đa", tr("{0} người", cfg["maxItems"].int))
        }
        ZaloSection(title = "Tuỳ chọn hành vi") {
            InfoRow("Tự động gửi lời mời kết bạn", if (cfg["addFriend"].bool) tr("Có") else tr("Không"))
            InfoRow("Ưu tiên người nhắn lâu nhất", if (cfg["sortByOldestMessaged"].bool) tr("Có") else tr("Không"))
        }
        ZaloSection(title = "Tài khoản gửi tin") {
            val ids = cfg["selectedAccountIds"].array.map { it.string }
            if (ids.isEmpty()) Text("0 tài khoản", fontSize = 13.5.sp, color = FutaColors.Slate)
            ids.forEach { VerbatimText(tr("Tài khoản ID: {0}", it), fontSize = 12.5.sp, color = FutaColors.Body) }
        }
    }
}
