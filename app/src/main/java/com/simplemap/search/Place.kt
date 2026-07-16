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

enum class FavoriteGroup(val label: String) {
    Home("家"),
    Work("公司"),
    Saved("收藏夹"),
}

data class FavoritePlace(
    val place: Place,
    val group: FavoriteGroup = FavoriteGroup.Saved,
)

interface PlaceRepository {
    fun search(query: String, city: String = ""): Result<List<Place>>

    fun searchNearby(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): Result<List<Place>> = search(query)

    fun searchBusLines(query: String, city: String): Result<List<BusLine>> = Result.success(emptyList())
}

interface FavoritePlaceStore {
    fun load(): List<Place>

    fun loadFavorites(): List<FavoritePlace> = load().map(::FavoritePlace)

    fun save(place: Place): Boolean

    fun save(place: Place, group: FavoriteGroup): Boolean = save(place)

    fun setGroup(placeId: String, group: FavoriteGroup): Boolean {
        val place = load().firstOrNull { it.id == placeId } ?: return false
        return save(place, group)
    }

    fun remove(placeId: String): Boolean

    fun clear(): Boolean
}