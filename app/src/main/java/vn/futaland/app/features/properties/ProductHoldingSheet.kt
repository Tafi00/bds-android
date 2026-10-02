package vn.futaland.app.features.properties

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.core.sales.SalesPolicy
import vn.futaland.app.designsystem.*
import vn.futaland.app.features.account.AdvisorLabeledRow
import vn.futaland.app.features.account.AdvisorSection
import vn.futaland.app.features.account.AdvisorTopBar
import vn.futaland.app.features.account.ChoiceChip
import vn.futaland.app.features.account.DocumentUploadRow
import vn.futaland.app.features.messaging.ChatWebSocketManager
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil

/** `registration["property"]`, falling back to `apartment` (iOS `nestedProperty`). */
internal val JSONValue.nestedProperty: JSONValue
    get() = if (this["property"].isNull) this["apartment"] else this["property"]

/** Why a registered unit cannot be held, or null (iOS `AdvisorSalesAvailability`). */
object AdvisorSalesAvailability {
    private val blockedStatuses = setOf("Đang giữ chỗ", "Đã cọc", "Hợp đồng cọc", "Đã bán", "Đã có người mua", "HĐMB", "Ngừng bán")

    fun unavailableReason(registration: JSONValue): String? {
        val property = registration["property"]
        val statusElement = property["status"].element as? kotlinx.serialization.json.JsonArray ?: return null
        if (property["source"].string == "ERP") {
            if (property["externalRemovedAt"].string.isNotEmpty()) return tr("Sản phẩm đã bị ERP ngừng bán nên không thể giữ chỗ")
            if (!property["externalOpenForSale"].bool) return tr("Sản phẩm không còn nằm trong rổ hàng được ERP cho phép giao dịch")
        }
        val status = statusElement.map { JSONValue(it).string }
        status.firstOrNull { it in blockedStatuses }?.let {
            return tr("Sản phẩm đang ở trạng thái \"{0}\" nên chưa thể giữ chỗ", it)
        }
        if (!status.contains("Đang mở bán")) return tr("Sản phẩm đang chờ Admin công bố lại trên website nên chưa thể giữ chỗ")
        return null
    }
}

/** 15-minute online hold and the deposit request that follows it (iOS `DepositRequestGate`). */
object DepositRequestGate {
    private const val HOLD_SECONDS = 15 * 60L

    val paymentMethods = listOf(
        "standard" to "Thanh toán chuẩn",
        "fast" to "Thanh toán nhanh",
        "loan" to "Thanh toán vay"
    )

    fun parseDate(raw: String): Instant? = raw.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Web: `onlineHoldExpiresAt`, falling back to `holdingSubmittedAt + 15 min`. */
    fun holdExpiry(reg: JSONValue): Instant? =
        parseDate(reg["onlineHoldExpiresAt"].string) ?: parseDate(reg["holdingSubmittedAt"].string)?.plusSeconds(HOLD_SECONDS)

    fun remainingSeconds(reg: JSONValue, nowMillis: Long = System.currentTimeMillis()): Int? {
        val expiry = holdExpiry(reg) ?: return null
        return maxOf(0, ceil((expiry.toEpochMilli() - nowMillis) / 1000.0).toInt())
    }

    /** The hold belongs to this (signed-in advisor's) registration and is still running. */
    fun canRequestDeposit(reg: JSONValue, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (reg["bookingStatus"].string != "online_holding") return false
        val status = reg["status"].string.lowercase()
        if (status.isNotEmpty() && status != "active" && status != "approved") return false
        val advisorId = reg["advisorId"].string
        val holderId = reg.nestedProperty["activeHoldingAdvisorId"].string
        if (advisorId.isNotEmpty() && holderId.isNotEmpty() && advisorId != holderId) return false
        val remaining = remainingSeconds(reg, nowMillis)
        return remaining == null || remaining > 0
    }

    fun countdownText(seconds: Int): String {
        val s = maxOf(0, seconds)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    /** Registration deposit amount when set, else 100,000,000 (GET /payment-qr then resolves the project default). */
    fun defaultDepositAmount(reg: JSONValue): Long {
        val amount = reg["depositAmount"].double
        return if (amount > 0) Math.round(amount) else 100_000_000L
    }
}

/** Current time that ticks every second while shown (iOS `TimelineView(.periodic)`). */
@Composable
fun rememberTickingNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** VietQR / bank transfer info returned by GET /sales/registrations/:id/payment-qr. */
@Composable
fun DepositPaymentQrView(qr: JSONValue) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        val qrUrl = qr["qrCodeUrl"].string
        if (qrUrl.isNotEmpty()) {
            AsyncImage(
                model = qrUrl,
                contentDescription = tr("Mã VietQR"),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Text("Quét qua bất kỳ app ngân hàng", fontSize = 12.sp, color = FutaColors.Slate, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        val bank = qr["bankName"].string + (if (qr["bankCode"].string.isEmpty()) "" else " (${qr["bankCode"].string})")
        AdvisorLabeledRow("Ngân hàng", bank)
        AdvisorLabeledRow("Số tài khoản", qr["accountNumber"].string)
        AdvisorLabeledRow("Chủ tài khoản", qr["accountHolder"].string)
        AdvisorLabeledRow("Số tiền cố định", qr["amount"].double.takeIf { it > 0 }?.let { LocalizedPrice.full(it) } ?: "-")
        AdvisorLabeledRow("Nội dung chuyển khoản", qr["syntax"].string)
        if (qrUrl.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, qrUrl)
                        }
                        context.startActivity(Intent.createChooser(share, tr("Chia sẻ / lưu mã QR")))
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Share, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chia sẻ / lưu mã QR", fontSize = 13.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private data class CoOwner(
    val key: Long = System.nanoTime(),
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val documentNumber: String = "",
    val permanentAddress: String = ""
)

private enum class UploadTarget(val wire: String) { ID_FRONT("idFront"), ID_BACK("idBack"), DIGITAL_ID("digitalId"), RECEIPT("receipt") }

/** Groups digits by thousands while the field keeps plain digits. */
private object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val formatted = raw.reversed().chunked(3).joinToString(".").reversed()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val digitsAfter = raw.length - offset
                val dotsAfter = if (digitsAfter > 0) (digitsAfter - 1) / 3 else 0
                return formatted.length - digitsAfter - dotsAfter
            }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset.coerceIn(0, formatted.length)).count { it != '.' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/**
 * Holding flow (iOS `ProductHoldingSheet`, web holding-booking-page.tsx):
 * step 1 "Đăng ký giữ chỗ" (POST /sales/registrations/:id/hold, or POST /sales/registrations
 * when the advisor has no active selling right yet) locks the unit for 15 minutes; step 2
 * "Yêu cầu xác nhận cọc" (POST /sales/registrations/:id/deposit-request) carries the full
 * customer file, payment method, deposit amount, UNC and the VietQR payment code.
 *
 * Opened with a registration whose hold is running, it starts directly at step 2.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductHoldingSheet(
    product: JSONValue,
    onDismiss: () -> Unit,
    onDone: suspend () -> Unit,
    onDepositSubmitted: ((JSONValue) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startsAtDeposit = remember { DepositRequestGate.canRequestDeposit(product) }
    fun day(key: String) = if (startsAtDeposit) product[key].string.take(10) else ""
    fun text(key: String) = if (startsAtDeposit) product[key].string else ""

    var depositStep by remember { mutableStateOf(startsAtDeposit) }
    var holdRegistration by remember { mutableStateOf<JSONValue?>(if (startsAtDeposit) product else null) }
    var regInfo by remember { mutableStateOf(JSONValue.EmptyObject) }
    var isCorporate by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf<JSONValue?>(null) }

    var customerName by remember { mutableStateOf(text("customerName")) }
    var customerPhone by remember { mutableStateOf(text("customerPhone")) }
    var customerEmail by remember { mutableStateOf(text("customerEmail")) }
    var customerCccd by remember { mutableStateOf(text("customerCccd")) }
    var customerGender by remember { mutableStateOf(text("customerGender")) }
    var customerBirthDate by remember { mutableStateOf(day("customerBirthDate")) }
    var customerDocumentType by remember { mutableStateOf(text("customerDocumentType")) }
    var customerDocumentIssuedAt by remember { mutableStateOf(day("customerDocumentIssuedAt")) }
    var customerDocumentIssuedPlace by remember { mutableStateOf(text("customerDocumentIssuedPlace")) }
    var customerPermanentAddress by remember { mutableStateOf(text("customerPermanentAddress")) }
    var customerContactAddress by remember { mutableStateOf(text("customerContactAddress")) }
    var companyName by remember { mutableStateOf("") }
    var taxCode by remember { mutableStateOf("") }
    var companyAddress by remember { mutableStateOf("") }
    var licenseIssuedAt by remember { mutableStateOf("") }
    var licenseIssuedPlace by remember { mutableStateOf("") }
    var representativeName by remember { mutableStateOf("") }
    var representativeTitle by remember { mutableStateOf("") }
    var coOwners by remember { mutableStateOf<List<CoOwner>>(emptyList()) }
    var uploads by remember {
        mutableStateOf<Map<UploadTarget, String>>(
            if (startsAtDeposit) mapOf(
                UploadTarget.ID_FRONT to product["customerIdFrontUrl"].string,
                UploadTarget.ID_BACK to product["customerIdBackUrl"].string,
                UploadTarget.DIGITAL_ID to product["customerDigitalIdUrl"].string
            ).filterValues { it.isNotEmpty() } else emptyMap()
        )
    }
    var uploadingCount by remember { mutableIntStateOf(0) }
    var paymentMethod by remember {
        mutableStateOf(product["paymentMethod"].string.takeIf { m -> startsAtDeposit && DepositRequestGate.paymentMethods.any { it.first == m } } ?: "standard")
    }
    val initialAmount = remember { if (startsAtDeposit) DepositRequestGate.defaultDepositAmount(product).toString() else "100000000" }
    var depositAmount by remember { mutableStateOf(initialAmount) }
    /** Last amount set by the app; the project default from /payment-qr replaces it until the advisor edits. */
    var autoDepositAmount by remember { mutableStateOf(initialAmount) }
    var notes by remember { mutableStateOf(text("notes")) }
    var showOptionalProfile by remember { mutableStateOf(false) }
    var acceptedPolicy by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var paymentQr by remember { mutableStateOf<JSONValue?>(null) }
    var isLoadingQr by remember { mutableStateOf(false) }
    var preemptedMessage by remember { mutableStateOf("") }

    val unitCode = product["unitCode"].string.ifEmpty { product["propertyCode"].string.ifEmpty { product.nestedProperty["propertyCode"].string } }
    val actualPropertyId = product["propertyId"].string
        .ifEmpty { product.nestedProperty["id"].string }
        .ifEmpty { product["id"].string }
        .ifEmpty { product.id }
    val holdRegistrationId = holdRegistration?.let { it["id"].string.ifEmpty { it.id } }.orEmpty()
    val isLiveErp = run {
        val reg = holdRegistration ?: product
        val source = reg["propertySource"].string.ifEmpty { reg.nestedProperty["source"].string }
        val externalId = reg["propertyExternalId"].string.ifEmpty { reg.nestedProperty["externalId"].string }
        source == "ERP" && externalId.isNotEmpty()
    }
    val depositAmountValue = depositAmount.toDoubleOrNull() ?: 0.0
    val requiredFieldsFilled = run {
        val contact = customerPhone.isNotBlank() && customerEmail.isNotBlank()
        if (isCorporate) contact && companyName.isNotBlank() && taxCode.isNotBlank() else contact && customerName.isNotBlank()
    }

    // ---------- networking ----------

    suspend fun loadPaymentQr(showErrors: Boolean = false) {
        val id = holdRegistrationId
        if (id.isEmpty()) return
        isLoadingQr = true
        try {
            val typed = Math.round(depositAmountValue)
            val query = if (depositAmount != autoDepositAmount && typed > 0) mapOf("amount" to typed.toString()) else emptyMap()
            val qr = APIClient.get().request("/sales/registrations/$id/payment-qr", query = query)["data"]
            paymentQr = qr
            val amount = qr["amount"].double
            if (amount > 0 && depositAmount == autoDepositAmount) {
                val value = Math.round(amount).toString()
                depositAmount = value
                autoDepositAmount = value
            }
        } catch (e: Exception) {
            if (showErrors) ToastCenter.show(e.message ?: tr("Không tạo được mã QR"), isError = true)
        } finally {
            isLoadingQr = false
        }
    }

    LaunchedEffect(Unit) {
        if (!depositStep) {
            if (actualPropertyId.isNotEmpty()) {
                runCatching { APIClient.get().request("/sales/registrations/apartment/$actualPropertyId")["data"] }
                    .onSuccess { regInfo = it }
            }
        } else {
            loadPaymentQr()
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        isSearching = true
        try {
            searchResults = APIClient.get().request("/sales/holding-customers", query = mapOf("query" to query))["data"].array
        } catch (_: Exception) {
            searchResults = emptyList()
        } finally {
            isSearching = false
        }
    }

    // Another advisor taking the unit while step 2 is open.
    DisposableEffect(depositStep) {
        val listenerKey = "ProductHoldingSheet"
        if (depositStep) {
            ChatWebSocketManager.shared.connect()
            ChatWebSocketManager.shared.addProductEventListener(listenerKey) { event ->
                if (preemptedMessage.isNotEmpty() || event["type"].string != "product_holding_updated") return@addProductEventListener
                if (event["propertyId"].string != actualPropertyId) return@addProductEventListener
                val action = event["action"].string
                if (action != "hold_locked" && action != "deposit_requested") return@addProductEventListener
                val myAdvisorId = holdRegistration?.get("advisorId")?.string.orEmpty()
                val eventAdvisorId = event["advisorId"].string
                if (myAdvisorId.isEmpty() || eventAdvisorId.isEmpty() || eventAdvisorId == myAdvisorId) return@addProductEventListener
                val advisorName = event["advisorName"].string
                val message = when {
                    action == "deposit_requested" -> tr("Căn {0} vừa được tư vấn viên khác gửi yêu cầu xác nhận cọc!", unitCode)
                    advisorName.isNotEmpty() -> tr("Căn {0} vừa được tư vấn viên {1} nhanh tay giữ chỗ trước!", unitCode, advisorName)
                    else -> tr("Căn {0} vừa được tư vấn viên khác nhanh tay giữ chỗ trước!", unitCode)
                }
                preemptedMessage = message
                ToastCenter.show(message, isError = true)
            }
        }
        onDispose { if (depositStep) ChatWebSocketManager.shared.removeProductEventListener(listenerKey) }
    }

    fun selectExistingCustomer(c: JSONValue) {
        selectedCustomer = c
        searchQuery = ""
        searchResults = emptyList()
        customerName = c["customerName"].string
        customerPhone = c["customerPhone"].string
        customerEmail = c["customerEmail"].string
        customerCccd = c["customerCccd"].string
        customerGender = c["customerGender"].string.ifEmpty { "male" }
        customerBirthDate = c["customerBirthDate"].string.take(10)
        customerDocumentType = c["customerDocumentType"].string.ifEmpty { "citizen_id" }
        customerDocumentIssuedAt = c["customerDocumentIssuedAt"].string.take(10)
        customerDocumentIssuedPlace = c["customerDocumentIssuedPlace"].string
        customerPermanentAddress = c["customerPermanentAddress"].string
        customerContactAddress = c["customerContactAddress"].string
        // Never carry over a UNC / payment receipt from a previous transaction.
        uploads = mapOf(
            UploadTarget.ID_FRONT to c["customerIdFrontUrl"].string,
            UploadTarget.ID_BACK to c["customerIdBackUrl"].string,
            UploadTarget.DIGITAL_ID to c["customerDigitalIdUrl"].string
        ).filterValues { it.isNotEmpty() }
        ToastCenter.show(tr("Đã tự động điền thông tin khách hàng {0}", customerName))
    }

    fun clearSelectedCustomer() {
        selectedCustomer = null
        customerName = ""; customerPhone = ""; customerEmail = ""; customerCccd = ""
        customerGender = ""; customerBirthDate = ""; customerDocumentType = ""
        customerDocumentIssuedAt = ""; customerDocumentIssuedPlace = ""
        customerPermanentAddress = ""; customerContactAddress = ""
        companyName = ""; taxCode = ""; companyAddress = ""; notes = ""
        uploads = emptyMap()
    }

    /** Body for POST /sales/registrations/:id/hold: empty optional fields are omitted. */
    fun quickHoldFields(builder: JsonObjectBuilder, all: Boolean) {
        fun opt(key: String, value: String) {
            val v = value.trim()
            if (v.isNotEmpty()) builder.put(key, v)
        }
        builder.put("customerName", (if (isCorporate) companyName else customerName).trim())
        builder.put("customerPhone", customerPhone.trim())
        builder.put("customerEmail", customerEmail.trim())
        opt("customerCccd", if (isCorporate) taxCode else customerCccd)
        if (all) {
            opt("customerPermanentAddress", if (isCorporate) companyAddress else customerPermanentAddress)
            opt("customerContactAddress", customerContactAddress)
            if (!isCorporate) {
                opt("customerGender", customerGender)
                opt("customerBirthDate", customerBirthDate)
                opt("customerDocumentType", customerDocumentType)
                opt("customerDocumentIssuedAt", customerDocumentIssuedAt)
                opt("customerDocumentIssuedPlace", customerDocumentIssuedPlace)
            }
            opt("customerIdFrontUrl", uploads[UploadTarget.ID_FRONT].orEmpty())
            opt("customerIdBackUrl", uploads[UploadTarget.ID_BACK].orEmpty())
            opt("customerDigitalIdUrl", uploads[UploadTarget.DIGITAL_ID].orEmpty())
        }
        opt("notes", notes)
    }

    fun submitHold() {
        if (actualPropertyId.isEmpty()) {
            ToastCenter.show(tr("Mã định danh căn hộ không hợp lệ"), isError = true)
            return
        }
        if (!requiredFieldsFilled) {
            ToastCenter.show(
                tr(
                    if (isCorporate) "Vui lòng điền Tên công ty, Mã số thuế, Điện thoại và Email để giữ chỗ nhanh."
                    else "Vui lòng điền đầy đủ Họ và tên, Điện thoại và Email để giữ chỗ nhanh."
                ),
                isError = true
            )
            return
        }
        scope.launch {
            isSubmitting = true
            try {
                // Holding requires an approved "đăng ký bán": the backend rejects holds on
                // registrations that are not active yet.
                val viewer = regInfo["viewerRegistration"]
                val registrationId = if (viewer["hasActiveRights"].bool) viewer["registrationId"].string else ""
                if (registrationId.isEmpty()) {
                    val body = buildJsonObject {
                        put("propertyId", actualPropertyId)
                        put("salesPolicyAccepted", true)
                        put("salesPolicyVersion", SalesPolicy.VERSION)
                        // Deposit amount / UNC belong to step 2, not here.
                        quickHoldFields(this, all = false)
                    }.toString()
                    APIClient.get().request("/sales/registrations", method = "POST", bodyJson = body)
                    ToastCenter.show(tr("Đã gửi hồ sơ đăng ký bán, vui lòng chờ Admin duyệt trước khi giữ chỗ"))
                    onDone()
                    onDismiss()
                    return@launch
                }
                val body = buildJsonObject { quickHoldFields(this, all = true) }.toString()
                val res = APIClient.get().request("/sales/registrations/$registrationId/hold", method = "POST", bodyJson = body)
                ToastCenter.show(tr("Đã giữ chỗ thành công căn {0}! Bạn có 15 phút để hoàn tất thủ tục và yêu cầu xác nhận cọc.", unitCode))
                onDone()
                // Web parity: continue straight into step 2 "Yêu cầu xác nhận cọc".
                var held = res["data"]
                if (held["bookingStatus"].string != "online_holding") {
                    onDismiss()
                    return@launch
                }
                if (held["id"].string.isEmpty()) held = held.with("id", registrationId)
                holdRegistration = held
                held["paymentMethod"].string.takeIf { m -> DepositRequestGate.paymentMethods.any { it.first == m } }?.let { paymentMethod = it }
                val amount = DepositRequestGate.defaultDepositAmount(held).toString()
                depositAmount = amount
                autoDepositAmount = amount
                depositStep = true
                loadPaymentQr()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không gửi được yêu cầu giữ chỗ"), isError = true)
            } finally {
                isSubmitting = false
            }
        }
    }

    /** First client-side validation error (same order and messages as web/iOS), or null. */
    fun depositValidationError(): String? {
        fun t(v: String) = v.trim()
        val required = if (isCorporate) listOf(companyName, taxCode, companyAddress, customerContactAddress, licenseIssuedAt,
            licenseIssuedPlace, representativeName, representativeTitle, customerPhone, customerEmail)
        else listOf(customerName, customerPhone, customerEmail, customerCccd, customerGender, customerBirthDate,
            customerDocumentType, customerDocumentIssuedAt, customerDocumentIssuedPlace, customerPermanentAddress, customerContactAddress)
        if (required.any { t(it).isEmpty() } || DepositRequestGate.paymentMethods.none { it.first == paymentMethod } || depositAmountValue <= 0) {
            return "Vui lòng điền đầy đủ tất cả trường bắt buộc."
        }
        if (isCorporate) {
            if (t(companyAddress).length < 5) return "Địa chỉ công ty phải có ít nhất 5 ký tự."
            if (t(customerContactAddress).length < 5) return "Địa chỉ liên hệ phải có ít nhất 5 ký tự."
        } else {
            val minDocLength = if (customerDocumentType == "passport") 6 else 9
            if (t(customerCccd).length < minDocLength) {
                return if (customerDocumentType == "passport") "Số hộ chiếu phải có ít nhất 6 ký tự." else "Số CCCD/CMND/Hộ chiếu phải có ít nhất 9 ký tự."
            }
            if (t(customerPermanentAddress).length < 5) return "Địa chỉ thường trú phải có ít nhất 5 ký tự."
            if (t(customerContactAddress).length < 5) return "Địa chỉ liên hệ phải có ít nhất 5 ký tự."
        }
        if (coOwners.any { o -> listOf(o.name, o.phone, o.email, o.documentNumber, o.permanentAddress).any { t(it).isEmpty() } }) {
            return "Vui lòng điền đầy đủ thông tin người đồng sở hữu."
        }
        if (listOf(UploadTarget.ID_FRONT, UploadTarget.ID_BACK, UploadTarget.DIGITAL_ID).any { uploads[it].isNullOrBlank() }) {
            return "Vui lòng tải lên đầy đủ CCCD/CMND/Hộ chiếu mặt trước, mặt sau và căn cước điện tử."
        }
        return null
    }

    /** Corporate details and co-owners go into `notes` (the backend has no columns for them). */
    fun combinedNotes(): String {
        var result = notes.trim()
        if (isCorporate) {
            result += "\n\nTHÔNG TIN DOANH NGHIỆP\nTên công ty: ${companyName.trim()}\nMã số thuế: ${taxCode.trim()}" +
                "\nĐịa chỉ: ${companyAddress.trim()}\nGPKD ngày cấp: $licenseIssuedAt" +
                "\nGPKD nơi cấp: ${licenseIssuedPlace.trim()}\nNgười đại diện: ${representativeName.trim()}" +
                "\nChức vụ: ${representativeTitle.trim()}"
        }
        if (coOwners.isNotEmpty()) {
            val lines = coOwners.mapIndexed { index, o ->
                "${index + 1}. ${o.name.trim()} | ${o.phone.trim()} | ${o.email.trim()} | ${o.documentNumber.trim()} | ${o.permanentAddress.trim()}"
            }
            result += "\n\nNGƯỜI ĐỒNG SỞ HỮU\n" + lines.joinToString("\n")
        }
        return result.trim()
    }

    fun submitDepositRequest() {
        val id = holdRegistrationId
        if (id.isEmpty()) {
            ToastCenter.show(tr("Không tìm thấy phiếu giữ chỗ"), isError = true)
            return
        }
        if (preemptedMessage.isNotEmpty()) {
            ToastCenter.show(preemptedMessage, isError = true)
            return
        }
        val remaining = holdRegistration?.let { DepositRequestGate.remainingSeconds(it) }
        if (remaining != null && remaining <= 0) {
            ToastCenter.show(tr("Thời gian giữ chỗ 15 phút đã hết hạn!"), isError = true)
            return
        }
        if (uploadingCount > 0) {
            ToastCenter.show(tr("Vui lòng chờ tệp tải lên hoàn tất."), isError = true)
            return
        }
        depositValidationError()?.let {
            ToastCenter.show(tr(it), isError = true)
            return
        }
        scope.launch {
            isSubmitting = true
            try {
                fun t(v: String) = v.trim()
                val body = buildJsonObject {
                    put("customerType", if (isCorporate) "corporate" else "personal")
                    put("customerName", t(if (isCorporate) companyName else customerName))
                    put("customerPhone", t(customerPhone))
                    put("customerEmail", t(customerEmail))
                    put("customerCccd", t(if (isCorporate) taxCode else customerCccd))
                    put("customerGender", if (isCorporate) "other" else customerGender)
                    put("customerDocumentType", if (isCorporate) "national_id" else customerDocumentType)
                    put("customerDocumentIssuedAt", if (isCorporate) licenseIssuedAt else customerDocumentIssuedAt)
                    put("customerDocumentIssuedPlace", t(if (isCorporate) licenseIssuedPlace else customerDocumentIssuedPlace))
                    put("customerPermanentAddress", t(if (isCorporate) companyAddress else customerPermanentAddress))
                    put("customerContactAddress", t(if (isCorporate) companyAddress else customerContactAddress))
                    put("customerIdFrontUrl", uploads[UploadTarget.ID_FRONT].orEmpty().trim())
                    put("customerIdBackUrl", uploads[UploadTarget.ID_BACK].orEmpty().trim())
                    put("customerDigitalIdUrl", uploads[UploadTarget.DIGITAL_ID].orEmpty().trim())
                    put("paymentMethod", paymentMethod)
                    put("depositAmount", depositAmountValue)
                    if (!isCorporate) put("customerBirthDate", customerBirthDate)
                    uploads[UploadTarget.RECEIPT]?.trim()?.takeIf { it.isNotEmpty() }?.let { put("depositReceiptUrl", it) }
                    combinedNotes().takeIf { it.isNotEmpty() }?.let { put("notes", it) }
                }.toString()
                val res = APIClient.get().request("/sales/registrations/$id/deposit-request", method = "POST", bodyJson = body)
                ToastCenter.show(
                    if (isLiveErp) tr("Đã gửi yêu cầu xác nhận cọc căn {0} lên FUTA Land ERP. Đang chờ xác nhận.", unitCode)
                    else tr("Đã ghi nhận yêu cầu xác nhận cọc căn {0}.", unitCode)
                )
                onDepositSubmitted?.invoke(res["data"])
                onDone()
                onDismiss()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không gửi được yêu cầu xác nhận cọc"), isError = true)
            } finally {
                isSubmitting = false
            }
        }
    }

    // ---------- UI ----------

    @Composable
    fun Field(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, onChange: (String) -> Unit) {
        FutaFormSectionField(label = tr(label)) {
            if (singleLine) {
                FutaInput(value = value, onValueChange = onChange, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth())
            } else {
                FutaTextArea(value = value, onValueChange = onChange, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    fun DateField(label: String, value: String, onChange: (String) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.5.sp, color = FutaColors.Navy, modifier = Modifier.weight(1f))
            val current = runCatching { LocalDate.parse(value) }.getOrNull()
            Text(
                text = current?.let { "%02d/%02d/%04d".format(it.dayOfMonth, it.monthValue, it.year) } ?: tr("Chọn ngày"),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = FutaColors.BrandGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val base = current ?: LocalDate.now()
                        DatePickerDialog(context, { _, y, m, d ->
                            onChange("%04d-%02d-%02d".format(y, m + 1, d))
                        }, base.year, base.monthValue - 1, base.dayOfMonth).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            if (current != null) {
                IconButton(onClick = { onChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Cancel, tr("Xóa"), tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    @Composable
    fun Choices(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        FutaFormSectionField(label = tr(label)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, title) -> ChoiceChip(title, selected == value) { onSelect(value) } }
            }
        }
    }

    @Composable
    fun PersonalDocumentFields(required: Boolean) {
        val mark = if (required) " *" else ""
        Choices("Giới tính$mark", listOf("male" to "Nam", "female" to "Nữ", "other" to "Khác"), customerGender) { customerGender = it }
        DateField(tr("Ngày sinh") + mark, customerBirthDate) { customerBirthDate = it }
        Choices(
            "Loại giấy tờ$mark",
            listOf("citizen_id" to "Căn cước công dân", "national_id" to "Chứng minh nhân dân", "passport" to "Hộ chiếu"),
            customerDocumentType
        ) { customerDocumentType = it }
        Field("Số CCCD/CMND/Hộ chiếu$mark", customerCccd) { customerCccd = it }
        DateField(tr("Ngày cấp") + mark, customerDocumentIssuedAt) { customerDocumentIssuedAt = it }
        Field("Nơi cấp$mark", customerDocumentIssuedPlace) { customerDocumentIssuedPlace = it }
    }

    @Composable
    fun ImageUpload(title: String, target: UploadTarget) {
        DocumentUploadRow(
            title = title,
            url = uploads[target].orEmpty(),
            enabled = uploadingCount == 0,
            allowCamera = true,
            onRemove = { uploads = uploads - target },
            onUploadingChange = { uploadingCount += if (it) 1 else -1 },
            uploadName = target.wire,
            successMessage = if (target == UploadTarget.RECEIPT) "Đã tải ủy nhiệm chi lên hệ thống" else "Đã tải giấy tờ lên hệ thống",
            onUploaded = { uploads = uploads + (target to it) }
        )
    }

    @Composable
    fun IdentityUploads(required: Boolean) {
        val mark = if (required) " *" else ""
        ImageUpload(tr("Mặt trước CCCD/CMND/Hộ chiếu") + mark, UploadTarget.ID_FRONT)
        HorizontalDivider(color = FutaColors.PanelDivider)
        ImageUpload(tr("Mặt sau CCCD/CMND/Hộ chiếu") + mark, UploadTarget.ID_BACK)
        HorizontalDivider(color = FutaColors.PanelDivider)
        ImageUpload(tr("Căn cước điện tử (VNeID)") + mark, UploadTarget.DIGITAL_ID)
    }

    @Composable
    fun ContactFields(required: Boolean) {
        val mark = if (required) " *" else ""
        if (isCorporate) {
            Field("Tên công ty *", companyName) { companyName = it }
            Field("Mã số thuế *", taxCode) { taxCode = it }
        } else {
            Field("Họ và tên *", customerName) { customerName = it }
        }
        Field("Điện thoại *", customerPhone, KeyboardType.Phone) { customerPhone = it }
        Field("Email *", customerEmail, KeyboardType.Email) { customerEmail = it }
        if (isCorporate) {
            Field("Địa chỉ thường trú (sau sáp nhập)$mark", companyAddress, singleLine = false) { companyAddress = it }
        } else {
            Field("Địa chỉ thường trú (sau sáp nhập)$mark", customerPermanentAddress, singleLine = false) { customerPermanentAddress = it }
        }
        Field("Địa chỉ liên hệ$mark", customerContactAddress, singleLine = false) { customerContactAddress = it }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                AdvisorTopBar(
                    title = if (!depositStep) tr("Giữ chỗ: {0}", unitCode) else tr("Yêu cầu xác nhận cọc (Bước 2/2)"),
                    onBack = { if (!isSubmitting) onDismiss() }
                )
            },
            containerColor = Color(0xFFF7F8FA),
            modifier = Modifier.imePadding()
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .clearFocusOnTap()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (depositStep) {
                    val now = rememberTickingNow()
                    val remaining = holdRegistration?.let { DepositRequestGate.remainingSeconds(it, now) }
                    AdvisorSection {
                        when {
                            preemptedMessage.isNotEmpty() -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Report, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Đã có tư vấn viên khác giữ chỗ trước!", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                                Text(preemptedMessage, fontSize = 12.sp, color = FutaColors.Slate)
                            }
                            remaining == 0 -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.TimerOff, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Thời gian giữ chỗ 15 phút đã hết hạn!", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                                Text("Căn đã được nhả ra cho TVV khác đăng ký. Bạn sẽ có thời gian chờ (cooldown) 5 phút.", fontSize = 12.sp, color = FutaColors.Slate)
                            }
                            else -> {
                                val color = if ((remaining ?: Int.MAX_VALUE) < 180) Color(0xFFDC2626) else FutaColors.BrandOrange
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = color, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        tr("Thời gian giữ chỗ online còn lại: {0}", remaining?.let { DepositRequestGate.countdownText(it) } ?: "--:--"),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                }
                                Text("Vui lòng hoàn thành bước Yêu cầu xác nhận cọc trước khi hết thời gian giữ chỗ.", fontSize = 12.sp, color = FutaColors.Slate)
                            }
                        }
                    }
                }

                AdvisorSection(header = "Bất động sản") {
                    AdvisorLabeledRow("Mã căn", unitCode)
                    val projectName = product["projectName"].string.ifEmpty { product["property"]["projectName"].string }
                    AdvisorLabeledRow("Dự án / Phân khu", projectName.translated("project"))
                    val price = product["price"].double.takeIf { it > 0 } ?: product["sellPrice"].double.takeIf { it > 0 } ?: product.nestedProperty["price"].double
                    AdvisorLabeledRow("Giá niêm yết", LocalizedPrice.full(price))
                }

                if (!depositStep) {
                    AdvisorSection(header = "Tình trạng bán hàng") {
                        val maxSlots = regInfo["maxAdvisors"].int
                        AdvisorLabeledRow("Tư vấn viên đang đăng ký", "${regInfo["activeAdvisors"].array.size} / ${if (maxSlots > 0) maxSlots else 5}")
                        AdvisorLabeledRow("Số suất còn lại", "${regInfo["remainingSlots"].int}")
                    }
                }

                // Existing customer search (GET /sales/holding-customers)
                AdvisorSection(
                    header = "Tìm khách hàng có sẵn",
                    footer = "Chọn khách hàng đã lưu để tự động điền thông tin, không cần nhập lại."
                ) {
                    selectedCustomer?.let { selected ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HowToReg, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            VerbatimText(
                                "${selected["customerName"].string} (${selected["customerPhone"].string})",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FutaColors.BrandGreen,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { clearSelectedCustomer() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Cancel, tr("Bỏ chọn khách hàng"), tint = FutaColors.Slate, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    FutaInput(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = tr("Tên, số điện thoại hoặc CCCD khách hàng"),
                        leadingIcon = Icons.Default.Search,
                        trailingIcon = if (isSearching) {
                            { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = FutaColors.BrandGreen) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    searchResults.forEach { customer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectExistingCustomer(customer) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                VerbatimText(customer["customerName"].string, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                                var line = tr("SĐT: {0}", customer["customerPhone"].string)
                                customer["customerCccd"].string.takeIf { it.isNotEmpty() }?.let { line += " · " + tr("CCCD: {0}", it) }
                                VerbatimText(line, fontSize = 12.sp, color = FutaColors.Slate)
                            }
                            if (customer["hasTransaction"].bool) {
                                vn.futaland.app.features.account.AdvisorBadge(tr("Đã có giao dịch"), FutaColors.BrandGreen)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = FutaColors.Slate, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                AdvisorSection(header = "Loại khách hàng") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(tr("Khách cá nhân"), !isCorporate) { isCorporate = false }
                        ChoiceChip(tr("Khách doanh nghiệp"), isCorporate) { isCorporate = true }
                    }
                }

                if (!depositStep) {
                    // ---------- Step 1: Đăng ký giữ chỗ (15 phút) ----------
                    AdvisorSection(header = "Thông tin liên hệ", footer = "Giữ chỗ 15 phút chỉ cần các trường có dấu *.") {
                        ContactFields(required = false)
                        Field("Ghi chú giữ chỗ", notes, singleLine = false) { notes = it }
                    }

                    AdvisorSection(
                        header = "Hồ sơ bổ sung (không bắt buộc)",
                        footer = "Có thể bổ sung sau khi giữ chỗ, trước khi gửi yêu cầu xác nhận cọc."
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showOptionalProfile = !showOptionalProfile }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isCorporate) "Giấy tờ người đại diện" else "Giấy tờ & nhân khẩu",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FutaColors.Navy,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(if (showOptionalProfile) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = FutaColors.Slate)
                        }
                        if (showOptionalProfile) {
                            if (!isCorporate) PersonalDocumentFields(required = false)
                            IdentityUploads(required = false)
                        }
                    }

                    AdvisorSection(header = "Chính sách bán hàng") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { acceptedPolicy = !acceptedPolicy }) {
                            Checkbox(
                                checked = acceptedPolicy,
                                onCheckedChange = { acceptedPolicy = it },
                                colors = CheckboxDefaults.colors(checkedColor = FutaColors.BrandGreen)
                            )
                            Text(
                                tr("Tôi đã phổ biến và khách hàng đồng ý tuân thủ Chính sách bán hàng & Giữ chỗ FutaLand (Phiên bản {0})", SalesPolicy.VERSION),
                                fontSize = 12.5.sp,
                                color = FutaColors.Navy
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APIClient.termsOfServiceUrl))) }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Description, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Xem Chính sách bán hàng", fontSize = 13.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    FutaButton(
                        text = if (isSubmitting) "Đang gửi..." else "Xác nhận đăng ký giữ chỗ",
                        enabled = acceptedPolicy && requiredFieldsFilled && !isSubmitting && uploadingCount == 0,
                        onClick = { submitHold() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Bước 1/2: khóa căn 15 phút, chưa gửi sang ERP. Sau khi giữ chỗ thành công, bạn chuyển sang Bước 2/2 – Yêu cầu xác nhận cọc (phương thức thanh toán, số tiền cọc, ủy nhiệm chi, mã QR).",
                        fontSize = 11.5.sp,
                        color = FutaColors.Slate
                    )
                } else {
                    // ---------- Step 2: Yêu cầu xác nhận cọc ----------
                    AdvisorSection(header = "Thông tin liên hệ") { ContactFields(required = true) }

                    if (isCorporate) {
                        AdvisorSection(header = "Thông tin doanh nghiệp") {
                            DateField(tr("GPKD - ngày cấp *"), licenseIssuedAt) { licenseIssuedAt = it }
                            Field("GPKD - nơi cấp *", licenseIssuedPlace) { licenseIssuedPlace = it }
                            Field("Người đại diện *", representativeName) { representativeName = it }
                            Field("Chức vụ *", representativeTitle) { representativeTitle = it }
                        }
                    } else {
                        AdvisorSection(header = "Giấy tờ & nhân khẩu") { PersonalDocumentFields(required = true) }
                    }

                    AdvisorSection(
                        header = "Ảnh giấy tờ *",
                        footer = "Bắt buộc đủ mặt trước, mặt sau CCCD/CMND/Hộ chiếu và căn cước điện tử."
                    ) { IdentityUploads(required = true) }

                    AdvisorSection(header = "Thông tin người đồng sở hữu") {
                        coOwners.forEachIndexed { index, owner ->
                            fun update(change: (CoOwner) -> CoOwner) {
                                coOwners = coOwners.map { if (it.key == owner.key) change(it) else it }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Người đồng sở hữu {0}", index + 1), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                                IconButton(onClick = { coOwners = coOwners.filterNot { it.key == owner.key } }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Delete, tr("Xóa người đồng sở hữu"), tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                }
                            }
                            Field("Họ và tên *", owner.name) { v -> update { it.copy(name = v) } }
                            Field("Điện thoại *", owner.phone, KeyboardType.Phone) { v -> update { it.copy(phone = v) } }
                            Field("Email *", owner.email, KeyboardType.Email) { v -> update { it.copy(email = v) } }
                            Field("Số CCCD/CMND/Hộ chiếu *", owner.documentNumber) { v -> update { it.copy(documentNumber = v) } }
                            Field("Địa chỉ thường trú *", owner.permanentAddress, singleLine = false) { v -> update { it.copy(permanentAddress = v) } }
                            HorizontalDivider(color = FutaColors.PanelDivider)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { coOwners = coOwners + CoOwner() }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Thêm người đồng sở hữu", fontSize = 13.5.sp, color = FutaColors.BrandGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    AdvisorSection(
                        header = "Thông tin đặt cọc",
                        footer = "Khách chuyển khoản xong đưa UNC cho TVV đính kèm trước khi xác nhận cọc."
                    ) {
                        Choices("Phương thức thanh toán *", DepositRequestGate.paymentMethods.map { it.first to tr(it.second) }, paymentMethod) { paymentMethod = it }
                        FutaFormSectionField(label = tr("Số tiền đặt cọc (VNĐ) *")) {
                            FutaInput(
                                value = depositAmount,
                                onValueChange = { depositAmount = it.filter(Char::isDigit).take(15) },
                                visualTransformation = ThousandsTransformation,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = { Text("đ", color = FutaColors.Slate) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ImageUpload(tr("Ủy nhiệm chi / Chứng từ thanh toán của khách hàng"), UploadTarget.RECEIPT)
                        Field("Ghi chú thêm về giao dịch đặt cọc...", notes, singleLine = false) { notes = it }
                    }

                    AdvisorSection(header = "Mã thanh toán VietQR / SePay đặt cọc") {
                        val qr = paymentQr
                        if (qr != null) {
                            DepositPaymentQrView(qr)
                            if (qr["amount"].double > 0 && depositAmountValue > 0 && Math.round(qr["amount"].double) != Math.round(depositAmountValue)) {
                                Text("Số tiền cọc đã đổi. Bấm \"Sinh lại mã QR\" để mã QR theo số tiền mới.", fontSize = 12.sp, color = FutaColors.BrandOrange)
                            }
                        } else if (isLoadingQr) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = FutaColors.BrandGreen)
                                Spacer(Modifier.width(8.dp))
                                Text("Đang tạo mã thanh toán QR...", fontSize = 13.sp, color = FutaColors.Slate)
                            }
                        }
                        FutaButton(
                            text = if (qr == null) "Tạo mã QR thanh toán cọc" else "Sinh lại mã QR",
                            variant = FutaButtonVariant.OUTLINE,
                            icon = Icons.Default.QrCode,
                            enabled = !isLoadingQr,
                            onClick = { scope.launch { loadPaymentQr(showErrors = true) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val now = rememberTickingNow()
                    val expired = holdRegistration?.let { DepositRequestGate.remainingSeconds(it, now) }?.let { it <= 0 } ?: false
                    FutaButton(
                        text = when {
                            isSubmitting && isLiveErp -> "Đang gửi ERP..."
                            isSubmitting -> "Đang gửi..."
                            else -> "Gửi yêu cầu xác nhận cọc"
                        },
                        enabled = !isSubmitting && uploadingCount == 0 && !expired && preemptedMessage.isEmpty(),
                        onClick = { submitDepositRequest() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (isLiveErp) "Yêu cầu xác nhận cọc: Thông tin sẽ được gửi sang ERP để chính thức khóa căn."
                        else "Yêu cầu xác nhận cọc: Thông tin sẽ được gửi đến Quản trị viên để xác nhận.",
                        fontSize = 11.5.sp,
                        color = FutaColors.Slate
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
