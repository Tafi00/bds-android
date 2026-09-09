package vn.futaland.app.navigation

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
    const val PROPERTY_DETAIL = "property_detail/{id}"
    const val PROJECT_DETAIL = "project_detail/{id}"
    const val PROJECTS_LIST = "projects_list"
    const val NEWS_DETAIL = "news_detail/{slug}"
    const val LUCKY_WHEEL = "lucky_wheel"
    const val WORKSPACE = "workspace"
    const val CHAT_CONVERSATION = "chat_conversation/{conversationId}"
    const val NOTIFICATIONS = "notifications"
    const val PRICING = "pricing"

    // Admin & Advisor Modules
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_INVENTORY = "admin_inventory"
    const val ADMIN_REGISTRATIONS = "admin_registrations"
    const val ADMIN_PROJECTS = "admin_projects"
    const val ADMIN_CAMPAIGNS = "admin_campaigns"
    const val ADMIN_TRANSACTIONS = "admin_transactions"
    const val ADMIN_CMS = "admin_cms"
    const val ADMIN_SETTINGS = "admin_settings"
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_ROLES = "admin_roles"
    const val ADMIN_ADVISOR_PROFILES = "admin_advisor_profiles"
    const val ADMIN_AI = "admin_ai"
    const val ADMIN_ZALO = "admin_zalo"
    const val ADMIN_CUSTOMERS = "admin_customers"
    const val ADMIN_CONTRACTS = "admin_contracts"
    const val ADMIN_REPORTS = "admin_reports"
    const val CRM = "crm"
    const val ADVISOR = "advisor"
    fun propertyDetail(id: String) = "property_detail/$id"
    fun projectDetail(id: String) = "project_detail/$id"
    fun newsDetail(slug: String) = "news_detail/$slug"
    fun chatConversation(conversationId: String) = "chat_conversation/$conversationId"
}
