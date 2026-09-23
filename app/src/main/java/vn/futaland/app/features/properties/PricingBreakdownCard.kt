package vn.futaland.app.features.properties

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.FutaCard
import vn.futaland.app.designsystem.FutaColors
import java.text.NumberFormat
import java.util.Locale

data class PricingBreakdownItem(
    val key: String,
    val label: String,
    val value: Double?,
    val displayString: String? = null
) {
    val formattedValue: String
        get() {
            if (displayString != null && displayString.isNotBlank()) return displayString
            if (value != null && value > 0) {
                return formatVndCurrency(value)
            }
            return "Đang cập nhật"
        }

    companion object {
        fun formatVndCurrency(amount: Double): String {
            val formatter = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"))
            formatter.maximumFractionDigits = 0
            return "${formatter.format(amount.toLong())} đ"
        }
    }
}

object PricingBreakdownParser {

    fun parse(property: JSONValue): List<PricingBreakdownItem> {
        val breakdownArray = property["pricingBreakdown"].array
        val isApt = isApartment(property)
        val configured = breakdownArray.mapNotNull { item ->
            val rawLabel = item["label"].string.trim()
            val key = item["key"].string.ifEmpty { rawLabel }
            val value = if (item["value"].double > 0) item["value"].double else null
            val strVal = item["value"].string.trim()
            if (rawLabel.isNotEmpty() && (value != null || strVal.isNotEmpty())) {
                val label = if (isApt) {
                    when {
                        key == "netPriceBeforeVat" || rawLabel.contains("gồm CPBH", ignoreCase = true) ->
                            "Giá trị căn bán (chưa bao gồm VAT và KPBT)"
                        key == "maintenanceFee" || rawLabel == "Phí bảo trì" || rawLabel == "Kinh phí bảo trì" ->
                            "Phí bảo trì (2%)"
                        key == "vatAmount" || rawLabel == "Thuế VAT" || rawLabel == "VAT" ->
                            "Thuế GTGT (10%)"
                        else -> rawLabel
                    }
                } else rawLabel
                PricingBreakdownItem(
                    key = key,
                    label = label,
                    value = value,
                    displayString = if (value == null && strVal.isNotEmpty()) strVal else null
                )
            } else null
        }
        if (configured.isNotEmpty()) return configured

        // Fallback standard rows if pricingBreakdown is not configured
        val rows = mutableListOf<PricingBreakdownItem>()
        val listPrice = property["price"].double
        val sellPrice = property["sellPrice"].double
        val finalPrice = if (sellPrice > 0) sellPrice else listPrice

        if (listPrice > 0) {
            rows.add(PricingBreakdownItem("listPrice", "Giá niêm yết từ hệ thống", listPrice))
        }
        if (finalPrice > 0 && finalPrice != listPrice) {
            rows.add(PricingBreakdownItem("currentPrice", "Giá bán hiện tại", finalPrice))
        }
        val mgmtFee = property["fees"]["managementFee"].string.trim()
        if (mgmtFee.isNotEmpty()) {
            rows.add(PricingBreakdownItem("managementFee", "Phí quản lý", null, displayString = mgmtFee))
        }
        val utilFee = property["fees"]["utilitiesFee"].string.trim()
        if (utilFee.isNotEmpty()) {
            rows.add(PricingBreakdownItem("utilitiesFee", "Phí tiện ích", null, displayString = utilFee))
        }
        return rows
    }

    fun resolveSalePriceLabel(property: JSONValue): String {
        val listingType = property["listingType"].string.lowercase()
        if (listingType == "rent" || listingType == "thuê" || listingType == "cho thuê") {
            return "Giá cho thuê"
        }
        val raw = property["salePriceLabel"].string.trim()
        val defaultLabel = if (isApartment(property)) {
            "Giá bán (bao gồm VAT và PBT)"
        } else {
            "Giá bán"
        }
        val baseLabel = raw.ifEmpty { defaultLabel }
        return baseLabel
            .replace(Regex("(?i)bao gồm|đã gồm"), "gồm")
            .replace(Regex("(?i)\\s+và\\s+phí bảo trì"), " và PBT")
    }

    private fun isApartment(property: JSONValue): Boolean {
        val proj = (property["projectName"].string + " " + property["zone"].string).lowercase()
        if (proj.contains("c5b")) return false
        val type = property["propertyType"].string.lowercase()
        return isApartmentType(type) || proj.contains("times square") || proj.contains("căn hộ")
    }

    private fun isApartmentType(type: String): Boolean {
        val lower = type.lowercase()
        return lower.contains("can-ho") || lower.contains("chung-cu") || lower.contains("apartment") || lower.contains("căn hộ")
    }
}

@Composable
fun PricingBreakdownCard(
    property: JSONValue,
    hasPolicies: Boolean,
    onTryCalculationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(property) { PricingBreakdownParser.parse(property) }
    val salePriceLabel = remember(property) { PricingBreakdownParser.resolveSalePriceLabel(property) }
    val finalPrice = remember(property) {
        val s = property["sellPrice"].double
        if (s > 0) s else property["price"].double
    }
    val formattedFinalPrice = remember(finalPrice) {
        if (finalPrice > 0) PricingBreakdownItem.formatVndCurrency(finalPrice) else "Đang cập nhật"
    }

    if (items.isEmpty() && finalPrice <= 0) return

    FutaCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = Color(0xFFDFE6ED)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Text(
                text = "BẢNG GIÁ CHI TIẾT (Tạm tính)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = FutaColors.Navy,
                letterSpacing = 0.5.sp
            )

            // Breakdown Rows
            if (items.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.label,
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = item.formattedValue,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FutaColors.Navy,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            // Summary Total Highlight Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFFEF3C7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = salePriceLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF92400E),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formattedFinalPrice,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFB45309),
                        textAlign = TextAlign.End
                    )
                }
            }

            // "Tính giá thử" Action Button
            if (hasPolicies) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFF238451)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable { onTryCalculationClick() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = Color(0xFF238451),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tính giá thử",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF238451)
                        )
                    }
                }
            }
        }
    }
}
