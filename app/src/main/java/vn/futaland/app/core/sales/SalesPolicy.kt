package vn.futaland.app.core.sales

/**
 * Selling rules shared by every screen that can register to sell or hold a unit.
 *
 * [VERSION] must match `SALES_POLICY_VERSION` in the backend
 * (bds-backend/src/constants/sales-policy.ts); the server rejects registrations
 * that quote an outdated policy version.
 */
object SalesPolicy {
    const val VERSION = "2026.08.26"

    /** What the UI may offer the current viewer for a given unit. */
    enum class SellingAction { HOLD, AWAITING_APPROVAL, REGISTER, OUT_OF_SLOTS, UNAVAILABLE }

    /**
     * Holding is only possible on an approved "đăng ký bán" registration, so an
     * advisor who has not been approved must register first instead of holding.
     */
    fun sellingAction(
        isAuthenticated: Boolean,
        isAdvisor: Boolean,
        hasActiveSellingRights: Boolean,
        hasPendingRegistration: Boolean,
        remainingSlots: Int
    ): SellingAction {
        if (!isAuthenticated || !isAdvisor) return SellingAction.UNAVAILABLE
        if (hasActiveSellingRights) return SellingAction.HOLD
        if (hasPendingRegistration) return SellingAction.AWAITING_APPROVAL
        if (remainingSlots > 0) return SellingAction.REGISTER
        return SellingAction.OUT_OF_SLOTS
    }
}
