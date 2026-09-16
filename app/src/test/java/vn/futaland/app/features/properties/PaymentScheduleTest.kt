package vn.futaland.app.features.properties

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.futaland.app.core.network.JSONValue

class PaymentScheduleTest {

    private fun json(raw: String) = JSONValue.parse(raw)

    @Test
    fun `empty json yields no policies`() {
        val policies = PaymentScheduleEngine.parsePolicies(json("{}"))
        assertTrue(policies.isEmpty())
    }

    @Test
    fun `parses erpPriceTable payment plans with pre-computed amounts`() {
        val raw = """
        {
            "erpPriceTable": {
                "basis": { "listPrice": 10000000000.0 },
                "paymentPlans": [
                    {
                        "policyId": "plan1",
                        "policyCode": "CSTT-001",
                        "policyName": "Thanh toán chuẩn",
                        "totalAmount": 10000000000.0,
                        "balanced": true,
                        "installments": [
                            { "order": 0, "name": "Đợt 1", "amount": 1500000000.0, "percent": 15.0, "timing": { "days": 0, "anchorLabel": "Ký HĐMB" } },
                            { "order": 1, "name": "Đợt 2", "amount": 8500000000.0, "percent": 85.0, "timing": { "days": 30, "anchorLabel": "Sau đợt 1" } }
                        ]
                    }
                ]
            }
        }
        """
        val prop = json(raw)
        val policies = PaymentScheduleEngine.parsePolicies(prop)
        assertEquals(1, policies.size)
        assertEquals("plan1", policies[0].id)
        assertEquals("Thanh toán chuẩn", policies[0].name)

        val schedule = PaymentScheduleEngine.calculate(policies[0], 10000000000L)
        assertEquals(2, schedule.rows.size)
        assertEquals(1500000000L, schedule.rows[0].amount)
        assertEquals(8500000000L, schedule.rows[1].amount)
        assertEquals(10000000000L, schedule.rows[1].cumulative)
        assertEquals("Ký HĐMB", schedule.rows[0].timing)
        assertEquals("30 ngày sau Sau đợt 1", schedule.rows[1].timing)
    }

    @Test
    fun `parses erpPaymentMethods fallback with percent only and scales correctly`() {
        val raw = """
        {
            "erpPaymentMethods": {
                "paymentPolicies": [
                    {
                        "id": "method1",
                        "code": "CS-NHANH",
                        "name": "Phương thức nhanh",
                        "installments": [
                            { "order": 0, "name": "1", "percent": 30.0, "amount": null },
                            { "order": 1, "name": "2", "percent": 70.0, "amount": null }
                        ]
                    }
                ]
            }
        }
        """
        val prop = json(raw)
        val policies = PaymentScheduleEngine.parsePolicies(prop)
        assertEquals(1, policies.size)
        assertEquals("method1", policies[0].id)

        val schedule = PaymentScheduleEngine.calculate(policies[0], 5000000000L)
        assertEquals(2, schedule.rows.size)
        assertEquals(1500000000L, schedule.rows[0].amount)
        assertEquals(3500000000L, schedule.rows[1].amount)
        assertEquals(5000000000L, schedule.rows[1].cumulative)
    }

    @Test
    fun `last installment absorbs rounding drift when balanced`() {
        val raw = """
        {
            "erpPaymentMethods": {
                "paymentPolicies": [
                    {
                        "id": "m1",
                        "name": "Chia 3 đợt",
                        "installments": [
                            { "order": 0, "percent": 33.33 },
                            { "order": 1, "percent": 33.33 },
                            { "order": 2, "percent": 33.34 }
                        ]
                    }
                ]
            }
        }
        """
        val prop = json(raw)
        val policies = PaymentScheduleEngine.parsePolicies(prop)
        val schedule = PaymentScheduleEngine.calculate(policies[0], 1000000000L)
        assertEquals(3, schedule.rows.size)
        assertEquals(1000000000L, schedule.rows[2].cumulative)
    }
}
