package vn.futaland.app.core.i18n

import java.text.Normalizer
import java.text.NumberFormat

/**
 * Vietnamese-dong amounts in the in-app language (port of iOS `LocalizedPrice`, mirroring the web's
 * `formatPrice`): vi `7,9 tỷ` · en `7.9B VND` · zh `78.99亿越南盾` · ru `7,9 млрд ₫` · ko `78.99억 VND`.
 */
object LocalizedPrice {
    /** Compact amount (billions / millions); below one million the amount is written out in full. */
    fun compact(value: Double, language: AppLanguage = I18n.language): String {
        when (language) {
            AppLanguage.VI -> return compactVietnamese(value)
            AppLanguage.EN -> {
                if (value >= 1e9) return decimal(value / 1e9, 2, language) + "B VND"
                if (value >= 1e6) return decimal(value / 1e6, 1, language) + "M VND"
            }
            AppLanguage.RU -> {
                if (value >= 1e9) return decimal(value / 1e9, 2, language) + " млрд ₫"
                if (value >= 1e6) return decimal(value / 1e6, 1, language) + " млн ₫"
            }
            AppLanguage.ZH -> {
                if (value >= 1e9) return decimal(value / 1e8, 2, language) + "亿越南盾"
                if (value >= 1e6) return decimal(value / 1e4, 1, language) + "万越南盾"
            }
            AppLanguage.KO -> {
                if (value >= 1e9) return decimal(value / 1e8, 2, language) + "억 VND"
                if (value >= 1e6) return decimal(value / 1e4, 1, language) + "만 VND"
            }
        }
        return full(value, language)
    }

    /** Exact amount with the currency: `7.898.758.038 đ`, `7,898,758,038 VND`, `7 898 758 038 ₫`. */
    fun full(value: Double, language: AppLanguage = I18n.language): String {
        val amount = decimal(Math.round(value).toDouble(), 0, language)
        return when (language) {
            AppLanguage.VI -> "$amount đ"
            AppLanguage.EN, AppLanguage.KO -> "$amount VND"
            AppLanguage.ZH -> "$amount 越南盾"
            AppLanguage.RU -> "$amount ₫"
        }
    }

    private fun compactVietnamese(value: Double): String {
        if (value >= 1_000_000_000) {
            val bill = value / 1_000_000_000.0
            return "%.2f".format(java.util.Locale.US, bill).replace(".00", "").replace(".", ",") + " tỷ"
        }
        if (value >= 1_000_000) {
            val mil = value / 1_000_000.0
            return "%.1f".format(java.util.Locale.US, mil).replace(".0", "").replace(".", ",") + " triệu"
        }
        return "${"%,d".format(java.util.Locale.US, value.toLong()).replace(",", ".")} đ"
    }

    private fun decimal(value: Double, maxFractionDigits: Int, language: AppLanguage): String {
        val formatter = NumberFormat.getNumberInstance(language.locale)
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = maxFractionDigits
        return formatter.format(value)
    }
}

/**
 * Compass direction (`Đông`, `dong-nam`, `tay_bac`, `southeast`…) in the in-app language; anything else
 * passes through. Uses dedicated `direction.*` keys: "Nam" also means "Male" in the shared dictionary.
 */
object LocalizedDirection {
    fun name(raw: String): String {
        val norm = Normalizer.normalize(raw.trim().lowercase().replace("đ", "d"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('_', '-')
            .replace(' ', '-')
        return when (norm) {
            "dong", "east" -> trKey("direction.east", "Đông")
            "tay", "west" -> trKey("direction.west", "Tây")
            "nam", "south" -> trKey("direction.south", "Nam")
            "bac", "north" -> trKey("direction.north", "Bắc")
            "dong-nam", "dongnam", "southeast", "south-east" -> trKey("direction.southeast", "Đông Nam")
            "dong-bac", "dongbac", "northeast", "north-east" -> trKey("direction.northeast", "Đông Bắc")
            "tay-nam", "taynam", "southwest", "south-west" -> trKey("direction.southwest", "Tây Nam")
            "tay-bac", "taybac", "northwest", "north-west" -> trKey("direction.northwest", "Tây Bắc")
            else -> raw
        }
    }
}

/** Account role shown to the user (`customer` → "Khách"), via dedicated `role.*` keys. */
object LocalizedRole {
    fun name(role: String): String = when (role) {
        "customer", "" -> trKey("role.customer", "Khách")
        "admin" -> trKey("role.admin", "Quản trị viên")
        "sale" -> trKey("role.sale", "Nhân viên kinh doanh")
        "telesale" -> trKey("role.telesale", "Telesale")
        "advisor_trainee" -> trKey("role.advisor_trainee", "Đại lý")
        else -> role
    }
}

/** Gender value (`male`/`Nam`, `female`/`Nữ`) for display, via dedicated `gender.*` keys. */
object LocalizedGender {
    fun name(raw: String): String = when (raw.trim().lowercase()) {
        "male", "nam" -> trKey("gender.male", "Nam")
        "female", "nữ", "nu" -> trKey("gender.female", "Nữ")
        "other", "khác" -> trKey("gender.other", "Khác")
        else -> raw
    }
}
