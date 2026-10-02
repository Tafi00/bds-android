package vn.futaland.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.features.account.STAFF_APPOINTMENT_ROLES

/**
 * Who may open each route (iOS `NativeModule.access`, NativeNavigation.swift). Deep links and
 * notification taps reach any route directly, so the check runs on the destination itself, not
 * only on the workspace tiles. Routes without an entry are public.
 */
object RouteAccess {
    private val admin = NativeAccess.Permissions(listOf("admin:access"))
    private val signedIn = NativeAccess.SignedIn

    private val rules: Map<String, NativeAccess> = mapOf(
        FutaDestinations.ADMIN_DASHBOARD to admin,
        FutaDestinations.ADMIN_INVENTORY to admin,
        FutaDestinations.ADMIN_REGISTRATIONS to admin,
        FutaDestinations.ADMIN_PROJECTS to NativeAccess.Permissions(listOf("projects:view", "projects:edit", "admin:access")),
        FutaDestinations.ADMIN_CAMPAIGNS to admin,
        FutaDestinations.ADMIN_TRANSACTIONS to admin,
        FutaDestinations.ADMIN_CMS to admin,
        FutaDestinations.ADMIN_SETTINGS to admin,
        FutaDestinations.ADMIN_USERS to admin,
        FutaDestinations.ADMIN_ROLES to admin,
        FutaDestinations.ADMIN_ADVISOR_PROFILES to admin,
        FutaDestinations.ADMIN_AI to admin,
        FutaDestinations.ADMIN_EXAMS to admin,
        FutaDestinations.ADMIN_LUCKY_WHEEL to admin,
        FutaDestinations.ADMIN_ZALO to NativeAccess.Permissions(listOf("zalo:view", "zalo:manage")),
        FutaDestinations.ADMIN_CUSTOMERS to NativeAccess.Permissions(listOf("customers:view")),
        FutaDestinations.CRM to NativeAccess.Permissions(listOf("customers:view")),
        FutaDestinations.ADMIN_CONTRACTS to NativeAccess.Permissions(listOf("contracts:view")),
        FutaDestinations.ADMIN_REPORTS to NativeAccess.Permissions(listOf("reports:view")),
        FutaDestinations.CHAT_CENTER to NativeAccess.Permissions(listOf("chat:view")),
        FutaDestinations.ADVISOR_PRODUCTS to NativeAccess.Permissions(listOf("apartments:view")),
        FutaDestinations.MY_LISTINGS to NativeAccess.Permissions(listOf("apartments:view")),
        FutaDestinations.STAFF_APPOINTMENTS to NativeAccess.Roles(STAFF_APPOINTMENT_ROLES),
        "staff_appointment_detail" to NativeAccess.Roles(STAFF_APPOINTMENT_ROLES),
        FutaDestinations.WORKSPACE to signedIn,
        FutaDestinations.ADVISOR to signedIn,
        FutaDestinations.ADVISOR_PACKAGE to signedIn,
        FutaDestinations.ADVISOR_PROFILE to signedIn,
        FutaDestinations.ADVISOR_EXAM to signedIn,
        FutaDestinations.ADVISOR_VERIFICATION to signedIn,
        FutaDestinations.ADVISOR_PROPOSALS to signedIn,
        FutaDestinations.ADVISOR_REGISTRATIONS to signedIn,
        FutaDestinations.PROFILE to signedIn,
        FutaDestinations.BILLING to signedIn,
        FutaDestinations.VIEW_HISTORY to signedIn,
        FutaDestinations.VIEWING_APPOINTMENTS to signedIn,
        FutaDestinations.LUCKY_WHEEL to signedIn,
        FutaDestinations.NOTIFICATIONS to signedIn,
    )

    /** Rule for a route pattern such as `admin_contracts?contractId={contractId}`. */
    fun accessFor(route: String): NativeAccess? =
        rules[route.substringBefore('?').substringBefore('/')] ?: rules[route]
}

/** Opens the sign-in screen from an access gate; provided by `MainActivity` around the NavHost. */
val LocalRequireLogin = compositionLocalOf<() -> Unit> { {} }

/** `composable` that applies [RouteAccess] to the destination before showing [content]. */
fun NavGraphBuilder.gatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    val access = RouteAccess.accessFor(route)
    composable(route = route, arguments = arguments, deepLinks = deepLinks) { entry ->
        if (access == null) {
            content(entry)
        } else {
            FutaAccessGate(access = access, session = AppSession.shared, onRequireLogin = LocalRequireLogin.current) {
                content(entry)
            }
        }
    }
}
