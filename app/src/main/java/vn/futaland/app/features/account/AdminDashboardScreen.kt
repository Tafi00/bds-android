package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.i18n.I18n
import vn.futaland.app.core.i18n.AppLanguage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.*
import java.text.NumberFormat
import java.util.Locale

// Mirrors iOS Features/CMS/AdminDashboardView.swift:
// overview + pricing plans from GET /cms/admin, paged orders from GET /cms/orders,
// order confirm via POST /pricing/orders/:orderId/confirm, plan edit via PUT /cms/pricing-plans.

private sealed interface DashboardRoute {
    data class OrderDetail(val order: JSONValue) : DashboardRoute
    data class PlanEdit(val plan: JSONValue) : DashboardRoute
}

private val orderStatusTabs = listOf(
    "all" to "Tất cả",
    "pending" to "Chờ duyệt",
    "paid" to "Đã thanh toán",
    "expired" to "Hết hạn",
    "cancelled" to "Đã huỷ"
)

/** iOS `formatVND`: grouped digits with "₫" in Vietnamese, localized price otherwise. */
private fun formatVnd(value: Double): String {
    if (I18n.language != AppLanguage.VI) return LocalizedPrice.full(value)
    if (value == 0.0) return "0 ₫"
    val formatted = NumberFormat.getIntegerInstance(Locale.GERMANY).format(Math.round(value))
    return "$formatted ₫"
}

private fun orderStatusInfo(status: String): Pair<String, Color> = when (status) {
    "paid" -> "Đã thanh toán" to FutaColors.BrandGreen
    "pending" -> "Chờ xử lý" to Color(0xFFF97316)
    "expired" -> "Hết hạn" to FutaColors.Slate
    "cancelled" -> "Đã huỷ" to Color(0xFFDC2626)
    else -> status.ifEmpty { "-" } to Color(0xFF2563EB)
}

/**
 * Body item for `PUT /cms/pricing-plans` (cmsPricingPlansSchema): only the fields the backend
 * accepts, limits kept as null (= unlimited) when unset.
 */
private fun planPayload(item: JSONValue) = buildJsonObject {
    fun limit(v: JSONValue) = if (v.isNull) JsonNull else JsonPrimitive(v.int)
    put("id", item["id"].string)
    put("name", item["name"].string)
    put("monthlyPrice", Math.round(item["monthlyPrice"].double))
    put("sixMonthDiscount", item["sixMonthDiscount"].double)
    put("dailyListingLimit", limit(item["dailyListingLimit"]))
    put("activeListingLimit", limit(item["activeListingLimit"]))
    put("dailyBoostLimit", item["dailyBoostLimit"].int)
    put("features", JsonArray(item["features"].array.map { it.string.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) }))
    put("isEnabled", item["isEnabled"].bool)
    if (!item["sortOrder"].isNull) put("sortOrder", item["sortOrder"].int)
}

@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<DashboardRoute>() }
    var overview by remember { mutableStateOf<JSONValue?>(null) }
    var pricingPlans by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var orders by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var ordersLoading by remember { mutableStateOf(true) }
    var ordersError by remember { mutableStateOf<String?>(null) }
    var orderStatus by remember { mutableStateOf("all") }
    var orderSearch by remember { mutableStateOf("") }
    var appliedSearch by remember { mutableStateOf("") }
    var orderPage by remember { mutableIntStateOf(1) }
    var totalOrderPages by remember { mutableIntStateOf(1) }
    var totalOrders by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    suspend fun fetchOverview() {
        try {
            val data = APIClient.get().request("/cms/admin")["data"]
            overview = data
            pricingPlans = data["pricingPlans"].array
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        } finally {
            loading = false
        }
    }

    suspend fun fetchOrders() {
        ordersLoading = true
        try {
            val query = mutableMapOf("page" to "$orderPage", "limit" to "10")
            if (orderStatus != "all") query["status"] = orderStatus
            if (appliedSearch.isNotBlank()) query["search"] = appliedSearch.trim()
            // Backend: `{ data: [...orders], pagination: { page, limit, total, totalPages } }`.
            val res = APIClient.get().request("/cms/orders", query = query)
            orders = res["data"].array
            totalOrderPages = maxOf(1, res["pagination"]["totalPages"].int)
            totalOrders = res["pagination"]["total"].int
            ordersError = null
        } catch (e: Exception) {
            ordersError = e.message ?: tr("Không thể tải danh sách đơn hàng")
        } finally {
            ordersLoading = false
        }
    }

    fun reloadAll() {
        scope.launch {
            launch { fetchOverview() }
            launch { fetchOrders() }
        }
    }

    LaunchedEffect(Unit) { fetchOverview() }
    LaunchedEffect(orderStatus, appliedSearch, orderPage) { fetchOrders() }

    ScreenStackHost(
        stack = stack,
        base = {
            Scaffold(
                containerColor = FutaColors.PageBg,
                topBar = {
                    SalesAdminTopBar(title = "Bảng điều khiển", onBack = onBack) {
                        FutaHeaderIconButton(icon = Icons.Default.Refresh, contentDescription = tr("Tải lại"), onClick = { loading = overview == null; reloadAll() })
                    }
                }
            ) { padding ->
                val ov = overview
                when {
                    ov == null && loading -> Column(Modifier.padding(padding).padding(16.dp)) { FutaDashboardSkeleton() }
                    ov == null -> AdminErrorState(loadError.orEmpty(), { loading = true; reloadAll() }, Modifier.padding(padding))
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item { DashboardMetrics(ov["dashboard"]) }
                        item {
                            SectionHeader("Gói tin & Bảng giá", "Cấu hình phí đăng tin và quyền lợi người dùng")
                        }
                        if (pricingPlans.isEmpty()) {
                            item {
                                FutaEmptyState(title = "Chưa có gói tin nào được cấu hình", message = "", icon = Icons.Default.LocalOffer)
                            }
                        } else {
                            items(pricingPlans, key = { "plan-" + it["id"].string }) { plan ->
                                PricingTierCard(plan) { stack.push(DashboardRoute.PlanEdit(plan)) }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Spacer(Modifier.height(6.dp))
                                SectionHeader("Quản lý đơn hàng", tr("Tổng cộng {0} đơn", totalOrders))
                                QuickChipRow {
                                    orderStatusTabs.forEach { (id, title) ->
                                        QuickChip(title, orderStatus == id) {
                                            if (orderStatus != id) { orderStatus = id; orderPage = 1 }
                                        }
                                    }
                                }
                                FutaInput(
                                    value = orderSearch,
                                    onValueChange = {
                                        orderSearch = it
                                        if (it.isEmpty() && appliedSearch.isNotEmpty()) { appliedSearch = ""; orderPage = 1 }
                                    },
                                    placeholder = "Tìm theo mã đơn, khách hàng, SĐT...",
                                    leadingIcon = Icons.Default.Search,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { appliedSearch = orderSearch.trim(); orderPage = 1 }),
                                    trailingIcon = {
                                        Text(
                                            "Tìm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen,
                                            modifier = Modifier.clickable { appliedSearch = orderSearch.trim(); orderPage = 1 }.padding(horizontal = 6.dp)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        when {
                            ordersLoading && orders.isEmpty() -> items(4) { FutaAdminRowSkeleton() }
                            ordersError != null && orders.isEmpty() -> item {
                                AdminErrorState(ordersError.orEmpty(), { scope.launch { fetchOrders() } })
                            }
                            orders.isEmpty() -> item {
                                val filtered = orderStatus != "all" || appliedSearch.isNotEmpty()
                                FutaEmptyState(
                                    title = if (filtered) "Không có đơn hàng nào" else "Chưa có đơn hàng nào",
                                    message = if (filtered) "Không tìm thấy đơn hàng nào phù hợp với bộ lọc hiện tại." else "Các đơn mua gói tin sẽ xuất hiện tại đây.",
                                    icon = Icons.Default.Inbox,
                                    actionButton = if (filtered) {
                                        { FutaButton(text = "Xóa bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = { orderStatus = "all"; orderSearch = ""; appliedSearch = ""; orderPage = 1 }) }
                                    } else null
                                )
                            }
                            else -> {
                                items(orders, key = { "order-" + it["id"].string }) { order ->
                                    DashboardOrderCard(order, dimmed = ordersLoading) { stack.push(DashboardRoute.OrderDetail(order)) }
                                }
                                item {
                                    PaginationBar(
                                        currentPage = orderPage,
                                        totalPages = totalOrderPages,
                                        rangeText = tr("{0} đơn", totalOrders),
                                        onPrevious = { if (orderPage > 1) { orderPage -= 1; scope.launch { listState.animateScrollToItem(0) } } },
                                        onNext = { if (orderPage < totalOrderPages) orderPage += 1 }
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    ) { route ->
        when (route) {
            is DashboardRoute.OrderDetail -> DashboardOrderDetailScreen(
                order = route.order,
                onBack = { stack.pop() },
                onConfirmed = { stack.pop(); reloadAll() }
            )
            is DashboardRoute.PlanEdit -> PricingPlanEditScreen(
                plan = route.plan,
                onClose = { stack.pop() },
                onSave = { edited ->
                    // The backend replaces the whole list: send every plan, the edited one merged in.
                    val id = route.plan["id"].string
                    val plans = pricingPlans.map { p -> if (p["id"].string == id) planPayload(edited) else planPayload(p) }
                    APIClient.get().request(
                        "/cms/pricing-plans", method = "PUT",
                        bodyJson = buildJsonObject { put("plans", JsonArray(plans)) }.toString()
                    )
                    ToastCenter.show(tr("Cập nhật gói tin thành công!"))
                    stack.pop()
                    reloadAll()
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
        Text(subtitle, fontSize = 12.sp, color = FutaColors.Slate)
    }
}

@Composable
private fun DashboardMetrics(d: JSONValue) {
    val pending = d["pendingOrders"].int
    val total = d["totalOrders"].int
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Số liệu tổng quan", "Chỉ số kinh doanh và vận hành theo thời gian thực")
        MetricGrid(
            listOf(
                { m -> SalesMetricCard("Doanh thu toàn bộ", formatVnd(d["totalRevenue"].double), Icons.Default.TrendingUp, FutaColors.BrandGreen, m, subtitle = "Toàn thời gian") },
                { m -> SalesMetricCard("Doanh thu tháng này", formatVnd(d["monthRevenue"].double), Icons.Default.CalendarMonth, Color(0xFF0284C7), m, subtitle = "Tháng hiện tại") },
                { m -> SalesMetricCard("Đơn mới hôm nay", "${d["newOrdersToday"].int}", Icons.Default.ShoppingBag, Color(0xFFF97316), m, subtitle = "Hôm nay") },
                { m ->
                    SalesMetricCard(
                        "Đơn chờ xử lý", "$pending", Icons.Default.Schedule,
                        if (pending > 0) Color(0xFFDC2626) else Color(0xFF7C3AED), m,
                        subtitle = if (pending > 0) "Xử lý ngay" else "Hoàn tất", selected = pending > 0
                    )
                },
                { m -> SalesMetricCard("Đã hoàn thành", "${maxOf(0, total - pending)}", Icons.Default.CheckCircle, FutaColors.BrandGreen, m, subtitle = "Đã thanh toán") },
                { m -> SalesMetricCard("Tổng đơn dịch vụ", "$total", Icons.Default.ShoppingCart, Color(0xFF4F46E5), m, subtitle = "Tất cả đơn") },
                { m -> SalesMetricCard("Người dùng đăng ký", "${d["totalUsers"].int}", Icons.Default.People, Color(0xFF0D9488), m, subtitle = "Tài khoản hệ thống") },
                { m -> SalesMetricCard("Tin đăng BĐS", "${d["totalListings"].int}", Icons.Default.Apartment, Color(0xFF92400E), m, subtitle = "Kho căn & sản phẩm") }
            )
        )
    }
}

@Composable
private fun PricingTierCard(plan: JSONValue, onEdit: () -> Unit) {
    val code = plan["name"].string.uppercase()
    val tierColor = when {
        code.contains("VIP") -> Color(0xFFD97706)
        code.contains("PRO") -> FutaColors.BrandGreen
        else -> FutaColors.Slate
    }
    val tierIcon = when {
        code.contains("VIP") -> Icons.Default.WorkspacePremium
        code.contains("PRO") -> Icons.Default.Star
        else -> Icons.Default.AutoAwesome
    }
    val enabled = plan["isEnabled"].bool
    val discount = Math.round(plan["sixMonthDiscount"].double * 100).toInt()
    val limit = plan["activeListingLimit"]
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(tierColor.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(tierIcon, null, tint = tierColor, modifier = Modifier.size(13.dp))
                    VerbatimText(plan["name"].string, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                }
                Spacer(Modifier.weight(1f))
                StatusPill(if (enabled) "Đang áp dụng" else "Tạm tắt", if (enabled) Color(0xFF15803D) else FutaColors.Slate, dot = true)
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(FutaColors.BrandGreen.copy(alpha = 0.1f)).clickable(onClick = onEdit).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Edit, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(12.dp))
                    Text("Sửa", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatVnd(plan["monthlyPrice"].double), fontSize = 22.sp, fontWeight = FontWeight.Black, color = FutaColors.Navy)
                Text("/ tháng", fontSize = 13.sp, color = FutaColors.Slate, modifier = Modifier.padding(bottom = 3.dp))
            }
            if (discount > 0) {
                StatusPill(tr("Giảm {0}% khi đóng 6 tháng", discount), Color(0xFF059669))
            }
            HorizontalDivider(color = FutaColors.PanelDivider)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                Text("Hạn mức:", fontSize = 12.5.sp, color = FutaColors.Slate)
                Text(
                    if (!limit.isNull) tr("{0} tin đăng hoạt động", limit.int) else tr("Không giới hạn tin đăng"),
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy
                )
            }
        }
    }
}

@Composable
private fun DashboardOrderCard(order: JSONValue, dimmed: Boolean, onClick: () -> Unit) {
    val (statusTitle, statusColor) = orderStatusInfo(order["status"].string)
    Surface(
        shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, FutaColors.LightBlueBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).alpha(if (dimmed) 0.5f else 1f)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Description, null, tint = FutaColors.Slate, modifier = Modifier.size(14.dp))
                Text("#" + order["id"].string.take(8).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = FutaColors.Navy)
                Spacer(Modifier.weight(1f))
                StatusPill(statusTitle, statusColor)
            }
            val title = order["listingTitle"].string
            if (title.isNotEmpty()) {
                VerbatimText(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = FutaColors.PanelDivider)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val name = order["user"]["name"].string
                if (name.isNotEmpty()) {
                    Icon(Icons.Default.AccountCircle, null, tint = FutaColors.Slate, modifier = Modifier.size(14.dp))
                    VerbatimText(name, fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                Text(formatVnd(order["amount"].double), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DashboardOrderDetailScreen(order: JSONValue, onBack: () -> Unit, onConfirmed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val (statusTitle, statusColor) = orderStatusInfo(order["status"].string)
    val isPending = order["status"].string == "pending"

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = "Chi tiết đơn hàng", subtitle = "#" + order["id"].string.take(8).uppercase(), onBack = onBack) },
        bottomBar = {
            if (isPending) {
                FutaStickyActionBar {
                    FutaButton(
                        text = if (confirming) "Đang xử lý…" else "Duyệt và xác nhận thanh toán",
                        icon = if (confirming) null else Icons.Default.CheckCircle,
                        enabled = !confirming,
                        onClick = { showConfirm = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DetailSection("Thông tin đơn hàng", Icons.Default.Description, trailing = { StatusPill(statusTitle, statusColor) }) {
                InfoRow("Mã đơn", order["id"].string, verbatim = true)
                InfoRow("Số tiền thanh toán", formatVnd(order["amount"].double), valueColor = FutaColors.BrandGreen)
                InfoRow("Gói", order["planId"].string.uppercase(), verbatim = true)
                InfoRow("Hình thức", order["paymentMethod"].string, verbatim = true)
                InfoRow("Ngày tạo", SalesFormatters.dateTime(order["createdAt"].string))
                if (order["paidAt"].string.isNotEmpty()) InfoRow("Ngày thanh toán", SalesFormatters.dateTime(order["paidAt"].string))
            }
            val user = order["user"]
            if (user["name"].string.isNotEmpty() || user["phone"].string.isNotEmpty()) {
                DetailSection("Khách hàng", Icons.Default.Person) {
                    InfoRow("Họ và tên", user["name"].string, verbatim = true)
                    InfoRow("Số điện thoại", user["phone"].string, verbatim = true)
                    InfoRow("Email", user["email"].string, verbatim = true)
                }
            }
            if (order["listingTitle"].string.isNotEmpty()) {
                DetailSection("Tin bất động sản", Icons.Default.Apartment) {
                    InfoRow("Tiêu đề tin", order["listingTitle"].string, verbatim = true)
                    InfoRow("Mã tin BĐS", order["apartmentId"].string, verbatim = true)
                }
            }
        }
    }

    ConfirmDialog(
        visible = showConfirm,
        title = "Xác nhận thanh toán?",
        message = tr("Xác nhận đơn {0} đã được thanh toán. Gói tin sẽ được kích hoạt cho khách hàng.", "#" + order["id"].string.take(8).uppercase()),
        confirmText = "Xác nhận",
        onDismiss = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            scope.launch {
                confirming = true
                try {
                    APIClient.get().request("/pricing/orders/${order["id"].string}/confirm", method = "POST")
                    ToastCenter.show(tr("Xác nhận thanh toán đơn hàng thành công!"))
                    onConfirmed()
                } catch (e: Exception) {
                    ToastCenter.show(tr("Không thể xác nhận đơn: {0}", e.message.orEmpty()), isError = true)
                } finally {
                    confirming = false
                }
            }
        }
    )
}

/** Plan editor (iOS `DashboardPricingPlanEditView`), limited to the fields the backend stores. */
@Composable
private fun PricingPlanEditScreen(plan: JSONValue, onClose: () -> Unit, onSave: suspend (JSONValue) -> Unit) {
    val scope = rememberCoroutineScope()
    fun limitText(v: JSONValue) = if (v.isNull) "" else "${v.int}"
    val initial = remember {
        listOf(
            plan["name"].string,
            "${Math.round(plan["monthlyPrice"].double)}",
            "${Math.round(plan["sixMonthDiscount"].double * 100)}",
            limitText(plan["activeListingLimit"]),
            limitText(plan["dailyListingLimit"]),
            "${plan["dailyBoostLimit"].int}",
            plan["features"].array.joinToString("\n") { it.string },
            plan["isEnabled"].bool.toString()
        )
    }
    var name by remember { mutableStateOf(initial[0]) }
    var price by remember { mutableStateOf(initial[1]) }
    var discount by remember { mutableStateOf(initial[2]) }
    var activeLimit by remember { mutableStateOf(initial[3]) }
    var dailyLimit by remember { mutableStateOf(initial[4]) }
    var boostLimit by remember { mutableStateOf(initial[5]) }
    var features by remember { mutableStateOf(initial[6]) }
    var enabled by remember { mutableStateOf(plan["isEnabled"].bool) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    val dirty = listOf(name, price, discount, activeLimit, dailyLimit, boostLimit, features, enabled.toString()) != initial
    fun close() { if (dirty) showDiscard = true else onClose() }
    androidx.activity.compose.BackHandler { close() }

    fun digits(s: String) = s.filter { it.isDigit() }

    fun save() {
        val priceValue = price.toLongOrNull()
        val discountValue = discount.ifBlank { "0" }.toIntOrNull()
        error = when {
            name.isBlank() -> tr("Vui lòng nhập tên gói")
            priceValue == null -> tr("Giá tháng không hợp lệ")
            discountValue == null || discountValue !in 0..95 -> tr("Chiết khấu 6 tháng phải từ 0 đến 95%")
            features.lines().count { it.isNotBlank() } > 20 -> tr("Tối đa 20 quyền lợi")
            else -> null
        }
        if (error != null) return
        fun limit(s: String): JSONValue = s.toIntOrNull()?.let { JSONValue(JsonPrimitive(it)) } ?: JSONValue.Null
        val edited = plan.withUpdates(
            mapOf(
                "name" to name.trim(),
                "monthlyPrice" to priceValue,
                "sixMonthDiscount" to (discountValue ?: 0) / 100.0,
                "activeListingLimit" to limit(activeLimit),
                "dailyListingLimit" to limit(dailyLimit),
                "dailyBoostLimit" to (boostLimit.toIntOrNull() ?: 0),
                "features" to JSONValue(JsonArray(features.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) })),
                "isEnabled" to enabled
            )
        )
        scope.launch {
            saving = true
            try {
                onSave(edited)
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu gói tin"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = tr("Sửa gói: {0}", plan["name"].string), onBack = { close() }) {
                StatusPill(if (enabled) "Đang áp dụng" else "Tạm tắt", if (enabled) Color(0xFF15803D) else FutaColors.Slate, dot = true)
            }
        },
        bottomBar = { FormActionBar("Lưu thay đổi", saving, enabled = dirty, onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            FormSection("Thông tin cơ bản") {
                FormTextField("Tên gói", name, { name = it }, required = true)
                FormToggle("Kích hoạt gói này", enabled, { enabled = it })
            }
            FormSection("Giá & Chiết khấu") {
                FormTextField("Giá tháng (₫)", price, { price = digits(it) }, placeholder = "0", keyboardType = KeyboardType.Number)
                FormTextField("Giảm khi mua 6T (%)", discount, { discount = digits(it).take(2) }, placeholder = "0", keyboardType = KeyboardType.Number)
            }
            FormSection("Hạn mức tin đăng") {
                FormTextField("Số tin hoạt động tối đa", activeLimit, { activeLimit = digits(it) }, placeholder = "Không giới hạn", keyboardType = KeyboardType.Number)
                FormTextField("Số tin đăng mỗi ngày", dailyLimit, { dailyLimit = digits(it) }, placeholder = "Không giới hạn", keyboardType = KeyboardType.Number)
                FormTextField("Lượt đẩy tin mỗi ngày", boostLimit, { boostLimit = digits(it) }, placeholder = "0", keyboardType = KeyboardType.Number)
                Text("Để trống nếu không giới hạn số lượng tin đăng.", fontSize = 12.sp, color = FutaColors.Slate)
            }
            FormSection("Quyền lợi") {
                FormTextField("Danh sách quyền lợi", features, { features = it }, placeholder = "Mỗi dòng một quyền lợi", multiline = true)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
}
