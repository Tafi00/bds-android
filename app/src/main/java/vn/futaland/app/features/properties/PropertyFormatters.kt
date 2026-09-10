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

    fun propertyTitle(value: JSONValue): String {
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
        if (raw.isEmpty()) return "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=800&q=80"
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val clean = if (raw.startsWith("/")) raw else "/$raw"
        return "https://bds.futaland.vn$clean"
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
        val direct = value["image"].string.trim()
        if (direct.isNotEmpty() && direct != "null") return direct
        val banner = value["bannerImage"].string.trim()
        if (banner.isNotEmpty() && banner != "null") return banner
        val images = value["images"].array
        if (images.isNotEmpty()) {
            val first = images[0]
            val orig = first["original"].string.trim()
            if (orig.isNotEmpty() && orig != "null") return orig
            val url = first["url"].string.trim()
            if (url.isNotEmpty() && url != "null") return url
            val str = first.string.trim()
            if (str.isNotEmpty() && str != "null") return str
        }
        return ""
    }
}
