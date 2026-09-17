package vn.futaland.app.features.properties

import vn.futaland.app.core.network.JSONValue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

data class ScheduleRow(
    val id: String = "",
    val order: Int,
    val name: String,
    val timing: String,
    val percent: Double?,
    val amount: Long,
    val cumulative: Long,
    val note: String = "",
    val includesDeposit: Boolean = false
)

data class PaymentSchedulePolicy(
    val id: String,
    val code: String,
    val name: String,
    val discountPercent: Double = 0.0,
    val depositAmount: Long = 0L,
    val rawInstallments: List<JSONValue> = emptyList(),
    val isBalanced: Boolean = true,
    val defaultAmount: Long = 0L
)

data class PaymentScheduleResult(
    val basePrice: Long,
    val discountPercent: Double,
    val discountAmount: Long,
    val netPrice: Long,
    val depositAmount: Long,
    val totalPercent: Double,
    val isBalanced: Boolean,
    val rows: List<ScheduleRow>
)

object PaymentScheduleEngine {

    fun formatErpTiming(timing: JSONValue): String {
        if (timing.isNull) return ""
        val anchor = timing["anchorLabel"].string.trim().ifEmpty { timing["anchor"].string.trim() }
        val days = if (!timing["days"].isNull) timing["days"].int else null
        return when {
            days == null -> anchor
            days == 0 -> anchor.ifEmpty { "Cùng ngày mốc thanh toán" }
            else -> "$days ngày sau ${anchor.ifEmpty { "mốc thanh toán" }}"
        }
    }

    private fun normalizeToHundred(percents: List<Double>): List<Double> {
        if (percents.isEmpty()) return emptyList()
        val total = percents.sum()
        if (total <= 0.0) return percents.map { 0.0 }
        val scaled = percents.map { (it / total) * 100.0 }.toMutableList()
        val drift = 100.0 - scaled.sum()
        scaled[scaled.size - 1] += drift
        return scaled
    }

    private fun deriveBalancedPercents(installments: List<JSONValue>): List<Double> {
        val amounts = installments.map { if (!it["amount"].isNull) it["amount"].double else 0.0 }
        val totalAmount = amounts.filter { it > 0.0 }.sum()
        if (totalAmount > 0.0) {
            return normalizeToHundred(amounts.map { (it / totalAmount) * 100.0 })
        }
        val percents = installments.map { if (!it["percent"].isNull) it["percent"].double else 0.0 }
        return normalizeToHundred(percents)
    }

    fun parsePolicies(property: JSONValue, allowFallback: Boolean = true): List<PaymentSchedulePolicy> {
        // Priority 0: property.paymentPolicies (Direct admin configured policies)
        val directPolicies = property["paymentPolicies"].array
        if (directPolicies.isNotEmpty()) {
            return directPolicies.mapIndexed { index, plan ->
                val id = plan["id"].string.ifEmpty { plan["policyId"].string.ifEmpty { "policy-$index" } }
                val code = plan["code"].string.ifEmpty { plan["policyCode"].string }
                val name = plan["name"].string.ifEmpty { plan["policyName"].string.ifEmpty { "Chính sách ${index + 1}" } }
                val discount = if (!plan["discountPercent"].isNull) plan["discountPercent"].double else 0.0
                val deposit = if (!plan["depositAmount"].isNull) plan["depositAmount"].double.roundToLong() else 100_000_000L
                PaymentSchedulePolicy(
                    id = id,
                    code = code,
                    name = name,
                    discountPercent = discount,
                    depositAmount = deposit,
                    rawInstallments = plan["installments"].array,
                    isBalanced = true,
                    defaultAmount = 0L
                )
            }
        }

        // Priority 1: erpPriceTable.paymentPlans (amounts pre-computed by ERP)
        val priceTablePlans = property["erpPriceTable"]["paymentPlans"].array
        if (priceTablePlans.isNotEmpty()) {
            return priceTablePlans.mapIndexed { index, plan ->
                val id = plan["policyId"].string.ifEmpty { plan["id"].string.ifEmpty { "plan-$index" } }
                val code = plan["policyCode"].string.ifEmpty { plan["code"].string }
                val name = plan["policyName"].string.ifEmpty { plan["name"].string.ifEmpty { "Phương thức ${index + 1}" } }
                val totalAmount = if (!plan["totalAmount"].isNull) plan["totalAmount"].double.roundToLong() else 0L
                val isBalanced = if (!plan["balanced"].isNull) plan["balanced"].bool else true
                val discount = if (!plan["discountPercent"].isNull) plan["discountPercent"].double else 0.0
                val deposit = if (!plan["depositAmount"].isNull) plan["depositAmount"].double.roundToLong() else 0L
                PaymentSchedulePolicy(
                    id = id,
                    code = code,
                    name = name,
                    discountPercent = discount,
                    depositAmount = deposit,
                    rawInstallments = plan["installments"].array,
                    isBalanced = isBalanced,
                    defaultAmount = totalAmount
                )
            }
        }

        // Priority 2: erpPaymentMethods.paymentPolicies (percent only)
        val methodPolicies = property["erpPaymentMethods"]["paymentPolicies"].array
        if (methodPolicies.isNotEmpty()) {
            return methodPolicies.mapIndexed { index, plan ->
                val id = plan["id"].string.ifEmpty { plan["policyId"].string.ifEmpty { "method-policy-$index" } }
                val code = plan["code"].string.ifEmpty { plan["policyCode"].string }
                val name = plan["name"].string.ifEmpty { plan["policyName"].string.ifEmpty { "Chính sách ${index + 1}" } }
                val discount = if (!plan["discountPercent"].isNull) plan["discountPercent"].double else 0.0
                val deposit = if (!plan["depositAmount"].isNull) plan["depositAmount"].double.roundToLong() else 0L
                PaymentSchedulePolicy(
                    id = id,
                    code = code,
                    name = name,
                    discountPercent = discount,
                    depositAmount = deposit,
                    rawInstallments = plan["installments"].array,
                    isBalanced = true,
                    defaultAmount = 0L
                )
            }
        }

        if (!allowFallback) {
            return emptyList()
        }

        // Priority 3: Standard Fallback Policies (Matching Web & Screenshot)
        return createStandardPolicies()
    }

    fun createStandardPolicies(): List<PaymentSchedulePolicy> {
        val standardJson = """
        [
            { "name": "Đặt cọc / Ký thỏa thuận đặt cọc (TTĐC)", "timing": { "days": 7, "anchorLabel": "kể từ ngày ký hợp đồng cọc" }, "percent": 15.0, "note": "Bao gồm 100.000.000 đ tiền đặt cọc (ký TTĐC/HĐC)" },
            { "name": "Ký HĐMB / Hoàn thành móng cọc", "timing": { "days": 30, "anchorLabel": "kể từ ngày hoàn tất đợt 1" }, "percent": 15.0, "note": "Chuyển sang Hợp đồng Mua bán chính thức" },
            { "name": "Xây dựng kết cấu đến tầng 15", "timing": { "days": 60, "anchorLabel": "kể từ ngày thanh toán đợt trước" }, "percent": 10.0, "note": "Biên bản nghiệm thu phần thô tầng 15" },
            { "name": "Cất nóc tòa nhà (Tầng cao nhất)", "timing": { "days": 90, "anchorLabel": "kể từ ngày thanh toán đợt trước" }, "percent": 15.0, "note": "Thông báo hoàn tất cất nóc từ Tổng thầu" },
            { "name": "Nhận thông báo bàn giao căn hộ", "timing": { "days": 15, "anchorLabel": "kể từ ngày nhận thông báo bàn giao" }, "percent": 40.0, "note": "+ 2% Kinh phí bảo trì (KPBT) theo luật" },
            { "name": "Nghiệm thu chính chủng QSDĐ (Sổ hồng)", "timing": { "anchorLabel": "Kể từ ngày nhận thông báo bàn giao GCN chính thức" }, "percent": 5.0, "note": "Quyết toán 100% hợp đồng" }
        ]
        """
        val fastJson = """
        [
            { "name": "Đặt cọc / Ký thỏa thuận đặt cọc (TTĐC)", "timing": { "days": 7, "anchorLabel": "kể từ ngày ký hợp đồng cọc" }, "percent": 25.0, "note": "Bao gồm 100.000.000 đ tiền đặt cọc (ký TTĐC/HĐC)" },
            { "name": "Ký HĐMB / Thanh toán sớm 70%", "timing": { "days": 15, "anchorLabel": "kể từ ngày ký TTĐC" }, "percent": 70.0, "note": "Chiết khấu 2% trực tiếp vào giá niêm yết" },
            { "name": "Nghiệm thu chính chủng QSDĐ (Sổ hồng)", "timing": { "anchorLabel": "Kể từ ngày nhận thông báo bàn giao GCN chính thức" }, "percent": 5.0, "note": "Quyết toán 100% hợp đồng" }
        ]
        """
        val loanJson = """
        [
            { "name": "Vốn tự có: Đặt cọc / Ký TTĐC", "timing": { "days": 7, "anchorLabel": "kể từ ngày ký hợp đồng cọc" }, "percent": 15.0, "note": "Bao gồm 100.000.000 đ tiền đặt cọc (ký TTĐC/HĐC)" },
            { "name": "Ngân hàng giải ngân đợt 1 (Hỗ trợ LS 0%)", "timing": { "days": 30, "anchorLabel": "kể từ ngày hoàn tất đợt 1" }, "percent": 55.0, "note": "Ân hạn nợ gốc và hỗ trợ lãi suất 0%" },
            { "name": "Nhận thông báo bàn giao căn hộ", "timing": { "days": 15, "anchorLabel": "kể từ ngày nhận thông báo bàn giao" }, "percent": 25.0, "note": "+ 2% Kinh phí bảo trì (KPBT) theo luật" },
            { "name": "Nghiệm thu chính chủng QSDĐ (Sổ hồng)", "timing": { "anchorLabel": "Kể từ ngày nhận thông báo bàn giao GCN chính thức" }, "percent": 5.0, "note": "Quyết toán 100% hợp đồng" }
        ]
        """
        return listOf(
            PaymentSchedulePolicy(
                id = "standard-policy",
                code = "CSTT-CHUAN",
                name = "Chính sách thanh toán chuẩn",
                discountPercent = 0.5,
                depositAmount = 100_000_000L,
                rawInstallments = JSONValue.parse(standardJson).array,
                isBalanced = true,
                defaultAmount = 0L
            ),
            PaymentSchedulePolicy(
                id = "fast-policy",
                code = "CSTT-NHANH",
                name = "Chính sách thanh toán nhanh",
                discountPercent = 2.0,
                depositAmount = 100_000_000L,
                rawInstallments = JSONValue.parse(fastJson).array,
                isBalanced = true,
                defaultAmount = 0L
            ),
            PaymentSchedulePolicy(
                id = "loan-policy",
                code = "CSTT-VAY",
                name = "Chính sách thanh toán vay",
                discountPercent = 0.0,
                depositAmount = 100_000_000L,
                rawInstallments = JSONValue.parse(loanJson).array,
                isBalanced = true,
                defaultAmount = 0L
            )
        )
    }

    fun resolveUnitCode(property: JSONValue): String {
        val code = property["propertyCode"].string.trim()
        if (code.isNotEmpty()) return code
        val altCode = property["code"].string.trim()
        if (altCode.isNotEmpty()) return altCode
        val title = property["title"].string.trim()
        return title.ifEmpty { "—" }
    }

    fun resolveDefaultBasePrice(property: JSONValue, policies: List<PaymentSchedulePolicy>): Long {
        val basisPrice = property["erpPriceTable"]["basis"]["listPrice"].double
        if (basisPrice > 0) return basisPrice.roundToLong()

        val sellPrice = property["sellPrice"].double
        if (sellPrice > 0) return sellPrice.roundToLong()

        val price = property["price"].double
        if (price > 0) return price.roundToLong()

        policies.firstOrNull()?.defaultAmount?.let {
            if (it > 0L) return it
        }

        return 7_904_534_578L // Default demo price matching screenshot if no price found
    }

    fun calculate(policy: PaymentSchedulePolicy, basePrice: Long): PaymentScheduleResult {
        val safeBase = max(0L, basePrice)
        val discountPercent = max(0.0, policy.discountPercent)
        val discountAmount = ((safeBase * discountPercent) / 100.0).roundToLong()
        val netPrice = max(0L, safeBase - discountAmount)
        val depositAmount = max(0L, policy.depositAmount)

        val installments = policy.rawInstallments
        if (installments.isEmpty()) {
            return PaymentScheduleResult(
                basePrice = safeBase,
                discountPercent = discountPercent,
                discountAmount = discountAmount,
                netPrice = netPrice,
                depositAmount = depositAmount,
                totalPercent = 0.0,
                isBalanced = true,
                rows = emptyList()
            )
        }

        val hasAllErpAmounts = installments.all { !it["amount"].isNull && it["amount"].double > 0 }
        val sumErpAmount = installments.sumOf { it["amount"].double.roundToLong() }
        val matchesErpTotal = hasAllErpAmounts && discountPercent == 0.0 && (safeBase == sumErpAmount || safeBase == 0L)

        val percents = deriveBalancedPercents(installments)
        val totalPercent = percents.sum()
        val isBalanced = policy.isBalanced && abs(totalPercent - 100.0) < 0.01

        val rows = mutableListOf<ScheduleRow>()
        var cumulative = 0L

        installments.forEachIndexed { index, item ->
            val isLast = index == installments.size - 1
            val name = item["name"].string.trim().ifEmpty { "Đợt ${index + 1}" }
            val timing = formatErpTiming(item["timing"])
            val percent = percents.getOrNull(index) ?: 0.0

            val amount: Long = if (matchesErpTotal) {
                item["amount"].double.roundToLong()
            } else {
                if (isLast && isBalanced) {
                    max(0L, netPrice - cumulative)
                } else {
                    ((netPrice * percent) / 100.0).roundToLong()
                }
            }

            cumulative += amount
            rows.add(
                ScheduleRow(
                    id = "row-$index",
                    order = index + 1,
                    name = name,
                    timing = timing,
                    percent = percent,
                    amount = amount,
                    cumulative = cumulative,
                    note = item["base"].string.let { if (it.isNotEmpty() && it != "contractPrice") "Gốc: $it" else "" },
                    includesDeposit = index == 0 && depositAmount > 0
                )
            )
        }

        return PaymentScheduleResult(
            basePrice = safeBase,
            discountPercent = discountPercent,
            discountAmount = discountAmount,
            netPrice = netPrice,
            depositAmount = depositAmount,
            totalPercent = totalPercent,
            isBalanced = isBalanced,
            rows = rows
        )
    }

    fun formatVnd(amount: Long): String {
        return String.format(Locale.US, "%,d", amount) + " đ"
    }

    fun formatNumberOnly(amount: Long): String {
        return String.format(Locale.US, "%,d", amount)
    }

    fun exportSummaryText(
        policy: PaymentSchedulePolicy,
        result: PaymentScheduleResult,
        unitLabel: String
    ): String {
        val builder = StringBuilder()
        builder.append("BẢNG TÍNH MINH HỌA GIÁ TRỊ THANH TOÁN THEO ĐỢT\n")
        builder.append("Mã căn: $unitLabel\n")
        builder.append("Chính sách: ${policy.name}\n")
        builder.append("----------------------------------------\n")
        builder.append("Tổng giá niêm yết: ${formatVnd(result.basePrice)}\n")
        if (result.discountPercent > 0.0) {
            builder.append("Tổng chiết khấu (%.1f%%): - ${formatVnd(result.discountAmount)}\n".format(result.discountPercent))
        }
        if (result.depositAmount > 0L) {
            builder.append("Tiền đặt cọc quy định: ${formatVnd(result.depositAmount)}\n")
        }
        builder.append("GIÁ THANH TOÁN THỰC TẾ: ${formatVnd(result.netPrice)}\n")
        builder.append("----------------------------------------\n")
        builder.append("CHI TIẾT TỪNG ĐỢT:\n")
        for (row in result.rows) {
            val pct = row.percent?.let { " (%.1f%%)".format(it).replace(".0%", "%") } ?: ""
            builder.append("Đợt ${row.order}: ${row.name}$pct\n")
            if (row.timing.isNotEmpty()) {
                builder.append("  - Mốc: ${row.timing}\n")
            }
            builder.append("  - Số tiền: ${formatVnd(row.amount)}\n")
            builder.append("  - Lũy kế: ${formatVnd(row.cumulative)}\n")
            if (row.note.isNotEmpty()) {
                builder.append("  - Ghi chú: ${row.note}\n")
            }
        }
        builder.append("----------------------------------------\n")
        builder.append("* Dữ liệu tham khảo, sẽ điều chỉnh theo điều kiện thực tế bàn giao.")
        return builder.toString()
    }
}
