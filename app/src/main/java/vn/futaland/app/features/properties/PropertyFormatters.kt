package vn.futaland.app.features.properties

import vn.futaland.app.core.network.JSONValue

object PropertyFormatters {

    fun formatPrice(value: Double, fallback: String = ""): String {
        if (value <= 0) {
            return fallback.ifEmpty { "Thỏa thuận" }
        }
        if (value >= 1_000_000_000) {
            val bill = value / 1_000_000_000.0
            val formatted = "%.2f".format(bill).replace(".00", "").replace(".", ",")
            return "$formatted tỷ"
        }
        if (value >= 1_000_000) {
            val mil = value / 1_000_000.0
            val formatted = "%.1f".format(mil).replace(".0", "").replace(".", ",")
            return "$formatted triệu"
        }
        return "${"%,d".format(value.toLong()).replace(",", ".")} đ"
    }

    fun listingPrice(apartment: JSONValue): String {
        val listingType = apartment["listingType"].string.lowercase()
        val isSell = listingType == "sell" || listingType == "bán" || listingType == "mua bán"
        val rawPrice = if (isSell && apartment["sellPrice"].double > 0) {
            apartment["sellPrice"].double
        } else {
            if (apartment["price"].double > 0) apartment["price"].double else apartment["sellPrice"].double
        }

        val formatted = formatPrice(rawPrice, fallback = apartment["price"].string)
        if (isSell || rawPrice <= 0 || formatted == "Thỏa thuận") {
            return formatted
        }
        return "$formatted/tháng"
    }

    fun propertyTitle(value: JSONValue?): String {
        if (value == null) return "Bất động sản FUTA Land"
        val code = value["propertyCode"].string.trim()
        val t = value["title"].string.trim()

        if (code.isNotEmpty()) {
            if (t.isNotEmpty() && t.length <= 40) return t
            val aptType = value["apartmentType"].string.trim()
            val building = value["building"].string.trim()
            if (aptType.isNotEmpty() && building.isNotEmpty()) {
                return "$aptType $building • Mã: $code"
            }
            return if (t.isNotEmpty()) t else "Mã căn: $code"
        }
        return t.ifEmpty { "Bất động sản FUTA Land" }
    }

    fun addressText(value: JSONValue): String {
        val addr = value["address"].string.trim()
        if (addr.isNotEmpty()) return addr
        val zone = value["zone"].string.trim()
        val proj = value["projectName"].string.trim()
        if (zone.isNotEmpty() && proj.isNotEmpty()) return "$zone • $proj"
        if (zone.isNotEmpty()) return zone
        return "Đà Nẵng · FUTA Land"
    }

    fun resolveImage(value: JSONValue): String {
        val raw = extractRawImage(value)
        if (raw.isNotEmpty()) {
            if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
            val clean = raw.removePrefix("/")
            return "https://bds.futaland.vn/$clean"
        }
        val zoneText = (value["zone"].string.ifEmpty {
            value["projectName"].string.ifEmpty {
                value["apartment"]["zone"].string.ifEmpty {
                    value["project"]["displayName"].string
                }
            }
        }).lowercase()
        return when {
            zoneText.contains("kim an") || zoneText.contains("c5b") ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/futa-kim-an.png"
            zoneText.contains("kim phát") || zoneText.contains("kim-phat") ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/futa-kim-phat.png"
            zoneText.contains("hampton") || zoneText.contains("võ nguyên giáp") || zoneText.contains("vo-nguyen-giap") ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/hampton-vo-nguyen-giap.png"
            else ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/times-square.png"
        }
    }

    fun resolveProjectBanner(project: JSONValue): String {
        val mobBanner = project["bannerImageMobile"].string.trim()
        if (mobBanner.isNotEmpty() && mobBanner != "null") {
            return if (mobBanner.startsWith("http://") || mobBanner.startsWith("https://")) mobBanner else "https://bds.futaland.vn/${mobBanner.removePrefix("/")}"
        }
        val banner = project["bannerImage"].string.trim()
        if (banner.isNotEmpty() && banner != "null") {
            return if (banner.startsWith("http://") || banner.startsWith("https://")) banner else "https://bds.futaland.vn/${banner.removePrefix("/")}"
        }
        val img = project["image"].string.trim()
        if (img.isNotEmpty() && img != "null") {
            return if (img.startsWith("http://") || img.startsWith("https://")) img else "https://bds.futaland.vn/${img.removePrefix("/")}"
        }
        return resolveImage(project)
    }

    private fun extractRawImage(value: JSONValue): String {
        // 1. Direct fields
        val direct = value["image"].string.trim()
        if (direct.isNotEmpty() && direct != "null") return direct
        val banner = value["bannerImage"].string.trim()
        if (banner.isNotEmpty() && banner != "null") return banner
        val bannerMob = value["bannerImageMobile"].string.trim()
        if (bannerMob.isNotEmpty() && bannerMob != "null") return bannerMob

        // 2. Direct images array
        val images = value["images"].array
        if (images.isNotEmpty()) {
            for (img in images) {
                val orig = img["original"].string.trim()
                if (orig.isNotEmpty() && orig != "null") return orig
                val url = img["url"].string.trim()
                if (url.isNotEmpty() && url != "null") return url
                val str = img.string.trim()
                if (str.isNotEmpty() && str != "null") return str
            }
        }

        // 3. Nested apartment object
        val apt = value["apartment"]
        if (!apt.isNull) {
            val aptImg = apt["image"].string.trim()
            if (aptImg.isNotEmpty() && aptImg != "null") return aptImg
            val aptBanner = apt["bannerImage"].string.trim()
            if (aptBanner.isNotEmpty() && aptBanner != "null") return aptBanner
            val aptImages = apt["images"].array
            if (aptImages.isNotEmpty()) {
                for (img in aptImages) {
                    val orig = img["original"].string.trim()
                    if (orig.isNotEmpty() && orig != "null") return orig
                    val url = img["url"].string.trim()
                    if (url.isNotEmpty() && url != "null") return url
                    val str = img.string.trim()
                    if (str.isNotEmpty() && str != "null") return str
                }
            }
        }

        // 4. Nested project object
        val proj = value["project"]
        if (!proj.isNull) {
            val projBanner = proj["bannerImage"].string.trim()
            if (projBanner.isNotEmpty() && projBanner != "null") return projBanner
            val projImg = proj["image"].string.trim()
            if (projImg.isNotEmpty() && projImg != "null") return projImg
        }

        return ""
    }

    fun shareUrl(value: JSONValue?, fallbackId: String = ""): String {
        val rawId = value?.get("recordId")?.string?.trim()
            ?.ifEmpty { value.id.trim() }
            ?.ifEmpty { fallbackId.trim() }
            ?: fallbackId.trim()
        if (rawId.isEmpty()) return "https://bds.futaland.vn"
        val encodedId = java.net.URLEncoder.encode(rawId, "UTF-8").replace("+", "%20")
        return "https://bds.futaland.vn/listing/$encodedId"
    }
}
