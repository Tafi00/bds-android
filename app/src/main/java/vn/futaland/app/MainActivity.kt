package vn.futaland.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import vn.futaland.app.features.account.ViewingAppointmentsScreen
import vn.futaland.app.features.discovery.ProjectDetailScreen
import vn.futaland.app.features.messaging.NotificationsScreen
import vn.futaland.app.features.messaging.ChatUnreadBadge
import vn.futaland.app.features.messaging.FutaMessagingService
import vn.futaland.app.features.luckywheel.LuckyWheelScreen
import vn.futaland.app.features.messaging.ChatScreen
import vn.futaland.app.features.properties.PropertyDetailScreen
import vn.futaland.app.features.properties.AgentDetailScreen
import vn.futaland.app.core.sales.ProductContext
import vn.futaland.app.features.properties.PropertySearchScreen
import vn.futaland.app.core.update.AppUpdateDialog
import vn.futaland.app.core.update.PlayStoreUpdateManager
import vn.futaland.app.navigation.*
import vn.futaland.app.features.messaging.FcmRegistrar

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)
        requestNotificationPermission()
        PlayStoreUpdateManager.checkForUpdates(this)

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
                    if (AppSession.shared.isAuthenticated) {
                        ChatUnreadBadge.refresh()
                    }
                }

                // Listen to deep links, FCM taps and in-app notification routes.
                val pendingRoute by RouteCoordinator.pendingIntent.collectAsState()
                val sessionUser by AppSession.shared.currentUser.collectAsState()
                LaunchedEffect(pendingRoute, sessionUser) {
                    val intent = pendingRoute ?: return@LaunchedEffect
                    val routeUri = android.net.Uri.parse(intent.route)
                    val path = routeUri.path.orEmpty()
                    val segments = path.split("/").filter { it.isNotEmpty() }
                    val first = segments.firstOrNull().orEmpty()

                    // iOS parity: non-public roots require an authenticated session.
                    // The intent stays pending so it resumes after login.
                    val publicRoots = setOf(
                        "", "listing", "projects", "news", "search", "properties", "danh-sach-bds",
                        "login", "auth", "dang-nhap", "signin", "account", "chat", "logout", "signout", "dang-xuat",
                        "policies", "dieu-khoan-chinh-sach", "quy-che-hoat-dong",
                        "about", "gioi-thieu", "contact", "lien-he", "ho-tro",
                        "guide", "huong-dan", "docs", "pricing", "bang-gia", "advisor", "registrations", "map"
                    )
                    if ((!publicRoots.contains(first) || intent.userId != null) && !AppSession.shared.isAuthenticated) {
                        safeNavigate(FutaDestinations.AUTH)
                        return@LaunchedEffect
                    }
                    // iOS parity: a notification owned by another account is dropped.
                    if (intent.userId != null && intent.userId != sessionUser?.get("id")?.string) {
                        RouteCoordinator.consume(intent)
                        ToastCenter.show("Thông báo này thuộc tài khoản khác.", isError = true)
                        return@LaunchedEffect
                    }
                    RouteCoordinator.consume(intent)
                    // iOS parity: tapping a push marks its inbox row as read.
                    intent.notificationId?.takeIf { it.isNotEmpty() }?.let { notifId ->
                        launch {
                            try {
                                APIClient.get().request("/notifications/$notifId/read", method = "PATCH", bodyJson = "{\"isRead\":true}")
                            } catch (_: Exception) {}
                        }
                    }

                    when (first) {
                        "" -> navController.navigate(FutaDestinations.DISCOVER) { popUpTo(0) }
                        "login-admin", "admin-login", "auth", "login", "dang-nhap", "signin", "auth-password" ->
                            safeNavigate(if (first == "auth-password") "auth_password" else FutaDestinations.AUTH)
                        "logout", "signout", "dang-xuat" -> {
                            AppSession.shared.logout()
                            navController.navigate(FutaDestinations.ACCOUNT)
                        }
                        "listing" -> when {
                            segments.size >= 3 && segments[1] == "agents" ->
                                safeNavigate(FutaDestinations.agentDetail(segments[2]))
                            segments.size >= 2 -> navController.navigate(
                                FutaDestinations.propertyDetail(
                                    segments[1],
                                    if (routeUri.getQueryParameter("context") == "advisor") ProductContext.ADVISOR else ProductContext.CUSTOMER
                                )
                            )
                            else -> safeNavigate(FutaDestinations.SEARCH)
                        }
                        "lucky-wheel" -> safeNavigate(FutaDestinations.LUCKY_WHEEL)
                        "chat", "tro-chuyen" -> {
                            val isAdvisorRole = AppSession.shared.role != "customer" && AppSession.shared.role != "guest"
                            val contextParam = routeUri.getQueryParameter("context")
                            val isAdvisor = contextParam == "advisor" || (isAdvisorRole && contextParam != "customer")
                            navController.navigate(
                                FutaDestinations.chat(
                                    conversationId = routeUri.getQueryParameter("conversationId"),
                                    propertyId = routeUri.getQueryParameter("propertyId") ?: routeUri.getQueryParameter("apartmentId"),
                                    context = if (isAdvisor) ProductContext.ADVISOR else ProductContext.CUSTOMER
                                )
                            )
                        }
                        "search", "properties", "danh-sach-bds" -> safeNavigate(FutaDestinations.SEARCH)
                        "workspace" -> safeNavigate(FutaDestinations.WORKSPACE)
                        "account" -> when (segments.getOrNull(1)) {
                            "profile" -> safeNavigate(FutaDestinations.PROFILE)
                            "billing" -> safeNavigate(FutaDestinations.BILLING)
                            else -> safeNavigate(FutaDestinations.ACCOUNT)
                        }
                        "saved", "favorites", "tin-da-luu", "folders" -> safeNavigate(FutaDestinations.SAVED)
                        "projects", "du-an" -> when {
                            segments.getOrNull(1) == "map" -> safeNavigate(FutaDestinations.PROJECTS_MAP)
                            segments.size >= 2 -> safeNavigate(FutaDestinations.projectDetail(segments[1]))
                            else -> safeNavigate(FutaDestinations.PROJECTS_LIST)
                        }
                        "project" -> if (segments.size >= 2) {
                            safeNavigate(FutaDestinations.projectDetail(segments[1]))
                        } else {
                            safeNavigate(FutaDestinations.PROJECTS_LIST)
                        }
                        "map" -> safeNavigate(FutaDestinations.PROJECTS_MAP)
                        "news" -> if (segments.size >= 2) {
                            safeNavigate(FutaDestinations.newsDetail(segments[1]))
                        } else {
                            safeNavigate(FutaDestinations.NEWS)
                        }
                        "pricing", "bang-gia", "upgrade" -> safeNavigate(FutaDestinations.PRICING)
                        "notifications" -> safeNavigate(FutaDestinations.NOTIFICATIONS)
                        "customers" -> routeUri.getQueryParameter("conversationId")?.takeIf { it.isNotEmpty() }?.let { convId ->
                            navController.navigate(FutaDestinations.chat(conversationId = convId))
                        } ?: safeNavigate(
                            FutaDestinations.adminCustomers(
                                segments.getOrNull(1)
                                    ?: routeUri.getQueryParameter("customerId")
                                    ?: routeUri.getQueryParameter("id")
                            )
                        )
                        "crm" -> safeNavigate(
                            FutaDestinations.crm(
                                groupId = routeUri.getQueryParameter("groupId"),
                                leadId = routeUri.getQueryParameter("leadId")
                            )
                        )
                        "contracts" -> safeNavigate(
                            FutaDestinations.adminContracts(
                                routeUri.getQueryParameter("contractId") ?: routeUri.getQueryParameter("id")
                            )
                        )
                        "reports", "report", "bao-cao" -> safeNavigate(FutaDestinations.ADMIN_REPORTS)
                        "advisor" -> when {
                            segments.getOrNull(1) == "products" -> safeNavigate(
                                FutaDestinations.advisorProducts(
                                    code = segments.getOrNull(2),
                                    booking = segments.getOrNull(3) == "booking"
                                )
                            )
                            segments.getOrNull(1) == "proposals" -> safeNavigate(FutaDestinations.ADVISOR_PROPOSALS)
                            segments.contains("registrations") -> safeNavigate(FutaDestinations.ADMIN_REGISTRATIONS)
                            else -> safeNavigate(FutaDestinations.ADVISOR)
                        }
                        "advisor-products" -> safeNavigate(FutaDestinations.ADVISOR_PRODUCTS)
                        "proposals", "de-xuat" -> safeNavigate(FutaDestinations.ADVISOR_PROPOSALS)
                        "registrations" -> safeNavigate(FutaDestinations.ADMIN_REGISTRATIONS)
                        "my-listings" -> safeNavigate(FutaDestinations.MY_LISTINGS)
                        "history", "view-history", "tin-da-xem" -> safeNavigate(FutaDestinations.VIEW_HISTORY)
                        "billing" -> safeNavigate(FutaDestinations.BILLING)
                        "profile" -> safeNavigate(FutaDestinations.PROFILE)
                        "inventory" -> safeNavigate(FutaDestinations.ADMIN_INVENTORY)
                        "transactions" -> safeNavigate(FutaDestinations.ADMIN_TRANSACTIONS)
                        "cms" -> safeNavigate(FutaDestinations.ADMIN_CMS)
                        "users" -> safeNavigate(FutaDestinations.ADMIN_USERS)
                        "admin-news" -> safeNavigate(FutaDestinations.ADMIN_CMS)
                        "admin-wheel" -> safeNavigate(FutaDestinations.ADMIN_LUCKY_WHEEL)
                        "about", "gioi-thieu" -> safeNavigate(FutaDestinations.ABOUT)
                        "contact", "lien-he", "ho-tro" -> safeNavigate(FutaDestinations.CONTACT)
                        "policies", "dieu-khoan-chinh-sach", "quy-che-hoat-dong" -> safeNavigate(FutaDestinations.POLICIES)
                        "guide", "huong-dan", "docs" -> safeNavigate(FutaDestinations.GUIDE)
                        "admin" -> when (segments.getOrNull(1).orEmpty()) {
                            "dashboard" -> safeNavigate(FutaDestinations.ADMIN_DASHBOARD)
                            "projects" -> safeNavigate(FutaDestinations.ADMIN_PROJECTS)
                            "campaigns", "sales-campaigns" -> safeNavigate(FutaDestinations.ADMIN_CAMPAIGNS)
                            "inventory", "product-inventory" -> safeNavigate(FutaDestinations.ADMIN_INVENTORY)
                            "registrations", "sales-registrations" -> safeNavigate(FutaDestinations.adminRegistrations(segments.getOrNull(2)))
                            "transactions" -> safeNavigate(FutaDestinations.ADMIN_TRANSACTIONS)
                            "cms", "news" -> safeNavigate(FutaDestinations.ADMIN_CMS)
                            "settings", "system-settings" -> safeNavigate(FutaDestinations.ADMIN_SETTINGS)
                            "users" -> safeNavigate(FutaDestinations.ADMIN_USERS)
                            "roles" -> safeNavigate(FutaDestinations.ADMIN_ROLES)
                            "advisor-profiles" -> safeNavigate(FutaDestinations.ADMIN_ADVISOR_PROFILES)
                            "ai", "ai-training" -> safeNavigate(FutaDestinations.ADMIN_AI)
                            "exams", "advisor-exams" -> safeNavigate(FutaDestinations.ADMIN_EXAMS)
                            "zalo" -> safeNavigate(FutaDestinations.ADMIN_ZALO)
                            "customers" -> safeNavigate(FutaDestinations.adminCustomers(segments.getOrNull(2)))
                            "chat" -> safeNavigate(FutaDestinations.CHAT_CENTER)
                            "contracts" -> safeNavigate(FutaDestinations.ADMIN_CONTRACTS)
                            "reports" -> safeNavigate(FutaDestinations.ADMIN_REPORTS)
                            "crm" -> safeNavigate(FutaDestinations.CRM)
                            "lucky-wheel", "wheel", "game-admin", "gameAdmin" -> safeNavigate(FutaDestinations.ADMIN_LUCKY_WHEEL)
                            else -> safeNavigate(FutaDestinations.ADMIN_DASHBOARD)
                        }
                        else -> ToastCenter.show("Liên kết không còn khả dụng hoặc chưa được hỗ trợ.", isError = true)
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
                                // Staff land on the advisor chat center (same as the
                                // bottom-bar chat button); customers/guests keep the
                                // buyer-side AI + advisor chat list.
                                val isStaffRole = AppSession.shared.isAuthenticated &&
                                    AppSession.shared.role != "customer" && AppSession.shared.role != "guest"
                                ChatScreen(
                                    staffContext = isStaffRole,
                                    productContext = if (isStaffRole) ProductContext.ADVISOR else ProductContext.CUSTOMER,
                                    onNavigate = { route -> safeNavigate(route) },
                                    onBack = if (!isRootTab) ({ navController.popBackStack() }) else null
                                )
                            }
                            // Staff chat center lives inside the advisor workspace only
                            composable(FutaDestinations.CHAT_CENTER) {
                                ChatScreen(
                                    staffContext = true,
                                    productContext = ProductContext.ADVISOR,
                                    onNavigate = { route -> safeNavigate(route) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = FutaDestinations.CHAT_ROUTE,
                                arguments = listOf(
                                    navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("advisorId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("advisorName") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("propertyId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("isAi") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("context") { type = NavType.StringType; defaultValue = "customer" }
                                )
                            ) { backStack ->
                                val convId = backStack.arguments?.getString("conversationId")
                                val advId = backStack.arguments?.getString("advisorId")
                                val advName = backStack.arguments?.getString("advisorName")
                                val propertyId = backStack.arguments?.getString("propertyId")
                                val isAi = backStack.arguments?.getString("isAi") == "true"
                                val isStaffRole = AppSession.shared.role != "customer" && AppSession.shared.role != "guest"
                                val contextArg = backStack.arguments?.getString("context")
                                val isAdvisorContext = contextArg == "advisor" || (isStaffRole && contextArg != "customer" && advId == null)
                                ChatScreen(
                                    initialConversationId = convId,
                                    targetAdvisorId = advId,
                                    targetAdvisorName = advName,
                                    targetPropertyId = propertyId,
                                    isAiChat = isAi,
                                    staffContext = isAdvisorContext,
                                    productContext = if (isAdvisorContext) ProductContext.ADVISOR else ProductContext.CUSTOMER,
                                    onNavigate = { route -> safeNavigate(route) },
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

                            // Secondary: Agent Detail (deep link /listing/agents/{id})
                            composable(
                                route = FutaDestinations.AGENT_DETAIL,
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
                            ) { backStack ->
                                AgentDetailScreen(
                                    agentId = backStack.arguments?.getString("id") ?: "",
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }

                            // Secondary: Property Detail
                            composable(
                                route = FutaDestinations.PROPERTY_DETAIL,
                                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("context") { type = NavType.StringType; defaultValue = "customer" })
                            ) { backStack ->
                                val id = backStack.arguments?.getString("id") ?: ""
                                PropertyDetailScreen(
                                    propertyId = id,
                                    productContext = if (backStack.arguments?.getString("context") == "advisor") ProductContext.ADVISOR else ProductContext.CUSTOMER,
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
                                    onNavigate = { route -> RouteCoordinator.enqueue(route) }
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
                            composable(
                                route = FutaDestinations.ADMIN_REGISTRATIONS_ROUTE,
                                arguments = listOf(navArgument("propertyId") { type = NavType.StringType; nullable = true; defaultValue = null })
                            ) { backStack ->
                                AdminRegistrationsScreen(
                                    onBack = { navController.popBackStack() },
                                    initialPropertyId = backStack.arguments?.getString("propertyId")
                                )
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
                            composable(
                                route = FutaDestinations.ADMIN_CUSTOMERS_ROUTE,
                                arguments = listOf(navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null })
                            ) { backStack ->
                                AdminCustomersScreen(
                                    onBack = { navController.popBackStack() },
                                    initialCustomerId = backStack.arguments?.getString("customerId")
                                )
                            }
                            composable(
                                route = FutaDestinations.ADMIN_CONTRACTS_ROUTE,
                                arguments = listOf(navArgument("contractId") { type = NavType.StringType; nullable = true; defaultValue = null })
                            ) { backStack ->
                                AdminContractsScreen(
                                    onBack = { navController.popBackStack() },
                                    initialContractId = backStack.arguments?.getString("contractId")
                                )
                            }
                            composable(FutaDestinations.ADMIN_REPORTS) {
                                AdminReportsScreen { navController.popBackStack() }
                            }
                            composable(FutaDestinations.ADMIN_DASHBOARD) {
                                AdminDashboardScreen { navController.popBackStack() }
                            }
                            composable(
                                route = FutaDestinations.CRM_ROUTE,
                                arguments = listOf(
                                    navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("leadId") { type = NavType.StringType; nullable = true; defaultValue = null }
                                )
                            ) { backStack ->
                                AdminCRMScreen(
                                    onBack = { navController.popBackStack() },
                                    initialGroupId = backStack.arguments?.getString("groupId"),
                                    initialLeadId = backStack.arguments?.getString("leadId")
                                )
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
                            composable(
                                route = FutaDestinations.ADVISOR_PRODUCTS_ROUTE,
                                arguments = listOf(
                                    navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("booking") { type = NavType.StringType; nullable = true; defaultValue = null }
                                )
                            ) { backStack ->
                                AdvisorProductsScreen(
                                    initialProductCode = backStack.arguments?.getString("code"),
                                    bookingIntent = backStack.arguments?.getString("booking") == "true",
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
                            composable(FutaDestinations.VIEWING_APPOINTMENTS) {
                                ViewingAppointmentsScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigate = { route -> safeNavigate(route) }
                                )
                            }
                        }
                    }
                    // Toast System Overlay
                    FutaToastOverlay()

                    AppUpdateDialog(
                        visible = PlayStoreUpdateManager.shouldShowAlert.value,
                        installedVersionName = PlayStoreUpdateManager.installedVersionName.value,
                        availableVersionCode = PlayStoreUpdateManager.availableVersionCode.value,
                        onUpdate = { PlayStoreUpdateManager.actOnUpdate(this@MainActivity) },
                        onLater = { PlayStoreUpdateManager.dismiss(this@MainActivity) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PlayStoreUpdateManager.checkForUpdates(this)
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

        // FCM notification tap → navigate to its target route (e.g. /chat).
        val isNotificationTap = intent?.getBooleanExtra(FutaMessagingService.EXTRA_NOTIFICATION_TAP, false) == true
        val fcmRoute = intent?.getStringExtra(FutaMessagingService.EXTRA_ROUTE)
        if (isNotificationTap) {
            if (!fcmRoute.isNullOrEmpty()) {
                RouteCoordinator.enqueue(
                    fcmRoute,
                    userId = intent.getStringExtra(FutaMessagingService.EXTRA_USER_ID),
                    notificationId = intent.getStringExtra(FutaMessagingService.EXTRA_NOTIFICATION_ID)
                )
            } else {
                // iOS parity: a push without a usable link surfaces an error.
                ToastCenter.show("Thông báo không còn khả dụng. Vui lòng mở hộp thông báo.", isError = true)
            }
            return
        }

        val uri = intent?.data ?: return
        RouteCoordinator.enqueue(uri.toString())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            } else {
                FcmRegistrar.ensureRegistered(this)
            }
        } else {
            FcmRegistrar.ensureRegistered(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            FcmRegistrar.ensureRegistered(this)
        }
    }
}
