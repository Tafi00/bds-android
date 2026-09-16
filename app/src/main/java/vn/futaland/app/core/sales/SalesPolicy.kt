package vn.futaland.app.core.sales

import vn.futaland.app.core.network.JSONValue

enum class ProductContext(val wire: String) {
    CUSTOMER("customer"), ADVISOR("advisor"), ADMIN("admin");
    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.wire == value } ?: CUSTOMER
    }
}

/** Presentation only: all business decisions are returned by the server. */
object SalesPolicy {
    const val VERSION = "2026.08.26"
    enum class SellingAction { HOLD, REGISTER, STATUS, UNAVAILABLE }
    fun sellingAction(context: ProductContext, access: JSONValue?): SellingAction {
        if (context != ProductContext.ADVISOR || access?.get("context")?.string != "advisor") return SellingAction.UNAVAILABLE
        if (access["canHold"].bool) return SellingAction.HOLD
        if (access["canRegister"].bool) return SellingAction.REGISTER
        return SellingAction.STATUS
    }
    fun registrationLabel(state: String) = when (state) {
        "not_registered" -> "Chưa đăng ký"
        "pending" -> "Chờ duyệt"
        "active" -> "Đã được cấp quyền bán"
        "full" -> "Hết suất"
        "expired" -> "Hết hạn"
        "revoked" -> "Đã thu hồi"
        "rejected" -> "Bị từ chối"
        else -> "Không thể đăng ký"
    }
}
