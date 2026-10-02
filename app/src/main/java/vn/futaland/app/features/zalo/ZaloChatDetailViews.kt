package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Patterns
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.futaland.app.core.network.APIError
import vn.futaland.app.designsystem.*
import java.io.ByteArrayOutputStream

private const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024
private val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

/** File staged in the composer, sent with `send-with-file`. */
class ZaloPendingAttachment(val data: ByteArray, val filename: String, val mimeType: String) {
    val preview: Bitmap? by lazy {
        if (mimeType.startsWith("image/")) runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull() else null
    }
}

/**
 * Reads a picked file. Images in formats the backend refuses (HEIC…) are re-encoded as JPEG;
 * anything over the 10 MB server limit is rejected before upload.
 */
private suspend fun readAttachment(context: Context, uri: Uri): ZaloPendingAttachment = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var name = "file"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(0)?.let { name = it }
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    if (size > MAX_ATTACHMENT_BYTES) throw APIError(0, tr("Tệp vượt quá 10 MB"))
    val ext = name.substringAfterLast('.', "").lowercase()
    var mime = resolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    var bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw APIError(0, tr("Không thể đọc tệp đã chọn"))
    if (mime.startsWith("image/") && mime !in SUPPORTED_IMAGE_TYPES) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw APIError(0, tr("Không thể đọc ảnh đã chọn"))
        bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        bitmap.recycle()
        mime = "image/jpeg"
        name = name.substringBeforeLast('.') + ".jpg"
    }
    if (bytes.size > MAX_ATTACHMENT_BYTES) throw APIError(0, tr("Tệp vượt quá 10 MB"))
    ZaloPendingAttachment(bytes, name, mime)
}

internal fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        ToastCenter.show(tr("Không có ứng dụng để mở liên kết này"), isError = true)
    } catch (_: Exception) {
        ToastCenter.show(tr("Không thể mở liên kết"), isError = true)
    }
}

internal fun shareUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Merge by id (keeping the recalled state), then order by time like iOS ZaloChatPresentation. */
private fun mergeMessages(existing: List<ZaloMessageModel>, incoming: List<ZaloMessageModel>): List<ZaloMessageModel> {
    val positions = HashMap<String, Int>()
    val result = ArrayList<ZaloMessageModel>(existing.size + incoming.size)
    for (message in existing + incoming) {
        val index = positions[message.id]
        if (index != null) {
            if (!result[index].isUndone || message.isUndone) result[index] = message
        } else {
            positions[message.id] = result.size
            result += message
        }
    }
    return result.sortedWith(compareBy<ZaloMessageModel> { ZaloDates.epoch(it.timestamp) }.thenBy { it.id })
}

/** User text with tappable links (iOS renders it through MarkdownFormatter). */
private fun linkedText(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val matcher = Patterns.WEB_URL.matcher(text)
    var last = 0
    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()
        append(text.substring(last, start))
        val raw = text.substring(start, end)
        val url = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
            append(raw)
        }
        last = end
    }
    append(text.substring(last))
}

// MARK: - Chat detail

@Composable
fun ZaloChatDetailScreen(conversation: ZaloConversationModel, navigator: ZaloNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf<List<ZaloMessageModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var messageError by remember { mutableStateOf<String?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var hasOlder by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var account by remember { mutableStateOf<ZaloAccountModel?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ZaloPendingAttachment?>(null) }
    var loadingAttachment by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<ZaloMediaImageItem?>(null) }
    var customerTyping by remember { mutableStateOf(false) }
    var showInspector by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var messageToRecall by remember { mutableStateOf<ZaloMessageModel?>(null) }
    // The inspector can rename the customer; the header follows.
    var headerConversation by remember { mutableStateOf(conversation) }

    val canSend = ZaloAccess.canSend
    val canCompose = canSend && account?.isOnline == true
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }

    suspend fun scrollToBottom(animated: Boolean = true) {
        val count = listState.layoutInfo.totalItemsCount
        if (count == 0) return
        if (animated) listState.animateScrollToItem(count - 1) else listState.scrollToItem(count - 1)
    }

    suspend fun loadMessages() {
        loading = true
        messageError = null
        val initial = messages.isEmpty()
        try {
            val latest = ZaloService.fetchMessages(conversation.accountId, conversation.threadId, conversation.provider)
            messages = mergeMessages(messages, latest)
            if (initial) hasOlder = latest.size >= 50
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageError = e.message ?: tr("Không thể tải tin nhắn")
            if (messages.isNotEmpty()) ToastCenter.show(messageError!!, isError = true)
        }
        loading = false
    }

    suspend fun loadOlder() {
        val oldest = messages.firstOrNull() ?: return
        if (loadingOlder || !hasOlder) return
        loadingOlder = true
        historyError = null
        try {
            val older = ZaloService.fetchMessages(conversation.accountId, conversation.threadId, conversation.provider, before = oldest.timestamp)
            val before = messages.size
            val anchorIndex = listState.firstVisibleItemIndex
            messages = mergeMessages(messages, older)
            val added = messages.size - before
            hasOlder = older.size >= 50 && added > 0
            if (added > 0) listState.scrollToItem(anchorIndex + added)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            historyError = e.message ?: tr("Không thể tải tin nhắn cũ hơn")
        }
        loadingOlder = false
    }

    suspend fun loadAccount() {
        try {
            account = ZaloService.fetchAccounts(conversation.provider).firstOrNull { it.zaloId == conversation.accountId }
            accountError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            accountError = e.message ?: tr("Không thể tải tài khoản")
            account = null
        }
    }

    suspend fun markRead() {
        try {
            ZaloService.markAsRead(conversation.accountId, conversation.threadId, conversation.provider)
            navigator.conversationsVersion++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToastCenter.show(e.message ?: tr("Không thể đánh dấu đã đọc"), isError = true)
        }
    }

    LaunchedEffect(Unit) {
        launch { loadAccount() }
        loadMessages()
        scrollToBottom(animated = false)
        markRead()
        if (ZaloAccess.canSend) {
            suggestions = ZaloService.fetchMessageSuggestions(conversation.accountId, conversation.threadId, conversation.provider)
        }
    }

    LaunchedEffect(Unit) {
        var typingJob: Job? = null
        ZaloWebSocketManager.events.collect { event ->
            when (event) {
                is ZaloEvent.NewMessage -> {
                    val msg = event.message
                    if (msg.threadId == conversation.threadId && msg.accountId == conversation.accountId &&
                        messages.none { it.id == msg.id }
                    ) {
                        val follow = isAtBottom || msg.isSelf
                        messages = messages + msg
                        if (!msg.isSelf) customerTyping = false
                        if (follow) launch { delay(50); scrollToBottom() }
                        launch { markRead() }
                    }
                }
                is ZaloEvent.MessageUndone -> {
                    val index = messages.indexOfFirst { it.msgId == event.msgId || it.id == event.msgId }
                    if (index >= 0) {
                        val target = messages[index]
                        if (previewImage != null && target.mediaImages.any { it == previewImage }) previewImage = null
                        messages = messages.toMutableList().also { it[index] = ZaloMessageModel(target.raw.updating("isUndone", true)) }
                    }
                }
                is ZaloEvent.Typing -> {
                    if (event.threadId == conversation.threadId && event.accountId == conversation.accountId) {
                        customerTyping = event.isTyping
                        typingJob?.cancel()
                        if (event.isTyping) typingJob = launch { delay(3000); customerTyping = false }
                    }
                }
                is ZaloEvent.AccountStatus, ZaloEvent.AccountsRefresh -> launch { loadAccount() }
                else -> Unit
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            loadingAttachment = true
            scope.launch {
                try {
                    pending = readAttachment(context, uri)
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể đọc ảnh hoặc video đã chọn"), isError = true)
                }
                loadingAttachment = false
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loadingAttachment = true
            scope.launch {
                try {
                    pending = readAttachment(context, uri)
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể đọc tệp đã chọn"), isError = true)
                }
                loadingAttachment = false
            }
        }
    }

    fun sendDraft() {
        val text = input.trim()
        val attachment = pending
        if (!canCompose || sending || loadingAttachment || (text.isEmpty() && attachment == null)) return
        sending = true
        scope.launch {
            try {
                val result = if (attachment != null) {
                    ZaloService.sendFileMessage(conversation.accountId, conversation.threadId, attachment.data,
                        attachment.filename, attachment.mimeType, text, conversation.provider)
                } else {
                    ZaloService.sendMessage(conversation.accountId, conversation.threadId, text, conversation.provider)
                }
                result["error"].string.takeIf { it.isNotEmpty() }?.let { throw APIError(0, it) }
                input = ""
                pending = null
                loadMessages()
                scrollToBottom()
                navigator.conversationsVersion++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể gửi tin nhắn"), isError = true)
            }
            sending = false
        }
    }

    fun recall(msg: ZaloMessageModel) {
        scope.launch {
            try {
                ZaloService.undoMessage(conversation.accountId, conversation.threadId, msg.msgId, msg.cliMsgId, conversation.provider)
                messages = messages.map { if (it.id == msg.id) ZaloMessageModel(it.raw.updating("isUndone", true)) else it }
                ToastCenter.show(tr("Đã thu hồi tin nhắn"))
                loadMessages()
                navigator.conversationsVersion++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể thu hồi tin nhắn"), isError = true)
            }
        }
    }

    ZaloTopBar(
        title = headerConversation.userName,
        verbatimTitle = true,
        subtitle = "${conversation.provider.displayName} • ${account?.displayName ?: conversation.accountId}",
        onBack = { navigator.pop() },
        leading = { ZaloAvatar(headerConversation.userAvatar, headerConversation.userName, 34.dp, conversation.provider.brandColor) }
    ) {
        FutaHeaderIconButton(Icons.Default.Label, tr("Gán nhãn"), { showLabels = true }, size = 36.dp)
        FutaHeaderIconButton(Icons.Default.Info, tr("Chi tiết khách hàng"), { showInspector = true }, size = 36.dp)
    }
    HorizontalDivider(color = FutaColors.PanelDivider)

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF5F7FA))) {
            when {
                loading && messages.isEmpty() -> MessagesSkeleton()
                messageError != null && messages.isEmpty() ->
                    ZaloErrorState(messageError!!, onRetry = { scope.launch { loadMessages(); scrollToBottom(false) } })
                messages.isEmpty() -> FutaEmptyState(
                    title = "Chưa có tin nhắn",
                    message = "Chưa có tin nhắn trong cuộc trò chuyện này",
                    icon = Icons.Default.Forum
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasOlder || historyError != null) {
                        item(key = "older") {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (hasOlder) {
                                    if (loadingOlder) ZaloSpinner()
                                    else Text("Tải tin nhắn cũ hơn", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZaloBlue,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { scope.launch { loadOlder() } }.padding(8.dp))
                                }
                                historyError?.let { Text(it, fontSize = 12.sp, color = Color(0xFFDC2626)) }
                            }
                        }
                    }
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (index == 0 || ZaloDates.dayKey(msg.timestamp) != ZaloDates.dayKey(messages[index - 1].timestamp)) {
                                VerbatimText(ZaloDates.dayLabel(msg.timestamp), fontSize = 11.5.sp, color = FutaColors.Slate,
                                    modifier = Modifier.clip(CircleShape).background(FutaColors.TabBg).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            ZaloMessageBubble(
                                message = msg,
                                onImage = { previewImage = it },
                                onRecall = if (msg.isSelf && canSend && msg.msgId.isNotEmpty()) ({ messageToRecall = msg }) else null
                            )
                        }
                    }
                }
            }
        }

        // AI suggestions
        if (suggestions.isNotEmpty() && canCompose) {
            Row(Modifier.fillMaxWidth().background(FutaColors.PageBg).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        Row(
                            Modifier.clip(CircleShape).background(ZaloBlueSoft).clickable(enabled = !sending) { input = suggestion }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = ZaloBlue, modifier = Modifier.size(12.dp))
                            VerbatimText(suggestion, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ZaloBlue, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
                        }
                    }
                }
                IconButton(onClick = { navigator.push(ZaloRoute.SuggestionConfig) }) {
                    Icon(Icons.Default.Tune, tr("Cấu hình AI gợi ý"), tint = FutaColors.Slate, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (customerTyping) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFF1F5F9)).padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZaloSpinner(size = 11.dp)
                Text(tr("{0} đang soạn tin...", headerConversation.userName), fontSize = 12.sp, color = FutaColors.Slate)
            }
        }

        HorizontalDivider(color = FutaColors.PanelDivider)

        val notice: String? = when {
            accountError != null -> accountError
            !canSend -> tr("Bạn chỉ có quyền xem hội thoại")
            account == null && accountError == null && loading -> null
            account?.isOnline != true -> tr("Tài khoản chưa kết nối. Mở Tài khoản để kết nối lại và gửi tin nhắn.")
            else -> null
        }
        if (notice != null) {
            Row(Modifier.fillMaxWidth().background(FutaColors.PageBg).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VerbatimText(notice, fontSize = 12.sp, color = if (accountError != null) Color(0xFFDC2626) else FutaColors.Slate,
                    modifier = Modifier.weight(1f))
                if (accountError != null) {
                    Text("Thử tải tài khoản lại", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZaloBlue,
                        modifier = Modifier.clickable { scope.launch { loadAccount() } }.padding(start = 8.dp))
                }
            }
        }

        pending?.let { attachment ->
            AttachmentDraft(attachment, enabled = !sending) { pending = null }
        }

        // Composer
        Row(
            Modifier.fillMaxWidth().background(FutaColors.CardBg).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val attachEnabled = canCompose && !sending && !loadingAttachment
            IconButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                enabled = attachEnabled) {
                Icon(Icons.Default.Image, tr("Gửi ảnh hoặc video"), tint = if (attachEnabled) ZaloBlue else FutaColors.Slate.copy(alpha = 0.4f))
            }
            IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = attachEnabled) {
                Icon(Icons.Default.AttachFile, tr("Gửi tệp"), tint = if (attachEnabled) ZaloBlue else FutaColors.Slate.copy(alpha = 0.4f))
            }
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF1F5F9))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) Text("Nhập tin nhắn...", fontSize = 14.sp, color = FutaColors.Muted)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(10_000) },
                    enabled = canCompose && !sending,
                    maxLines = 5,
                    textStyle = TextStyle(fontSize = 14.sp, color = FutaColors.Navy),
                    cursorBrush = SolidColor(ZaloBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val sendEnabled = canCompose && !sending && !loadingAttachment && (input.isNotBlank() || pending != null)
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (sending || loadingAttachment) ZaloSpinner(size = 22.dp)
                else Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (sendEnabled) ZaloBlue else Color(0xFFCBD5E1))
                        .clickable(enabled = sendEnabled) { sendDraft() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowUpward, tr("Gửi tin nhắn"), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showLabels) {
        ZaloConversationLabelSheet(conversation, navigator) { showLabels = false }
    }
    if (showInspector) {
        ZaloCustomerInspectorSheet(headerConversation, onDismiss = { showInspector = false }, onSaved = { updated ->
            headerConversation = updated
            navigator.conversationsVersion++
        })
    }
    previewImage?.let { image -> ZaloMediaViewer(image) { previewImage = null } }

    ZaloConfirmDialog(
        visible = messageToRecall != null,
        title = "Thu hồi tin nhắn",
        message = "Tin nhắn sẽ được thu hồi với cả khách hàng. Bạn có chắc chắn?",
        confirmText = "Thu hồi",
        onConfirm = { messageToRecall?.let { recall(it) } },
        onDismiss = { messageToRecall = null }
    )
}

@Composable
private fun MessagesSkeleton() {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(7) { index ->
            val mine = index % 2 == 0
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Column(Modifier.width(230.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                    FutaSkeletonLines(count = if (index % 3 == 0) 3 else 2)
                }
            }
        }
    }
}

@Composable
private fun AttachmentDraft(attachment: ZaloPendingAttachment, enabled: Boolean, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FutaColors.CardBg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val preview = attachment.preview
        if (preview != null) {
            Image(preview.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
        } else {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                Icon(if (attachment.mimeType.startsWith("video/")) Icons.Default.Videocam else Icons.Default.Description, null,
                    tint = FutaColors.Slate)
            }
        }
        Column(Modifier.weight(1f)) {
            VerbatimText(attachment.filename, fontSize = 14.sp, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VerbatimText(formatByteCount(attachment.data.size.toLong()), fontSize = 12.sp, color = FutaColors.Slate)
        }
        IconButton(onClick = onCancel, enabled = enabled) {
            Icon(Icons.Default.Cancel, tr("Hủy tệp đính kèm"), tint = FutaColors.Slate)
        }
    }
}

// MARK: - Message bubble

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZaloMessageBubble(
    message: ZaloMessageModel,
    onImage: (ZaloMediaImageItem) -> Unit,
    onRecall: (() -> Unit)?
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    val mine = message.isSelf
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (message.isUndone) {
            Text("Tin nhắn đã được thu hồi", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = FutaColors.Slate,
                modifier = Modifier.padding(start = if (mine) 24.dp else 0.dp, end = if (mine) 0.dp else 24.dp)
                    .clip(RoundedCornerShape(16.dp)).background(FutaColors.TabBg).padding(12.dp))
            return@Row
        }
        val fg = if (mine) Color.White else FutaColors.Body
        val shareUrl = message.mediaImages.firstOrNull()?.fullUrl ?: message.mediaVideos.firstOrNull()?.videoUrl
            ?: (message.mediaFiles.firstOrNull() ?: message.mediaAudio.firstOrNull())?.fileUrl
        val text = message.textContent
        Box {
            Column(
                Modifier
                    .padding(start = if (mine) 24.dp else 0.dp, end = if (mine) 0.dp else 24.dp)
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (mine) ZaloBlue else Color.White)
                    .then(if (mine) Modifier else Modifier.border(1.dp, FutaColors.PanelDivider, RoundedCornerShape(16.dp)))
                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                message.quote?.let { quote ->
                    Row(Modifier.height(IntrinsicSize.Min).clip(RoundedCornerShape(6.dp))
                        .background(if (mine) Color.White.copy(alpha = 0.15f) else FutaColors.PageBg)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(Color(0xFF3B82F6)))
                        Column(Modifier.padding(8.dp)) {
                            VerbatimText(quote.fromD, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
                            VerbatimText(quote.msg, fontSize = 12.sp, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                message.mediaImages.forEach { image ->
                    MediaImage(image.thumbUrl.ifEmpty { image.fullUrl }, Modifier.clickable { onImage(image) })
                }
                message.mediaVideos.forEach { video ->
                    Column(Modifier.clickable { openExternal(context, video.videoUrl) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            MediaImage(video.thumbUrl)
                            Icon(Icons.Default.PlayCircle, tr("Phát video"), tint = Color.White, modifier = Modifier.size(44.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Videocam, null, tint = fg, modifier = Modifier.size(14.dp))
                            VerbatimText(video.title, fontSize = 12.sp, color = fg, maxLines = 2)
                        }
                    }
                }
                message.mediaAudio.forEach { audio -> AudioAttachment(audio, fg) }
                message.mediaFiles.forEach { file ->
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { openExternal(context, file.fileUrl) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (file.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description, null, tint = fg, modifier = Modifier.size(26.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            VerbatimText(file.fileName, fontSize = 13.5.sp, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (file.formattedSize.isNotEmpty()) VerbatimText(file.formattedSize, fontSize = 11.sp, color = fg.copy(alpha = 0.75f))
                        }
                        Icon(Icons.Default.OpenInNew, tr("Mở tệp"), tint = fg, modifier = Modifier.size(16.dp))
                    }
                }
                message.linkCard?.let { link ->
                    Column(Modifier.clickable { openExternal(context, link.href) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (link.thumbUrl.isNotEmpty()) MediaImage(link.thumbUrl)
                        if (link.title.isNotEmpty()) VerbatimText(link.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 2)
                        if (link.description.isNotEmpty()) VerbatimText(link.description, fontSize = 12.sp, color = fg, maxLines = 3)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Link, null, tint = fg, modifier = Modifier.size(13.dp))
                            VerbatimText(Uri.parse(link.href).host ?: link.href, fontSize = 11.sp, color = fg, maxLines = 1)
                        }
                    }
                }
                if (text.isNotEmpty()) {
                    Text(linkedText(text, if (mine) Color.White else ZaloBlue), fontSize = 15.sp, color = fg, lineHeight = 20.sp)
                }
                VerbatimText(ZaloDates.time(message.timestamp), fontSize = 10.5.sp, color = fg.copy(alpha = 0.7f),
                    textAlign = TextAlign.End, modifier = Modifier.align(Alignment.End))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (shareUrl != null) {
                    DropdownMenuItem(
                        text = { Text("Chia sẻ", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { showMenu = false; shareUrl(context, shareUrl) }
                    )
                }
                if (text.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sao chép", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            showMenu = false
                            clipboard.setText(AnnotatedString(text))
                            ToastCenter.show(tr("Đã sao chép tin nhắn"))
                        }
                    )
                }
                if (onRecall != null) {
                    HorizontalDivider(color = FutaColors.PanelDivider)
                    DropdownMenuItem(
                        text = { Text("Thu hồi tin nhắn", fontSize = 14.sp, color = Color(0xFFDC2626)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = Color(0xFFDC2626)) },
                        onClick = { showMenu = false; onRecall() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaImage(url: String, modifier: Modifier = Modifier) {
    if (url.isEmpty()) {
        Box(modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE2E8F0)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, null, tint = FutaColors.Slate)
        }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)),
        loading = { FutaSkeletonBlock(height = 150.dp, radius = 8.dp) },
        error = {
            Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFE2E8F0)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.BrokenImage, null, tint = FutaColors.Slate)
            }
        }
    )
}

/** Inline voice-note player (iOS ZaloAudioAttachmentView). */
@Composable
private fun AudioAttachment(audio: ZaloMediaFileItem, fg: Color) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }

    DisposableEffect(audio.fileUrl) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun toggle() {
        val current = player
        if (current != null) {
            if (playing) current.pause() else current.start()
            playing = !playing
            return
        }
        preparing = true
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(audio.fileUrl)
                setOnPreparedListener { it.start(); preparing = false; playing = true }
                setOnCompletionListener { it.seekTo(0); playing = false }
                setOnErrorListener { _, _, _ ->
                    preparing = false; playing = false
                    ToastCenter.show(tr("Không thể phát ghi âm"), isError = true)
                    true
                }
                prepareAsync()
            }
        }.onSuccess { player = it }.onFailure {
            preparing = false
            ToastCenter.show(tr("Không thể phát ghi âm"), isError = true)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = !preparing) { toggle() }, contentAlignment = Alignment.Center) {
            if (preparing) ZaloSpinner(size = 18.dp, color = fg)
            else Icon(if (playing) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                if (playing) tr("Tạm dừng ghi âm") else tr("Phát ghi âm"), tint = fg, modifier = Modifier.size(32.dp))
        }
        VerbatimText(audio.fileName, fontSize = 13.5.sp, color = fg, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Default.OpenInNew, tr("Mở ghi âm"), tint = fg,
            modifier = Modifier.size(16.dp).clickable { openExternal(context, audio.fileUrl) })
    }
}

// MARK: - Media viewer

@Composable
private fun ZaloMediaViewer(image: ZaloMediaImageItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SubcomposeAsyncImage(
                model = image.fullUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ZaloSpinner(size = 28.dp, color = Color.White) } },
                error = {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Text("Không thể tải hình ảnh", color = Color.White.copy(alpha = 0.8f))
                    }
                }
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Đóng"), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { shareUrl(context, image.fullUrl) }) { Icon(Icons.Default.Share, tr("Chia sẻ ảnh"), tint = Color.White) }
                IconButton(onClick = { openExternal(context, image.fullUrl) }) { Icon(Icons.Default.OpenInBrowser, tr("Mở trong trình duyệt"), tint = Color.White) }
            }
            if (image.caption.isNotEmpty()) {
                VerbatimText(image.caption, fontSize = 14.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding().padding(16.dp))
            }
        }
    }
}

// MARK: - Customer inspector

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ZaloCustomerInspectorSheet(
    conversation: ZaloConversationModel,
    onDismiss: () -> Unit,
    onSaved: (ZaloConversationModel) -> Unit
) {
    val scope = rememberCoroutineScope()
    val originalNickname = conversation.userNickname
    val originalPhone = conversation.userPhone
    var nickname by remember { mutableStateOf(originalNickname) }
    var phone by remember { mutableStateOf(originalPhone) }
    var needs by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var originalNeeds by remember { mutableStateOf("") }
    var originalTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var newTag by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val canEdit = ZaloAccess.canSend
    val dirty = nickname != originalNickname || phone != originalPhone || needs != originalNeeds || tags != originalTags

    LaunchedEffect(reload) {
        loading = true
        loadError = null
        try {
            val data = ZaloService.fetchCustomerData(conversation.userId, conversation.accountId, conversation.provider)
            needs = data["customerNeeds"].string
            tags = data["tags"].array.map { it.string }.filter { it.isNotEmpty() }
            originalNeeds = needs
            originalTags = tags
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải thông tin khách hàng")
        }
        loading = false
    }

    fun close() {
        if (saving) return
        if (dirty) confirmDiscard = true else onDismiss()
    }

    fun addTag() {
        val tag = newTag.trim()
        if (tag.isNotEmpty() && tag !in tags && tags.size < 50) tags = tags + tag.take(100)
        newTag = ""
    }

    fun save() {
        if (saving || loading) return
        if (newTag.isNotBlank()) addTag()
        saving = true
        scope.launch {
            try {
                if (nickname != originalNickname) {
                    ZaloService.updateNickname(conversation.userId, nickname.trim(), conversation.accountId, conversation.provider)
                }
                if (phone != originalPhone) {
                    ZaloService.updateUserPhone(conversation.userId, phone.trim(), conversation.accountId, conversation.provider)
                }
                ZaloService.updateCustomerNeeds(conversation.userId, needs, conversation.accountId, conversation.provider)
                ZaloService.updateCustomerTags(conversation.userId, tags, conversation.accountId, conversation.provider)
                ToastCenter.show(tr("Đã lưu thông tin khách hàng"))
                val user = conversation.userRaw.withUpdates(mapOf("nickname" to nickname.trim(), "phoneNumber" to phone.trim()))
                onSaved(ZaloConversationModel(conversation.raw.updating("user", user)))
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể lưu thông tin khách hàng"), isError = true)
            }
            saving = false
        }
    }

    FutaBottomSheet(
        visible = true,
        onDismiss = { close() },
        title = tr("Chi tiết khách hàng"),
        footer = if (canEdit) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FutaButton("Đóng", onClick = { close() }, variant = FutaButtonVariant.OUTLINE, enabled = !saving, modifier = Modifier.weight(1f))
                    ZaloButton("Lưu", onClick = { save() }, loading = saving, enabled = !loading && loadError == null && dirty,
                        modifier = Modifier.weight(1f))
                }
            }
        } else null
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ZaloAvatar(conversation.userAvatar, conversation.userName, 54.dp, conversation.provider.brandColor)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    VerbatimText(conversation.userName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                    VerbatimText("ID: ${conversation.userId}", fontSize = 12.sp, color = FutaColors.Slate)
                    Text(tr("Nền tảng: {0}", conversation.provider.displayName), fontSize = 11.5.sp, color = conversation.provider.brandColor)
                }
            }
            when {
                loading -> FutaSkeletonLines(count = 5)
                loadError != null -> ZaloInlineError(loadError!!) { reload++ }
                else -> {
                    FutaFormSectionField(label = "Biệt danh") {
                        FutaInput(nickname, { nickname = it.take(100) }, placeholder = tr("Nhập biệt danh"), enabled = canEdit && !saving)
                    }
                    FutaFormSectionField(label = "Số điện thoại") {
                        FutaInput(phone, { phone = it.take(32) }, placeholder = tr("Nhập số điện thoại"), enabled = canEdit && !saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    }
                    FutaFormSectionField(label = "Nhu cầu khách hàng") {
                        FutaTextArea(needs, { needs = it.take(5000) },
                            placeholder = tr("Ghi chú nhu cầu khách (ví dụ: Cần căn 2PN Vinhomes, tài chính 3 tỷ...)"),
                            enabled = canEdit && !saving)
                    }
                    FutaFormSectionField(label = "Thẻ phân loại (Tags)") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FutaInput(newTag, { newTag = it }, placeholder = tr("Thêm thẻ mới..."), enabled = canEdit && !saving,
                                keyboardActions = KeyboardActions(onDone = { addTag() }), modifier = Modifier.weight(1f))
                            IconButton(onClick = { addTag() }, enabled = canEdit && !saving && newTag.isNotBlank()) {
                                Icon(Icons.Default.AddCircle, tr("Thêm thẻ"), tint = FutaColors.BrandGreen)
                            }
                        }
                        if (tags.isNotEmpty()) {
                            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                tags.forEach { tag ->
                                    Row(
                                        Modifier.clip(CircleShape).background(Color(0xFF3B82F6).copy(alpha = 0.12f))
                                            .padding(start = 9.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        VerbatimText(tag, fontSize = 12.sp, color = Color(0xFF2563EB))
                                        if (canEdit) {
                                            Icon(Icons.Default.Close, tr("Xoá thẻ {0}", tag), tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(20.dp).clip(CircleShape)
                                                    .clickable(enabled = !saving) { tags = tags - tag }.padding(3.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ZaloConfirmDialog(
        visible = confirmDiscard,
        title = "Bỏ thay đổi chưa lưu?",
        message = "Các thay đổi chưa lưu sẽ bị mất.",
        confirmText = "Bỏ thay đổi",
        cancelText = "Tiếp tục chỉnh sửa",
        onConfirm = onDismiss,
        onDismiss = { confirmDiscard = false }
    )
}

// MARK: - AI suggestion settings

@Composable
fun ZaloSuggestionConfigScreen(navigator: ZaloNavigator) {
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf("") }
    var samples by remember { mutableStateOf<List<String>>(emptyList()) }
    var originalStyle by remember { mutableStateOf("") }
    var originalSamples by remember { mutableStateOf<List<String>>(emptyList()) }
    var newSample by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val dirty = style != originalStyle || samples != originalSamples || newSample.isNotBlank()

    LaunchedEffect(reload) {
        loading = true
        loadError = null
        try {
            val res = ZaloService.fetchSuggestionConfig()
            style = res["styleDescription"].string
            samples = res["sampleMessages"].array.map { it.string }.filter { it.isNotEmpty() }
            originalStyle = style
            originalSamples = samples
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải cấu hình")
        }
        loading = false
    }

    fun close() {
        if (saving) return
        if (dirty) confirmDiscard = true else navigator.pop()
    }

    BackHandler { close() }

    fun save() {
        if (loading || saving || loadError != null) return
        if (style.isBlank()) {
            ToastCenter.show(tr("Vui lòng nhập mô tả phong cách AI"), isError = true)
            return
        }
        val extra = newSample.trim()
        val allSamples = if (extra.isNotEmpty()) samples + extra else samples
        saving = true
        scope.launch {
            try {
                ZaloService.updateSuggestionConfig(style, allSamples)
                ToastCenter.show(tr("Đã lưu cấu hình gợi ý AI"))
                originalStyle = style; samples = allSamples; originalSamples = allSamples; newSample = ""
                navigator.pop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể lưu cấu hình"), isError = true)
            }
            saving = false
        }
    }

    fun resetDefaults() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                ZaloService.resetSuggestionConfig()
                ToastCenter.show(tr("Đã đặt lại cấu hình mặc định"))
                reload++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể đặt lại cấu hình"), isError = true)
            }
            saving = false
        }
    }

    ZaloTopBar(title = "Cấu hình AI Gợi ý", onBack = { close() })

    when {
        loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FutaSkeletonLines(count = 5)
            FutaSkeletonLines(count = 4)
        }
        loadError != null -> ZaloErrorState(loadError!!, onRetry = { reload++ })
        else -> Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ZaloSection(title = "Mô tả phong cách AI") {
                    FutaTextArea(style, { style = it }, placeholder = tr("Ví dụ: Trả lời lịch sự, thân thiện, xưng em, tư vấn nhiệt tình..."),
                        enabled = !saving)
                }
                ZaloSection(title = "Tin nhắn mẫu (để AI học hỏi)") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FutaInput(newSample, { newSample = it }, placeholder = tr("Thêm tin nhắn mẫu..."), enabled = !saving,
                            modifier = Modifier.weight(1f))
                        FutaButton("Thêm", onClick = {
                            val sample = newSample.trim()
                            if (sample.isNotEmpty()) { samples = samples + sample; newSample = "" }
                        }, enabled = newSample.isNotBlank() && !saving, height = 48.dp)
                    }
                    if (samples.isEmpty()) {
                        Text("Chưa có tin nhắn mẫu", fontSize = 12.5.sp, color = FutaColors.Slate)
                    }
                    samples.forEachIndexed { index, sample ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VerbatimText(sample, fontSize = 14.sp, color = FutaColors.Body, modifier = Modifier.weight(1f))
                            IconButton(onClick = { samples = samples.toMutableList().also { it.removeAt(index) } }, enabled = !saving) {
                                Icon(Icons.Default.Delete, tr("Xoá tin nhắn mẫu"), tint = FutaColors.RedPdf, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                // Destructive reset kept apart from Save.
                FutaButton("Đặt lại cấu hình mặc định", onClick = { confirmReset = true }, variant = FutaButtonVariant.OUTLINE,
                    icon = Icons.Default.RestartAlt, enabled = !saving, modifier = Modifier.fillMaxWidth())
            }
            FutaStickyActionBar {
                FutaButton("Đóng", onClick = { close() }, variant = FutaButtonVariant.OUTLINE, enabled = !saving, modifier = Modifier.weight(1f))
                ZaloButton("Lưu", onClick = { save() }, loading = saving, modifier = Modifier.weight(1f))
            }
        }
    }

    ZaloConfirmDialog(
        visible = confirmDiscard,
        title = "Bỏ thay đổi chưa lưu?",
        message = "Các thay đổi chưa lưu sẽ bị mất.",
        confirmText = "Bỏ thay đổi",
        cancelText = "Tiếp tục chỉnh sửa",
        onConfirm = { navigator.pop() },
        onDismiss = { confirmDiscard = false }
    )
    ZaloConfirmDialog(
        visible = confirmReset,
        title = "Khôi phục cấu hình mặc định?",
        message = "Mô tả phong cách và tin nhắn mẫu hiện tại sẽ bị thay bằng cấu hình mặc định.",
        confirmText = "Khôi phục",
        onConfirm = { resetDefaults() },
        onDismiss = { confirmReset = false }
    )
}
