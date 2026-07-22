package com.simplemap.search

import android.content.Context
import com.amap.api.services.busline.BusLineQuery
import com.amap.api.services.busline.BusLineSearch
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.poisearch.PoiSearch

class AmapPlaceRepository(context: Context) : PlaceRepository {
    private val applicationContext = context.applicationContext

    override fun search(query: String, city: String): Result<List<Place>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return Result.success(emptyList())

        return runCatching {
            val places = fuzzyAddressQueries(normalizedQuery).flatMap { keyword ->
                val searchQuery = PoiSearch.Query(keyword, "", city).apply {
                    pageNum = 1
                    pageSize = 30
                    extensions = PoiSearch.EXTENSIONS_ALL
                }
                PoiSearch(applicationContext, searchQuery).searchPOI().pois.mapNotNull { it.toPlace() }
            }
            rankFuzzyPlaces(normalizedQuery, places)
                .filterNot(::isBusStationPlace)
                .take(30)
        }
    }

    override fun searchNearby(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): Result<List<Place>> = runCatching {
        val searchQuery = PoiSearch.Query(query.trim(), "", "").apply {
            pageNum = 1
            pageSize = 30
            extensions = PoiSearch.EXTENSIONS_ALL
        }
        val places = PoiSearch(applicationContext, searchQuery).apply {
            setBound(
                PoiSearch.SearchBound(
                    LatLonPoint(latitude, longitude),
                    radiusMeters.coerceIn(100, 50_000),
                    true,
                ),
            )
        }.searchPOI().pois.mapNotNull { it.toPlace() }
        rankFuzzyPlaces(query, places)
            .filterNot(::isBusStationPlace)
            .take(30)
    }

    override fun searchBusLines(query: String, city: String): Result<List<BusLine>> {
        val normalizedQuery = query.trim()
        val normalizedCity = city.trim()
        if (normalizedQuery.isEmpty() || normalizedCity.isEmpty()) return Result.success(emptyList())

        return runCatching {
            val busQuery = BusLineQuery(
                normalizedQuery,
                BusLineQuery.SearchType.BY_LINE_NAME,
                normalizedCity,
            ).apply {
                pageNumber = 1
                pageSize = 10
                extensions = BusLineSearch.EXTENSIONS_ALL
            }
            BusLineSearch(applicationContext, busQuery)
                .searchBusLine()
                .busLines
                .map { line ->
                    BusLine(
                        id = line.busLineId.orEmpty().ifBlank { line.busLineName.orEmpty() },
                        name = line.busLineName.orEmpty().ifBlank { "未命名公交线路" },
                        originStation = line.originatingStation.orEmpty(),
                        terminalStation = line.terminalStation.orEmpty(),
                        stationNames = line.busStations.mapNotNull { station ->
                            station.busStationName?.takeIf(String::isNotBlank)
                        },
                        basicPriceYuan = line.basicPrice.takeIf { it >= 0f },
                    )
                }
        }
    }

    private fun com.amap.api.services.core.PoiItem.toPlace(): Place? {
        val point = latLonPoint ?: return null
        return Place(
            id = poiId.orEmpty().ifBlank { "${point.latitude},${point.longitude}" },
            name = title.orEmpty().ifBlank { "未命名地点" },
            address = snippet.orEmpty(),
            district = listOfNotNull(cityName, adName)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · "),
            category = typeDes.orEmpty(),
            phone = tel.orEmpty(),
            latitude = point.latitude,
            longitude = point.longitude,
            distanceMeters = distance.takeIf { it >= 0 },
        )
    }
}

internal fun fuzzyAddressQueries(query: String): List<String> {
    val normalized = query.trim().replace(Regex("[，,；;]+"), " ").replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return emptyList()
    val compact = normalized.replace(" ", "")
    val districtBoundary = compact.indexOfLast { it == '省' || it == '市' || it == '区' || it == '县' }
    val localAddress = compact.substring(districtBoundary + 1).takeIf { it.length >= 3 }

    return listOfNotNull(
        normalized,
        compact.takeIf { it != normalized },
        localAddress,
    )
        .distinct()
}

internal fun rankFuzzyPlaces(query: String, places: List<Place>): List<Place> {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isEmpty()) return emptyList()
    return places
        .distinctBy(Place::id)
        .map { place -> place to fuzzyPlaceScore(normalizedQuery, place) }
        .filter { (_, score) -> score < NO_FUZZY_MATCH }
        .sortedWith(
            compareBy<Pair<Place, Int>> { it.second }
                .thenBy { it.first.distanceMeters ?: Int.MAX_VALUE }
                .thenBy { it.first.name.length },
        )
        .map(Pair<Place, Int>::first)
}

internal fun isBusStationPlace(place: Place): Boolean {
    val category = normalizeSearchText(place.category)
    val name = normalizeSearchText(place.name)
    return category.contains("公交车站") ||
        category.contains("公交站") ||
        (category.contains("交通设施") && name.endsWith("公交站"))
}

private fun fuzzyPlaceScore(query: String, place: Place): Int {
    val name = normalizeSearchText(place.name)
    val address = normalizeSearchText(listOf(place.address, place.district).joinToString(""))
    return when {
        name == query -> 0
        name.startsWith(query) -> 10 + name.length - query.length
        name.contains(query) -> 30 + name.indexOf(query)
        address.contains(query) -> 60 + address.indexOf(query)
        query.isOrderedSubsequenceOf(name) -> 100 + name.length - query.length
        query.isOrderedSubsequenceOf(address) -> 140 + address.length - query.length
        else -> NO_FUZZY_MATCH
    }
}

private fun normalizeSearchText(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("[\\s，,；;·._-]+"), "")

private fun String.isOrderedSubsequenceOf(target: String): Boolean {
    if (isEmpty()) return true
    var queryIndex = 0
    target.forEach { character ->
        if (character == this[queryIndex]) {
            queryIndex += 1
            if (queryIndex == length) return true
        }
    }
    return false
}

private const val NO_FUZZY_MATCH = Int.MAX_VALUE
