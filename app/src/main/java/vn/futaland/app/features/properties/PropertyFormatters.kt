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

    fun listingPrice(property: JSONValue): String {
        val listingType = property["listingType"].string.lowercase()
        val isSell = listingType == "sell" || listingType == "bán" || listingType == "mua bán"
        val rawPrice = if (isSell && property["sellPrice"].double > 0) {
            property["sellPrice"].double
        } else {
            if (property["price"].double > 0) property["price"].double else property["sellPrice"].double
        }

        val formatted = formatPrice(rawPrice, fallback = property["price"].string)
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
            val unitType = value["unitType"].string.trim().ifEmpty { value["apartmentType"].string.trim() }
            val block = value["block"].string.trim().ifEmpty { value["building"].string.trim() }
            if (unitType.isNotEmpty() && block.isNotEmpty()) {
                return "$unitType $block • Mã: $code"
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
        val projectName = (
            value["projectName"].string
                .ifEmpty { value["zone"].string }
                .ifEmpty { value["property"]["projectName"].string }
                .ifEmpty { value["project"]["displayName"].string }
            ).lowercase()
        return when {
            projectName.contains("kim an") || projectName.contains("c5b") ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/futa-kim-an.png"
            projectName.contains("kim phát") || projectName.contains("kim-phat") ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/futa-kim-phat.png"
            projectName.contains("hampton") || projectName.contains("võ nguyên giáp") || projectName.contains("vo-nguyen-giap") ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/hampton-vo-nguyen-giap.png"
            projectName.contains("hilton") || projectName.contains("mũi né") || projectName.contains("mui ne") ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/hilton-mui-ne.png"
            else ->
                "https://bds.futaland.vn/images/figma-data/projects/exact/times-square.png"
        }
    }

    fun resolveProjectBanner(project: JSONValue): String {
        val mobBanner = resolveImageUrl(project["bannerImageMobile"].string)
        if (mobBanner.isNotEmpty()) return mobBanner
        val banner = resolveImageUrl(project["bannerImage"].string)
        if (banner.isNotEmpty()) return banner
        val img = resolveImageUrl(project["image"].string)
        if (img.isNotEmpty()) return img
        return resolveImage(project)
    }

    /** Prepends the public web origin to relative `/images/...` paths; absolute
     *  URLs and empty/"null" placeholders pass through untouched. */
    fun resolveImageUrl(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty() || clean == "null") return ""
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        return "https://bds.futaland.vn/${clean.removePrefix("/")}"
    }

    /** Public marketing site for a project — mirrors the web
     *  `getProjectWebsiteUrl` mapping (bds-clone/src/lib/project-website.ts). */
    fun projectWebsiteUrl(project: JSONValue): String {
        val websiteUrl = project["websiteUrl"].string.trim()
        if (websiteUrl.isNotEmpty()) return websiteUrl
        val haystack = listOf(
            project["name"].string,
            project["displayName"].string,
            project["title"].string,
            project["id"].string,
            project["code"].string
        ).joinToString(" ").lowercase()
        return when {
            haystack.contains("kim an") || haystack.contains("kim-an") || haystack.contains("futa-ka") ->
                "https://futakiman.vn/"
            haystack.contains("kim phát") || haystack.contains("kim phat") || haystack.contains("kim-phat") || haystack.contains("futa-kp") ->
                "https://futakimphat.com.vn/"
            haystack.contains("times square") || haystack.contains("times-square") || haystack.contains("times-sq") ||
                haystack.contains("residence") || haystack.contains("dnts") || haystack.contains("dts") ->
                "https://futaresidence.vn/"
            else -> "https://www.futaland.vn"
        }
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

        // 3. Nested property object
        val property = if (value["property"].isNull) value["apartment"] else value["property"]
        if (!property.isNull) {
            val propertyImg = property["image"].string.trim()
            if (propertyImg.isNotEmpty() && propertyImg != "null") return propertyImg
            val propertyBanner = property["bannerImage"].string.trim()
            if (propertyBanner.isNotEmpty() && propertyBanner != "null") return propertyBanner
            val propertyImages = property["images"].array
            if (propertyImages.isNotEmpty()) {
                for (img in propertyImages) {
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
