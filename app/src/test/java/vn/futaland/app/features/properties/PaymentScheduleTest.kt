package vn.futaland.app.features.properties

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.futaland.app.core.network.JSONValue

class PaymentScheduleTest {

    private fun json(raw: String) = JSONValue.parse(raw)

    @Test
    fun `empty json with fallback returns standard policies`() {
        val policies = PaymentScheduleEngine.parsePolicies(json("{}"))
        assertEquals(3, policies.size)
        assertEquals("Chính sách thanh toán chuẩn", policies[0].name)
        assertEquals("Chính sách thanh toán nhanh", policies[1].name)
        assertEquals("Chính sách thanh toán vay", policies[2].name)
    }

    @Test
    fun `empty json yields no policies when fallback disabled`() {
        val policies = PaymentScheduleEngine.parsePolicies(json("{}"), allowFallback = false)
        assertTrue(policies.isEmpty())
    }

    @Test
    fun `calculates discount and net price accurately`() {
        val raw = """
        {
            "paymentPolicies": [
                {
                    "id": "p1",
                    "code": "CSTT-01",
                    "name": "Chính sách chuẩn",
                    "discountPercent": 0.5,
                    "depositAmount": 100000000.0,
                    "installments": [
                        { "name": "Đợt 1", "timing": { "days": 7, "anchorLabel": "ký cọc" }, "percent": 15.0 },
                        { "name": "Đợt 2", "timing": { "days": 30, "anchorLabel": "sau đợt 1" }, "percent": 85.0 }
                    ]
                }
            ]
        }
        """
        val policies = PaymentScheduleEngine.parsePolicies(json(raw))
        assertEquals(1, policies.size)
        val policy = policies[0]
        assertEquals(0.5, policy.discountPercent, 0.001)
        assertEquals(100_000_000L, policy.depositAmount)

        val basePrice = 7_904_534_578L
        val result = PaymentScheduleEngine.calculate(policy, basePrice)

        assertEquals(7_904_534_578L, result.basePrice)
        // 7_904_534_578 * 0.5% = 39_522_673
        assertEquals(39_522_673L, result.discountAmount)
        // 7_904_534_578 - 39_522_673 = 7_865_011_905
        assertEquals(7_865_011_905L, result.netPrice)
        assertEquals(100_000_000L, result.depositAmount)
        assertEquals(2, result.rows.size)
        assertTrue(result.rows[0].includesDeposit)
        assertEquals(7_865_011_905L, result.rows[1].cumulative)

        val summaryText = PaymentScheduleEngine.exportSummaryText(policy, result, "CT7-05.08")
        assertTrue(summaryText.contains("Mã căn: CT7-05.08"))
        assertTrue(summaryText.contains("GIÁ THANH TOÁN THỰC TẾ"))
        assertTrue(summaryText.contains("7,865,011,905 đ"))
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

    @Test
    fun `formatErpTiming supports plain string timing`() {
        val timingStr = json("\"7 ngày kể từ ngày ký hợp đồng cọc\"")
        assertEquals("7 ngày kể từ ngày ký hợp đồng cọc", PaymentScheduleEngine.formatErpTiming(timingStr))
    }

    @Test
    fun `calculates CT7-05 12 fast payment policy accurately matching web screenshot`() {
        val remoteJson = """
        [
            {
                "id": "9e785216",
                "code": "CT-003172",
                "name": "Chính sách thanh toán nhanh",
                "discountPercent": 1.0,
                "depositAmount": 100000000,
                "installments": [
                    {
                        "name": "Đặt cọc / Ký thỏa thuận đặt cọc (TTĐC)",
                        "timing": "7 ngày kể từ ngày ký hợp đồng cọc",
                        "percent": 95,
                        "note": "Bao gồm 100.000.000 đ tiền đặt cọc (ký TTĐC/HĐC)"
                    },
                    {
                        "name": "Ký HĐMB / Hoàn thành móng cọc",
                        "timing": "30 ngày kể từ ngày hoàn tất đợt 1",
                        "percent": 5,
                        "note": "Chuyển sang Hợp đồng Mua bán chính thức"
                    }
                ]
            }
        ]
        """
        val externalPolicies = json(remoteJson).array
        val policies = PaymentScheduleEngine.parsePolicies(json("{}"), externalPolicies = externalPolicies)
        assertEquals(1, policies.size)
        val policy = policies[0]
        assertEquals("Chính sách thanh toán nhanh", policy.name)
        assertEquals(1.0, policy.discountPercent, 0.0001)

        val basePrice = 6_860_720_282L
        val result = PaymentScheduleEngine.calculate(policy, basePrice)

        assertEquals(6_860_720_282L, result.basePrice)
        assertEquals(68_607_203L, result.discountAmount)
        assertEquals(6_792_113_079L, result.netPrice)
        assertEquals(100_000_000L, result.depositAmount)
        assertEquals(2, result.rows.size)

        // Row 1: 95%
        assertEquals(1, result.rows[0].order)
        assertEquals(95.0, result.rows[0].percent ?: 0.0, 0.001)
        assertEquals(6_452_507_425L, result.rows[0].amount)
        assertEquals(6_452_507_425L, result.rows[0].cumulative)
        assertEquals("7 ngày kể từ ngày ký hợp đồng cọc", result.rows[0].timing)
        assertEquals("Bao gồm 100.000.000 đ tiền đặt cọc (ký TTĐC/HĐC)", result.rows[0].note)

        // Row 2: 5%
        assertEquals(2, result.rows[1].order)
        assertEquals(5.0, result.rows[1].percent ?: 0.0, 0.001)
        assertEquals(339_605_654L, result.rows[1].amount)
        assertEquals(6_792_113_079L, result.rows[1].cumulative)
        assertEquals("30 ngày kể từ ngày hoàn tất đợt 1", result.rows[1].timing)
        assertEquals("Chuyển sang Hợp đồng Mua bán chính thức", result.rows[1].note)
    }
}
