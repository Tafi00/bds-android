package vn.futaland.app.features.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.core.network.JSONValue

enum class HomePropertySegment(val id: String, val title: String) {
    ALL("all", "Tất cả"),
    APARTMENT("apartment", "Căn hộ"),
    TOWNHOUSE("townhouse", "Nhà phố"),
    UNDER_8B("under8B", "Dưới 8 tỷ"),
    SELLING("selling", "Đang mở bán")
}

data class FeaturedCity(
    val id: String,
    val name: String,
    val image: String,
    val projectCount: Int,
    val averagePrice: String,
    val growth: String
)

class HomeViewModel : ViewModel() {

    private val _projects = MutableStateFlow<List<JSONValue>>(emptyList())
    val projects = _projects.asStateFlow()

    private val _allApartments = MutableStateFlow<List<JSONValue>>(emptyList())
    val allApartments = _allApartments.asStateFlow()

    private val _featuredApartments = MutableStateFlow<List<JSONValue>>(emptyList())
    val featuredApartments = _featuredApartments.asStateFlow()

    private val _cmsSettings = MutableStateFlow(JSONValue.EmptyObject)
    val cmsSettings = _cmsSettings.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _selectedCity = MutableStateFlow("Tất cả")
    val selectedCity = _selectedCity.asStateFlow()

    private val _selectedSegment = MutableStateFlow(HomePropertySegment.ALL)
    val selectedSegment = _selectedSegment.asStateFlow()

    val availableCities: List<String>
        get() {
            val list = mutableListOf("Tất cả")
            val pCities = _projects.value.map { it["province"].string }.filter { it.isNotEmpty() }
            val cCities = _cmsSettings.value.valueAt("systemConfig.homepageContent.featuredCities.items").array
                .map { it["name"].string }.filter { it.isNotEmpty() }
            val combined = (pCities + cCities).distinct().sorted()
            list.addAll(if (combined.isEmpty()) listOf("Đà Nẵng", "TP. Hồ Chí Minh", "Khánh Hòa", "Bến Tre") else combined)
            return list
        }

    val heroProjects: List<JSONValue>
        get() {
            val pool = if (_selectedCity.value == "Tất cả") {
                _projects.value
            } else {
                _projects.value.filter {
                    it["province"].string.contains(_selectedCity.value, ignoreCase = true) ||
                    it["location"].string.contains(_selectedCity.value, ignoreCase = true)
                }
            }
            return (if (pool.isEmpty()) _projects.value else pool).take(5)
        }

    val secondaryProjects: List<JSONValue>
        get() {
            val all = _projects.value
            if (all.isEmpty()) return emptyList()
            if (all.size == 1) return all

            if (_selectedCity.value != "Tất cả") {
                val cityMatches = all.filter {
                    val p = it["province"].string + " " + it["location"].string + " " + it["address"].string
                    p.contains(_selectedCity.value, ignoreCase = true)
                }
                if (cityMatches.size > 1) {
                    return cityMatches.drop(1)
                } else if (cityMatches.isNotEmpty()) {
                    return cityMatches + all.filter { it.id != cityMatches[0].id }
                }
            }
            return all.drop(1)
        }

    val featuredCityItems: List<FeaturedCity>
        get() {
            val configured = _cmsSettings.value.valueAt("systemConfig.homepageContent.featuredCities.items").array
            if (configured.isNotEmpty()) {
                return configured.mapNotNull { item ->
                    val name = item["name"].string
                    if (name.isEmpty()) return@mapNotNull null
                    val rawImg = item["image"].string
                    val imgUrl = if (rawImg.startsWith("http://") || rawImg.startsWith("https://")) rawImg
                        else "https://bds.futaland.vn${if (rawImg.startsWith("/")) rawImg else "/$rawImg"}"
                    val count = item["projectCount"].int.takeIf { it > 0 } ?: countProjects(name)
                    val price = item["averagePrice"].string.ifEmpty { "65 triệu/m²" }
                    val growth = item["growth"].string.ifEmpty { "+8.5%/năm" }
                    FeaturedCity(
                        id = item["id"].string.ifEmpty { name },
                        name = name,
                        image = imgUrl,
                        projectCount = count,
                        averagePrice = price,
                        growth = growth
                    )
                }
            }

            return listOf(
                FeaturedCity("hcm", "Hồ Chí Minh", "https://bds.futaland.vn/images/futa/city-hcm-cms-clean-v2.webp", 3, "65 triệu/m²", "+12%/năm"),
                FeaturedCity("danang", "Huế", "https://bds.futaland.vn/images/futa/city-hue-cms-clean-v2.webp", 3, "65 triệu/m²", "Tiềm năng"),
                FeaturedCity("hanoi", "Đà Nẵng", "https://bds.futaland.vn/images/futa/city-danang-cms-clean-v2.webp", 3, "65 triệu/m²", "+9%/năm"),
                FeaturedCity("nhatrang", "Nha Trang", "https://bds.futaland.vn/images/futa/city-nhatrang-cms-clean-v2.webp", 3, "65 triệu/m²", "Thu hút")
            )
        }

    private fun countProjects(cityName: String): Int {
        val c = _projects.value.count {
            val p = it["province"].string + " " + it["location"].string
            p.contains(cityName, ignoreCase = true)
        }
        return maxOf(c, 3)
    }
    val filteredApartments: List<JSONValue>
        get() {
            var res = _allApartments.value
            if (_selectedCity.value != "Tất cả") {
                val cityMatches = res.filter {
                    (it["address"].string + " " + it["zone"].string).contains(_selectedCity.value, ignoreCase = true)
                }
                if (cityMatches.isNotEmpty()) res = cityMatches
            }

            return when (_selectedSegment.value) {
                HomePropertySegment.ALL -> res
                HomePropertySegment.APARTMENT -> res.filter {
                    val text = (it["propertyType"].string + " " + it["title"].string).lowercase()
                    text.contains("can-ho") || text.contains("chung-cu") || text.contains("căn hộ")
                }
                HomePropertySegment.TOWNHOUSE -> res.filter {
                    val text = (it["propertyType"].string + " " + it["title"].string).lowercase()
                    text.contains("nha-pho") || text.contains("biet-thu") || text.contains("nhà phố")
                }
                HomePropertySegment.UNDER_8B -> res.filter {
                    val price = it["price"].double
                    price in 1.0..8_000_000_000.0
                }
                HomePropertySegment.SELLING -> res.filter {
                    it["status"].string.equals("available", ignoreCase = true) ||
                    it["status"].string.equals("selling", ignoreCase = true) ||
                    it["status"].string.equals("active", ignoreCase = true)
                }
            }
        }

    fun selectCity(city: String) {
        _selectedCity.value = city
    }

    fun selectSegment(segment: HomePropertySegment) {
        _selectedSegment.value = segment
    }

    fun loadData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val pDeferred = async { APIClient.get().request("/projects") }
                val aDeferred = async { APIClient.get().request("/apartments", query = mapOf("limit" to "24", "showOnHome" to "true")) }
                val fDeferred = async {
                    try {
                        APIClient.get().request("/apartments", query = mapOf("limit" to "12", "isFavorite" to "true", "sort" to "newest"))
                    } catch (_: Exception) {
                        JSONValue.EmptyObject
                    }
                }
                val sDeferred = async { APIClient.get().request("/cms/settings") }

                val pRes = pDeferred.await()
                val aRes = aDeferred.await()
                val fRes = fDeferred.await()
                val sRes = sDeferred.await()

                _cmsSettings.value = sRes["data"]
                val allNonHidden = pRes["data"].array.filter { !it["hidden"].bool }
                val homeOnly = allNonHidden.filter { it["showOnHome"].bool }
                val rawProjects = if (homeOnly.size >= 4) homeOnly else allNonHidden
                _projects.value = rawProjects.sortedBy { it["homeOrder"].int }
                val apartmentsList = aRes["data"].array
                _allApartments.value = apartmentsList
                val featList = fRes["data"].array
                _featuredApartments.value = if (featList.isNotEmpty()) {
                    featList
                } else {
                    val favs = apartmentsList.filter { it["isFavorite"].bool }
                    if (favs.isNotEmpty()) favs else apartmentsList.take(8)
                }
            } catch (_: Exception) {
            } finally {
                _loading.value = false
            }
        }
    }
    fun toggleFavorite(apartmentId: String) {
        viewModelScope.launch {
            try {
                APIClient.get().request("/favorites/$apartmentId", method = "POST")
            } catch (_: Exception) {}
        }
    }
}
