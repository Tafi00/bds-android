package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

@Composable
private fun ConversationsSkeleton() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(8) { index ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FutaSkeletonBlock(width = 50.dp, height = 50.dp, radius = 25.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        FutaSkeletonBlock(width = if (index % 2 == 0) 130.dp else 105.dp, height = 15.dp)
                        Spacer(Modifier.weight(1f))
                        FutaSkeletonBlock(width = 35.dp, height = 10.dp)
                    }
                    FutaSkeletonBlock(height = 12.dp)
                }
            }
        }
    }
}

/** Last-message line: placeholders are translated, real text stays verbatim. */
@Composable
private fun previewText(conv: ZaloConversationModel): String {
    if (conv.sendError.isNotEmpty()) return conv.sendError
    val prefix = if (conv.lastMessageIsSelf) tr("Bạn: ") else ""
    return prefix + when (val p = conv.lastMessagePreview) {
        is ZaloPreview.Verbatim -> p.text
        is ZaloPreview.Placeholder -> tr(p.source)
        is ZaloPreview.File -> tr("[Tệp] {0}", p.name)
    }
}

// MARK: - Conversation list

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZaloConversationListView(navigator: ZaloNavigator) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<ZaloConversationModel>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<ZaloAccountModel>>(emptyList()) }
    var customLabels by remember { mutableStateOf<List<ZaloCustomLabelModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var requestId by remember { mutableIntStateOf(0) }
    var loadedFilterKey by remember { mutableStateOf<String?>(null) }

    var selectedFilter by remember { mutableStateOf(ZaloConversationFilter.ALL) }
    var selectedAccount by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<ZaloProvider?>(null) }
    var selectedLabelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchText by remember { mutableStateOf("") }
    var appliedSearch by remember { mutableStateOf("") }

    var labelSheetFor by remember { mutableStateOf<ZaloConversationModel?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }

    fun filterKey() = listOf(
        selectedFilter.raw, selectedAccount, selectedProvider?.raw.orEmpty(), appliedSearch,
        selectedLabelIds.sorted().joinToString(",")
    ).joinToString("|")

    // Port of iOS loadConversations: a refresh re-fetches as many rows as are shown, a filter
    // change starts over, and stale responses (an older request or filter) are dropped.
    suspend fun loadConversations(append: Boolean = false) {
        if (append && (loadingMore || loading || !hasMore)) return
        val key = filterKey()
        val changedFilter = key != loadedFilterKey
        val token = ++requestId
        if (append) loadingMore = true else {
            loading = true
            loadingMore = false
            if (changedFilter) {
                conversations = emptyList(); nextOffset = 0; hasMore = false
            }
        }
        error = null
        val offset = if (append) nextOffset else 0
        val target = if (append || changedFilter) 50 else maxOf(50, nextOffset)
        val filter = selectedFilter
        try {
            val fetched = mutableListOf<ZaloConversationModel>()
            var more: Boolean
            do {
                val result = ZaloService.fetchConversations(
                    limit = minOf(100, target - fetched.size), offset = offset + fetched.size,
                    unreadOnly = filter == ZaloConversationFilter.UNREAD,
                    accountId = selectedAccount.ifEmpty { null }, provider = selectedProvider,
                    search = appliedSearch.ifEmpty { null }, replyStatus = filter,
                    labelIds = selectedLabelIds.ifEmpty { null }
                )
                if (token != requestId || key != filterKey()) return
                fetched += result.conversations
                more = result.hasMore && result.conversations.isNotEmpty()
            } while (more && fetched.size < target)
            conversations = if (append) {
                val seen = conversations.map { it.id }.toMutableSet()
                conversations + fetched.filter { seen.add(it.id) }
            } else fetched
            nextOffset = offset + fetched.size
            hasMore = more
            loadedFilterKey = key
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (token != requestId) return
            error = e.message ?: tr("Không thể tải hội thoại")
        }
        if (token != requestId) return
        loading = false
        loadingMore = false
    }

    suspend fun loadData() {
        loading = true
        error = null
        try {
            coroutineScope {
                val accountsTask = async { ZaloService.fetchAccounts() }
                val labelsTask = async { ZaloService.fetchLabels() }
                accounts = accountsTask.await()
                customLabels = labelsTask.await()
            }
            loadConversations()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: tr("Không thể tải hội thoại")
            loading = false
        }
    }

    fun reloadConversations() {
        scope.launch { loadConversations() }
    }

    LaunchedEffect(Unit) { loadData() }
    LaunchedEffect(navigator.conversationsVersion, navigator.labelsVersion) {
        if (loadedFilterKey != null) {
            customLabels = runCatching { ZaloService.fetchLabels() }.getOrDefault(customLabels)
            selectedLabelIds = selectedLabelIds.filter { id -> customLabels.any { it.id == id } }.toSet()
            loadConversations()
        }
    }
    LaunchedEffect(Unit) {
        var pending: Job? = null
        ZaloWebSocketManager.events.collect { event ->
            when (event) {
                // Bursts of messages reload the list once.
                is ZaloEvent.NewMessage, is ZaloEvent.AliasesSynced -> {
                    pending?.cancel()
                    pending = launch { delay(400); loadConversations() }
                }
                is ZaloEvent.AccountsRefresh, is ZaloEvent.AccountStatus, is ZaloEvent.AccountsInitFailed ->
                    launch { loadData() }
                else -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Search
            FutaInput(
                value = searchText,
                onValueChange = {
                    searchText = it
                    if (it.isEmpty() && appliedSearch.isNotEmpty()) {
                        appliedSearch = ""
                        reloadConversations()
                    }
                },
                placeholder = tr("Tìm theo tên hoặc số điện thoại"),
                leadingIcon = Icons.Default.Search,
                trailingIcon = if (searchText.isNotEmpty()) {
                    {
                        Icon(Icons.Default.Close, tr("Xoá tìm kiếm"), tint = FutaColors.Slate,
                            modifier = Modifier.size(18.dp).clickable {
                                searchText = ""
                                if (appliedSearch.isNotEmpty()) { appliedSearch = ""; reloadConversations() }
                            })
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { appliedSearch = searchText.trim(); reloadConversations() }),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Reply-status filters
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZaloConversationFilter.entries.forEach { filter ->
                    ZaloChip(
                        text = filter.title,
                        selected = selectedFilter == filter,
                        icon = when (filter) {
                            ZaloConversationFilter.ALL -> Icons.Default.Inbox
                            ZaloConversationFilter.UNREAD -> Icons.Default.MarkEmailUnread
                            ZaloConversationFilter.AWAITING_REPLY -> Icons.Default.NorthEast
                            ZaloConversationFilter.NEEDS_RESPONSE -> Icons.Default.SouthWest
                        },
                        onClick = { selectedFilter = filter; reloadConversations() }
                    )
                }
            }

            // Account & provider selectors
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    val accountTitle = if (selectedAccount.isEmpty()) tr("Tất cả tài khoản")
                    else accounts.firstOrNull { it.zaloId == selectedAccount }?.displayName ?: tr("Tài khoản")
                    SelectorPill(Icons.Default.People, accountTitle) { showAccountMenu = true }
                    DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                        DropdownMenuItem(text = { Text("Tất cả tài khoản", fontSize = 14.sp) }, onClick = {
                            showAccountMenu = false; selectedAccount = ""; reloadConversations()
                        })
                        accounts.filter { selectedProvider == null || it.provider == selectedProvider }.forEach { acc ->
                            DropdownMenuItem(text = { VerbatimText(acc.displayName, fontSize = 14.sp) }, onClick = {
                                showAccountMenu = false; selectedAccount = acc.zaloId; reloadConversations()
                            })
                        }
                    }
                }
                Box {
                    SelectorPill(Icons.Default.Forum, selectedProvider?.displayName ?: tr("Tất cả nền tảng")) { showProviderMenu = true }
                    DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }) {
                        DropdownMenuItem(text = { Text("Tất cả nền tảng", fontSize = 14.sp) }, onClick = {
                            showProviderMenu = false; selectedProvider = null; selectedAccount = ""; reloadConversations()
                        })
                        ZaloProvider.entries.forEach { provider ->
                            DropdownMenuItem(text = { VerbatimText(provider.displayName, fontSize = 14.sp) }, onClick = {
                                showProviderMenu = false; selectedProvider = provider; selectedAccount = ""; reloadConversations()
                            })
                        }
                    }
                }
            }

            // Custom labels
            if (customLabels.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    customLabels.forEach { label ->
                        val selected = label.id in selectedLabelIds
                        ZaloChip(
                            text = label.name, selected = selected, verbatim = true, color = label.color, dotColor = label.color,
                            onClick = {
                                selectedLabelIds = if (selected) selectedLabelIds - label.id else selectedLabelIds + label.id
                                reloadConversations()
                            }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(color = FutaColors.PanelDivider)

            when {
                loading && conversations.isEmpty() -> ConversationsSkeleton()
                error != null && conversations.isEmpty() -> ZaloErrorState(error!!, onRetry = { scope.launch { loadData() } })
                conversations.isEmpty() -> {
                    val filtered = appliedSearch.isNotEmpty() || selectedFilter != ZaloConversationFilter.ALL ||
                        selectedAccount.isNotEmpty() || selectedProvider != null || selectedLabelIds.isNotEmpty()
                    FutaEmptyState(
                        title = "Không có cuộc trò chuyện",
                        message = if (filtered) "Không tìm thấy cuộc trò chuyện phù hợp." else "Chưa có tin nhắn nào trong hộp thư.",
                        icon = Icons.Default.ChatBubbleOutline,
                        actionButton = if (filtered) {
                            {
                                FutaButton("Xoá bộ lọc", variant = FutaButtonVariant.OUTLINE, height = 40.dp, onClick = {
                                    searchText = ""; appliedSearch = ""; selectedFilter = ZaloConversationFilter.ALL
                                    selectedAccount = ""; selectedProvider = null; selectedLabelIds = emptySet()
                                    reloadConversations()
                                })
                            }
                        } else null
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conv = conv,
                            account = accounts.firstOrNull { it.zaloId == conv.accountId && it.provider == conv.provider },
                            onOpen = { navigator.push(ZaloRoute.Chat(conv)) },
                            onToggleRead = {
                                scope.launch {
                                    try {
                                        if (conv.unreadCount > 0) ZaloService.markAsRead(conv.accountId, conv.threadId, conv.provider)
                                        else ZaloService.markAsUnread(conv.accountId, conv.threadId, conv.provider)
                                        loadConversations()
                                    } catch (e: Exception) {
                                        ToastCenter.show(e.message ?: tr("Không thể cập nhật hội thoại"), isError = true)
                                    }
                                }
                            },
                            onLabels = { labelSheetFor = conv }
                        )
                        HorizontalDivider(color = FutaColors.PanelDivider, modifier = Modifier.padding(start = 78.dp))
                    }
                    error?.let { msg ->
                        item { Box(Modifier.padding(16.dp)) { ZaloInlineError(msg) { scope.launch { loadConversations(append = hasMore) } } } }
                    }
                    if (hasMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                ZaloButton("Tải thêm hội thoại", onClick = { scope.launch { loadConversations(append = true) } },
                                    variant = FutaButtonVariant.OUTLINE, loading = loadingMore, enabled = !loading, height = 40.dp,
                                    modifier = Modifier.width(220.dp))
                            }
                        }
                    }
                }
            }
        }

        // New conversation (iOS toolbar "square.and.pencil").
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(54.dp)
                .futaDropShadow(shape = CircleShape, color = Color(0x33061D3D), blur = 12.dp, offsetY = 4.dp)
                .clip(CircleShape).background(ZaloBlue)
                .clickable { navigator.push(ZaloRoute.NewChat(accounts)) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Edit, tr("Cuộc trò chuyện mới"), tint = Color.White)
        }
    }

    labelSheetFor?.let { conv ->
        ZaloConversationLabelSheet(conv, navigator) { labelSheetFor = null }
    }
}

@Composable
private fun SelectorPill(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9)).clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = FutaColors.Slate, modifier = Modifier.size(14.dp))
        VerbatimText(title, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = FutaColors.Slate, modifier = Modifier.size(14.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv: ZaloConversationModel,
    account: ZaloAccountModel?,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onLabels: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val unread = conv.unreadCount > 0
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { showMenu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                ZaloAvatar(conv.userAvatar, conv.userName, 50.dp, conv.provider.brandColor)
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(17.dp).clip(CircleShape)
                        .background(Color.White).padding(1.5.dp).clip(CircleShape).background(conv.provider.brandColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (conv.provider == ZaloProvider.ZALO) Icons.AutoMirrored.Filled.Chat else Icons.Default.Phone, null,
                        tint = Color.White, modifier = Modifier.size(9.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VerbatimText(conv.userName, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val time = ZaloDates.listTime(conv.lastMessageTime)
                    if (time.isNotEmpty()) VerbatimText(time, fontSize = 11.sp, color = FutaColors.Slate)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VerbatimText(
                        previewText(conv), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = when {
                            conv.sendError.isNotEmpty() -> Color(0xFFDC2626)
                            unread -> FutaColors.Navy
                            else -> FutaColors.Slate
                        },
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (unread) {
                        VerbatimText("${conv.unreadCount}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.clip(CircleShape).background(Color(0xFF3B82F6)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (account?.isOnline == true) Color(0xFF059669) else FutaColors.Slate))
                    VerbatimText(account?.displayName ?: conv.accountId, fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (account?.isOnline == false) Text("Offline", fontSize = 11.sp, color = FutaColors.Slate)
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (unread) "Đánh dấu đã đọc" else "Đánh dấu chưa đọc", fontSize = 14.sp) },
                leadingIcon = { Icon(if (unread) Icons.Default.Drafts else Icons.Default.MarkEmailUnread, null) },
                onClick = { showMenu = false; onToggleRead() }
            )
            DropdownMenuItem(
                text = { Text("Gán nhãn", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                onClick = { showMenu = false; onLabels() }
            )
        }
    }
}

// MARK: - New conversation

@Composable
fun ZaloNewChatScreen(initialAccounts: List<ZaloAccountModel>, navigator: ZaloNavigator) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(initialAccounts) }
    var selectedAccountId by remember { mutableStateOf(initialAccounts.firstOrNull()?.zaloId.orEmpty()) }
    var phoneInput by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var foundUser by remember { mutableStateOf<JSONValue?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var contacts by remember { mutableStateOf<List<ZaloContactModel>>(emptyList()) }
    var loadingContacts by remember { mutableStateOf(false) }
    var contactsError by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var contactSearch by remember { mutableStateOf("") }
    var startingId by remember { mutableStateOf<String?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.zaloId == selectedAccountId } ?: accounts.firstOrNull()

    LaunchedEffect(Unit) {
        if (accounts.isEmpty()) {
            accounts = runCatching { ZaloService.fetchAccounts() }.getOrDefault(emptyList())
            if (selectedAccountId.isEmpty()) selectedAccountId = accounts.firstOrNull()?.zaloId.orEmpty()
        }
    }

    suspend fun loadContacts() {
        val acc = selectedAccount ?: return
        loadingContacts = true
        contactsError = null
        try {
            contacts = ZaloService.fetchCachedContacts(acc.zaloId, acc.provider)
        } catch (e: Exception) {
            contactsError = e.message ?: tr("Không thể tải danh bạ")
        }
        loadingContacts = false
    }

    LaunchedEffect(selectedAccount?.zaloId) { loadContacts() }

    fun searchByPhone() {
        val acc = selectedAccount ?: return
        val phone = phoneInput.trim()
        if (phone.isEmpty() || searching) return
        searching = true
        searchError = null
        foundUser = null
        scope.launch {
            try {
                foundUser = ZaloService.findUserByPhone(acc.zaloId, phone, acc.provider)
            } catch (e: Exception) {
                searchError = "Không tìm thấy người dùng với số điện thoại này."
            }
            searching = false
        }
    }

    fun sync() {
        val acc = selectedAccount ?: return
        if (syncing) return
        syncing = true
        scope.launch {
            try {
                ZaloService.syncContacts(acc.zaloId, acc.provider)
                ToastCenter.show(tr("Đồng bộ danh bạ thành công!"))
                loadContacts()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể đồng bộ danh bạ"), isError = true)
            }
            syncing = false
        }
    }

    // A first greeting creates the thread, as on iOS.
    fun startChat(zaloId: String) {
        val acc = selectedAccount ?: return
        if (zaloId.isEmpty() || startingId != null) return
        startingId = zaloId
        scope.launch {
            try {
                ZaloService.sendMessage(acc.zaloId, zaloId, "Xin chào!", acc.provider)
                navigator.conversationsVersion++
                navigator.pop()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể gửi tin nhắn"), isError = true)
            }
            startingId = null
        }
    }

    ZaloTopBar(title = "Cuộc trò chuyện mới", onBack = { navigator.pop() })

    if (accounts.isEmpty()) {
        FutaEmptyState(title = "Chưa có tài khoản nào được kết nối", message = "Quét mã QR để liên kết Zalo hoặc WhatsApp",
            icon = Icons.Default.PersonOff)
        return
    }

    val filteredContacts = if (contactSearch.isBlank()) contacts else contacts.filter {
        it.displayName.contains(contactSearch.trim(), ignoreCase = true) || it.phoneNumber.contains(contactSearch.trim())
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (accounts.size > 1) {
            item {
                Box {
                    FutaSelectField(
                        title = tr("Gửi từ tài khoản"),
                        displayValue = selectedAccount?.let { "${it.displayName} (${it.provider.displayName})" }.orEmpty(),
                        onClick = { showAccountMenu = true }
                    )
                    DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { VerbatimText("${acc.displayName} (${acc.provider.displayName})", fontSize = 14.sp) },
                                onClick = {
                                    showAccountMenu = false
                                    selectedAccountId = acc.zaloId
                                    foundUser = null
                                    searchError = null
                                })
                        }
                    }
                }
            }
        }
        item {
            ZaloSection(title = "Tìm kiếm bằng số điện thoại") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutaInput(
                        value = phoneInput, onValueChange = { phoneInput = it },
                        placeholder = tr("Nhập số điện thoại (ví dụ: 0901234567)"), leadingIcon = Icons.Default.Phone,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchByPhone() }),
                        modifier = Modifier.weight(1f)
                    )
                    ZaloButton("Tìm", onClick = { searchByPhone() }, loading = searching, enabled = phoneInput.isNotBlank(),
                        modifier = Modifier.width(76.dp), height = 48.dp)
                }
                searchError?.let { Text(it, fontSize = 12.sp, color = Color(0xFFDC2626)) }
                foundUser?.takeIf { !it.isNull }?.let { user ->
                    // The backend returns zca-js fields (uid / display_name / zalo_name).
                    val zaloId = user["uid"].string.ifEmpty { user["zaloId"].string }
                    val name = user["display_name"].string.ifEmpty { user["zalo_name"].string }
                        .ifEmpty { user["displayName"].string }.ifEmpty { phoneInput }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FutaColors.MintBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ZaloAvatar(user["avatar"].string, name, 48.dp, FutaColors.BrandGreen)
                        Column(Modifier.weight(1f)) {
                            VerbatimText(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                            VerbatimText(phoneInput, fontSize = 12.sp, color = FutaColors.Slate)
                        }
                        ZaloButton("Nhắn tin", onClick = { startChat(zaloId) }, loading = startingId == zaloId,
                            enabled = zaloId.isNotEmpty() && startingId == null, height = 38.dp, modifier = Modifier.width(110.dp))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(tr("Danh bạ tài khoản ({0})", contacts.size).uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    color = FutaColors.Slate, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !syncing) { sync() }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (syncing) ZaloSpinner(size = 12.dp, color = FutaColors.BrandGreen)
                    else Icon(Icons.Default.Sync, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                    Text("Đồng bộ", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
                }
            }
        }
        if (contacts.isNotEmpty()) {
            item {
                FutaInput(value = contactSearch, onValueChange = { contactSearch = it }, placeholder = tr("Tìm trong danh bạ"),
                    leadingIcon = Icons.Default.Search)
            }
        }
        when {
            loadingContacts -> items(6) { FutaAdminRowSkeleton() }
            contactsError != null -> item { ZaloInlineError(contactsError!!) { scope.launch { loadContacts() } } }
            contacts.isEmpty() -> item {
                Text("Chưa có danh bạ. Bấm 'Đồng bộ' để tải danh bạ từ Zalo.", fontSize = 12.5.sp, color = FutaColors.Slate,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            filteredContacts.isEmpty() -> item {
                Text("Không tìm thấy liên hệ phù hợp.", fontSize = 12.5.sp, color = FutaColors.Slate,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            else -> items(filteredContacts, key = { it.id }) { contact ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = startingId == null) { startChat(contact.zaloId) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ZaloAvatar(contact.avatar, contact.displayName, 40.dp)
                    Column(Modifier.weight(1f)) {
                        VerbatimText(contact.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (contact.phoneNumber.isNotEmpty()) VerbatimText(contact.phoneNumber, fontSize = 12.sp, color = FutaColors.Slate)
                    }
                    if (startingId == contact.zaloId) ZaloSpinner(color = FutaColors.BrandGreen)
                    else Icon(Icons.AutoMirrored.Filled.Chat, tr("Nhắn tin"), tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
