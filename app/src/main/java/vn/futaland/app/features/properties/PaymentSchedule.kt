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

    fun parsePolicies(property: JSONValue): List<PaymentSchedulePolicy> {
        // Priority 1: erpPriceTable.paymentPlans (amounts pre-computed by ERP)
        val priceTablePlans = property["erpPriceTable"]["paymentPlans"].array
        if (priceTablePlans.isNotEmpty()) {
            return priceTablePlans.mapIndexed { index, plan ->
                val id = plan["policyId"].string.ifEmpty { plan["id"].string.ifEmpty { "plan-$index" } }
                val code = plan["policyCode"].string.ifEmpty { plan["code"].string }
                val name = plan["policyName"].string.ifEmpty { plan["name"].string.ifEmpty { "Phương thức ${index + 1}" } }
                val totalAmount = if (!plan["totalAmount"].isNull) plan["totalAmount"].double.roundToLong() else 0L
                val isBalanced = if (!plan["balanced"].isNull) plan["balanced"].bool else true
                PaymentSchedulePolicy(
                    id = id,
                    code = code,
                    name = name,
                    discountPercent = 0.0,
                    depositAmount = 0L,
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
                PaymentSchedulePolicy(
                    id = id,
                    code = code,
                    name = name,
                    discountPercent = 0.0,
                    depositAmount = 0L,
                    rawInstallments = plan["installments"].array,
                    isBalanced = true,
                    defaultAmount = 0L
                )
            }
        }

        return emptyList()
    }

    fun resolveDefaultBasePrice(property: JSONValue, policies: List<PaymentSchedulePolicy>): Long {
        policies.firstOrNull()?.defaultAmount?.let {
            if (it > 0L) return it
        }

        val basisPrice = property["erpPriceTable"]["basis"]["listPrice"].double
        if (basisPrice > 0) return basisPrice.roundToLong()

        val sellPrice = property["sellPrice"].double
        if (sellPrice > 0) return sellPrice.roundToLong()

        val price = property["price"].double
        if (price > 0) return price.roundToLong()

        return 0L
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
}
