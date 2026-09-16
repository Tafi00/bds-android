package vn.futaland.app.core.sales

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.futaland.app.core.network.JSONValue

/**
 * Regression coverage for the shared product-flow rule on Android.
 * Business state is decided by the server; these tests lock the client
 * presentation contract so a button can only appear in its own context.
 */
class SalesPolicyTest {

    private fun access(json: String) = JSONValue.parse(json)

    @Test
    fun `unknown context defaults to customer`() {
        assertEquals(ProductContext.CUSTOMER, ProductContext.parse(null))
        assertEquals(ProductContext.CUSTOMER, ProductContext.parse(""))
        assertEquals(ProductContext.CUSTOMER, ProductContext.parse("guest"))
        assertEquals(ProductContext.CUSTOMER, ProductContext.parse("CUSTOMER"))
        assertEquals(ProductContext.ADVISOR, ProductContext.parse("advisor"))
        assertEquals(ProductContext.ADMIN, ProductContext.parse("admin"))
    }

    @Test
    fun `customer and admin never receive advisor selling actions`() {
        val advisorAccess = access("""{"context":"advisor","canHold":true,"canRegister":true}""")
        assertEquals(SalesPolicy.SellingAction.UNAVAILABLE, SalesPolicy.sellingAction(ProductContext.CUSTOMER, advisorAccess))
        assertEquals(SalesPolicy.SellingAction.UNAVAILABLE, SalesPolicy.sellingAction(ProductContext.ADMIN, advisorAccess))
    }

    @Test
    fun `advisor context without server approval stays unavailable`() {
        assertEquals(SalesPolicy.SellingAction.UNAVAILABLE, SalesPolicy.sellingAction(ProductContext.ADVISOR, null))
        assertEquals(SalesPolicy.SellingAction.UNAVAILABLE, SalesPolicy.sellingAction(ProductContext.ADVISOR, JSONValue.Null))
        assertEquals(
            SalesPolicy.SellingAction.UNAVAILABLE,
            SalesPolicy.sellingAction(ProductContext.ADVISOR, access("""{"context":"customer","canRegister":true}""")),
        )
    }

    @Test
    fun `selling action follows the server decision`() {
        assertEquals(
            SalesPolicy.SellingAction.HOLD,
            SalesPolicy.sellingAction(ProductContext.ADVISOR, access("""{"context":"advisor","canHold":true}""")),
        )
        assertEquals(
            SalesPolicy.SellingAction.REGISTER,
            SalesPolicy.sellingAction(ProductContext.ADVISOR, access("""{"context":"advisor","canRegister":true}""")),
        )
        assertEquals(
            SalesPolicy.SellingAction.STATUS,
            SalesPolicy.sellingAction(ProductContext.ADVISOR, access("""{"context":"advisor","registrationState":"pending"}""")),
        )
    }

    @Test
    fun `every registration state has its own label`() {
        assertEquals("Chưa đăng ký", SalesPolicy.registrationLabel("not_registered"))
        assertEquals("Chờ duyệt", SalesPolicy.registrationLabel("pending"))
        assertEquals("Đã được cấp quyền bán", SalesPolicy.registrationLabel("active"))
        assertEquals("Hết suất", SalesPolicy.registrationLabel("full"))
        assertEquals("Hết hạn", SalesPolicy.registrationLabel("expired"))
        assertEquals("Đã thu hồi", SalesPolicy.registrationLabel("revoked"))
        assertEquals("Bị từ chối", SalesPolicy.registrationLabel("rejected"))
        assertEquals("Không thể đăng ký", SalesPolicy.registrationLabel(""))
        assertEquals("Không thể đăng ký", SalesPolicy.registrationLabel("unavailable"))
    }
}
