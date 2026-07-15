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

interface PlaceRepository {
    fun search(query: String, city: String = ""): Result<List<Place>>
}

interface FavoritePlaceStore {
    fun load(): List<Place>

    fun save(place: Place): Boolean

    fun remove(placeId: String): Boolean

    fun clear(): Boolean
}