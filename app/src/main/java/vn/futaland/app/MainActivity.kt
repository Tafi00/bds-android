package vn.futaland.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.designsystem.FutaLandTheme
import vn.futaland.app.designsystem.FutaToastOverlay
import vn.futaland.app.features.account.AccountScreen
import vn.futaland.app.features.account.PricingScreen
import vn.futaland.app.features.account.AuthenticationScreen
import vn.futaland.app.features.properties.SavedPropertiesScreen
import vn.futaland.app.features.account.WorkspaceScreen
import vn.futaland.app.features.discovery.DiscoveryScreen
import vn.futaland.app.features.discovery.ProjectsScreen
import vn.futaland.app.features.account.AdminModuleScreen
import vn.futaland.app.features.account.AdminSettingsScreen
import vn.futaland.app.features.discovery.ProjectDetailScreen
import vn.futaland.app.features.messaging.NotificationsScreen
import vn.futaland.app.features.luckywheel.LuckyWheelScreen
import vn.futaland.app.features.messaging.ChatScreen
import vn.futaland.app.features.properties.PropertyDetailScreen
import vn.futaland.app.features.properties.PropertySearchScreen
import vn.futaland.app.navigation.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        setContent {
            FutaLandTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route


                fun safeNavigate(route: String) {
                    try {
                        navController.navigate(route)
                    } catch (e: Exception) {
                        android.util.Log.e("FutaNav", "Navigation error: ${e.message}")
                    }
                }
                // Restore session on startup
                LaunchedEffect(Unit) {
                    AppSession.shared.restore()
                }

                // Listen to deep links
                val pendingRoute by RouteCoordinator.pendingIntent.collectAsState()
                LaunchedEffect(pendingRoute) {
                    pendingRoute?.let { intent ->
                        RouteCoordinator.consume(intent)
                        val path = intent.route
                        if (path.startsWith("/listing/")) {
                            val id = path.removePrefix("/listing/")
                            navController.navigate(FutaDestinations.propertyDetail(id))
                        } else if (path == "/lucky-wheel") {
                            navController.navigate(FutaDestinations.LUCKY_WHEEL)
                        } else if (path == "/chat") {
                            navController.navigate(FutaDestinations.INBOX)
                        } else if (path == "/search") {
                            navController.navigate(FutaDestinations.SEARCH)
                        } else if (path == "/workspace") {
                            navController.navigate(FutaDestinations.WORKSPACE)
                        } else if (path == "/account") {
                            navController.navigate(FutaDestinations.ACCOUNT)
                        } else if (path == "/auth" || path == "/login") {
                            safeNavigate(FutaDestinations.AUTH)
                        }
                    }
                }

                val isRootTab = currentRoute in BottomNavRoutes

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = Color.White,
                        bottomBar = {
                            if (isRootTab) {
                                FutaBottomBar(
                                    currentRoute = currentRoute,
                                    onNavigate = { route ->
                                        if (route != currentRoute) {
                                            navController.navigate(route) {
                                                popUpTo(FutaDestinations.DISCOVER)
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = FutaDestinations.DISCOVER,
                            modifier = Modifier.padding(padding)
                        ) {
                            // 1. Discover Tab
                            composable(FutaDestinations.DISCOVER) {
                                DiscoveryScreen(
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // 2. Saved Tab
                            composable(FutaDestinations.SAVED) {
                                FutaAccessGate(
                                    access = NativeAccess.SignedIn,
                                    session = AppSession.shared,
                                    onRequireLogin = { safeNavigate(FutaDestinations.AUTH) }
                                ) {
                                    SavedPropertiesScreen(
                                        onNavigate = { route -> safeNavigate(route) }
                                    )
                                }
                            }

                            // 3. Inbox Tab
                            composable(FutaDestinations.INBOX) {
                                FutaAccessGate(
                                    access = NativeAccess.SignedIn,
                                    session = AppSession.shared,
                                    onRequireLogin = { safeNavigate(FutaDestinations.AUTH) }
                                ) {
                                    ChatScreen(
                                        onBack = if (!isRootTab) ({ navController.popBackStack() }) else null
                                    )
                                }
                            }

                            // 4. Account Tab
                            composable(FutaDestinations.ACCOUNT) {
                                AccountScreen(
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // 5. Search Tab
                            composable(FutaDestinations.SEARCH) {
                                PropertySearchScreen(
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // Auth Modal / Destination
                            composable(FutaDestinations.AUTH) {
                                AuthenticationScreen(
                                    onBack = { navController.popBackStack() },
                                    onSuccess = { navController.popBackStack() }
                                )
                            }

                            // Secondary: Property Detail
                            composable(
                                route = FutaDestinations.PROPERTY_DETAIL,
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
                            ) { backStack ->
                                val id = backStack.arguments?.getString("id") ?: ""
                                PropertyDetailScreen(
                                    propertyId = id,
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // Secondary: Project Detail
                            composable(
                                route = FutaDestinations.PROJECT_DETAIL,
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
                            ) { backStack ->
                                val id = backStack.arguments?.getString("id") ?: ""
                                ProjectDetailScreen(
                                    projectId = id,
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // Secondary: Projects List
                            composable(FutaDestinations.PROJECTS_LIST) {
                                ProjectsScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // Secondary: Notifications
                            composable(FutaDestinations.NOTIFICATIONS) {
                                NotificationsScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // Secondary: Lucky Wheel
                            composable(FutaDestinations.LUCKY_WHEEL) {
                                LuckyWheelScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // Secondary: Workspace
                            composable(FutaDestinations.WORKSPACE) {
                                WorkspaceScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // Secondary: Admin & Business Modules
                            composable(FutaDestinations.ADMIN_PROJECTS) {
                                AdminModuleScreen("Quản lý dự án", "/projects") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_CAMPAIGNS) {
                                AdminModuleScreen("Chương trình bán hàng", "/sales/campaigns") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_INVENTORY) {
                                AdminModuleScreen("Quản lý sản phẩm", "/apartments") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_REGISTRATIONS) {
                                AdminModuleScreen("Duyệt đăng ký bán", "/sales/registrations") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_TRANSACTIONS) {
                                AdminModuleScreen("Quản lý giao dịch", "/advisor/transactions") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_CMS) {
                                AdminModuleScreen("CMS Bài viết", "/cms/admin/news") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_SETTINGS) {
                                AdminSettingsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_USERS) {
                                AdminModuleScreen("Quản lý người dùng", "/users") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_ROLES) {
                                AdminModuleScreen("Vai trò & Phân quyền", "/users") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_ADVISOR_PROFILES) {
                                AdminModuleScreen("Duyệt hồ sơ TVV", "/advisor/profile-requests") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_AI) {
                                AdminModuleScreen("Training AI", "/projects") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_ZALO) {
                                AdminModuleScreen("Marketing Zalo", "/zalo/campaigns") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_CUSTOMERS) {
                                AdminModuleScreen("Khách hàng", "/customers") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_CONTRACTS) {
                                AdminModuleScreen("Hợp đồng giao dịch", "/contracts") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_REPORTS) {
                                AdminModuleScreen("Báo cáo doanh số", "/sales/campaigns") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_DASHBOARD) {
                                AdminModuleScreen("Bảng điều khiển", "/projects") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.CRM) {
                                AdminModuleScreen("Chăm sóc khách hàng (CRM)", "/customers") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADVISOR) {
                                AdminModuleScreen("Trở thành tư vấn viên", "/advisor/profile-requests") { navController.popBackStack() }
                            }
                            // Secondary: Authentication (Login / Register)
                            composable(FutaDestinations.AUTH) {
                                AuthenticationScreen(
                                    onBack = { navController.popBackStack() },
                                    onSuccess = { navController.popBackStack() }
                                )
                            }

                            // Secondary: Pricing VIP Plans
                            composable(FutaDestinations.PRICING) {
                                PricingScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                    // Toast System Overlay
                    FutaToastOverlay()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        RouteCoordinator.enqueue(uri.toString())
    }
}
