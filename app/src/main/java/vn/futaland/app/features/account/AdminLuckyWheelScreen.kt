package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.LocalizedRole
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.*

// Mirrors iOS Features/LuckyWheel/AdminLuckyWheelView.swift:
// settings (GET/PUT /lucky-wheel/admin/settings — the PUT replaces the whole settings object, so
// the full settings are always sent), grants (POST /admin/grant-spins, POST /admin/vip-override,
// GET /admin/users/search, GET /admin/users/spins-summary, GET /admin/grants) and fulfillment
// (GET /admin/rewards/:code, PATCH /admin/spins/:id/fulfillment).

private sealed interface WheelRoute {
    data class PrizeForm(val index: Int?) : WheelRoute
    data class RuleForm(val index: Int?) : WheelRoute
}

private enum class WheelTab(val title: String) { SETTINGS("1. Cài đặt"), GRANTS("2. Cấp lượt & VIP"), FULFILLMENT("3. Đối soát quà") }

private val wheelRoles = listOf("admin", "sale", "telesale", "customer")

private val ruleTypes = listOf(
    SelectOption("daily_checkin", "Điểm danh hàng ngày"),
    SelectOption("become_advisor", "Trở thành tư vấn viên"),
    SelectOption("transaction_success", "Giao dịch thành công"),
    SelectOption("account_registered", "Đăng ký tài khoản"),
    SelectOption("custom", "Tuỳ chỉnh")
)

private val fulfillmentTypes = listOf(
    SelectOption("digital", "Kỹ thuật số / Mã voucher"),
    SelectOption("physical", "Hiện vật (Cần địa chỉ giao)"),
    SelectOption("none", "Không cần giao (none)")
)

private val fulfillmentStatuses = listOf(
    SelectOption("pending_fulfillment", "Chờ xử lý"),
    SelectOption("shipping", "Đang vận chuyển (shipping)"),
    SelectOption("received", "Đã trao / Đã nhận quà (received)")
)

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toBooleanStrictOrNull() ?: default

private fun prizePayload(p: JsonObject): JsonObject = buildJsonObject {
    put("id", p.str("id").trim())
    put("label", p.str("label").trim())
    put("shortLabel", p.str("shortLabel").trim())
    p.str("value").trim().let { if (it.isEmpty()) put("value", JsonNull) else put("value", it) }
    if (p.containsKey("imageUrl")) p.str("imageUrl").trim().let { if (it.isEmpty()) put("imageUrl", JsonNull) else put("imageUrl", it) }
    put("fulfillmentType", p.str("fulfillmentType").takeIf { it in listOf("digital", "physical", "none") } ?: "digital")
    put("color", p.str("color").takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: "#16803C")
    put("weight", maxOf(1, p.int("weight") ?: 1))
    p.int("stock").let { if (p.str("stock").isEmpty() || it == null) put("stock", JsonNull) else put("stock", maxOf(0, it)) }
    put("enabled", p.bool("enabled"))
    p.int("tier")?.takeIf { it in 1..20 }?.let { put("tier", it) }
    p.str("unlockAt").takeIf { it.isNotEmpty() }?.let { put("unlockAt", it) }
    if (p.containsKey("minTotalSpins")) p.int("minTotalSpins").let { if (it == null) put("minTotalSpins", JsonNull) else put("minTotalSpins", maxOf(0, it)) }
}

private fun rulePayload(r: JsonObject): JsonObject = buildJsonObject {
    put("id", r.str("id").trim())
    put("type", r.str("type").takeIf { t -> ruleTypes.any { it.value == t } } ?: "custom")
    put("title", r.str("title").trim())
    put("description", r.str("description").trim())
    put("spins", (r.int("spins") ?: 1).coerceIn(1, 100))
    put("enabled", r.bool("enabled", true))
}

/** Full `PUT /lucky-wheel/admin/settings` body (strict schema): every field, loaded values kept. */
private fun settingsPayload(s: JsonObject, prizes: List<JsonObject>, rules: List<JsonObject>): JsonObject = buildJsonObject {
    val batch = (s.int("batchSize")?.takeIf { it > 0 } ?: 10).coerceIn(2, 1000)
    put("enabled", s.bool("enabled"))
    put("title", s.str("title").trim().ifEmpty { "Vòng quay may mắn" })
    put("description", s.str("description").trim())
    s.str("startAt").let { if (it.isEmpty()) put("startAt", JsonNull) else put("startAt", it) }
    s.str("endAt").let { if (it.isEmpty()) put("endAt", JsonNull) else put("endAt", it) }
    put("dailySpinLimit", (s.int("dailySpinLimit") ?: 0).coerceIn(0, 100))
    put("winRateMode", if (s.str("winRateMode") == "weight") "weight" else "batch")
    put("batchSize", batch)
    put("winsPerBatch", (s.int("winsPerBatch")?.takeIf { it > 0 } ?: 1).coerceIn(1, batch))
    put("tierOrderEnabled", s.bool("tierOrderEnabled", true))
    put("eventModeEnabled", s.bool("eventModeEnabled"))
    s.str("activeEventCode").trim().let { if (it.isEmpty()) put("activeEventCode", JsonNull) else put("activeEventCode", it) }
    put("pausePublicSpins", s.bool("pausePublicSpins"))
    s.int("targetTier")?.takeIf { it in 1..20 }.let { if (it == null) put("targetTier", JsonNull) else put("targetTier", it) }
    put("spinRules", JsonArray(rules.map { rulePayload(it) }))
    val roles = (s["eligibleRoles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.filter { it in wheelRoles }.orEmpty()
    put("eligibleRoles", JsonArray(roles.ifEmpty { wheelRoles }.map { JsonPrimitive(it) }))
    put("terms", s.str("terms"))
    put("prizes", JsonArray(prizes.map { prizePayload(it) }))
}

@Composable
fun AdminLuckyWheelScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val stack = remember { ScreenStack<WheelRoute>() }
    var tab by remember { mutableStateOf(WheelTab.SETTINGS) }
    var adminState by remember { mutableStateOf<JSONValue?>(null) }
    var summary by remember { mutableStateOf(JSONValue.Null) }
    var recentGrants by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Settings draft (saved as one PUT).
    var settings by remember { mutableStateOf(JsonObject(emptyMap())) }
    var prizes by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var rules by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var initialSettings by remember { mutableStateOf(JsonObject(emptyMap())) }
    var initialPrizes by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var initialRules by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

    fun applySettings(data: JSONValue) {
        adminState = data
        val s = data["settings"].element as? JsonObject ?: JsonObject(emptyMap())
        settings = s; initialSettings = s
        prizes = (s["prizes"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty(); initialPrizes = prizes
        rules = (s["spinRules"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty(); initialRules = rules
    }

    suspend fun loadOverview(includeSettings: Boolean) {
        try {
            val api = APIClient.get()
            val s = scope.async { api.request("/lucky-wheel/admin/settings")["data"] }
            val sm = scope.async { runCatching { api.request("/lucky-wheel/admin/users/spins-summary")["data"] }.getOrDefault(JSONValue.Null) }
            val g = scope.async { runCatching { api.request("/lucky-wheel/admin/grants")["data"].array }.getOrDefault(emptyList()) }
            val data = s.await()
            if (includeSettings || adminState == null) applySettings(data) else adminState = data
            summary = sm.await()
            recentGrants = g.await()
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        }
    }
    LaunchedEffect(Unit) { loadOverview(true) }

    val settingsDirty = adminState != null && (settings != initialSettings || prizes != initialPrizes || rules != initialRules)

    ScreenStackHost(
        stack = stack,
        base = {
            var showDiscard by remember { mutableStateOf(false) }
            BackHandler(enabled = settingsDirty) { showDiscard = true }
            Scaffold(
                containerColor = FutaColors.PageBg,
                topBar = {
                    Column(Modifier.background(FutaColors.PageBg)) {
                        SalesAdminTopBar(title = "Quản trị vòng quay", onBack = { if (settingsDirty) showDiscard = true else onBack() }) {
                            FutaHeaderIconButton(icon = Icons.Default.Refresh, contentDescription = tr("Làm mới"), onClick = { scope.launch { loadOverview(!settingsDirty) } })
                        }
                        val state = adminState
                        if (state != null) {
                            val played = state["totalSpins"].int.takeIf { it > 0 } ?: state["recentSpins"].array.size
                            Row(
                                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, FutaColors.CardBorderWeb, RoundedCornerShape(12.dp)).background(Color.White).padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WheelStat("Lượt tồn kho", "${summary["totalUnusedSpins"].int}", Modifier.weight(1f))
                                WheelStat("Có lượt quay", "${summary["totalUsersWithSpins"].int}", Modifier.weight(1f))
                                WheelStat("Đã quay", "$played", Modifier.weight(1f))
                                WheelStat("Cấp thủ công", "${recentGrants.sumOf { it["spins"].int }}", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            QuickChipRow {
                                Spacer(Modifier.width(8.dp))
                                WheelTab.entries.forEach { t -> QuickChip(t.title, t == tab) { tab = t } }
                                Spacer(Modifier.width(8.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            ) { padding ->
                val state = adminState
                when {
                    state == null && loadError == null -> AdminListSkeleton(modifier = Modifier.padding(padding))
                    state == null -> AdminErrorState(loadError.orEmpty(), { loadError = null; scope.launch { loadOverview(true) } }, Modifier.padding(padding))
                    else -> Box(Modifier.padding(padding)) {
                        when (tab) {
                            WheelTab.SETTINGS -> WheelSettingsTab(
                                adminState = state,
                                settings = settings,
                                onSettings = { settings = it },
                                prizes = prizes,
                                onPrizes = { prizes = it },
                                rules = rules,
                                onRules = { rules = it },
                                dirty = settingsDirty,
                                initialSettings = initialSettings,
                                onReset = { settings = initialSettings; prizes = initialPrizes; rules = initialRules },
                                onOpenPrize = { stack.push(WheelRoute.PrizeForm(it)) },
                                onOpenRule = { stack.push(WheelRoute.RuleForm(it)) },
                                onSaved = { data -> applySettings(data); scope.launch { loadOverview(false) } }
                            )
                            WheelTab.GRANTS -> WheelGrantsTab(
                                prizes = (state["settings"]["prizes"].array),
                                onChanged = { scope.launch { loadOverview(false) } }
                            )
                            WheelTab.FULFILLMENT -> WheelFulfillmentTab(
                                recentSpins = state["recentSpins"].array,
                                onUpdated = { scope.launch { loadOverview(false) } }
                            )
                        }
                    }
                }
            }
            DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onBack() })
        }
    ) { route ->
        when (route) {
            is WheelRoute.PrizeForm -> PrizeFormScreen(
                prize = route.index?.let { prizes.getOrNull(it) },
                otherIds = prizes.filterIndexed { i, _ -> i != route.index }.map { it.str("id") }.toSet(),
                onClose = { stack.pop() },
                onDone = { p ->
                    prizes = if (route.index != null) prizes.toMutableList().also { it[route.index] = p } else prizes + p
                    stack.pop()
                }
            )
            is WheelRoute.RuleForm -> RuleFormScreen(
                rule = route.index?.let { rules.getOrNull(it) },
                otherIds = rules.filterIndexed { i, _ -> i != route.index }.map { it.str("id") }.toSet(),
                onClose = { stack.pop() },
                onDone = { r ->
                    rules = if (route.index != null) rules.toMutableList().also { it[route.index] = r } else rules + r
                    stack.pop()
                }
            )
        }
    }
}

@Composable
private fun WheelStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, color = FutaColors.Slate, maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
    }
}

// ============================================================================
// Tab 1 — Settings
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WheelSettingsTab(
    adminState: JSONValue,
    settings: JsonObject,
    onSettings: (JsonObject) -> Unit,
    prizes: List<JsonObject>,
    onPrizes: (List<JsonObject>) -> Unit,
    rules: List<JsonObject>,
    onRules: (List<JsonObject>) -> Unit,
    dirty: Boolean,
    initialSettings: JsonObject,
    onReset: () -> Unit,
    onOpenPrize: (Int?) -> Unit,
    onOpenRule: (Int?) -> Unit,
    onSaved: (JSONValue) -> Unit
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showStatusConfirm by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var deletingPrize by remember { mutableStateOf<Int?>(null) }
    var deletingRule by remember { mutableStateOf<Int?>(null) }

    fun set(key: String, value: JsonElement) = onSettings(JsonObject(settings + (key to value)))
    fun setText(key: String, v: String) = set(key, JsonPrimitive(v))
    fun setInt(key: String, v: String, max: Int) = set(key, v.filter(Char::isDigit).take(4).toIntOrNull()?.coerceAtMost(max)?.let { JsonPrimitive(it) } ?: JsonNull)
    fun intText(key: String) = settings.int(key)?.toString().orEmpty()
    val usage = adminState["prizeUsage"]

    fun submit() {
        scope.launch {
            saving = true
            try {
                val res = APIClient.get().request("/lucky-wheel/admin/settings", method = "PUT", bodyJson = settingsPayload(settings, prizes, rules).toString())
                ToastCenter.show(tr("Đã lưu cài đặt vòng quay thành công!"))
                onSaved(res["data"])
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu cài đặt"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    fun save() {
        val ids = prizes.map { it.str("id").trim() }
        val batch = settings.int("batchSize")?.takeIf { it > 0 } ?: 10
        val wins = settings.int("winsPerBatch")?.takeIf { it > 0 } ?: 1
        error = when {
            settings.str("title").isBlank() -> tr("Vui lòng nhập tiêu đề chiến dịch")
            prizes.size !in 2..24 -> tr("Vòng quay cần từ 2 đến 24 phần thưởng")
            ids.toSet().size != ids.size -> tr("Mã quà không được trùng nhau")
            prizes.none { it.bool("enabled") && (it.str("stock").isEmpty() || (it.int("stock") ?: 0) > 0) } -> tr("Cần ít nhất một phần thưởng đang bật và còn quà")
            settings.str("winRateMode") != "weight" && batch < 2 -> tr("Kích thước nhóm tối thiểu là 2")
            settings.str("winRateMode") != "weight" && wins > batch -> tr("Số lượt trúng không được lớn hơn tổng số lượt trong nhóm")
            rules.size > 20 -> tr("Tối đa 20 quy tắc nhận lượt")
            settings.str("terms").length > 5000 -> tr("Thể lệ tối đa 5000 ký tự")
            else -> null
        }
        if (error != null) return
        val statusChanged = settings.bool("enabled") != initialSettings.bool("enabled") ||
            settings.bool("pausePublicSpins") != initialSettings.bool("pausePublicSpins")
        if (statusChanged) showStatusConfirm = true else submit()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            DetailSection("Thống kê vận hành", Icons.Default.BarChart) {
                InfoRow("Tổng lượt quay toàn hệ thống:", "${adminState["totalSpins"].int}")
                if (adminState["version"].string.isNotEmpty()) InfoRow("Phiên bản cấu hình:", adminState["version"].string, verbatim = true)
                val batch = adminState["batchStatus"]
                if (!batch.isNull) InfoRow("Nhóm lượt hiện tại", tr("#{0} • {1}/{2} lượt", batch["batchIndex"].int, batch["currentSlot"].int, batch["batchSize"].int))
            }
            FormSection("Cấu hình chiến dịch") {
                FormToggle("Kích hoạt vòng quay", settings.bool("enabled"), { set("enabled", JsonPrimitive(it)) })
                FormTextField("Tiêu đề chiến dịch", settings.str("title"), { setText("title", it) }, required = true)
                FormTextField("Mô tả hiển thị", settings.str("description"), { setText("description", it.take(500)) }, multiline = true)
                FormTextField("Giới hạn lượt quay / ngày", intText("dailySpinLimit"), { setInt("dailySpinLimit", it, 100) }, placeholder = "0 = không giới hạn", keyboardType = KeyboardType.Number)
                FormToggle("Tạm dừng cho người dùng quay", settings.bool("pausePublicSpins"), { set("pausePublicSpins", JsonPrimitive(it)) }, helper = "Người dùng vẫn xem được vòng quay nhưng không thể quay.")
                Text("Đối tượng được tham gia", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                val roles = (settings["eligibleRoles"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    wheelRoles.forEach { role ->
                        val on = role in roles
                        QuickChip(LocalizedRole.name(role), on) {
                            val next = if (on) roles - role else roles + role
                            if (next.isNotEmpty()) set("eligibleRoles", JsonArray(next.map { JsonPrimitive(it) }))
                        }
                    }
                }
            }
            FormSection("Chế độ trúng thưởng (Win Rate Mode)") {
                AdminSelectField(
                    "Chế độ", settings.str("winRateMode").ifEmpty { "batch" },
                    listOf(SelectOption("batch", "Theo nhóm lượt (Batch)"), SelectOption("weight", "Theo tỷ trọng (Weight)")),
                    { setText("winRateMode", it) }
                )
                if (settings.str("winRateMode") != "weight") {
                    FormTextField("Kích thước nhóm (Batch size)", intText("batchSize"), { setInt("batchSize", it, 1000) }, placeholder = "10", keyboardType = KeyboardType.Number)
                    FormTextField("Số lượt trúng trong nhóm", intText("winsPerBatch"), { setInt("winsPerBatch", it, 1000) }, placeholder = "1", keyboardType = KeyboardType.Number)
                }
                FormToggle("Mở giải theo thứ tự hạng (Tier)", settings.bool("tierOrderEnabled", true), { set("tierOrderEnabled", JsonPrimitive(it)) })
            }
            FormSection("Chế độ sự kiện (Event Mode)") {
                FormToggle("Bật chế độ sự kiện riêng", settings.bool("eventModeEnabled"), { set("eventModeEnabled", JsonPrimitive(it)) })
                if (settings.bool("eventModeEnabled")) {
                    FormTextField("Mã sự kiện (Event Code)", settings.str("activeEventCode"), { setText("activeEventCode", it.take(80)) })
                }
            }
            FormSection(tr("Nhiệm vụ & Quy tắc nhận lượt ({0})", rules.size)) {
                if (rules.isEmpty()) Text("Chưa có quy tắc nhận lượt.", fontSize = 12.5.sp, color = FutaColors.Slate)
                rules.forEachIndexed { i, rule ->
                    if (i > 0) HorizontalDivider(color = FutaColors.PanelDivider)
                    Row(Modifier.fillMaxWidth().clickable { onOpenRule(i) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                VerbatimText(rule.str("title"), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (!rule.bool("enabled", true)) StatusPill("Tắt", FutaColors.Slate)
                            }
                            Text(tr("Loại: {0} • +{1} lượt", tr(ruleTypes.firstOrNull { it.value == rule.str("type") }?.label ?: rule.str("type")), rule.int("spins") ?: 1), fontSize = 12.sp, color = FutaColors.Slate)
                        }
                        IconButton(onClick = { deletingRule = i }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteOutline, tr("Xóa"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (rules.size < 20) FutaButton(text = "Thêm quy tắc nhận lượt", icon = Icons.Default.AddCircleOutline, variant = FutaButtonVariant.OUTLINE, height = 38.dp, onClick = { onOpenRule(null) })
            }
            FormSection("Thể lệ & Điều khoản tham gia") {
                FutaTextArea(
                    value = settings.str("terms"), onValueChange = { setText("terms", it.take(5000)) },
                    placeholder = "Nhập thể lệ chương trình, điều kiện nhận thưởng và quy định tham gia…", minLines = 6, maxLines = 16
                )
            }
            FormSection(tr("Danh sách phần thưởng ({0})", prizes.size)) {
                prizes.forEachIndexed { i, prize ->
                    if (i > 0) HorizontalDivider(color = FutaColors.PanelDivider)
                    Row(Modifier.fillMaxWidth().clickable { onOpenPrize(i) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(parseHexColor(prize.str("color"))))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                VerbatimText(prize.str("label"), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (!prize.bool("enabled")) StatusPill("Tắt", FutaColors.Slate)
                            }
                            VerbatimText(tr("Mã: {0} • Rút gọn: {1}", prize.str("id"), prize.str("shortLabel")), fontSize = 12.sp, color = FutaColors.Slate)
                            val stock = if (prize.str("stock").isEmpty()) tr("Vô hạn") else "${prize.int("stock") ?: 0}"
                            val used = usage[prize.str("id")].int
                            Text(tr("Kho: {0} • Tỷ trọng: {1} • Đã trao: {2}", stock, prize.int("weight") ?: 1, used), fontSize = 11.5.sp, color = FutaColors.Slate)
                        }
                        IconButton(onClick = { deletingPrize = i }, enabled = prizes.size > 2, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteOutline, tr("Xóa"), tint = if (prizes.size > 2) Color(0xFFDC2626) else FutaColors.Slate.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (prizes.size < 24) FutaButton(text = "Thêm phần thưởng mới", icon = Icons.Default.AddCircle, variant = FutaButtonVariant.MINT, height = 38.dp, onClick = { onOpenPrize(null) })
            }
            Spacer(Modifier.height(24.dp))
        }
        FutaStickyActionBar {
            FutaButton(text = "Hoàn tác", variant = FutaButtonVariant.OUTLINE, enabled = dirty && !saving, onClick = { showReset = true }, modifier = Modifier.weight(1f))
            FutaButton(
                text = if (saving) "Đang lưu…" else "Lưu cài đặt vòng quay",
                icon = if (saving) null else Icons.Default.Check,
                enabled = dirty && !saving,
                onClick = { save() },
                modifier = Modifier.weight(1.8f)
            )
        }
    }

    ConfirmDialog(
        visible = showStatusConfirm,
        title = "Thay đổi trạng thái vòng quay?",
        message = buildString {
            append(if (settings.bool("enabled")) tr("Vòng quay sẽ được kích hoạt.") else tr("Vòng quay sẽ bị tắt."))
            if (settings.bool("pausePublicSpins")) append(" " + tr("Người dùng sẽ tạm thời không thể quay."))
        },
        confirmText = "Lưu thay đổi",
        destructive = !settings.bool("enabled") || settings.bool("pausePublicSpins"),
        onDismiss = { showStatusConfirm = false },
        onConfirm = { showStatusConfirm = false; submit() }
    )
    ConfirmDialog(
        visible = showReset,
        title = "Hoàn tác thay đổi?",
        message = "Mọi thay đổi chưa lưu sẽ bị bỏ.",
        confirmText = "Hoàn tác",
        destructive = true,
        onDismiss = { showReset = false },
        onConfirm = { showReset = false; error = null; onReset() }
    )
    val prizeIndex = deletingPrize
    ConfirmDialog(
        visible = prizeIndex != null,
        title = "Xóa phần thưởng?",
        message = tr("\"{0}\" sẽ bị gỡ khỏi vòng quay khi bạn lưu cài đặt.", prizeIndex?.let { prizes.getOrNull(it)?.str("label") }.orEmpty()),
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { deletingPrize = null },
        onConfirm = { prizeIndex?.let { i -> onPrizes(prizes.filterIndexed { idx, _ -> idx != i }) }; deletingPrize = null }
    )
    val ruleIndex = deletingRule
    ConfirmDialog(
        visible = ruleIndex != null,
        title = "Xóa quy tắc?",
        message = tr("\"{0}\" sẽ bị xóa khi bạn lưu cài đặt.", ruleIndex?.let { rules.getOrNull(it)?.str("title") }.orEmpty()),
        confirmText = "Xóa",
        destructive = true,
        onDismiss = { deletingRule = null },
        onConfirm = { ruleIndex?.let { i -> onRules(rules.filterIndexed { idx, _ -> idx != i }) }; deletingRule = null }
    )
}

// ============================================================================
// Prize / rule editors (edit the local draft; saved with "Lưu cài đặt vòng quay")
// ============================================================================

private val prizeColorPresets = listOf("#16803C", "#207446", "#F97316", "#EAB308", "#DC2626", "#DB2777", "#7C3AED", "#2563EB", "#0EA5E9", "#475569")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrizeFormScreen(prize: JsonObject?, otherIds: Set<String>, onClose: () -> Unit, onDone: (JsonObject) -> Unit) {
    val isEdit = prize != null
    val init = remember {
        mapOf(
            "id" to (prize?.str("id") ?: "gift_${(100..999).random()}"),
            "label" to prize?.str("label").orEmpty(),
            "shortLabel" to prize?.str("shortLabel").orEmpty(),
            "value" to prize?.str("value").orEmpty(),
            "imageUrl" to prize?.str("imageUrl").orEmpty(),
            "color" to (prize?.str("color")?.ifEmpty { null } ?: "#16803C"),
            "weight" to "${prize?.int("weight") ?: 10}",
            "stock" to prize?.str("stock").orEmpty().let { s -> if (s.isEmpty()) "" else "${prize?.int("stock") ?: 0}" },
            "fulfillmentType" to (prize?.str("fulfillmentType")?.ifEmpty { null } ?: "digital"),
            "tier" to "${prize?.int("tier") ?: 1}",
            "minTotalSpins" to (prize?.int("minTotalSpins")?.toString() ?: ""),
            "enabled" to (prize?.bool("enabled") ?: true).toString()
        )
    }
    var f by remember { mutableStateOf(init) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    fun v(k: String) = f[k].orEmpty()
    fun set(k: String, value: String) { f = f + (k to value) }
    val uploader = rememberImageUploader("lucky_prize") { set("imageUrl", it) }
    val dirty = f != init
    fun close() { if (dirty) showDiscard = true else onClose() }
    BackHandler { close() }

    fun done() {
        val id = v("id").trim()
        error = when {
            !Regex("^[a-zA-Z0-9_-]{1,80}$").matches(id) -> tr("Mã quà chỉ gồm chữ, số, gạch ngang và gạch dưới")
            id in otherIds -> tr("Mã quà không được trùng nhau")
            v("label").isBlank() -> tr("Vui lòng nhập tên hiển thị")
            v("shortLabel").isBlank() || v("shortLabel").trim().length > 30 -> tr("Tên ngắn bắt buộc, tối đa 30 ký tự")
            !Regex("^#[0-9a-fA-F]{6}$").matches(v("color").trim()) -> tr("Màu phải có định dạng #RRGGBB")
            (v("weight").toIntOrNull() ?: 0) < 1 -> tr("Tỷ trọng tối thiểu là 1")
            uploader.uploading -> tr("Ảnh đang được tải lên, vui lòng đợi")
            else -> null
        }
        if (error != null) return
        // Keep keys the editor doesn't show (e.g. unlockAt) from the loaded prize.
        val base = prize ?: JsonObject(emptyMap())
        val out = base.toMutableMap()
        out["id"] = JsonPrimitive(id)
        out["label"] = JsonPrimitive(v("label").trim())
        out["shortLabel"] = JsonPrimitive(v("shortLabel").trim())
        out["value"] = v("value").trim().let { if (it.isEmpty()) JsonNull else JsonPrimitive(it) }
        out["imageUrl"] = v("imageUrl").trim().let { if (it.isEmpty()) JsonNull else JsonPrimitive(it) }
        out["color"] = JsonPrimitive(v("color").trim().uppercase())
        out["weight"] = JsonPrimitive(v("weight").toInt())
        out["stock"] = v("stock").toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        out["fulfillmentType"] = JsonPrimitive(v("fulfillmentType"))
        out["tier"] = JsonPrimitive((v("tier").toIntOrNull() ?: 1).coerceIn(1, 20))
        out["minTotalSpins"] = v("minTotalSpins").toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        out["enabled"] = JsonPrimitive(v("enabled") == "true")
        onDone(JsonObject(out))
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = {
            SalesAdminTopBar(title = if (isEdit) "Sửa giải thưởng" else "Thêm giải thưởng", subtitle = if (isEdit) v("id") else null, onBack = { close() }) {
                StatusPill(if (v("enabled") == "true") "Đang bật" else "Tắt", if (v("enabled") == "true") Color(0xFF16A34A) else FutaColors.Slate)
            }
        },
        bottomBar = { FormActionBar("Xong", saving = false, enabled = dirty || !isEdit, onCancel = { close() }, onSave = { done() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            Text("Thay đổi chỉ được áp dụng sau khi bấm \"Lưu cài đặt vòng quay\".", fontSize = 12.sp, color = FutaColors.Slate)
            FormSection("Thông tin phần thưởng") {
                FormTextField("Mã giải thưởng (id - chữ không dấu)", v("id"), { set("id", it.take(80)) }, required = true)
                FormTextField("Tên hiển thị đầy đủ", v("label"), { set("label", it.take(120)) }, required = true)
                FormTextField("Tên ngắn trên vòng quay (tối đa 30 ký tự)", v("shortLabel"), { set("shortLabel", it.take(30)) }, required = true)
                FormTextField("Giá trị quy đổi (VND hoặc quà)", v("value"), { set("value", it.take(120)) })
                ImageUploadField("Ảnh phần thưởng", v("imageUrl"), uploader.uploading, uploader.pick, { set("imageUrl", "") }, previewHeight = 120.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(parseHexColor(v("color"))).border(1.dp, FutaColors.LightBlueBorder, CircleShape))
                    Box(Modifier.weight(1f)) { FormTextField("Mã màu Hex (#RRGGBB)", v("color"), { set("color", it.take(7)) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prizeColorPresets.forEach { preset ->
                        val selected = preset.equals(v("color").trim(), ignoreCase = true)
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(parseHexColor(preset))
                                .border(if (selected) 3.dp else 0.dp, if (selected) FutaColors.Navy else Color.Transparent, CircleShape)
                                .clickable { set("color", preset) }
                        )
                    }
                }
            }
            FormSection("Tỷ lệ & Kho quà") {
                FormTextField("Tỷ trọng may mắn (Weight)", v("weight"), { set("weight", it.filter(Char::isDigit).take(7)) }, keyboardType = KeyboardType.Number)
                FormTextField("Số lượng trong kho (để trống: vô hạn)", v("stock"), { set("stock", it.filter(Char::isDigit).take(7)) }, keyboardType = KeyboardType.Number)
                FormTextField("Hạng giải (Tier 1–20)", v("tier"), { set("tier", it.filter(Char::isDigit).take(2)) }, keyboardType = KeyboardType.Number)
                FormTextField("Số lượt quay tối thiểu toàn hệ thống để mở", v("minTotalSpins"), { set("minTotalSpins", it.filter(Char::isDigit).take(8)) }, placeholder = "Không yêu cầu", keyboardType = KeyboardType.Number)
                AdminSelectField("Hình thức trao quà", v("fulfillmentType"), fulfillmentTypes, { set("fulfillmentType", it) })
                FormToggle("Kích hoạt giải thưởng", v("enabled") == "true", { set("enabled", it.toString()) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
}

@Composable
private fun RuleFormScreen(rule: JsonObject?, otherIds: Set<String>, onClose: () -> Unit, onDone: (JsonObject) -> Unit) {
    val isEdit = rule != null
    val init = remember {
        mapOf(
            "id" to (rule?.str("id") ?: "rule_${(100..999).random()}"),
            "type" to (rule?.str("type")?.ifEmpty { null } ?: "custom"),
            "title" to rule?.str("title").orEmpty(),
            "description" to rule?.str("description").orEmpty(),
            "spins" to "${maxOf(1, rule?.int("spins") ?: 1)}",
            "enabled" to (rule?.bool("enabled", true) ?: true).toString()
        )
    }
    var f by remember { mutableStateOf(init) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    fun v(k: String) = f[k].orEmpty()
    fun set(k: String, value: String) { f = f + (k to value) }
    val dirty = f != init
    fun close() { if (dirty) showDiscard = true else onClose() }
    BackHandler { close() }

    fun done() {
        val id = v("id").trim()
        val spins = v("spins").toIntOrNull()
        error = when {
            id.isEmpty() -> tr("Vui lòng nhập mã nhiệm vụ")
            id in otherIds -> tr("Mã nhiệm vụ không được trùng nhau")
            v("title").isBlank() -> tr("Vui lòng nhập tiêu đề hiển thị")
            spins == null || spins !in 1..100 -> tr("Số lượt tặng phải từ 1 đến 100")
            else -> null
        }
        if (error != null) return
        onDone(buildJsonObject {
            put("id", id)
            put("type", v("type"))
            put("title", v("title").trim())
            put("description", v("description").trim())
            put("spins", spins!!)
            put("enabled", v("enabled") == "true")
        })
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = if (isEdit) "Sửa nhiệm vụ" else "Thêm nhiệm vụ", subtitle = if (isEdit) v("id") else null, onBack = { close() }) },
        bottomBar = { FormActionBar("Xong", saving = false, enabled = dirty || !isEdit, onCancel = { close() }, onSave = { done() }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormErrorBanner(error)
            Text("Thay đổi chỉ được áp dụng sau khi bấm \"Lưu cài đặt vòng quay\".", fontSize = 12.sp, color = FutaColors.Slate)
            FormSection("Thông tin nhiệm vụ") {
                FormTextField("Mã nhiệm vụ (id)", v("id"), { set("id", it.take(80)) }, required = true)
                AdminSelectField("Loại nhiệm vụ", v("type"), ruleTypes, { set("type", it) })
                FormTextField("Tiêu đề hiển thị", v("title"), { set("title", it.take(120)) }, required = true)
                FormTextField("Mô tả chi tiết", v("description"), { set("description", it.take(500)) }, multiline = true)
                FormTextField("Số lượt tặng", v("spins"), { set("spins", it.filter(Char::isDigit).take(3)) }, keyboardType = KeyboardType.Number)
                FormToggle("Đang kích hoạt", v("enabled") == "true", { set("enabled", it.toString()) })
            }
        }
    }
    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onClose() })
}

// ============================================================================
// Tab 2 — Grants & VIP override
// ============================================================================

@Composable
private fun WheelGrantsTab(prizes: List<JSONValue>, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var search by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("all") }
    var draftRole by remember { mutableStateOf("all") }
    var showFilter by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var summary by remember { mutableStateOf(JSONValue.Null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var grants by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var grantTarget by remember { mutableStateOf<JSONValue?>(null) }
    var vipTarget by remember { mutableStateOf<JSONValue?>(null) }

    // "Có lượt" lists users holding spins; with a search it looks up any account (min 0 spins).
    suspend fun fetch() {
        loading = true
        try {
            val query = mutableMapOf("page" to "$page", "limit" to "20")
            if (search.isNotBlank()) { query["search"] = search.trim(); query["minSpins"] = "0" }
            if (role != "all") query["role"] = role
            summary = APIClient.get().request("/lucky-wheel/admin/users/spins-summary", query = query)["data"]
            grants = runCatching { APIClient.get().request("/lucky-wheel/admin/grants")["data"].array }.getOrDefault(grants)
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(search, role, page, reload) {
        if (search.isNotEmpty()) delay(350)
        fetch()
    }

    val users = summary["users"].array
    val totalPages = maxOf(1, summary["totalPages"].int)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { AdminSearchField(search, { search = it; page = 1 }, "Tìm theo tên, email, SĐT…") }
                BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), if (role != "all") 1 else 0) { draftRole = role; showFilter = true }
            }
        }
        if (role != "all") item { AppliedFilterChips(listOf(AppliedFilter(LocalizedRole.name(role)) { role = "all"; page = 1 })) { role = "all"; page = 1 } }
        item {
            Text(
                if (search.isBlank()) tr("Tài khoản có lượt quay tồn ({0})", summary["filteredUsersCount"].int) else tr("Kết quả tìm kiếm ({0})", summary["filteredUsersCount"].int),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate
            )
        }
        when {
            loading && users.isEmpty() -> items(4) { FutaAdminRowSkeleton() }
            loadError != null && users.isEmpty() -> item { AdminErrorState(loadError.orEmpty(), { reload++ }) }
            users.isEmpty() -> item {
                AdminListEmpty(search.isNotBlank() || role != "all", "Không có tài khoản nào có lượt tồn.", "Tìm người dùng theo tên hoặc SĐT để tặng lượt.", { search = ""; role = "all"; page = 1 }, Icons.Default.Casino)
            }
            else -> {
                items(users, key = { "wu-" + it.id }) { u -> WheelUserRow(u, prizes, onGrant = { grantTarget = u }, onVip = { vipTarget = u }) }
                if (totalPages > 1) item {
                    PaginationBar(page, totalPages, tr("{0} tài khoản", summary["filteredUsersCount"].int),
                        { if (page > 1) { page--; scope.launch { listState.scrollToItem(0) } } },
                        { if (page < totalPages) { page++; scope.launch { listState.scrollToItem(0) } } })
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(6.dp))
                Text("Lịch sử trao lượt gần đây", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                if (grants.isEmpty()) Text("Chưa có lượt cấp phát gần đây.", fontSize = 12.5.sp, color = FutaColors.Slate)
            }
        }
        items(grants.take(30), key = { "g-" + it.id + it["createdAt"].string }) { g ->
            AdminRowCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = g["userName"].string.ifEmpty { g["user"]["name"].string }
                    if (name.isNotEmpty()) VerbatimText(name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                    else Text("Người dùng", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                    Text(tr("+{0} lượt", g["spins"].int), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
                }
                if (g["note"].string.isNotEmpty()) VerbatimText(tr("Ghi chú: {0}", g["note"].string), fontSize = 12.sp, color = FutaColors.Slate)
                Text(SalesFormatters.dateTime(g["createdAt"].string), fontSize = 11.sp, color = FutaColors.Slate)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    FilterSheet(
        visible = showFilter, title = "Bộ lọc", applyTitle = "Áp dụng",
        canReset = draftRole != "all", onReset = { draftRole = "all" },
        onApply = { role = draftRole; page = 1; showFilter = false }, onDismiss = { showFilter = false }
    ) {
        FilterChipGroup("Vai trò", listOf(SelectOption("all", "Tất cả")) + (wheelRoles + "advisor_trainee").map { SelectOption(it, LocalizedRole.name(it)) }, draftRole) { draftRole = it }
    }

    grantTarget?.let { u ->
        GrantSpinsDialog(u, onDismiss = { grantTarget = null }) { spins, note ->
            grantTarget = null
            scope.launch {
                try {
                    APIClient.get().request("/lucky-wheel/admin/grant-spins", method = "POST", bodyJson = buildJsonObject {
                        put("userId", u.id); put("spins", spins); if (note.isNotBlank()) put("note", note.trim().take(500))
                    }.toString())
                    ToastCenter.show(tr("Đã cấp {0} lượt quay thành công!", spins))
                    reload++
                    onChanged()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể cấp lượt"), isError = true)
                }
            }
        }
    }
    vipTarget?.let { u ->
        VipOverrideSheet(u, prizes, onDismiss = { vipTarget = null }) { prizeId ->
            vipTarget = null
            scope.launch {
                try {
                    APIClient.get().request("/lucky-wheel/admin/vip-override", method = "POST", bodyJson = buildJsonObject {
                        put("userId", u.id); if (prizeId == null) put("prizeId", JsonNull) else put("prizeId", prizeId)
                    }.toString())
                    ToastCenter.show(tr("Đã cập nhật chỉ định VIP thành công!"))
                    reload++
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không thể cập nhật chỉ định VIP"), isError = true)
                }
            }
        }
    }
}

@Composable
private fun WheelUserRow(u: JSONValue, prizes: List<JSONValue>, onGrant: () -> Unit, onVip: () -> Unit) {
    AdminRowCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = u["name"].string
            if (name.isNotEmpty()) VerbatimText(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text("Chưa đặt tên", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            Text(tr("{0} lượt tồn", u["manualBonusSpins"].int), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
        }
        val phone = u["phone"].string.ifEmpty { u["phoneNumber"].string }
        VerbatimText(listOf(phone, u["email"].string, LocalizedRole.name(u["role"].string)).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val target = u["luckyWheelTargetPrizeId"].string
        if (target.isNotEmpty()) {
            val label = prizes.firstOrNull { it["id"].string == target }?.get("label")?.string ?: target
            StatusPill(tr("Trúng VIP: {0}", label), Color(0xFFF97316))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FutaButton(text = "Tặng lượt", icon = Icons.Default.Redeem, variant = FutaButtonVariant.MINT, height = 34.dp, onClick = onGrant)
            FutaButton(text = "Chỉ định VIP", icon = Icons.Default.WorkspacePremium, variant = FutaButtonVariant.OUTLINE, height = 34.dp, onClick = onVip)
        }
    }
}

@Composable
private fun GrantSpinsDialog(user: JSONValue, onDismiss: () -> Unit, onGrant: (Int, String) -> Unit) {
    var spins by remember { mutableStateOf("5") }
    var note by remember { mutableStateOf("Quản trị viên tặng lượt tri ân") }
    FutaDialog(
        visible = true,
        onDismiss = onDismiss,
        title = "Tặng lượt quay",
        confirmText = "Xác nhận",
        onConfirm = {
            val n = spins.toIntOrNull()
            if (n == null || n !in 1..1000) ToastCenter.show(tr("Số lượt tặng phải từ 1 đến 1000"), isError = true) else onGrant(n, note)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("Họ tên", user["name"].string, verbatim = true)
            InfoRow("SĐT", user["phone"].string.ifEmpty { user["phoneNumber"].string }, verbatim = true)
            FormTextField("Số lượt tặng", spins, { spins = it.filter(Char::isDigit).take(4) }, keyboardType = KeyboardType.Number)
            FormTextField("Ghi chú lý do", note, { note = it.take(500) })
        }
    }
}

@Composable
private fun VipOverrideSheet(user: JSONValue, prizes: List<JSONValue>, onDismiss: () -> Unit, onSet: (String?) -> Unit) {
    val current = user["luckyWheelTargetPrizeId"].string
    var selected by remember { mutableStateOf(current) }
    FutaBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Chỉ định VIP",
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FutaButton(text = "Hủy", variant = FutaButtonVariant.OUTLINE, onClick = onDismiss, modifier = Modifier.weight(1f))
                FutaButton(text = "Lưu", enabled = selected != current, onClick = { onSet(selected.ifEmpty { null }) }, modifier = Modifier.weight(1.6f))
            }
        }
    ) {
        Text(tr("Chỉ định giải thưởng cho lượt quay kế tiếp của {0}.", user["name"].string.ifEmpty { user["phone"].string }), fontSize = 12.5.sp, color = FutaColors.Slate)
        AdminSelectField(
            "Giải thưởng trúng", selected,
            listOf(SelectOption("", "Không chỉ định (Mặc định)")) + prizes.map { SelectOption(it["id"].string, "${it["label"].string} (${it["id"].string})") },
            { selected = it }
        )
    }
}

// ============================================================================
// Tab 3 — Fulfillment
// ============================================================================

@Composable
private fun WheelFulfillmentTab(recentSpins: List<JSONValue>, onUpdated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<JSONValue?>(null) }
    var searching by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var listSearch by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var draftStatus by remember { mutableStateOf("all") }
    var showFilter by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }

    fun lookup(raw: String = code) {
        val clean = raw.trim()
        if (clean.isEmpty()) return
        scope.launch {
            searching = true
            lookupError = null
            try {
                result = APIClient.get().request("/lucky-wheel/admin/rewards/${android.net.Uri.encode(clean)}")["data"]
            } catch (e: Exception) {
                result = null
                lookupError = e.message ?: tr("Không tìm thấy mã quà tặng")
            } finally {
                searching = false
            }
        }
    }

    val wins = remember(recentSpins, listSearch, statusFilter) {
        recentSpins.filter { it["isWin"].bool }.filter { s ->
            val q = listSearch.trim()
            (q.isEmpty() || listOf(s["user"]["name"].string, s["user"]["phone"].string, s["prizeLabel"].string, s["rewardCode"].string).joinToString(" ").contains(q, ignoreCase = true)) &&
                when (statusFilter) {
                    "received" -> s["fulfillmentStatus"].string == "received"
                    "pending" -> s["fulfillmentStatus"].string != "received"
                    else -> true
                }
        }
    }
    val slice = wins.pageSlice(page, 15)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FormSection("Tra cứu mã quà tặng") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FutaInput(value = code, onValueChange = { code = it.uppercase() }, placeholder = "Nhập mã quà (ví dụ: FUTA-…)", leadingIcon = Icons.Default.QrCode2)
                    }
                    FutaButton(text = "Tra cứu", enabled = code.isNotBlank() && !searching, height = 44.dp, onClick = { lookup() })
                }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FutaColors.BrandGreen)
                lookupError?.let { FormErrorBanner(it) }
            }
        }
        result?.let { spin ->
            item(key = "result-" + spin.id + spin["fulfillmentStatus"].string + spin["updatedAt"].string) {
                RewardResultCard(spin, onUpdated = { updated -> result = updated; onUpdated() }, onRefresh = { lookup(spin["rewardCode"].string) })
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(4.dp))
                Text(tr("Danh sách lượt trúng thưởng ({0})", wins.size), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { AdminSearchField(listSearch, { listSearch = it; page = 1 }, "Tìm theo tên, SĐT, giải, mã quà…") }
                    BadgedHeaderButton(Icons.Default.FilterList, tr("Bộ lọc"), if (statusFilter != "all") 1 else 0) { draftStatus = statusFilter; showFilter = true }
                }
                if (statusFilter != "all") AppliedFilterChips(listOf(AppliedFilter(if (statusFilter == "received") tr("Đã trao quà") else tr("Chờ trao quà")) { statusFilter = "all"; page = 1 })) { statusFilter = "all"; page = 1 }
            }
        }
        if (slice.items.isEmpty()) item {
            AdminListEmpty(recentSpins.any { it["isWin"].bool }, "Chưa ghi nhận lượt trúng thưởng nào.", "Các lượt trúng gần đây sẽ xuất hiện tại đây.", { listSearch = ""; statusFilter = "all"; page = 1 }, Icons.Default.EmojiEvents)
        }
        items(slice.items, key = { "w-" + it.id }) { spin ->
            val received = spin["fulfillmentStatus"].string == "received"
            AdminRowCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = spin["user"]["name"].string
                    if (name.isNotEmpty()) VerbatimText(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                    else Text("Khách hàng", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                    StatusPill(if (received) "Đã trao quà" else "Chờ trao quà", if (received) FutaColors.BrandGreen else Color(0xFFF97316))
                }
                VerbatimText(tr("Giải: {0}", spin["prizeLabel"].string), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                val rc = spin["rewardCode"].string
                if (rc.isNotEmpty()) androidx.compose.material3.Text(tr("Mã nhận quà: {0}", rc), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = FutaColors.Slate)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val phone = spin["user"]["phone"].string.ifEmpty { spin["user"]["phoneNumber"].string }
                    Text(if (phone.isNotEmpty()) tr("SĐT: {0}", phone) else SalesFormatters.dateTime(spin["createdAt"].string), fontSize = 11.5.sp, color = FutaColors.Slate, modifier = Modifier.weight(1f))
                    if (rc.isNotEmpty()) {
                        Text("Đối soát ngay", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen, modifier = Modifier.clickable { code = rc; lookup(rc) }.padding(4.dp))
                    }
                }
            }
        }
        if (slice.totalPages > 1) item {
            PaginationBar(slice.page, slice.totalPages, tr("{0}–{1} / {2}", slice.start, slice.end, slice.total), { page = slice.page - 1 }, { page = slice.page + 1 })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    FilterSheet(
        visible = showFilter, title = "Bộ lọc", applyTitle = "Áp dụng",
        canReset = draftStatus != "all", onReset = { draftStatus = "all" },
        onApply = { statusFilter = draftStatus; page = 1; showFilter = false }, onDismiss = { showFilter = false }
    ) {
        FilterChipGroup("Trạng thái trao quà", listOf(SelectOption("all", "Tất cả"), SelectOption("pending", "Chờ trao quà"), SelectOption("received", "Đã trao quà")), draftStatus) { draftStatus = it }
    }
}

/** Looked-up reward: details, recipient (editable) and fulfillment update. */
@Composable
private fun RewardResultCard(spin: JSONValue, onUpdated: (JSONValue) -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentStatus = spin["fulfillmentStatus"].string.ifEmpty { "pending_fulfillment" }
    var status by remember { mutableStateOf(currentStatus) }
    var tracking by remember { mutableStateOf(spin["trackingCode"].string) }
    var note by remember { mutableStateOf(spin["fulfillmentNote"].string) }
    var verified by remember { mutableStateOf(spin["recipientVerified"].bool) }
    var recipientName by remember { mutableStateOf(spin["recipientName"].string) }
    var recipientPhone by remember { mutableStateOf(spin["recipientPhone"].string) }
    var recipientAddress by remember { mutableStateOf(spin["recipientAddress"].string) }
    var updating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    val physical = spin["fulfillmentType"].string == "physical"
    val rewardCode = spin["rewardCode"].string.trim()
    val received = currentStatus == "received"

    fun submit() {
        val body = buildJsonObject {
            put("fulfillmentStatus", status)
            put("recipientVerified", verified)
            if (rewardCode.isNotEmpty()) put("rewardCode", rewardCode)
            tracking.trim().let { if (it.isNotEmpty()) put("trackingCode", it) }
            note.trim().let { if (it.isNotEmpty()) put("fulfillmentNote", it) }
            if (recipientName.trim() != spin["recipientName"].string && recipientName.isNotBlank()) put("recipientName", recipientName.trim())
            if (recipientPhone.trim() != spin["recipientPhone"].string && recipientPhone.isNotBlank()) put("recipientPhone", recipientPhone.trim())
            if (recipientAddress.trim() != spin["recipientAddress"].string && recipientAddress.isNotBlank()) put("recipientAddress", recipientAddress.trim())
        }.toString()
        scope.launch {
            updating = true
            try {
                val res = APIClient.get().request("/lucky-wheel/admin/spins/${spin.id}/fulfillment", method = "PATCH", bodyJson = body)
                ToastCenter.show(tr("Cập nhật trao thưởng thành công!"))
                val data = res["data"]
                if (!data.isNull && data["id"].string == spin.id) onUpdated(spin.withUpdates((data.element as JsonObject).toMap())) else onRefresh()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể cập nhật trao thưởng"), isError = true)
            } finally {
                updating = false
            }
        }
    }

    fun save() {
        error = when {
            status == "received" && rewardCode.isEmpty() -> tr("Cần mã quà tặng để xác nhận đã nhận thưởng")
            status == "received" && !verified -> tr("Cần đối chiếu tài khoản và người nhận trước khi trao quà")
            recipientName.isNotBlank() && recipientName.trim().length < 2 -> tr("Tên người nhận tối thiểu 2 ký tự")
            recipientPhone.isNotBlank() && !Regex("^[+0-9][0-9 .()-]{7,19}$").matches(recipientPhone.trim()) -> tr("Số điện thoại không đúng định dạng")
            recipientAddress.isNotBlank() && recipientAddress.trim().length < 5 -> tr("Địa chỉ nhận tối thiểu 5 ký tự")
            else -> null
        }
        if (error != null) return
        if (status != currentStatus) showConfirm = true else submit()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSection("Thông tin phần thưởng", Icons.Default.Redeem, trailing = {
            StatusPill(fulfillmentStatuses.firstOrNull { it.value == currentStatus }?.label?.substringBefore(" (") ?: currentStatus, if (received) FutaColors.BrandGreen else Color(0xFFF97316))
        }) {
            InfoRow("Giải thưởng", spin["prizeLabel"].string, verbatim = true)
            InfoRow("Mã nhận quà", rewardCode, verbatim = true)
            InfoRow("Hình thức trao", if (physical) "Hiện vật" else "Kỹ thuật số")
            InfoRow("Thời điểm trúng", SalesFormatters.dateTime(spin["createdAt"].string))
            if (spin["trackingCode"].string.isNotEmpty()) InfoRow("Mã vận đơn", spin["trackingCode"].string, verbatim = true)
        }
        DetailSection("Khách hàng trúng thưởng", Icons.Default.Person) {
            InfoRow("Họ và tên", spin["user"]["name"].string.ifEmpty { tr("Chưa cập nhật") }, verbatim = true)
            InfoRow("Số điện thoại", spin["user"]["phoneNumber"].string.ifEmpty { spin["user"]["phone"].string }.ifEmpty { tr("Chưa cập nhật") }, verbatim = true)
            InfoRow("Email", spin["user"]["email"].string.ifEmpty { tr("Chưa cập nhật") }, verbatim = true)
        }
        FormSection("Địa chỉ nhận hàng (Khách cung cấp)") {
            if (spin["recipientName"].string.isEmpty() && physical) Text("Khách chưa cung cấp địa chỉ nhận hàng.", fontSize = 12.5.sp, color = Color(0xFFF97316))
            FormTextField("Người nhận", recipientName, { recipientName = it.take(120) })
            FormTextField("SĐT nhận", recipientPhone, { recipientPhone = it.take(20) }, keyboardType = KeyboardType.Phone)
            FormTextField("Địa chỉ giao", recipientAddress, { recipientAddress = it.take(500) }, multiline = true)
        }
        FormSection("Cập nhật trạng thái trao quà") {
            FormErrorBanner(error)
            AdminSelectField("Trạng thái mới", status, fulfillmentStatuses, { status = it })
            FormTextField("Mã vận đơn (nếu có)", tracking, { tracking = it.take(120) })
            FormTextField("Ghi chú xử lý", note, { note = it.take(500) })
            FormToggle("Đã đối chiếu thông tin người nhận", verified, { verified = it }, helper = if (status == "received") tr("Bắt buộc khi xác nhận đã trao quà.") else null)
            FutaButton(
                text = if (updating) "Đang cập nhật…" else "Cập nhật trạng thái trao quà",
                icon = if (updating) null else Icons.Default.LocalShipping,
                enabled = !updating && !(status == "received" && (!verified || rewardCode.isEmpty())),
                onClick = { save() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    ConfirmDialog(
        visible = showConfirm,
        title = "Cập nhật trạng thái trao quà?",
        message = tr("Trạng thái sẽ chuyển sang \"{0}\".", tr(fulfillmentStatuses.firstOrNull { it.value == status }?.label ?: status)),
        confirmText = "Xác nhận",
        onDismiss = { showConfirm = false },
        onConfirm = { showConfirm = false; submit() }
    )
}
