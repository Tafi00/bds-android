package vn.futaland.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import vn.futaland.app.designsystem.clearFocusOnTap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.futaland.app.features.account.AdminRegistrationsScreen
import vn.futaland.app.features.account.AdvisorProductsScreen
import vn.futaland.app.features.account.AdvisorProposalsScreen
import vn.futaland.app.features.account.AdminTransactionsScreen
import vn.futaland.app.features.account.AdminCMSScreen
import vn.futaland.app.features.account.AdminUsersScreen
import vn.futaland.app.features.account.AdminAdvisorProfilesScreen
import vn.futaland.app.features.account.AdminCustomersScreen
import vn.futaland.app.features.account.AdminExamsScreen
import vn.futaland.app.features.account.AdminContractsScreen
import vn.futaland.app.features.account.AdminReportsScreen
import vn.futaland.app.features.discovery.DiscoveryScreen
import vn.futaland.app.features.discovery.ProjectsScreen
import vn.futaland.app.features.discovery.ProjectDetailScreen
import vn.futaland.app.features.discovery.ProjectMapScreen
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.FutaLandTheme
import vn.futaland.app.designsystem.FutaToastOverlay
import vn.futaland.app.designsystem.ToastCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.futaland.app.features.account.PricingScreen
import vn.futaland.app.features.account.AuthenticationScreen
import vn.futaland.app.features.account.AccountScreen
import vn.futaland.app.features.account.AuthStep
import vn.futaland.app.features.properties.SavedPropertiesScreen
import vn.futaland.app.features.account.WorkspaceScreen
import vn.futaland.app.features.discovery.DiscoveryScreen
import vn.futaland.app.features.discovery.ProjectsScreen
import vn.futaland.app.features.account.AdminModuleScreen
import vn.futaland.app.features.account.AdminSettingsScreen
import vn.futaland.app.features.account.AdminDashboardScreen
import vn.futaland.app.features.account.AdminCRMScreen
import vn.futaland.app.features.account.AdminAIScreen
import vn.futaland.app.features.account.AdminRolesScreen
import vn.futaland.app.features.account.AdminLuckyWheelScreen
import vn.futaland.app.features.account.ProfileScreen
import vn.futaland.app.features.account.AdvisorWorkspaceScreen
import vn.futaland.app.features.account.NewsScreen
import vn.futaland.app.features.account.NewsDetailScreen
import vn.futaland.app.features.account.GuideScreen
import vn.futaland.app.features.account.ContactScreen
import vn.futaland.app.features.account.AboutScreen
import vn.futaland.app.features.account.PoliciesScreen
import vn.futaland.app.features.account.BillingScreen
import vn.futaland.app.features.properties.MyListingsScreen
import vn.futaland.app.features.properties.ViewHistoryScreen
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
                        if (path == "/login-admin" || path == "/admin-login" || path == "/auth" || path == "/login") {
                            safeNavigate(FutaDestinations.AUTH)
                        } else if (path == "/logout") {
                            AppSession.shared.logout()
                            navController.navigate(FutaDestinations.ACCOUNT)
                        } else if (path.startsWith("/listing/")) {
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
                        } else if (path == "/saved") {
                            navController.navigate(FutaDestinations.SAVED)
                        } else if (path == "/projects/map" || path == "/map") {
                            safeNavigate(FutaDestinations.PROJECTS_MAP)
                        } else if (path == "/projects") {
                            safeNavigate(FutaDestinations.PROJECTS_LIST)
                        } else if (path.startsWith("/project/")) {
                            val id = path.removePrefix("/project/")
                            safeNavigate(FutaDestinations.projectDetail(id))
                        } else if (path == "/pricing") {
                            safeNavigate(FutaDestinations.PRICING)
                        } else if (path == "/notifications") {
                            safeNavigate(FutaDestinations.NOTIFICATIONS)
                        } else if (path.startsWith("/admin/")) {
                            val dest = path.removePrefix("/admin/")
                            when (dest) {
                                "dashboard" -> safeNavigate(FutaDestinations.ADMIN_DASHBOARD)
                                "projects" -> safeNavigate(FutaDestinations.ADMIN_PROJECTS)
                                "campaigns" -> safeNavigate(FutaDestinations.ADMIN_CAMPAIGNS)
                                "inventory" -> safeNavigate(FutaDestinations.ADMIN_INVENTORY)
                                "registrations" -> safeNavigate(FutaDestinations.ADMIN_REGISTRATIONS)
                                "transactions" -> safeNavigate(FutaDestinations.ADMIN_TRANSACTIONS)
                                "cms" -> safeNavigate(FutaDestinations.ADMIN_CMS)
                                "settings" -> safeNavigate(FutaDestinations.ADMIN_SETTINGS)
                                "users" -> safeNavigate(FutaDestinations.ADMIN_USERS)
                                "roles" -> safeNavigate(FutaDestinations.ADMIN_ROLES)
                                "advisor-profiles" -> safeNavigate(FutaDestinations.ADMIN_ADVISOR_PROFILES)
                                "ai" -> safeNavigate(FutaDestinations.ADMIN_AI)
                                "zalo" -> safeNavigate(FutaDestinations.ADMIN_ZALO)
                                "customers" -> safeNavigate(FutaDestinations.ADMIN_CUSTOMERS)
                                "contracts" -> safeNavigate(FutaDestinations.ADMIN_CONTRACTS)
                                "reports" -> safeNavigate(FutaDestinations.ADMIN_REPORTS)
                                "crm" -> safeNavigate(FutaDestinations.CRM)
                                "lucky-wheel" -> safeNavigate(FutaDestinations.ADMIN_LUCKY_WHEEL)
                            }
                        } else if (path == "/customers") {
                            safeNavigate(FutaDestinations.ADMIN_CUSTOMERS)
                        } else if (path == "/contracts") {
                            safeNavigate(FutaDestinations.ADMIN_CONTRACTS)
                        } else if (path == "/reports") {
                            safeNavigate(FutaDestinations.ADMIN_REPORTS)
                        } else if (path == "/advisor-products" || path == "/advisor/products") {
                            safeNavigate(FutaDestinations.ADVISOR_PRODUCTS)
                        } else if (path == "/proposals" || path == "/advisor/proposals") {
                            safeNavigate(FutaDestinations.ADVISOR_PROPOSALS)
                        } else if (path == "/advisor") {
                            safeNavigate(FutaDestinations.ADVISOR)
                        } else if (path == "/advisor/registrations" || path == "/registrations" || path == "/advisor_registrations") {
                            safeNavigate(FutaDestinations.ADMIN_REGISTRATIONS)
                        } else if (path == "/my-listings") {
                            safeNavigate(FutaDestinations.MY_LISTINGS)
                        } else if (path == "/history") {
                            safeNavigate(FutaDestinations.VIEW_HISTORY)
                        } else if (path == "/billing") {
                            safeNavigate(FutaDestinations.BILLING)
                        } else if (path == "/profile") {
                            safeNavigate(FutaDestinations.PROFILE)
                        } else if (path == "/auth-password") {
                            safeNavigate("auth_password")
                        } else if (path == "/auth" || path == "/login") {
                            safeNavigate(FutaDestinations.AUTH)
                        }
                    }
                }
                val isRootTab = (currentRoute in BottomNavRoutes || currentRoute?.startsWith("tab_search") == true) && (currentRoute != FutaDestinations.INBOX)
                Box(modifier = Modifier.fillMaxSize().clearFocusOnTap()) {
                    Scaffold(
                        containerColor = Color.White,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
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

                            // 3. Inbox Tab & Direct Chat
                            composable(FutaDestinations.INBOX) {
                                ChatScreen(
                                    onBack = if (!isRootTab) ({ navController.popBackStack() }) else null
                                )
                            }
                            // Staff chat center lives inside the advisor workspace only
                            composable(FutaDestinations.CHAT_CENTER) {
                                ChatScreen(
                                    staffContext = true,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = FutaDestinations.CHAT_ROUTE,
                                arguments = listOf(
                                    navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("advisorId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("advisorName") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("apartmentId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("isAi") { type = NavType.StringType; nullable = true; defaultValue = null }
                                )
                            ) { backStack ->
                                val convId = backStack.arguments?.getString("conversationId")
                                val advId = backStack.arguments?.getString("advisorId")
                                val advName = backStack.arguments?.getString("advisorName")
                                val aptId = backStack.arguments?.getString("apartmentId")
                                val isAi = backStack.arguments?.getString("isAi") == "true"
                                ChatScreen(
                                    initialConversationId = convId,
                                    targetAdvisorId = advId,
                                    targetAdvisorName = advName,
                                    targetApartmentId = aptId,
                                    isAiChat = isAi,
                                    onBack = { navController.popBackStack() }
                                )
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
                            composable(
                                route = FutaDestinations.SEARCH_ROUTE,
                                arguments = listOf(navArgument("propertyType") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                })
                            ) { backStack ->
                                val propType = backStack.arguments?.getString("propertyType")
                                PropertySearchScreen(
                                    initialPropertyType = propType,
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
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
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
                            // Secondary: Project Map
                            composable(FutaDestinations.PROJECTS_MAP) {
                                ProjectMapScreen(
                                    onBack = { navController.popBackStack() },
                                    onProjectClick = { id -> safeNavigate(FutaDestinations.projectDetail(id)) }
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
                                AdminRegistrationsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_TRANSACTIONS) {
                                AdminTransactionsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_CMS) {
                                AdminCMSScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_SETTINGS) {
                                AdminSettingsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_USERS) {
                                AdminUsersScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_ROLES) {
                                AdminRolesScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_ADVISOR_PROFILES) {
                                AdminAdvisorProfilesScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_AI) {
                                AdminAIScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_EXAMS) {
                                AdminExamsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_ZALO) {
                                AdminModuleScreen("Marketing Zalo", "/zalo/campaigns") { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_CUSTOMERS) {
                                AdminCustomersScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_CONTRACTS) {
                                AdminContractsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_REPORTS) {
                                AdminReportsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_DASHBOARD) {
                                AdminDashboardScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.CRM) {
                                AdminCRMScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_LUCKY_WHEEL) {
                                AdminLuckyWheelScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.PROFILE) {
                                ProfileScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADVISOR) {
                                AdvisorWorkspaceScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }
                            composable(FutaDestinations.ADVISOR_PRODUCTS) {
                                AdvisorProductsScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }
                            composable(FutaDestinations.ADVISOR_PROPOSALS) {
                                AdvisorProposalsScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }
                            // Secondary: Authentication (Login / Register)
                            composable(FutaDestinations.AUTH) {
                                AuthenticationScreen(
                                    onBack = { navController.popBackStack() },
                                    onSuccess = { navController.popBackStack() }
                                )
                            }

                            // Secondary: Pricing VIP Plans
                            composable("auth_password") {
                                AuthenticationScreen(
                                    initialStep = AuthStep.PASSWORD,
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
                            composable(FutaDestinations.NEWS) {
                                NewsScreen(
                                    onBack = { navController.popBackStack() },
                                    onArticleClick = { slug -> navController.navigate(FutaDestinations.newsDetail(slug)) }
                                )
                            }
                            composable(
                                route = FutaDestinations.NEWS_DETAIL,
                                arguments = listOf(navArgument("slug") { type = NavType.StringType })
                            ) { backStack ->
                                val slug = backStack.arguments?.getString("slug") ?: ""
                                NewsDetailScreen(
                                    slug = slug,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(FutaDestinations.GUIDE) {
                                GuideScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.CONTACT) {
                                ContactScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ABOUT) {
                                AboutScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.POLICIES) {
                                PoliciesScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.BILLING) {
                                BillingScreen(
                                    onBack = { navController.popBackStack() },
                                    onUpgradeClick = { navController.navigate(FutaDestinations.PRICING) }
                                )
                            }
                            composable(FutaDestinations.MY_LISTINGS) {
                                MyListingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }
                            composable(FutaDestinations.VIEW_HISTORY) {
                                ViewHistoryScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
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
        val authToken = intent?.getStringExtra("auth_token")
        if (!authToken.isNullOrEmpty()) {
            APIClient.get().tokenStorage.accessToken = authToken
        }
        val uri = intent?.data ?: return
        RouteCoordinator.enqueue(uri.toString())
    }
}
