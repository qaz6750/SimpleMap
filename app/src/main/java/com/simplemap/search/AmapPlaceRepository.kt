package com.simplemap.search

import android.content.Context
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
            PoiSearch(applicationContext, searchQuery)
                .searchPOI()
                .pois
                .mapNotNull { item ->
                    val point = item.latLonPoint ?: return@mapNotNull null
                    Place(
                        id = item.poiId.orEmpty().ifBlank { "${point.latitude},${point.longitude}" },
                        name = item.title.orEmpty().ifBlank { "未命名地点" },
                        address = item.snippet.orEmpty(),
                        district = listOfNotNull(item.cityName, item.adName)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" · "),
                        category = item.typeDes.orEmpty(),
                        phone = item.tel.orEmpty(),
                        latitude = point.latitude,
                        longitude = point.longitude,
                        distanceMeters = item.distance.takeIf { it >= 0 },
                    )
                }
        }
    }
}