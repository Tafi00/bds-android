package vn.futaland.app.features.account

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.translated
import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.i18n.LocalizedPrice
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Contracts (iOS ContractsView): /contracts list with search, status filter and sort, a detail
// page with status changes, e-signature, share and delete, and a create page whose apartment
// comes from the advisor sales inventory (/sales/inventory).

private val contractStatuses = listOf(
    "draft" to "Bản nháp",
    "deposited" to "Đã cọc",
    "signed" to "Đã ký",
    "active" to "Hiệu lực",
    "expired" to "Hết hạn",
    "cancelled" to "Đã huỷ"
)
private val contractSorts = listOf(
    "newest" to "Mới nhất",
    "oldest" to "Cũ nhất",
    "price_desc" to "Giá cao đến thấp",
    "price_asc" to "Giá thấp đến cao"
)

private fun contractStatusLabel(status: String) = contractStatuses.firstOrNull { it.first == status }?.second ?: status
private fun contractStatusColor(status: String) = when (status) {
    "deposited" -> Color(0xFFF97316)
    "signed" -> Color(0xFF2563EB)
    "active" -> FutaColors.BrandGreen
    "expired" -> Color(0xFF7C3AED)
    "cancelled" -> Color(0xFFDC2626)
    else -> Color(0xFF64748B)
}

private fun contractEnc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
private fun contractCode(c: JSONValue) = c["apartmentCode"].string.ifEmpty { c["propertyCode"].string }
private fun money(value: Double) = LocalizedPrice.full(value)

@Composable
fun AdminContractsScreen(
    onBack: () -> Unit,
    initialContractId: String? = null
) {
    var contracts by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadedOnce by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("newest") }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showFilter by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var detail by remember { mutableStateOf<JSONValue?>(null) }
    var creating by remember { mutableStateOf(false) }
    val canCreate = AppSession.shared.hasPermission("contracts:create")

    // Deep link (notification tap /contracts?contractId=…): open the detail page directly.
    LaunchedEffect(initialContractId) {
        val target = initialContractId?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        try {
            val res = APIClient.get().request("/contracts/${contractEnc(target)}")
            val d = res["data"]
            if (!d.isNull && d.id == target) detail = d
            else ToastCenter.show(tr("Hợp đồng không còn khả dụng hoặc bạn không có quyền truy cập."), isError = true)
        } catch (_: Exception) {
            ToastCenter.show(tr("Hợp đồng không còn khả dụng hoặc bạn không có quyền truy cập."), isError = true)
        }
    }

    LaunchedEffect(search, statusFilter, sort, page, reloadKey) {
        if (loadedOnce) delay(300)
        loading = true
        try {
            val q = mutableMapOf("page" to "$page", "limit" to "20", "sort" to sort)
            search.trim().takeIf { it.isNotEmpty() }?.let { q["search"] = it }
            if (statusFilter.isNotEmpty()) q["status"] = statusFilter
            val res = APIClient.get().request("/contracts", query = q)
            contracts = res["data"].array
            total = res["pagination"]["total"].int
            totalPages = maxOf(1, res["pagination"]["totalPages"].int)
            error = null
            loadedOnce = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: tr("Không tải được dữ liệu")
        } finally {
            loading = false
        }
    }

    fun reload() { reloadKey++ }
    val hasQuery = search.isNotBlank() || statusFilter.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = CrmPageBackground,
            topBar = {
                Surface(color = FutaColors.PageBg, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FutaHeaderIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", onClick = onBack)
                        Text("Hợp đồng giao dịch", fontSize = 17.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (canCreate) {
                            FutaHeaderIconButton(icon = Icons.Default.Add, contentDescription = "Tạo hợp đồng mới", tint = FutaColors.BrandGreen, onClick = { creating = true })
                        } else {
                            Spacer(Modifier.width(40.dp))
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FutaInput(
                            value = search,
                            onValueChange = { search = it; page = 1 },
                            placeholder = "Mã căn hộ, khách hàng...",
                            leadingIcon = Icons.Default.Search,
                            trailingIcon = if (search.isNotEmpty()) {
                                {
                                    Icon(Icons.Default.Close, tr("Xoá"), tint = FutaColors.Slate, modifier = Modifier.size(18.dp).clickable { search = ""; page = 1 })
                                }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, if (sort != "newest") FutaColors.BrandGreen else Color(0xFFE2E8F0)),
                                modifier = Modifier.size(46.dp).clickable { showSortMenu = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, tr("Sắp xếp"), tint = if (sort != "newest") FutaColors.BrandGreen else FutaColors.Slate)
                                }
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, containerColor = Color.White) {
                                contractSorts.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 14.sp, fontWeight = if (sort == key) FontWeight.Bold else FontWeight.Normal, color = FutaColors.Navy) },
                                        trailingIcon = { if (sort == key) Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen) },
                                        onClick = { sort = key; page = 1; showSortMenu = false }
                                    )
                                }
                            }
                        }
                        CrmFilterButton(activeCount = if (statusFilter.isNotEmpty()) 1 else 0, onClick = { showFilter = true })
                    }
                }
                item {
                    CrmAppliedFilterChips(
                        chips = if (statusFilter.isNotEmpty()) listOf(tr("Trạng thái: {0}", tr(contractStatusLabel(statusFilter))) to { statusFilter = ""; page = 1 }) else emptyList(),
                        onClearAll = { statusFilter = ""; search = ""; page = 1 }
                    )
                }

                when {
                    loading && contracts.isEmpty() -> items(5) { FutaAdminRowSkeleton() }
                    error != null && contracts.isEmpty() -> item { AdvisorErrorState(message = error ?: "", onRetry = { reload() }) }
                    contracts.isEmpty() -> item {
                        if (hasQuery) {
                            FutaEmptyState(
                                title = "Không có kết quả phù hợp",
                                message = "Không tìm thấy hợp đồng giao dịch nào phù hợp với bộ lọc.",
                                icon = Icons.Default.SearchOff,
                                actionButton = { FutaButton(text = "Xoá bộ lọc", variant = FutaButtonVariant.OUTLINE, onClick = { statusFilter = ""; search = ""; page = 1 }) }
                            )
                        } else {
                            FutaEmptyState(
                                title = "Không có hợp đồng",
                                message = "Tạo hợp đồng mới hoặc thử bộ lọc khác.",
                                icon = Icons.Default.Description,
                                actionButton = if (canCreate) {
                                    { FutaButton(text = "Tạo hợp đồng mới", icon = Icons.Default.Add, onClick = { creating = true }) }
                                } else null
                            )
                        }
                    }
                    else -> {
                        items(contracts, key = { it.id }) { contract ->
                            ContractCardRowItem(contract = contract, onClick = { detail = contract })
                        }
                        item {
                            CrmPaginationBar(
                                page = page,
                                totalPages = totalPages,
                                total = total,
                                onPrevious = { if (page > 1) page-- },
                                onNext = { if (page < totalPages) page++ }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }

        detail?.let { c ->
            ContractDetailPage(
                initial = c,
                onBack = { detail = null },
                onChanged = { reload() },
                onDeleted = { detail = null; reload() }
            )
        }
        if (creating) {
            ContractFormPage(
                contract = null,
                onBack = { creating = false },
                onSaved = { saved ->
                    creating = false
                    reload()
                    if (saved != null && !saved.isNull && saved.id.isNotEmpty()) detail = saved
                }
            )
        }
    }

    if (showFilter) {
        var draft by remember { mutableStateOf(statusFilter) }
        FutaBottomSheet(
            visible = true,
            onDismiss = { showFilter = false },
            title = "Bộ lọc hợp đồng",
            footer = {
                CrmFilterSheetFooter(
                    onReset = { draft = "" },
                    onCancel = { showFilter = false },
                    onApply = { statusFilter = draft; page = 1; showFilter = false }
                )
            }
        ) {
            CrmFilterOptionGroup(
                title = "Trạng thái",
                options = listOf("") + contractStatuses.map { it.first },
                selected = draft,
                label = { if (it.isEmpty()) "Tất cả" else contractStatusLabel(it) },
                onSelect = { draft = it }
            )
        }
    }
}

@Composable
private fun ContractCardRowItem(contract: JSONValue, onClick: () -> Unit) {
    val status = contract["status"].string.ifEmpty { "draft" }
    FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder, onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEFF6FF)) {
                    VerbatimText(
                        contractCode(contract).ifEmpty { tr("Mã hợp đồng") },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                AdvisorBadge(text = contractStatusLabel(status), color = contractStatusColor(status))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    VerbatimText(contract["customerName"].string.ifEmpty { "-" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    VerbatimText(contract["customerPhone"].string, fontSize = 11.5.sp, color = FutaColors.Slate)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(contract["rentalPrice"].double), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    if (contract["contractDuration"].string.isNotEmpty()) {
                        VerbatimText(contract["contractDuration"].string, fontSize = 11.sp, color = FutaColors.Slate)
                    }
                }
            }
        }
    }
}

// MARK: - Detail

@Composable
private fun ContractDetailPage(
    initial: JSONValue,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contract by remember(initial.id) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var showStatusSheet by remember { mutableStateOf(false) }
    var pendingStatus by remember { mutableStateOf<String?>(null) }
    var showSignPad by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val canEdit = AppSession.shared.hasPermission("contracts:edit")

    val id = contract.id
    val status = contract["status"].string.ifEmpty { "draft" }
    val code = contractCode(contract)

    LaunchedEffect(id) {
        try {
            val fresh = APIClient.get().request("/contracts/${contractEnc(id)}")["data"]
            if (fresh.id == id) contract = fresh
        } catch (_: Exception) {}
    }

    fun changeStatus(next: String) {
        scope.launch {
            busy = true
            try {
                val body = buildJsonObject { put("status", next) }
                val res = APIClient.get().request("/contracts/${contractEnc(id)}/status", method = "PATCH", bodyJson = body.toString())
                if (!res["data"].isNull) contract = res["data"]
                ToastCenter.show(tr("Đã cập nhật trạng thái hợp đồng"))
                onChanged()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không cập nhật được"), isError = true)
            } finally {
                busy = false
            }
        }
    }

    // iOS keeps the drawn signature on the device only; the API records the signing status and date.
    fun sign() {
        scope.launch {
            busy = true
            try {
                val body = buildJsonObject {
                    put("status", "signed")
                    put("contractSignDate", Instant.now().toString())
                }
                val res = APIClient.get().request("/contracts/${contractEnc(id)}", method = "PUT", bodyJson = body.toString())
                if (!res["data"].isNull) contract = res["data"]
                ToastCenter.show(tr("Ký hợp đồng thành công"))
                onChanged()
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không ký được hợp đồng"), isError = true)
            } finally {
                busy = false
            }
        }
    }

    fun shareSummary() {
        val text = listOf(
            tr("HỢP ĐỒNG BẤT ĐỘNG SẢN FUTALAND"),
            "----------------------------------",
            tr("Mã căn hộ: {0}", code),
            tr("Khách hàng: {0}", contract["customerName"].string),
            tr("Số điện thoại: {0}", contract["customerPhone"].string),
            tr("Giá trị: {0}", money(contract["rentalPrice"].double)),
            tr("Tiền cọc: {0}", money(contract["rentalDeposit"].double)),
            tr("Tiền giữ chỗ: {0}", money(contract["holdingDeposit"].double)),
            tr("Thời hạn: {0}", contract["contractDuration"].string),
            tr("Trạng thái: {0}", tr(contractStatusLabel(status))),
            "----------------------------------",
            tr("Tạo lúc: {0}", shortDate(contract["createdAt"].string))
        ).joinToString("\n")
        crmShareText(context, text, "Chia sẻ / Xuất tóm tắt hợp đồng")
    }

    Box(Modifier.fillMaxSize()) {
        CrmFullScreenPage(
            title = tr("Hợp đồng {0}", code),
            onBack = onBack,
            actions = {
                IconButton(onClick = { shareSummary() }) { Icon(Icons.Default.IosShare, tr("Chia sẻ"), tint = FutaColors.Navy) }
                if (canEdit) {
                    TextButton(onClick = { editing = true }) {
                        Text("Sửa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    }
                }
            }
        ) {
            FutaCard(modifier = Modifier.fillMaxWidth(), borderColor = FutaColors.LightBlueBorder) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerbatimText(code.ifEmpty { "-" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy, modifier = Modifier.weight(1f))
                        AdvisorBadge(text = contractStatusLabel(status), color = contractStatusColor(status))
                    }
                    Text(money(contract["rentalPrice"].double), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    if (contract["creatorInfo"]["name"].string.isNotEmpty()) {
                        Text(tr("Người tạo: {0}", contract["creatorInfo"]["name"].string), fontSize = 12.sp, color = FutaColors.Slate)
                    }
                }
            }

            AdvisorSection(header = "Bất động sản & Giá") {
                AdvisorLabeledRow("Mã căn hộ", code)
                AdvisorLabeledRow("Giá thuê / bán", money(contract["rentalPrice"].double))
                AdvisorLabeledRow("Tiền cọc thuê", money(contract["rentalDeposit"].double))
                AdvisorLabeledRow("Tiền giữ chỗ", money(contract["holdingDeposit"].double))
                AdvisorLabeledRow("Thời hạn hợp đồng", contract["contractDuration"].string)
            }

            AdvisorSection(header = "Khách hàng") {
                AdvisorLabeledRow("Họ và tên", contract["customerName"].string)
                AdvisorLabeledRow("Điện thoại", contract["customerPhone"].string)
                CrmContactActions(phone = contract["customerPhone"].string, showZalo = false)
            }

            val dates = listOf(
                "Ngày bắt đầu" to "contractStartDate",
                "Ngày kết thúc" to "contractEndDate",
                "Ngày ký" to "contractSignDate",
                "Ngày đặt cọc" to "depositDate"
            ).filter { contract[it.second].string.isNotEmpty() }
            if (dates.isNotEmpty()) {
                AdvisorSection(header = "Thời gian") {
                    dates.forEach { (label, key) -> AdvisorLabeledRow(label, shortDate(contract[key].string)) }
                }
            }

            val shared = contract["sharedSalesInfo"].array
            if (shared.isNotEmpty()) {
                AdvisorSection(header = "Nhân sự cùng phụ trách") {
                    VerbatimText(shared.joinToString(", ") { it["name"].string }, fontSize = 13.5.sp, color = FutaColors.Navy)
                }
            }

            AdvisorSection(header = "Trạng thái hợp đồng") {
                CrmSelectRow("Trạng thái", tr(contractStatusLabel(status)), enabled = canEdit && !busy) { showStatusSheet = true }
            }

            AdvisorSection(header = "Ký hợp đồng điện tử") {
                if (status == "signed" || status == "active") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(18.dp))
                        Text("Hợp đồng đã được ký xác nhận", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen, modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    FutaButton(
                        text = if (busy) "Đang xử lý..." else "Mở bảng vẽ ký điện tử",
                        icon = Icons.Default.Draw,
                        variant = FutaButtonVariant.OUTLINE,
                        enabled = canEdit && !busy && status != "cancelled" && status != "expired",
                        onClick = { showSignPad = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            FutaButton(
                text = "Chia sẻ / Xuất tóm tắt hợp đồng",
                icon = Icons.Default.IosShare,
                variant = FutaButtonVariant.MINT,
                onClick = { shareSummary() },
                modifier = Modifier.fillMaxWidth()
            )

            if (canEdit) {
                CrmDangerZone(buttonText = "Xoá hợp đồng", enabled = !busy) { confirmDelete = true }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (editing) {
            ContractFormPage(
                contract = contract,
                onBack = { editing = false },
                onSaved = { saved ->
                    if (saved != null && !saved.isNull) contract = saved
                    editing = false
                    onChanged()
                }
            )
        }
    }

    CrmOptionSheet(
        visible = showStatusSheet,
        title = "Trạng thái",
        options = contractStatuses.map { it.first },
        selected = status,
        label = { contractStatusLabel(it) },
        onSelect = { if (it != status) pendingStatus = it },
        onDismiss = { showStatusSheet = false }
    )
    CrmConfirmDialog(
        visible = pendingStatus != null,
        title = "Đổi trạng thái hợp đồng?",
        message = tr("Chuyển hợp đồng sang \"{0}\"?", tr(contractStatusLabel(pendingStatus.orEmpty()))),
        confirmText = "Xác nhận",
        destructive = pendingStatus == "cancelled",
        onConfirm = { pendingStatus?.let { changeStatus(it) } },
        onDismiss = { pendingStatus = null }
    )
    CrmConfirmDialog(
        visible = confirmDelete,
        title = "Xoá hợp đồng?",
        message = "Hợp đồng sẽ bị xoá vĩnh viễn và không thể khôi phục.",
        confirmText = "Xoá vĩnh viễn",
        destructive = true,
        onConfirm = {
            scope.launch {
                busy = true
                try {
                    APIClient.get().request("/contracts/${contractEnc(id)}", method = "DELETE")
                    ToastCenter.show(tr("Đã xoá hợp đồng"))
                    onDeleted()
                } catch (e: Exception) {
                    ToastCenter.show(e.message ?: tr("Không xoá được"), isError = true)
                } finally {
                    busy = false
                }
            }
        },
        onDismiss = { confirmDelete = false }
    )

    if (showSignPad) {
        Dialog(onDismissRequest = { showSignPad = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth(0.94f)) {
                FutaSignaturePad(
                    title = "Ký hợp đồng",
                    onSave = { dataUrl ->
                        showSignPad = false
                        if (dataUrl.isNotEmpty()) sign() else ToastCenter.show(tr("Không thể xuất ảnh chữ ký"), isError = true)
                    },
                    onCancel = { showSignPad = false }
                )
            }
        }
    }
}

// MARK: - Create / edit

private fun digitsOnly(value: String) = value.filter { it.isDigit() }
private fun groupThousands(value: String): String {
    val digits = digitsOnly(value).trimStart('0').ifEmpty { if (value.any { it.isDigit() }) "0" else "" }
    return digits.reversed().chunked(3).joinToString(".").reversed()
}
private fun amountText(value: Double) = if (value > 0) groupThousands(value.toLong().toString()) else ""

private fun isoToLocalDate(iso: String): LocalDate? =
    runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

private fun localDateToIso(date: LocalDate): String = date.atStartOfDay(ZoneOffset.UTC).toInstant().toString()

@Composable
private fun ContractFormPage(
    contract: JSONValue?,
    onBack: () -> Unit,
    onSaved: (JSONValue?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = contract != null

    var inventory by remember { mutableStateOf<List<JSONValue>>(emptyList()) }
    var loadingInventory by remember { mutableStateOf(!isEdit) }
    var inventoryError by remember { mutableStateOf<String?>(null) }
    var selectedProperty by remember { mutableStateOf<JSONValue?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    val initialPrice = contract?.let { amountText(it["rentalPrice"].double) }.orEmpty()
    val initialDeposit = contract?.let { amountText(it["rentalDeposit"].double) }.orEmpty()
    val initialHolding = contract?.let { amountText(it["holdingDeposit"].double) }.orEmpty()
    val initialDuration = contract?.get("contractDuration")?.string ?: tr("1 năm")
    val initialName = contract?.get("customerName")?.string.orEmpty()
    val initialPhone = contract?.get("customerPhone")?.string.orEmpty()
    val initialStatus = contract?.get("status")?.string?.ifEmpty { null } ?: "draft"
    val initialStart = contract?.get("contractStartDate")?.string?.let { isoToLocalDate(it) } ?: if (isEdit) null else LocalDate.now()
    val initialEnd = contract?.get("contractEndDate")?.string?.let { isoToLocalDate(it) } ?: if (isEdit) null else LocalDate.now().plusYears(1)

    var price by remember { mutableStateOf(initialPrice) }
    var deposit by remember { mutableStateOf(initialDeposit) }
    var holding by remember { mutableStateOf(initialHolding) }
    var duration by remember { mutableStateOf(initialDuration) }
    var customerName by remember { mutableStateOf(initialName) }
    var customerPhone by remember { mutableStateOf(initialPhone) }
    var status by remember { mutableStateOf(initialStatus) }
    var startDate by remember { mutableStateOf(initialStart) }
    var endDate by remember { mutableStateOf(initialEnd) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showStatusSheet by remember { mutableStateOf(false) }

    fun applyPropertyPrice(apt: JSONValue) {
        val p = apt["sellPrice"].double.takeIf { it > 0 } ?: apt["price"].double
        if (p > 0) price = amountText(p)
    }

    if (!isEdit) {
        LaunchedEffect(Unit) {
            try {
                inventory = APIClient.get().request("/sales/inventory", query = mapOf("limit" to "200"))["data"].array
                inventoryError = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                inventoryError = e.message ?: tr("Không tải được danh sách căn hộ")
            } finally {
                loadingInventory = false
            }
        }
    }

    val dirty = selectedProperty != null || price != initialPrice || deposit != initialDeposit || holding != initialHolding ||
        duration != initialDuration || customerName != initialName || customerPhone != initialPhone ||
        status != initialStatus || startDate != initialStart || endDate != initialEnd

    fun requestBack() { if (dirty && !saving) showDiscard = true else onBack() }

    fun pickDate(current: LocalDate?, onPicked: (LocalDate) -> Unit) {
        val d = current ?: LocalDate.now()
        DatePickerDialog(context, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (!isEdit && selectedProperty == null) e["property"] = tr("Vui lòng chọn căn hộ")
        if (digitsOnly(price).isEmpty()) e["price"] = tr("Vui lòng nhập giá thuê / giá bán")
        if (duration.isBlank()) e["duration"] = tr("Thời hạn hợp đồng không được để trống")
        if (customerName.isBlank()) e["name"] = tr("Tên khách hàng không được để trống")
        if (customerPhone.isBlank()) e["phone"] = tr("SĐT khách hàng không được để trống")
        val s = startDate
        val en = endDate
        if (s != null && en != null && en.isBefore(s)) e["end"] = tr("Ngày kết thúc phải sau ngày bắt đầu")
        errors = e
        if (e.isNotEmpty()) ToastCenter.show(e.values.first(), isError = true)
        return e.isEmpty()
    }

    fun submit() {
        if (saving || !validate()) return
        scope.launch {
            saving = true
            try {
                val body = buildJsonObject {
                    val apt = selectedProperty
                    if (apt != null) {
                        put("propertyId", apt.id)
                        // The create schema names the code `propertyCode` (apartmentCode is stripped).
                        put("propertyCode", apt["propertyCode"].string.ifEmpty { apt["title"].string })
                    }
                    put("rentalPrice", (digitsOnly(price).toDoubleOrNull() ?: 0.0))
                    put("rentalDeposit", (digitsOnly(deposit).toDoubleOrNull() ?: 0.0))
                    put("holdingDeposit", (digitsOnly(holding).toDoubleOrNull() ?: 0.0))
                    put("contractDuration", duration.trim())
                    put("customerName", customerName.trim())
                    put("customerPhone", customerPhone.trim())
                    if (!isEdit) put("status", status)
                    val start = startDate
                    val end = endDate
                    if (start != null) put("contractStartDate", localDateToIso(start)) else if (isEdit) put("contractStartDate", JsonNull)
                    if (end != null) put("contractEndDate", localDateToIso(end)) else if (isEdit) put("contractEndDate", JsonNull)
                }
                val res = if (contract != null) {
                    APIClient.get().request("/contracts/${contractEnc(contract.id)}", method = "PUT", bodyJson = body.toString())
                } else {
                    APIClient.get().request("/contracts", method = "POST", bodyJson = body.toString())
                }
                ToastCenter.show(tr(if (isEdit) "Đã cập nhật hợp đồng" else "Đã tạo hợp đồng mới thành công"))
                onSaved(res["data"])
            } catch (e: Exception) {
                ToastCenter.show(e.message ?: tr("Không lưu được"), isError = true)
            } finally {
                saving = false
            }
        }
    }

    CrmFullScreenPage(
        title = if (isEdit) tr("Cập nhật hợp đồng") else tr("Tạo hợp đồng mới"),
        onBack = ::requestBack,
        bottomBar = {
            CrmFormActionBar(
                submitText = if (isEdit) "Lưu" else "Tạo hợp đồng",
                submitting = saving,
                onCancel = ::requestBack,
                onSubmit = ::submit
            )
        }
    ) {
        if (contract != null) {
            AdvisorSection(header = "Căn hộ") {
                AdvisorLabeledRow("Mã căn hộ", contractCode(contract))
            }
        } else {
            AdvisorSection(header = "Chọn căn hộ từ giỏ hàng thực tế *") {
                when {
                    loadingInventory -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = FutaColors.BrandGreen, modifier = Modifier.size(16.dp))
                        Text("Đang tải danh sách căn hộ...", fontSize = 12.5.sp, color = FutaColors.Slate, modifier = Modifier.padding(start = 8.dp))
                    }
                    inventoryError != null -> Text(inventoryError ?: "", fontSize = 12.5.sp, color = Color(0xFFDC2626))
                    inventory.isEmpty() -> Text("Không có căn hộ nào sẵn sàng", fontSize = 12.5.sp, color = FutaColors.Slate)
                    else -> {
                        val apt = selectedProperty
                        FutaSelectField(
                            displayValue = apt?.let { "${it["propertyCode"].string.ifEmpty { it["title"].string.translated("property") }} - ${it["projectName"].string.translated("project")}" }.orEmpty(),
                            placeholder = tr("Vui lòng chọn căn hộ"),
                            onClick = { showPicker = true }
                        )
                    }
                }
                errors["property"]?.let { Text(it, fontSize = 11.5.sp, color = Color(0xFFDC2626)) }
            }
        }

        AdvisorSection(header = "Chi phí & Thời hạn") {
            CrmTextField("Giá thuê / Giá bán (VNĐ)", price, { price = groupThousands(it); errors = errors - "price" }, "0", required = true, error = errors["price"], keyboardType = KeyboardType.Number)
            CrmTextField("Tiền đặt cọc", deposit, { deposit = groupThousands(it) }, "0", keyboardType = KeyboardType.Number)
            CrmTextField("Tiền giữ chỗ", holding, { holding = groupThousands(it) }, "0", keyboardType = KeyboardType.Number)
            CrmTextField("Thời hạn hợp đồng", duration, { duration = it; errors = errors - "duration" }, "ví dụ: 12 tháng", required = true, error = errors["duration"])
        }

        AdvisorSection(header = "Khách hàng") {
            CrmTextField("Họ và tên khách hàng", customerName, { customerName = it; errors = errors - "name" }, "", required = true, error = errors["name"])
            CrmTextField("Số điện thoại", customerPhone, { customerPhone = it; errors = errors - "phone" }, "", required = true, error = errors["phone"], keyboardType = KeyboardType.Phone)
        }

        AdvisorSection(header = "Thời gian & Trạng thái") {
            CrmSelectRow("Ngày bắt đầu", startDate?.toString().orEmpty(), placeholder = "Chọn ngày") { pickDate(startDate) { startDate = it } }
            CrmSelectRow("Ngày kết thúc", endDate?.toString().orEmpty(), placeholder = "Chọn ngày") { pickDate(endDate ?: startDate) { endDate = it; errors = errors - "end" } }
            errors["end"]?.let { Text(it, fontSize = 11.5.sp, color = Color(0xFFDC2626)) }
            if (!isEdit) {
                CrmSelectRow("Trạng thái", tr(contractStatusLabel(status))) { showStatusSheet = true }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showPicker) {
        var query by remember { mutableStateOf("") }
        val filtered = inventory.filter {
            query.isBlank() || it["propertyCode"].string.contains(query, true) || it["title"].string.contains(query, true) ||
                it["projectName"].string.contains(query, true)
        }
        FutaBottomSheet(visible = true, onDismiss = { showPicker = false }, title = "Chọn căn hộ") {
            FutaInput(value = query, onValueChange = { query = it }, placeholder = "Tìm mã căn hộ...", leadingIcon = Icons.Default.Search, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text("Không tìm thấy căn hộ phù hợp", fontSize = 13.sp, color = FutaColors.Slate, modifier = Modifier.padding(vertical = 16.dp))
            }
            filtered.take(100).forEach { apt ->
                val sel = selectedProperty?.id == apt.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedProperty = apt
                            applyPropertyPrice(apt)
                            errors = errors - "property"
                            showPicker = false
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        VerbatimText(apt["propertyCode"].string.ifEmpty { apt["title"].string.translated("property") }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (sel) FutaColors.BrandGreen else FutaColors.Navy)
                        VerbatimText(apt["projectName"].string.translated("project"), fontSize = 12.sp, color = FutaColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val p = apt["sellPrice"].double.takeIf { it > 0 } ?: apt["price"].double
                    if (p > 0) Text(LocalizedPrice.compact(p), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                    if (sel) Icon(Icons.Default.Check, null, tint = FutaColors.BrandGreen, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
            }
        }
    }

    CrmOptionSheet(
        visible = showStatusSheet,
        title = "Trạng thái",
        options = listOf("draft", "deposited", "signed", "active"),
        selected = status,
        label = { contractStatusLabel(it) },
        onSelect = { status = it },
        onDismiss = { showStatusSheet = false }
    )
    CrmDiscardDialog(visible = showDiscard, onDiscard = onBack, onDismiss = { showDiscard = false })
}
