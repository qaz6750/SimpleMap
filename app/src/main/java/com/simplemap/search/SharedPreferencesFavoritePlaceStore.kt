package com.simplemap.search

import android.content.Context
import com.simplemap.storage.toJsonObject
import com.simplemap.storage.toStoredPlace
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesFavoritePlaceStore(context: Context) : FavoritePlaceStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): List<Place> = loadFavorites().map(FavoritePlace::place)

    override fun loadFavorites(): List<FavoritePlace> = synchronized(LOCK) { loadUnlocked() }

    override fun save(place: Place): Boolean = synchronized(LOCK) {
        saveUnlocked(place, FavoriteGroup.Saved)
    }

    override fun save(place: Place, group: FavoriteGroup): Boolean = synchronized(LOCK) {
        saveUnlocked(place, group)
    }

    override fun setGroup(placeId: String, group: FavoriteGroup): Boolean = synchronized(LOCK) {
        val favorite = loadUnlocked().firstOrNull { it.place.id == placeId } ?: return@synchronized false
        saveUnlocked(favorite.place, group)
    }

    override fun remove(placeId: String): Boolean = synchronized(LOCK) {
        persist(loadUnlocked().filterNot { it.place.id == placeId })
    }

    override fun clear(): Boolean = synchronized(LOCK) { persist(emptyList()) }

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

    private fun FavoritePlace.toJson() = place.toJsonObject(includeDistanceMeters = true).apply {
        put("favoriteGroup", group.name)
    }

    private fun JSONObject.toFavoritePlace() = FavoritePlace(
        place = toStoredPlace(includeDistanceMeters = true),
        group = runCatching { FavoriteGroup.valueOf(optString("favoriteGroup")) }
            .getOrDefault(FavoriteGroup.Saved),
    )

    private companion object {
        val LOCK = Any()
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
