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
            val searchQuery = PoiSearch.Query(normalizedQuery, "", city).apply {
                pageNum = 1
                pageSize = 30
                extensions = PoiSearch.EXTENSIONS_ALL
            }
            PoiSearch(applicationContext, searchQuery).searchPOI().pois.mapNotNull { it.toPlace() }
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