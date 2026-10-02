package vn.futaland.app.features.properties

import vn.futaland.app.core.i18n.tr
import vn.futaland.app.core.i18n.Text
import vn.futaland.app.core.i18n.VerbatimText
import vn.futaland.app.core.i18n.LocalizedDirection
import vn.futaland.app.core.i18n.LocalizedPrice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue
import vn.futaland.app.designsystem.*

/** Search filters extracted from a spoken request (iOS ParsedVoiceFilters). */
data class ParsedVoiceFilters(
    val listingType: String? = null,
    val propertyType: List<String> = emptyList(),
    val searchText: String? = null,
    val bedrooms: List<String> = emptyList(),
    val toilets: List<String> = emptyList(),
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val areaMin: Double? = null,
    val areaMax: Double? = null,
    val furniture: List<String> = emptyList(),
    val direction: List<String> = emptyList(),
    val matchCount: Int = 0,
    val rawTranscript: String = ""
) {
    data class Chip(val category: String, val title: String)

    val hasMeaningfulFilters: Boolean
        get() = bedrooms.isNotEmpty() || toilets.isNotEmpty() || priceMax != null || priceMin != null ||
            searchText != null || direction.isNotEmpty() || furniture.isNotEmpty() || areaMin != null || areaMax != null

    val chips: List<Chip>
        get() = buildList {
            if (listingType == "sell") add(Chip("Giao dịch", tr("Mua bán")))
            searchText?.takeIf { it.isNotEmpty() }?.let { add(Chip("Khu vực", it)) }
            propertyType.forEach { add(Chip("Loại BĐS", it)) }
            val pMin = priceMin?.takeIf { it > 0 }
            val pMax = priceMax?.takeIf { it > 0 }
            when {
                pMin != null && pMax != null -> add(Chip("Khoảng giá", "${LocalizedPrice.compact(pMin)} - ${LocalizedPrice.compact(pMax)}"))
                pMin != null -> add(Chip("Giá tối thiểu", tr("Từ {0}", LocalizedPrice.compact(pMin))))
                pMax != null -> add(Chip("Giá tối đa", tr("Đến {0}", LocalizedPrice.compact(pMax))))
            }
            val aMin = areaMin?.takeIf { it > 0 }?.toInt()
            val aMax = areaMax?.takeIf { it > 0 }?.toInt()
            when {
                aMin != null && aMax != null -> add(Chip("Diện tích", "$aMin - $aMax m²"))
                aMin != null -> add(Chip("Diện tích", tr("Từ {0} m²", aMin)))
                aMax != null -> add(Chip("Diện tích", tr("Đến {0} m²", aMax)))
            }
            bedrooms.forEach { add(Chip("Phòng ngủ", tr("{0} PN", it))) }
            toilets.forEach { add(Chip("Phòng tắm", tr("{0} WC", it))) }
            direction.forEach { add(Chip("Hướng nhà", LocalizedDirection.name(it))) }
            furniture.forEach { add(Chip("Nội thất", tr(it))) }
        }

    companion object {
        private fun list(value: JSONValue): List<String> =
            value.string.takeIf { it.isNotEmpty() }?.let { listOf(it) }
                ?: value.array.map { it.string }.filter { it.isNotEmpty() }

        /** `data` of POST /speech/parse-filters: `{ filters, transcript, matchCount }`. */
        fun fromJson(data: JSONValue, transcript: String): ParsedVoiceFilters {
            val f = data["filters"]
            return ParsedVoiceFilters(
                listingType = f["listingType"].string.ifEmpty { null },
                propertyType = list(f["propertyType"]),
                searchText = f["searchText"].string.ifEmpty { null },
                bedrooms = list(f["bedrooms"]),
                toilets = list(f["wc"]),
                priceMin = f["priceMin"].double.takeIf { it > 0 },
                priceMax = f["priceMax"].double.takeIf { it > 0 },
                areaMin = f["areaMin"].double.takeIf { it > 0 },
                areaMax = f["areaMax"].double.takeIf { it > 0 },
                furniture = list(f["furniture"]),
                direction = list(f["direction"]),
                matchCount = data["matchCount"].int,
                rawTranscript = data["transcript"].string.ifEmpty { transcript }
            )
        }
    }
}

/**
 * POST /speech/parse-filters (authenticated, AI + server rule fallback); when the call is not
 * possible or fails, the on-device rule parser is used like iOS. Returns null when nothing usable
 * could be extracted.
 */
suspend fun parseVoiceFilters(transcript: String): ParsedVoiceFilters? {
    val text = transcript.trim()
    if (text.isEmpty()) return null
    if (AppSession.shared.isAuthenticated) {
        try {
            val body = buildJsonObject { put("transcript", text) }.toString()
            val res = APIClient.get().request("/speech/parse-filters", method = "POST", bodyJson = body)
            return ParsedVoiceFilters.fromJson(if (res["data"].isNull) res else res["data"], text)
        } catch (_: Exception) {
            // Fall through to the local parser.
        }
    }
    return VoiceSearchLocalParser.parse(text).takeIf { it.hasMeaningfulFilters }
}

/** On-device rule parser, same rules as iOS VoiceSearchLocalParser. */
object VoiceSearchLocalParser {
    private fun match(pattern: String, text: String): String? =
        Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)

    private fun number(raw: String?): Double? = raw?.replace(",", ".")?.toDoubleOrNull()

    fun parse(transcript: String): ParsedVoiceFilters {
        val lower = transcript.lowercase()

        val bedrooms = match("""(\d+)\s*(?:phòng ngủ|pn|phòng)""", lower)?.let { listOf(it) }
            ?: if (lower.contains("studio")) listOf("0") else emptyList()
        val toilets = match("""(\d+)\s*(?:vệ sinh|wc|toilet|phòng tắm)""", lower)?.let { listOf(it) } ?: emptyList()

        val priceMax = number(match("""(?:dưới|tối đa|<|ít hơn)\s*(\d+(?:[.,]\d+)?)\s*(?:tỷ|ti)""", lower))?.times(1_000_000_000)
            ?: number(match("""(?:dưới|tối đa|<|ít hơn)\s*(\d+(?:[.,]\d+)?)\s*(?:triệu|tr)""", lower))?.times(1_000_000)
        val priceMin = number(match("""(?:trên|tối thiểu|>|lớn hơn|từ)\s*(\d+(?:[.,]\d+)?)\s*(?:tỷ|ti)""", lower))?.times(1_000_000_000)
            ?: number(match("""(?:trên|tối thiểu|>|lớn hơn|từ)\s*(\d+(?:[.,]\d+)?)\s*(?:triệu|tr)""", lower))?.times(1_000_000)

        val areaMax = number(match("""(?:dưới|tối đa)\s*(\d+(?:[.,]\d+)?)\s*(?:m2|m²|mét vuông)""", lower))
        val areaMin = number(match("""(?:trên|tối thiểu|từ)\s*(\d+(?:[.,]\d+)?)\s*(?:m2|m²|mét vuông)""", lower))

        val direction = listOf("Đông Nam", "Đông Bắc", "Tây Nam", "Tây Bắc", "Đông", "Tây", "Nam", "Bắc")
            .firstOrNull { lower.contains(it.lowercase()) }?.let { listOf(it) } ?: emptyList()

        val furniture = when {
            lower.contains("full nội thất") || lower.contains("đầy đủ nội thất") || lower.contains("đủ nội thất") -> listOf("Đầy đủ nội thất")
            lower.contains("nội thất cơ bản") || lower.contains("cơ bản") -> listOf("Nội thất cơ bản")
            lower.contains("nhà trống") -> listOf("Nhà trống")
            lower.contains("bếp rèm") -> listOf("Bếp rèm")
            else -> emptyList()
        }

        val location = Regex(
            """(?:quận|huyện|thành phố|tp|phường|đường|dự án|vinhomes|times square|sơn trà|ngũ hành sơn|hải châu|bình thạnh|thủ đức|c5b|kim an|đà nẵng)\s*[^,.]*""",
            RegexOption.IGNORE_CASE
        ).find(transcript)?.value

        return ParsedVoiceFilters(
            listingType = "sell",
            searchText = location?.let { cleanSearchText(it) },
            bedrooms = bedrooms,
            toilets = toilets,
            priceMin = priceMin,
            priceMax = priceMax,
            areaMin = areaMin,
            areaMax = areaMax,
            furniture = furniture,
            direction = direction,
            rawTranscript = transcript
        )
    }

    private fun cleanSearchText(text: String): String? {
        var cleaned = text
        listOf(
            """(?:tìm\s*kiếm|tìm|cần\s*mua|cần\s*bán|mua|bán)""",
            """(?:căn\s*hộ\s*chung\s*cư|căn\s*hộ|chung\s*cư|nhà\s*phố|biệt\s*thự\s*liền\s*kề|biệt\s*thự|shophouse|nhà\s*riêng|nhà\s*liền\s*kề|đất\s*nền)""",
            """\d+\s*(?:phòng\s*ngủ|pn|phòng|wc|vệ\s*sinh|phòng\s*tắm)""",
            """(?:dưới|trên|tối\s*đa|tối\s*thiểu|từ|<|>)?\s*\d+(?:[.,]\d+)?\s*(?:tỷ|ti|triệu|tr|m2|m²|mét\s*vuông)""",
            """(?:giá\s*rẻ|mới\s*nhất|đẹp|cao\s*cấp|cho\s*thuê|thuê)""",
            """(?:tại|ở|khu\s*vực|dự\s*án|toà|tòa)""",
            """[,.\-–]"""
        ).forEach { cleaned = Regex(it, RegexOption.IGNORE_CASE).replace(cleaned, " ") }
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return cleaned.takeIf { it.length >= 2 }
    }
}

/** Review step after speech recognition: editable transcript, extracted filter chips, apply. */
@Composable
fun VoiceFilterReviewSheet(
    parsing: Boolean,
    parsed: ParsedVoiceFilters?,
    transcript: String,
    onReparse: (String) -> Unit,
    onApply: (ParsedVoiceFilters) -> Unit,
    onSpeakAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    var editable by remember(transcript) { mutableStateOf(transcript) }

    FutaBottomSheet(visible = true, onDismiss = onDismiss, title = "Tìm kiếm bằng giọng nói") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (parsing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 18.dp)) {
                    CircularProgressIndicator(color = FutaColors.BrandGreen, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Đang phân tích yêu cầu…", fontSize = 13.5.sp, color = FutaColors.Slate)
                }
            } else if (parsed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(FutaColors.MintBg, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Phân tích hoàn tất", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                        if (parsed.matchCount > 0) {
                            Text(tr("Tìm thấy {0} bất động sản phù hợp", parsed.matchCount), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
                        }
                    }
                }
            } else {
                Text(
                    "Không thể phân tích yêu cầu giọng nói. Vui lòng thử nói lại rõ hơn hoặc gõ từ khóa.",
                    fontSize = 13.sp,
                    color = Color(0xFFDC2626)
                )
            }

            Text("Nội dung giọng nói", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.Slate)
            FutaInput(value = editable, onValueChange = { editable = it }, placeholder = "Nội dung giọng nói...", singleLine = false)
            if (editable.trim() != transcript.trim() && editable.isNotBlank() && !parsing) {
                Row(
                    modifier = Modifier.clickable { onReparse(editable) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, null, tint = FutaColors.BrandGreen, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cập nhật & Phân tích lại", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FutaColors.BrandGreen)
                }
            }

            if (parsed != null && !parsing) {
                Text("Bộ lọc được AI trích xuất:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FutaColors.Navy)
                val chips = parsed.chips
                if (chips.isEmpty()) {
                    Text("Không có tiêu chí đặc thù nào được nhận diện. Hệ thống sẽ tìm kiếm theo từ khóa.", fontSize = 12.sp, color = FutaColors.Slate)
                } else {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { chip ->
                            Column(
                                modifier = Modifier
                                    .background(FutaColors.BrandGreen.copy(alpha = 0.12f), CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(chip.category, fontSize = 9.sp, color = FutaColors.Slate)
                                VerbatimText(chip.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FutaColors.BrandGreen)
                            }
                        }
                    }
                }
                FutaButton(
                    text = if (parsed.matchCount > 0) tr("Áp dụng bộ lọc ({0} kết quả)", parsed.matchCount) else tr("Áp dụng bộ lọc"),
                    icon = Icons.Default.Search,
                    onClick = { onApply(parsed) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FutaButton(
                text = "Nói lại yêu cầu khác",
                icon = Icons.Default.Mic,
                variant = FutaButtonVariant.OUTLINE,
                enabled = !parsing,
                onClick = onSpeakAgain,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
