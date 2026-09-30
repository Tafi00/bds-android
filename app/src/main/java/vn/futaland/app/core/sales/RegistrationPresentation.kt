package vn.futaland.app.core.sales

import vn.futaland.app.core.network.JSONValue
import java.text.Normalizer
import java.util.Locale

/** A selling right is separate from the unit's deposit/sale transaction. */
object RegistrationPresentation {
    private fun fold(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd').replace('Đ', 'D')
        .trim().lowercase(Locale.ROOT)

    fun category(item: JSONValue): String {
        val status = item["registrationStatus"].string.ifEmpty { item["status"].string }.lowercase(Locale.ROOT)
        if (item["isAvailableProduct"].bool || status == "available") return "available"
        val booking = item["bookingStatus"].string
        val property = item["property"]
        val statuses = property["status"].array.map { it.string }
        val holding = item["activeHoldingStatus"].string.ifEmpty { property["activeHoldingStatus"].string }
        val erp = fold(item["erpHoldStatus"].string)
        val label = fold(item["erpHoldStatusLabel"].string)
        val waitingHdmb = erp in setOf("waiting_hdmb", "pending_hdmb", "cho_duyet_hdmb", "hdmb_pending", "contract_pending", "waiting_contract")
            || (label.contains("hdmb") && listOf("cho", "dang trinh", "pending").any { label.contains(it) })
        val completedHdmb = !waitingHdmb && (erp in setOf("hdmb", "hoan_tat_hdmb", "da_ra_hdmb", "hdmb_completed", "contracted", "completed", "contract_signed", "signed", "duyet_hdmb", "da_duyet_hdmb", "hdmb_approved", "approved_hdmb", "contract")
            || label == "hdmb" || listOf("hoan tat hdmb", "da ra hdmb", "da duyet hdmb", "duyet hdmb", "hoan tat hop dong mua ban", "da duyet hop dong mua ban").any { label.contains(it) })
        if (booking in setOf("purchased", "commission_pending", "commission_paid") || holding == "sold" || completedHdmb
            || statuses.any { it in setOf("Đã bán", "HĐMB", "Đã có người mua") }) return "sold"
        if (booking in setOf("deposited", "deposit_contract") || holding == "deposited" || item["depositPaidAt"].string.isNotEmpty()
            || waitingHdmb || erp in setOf("deposited", "deposit_collected", "da_thu_coc", "deposit_paid", "deposit_received", "deposit_success")
            || statuses.any { it in setOf("Đã cọc", "Đã thu cọc", "Hợp đồng cọc") }) return "deposited"
        return if (status == "approved") "active" else status
    }

    fun isVisible(item: JSONValue): Boolean {
        if (item["status"].string == "revoked" || item["registrationStatus"].string == "revoked") return false
        val property = item["property"]
        if (property["externalRemovedAt"].string.isNotEmpty()) return false
        if (category(item) in setOf("sold", "deposited")) return true
        if (property["status"].array.any { it.string in setOf("Chưa mở bán", "Ngừng bán") }) return false
        return property["source"].string != "ERP" || property["externalOpenForSale"].bool
    }

    fun canRegister(quota: JSONValue): Boolean = !quota.isNull && !quota["advisorLimitReached"].bool
}
