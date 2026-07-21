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
            var places = emptyList<Place>()
            for (keyword in fuzzyAddressQueries(normalizedQuery)) {
                val searchQuery = PoiSearch.Query(keyword, "", city).apply {
                    pageNum = 1
                    pageSize = 30
                    extensions = PoiSearch.EXTENSIONS_ALL
                }
                places = PoiSearch(applicationContext, searchQuery).searchPOI().pois.mapNotNull { it.toPlace() }
                if (places.isNotEmpty()) break
            }
            places.distinctBy(Place::id).take(30)
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
        PoiSearch(applicationContext, searchQuery).apply {
            setBound(
                PoiSearch.SearchBound(
                    LatLonPoint(latitude, longitude),
                    radiusMeters.coerceIn(100, 50_000),
                    true,
                ),
            )
        }.searchPOI().pois.mapNotNull { it.toPlace() }
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

private val roadSuffixes = listOf("路", "街", "道", "巷", "大道", "胡同", "弄")

internal fun fuzzyAddressQueries(query: String): List<String> {
    val normalized = query.trim().replace(Regex("[，,；;]+"), " ").replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return emptyList()
    val compact = normalized.replace(" ", "")
    val districtBoundary = compact.indexOfLast { it == '省' || it == '市' || it == '区' || it == '县' }
    val localAddress = compact.substring(districtBoundary + 1).takeIf { it.length >= 3 }

    // 关键词拆分：按空格/常见分隔符拆分为多个关键词，逐个尝试
    val keywordCandidates = normalized.split(Regex("[\\s/\\\\、·]+"))
        .map { it.trim() }
        .filter { it.length >= 2 }

    // 前缀匹配：从完整字符串逐步缩短前缀，便于用户输入不完整地址时也能匹配
    val prefixCandidates = mutableListOf<String>()
    if (compact.length > 4) {
        var len = compact.length - 1
        while (len >= 4) {
            prefixCandidates.add(compact.substring(0, len))
            len -= 2
        }
    }

    // 去除常见路名后缀：如"路""街""道""巷"等，便于模糊匹配道路名称的核心部分
    val suffixTrimmed = roadSuffixes.firstNotNullOfOrNull { suffix ->
        if (compact.endsWith(suffix) && compact.length > suffix.length + 1) {
            compact.removeSuffix(suffix)
        } else {
            null
        }
    }

    // 拼音首字母风格的部分匹配：拆分中文字符，尝试相邻字符组合，扩大模糊匹配范围
    val charSplitCandidates = if (compact.length in 4..8) {
        val chars = compact.toList()
        (2 until chars.size).mapNotNull { window ->
            if (window < chars.size) chars.subList(0, window).joinToString("") else null
        }
    } else {
        emptyList()
    }

    return listOfNotNull(
        normalized,
        compact.takeIf { it != normalized },
        localAddress,
        suffixTrimmed,
    )
        .plus(keywordCandidates)
        .plus(prefixCandidates)
        .plus(charSplitCandidates)
        .filter { it.isNotBlank() }
        .distinct()
}