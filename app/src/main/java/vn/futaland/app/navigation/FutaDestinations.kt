package vn.futaland.app.navigation

import vn.futaland.app.core.sales.ProductContext

object FutaDestinations {
    // 5 Main Tabs
    const val DISCOVER = "tab_discover"
    const val SAVED = "tab_saved"
    const val INBOX = "tab_inbox"
    const val ACCOUNT = "tab_account"
    const val SEARCH = "tab_search"

    // Auth
    const val AUTH = "auth"

    // Detail & Secondary Screens
    const val PROPERTY_DETAIL = "property_detail/{id}?context={context}"
    const val PROJECT_DETAIL = "project_detail/{id}"
    const val PROJECTS_LIST = "projects_list"
    const val PROJECTS_MAP = "projects_map"
    const val NEWS_DETAIL = "news_detail/{slug}"
    const val LUCKY_WHEEL = "lucky_wheel"
    const val WORKSPACE = "workspace"
    const val CHAT_CENTER = "chat_center"
    const val CHAT_CONVERSATION = "chat_conversation/{conversationId}"
    const val CHAT_ROUTE = "chat?conversationId={conversationId}&advisorId={advisorId}&advisorName={advisorName}&propertyId={propertyId}&isAi={isAi}&context={context}"

    fun chat(
        conversationId: String? = null,
        advisorId: String? = null,
        advisorName: String? = null,
        propertyId: String? = null,
        context: ProductContext = ProductContext.CUSTOMER,
        isAi: Boolean = false
    ): String {
        val params = mutableListOf<String>()
        if (!conversationId.isNullOrEmpty()) params.add("conversationId=$conversationId")
        if (!advisorId.isNullOrEmpty()) params.add("advisorId=$advisorId")
        if (!advisorName.isNullOrEmpty()) params.add("advisorName=${android.net.Uri.encode(advisorName)}")
        if (!propertyId.isNullOrEmpty()) params.add("propertyId=$propertyId")
        if (isAi) params.add("isAi=true")
        if (context == ProductContext.ADVISOR) params.add("context=advisor")
        return if (params.isEmpty()) INBOX else "chat?${params.joinToString("&")}"
    }
    const val NOTIFICATIONS = "notifications"
    const val PRICING = "pricing"
    const val PROFILE = "profile"
    const val ADMIN_LUCKY_WHEEL = "admin_lucky_wheel"

    // Deep-link variants carrying an initial target (notification taps, web routes).
    const val AGENT_DETAIL = "agent_detail/{id}"
    const val ADMIN_CONTRACTS_ROUTE = "admin_contracts?contractId={contractId}"
    const val ADMIN_CUSTOMERS_ROUTE = "admin_customers?customerId={customerId}"
    const val CRM_ROUTE = "crm?groupId={groupId}&leadId={leadId}"
    const val ADVISOR_PRODUCTS_ROUTE = "advisor_products?code={code}&booking={booking}"
    fun agentDetail(id: String) = "agent_detail/${android.net.Uri.encode(id)}"
    fun adminContracts(contractId: String? = null) =
        if (contractId.isNullOrEmpty()) ADMIN_CONTRACTS else "admin_contracts?contractId=${android.net.Uri.encode(contractId)}"
    fun adminCustomers(customerId: String? = null) =
        if (customerId.isNullOrEmpty()) ADMIN_CUSTOMERS else "admin_customers?customerId=${android.net.Uri.encode(customerId)}"
    fun adminRegistrations(propertyId: String? = null) =
        if (propertyId.isNullOrEmpty()) ADMIN_REGISTRATIONS else "admin_registrations?propertyId=${android.net.Uri.encode(propertyId)}"
    fun crm(groupId: String? = null, leadId: String? = null): String {
        val params = mutableListOf<String>()
        if (!groupId.isNullOrEmpty()) params.add("groupId=${android.net.Uri.encode(groupId)}")
        if (!leadId.isNullOrEmpty()) params.add("leadId=${android.net.Uri.encode(leadId)}")
        return if (params.isEmpty()) CRM else "crm?${params.joinToString("&")}"
    }
    fun advisorProducts(code: String? = null, booking: Boolean = false): String {
        val params = mutableListOf<String>()
        if (!code.isNullOrEmpty()) params.add("code=${android.net.Uri.encode(code)}")
        if (booking) params.add("booking=true")
        return if (params.isEmpty()) ADVISOR_PRODUCTS else "advisor_products?${params.joinToString("&")}"
    }
    const val NEWS = "news"
    const val GUIDE = "guide"
    const val CONTACT = "contact"
    const val ABOUT = "about"
    const val POLICIES = "policies"
    const val BILLING = "billing"
    const val MY_LISTINGS = "my_listings"
    const val VIEW_HISTORY = "view_history"
    // Admin & Advisor Modules
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_INVENTORY = "admin_inventory"
    const val ADMIN_REGISTRATIONS = "admin_registrations"
    const val ADMIN_REGISTRATIONS_ROUTE = "admin_registrations?propertyId={propertyId}"
    const val ADMIN_PROJECTS = "admin_projects"
    const val ADMIN_CAMPAIGNS = "admin_campaigns"
    const val ADMIN_TRANSACTIONS = "admin_transactions"
    const val ADMIN_CMS = "admin_cms"
    const val ADMIN_SETTINGS = "admin_settings"
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_ROLES = "admin_roles"
    const val ADMIN_ADVISOR_PROFILES = "admin_advisor_profiles"
    const val ADMIN_AI = "admin_ai"
    const val ADMIN_EXAMS = "admin_exams"
    const val ADMIN_ZALO = "admin_zalo"
    const val ADMIN_CUSTOMERS = "admin_customers"
    const val ADMIN_CONTRACTS = "admin_contracts"
    const val ADMIN_REPORTS = "admin_reports"
    const val CRM = "crm"
    const val ADVISOR = "advisor"
    const val ADVISOR_PACKAGE = "advisor_package"
    const val ADVISOR_PROFILE = "advisor_profile"
    const val ADVISOR_EXAM = "advisor_exam"
    const val ADVISOR_VERIFICATION = "advisor_verification"
    const val ADVISOR_PROPOSALS = "advisor_proposals"
    const val ADVISOR_PRODUCTS = "advisor_products"
    const val ADVISOR_REGISTRATIONS = "advisor_registrations"
    const val SEARCH_ROUTE = "tab_search?propertyType={propertyType}"
    fun search(propertyType: String? = null) = if (propertyType != null) "tab_search?propertyType=$propertyType" else "tab_search"
    fun propertyDetail(id: String, context: ProductContext = ProductContext.CUSTOMER) = "property_detail/${android.net.Uri.encode(id)}?context=${context.wire}"
    fun projectDetail(id: String) = "project_detail/$id"
    fun newsDetail(slug: String) = "news_detail/$slug"
    fun chatConversation(conversationId: String) = "chat_conversation/$conversationId"
}
