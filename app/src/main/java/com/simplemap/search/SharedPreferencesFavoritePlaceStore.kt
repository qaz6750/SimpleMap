package com.simplemap.search

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesFavoritePlaceStore(context: Context) : FavoritePlaceStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun load(): List<Place> = loadFavorites().map(FavoritePlace::place)

    override fun loadFavorites(): List<FavoritePlace> = synchronized(lock) { loadUnlocked() }

    override fun save(place: Place): Boolean = synchronized(lock) {
        saveUnlocked(place, FavoriteGroup.Saved)
    }

    override fun save(place: Place, group: FavoriteGroup): Boolean = synchronized(lock) {
        saveUnlocked(place, group)
    }

    override fun setGroup(placeId: String, group: FavoriteGroup): Boolean = synchronized(lock) {
        val favorite = loadUnlocked().firstOrNull { it.place.id == placeId } ?: return@synchronized false
        saveUnlocked(favorite.place, group)
    }

    override fun remove(placeId: String): Boolean = synchronized(lock) {
        persist(loadUnlocked().filterNot { it.place.id == placeId })
    }

    override fun clear(): Boolean = synchronized(lock) { persist(emptyList()) }

    private fun loadUnlocked(): List<FavoritePlace> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PLACES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                runCatching { array.getJSONObject(index).toFavoritePlace() }
                    .getOrNull()
                    ?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun saveUnlocked(place: Place, group: FavoriteGroup): Boolean {
        return persist(updateFavoriteGroup(loadUnlocked(), place, group))
    }

    private fun persist(favorites: List<FavoritePlace>): Boolean {
        val array = JSONArray().apply {
            favorites.forEach { favorite -> put(favorite.toJson()) }
        }
        return preferences.edit().putString(KEY_PLACES, array.toString()).commit()
    }

    private fun FavoritePlace.toJson() = JSONObject().apply {
        val place = place
        put("id", place.id)
        put("name", place.name)
        put("address", place.address)
        put("district", place.district)
        put("category", place.category)
        put("phone", place.phone)
        put("latitude", place.latitude)
        put("longitude", place.longitude)
        if (place.distanceMeters != null) put("distanceMeters", place.distanceMeters)
        put("favoriteGroup", group.name)
    }

    private fun JSONObject.toFavoritePlace() = FavoritePlace(
        place = Place(
            id = getString("id"),
            name = getString("name"),
            address = optString("address"),
            district = optString("district"),
            category = optString("category"),
            phone = optString("phone"),
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            distanceMeters = if (has("distanceMeters")) getInt("distanceMeters") else null,
        ),
        group = runCatching { FavoriteGroup.valueOf(optString("favoriteGroup")) }
            .getOrDefault(FavoriteGroup.Saved),
    )

    private companion object {
        const val FILE_NAME = "favorite_places"
        const val KEY_PLACES = "places"
    }
}

internal fun updateFavoriteGroup(
    favorites: List<FavoritePlace>,
    place: Place,
    group: FavoriteGroup,
): List<FavoritePlace> = favorites.filterNot { favorite ->
    favorite.place.id == place.id ||
        (group != FavoriteGroup.Saved && favorite.group == group)
} + FavoritePlace(place, group)
