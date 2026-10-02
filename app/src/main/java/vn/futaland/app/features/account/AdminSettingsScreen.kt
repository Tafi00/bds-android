package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.salesadmin.*

// Mirrors iOS Features/CMS/AdminSettingsView.swift. Settings live in the CMS record
// (GET /cms/admin → PUT /cms/settings). The backend merges systemConfig key by key, so only the
// blocks edited here are sent, each merged into its loaded value so unknown keys survive.
// Secrets (notification provider secret, messaging API key) are never shown: they are sent only
// when the admin types a new value.

/** systemConfig blocks edited on this screen (key-merged on save). */
private val settingsBlocks = listOf("banking", "brokerContract", "serviceContract", "acceptanceReport", "holding")

@Composable
fun AdminSettingsScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<JsonElement?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var top by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var initialTop by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var config by remember { mutableStateOf<JsonElement>(JsonObject(emptyMap())) }
    var initialConfig by remember { mutableStateOf<JsonElement>(JsonObject(emptyMap())) }
    var editingNotificationSecret by remember { mutableStateOf(false) }
    var newNotificationSecret by remember { mutableStateOf("") }
    var editingMessagingKey by remember { mutableStateOf(false) }
    var newMessagingKey by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    fun apply(settings: JsonElement) {
        original = settings
        val t = listOf("siteName", "footerDescription").associateWith { settings.stringAt(it) }
        top = t; initialTop = t
        val blocks = JsonObject(settingsBlocks.associateWith { settings.at("systemConfig.$it") as? JsonObject ?: JsonObject(emptyMap()) })
        config = blocks; initialConfig = blocks
        editingNotificationSecret = false; newNotificationSecret = ""
        editingMessagingKey = false; newMessagingKey = ""
    }

    suspend fun fetch() {
        try {
            // /cms/admin carries the full systemConfig (contracts, banking, secrets).
            val res = APIClient.get().request("/cms/admin")
            val s = res["data"]["settings"].takeIf { !it.isNull } ?: APIClient.get().request("/cms/settings")["data"]
            apply(s.element)
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: tr("Không thể tải dữ liệu")
        }
    }
    LaunchedEffect(Unit) { fetch() }

    val secretsDirty = (editingNotificationSecret && newNotificationSecret.isNotBlank()) || (editingMessagingKey && newMessagingKey.isNotBlank())
    val dirty = original != null && (top != initialTop || config != initialConfig || secretsDirty)
    fun close() { if (dirty && !saving) showDiscard = true else onBack() }
    BackHandler { close() }

    fun text(key: String) = top[key].orEmpty()
    fun c(path: String) = config.stringAt(path)
    fun setC(path: String, v: String) { config = config.withValueAt(path, JsonPrimitive(v)) }
    fun setInt(path: String, v: String) {
        val digits = v.filter(Char::isDigit).take(12)
        config = config.withValueAt(path, digits.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    /** Text of a contract's clauses: `clausesText` (web/iOS), falling back to legacy `content`. */
    fun clauses(block: String) = c("$block.clausesText").ifEmpty { c("$block.content") }

    fun save() {
        val hold = config.at("holding.holdDurationMinutes")?.let { (it as? JsonPrimitive)?.content?.toLongOrNull() }
        error = when {
            initialTop["siteName"].orEmpty().isNotBlank() && text("siteName").isBlank() -> tr("Tên website không được để trống")
            initialTop["footerDescription"].orEmpty().isNotBlank() && text("footerDescription").isBlank() -> tr("Mô tả chân trang không được để trống")
            hold != null && hold !in 1..120 -> tr("Thời gian giữ chỗ phải từ 1 đến 120 phút")
            editingNotificationSecret && newNotificationSecret.isNotEmpty() && newNotificationSecret.isBlank() -> tr("Khóa bí mật không hợp lệ")
            else -> null
        }
        if (error != null) return
        val source = original
        val body = buildJsonObject {
            listOf("siteName", "footerDescription").forEach { k -> text(k).trim().takeIf { it.isNotEmpty() && text(k) != initialTop[k] }?.let { put(k, it) } }
            val sc = buildJsonObject {
                settingsBlocks.forEach { block ->
                    val now = config.at(block)
                    if (now != initialConfig.at(block) && now != null) put(block, now)
                }
                if (editingNotificationSecret && newNotificationSecret.isNotBlank()) {
                    val base = source.at("systemConfig.notifications") as? JsonObject ?: JsonObject(emptyMap())
                    put("notifications", JsonObject(base + ("providerSecret" to JsonPrimitive(newNotificationSecret.trim()))))
                }
                if (editingMessagingKey && newMessagingKey.isNotBlank()) {
                    val base = source.at("systemConfig.messaging") as? JsonObject ?: JsonObject(emptyMap())
                    put("messaging", JsonObject(base + ("apiKey" to JsonPrimitive(newMessagingKey.trim()))))
                }
            }
            if (sc.isNotEmpty()) put("systemConfig", sc)
        }
        if (body.isEmpty()) return
        scope.launch {
            saving = true
            try {
                val res = APIClient.get().request("/cms/settings", method = "PUT", bodyJson = body.toString())
                ToastCenter.show(tr("Đã lưu cài đặt hệ thống thành công!"))
                val saved = res["data"]
                if (!saved.isNull && !saved["systemConfig"].isNull) apply(saved.element) else fetch()
            } catch (e: Exception) {
                error = e.message
                ToastCenter.show(e.message ?: tr("Không thể lưu cài đặt"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = FutaColors.PageBg,
        topBar = { SalesAdminTopBar(title = "Cài đặt hệ thống", onBack = { close() }) },
        bottomBar = { if (original != null) FormActionBar("Lưu cài đặt", saving, enabled = dirty, onCancel = { close() }, onSave = { save() }) }
    ) { padding ->
        when {
            original == null && loadError == null -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { FutaSkeletonBlock(height = 120.dp, radius = 16.dp) }
            }
            original == null -> AdminErrorState(loadError.orEmpty(), { loadError = null; scope.launch { fetch() } }, Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormErrorBanner(error)
                FormSection("Thông tin website chung") {
                    FormTextField("Tên website / Thương hiệu", text("siteName"), { top = top + ("siteName" to it) })
                    FormTextField("Mô tả chân trang (Footer)", text("footerDescription"), { top = top + ("footerDescription" to it) }, multiline = true)
                }
                FormSection("Thông tin tài khoản ngân hàng") {
                    FormTextField("Tên ngân hàng (ví dụ: Vietcombank, TPBank)", c("banking.bankName"), { setC("banking.bankName", it) })
                    FormTextField("Mã ngân hàng (ví dụ: VCB, TPB)", c("banking.bankCode"), { setC("banking.bankCode", it) })
                    FormTextField("Số tài khoản", c("banking.accountNumber"), { setC("banking.accountNumber", it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                    FormTextField("Chủ tài khoản (chữ hoa không dấu)", c("banking.accountHolder"), { setC("banking.accountHolder", it.uppercase()) })
                    FormTextField("Cú pháp chuyển khoản (Prefix)", c("banking.transferSyntaxPrefix"), { setC("banking.transferSyntaxPrefix", it) })
                }
                FormSection("Bảng giá gói thành viên TVV") {
                    FormTextField("Giá gói năm (VND)", c("banking.yearlyPackagePrice"), { setInt("banking.yearlyPackagePrice", it) }, keyboardType = KeyboardType.Number)
                    FormTextField("Giá gói tháng (VND)", c("banking.monthlyPackagePrice"), { setInt("banking.monthlyPackagePrice", it) }, keyboardType = KeyboardType.Number)
                }
                ContractSection("Hợp đồng môi giới (Broker Contract)", "brokerContract", "Nhập các điều khoản hợp đồng môi giới (mỗi dòng một điều khoản)…", ::c, ::setC, clauses("brokerContract"))
                ContractSection("Hợp đồng dịch vụ (Service Contract)", "serviceContract", "Nhập các điều khoản hợp đồng dịch vụ (mỗi dòng một điều khoản)…", ::c, ::setC, clauses("serviceContract"))
                FormSection("Biên bản nghiệm thu (Acceptance Report)") {
                    FormTextField("Tiêu đề biên bản", c("acceptanceReport.title"), { setC("acceptanceReport.title", it) })
                    FormTextField("Tên công ty", c("acceptanceReport.companyName"), { setC("acceptanceReport.companyName", it) })
                    FormTextField("Người đại diện", c("acceptanceReport.companyRepresentative"), { setC("acceptanceReport.companyRepresentative", it) })
                    FormTextField(
                        "Ghi chú mặc định", c("acceptanceReport.notes").ifEmpty { c("acceptanceReport.content") }, { setC("acceptanceReport.notes", it) },
                        placeholder = "Nhập các ghi chú mặc định cho biên bản nghiệm thu…", multiline = true
                    )
                }
                FormSection("Quy chế giữ chỗ (Online Holding)") {
                    FormTextField("Thời gian giữ chỗ (phút)", c("holding.holdDurationMinutes"), { setInt("holding.holdDurationMinutes", it.take(3)) }, placeholder = "15", keyboardType = KeyboardType.Number)
                    FormTextField("Thời gian chờ giữa 2 lần (phút)", c("holding.cooldownMinutes"), { setInt("holding.cooldownMinutes", it.take(4)) }, placeholder = "5", keyboardType = KeyboardType.Number)
                }
                SecretSection(
                    title = "Bảo mật thông báo (Push Notification Secret)",
                    label = "Khóa bí mật thông báo (Provider Secret)",
                    placeholder = "Nhập Secret mới",
                    configured = original.stringAt("systemConfig.notifications.providerSecret").isNotEmpty(),
                    editing = editingNotificationSecret,
                    value = newNotificationSecret,
                    onValueChange = { newNotificationSecret = it },
                    onEdit = { editingNotificationSecret = true },
                    onCancel = { editingNotificationSecret = false; newNotificationSecret = "" }
                )
                SecretSection(
                    title = "Khóa Messaging API (Zalo / SMS / Chat)",
                    label = "Messaging API Key",
                    placeholder = "Nhập API Key mới",
                    configured = original.stringAt("systemConfig.messaging.apiKey").isNotEmpty(),
                    editing = editingMessagingKey,
                    value = newMessagingKey,
                    onValueChange = { newMessagingKey = it },
                    onEdit = { editingMessagingKey = true },
                    onCancel = { editingMessagingKey = false; newMessagingKey = "" }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    DiscardChangesDialog(showDiscard, onDismiss = { showDiscard = false }, onDiscard = { showDiscard = false; onBack() })
}

@Composable
private fun ContractSection(
    title: String,
    block: String,
    clausesPlaceholder: String,
    get: (String) -> String,
    set: (String, String) -> Unit,
    clauses: String
) {
    FormSection(title) {
        FormTextField("Tiêu đề hợp đồng", get("$block.title"), { set("$block.title", it) })
        FormTextField("Tên công ty", get("$block.companyName"), { set("$block.companyName", it) })
        FormTextField("Mã số thuế", get("$block.companyTaxCode"), { set("$block.companyTaxCode", it) })
        FormTextField("Người đại diện", get("$block.companyRepresentative"), { set("$block.companyRepresentative", it) })
        FormTextField("Chức vụ", get("$block.companyRepresentativeTitle"), { set("$block.companyRepresentativeTitle", it) })
        FormTextField("Các điều khoản hợp đồng", clauses, { set("$block.clausesText", it) }, placeholder = clausesPlaceholder, multiline = true)
    }
}

/**
 * Masked secret: the stored value is never rendered. "Đổi khóa" opens a password field; the new
 * value is sent only when typed, otherwise the stored secret is left untouched.
 */
@Composable
private fun SecretSection(
    title: String,
    label: String,
    placeholder: String,
    configured: Boolean,
    editing: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    FormSection(title) {
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (configured) Icons.Default.Lock else Icons.Default.LockOpen, null, tint = if (configured) FutaColors.BrandGreen else FutaColors.Slate, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.Navy)
                    if (configured) Text("••••••••••••••••", fontSize = 12.sp, color = FutaColors.Slate)
                    else Text("Chưa cấu hình", fontSize = 12.sp, color = Color(0xFFF97316))
                }
                FutaButton(text = if (configured) "Đổi khóa" else "Thiết lập", variant = FutaButtonVariant.OUTLINE, height = 36.dp, onClick = onEdit)
            }
        } else {
            FutaFormSectionField(label = label) {
                FutaInput(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                )
            }
            Text("Khóa hiện tại được giữ nguyên nếu bạn không nhập giá trị mới.", fontSize = 11.5.sp, color = FutaColors.Slate)
            FutaButton(text = "Huỷ đổi khóa", variant = FutaButtonVariant.GHOST, height = 34.dp, onClick = onCancel)
        }
    }
}
