package vn.futaland.app.features.properties

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.futaland.app.core.network.JSONValue

class PricingBreakdownTest {

    private fun json(raw: String) = JSONValue.parse(raw)

    @Test
    fun `parses pricingBreakdown items and formats currency correctly`() {
        val raw = """
        {
            "salePriceLabel": "Giá bán (bao gồm VAT và PBT)",
            "price": 6860720282,
            "sellPrice": 6860720282,
            "pricingBreakdown": [
                {
                    "key": "netPriceBeforeVat",
                    "label": "Giá trị căn bán (gồm CPBH & chưa VAT)",
                    "value": 6125643109
                },
                {
                    "key": "netUnitPriceBeforeVat",
                    "label": "Đơn giá thông thủy (chưa VAT)",
                    "value": 134836960
                },
                {
                    "key": "maintenanceFee",
                    "label": "Phí bảo trì",
                    "value": 122512862
                },
                {
                    "key": "vatAmount",
                    "label": "Thuế VAT",
                    "value": 612564311
                },
                {
                    "key": "netUnitPriceWithVat",
                    "label": "Đơn giá thông thủy (gồm VAT)",
                    "value": 148320656
                },
                {
                    "key": "landPriceIncluded",
                    "label": "Giá bán (gồm QSDĐ, TSDĐ)",
                    "value": 6738207420
                }
            ]
        }
        """
        val prop = json(raw)
        val items = PricingBreakdownParser.parse(prop)
        assertEquals(6, items.size)
        assertEquals("Giá trị căn bán (gồm CPBH & chưa VAT)", items[0].label)
        assertEquals("6.125.643.109 đ", items[0].formattedValue)
        assertEquals("Đơn giá thông thủy (chưa VAT)", items[1].label)
        assertEquals("134.836.960 đ", items[1].formattedValue)
        assertEquals("Phí bảo trì", items[2].label)
        assertEquals("122.512.862 đ", items[2].formattedValue)
        assertEquals("Thuế VAT", items[3].label)
        assertEquals("612.564.311 đ", items[3].formattedValue)

        val label = PricingBreakdownParser.resolveSalePriceLabel(prop)
        assertEquals("Giá bán (gồm VAT và PBT)", label)
    }
    @Test
    fun `parses pricingBreakdown items for apartment properties with updated ERP labels`() {
        val raw = """
        {
            "propertyType": "can-ho-chung-cu",
            "projectName": "Đà Nẵng Times Square",
            "salePriceLabel": "Giá bán (bao gồm VAT và PBT)",
            "price": 6860720282,
            "sellPrice": 6860720282,
            "pricingBreakdown": [
                {
                    "key": "netPriceBeforeVat",
                    "label": "Giá trị căn bán (gồm CPBH & chưa VAT)",
                    "value": 6125643109
                },
                {
                    "key": "netUnitPriceBeforeVat",
                    "label": "Đơn giá thông thủy (chưa VAT)",
                    "value": 134836960
                },
                {
                    "key": "maintenanceFee",
                    "label": "Phí bảo trì",
                    "value": 122512862
                },
                {
                    "key": "vatAmount",
                    "label": "Thuế VAT",
                    "value": 612564311
                },
                {
                    "key": "netUnitPriceWithVat",
                    "label": "Đơn giá thông thủy (gồm VAT)",
                    "value": 148320656
                },
                {
                    "key": "landPriceIncluded",
                    "label": "Giá bán (gồm QSDĐ, TSDĐ)",
                    "value": 6738207420
                }
            ]
        }
        """
        val prop = json(raw)
        val items = PricingBreakdownParser.parse(prop)
        assertEquals(6, items.size)
        assertEquals("Giá trị căn bán (chưa bao gồm VAT và KPBT)", items[0].label)
        assertEquals("6.125.643.109 đ", items[0].formattedValue)
        assertEquals("Đơn giá thông thủy (chưa VAT)", items[1].label)
        assertEquals("134.836.960 đ", items[1].formattedValue)
        assertEquals("Phí bảo trì (2%)", items[2].label)
        assertEquals("122.512.862 đ", items[2].formattedValue)
        assertEquals("Thuế GTGT (10%)", items[3].label)
        assertEquals("612.564.311 đ", items[3].formattedValue)
    }

    @Test
    fun `preserves ERP labels for C5B project`() {
        val raw = """
        {
            "propertyType": "biet-thu-lien-ke",
            "projectName": "Khu Đô Thị C5B",
            "salePriceLabel": "Giá bán (bao gồm VAT và PBT)",
            "price": 16408500000,
            "sellPrice": 16408500000,
            "pricingBreakdown": [
                {
                    "key": "netPriceBeforeVat",
                    "label": "Giá trị căn bán (gồm CPBH & chưa VAT)",
                    "value": 14916818182
                },
                {
                    "key": "maintenanceFee",
                    "label": "Phí bảo trì",
                    "value": 298336364
                },
                {
                    "key": "vatAmount",
                    "label": "Thuế VAT",
                    "value": 1491681818
                }
            ]
        }
        """
        val prop = json(raw)
        val items = PricingBreakdownParser.parse(prop)
        assertEquals(3, items.size)
        assertEquals("Giá trị căn bán (gồm CPBH & chưa VAT)", items[0].label)
        assertEquals("Phí bảo trì", items[1].label)
        assertEquals("Thuế VAT", items[2].label)
    }

    @Test
    fun `fallbacks to standard price rows when pricingBreakdown is missing`() {
        val raw = """
        {
            "price": 5000000000,
            "sellPrice": 4800000000,
            "fees": {
                "managementFee": "15.000 đ/m²",
                "utilitiesFee": "100.000 đ"
            }
        }
        """
        val prop = json(raw)
        val items = PricingBreakdownParser.parse(prop)
        assertEquals(4, items.size)
        assertEquals("Giá niêm yết từ hệ thống", items[0].label)
        assertEquals("5.000.000.000 đ", items[0].formattedValue)
        assertEquals("Giá bán hiện tại", items[1].label)
        assertEquals("4.800.000.000 đ", items[1].formattedValue)
        assertEquals("Phí quản lý", items[2].label)
        assertEquals("15.000 đ/m²", items[2].formattedValue)
        assertEquals("Phí tiện ích", items[3].label)
        assertEquals("100.000 đ", items[3].formattedValue)
    }
}
