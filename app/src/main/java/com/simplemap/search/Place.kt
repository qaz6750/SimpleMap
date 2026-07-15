package com.simplemap.search

data class Place(
    val id: String,
    val name: String,
    val address: String,
    val district: String,
    val category: String,
    val phone: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int?,
)

data class BusLine(
    val id: String,
    val name: String,
    val originStation: String,
    val terminalStation: String,
    val stationNames: List<String>,
    val basicPriceYuan: Float?,
)

interface PlaceRepository {
    fun search(query: String, city: String = ""): Result<List<Place>>

    fun searchBusLines(query: String, city: String): Result<List<BusLine>> = Result.success(emptyList())
}

interface FavoritePlaceStore {
    fun load(): List<Place>

    fun save(place: Place): Boolean

    fun remove(placeId: String): Boolean

    fun clear(): Boolean
}