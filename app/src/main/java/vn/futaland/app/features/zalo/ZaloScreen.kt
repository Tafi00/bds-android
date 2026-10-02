package vn.futaland.app.features.zalo

import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.*
import vn.futaland.app.navigation.FutaAccessGate
import vn.futaland.app.navigation.NativeAccess

/** Screens pushed on top of the module root (iOS NavigationLink / sheets of ZaloView). */
sealed interface ZaloRoute {
    data object Accounts : ZaloRoute
    data class QRLogin(val provider: ZaloProvider) : ZaloRoute
    data object Labels : ZaloRoute
    data object SuggestionConfig : ZaloRoute
    data class Chat(val conversation: ZaloConversationModel) : ZaloRoute
    data class NewChat(val accounts: List<ZaloAccountModel>) : ZaloRoute
    data class CampaignDetail(val campaignId: String) : ZaloRoute
    data object CreateCampaign : ZaloRoute
}

/**
 * In-module navigation: pages stack over the root so lists keep their state and keep listening
 * to realtime events while a chat or campaign is open, like a SwiftUI NavigationStack.
 */
class ZaloNavigator {
    val stack = mutableStateListOf<ZaloRoute>()

    /** Bumped to make the lists reload after a change made on a pushed page. */
    var conversationsVersion by mutableIntStateOf(0)
    var campaignsVersion by mutableIntStateOf(0)
    var accountsVersion by mutableIntStateOf(0)
    var labelsVersion by mutableIntStateOf(0)

    fun push(route: ZaloRoute) {
        stack.add(route)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun ZaloScreen(onBack: () -> Unit) {
    // iOS NativeNavigation: zalo:view or zalo:manage.
    FutaAccessGate(access = NativeAccess.Permissions(listOf("zalo:view", "zalo:manage")), session = AppSession.shared) {
        ZaloModule(onBack)
    }
}

@Composable
private fun ZaloModule(onBack: () -> Unit) {
    val navigator = remember { ZaloNavigator() }

    DisposableEffect(Unit) {
        ZaloWebSocketManager.connect()
        onDispose { ZaloWebSocketManager.disconnect() }
    }

    BackHandler(enabled = navigator.stack.isNotEmpty()) { navigator.pop() }

    Box(modifier = Modifier.fillMaxSize().background(FutaColors.PageBg)) {
        ZaloRoot(navigator, onBack)
        navigator.stack.forEachIndexed { index, route ->
            key(index, route) {
                ZaloPage { ZaloRouteContent(route, navigator) }
            }
        }
    }
}

@Composable
private fun ZaloRouteContent(route: ZaloRoute, navigator: ZaloNavigator) {
    when (route) {
        ZaloRoute.Accounts -> ZaloAccountsScreen(navigator)
        is ZaloRoute.QRLogin -> ZaloQRLoginScreen(route.provider, navigator)
        ZaloRoute.Labels -> ZaloLabelsScreen(navigator)
        ZaloRoute.SuggestionConfig -> ZaloSuggestionConfigScreen(navigator)
        is ZaloRoute.Chat -> ZaloChatDetailScreen(route.conversation, navigator)
        is ZaloRoute.NewChat -> ZaloNewChatScreen(route.accounts, navigator)
        is ZaloRoute.CampaignDetail -> ZaloCampaignDetailScreen(route.campaignId, navigator)
        ZaloRoute.CreateCampaign -> ZaloCreateCampaignScreen(navigator)
    }
}

private enum class ZaloTab(val title: String, val icon: ImageVector) {
    MESSAGES("Tin nhắn", Icons.Default.Forum),
    CAMPAIGNS("Chiến dịch", Icons.Default.Campaign)
}

@Composable
private fun ZaloRoot(navigator: ZaloNavigator, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<ZaloAccountModel>>(emptyList()) }
    var loadingAccounts by remember { mutableStateOf(true) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(ZaloTab.MESSAGES) }
    var showMenu by remember { mutableStateOf(false) }
    val canUseCampaigns = ZaloAccess.canUseCampaigns
    val canManageAccounts = ZaloAccess.canManageAccounts

    fun loadAccounts() {
        scope.launch {
            try {
                accounts = ZaloService.fetchAccounts()
                accountError = null
            } catch (e: Exception) {
                accountError = e.message ?: tr("Lỗi tải tài khoản")
            } finally {
                loadingAccounts = false
            }
        }
    }

    LaunchedEffect(navigator.accountsVersion) { loadAccounts() }
    LaunchedEffect(Unit) {
        ZaloWebSocketManager.events.collect { event ->
            if (event is ZaloEvent.AccountsRefresh || event is ZaloEvent.AccountStatus) loadAccounts()
        }
    }
    LaunchedEffect(canUseCampaigns) { if (!canUseCampaigns) activeTab = ZaloTab.MESSAGES }

    Column(modifier = Modifier.fillMaxSize()) {
        ZaloTopBar(title = "Marketing", onBack = onBack) {
            AccountStatusPill(loadingAccounts, accountError != null, accounts.count { it.isOnline }) {
                navigator.push(ZaloRoute.Accounts)
            }
            Box {
                FutaHeaderIconButton(Icons.Default.Tune, tr("Tuỳ chọn"), { showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (canManageAccounts) {
                        MenuRow("Quản lý tài khoản", Icons.Default.People) {
                            showMenu = false; navigator.push(ZaloRoute.Accounts)
                        }
                        MenuRow("Thêm Zalo (QR)", Icons.Default.QrCode2) {
                            showMenu = false; navigator.push(ZaloRoute.QRLogin(ZaloProvider.ZALO))
                        }
                        MenuRow("Thêm WhatsApp (QR)", Icons.Default.QrCode2) {
                            showMenu = false; navigator.push(ZaloRoute.QRLogin(ZaloProvider.WHATSAPP))
                        }
                        HorizontalDivider(color = FutaColors.PanelDivider)
                    }
                    MenuRow("Quản lý nhãn", Icons.Default.Label) {
                        showMenu = false; navigator.push(ZaloRoute.Labels)
                    }
                    MenuRow("Cấu hình AI gợi ý", Icons.Default.AutoAwesome) {
                        showMenu = false; navigator.push(ZaloRoute.SuggestionConfig)
                    }
                }
            }
        }

        if (canUseCampaigns) {
            FutaSegmentTabs(
                items = ZaloTab.entries.toList(),
                selectedItem = activeTab,
                onSelect = { activeTab = it },
                titleFor = { it.title },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                ZaloTab.MESSAGES -> ZaloConversationListView(navigator)
                ZaloTab.CAMPAIGNS -> if (canUseCampaigns) ZaloCampaignsListView(navigator)
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, fontSize = 14.sp, color = FutaColors.Navy) },
        leadingIcon = { Icon(icon, null, tint = FutaColors.Slate, modifier = Modifier.size(18.dp)) },
        onClick = onClick
    )
}

@Composable
private fun AccountStatusPill(loading: Boolean, failed: Boolean, onlineCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFFF1F5F9))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        when {
            loading -> {
                ZaloSpinner(size = 10.dp)
                Text("Đang kết nối…", fontSize = 11.sp, color = FutaColors.Slate)
            }
            failed -> {
                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFEA580C), modifier = Modifier.size(13.dp))
                Text("Lỗi tải tài khoản", fontSize = 11.sp, color = FutaColors.Slate)
            }
            else -> {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (onlineCount > 0) Color(0xFF16A34A) else Color.Gray))
                Text(tr("{0} tài khoản hoạt động", onlineCount), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = FutaColors.Slate, maxLines = 1)
            }
        }
    }
}
