package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import vn.futaland.app.core.network.APIError
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import java.io.ByteArrayOutputStream

/** CRM audience filters (iOS ZaloCampaignCRMFilters); `json` is the snapshot request body. */
data class ZaloCampaignCRMFilters(
    val workspaceId: String = "",
    val pipelineId: String = "",
    val status: String = "",
    val assigneeId: String = "",
    val labels: Set<String> = emptySet()
) {
    val json: JsonObject
        get() = buildJsonObject {
            put("labels", JsonArray(labels.sorted().map { JsonPrimitive(it) }))
            if (workspaceId.isNotEmpty()) put("workspaceId", workspaceId)
            if (pipelineId.isNotEmpty()) put("pipelineId", pipelineId)
            if (status.isNotEmpty()) put("status", status)
            if (assigneeId.isNotEmpty()) put("assigneeId", assigneeId)
        }

    val activeCount: Int
        get() = listOf(workspaceId, pipelineId, status, assigneeId).count { it.isNotEmpty() } + (if (labels.isEmpty()) 0 else 1)
}

private data class RecipientEntry(val id: Long, val name: String, val phone: String, val note: String)

private enum class CampaignSource(val title: String) {
    LIST("Danh sách"), GROUP("Nhóm Zalo"), CRM("Khách CRM")
}

/** Backend allowed-hours format (schemas/campaign.ts). */
private val ALLOWED_HOURS = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d-(?:[01]\\d|2[0-3]):[0-5]\\d(?:,(?:[01]\\d|2[0-3]):[0-5]\\d-(?:[01]\\d|2[0-3]):[0-5]\\d)*$")

/** Campaign image as JPEG (EXIF-rotated), like iOS `jpegData(compressionQuality: 0.9)`. */
private suspend fun encodeCampaignImage(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 2560) {
                    val scale = 2560f / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
        bitmap?.let { bmp ->
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray().also { bmp.recycle() }
        }
    }.getOrNull()
}

@Composable
fun ZaloCreateCampaignScreen(navigator: ZaloNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(CampaignSource.LIST) }
    var recipients by remember { mutableStateOf<List<RecipientEntry>>(emptyList()) }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var recipientNote by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<ZaloGroupInfoModel>>(emptyList()) }
    var groupId by remember { mutableStateOf("") }
    var groupLink by remember { mutableStateOf("") }
    var scannedGroup by remember { mutableStateOf<ZaloGroupInfoModel?>(null) }
    var skipLeaders by remember { mutableStateOf(true) }
    var skipAlreadySent by remember { mutableStateOf(true) }
    var crmBootstrap by remember { mutableStateOf(JSONValue.Null) }
    var crmFilters by remember { mutableStateOf(ZaloCampaignCRMFilters()) }
    var draftCrmFilters by remember { mutableStateOf(ZaloCampaignCRMFilters()) }
    var crmSnapshot by remember { mutableStateOf(JSONValue.Null) }
    var showCrmFilters by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf(ZALO_DEFAULT_CAMPAIGN_MESSAGE) }
    var imageUrl by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf<List<ZaloAccountModel>>(emptyList()) }
    var selectedAccounts by remember { mutableStateOf<Set<String>>(emptySet()) }
    var delayText by remember { mutableStateOf("120") }
    var maxItemsText by remember { mutableStateOf("100") }
    var maxDailyText by remember { mutableStateOf("0") }
    var allowedHours by remember { mutableStateOf("") }
    var addFriend by remember { mutableStateOf(true) }
    var sortByOldest by remember { mutableStateOf(true) }
    var loadingAccounts by remember { mutableStateOf(true) }
    var loadingSource by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var accountsError by remember { mutableStateOf<String?>(null) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var accountsReload by remember { mutableIntStateOf(0) }
    var sourceReload by remember { mutableIntStateOf(0) }

    val delay = delayText.toIntOrNull()
    val maxItems = maxItemsText.toIntOrNull()
    val maxDaily = maxDailyText.toIntOrNull()
    val busy = submitting || uploading || scanning || previewing
    val eligibleAccounts = accounts.filter { source != CampaignSource.GROUP || it.provider == ZaloProvider.ZALO }
    val hasChanges = name.isNotEmpty() || source != CampaignSource.LIST || recipients.isNotEmpty() || recipientName.isNotEmpty() ||
        recipientPhone.isNotEmpty() || recipientNote.isNotEmpty() || groupId.isNotEmpty() || groupLink.isNotEmpty() ||
        template != ZALO_DEFAULT_CAMPAIGN_MESSAGE || imageUrl.isNotEmpty() || selectedAccounts.isNotEmpty() ||
        delayText != "120" || maxItemsText != "100" || maxDailyText != "0" || allowedHours.isNotEmpty() || !addFriend ||
        !sortByOldest || !skipLeaders || !skipAlreadySent

    val validationMessage: String? = when {
        name.isBlank() -> "Vui lòng nhập tên chiến dịch"
        template.isBlank() -> "Vui lòng nhập nội dung tin nhắn"
        selectedAccounts.isEmpty() -> "Vui lòng chọn ít nhất một tài khoản gửi"
        delay == null || maxItems == null || maxDaily == null || delay !in 0..86_400 || maxItems !in 1..10_000 ||
            maxDaily !in 0..10_000 -> "Giới hạn gửi không hợp lệ"
        allowedHours.isNotEmpty() && !ALLOWED_HOURS.matches(allowedHours.trim()) ->
            "Định dạng khung giờ không hợp lệ (VD: 08:00-12:00,14:00-18:00)"
        source == CampaignSource.LIST && recipients.isEmpty() -> "Vui lòng nhập ít nhất 1 người nhận."
        source == CampaignSource.LIST && recipients.any { r -> r.phone.count { it.isDigit() } < 8 } ->
            "Số điện thoại người nhận không hợp lệ (tối thiểu 8 chữ số)."
        source == CampaignSource.GROUP && groupId.isEmpty() && scannedGroup == null -> "Vui lòng chọn nhóm hoặc quét link nhóm"
        source == CampaignSource.CRM && (crmSnapshot["id"].string.isEmpty() || crmSnapshot["count"].int == 0) ->
            "Vui lòng xem trước và lưu snapshot người nhận CRM"
        else -> null
    }

    fun close() {
        if (busy) return
        if (hasChanges) showDiscard = true else navigator.pop()
    }

    BackHandler { close() }

    LaunchedEffect(accountsReload) {
        loadingAccounts = true
        accountsError = null
        try {
            accounts = ZaloService.fetchAccounts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            accountsError = e.message ?: tr("Không thể tải tài khoản")
        }
        loadingAccounts = false
    }

    LaunchedEffect(source, sourceReload) {
        // Only Zalo accounts can send to Zalo group members.
        selectedAccounts = selectedAccounts.filter { id -> eligibleAccounts.any { it.zaloId == id } }.toSet()
        if (source == CampaignSource.LIST) return@LaunchedEffect
        loadingSource = true
        sourceError = null
        try {
            if (source == CampaignSource.GROUP) groups = ZaloService.getMyGroups()
            if (source == CampaignSource.CRM) crmBootstrap = ZaloService.fetchCrmBootstrap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sourceError = e.message ?: tr("Không thể tải nguồn dữ liệu")
        }
        loadingSource = false
    }

    fun scanGroup() {
        val link = groupLink.trim()
        if (scanning || link.isEmpty()) return
        scanning = true
        scannedGroup = null
        groupId = ""
        submitError = null
        scope.launch {
            try {
                val result = ZaloService.scanGroupLink(link)
                if (groupLink.trim() == link) scannedGroup = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                submitError = e.message
            }
            scanning = false
        }
    }

    fun previewCrm() {
        if (previewing) return
        previewing = true
        crmSnapshot = JSONValue.Null
        submitError = null
        scope.launch {
            try {
                crmSnapshot = ZaloService.createCrmRecipientSnapshot(crmFilters.json)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                submitError = e.message
            }
            previewing = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || uploading) return@rememberLauncherForActivityResult
        uploading = true
        submitError = null
        scope.launch {
            try {
                val jpeg = encodeCampaignImage(context, uri) ?: throw APIError(0, tr("Không thể đọc ảnh đã chọn."))
                if (jpeg.size > 10 * 1024 * 1024) throw APIError(0, tr("File quá lớn. Tối đa 10MB"))
                imageUrl = ZaloService.uploadCampaignImage(jpeg)
                ToastCenter.show(tr("Đã tải ảnh lên"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                submitError = tr("Lỗi tải ảnh: {0}", e.message.orEmpty())
            }
            uploading = false
        }
    }

    fun create() {
        if (busy) return
        validationMessage?.let { submitError = it; return }
        submitting = true
        submitError = null
        val config = buildJsonObject {
            put("name", name.trim())
            put("messageTemplate", template)
            put("delayBetweenMessages", delay ?: 0)
            put("maxDailyMessages", maxDaily ?: 0)
            put("maxItems", maxItems ?: 1)
            put("allowedHours", allowedHours.trim())
            put("addFriend", addFriend)
            put("sortByOldestMessaged", sortByOldest)
            put("selectedAccountIds", JsonArray(selectedAccounts.sorted().map { JsonPrimitive(it) }))
            if (imageUrl.isNotEmpty()) put("globalImageUrl", imageUrl)
            when (source) {
                CampaignSource.LIST -> {
                    put("dataSource", "excel")
                    putJsonObject("excelData") {
                        put("recipients", JsonArray(recipients.map { r ->
                            buildJsonObject {
                                put("phone", r.phone)
                                put("name", r.name)
                                putJsonObject("customFields") { put("custom1", r.note) }
                            }
                        }))
                        putJsonObject("columnMapping") {
                            put("phoneColumn", "phone")
                            put("nameColumn", "name")
                        }
                    }
                }
                CampaignSource.GROUP -> {
                    put("dataSource", "group")
                    putJsonObject("groupData") {
                        put("skipLeaders", skipLeaders)
                        put("skipAlreadySent", skipAlreadySent)
                        val scanned = scannedGroup
                        if (scanned != null) {
                            val link = groupLink.trim()
                            put("mode", "link")
                            put("groupLink", if (link.startsWith("http")) link else "https://$link")
                            put("groupId", scanned.groupId)
                            put("groupName", scanned.name)
                        } else {
                            put("mode", "joined")
                            put("groupId", groupId)
                        }
                    }
                }
                CampaignSource.CRM -> {
                    put("dataSource", "crm")
                    putJsonObject("crmSource") {
                        put("snapshotId", crmSnapshot["id"].element)
                        put("checksum", crmSnapshot["checksum"].element)
                        put("count", crmSnapshot["count"].element)
                        put("filters", crmFilters.json)
                    }
                }
            }
        }
        scope.launch {
            try {
                ZaloService.createCampaign(config.toString())
                ToastCenter.show(tr("Đã tạo chiến dịch thành công!"))
                navigator.campaignsVersion++
                navigator.pop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                submitError = e.message ?: tr("Không thể tạo chiến dịch")
                ToastCenter.show(submitError!!, isError = true)
            }
            submitting = false
        }
    }

    ZaloTopBar(title = "Tạo chiến dịch", onBack = { close() })

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ZaloSection(title = "Tên chiến dịch") {
                FutaInput(name, { name = it.take(120) }, placeholder = tr("Nhập tên chiến dịch"), enabled = !submitting)
            }

            ZaloSection(title = "Nguồn dữ liệu") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampaignSource.entries.forEach { option ->
                        ZaloChip(option.title, selected = source == option, enabled = !loadingSource && !scanning && !previewing && !submitting,
                            onClick = { source = option })
                    }
                }
                when {
                    loadingSource -> FutaSkeletonBlock(height = 70.dp, radius = 10.dp)
                    sourceError != null -> ZaloInlineError(sourceError!!) { sourceReload++ }
                    source == CampaignSource.LIST -> {
                        Text(tr("Danh sách người nhận ({0})", recipients.size), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                        FutaInput(recipientName, { recipientName = it.take(200) }, placeholder = tr("Tên"))
                        FutaInput(recipientPhone, { recipientPhone = it.take(20) }, placeholder = tr("Số điện thoại"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                        FutaInput(recipientNote, { recipientNote = it.take(1000) }, placeholder = tr("Ghi chú (\${custom1})"))
                        FutaButton("Thêm người nhận", onClick = {
                            recipients = recipients + RecipientEntry(System.nanoTime(), recipientName.trim(), recipientPhone.trim(), recipientNote)
                            recipientName = ""; recipientPhone = ""; recipientNote = ""
                        }, variant = FutaButtonVariant.MINT, icon = Icons.Default.PersonAdd,
                            enabled = recipientPhone.count { it.isDigit() } >= 8 && recipients.size < 10_000, modifier = Modifier.fillMaxWidth())
                        recipients.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    VerbatimText(r.name.ifEmpty { r.phone }, fontSize = 14.sp, color = FutaColors.Navy)
                                    VerbatimText(r.phone, fontSize = 12.sp, color = FutaColors.Slate)
                                    if (r.note.isNotEmpty()) VerbatimText(r.note, fontSize = 12.sp, color = FutaColors.Body)
                                }
                                IconButton(onClick = { recipients = recipients.filterNot { it.id == r.id } }) {
                                    Icon(Icons.Default.Delete, tr("Xóa người nhận"), tint = FutaColors.RedPdf, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (recipients.isNotEmpty()) {
                            Text(tr("Số người nhận: {0}", minOf(recipients.size, maxOf(0, maxItems ?: 0))), fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, color = ZaloBlue)
                        }
                    }
                    source == CampaignSource.GROUP -> {
                        Box {
                            val selected = groups.firstOrNull { it.groupId == groupId }
                            FutaSelectField(
                                title = "Nhóm đã tham gia",
                                displayValue = selected?.let { "${it.name} (${tr("{0} thành viên", it.totalMember)})" }.orEmpty(),
                                placeholder = tr("Chọn nhóm"),
                                onClick = { showGroupMenu = true }
                            )
                            androidx.compose.material3.DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                                if (groups.isEmpty()) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("Chưa có nhóm nào", fontSize = 14.sp) },
                                        enabled = false, onClick = {})
                                }
                                groups.forEach { group ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { VerbatimText("${group.name} (${tr("{0} thành viên", group.totalMember)})", fontSize = 14.sp) },
                                        onClick = {
                                            showGroupMenu = false
                                            groupId = group.groupId
                                            scannedGroup = null
                                            groupLink = ""
                                        }
                                    )
                                }
                            }
                        }
                        FutaInput(groupLink, { groupLink = it; scannedGroup = null; if (it.isNotEmpty()) groupId = "" },
                            placeholder = tr("Hoặc dán link nhóm Zalo"), leadingIcon = Icons.Default.Link,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        ZaloButton("Quét nhóm", onClick = { scanGroup() }, icon = Icons.Default.Search, loading = scanning,
                            enabled = groupLink.isNotBlank(), variant = FutaButtonVariant.OUTLINE, modifier = Modifier.fillMaxWidth())
                        scannedGroup?.let {
                            Text(tr("Đã nhận diện nhóm: {0} ({1} thành viên)", it.name, it.totalMember), fontSize = 12.sp, color = ZaloBlue)
                        }
                        ToggleRow("Bỏ qua trưởng/phó nhóm", skipLeaders, { skipLeaders = it })
                        ToggleRow("Bỏ qua người đã gửi trước đó", skipAlreadySent, { skipAlreadySent = it })
                    }
                    else -> {
                        if (crmBootstrap["workspaces"].array.isEmpty()) {
                            Text("CRM chưa có nhóm khách hàng bạn được phép truy cập.", fontSize = 12.5.sp, color = FutaColors.Slate)
                        } else {
                            val count = crmFilters.activeCount
                            FutaButton(if (count > 0) tr("Bộ lọc CRM ({0})", count) else tr("Bộ lọc CRM"), onClick = {
                                draftCrmFilters = crmFilters; showCrmFilters = true
                            }, variant = FutaButtonVariant.OUTLINE, icon = Icons.Default.FilterList, enabled = !previewing,
                                modifier = Modifier.fillMaxWidth())
                            Text(
                                if (crmSnapshot.isNull) tr("Hãy xem trước để khóa danh sách người nhận")
                                else tr("{0} người trong snapshot", crmSnapshot["count"].int),
                                fontSize = 14.sp, color = FutaColors.Body
                            )
                            if (!crmSnapshot.isNull) {
                                Text(tr("Danh sách bất biến trong 24 giờ; chiến dịch chỉ lấy tối đa {0} người.", maxItems ?: 0),
                                    fontSize = 12.sp, color = FutaColors.Slate)
                            }
                            ZaloButton("Xem trước người nhận", onClick = { previewCrm() }, icon = Icons.Default.Visibility,
                                loading = previewing, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            ZaloSection(title = "Hành vi") {
                ToggleRow("Kết bạn trước khi gửi", addFriend, { addFriend = it },
                    subtitle = tr("Chỉ áp dụng cho tài khoản Zalo; tài khoản WhatsApp bỏ qua bước này."))
                ToggleRow("Ưu tiên người lâu chưa nhắn", sortByOldest, { sortByOldest = it })
            }

            ZaloSection(title = "Thời gian") {
                NumberField("Thời gian chờ (giây)", delayText) { delayText = it }
                NumberField("Số lượng tin nhắn tối đa", maxItemsText) { maxItemsText = it }
                FutaFormSectionField(label = "Khung giờ") {
                    FutaInput(allowedHours, { allowedHours = it.take(200) }, placeholder = tr("Khung giờ: 08:00-12:00,14:00-18:00"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                    Text("Để trống = gửi 24/7", fontSize = 11.5.sp, color = FutaColors.Slate, modifier = Modifier.padding(top = 4.dp))
                }
                NumberField("Giới hạn tin nhắn/ngày", maxDailyText) { maxDailyText = it }
                Text("0 = không giới hạn", fontSize = 11.5.sp, color = FutaColors.Slate)
            }

            ZaloSection(title = "Nội dung tin nhắn") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("\${name}", "\${phone}", "\${custom1}", "\${custom2}").forEach { variable ->
                        ZaloChip(variable, selected = false, verbatim = true, onClick = { template = "$template $variable".take(4000) })
                    }
                }
                FutaTextArea(template, { template = it.take(4000) }, placeholder = tr("Nhập nội dung tin nhắn"), minLines = 4, maxLines = 8)
                Text("Khôi phục mặc định", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ZaloBlue,
                    modifier = Modifier.clickable { template = ZALO_DEFAULT_CAMPAIGN_MESSAGE })
                if (source == CampaignSource.LIST) recipients.firstOrNull()?.let { first ->
                    Text("Xem trước:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                    VerbatimText(
                        template.replace("\${name}", first.name).replace("\${phone}", first.phone).replace("\${custom1}", first.note),
                        fontSize = 13.5.sp, color = FutaColors.Slate
                    )
                }
                // Image: upload, preview, replace, remove.
                if (imageUrl.isNotEmpty()) ZaloCampaignMediaPreview(imageUrl)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZaloButton(
                        if (imageUrl.isEmpty()) "Hình ảnh đính kèm (tùy chọn)" else "Thay ảnh",
                        onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        icon = Icons.Default.AddPhotoAlternate, variant = FutaButtonVariant.OUTLINE, loading = uploading,
                        modifier = Modifier.weight(1f)
                    )
                    if (imageUrl.isNotEmpty()) {
                        FutaButton("Gỡ ảnh", onClick = { imageUrl = "" }, variant = FutaButtonVariant.DANGER, enabled = !uploading)
                    }
                }
            }

            ZaloSection(title = tr("Tài khoản gửi ({0})", selectedAccounts.size)) {
                when {
                    loadingAccounts -> FutaSkeletonBlock(height = 44.dp, radius = 10.dp)
                    accountsError != null -> ZaloInlineError(accountsError!!) { accountsReload++ }
                    accounts.isEmpty() -> Text("Chưa có tài khoản nào được kết nối", fontSize = 13.5.sp, color = FutaColors.Slate)
                    else -> {
                        Text("Chọn tất cả online", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZaloBlue,
                            modifier = Modifier.clickable {
                                selectedAccounts = eligibleAccounts.filter { it.isOnline }.map { it.zaloId }.toSet()
                            })
                        eligibleAccounts.forEach { acc ->
                            val checked = acc.zaloId in selectedAccounts
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                    selectedAccounts = if (checked) selectedAccounts - acc.zaloId else selectedAccounts + acc.zaloId
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                                    tint = if (checked) ZaloBlue else FutaColors.Slate)
                                Column(Modifier.weight(1f)) {
                                    VerbatimText(acc.displayName, fontSize = 14.sp, color = FutaColors.Navy)
                                    Text("${acc.provider.displayName} · ${if (acc.isOnline) tr("Online") else tr("Offline")}",
                                        fontSize = 12.sp, color = FutaColors.Slate)
                                }
                            }
                        }
                        if (accounts.any { it.zaloId in selectedAccounts && !it.isOnline }) {
                            Text("Một số tài khoản đang offline. Tin nhắn sẽ được gửi qua các tài khoản online. Nếu tất cả offline, chiến dịch sẽ tạm dừng.",
                                fontSize = 12.sp, color = Color(0xFFEA580C))
                        }
                    }
                }
            }

            (submitError ?: validationMessage)?.let { message ->
                Text(message, fontSize = 12.5.sp, color = if (submitError == null) FutaColors.Slate else Color(0xFFDC2626))
            }
        }

        FutaStickyActionBar {
            FutaButton("Hủy", onClick = { close() }, variant = FutaButtonVariant.OUTLINE, enabled = !busy, modifier = Modifier.weight(1f))
            ZaloButton("Tạo chiến dịch", onClick = { create() }, loading = submitting,
                enabled = validationMessage == null && !busy && !loadingAccounts, modifier = Modifier.weight(1.4f))
        }
    }

    if (showCrmFilters) {
        ZaloFilterSheet(
            onDismiss = { showCrmFilters = false },
            onReset = { draftCrmFilters = ZaloCampaignCRMFilters() },
            onApply = {
                if (crmFilters != draftCrmFilters) {
                    crmFilters = draftCrmFilters
                    crmSnapshot = JSONValue.Null
                }
            }
        ) {
            CrmFilterFields(crmBootstrap, draftCrmFilters) { draftCrmFilters = it }
        }
    }

    ZaloConfirmDialog(
        visible = showDiscard,
        title = "Hủy tạo chiến dịch?",
        message = "Các thay đổi chưa lưu sẽ bị mất.",
        confirmText = "Bỏ thay đổi",
        cancelText = "Tiếp tục chỉnh sửa",
        onConfirm = { navigator.pop() },
        onDismiss = { showDiscard = false }
    )
}

@Composable
private fun NumberField(title: String, value: String, onChange: (String) -> Unit) {
    FutaFormSectionField(label = title) {
        FutaInput(value, { input -> onChange(input.filter { it.isDigit() }.take(6)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = FutaColors.Body)
            if (subtitle != null) Text(subtitle, fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 15.sp)
        }
        FutaSwitch(checked = checked, onCheckedChange = onChange, activeColor = ZaloBlue)
    }
}

/** CRM audience pickers (iOS ZaloCampaignCRMFilterFields). */
@Composable
private fun CrmFilterFields(bootstrap: JSONValue, filters: ZaloCampaignCRMFilters, onChange: (ZaloCampaignCRMFilters) -> Unit) {
    val workspaces = bootstrap["workspaces"].array
    val pipelines = bootstrap["pipelines"].array
    val seen = mutableSetOf<String>()
    val stages = pipelines.filter { filters.pipelineId.isEmpty() || it["id"].string == filters.pipelineId }
        .flatMap { it["stages"].array }.filter { seen.add(it["id"].string) }

    ZaloPickerField(
        "Nhóm khách",
        listOf("" to tr("Tất cả nhóm được phép")) + workspaces.map { it["id"].string to it["name"].string },
        filters.workspaceId,
        { id ->
            val pipeline = if (id.isEmpty()) filters.pipelineId
            else workspaces.firstOrNull { it["id"].string == id }?.get("pipelineId")?.string.orEmpty()
            onChange(filters.copy(workspaceId = id, pipelineId = pipeline, status = if (id.isEmpty()) filters.status else ""))
        },
        verbatimOptions = true
    )
    ZaloPickerField(
        "Pipeline",
        listOf("" to tr("Tất cả pipeline")) + pipelines.map { it["id"].string to it["name"].string },
        filters.pipelineId,
        { id ->
            val workspace = workspaces.firstOrNull { it["id"].string == filters.workspaceId }
            val keepWorkspace = workspace == null || workspace["pipelineId"].string == id
            onChange(filters.copy(pipelineId = id, workspaceId = if (keepWorkspace) filters.workspaceId else "", status = ""))
        },
        verbatimOptions = true
    )
    ZaloPickerField(
        "Trạng thái",
        listOf("" to tr("Tất cả trạng thái")) + stages.map { it["id"].string to it["label"].string },
        filters.status,
        { onChange(filters.copy(status = it)) },
        verbatimOptions = true
    )
    ZaloPickerField(
        "Người phụ trách",
        listOf("" to tr("Tất cả người phụ trách"), "unassigned" to tr("Chưa phân công")) +
            bootstrap["staff"].array.map { it["id"].string to it["name"].string },
        filters.assigneeId,
        { onChange(filters.copy(assigneeId = it)) },
        verbatimOptions = true
    )
    Text("Nhãn (khách phải có đủ nhãn đã chọn)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
    val labels = bootstrap["labels"].array.map { it.string }
    if (labels.isEmpty()) Text("CRM chưa có nhãn.", fontSize = 13.sp, color = FutaColors.Slate)
    labels.forEach { label ->
        val checked = label in filters.labels
        Row(
            Modifier.fillMaxWidth().clickable {
                onChange(filters.copy(labels = if (checked) filters.labels - label else filters.labels + label))
            }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                tint = if (checked) ZaloBlue else FutaColors.Slate)
            VerbatimText(label, fontSize = 14.sp, color = FutaColors.Body)
        }
    }
}
