package vn.futaland.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.R
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.FutaColors
import vn.futaland.app.designsystem.futaDropShadow

data class BottomNavItemSpec(
    val route: String,
    val title: String,
    val activeIcon: Int,
    val inactiveIcon: Int
)

// 4 main navigation tabs using 100% exact Apple SF Symbols (Retina pre-rendered)
val MainFourNavSpecs = listOf(
    BottomNavItemSpec(FutaDestinations.DISCOVER, "Trang chủ", R.drawable.sf_tab_home_active, R.drawable.sf_tab_home_inactive),
    BottomNavItemSpec(FutaDestinations.SAVED, "Yêu thích", R.drawable.sf_tab_heart_active, R.drawable.sf_tab_heart_inactive),
    BottomNavItemSpec(FutaDestinations.SEARCH, "Tìm kiếm", R.drawable.sf_nav_search, R.drawable.sf_nav_search),
    BottomNavItemSpec(FutaDestinations.ACCOUNT, "Tài khoản", R.drawable.sf_tab_account_active, R.drawable.sf_tab_account_inactive)
)

val BottomNavRoutes = MainFourNavSpecs.map { it.route } + listOf(FutaDestinations.INBOX)

/**
 * Dual-Island Floating Capsule Navigation Dock matching iOS layout and Apple SF Symbols 100%.
 */
@Composable
fun FutaBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val capsuleShape = RoundedCornerShape(32.dp)
    val brandGreen = Color(0xFF0E7643)
    val inactiveDark = Color(0xFF1E293B)
    val activePillBg = Color(0xFFE8F5E9)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ====================================================================
            // ISLAND 1: Main Navigation Capsule (4 Tabs, 64dp Height)
            // ====================================================================
            Surface(
                shape = capsuleShape,
                color = Color.White,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .futaDropShadow(
                        shape = capsuleShape,
                        color = Color(0x14061D3D),
                        blur = 16.dp,
                        offsetY = 6.dp
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MainFourNavSpecs.forEach { spec ->
                        val selected = currentRoute == spec.route ||
                            (spec.route == FutaDestinations.SEARCH && currentRoute?.startsWith("tab_search") == true)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .padding(horizontal = 2.dp)
                                .then(
                                    if (selected) {
                                        Modifier
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(activePillBg)
                                            .border(1.dp, Color(0xFFC8E6C9).copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                                    } else Modifier
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onNavigate(spec.route) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box {
                                    Icon(
                                        painter = painterResource(id = if (selected) spec.activeIcon else spec.inactiveIcon),
                                        contentDescription = spec.title,
                                        tint = if (spec.route == FutaDestinations.SEARCH) {
                                            if (selected) brandGreen else inactiveDark
                                        } else Color.Unspecified,
                                        modifier = Modifier.size(if (spec.route == FutaDestinations.SEARCH) 20.dp else 22.dp)
                                    )
                                }
                                Spacer(Modifier.height(2.5.dp))
                                Text(
                                    text = spec.title,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (selected) brandGreen else inactiveDark,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // ====================================================================
            // ISLAND 2: Detached Floating Circular Chat / AI Button (64dp Diameter)
            // ====================================================================
            val chatSelected = currentRoute == FutaDestinations.INBOX
            val isGuest = !AppSession.shared.isAuthenticated || AppSession.shared.role in listOf("guest", "khach", "customer")
            val chatActionTitle = if (isGuest) "Trợ lý AI" else "Tin nhắn"

            Surface(
                shape = CircleShape,
                color = if (chatSelected) brandGreen else Color.White,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, if (chatSelected) brandGreen else Color(0xFFF1F5F9)),
                modifier = Modifier
                    .size(64.dp)
                    .futaDropShadow(
                        shape = CircleShape,
                        color = if (chatSelected) brandGreen.copy(alpha = 0.32f) else Color(0x14061D3D),
                        blur = 14.dp,
                        offsetY = 5.dp
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onNavigate(FutaDestinations.INBOX) }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = if (chatSelected) R.drawable.sf_tab_chat_active else R.drawable.sf_tab_chat_inactive),
                        contentDescription = chatActionTitle,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
