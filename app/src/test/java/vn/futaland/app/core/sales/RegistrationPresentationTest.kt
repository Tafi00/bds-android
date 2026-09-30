package vn.futaland.app.core.sales

import org.junit.Assert.*
import org.junit.Test
import vn.futaland.app.core.network.JSONValue

class RegistrationPresentationTest {
    private fun row(json: String) = JSONValue.parse(json)

    @Test fun `approval means selling not sold`() {
        assertEquals("active", RegistrationPresentation.category(row("""{"status":"active","bookingStatus":"none","property":{"status":["Đang mở bán"]}}""")))
        assertEquals("active", RegistrationPresentation.category(row("""{"status":"approved"}""")))
    }

    @Test fun `completed and deposited transactions leave the selling tab`() {
        for (booking in listOf("purchased", "commission_pending", "commission_paid")) {
            assertEquals("sold", RegistrationPresentation.category(row("""{"status":"active","bookingStatus":"$booking"}""")))
        }
        for (booking in listOf("deposited", "deposit_contract")) {
            assertEquals("deposited", RegistrationPresentation.category(row("""{"status":"active","bookingStatus":"$booking"}""")))
        }
        assertEquals("deposited", RegistrationPresentation.category(row("""{"status":"active","erpHoldStatus":"waiting_hdmb"}""")))
    }

    @Test fun `revoked and unpublished units disappear even with selling rights`() {
        assertFalse(RegistrationPresentation.isVisible(row("""{"status":"revoked","property":{"status":["Đang mở bán"]}}""")))
        assertFalse(RegistrationPresentation.isVisible(row("""{"status":"active","property":{"status":["Chưa mở bán"]}}""")))
        assertFalse(RegistrationPresentation.isVisible(row("""{"status":"expired","property":{"source":"ERP","externalOpenForSale":false,"status":["Đang mở bán"]}}""")))
        assertTrue(RegistrationPresentation.isVisible(row("""{"status":"active","bookingStatus":"purchased","property":{"status":["Đã bán","Chưa mở bán"]}}""")))
    }

    @Test fun `registration is disabled until quota loads and when limit is reached`() {
        assertFalse(RegistrationPresentation.canRegister(JSONValue.Null))
        assertFalse(RegistrationPresentation.canRegister(row("""{"maxProductsPerAdvisor":3,"advisorRegisteredCount":3,"advisorLimitReached":true}""")))
        assertTrue(RegistrationPresentation.canRegister(row("""{"maxProductsPerAdvisor":3,"advisorRegisteredCount":2,"advisorLimitReached":false}""")))
    }
}
