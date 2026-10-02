package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.tr
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.futaland.app.designsystem.*

/** Fire-and-forget clean-up calls that must outlive the screen (cancelling a QR session). */
internal val zaloBackgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// MARK: - Accounts

@Composable
fun ZaloAccountsScreen(navigator: ZaloNavigator) {
    val scope = rememberCoroutineScope()
    val canManage = ZaloAccess.canManageAccounts
    var accounts by remember { mutableStateOf<List<ZaloAccountModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reconnectingId by remember { mutableStateOf<String?>(null) }
    var updatingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var accountToDelete by remember { mutableStateOf<ZaloAccountModel?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    val busy = reconnectingId != null || updatingId != null || deletingId != null

    suspend fun load() {
        loading = true
        error = null
        try {
            accounts = ZaloService.fetchAccounts()
        } catch (e: Exception) {
            error = e.message ?: tr("Không thể tải tài khoản")
        }
        loading = false
    }

    fun changed() {
        navigator.accountsVersion++
        navigator.conversationsVersion++
    }

    LaunchedEffect(navigator.accountsVersion) { load() }
    LaunchedEffect(Unit) {
        ZaloWebSocketManager.events.collect { event ->
            if (event is ZaloEvent.AccountsRefresh || event is ZaloEvent.AccountStatus) load()
        }
    }

    fun reconnect(acc: ZaloAccountModel) {
        if (!canManage || busy) return
        reconnectingId = acc.id
        scope.launch {
            try {
                ZaloService.reconnectAccount(acc.zaloId, acc.provider)
                ToastCenter.show(tr("Đang kết nối lại tài khoản..."))
                delay(2000)
                load()
                changed()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể cập nhật tài khoản"), isError = true)
            }
            reconnectingId = null
        }
    }

    fun updateInbound(acc: ZaloAccountModel, value: Boolean) {
        if (!canManage || busy) return
        updatingId = acc.id
        scope.launch {
            try {
                ZaloService.updateAccountSettings(acc.zaloId, value, acc.provider)
                ToastCenter.show(tr("Đã lưu cài đặt tài khoản"))
                load()
                changed()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể cập nhật tài khoản"), isError = true)
            }
            updatingId = null
        }
    }

    fun delete(acc: ZaloAccountModel) {
        if (!canManage || busy) return
        deletingId = acc.id
        scope.launch {
            try {
                ZaloService.removeAccount(acc.zaloId, acc.provider)
                ToastCenter.show(tr("Đã ngắt kết nối tài khoản {0}", acc.displayName))
                load()
                changed()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không thể cập nhật tài khoản"), isError = true)
            }
            deletingId = null
        }
    }

    ZaloTopBar(title = "Tài khoản nhắn tin", onBack = { if (!busy) navigator.pop() }) {
        if (canManage) {
            Box {
                FutaHeaderIconButton(Icons.Default.Add, tr("Thêm tài khoản nhắn tin"), { showAddMenu = true })
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    ZaloProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(tr("Thêm {0}", provider.displayName), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.QrCode2, null, tint = provider.brandColor) },
                            onClick = { showAddMenu = false; navigator.push(ZaloRoute.QRLogin(provider)) }
                        )
                    }
                }
            }
        }
    }

    when {
        loading && accounts.isEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { FutaAdminRowSkeleton() }
        }
        error != null && accounts.isEmpty() -> ZaloErrorState(error!!, onRetry = { scope.launch { load() } })
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let { msg -> item { ZaloInlineError(msg) { scope.launch { load() } } } }
            if (canManage) {
                item {
                    var showProviders by remember { mutableStateOf(false) }
                    Box {
                        FutaCard(onClick = { showProviders = true }, borderColor = FutaColors.LightBlueBorder) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(26.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Kết nối tài khoản mới", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                                    Text("Quét mã QR để liên kết Zalo hoặc WhatsApp", fontSize = 12.sp, color = FutaColors.Slate)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate)
                            }
                        }
                        DropdownMenu(expanded = showProviders, onDismissRequest = { showProviders = false }) {
                            ZaloProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { VerbatimText(provider.displayName, fontSize = 14.sp) },
                                    onClick = { showProviders = false; navigator.push(ZaloRoute.QRLogin(provider)) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(tr("Tài khoản đã liên kết ({0})", accounts.size).uppercase(), fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold, color = FutaColors.Slate, modifier = Modifier.padding(start = 4.dp))
            }
            if (accounts.isEmpty()) {
                item {
                    FutaEmptyState(
                        title = "Chưa có tài khoản nào được kết nối",
                        message = "Quét mã QR để liên kết Zalo hoặc WhatsApp",
                        icon = Icons.Default.PersonOff
                    )
                }
            } else {
                items(accounts, key = { "${it.provider.raw}:${it.id}" }) { acc ->
                    AccountRow(
                        acc = acc,
                        canManage = canManage,
                        busy = busy,
                        reconnecting = reconnectingId == acc.id,
                        deleting = deletingId == acc.id,
                        updating = updatingId == acc.id,
                        onReconnect = { reconnect(acc) },
                        onReconnectQR = { navigator.push(ZaloRoute.QRLogin(acc.provider)) },
                        onDelete = { accountToDelete = acc },
                        onInboundChange = { updateInbound(acc, it) }
                    )
                }
            }
            item {
                Text("Chỉ nhận hội thoại từ khách chiến dịch: chỉ hiển thị người đã được tài khoản này gửi chiến dịch thành công.",
                    fontSize = 11.5.sp, color = FutaColors.Slate, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }

    ZaloConfirmDialog(
        visible = accountToDelete != null,
        title = "Ngắt kết nối tài khoản",
        message = tr("Bạn có chắc chắn muốn ngắt kết nối tài khoản {0}? Các cuộc trò chuyện sẽ bị ẩn.",
            accountToDelete?.displayName.orEmpty()),
        confirmText = "Ngắt kết nối",
        onConfirm = { accountToDelete?.let { delete(it) } },
        onDismiss = { accountToDelete = null }
    )
}

@Composable
private fun AccountRow(
    acc: ZaloAccountModel,
    canManage: Boolean,
    busy: Boolean,
    reconnecting: Boolean,
    deleting: Boolean,
    updating: Boolean,
    onReconnect: () -> Unit,
    onReconnectQR: () -> Unit,
    onDelete: () -> Unit,
    onInboundChange: (Boolean) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    ZaloAvatar(acc.avatar, acc.displayName, 44.dp, acc.provider.brandColor)
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                            .background(Color.White).padding(2.dp).clip(CircleShape)
                            .background(if (acc.isOnline) Color(0xFF16A34A) else Color.Gray)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VerbatimText(acc.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        ProviderBadge(acc.provider)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val color = if (acc.isOnline) Color(0xFF059669) else FutaColors.Slate
                        Icon(if (acc.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff, null, tint = color, modifier = Modifier.size(13.dp))
                        Text(if (acc.isOnline) "Đang hoạt động" else "Ngoại tuyến", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
                    }
                    VerbatimText("ID: ${acc.zaloId}", fontSize = 11.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (canManage) {
                    Box {
                        if (reconnecting || deleting) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { ZaloSpinner() }
                        } else {
                            IconButton(onClick = { showMenu = true }, enabled = !busy) {
                                Icon(Icons.Default.MoreHoriz, tr("Thao tác tài khoản {0}", acc.displayName), tint = FutaColors.Slate)
                            }
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (!acc.isOnline) {
                                DropdownMenuItem(
                                    text = { Text("Kết nối lại", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = { showMenu = false; onReconnect() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Kết nối lại bằng QR", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                                    onClick = { showMenu = false; onReconnectQR() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Ngắt kết nối", fontSize = 14.sp, color = Color(0xFFDC2626)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626)) },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Chỉ nhận hội thoại từ khách chiến dịch", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = FutaColors.Body)
                    Text(
                        if (canManage) "Chỉ hiển thị người đã được tài khoản này gửi chiến dịch thành công."
                        else "Bạn cần quyền quản lý tài khoản nhắn tin để thay đổi.",
                        fontSize = 11.sp, color = FutaColors.Slate, lineHeight = 15.sp
                    )
                    if (updating) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ZaloSpinner(size = 12.dp)
                            Text("Đang lưu…", fontSize = 11.sp, color = FutaColors.Slate)
                        }
                    }
                }
                FutaSwitch(
                    checked = acc.campaignOnlyInbound,
                    onCheckedChange = { if (canManage && !busy) onInboundChange(it) },
                    activeColor = FutaColors.BrandGreen
                )
            }
        }
    }
}

@Composable
fun ProviderBadge(provider: ZaloProvider) {
    VerbatimText(
        provider.displayName,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = provider.brandColor,
        modifier = Modifier
            .clip(CircleShape)
            .background(provider.brandColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// MARK: - QR login

private fun decodeBase64Image(value: String): Bitmap? = runCatching {
    val payload = value.substringAfter(",", value)
    val bytes = Base64.decode(payload, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

private fun generateQRCode(content: String): Bitmap? = runCatching {
    val size = 512
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i ->
        if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

@Composable
fun ZaloQRLoginScreen(provider: ZaloProvider, navigator: ZaloNavigator) {
    val canManage = ZaloAccess.canManageAccounts
    var status by remember { mutableStateOf(ZaloQRStatus.WAITING) }
    var loginId by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var scannedName by remember { mutableStateOf<String?>(null) }
    var scannedAvatar by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    /** Drops the current session; the server-side login is cancelled unless it succeeded. */
    fun endSession() {
        val previous = loginId
        loginId = ""
        if (previous.isEmpty()) return
        ZaloWebSocketManager.unregisterQRLogin(previous)
        if (status != ZaloQRStatus.SUCCESS) zaloBackgroundScope.launch { ZaloService.cancelQRLogin(previous) }
    }

    fun handleStatus(res: vn.futaland.app.core.network.JSONValue, statusStr: String) {
        if (status != ZaloQRStatus.WAITING && status != ZaloQRStatus.SCANNED) return
        when (statusStr) {
            "waiting" -> {
                if (status == ZaloQRStatus.SCANNED) return
                val image = res["image"].string.ifEmpty { null }
                val code = res["code"].string.ifEmpty { null }
                val payload = image ?: code ?: return
                if (payload == qrPayload) return
                qrPayload = payload
                qrBitmap = if (image != null) decodeBase64Image(payload) else generateQRCode(payload)
                if (qrBitmap == null) {
                    status = ZaloQRStatus.ERROR
                    errorMessage = "Không thể hiển thị mã QR. Vui lòng tạo mã mới."
                }
            }
            "scanned" -> {
                status = ZaloQRStatus.SCANNED
                scannedName = res["scannedUser"]["displayName"].string
                scannedAvatar = res["scannedUser"]["avatar"].string
            }
            "success" -> {
                status = ZaloQRStatus.SUCCESS
                ToastCenter.show(tr("Kết nối {0} thành công!", provider.displayName))
                navigator.accountsVersion++
                navigator.conversationsVersion++
            }
            "expired" -> {
                status = ZaloQRStatus.EXPIRED
                errorMessage = res["error"].string.ifEmpty { null }
            }
            "declined" -> status = ZaloQRStatus.DECLINED
            "error" -> {
                status = ZaloQRStatus.ERROR
                errorMessage = res["error"].string.ifEmpty { "Không thể kết nối tài khoản." }
            }
        }
    }

    // One login session per generation: start, register for socket events, poll as fallback.
    LaunchedEffect(generation) {
        endSession()
        status = ZaloQRStatus.WAITING
        errorMessage = null
        qrBitmap = null
        qrPayload = null
        scannedName = null
        scannedAvatar = null
        if (!canManage) {
            status = ZaloQRStatus.ERROR
            errorMessage = "Bạn cần quyền quản lý tài khoản nhắn tin để kết nối."
            return@LaunchedEffect
        }
        try {
            val id = ZaloService.startQRLogin(provider)
            if (id.isEmpty()) {
                status = ZaloQRStatus.ERROR
                errorMessage = "Không nhận được phiên đăng nhập. Vui lòng tạo mã mới."
                return@LaunchedEffect
            }
            loginId = id
            ZaloWebSocketManager.registerQRLogin(id)
            while (isActive && loginId == id && (status == ZaloQRStatus.WAITING || status == ZaloQRStatus.SCANNED)) {
                val res = ZaloService.fetchQRLoginStatus(id)
                if (loginId != id) break
                handleStatus(res, res["status"].string)
                delay(1200)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (status == ZaloQRStatus.WAITING || status == ZaloQRStatus.SCANNED) {
                status = ZaloQRStatus.ERROR
                errorMessage = e.message
            }
        }
    }

    LaunchedEffect(Unit) {
        ZaloWebSocketManager.events.collect { event ->
            if (event !is ZaloEvent.QR) return@collect
            val data = event.data
            if (loginId.isEmpty() || data["loginId"].string != loginId) return@collect
            val statusStr = data["status"].string.ifEmpty { event.event.removePrefix("qr:") }
            handleStatus(data, statusStr)
        }
    }

    DisposableEffect(Unit) { onDispose { endSession() } }

    ZaloTopBar(title = tr("Kết nối {0}", provider.displayName), onBack = { endSession(); navigator.pop() }) {
        Text(
            if (status == ZaloQRStatus.SUCCESS) "Xong" else "Huỷ",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZaloBlue,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { endSession(); navigator.pop() }.padding(8.dp)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.QrCode2, null, tint = provider.brandColor, modifier = Modifier.size(32.dp))
            Text(
                if (provider == ZaloProvider.WHATSAPP)
                    "Mở WhatsApp → Cài đặt → Thiết bị liên kết → Liên kết thiết bị, rồi quét mã QR."
                else "Mở Zalo trên điện thoại → Quét mã QR, rồi xác nhận đăng nhập.",
                fontSize = 14.sp, color = FutaColors.Slate, textAlign = TextAlign.Center
            )
        }

        FutaCard(modifier = Modifier.size(width = 280.dp, height = 320.dp), shape = RoundedCornerShape(24.dp)) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                when (status) {
                    ZaloQRStatus.WAITING -> {
                        val bitmap = qrBitmap
                        if (bitmap != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = tr("Mã QR đăng nhập"),
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, provider.brandColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ZaloSpinner(size = 12.dp)
                                    Text("Đang chờ quét mã QR…", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FutaColors.Slate)
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ZaloSpinner(size = 28.dp)
                                Text("Đang tạo mã QR...", fontSize = 14.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                    ZaloQRStatus.SCANNED -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZaloAvatar(scannedAvatar.orEmpty(), scannedName.orEmpty().ifEmpty { "?" }, 72.dp, provider.brandColor)
                        VerbatimText(scannedName?.ifEmpty { null } ?: tr("Người dùng"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        Text("Đã quét mã QR!", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                        Text("Vui lòng nhấn Xác nhận trên điện thoại của bạn", fontSize = 12.sp, color = FutaColors.Slate, textAlign = TextAlign.Center)
                        ZaloSpinner()
                    }
                    ZaloQRStatus.SUCCESS -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(64.dp))
                        Text("Đăng nhập thành công!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                    ZaloQRStatus.EXPIRED, ZaloQRStatus.DECLINED, ZaloQRStatus.ERROR -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFEA580C), modifier = Modifier.size(48.dp))
                        Text(errorMessage ?: status.title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = FutaColors.Slate, textAlign = TextAlign.Center)
                        FutaButton(text = "Tạo mã mới", onClick = { generation++ }, icon = Icons.Default.Refresh,
                            enabled = canManage, height = 40.dp)
                    }
                    ZaloQRStatus.IDLE -> ZaloSpinner()
                }
            }
        }

        Text("Chỉ quét mã bằng tài khoản bạn muốn kết nối. Không chia sẻ mã QR đăng nhập.",
            fontSize = 12.sp, color = FutaColors.Slate, textAlign = TextAlign.Center)
    }
}
