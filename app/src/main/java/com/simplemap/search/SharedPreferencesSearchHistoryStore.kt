package com.simplemap.search

import android.content.Context
import com.simplemap.storage.toJsonObject
import com.simplemap.storage.toStoredPlace
import org.json.JSONArray

class SharedPreferencesSearchHistoryStore(context: Context) : SearchHistoryStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): List<Place> = synchronized(LOCK) { loadUnlocked() }

    override fun record(place: Place): Boolean = synchronized(LOCK) {
        persist(appendSearchHistory(loadUnlocked(), place.copy(distanceMeters = null)))
    }

    override fun remove(placeId: String): Boolean = synchronized(LOCK) {
        persist(loadUnlocked().filterNot { it.id == placeId })
    }

    override fun clear(): Boolean = synchronized(LOCK) { persist(emptyList()) }

    private fun loadUnlocked(): List<Place> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PLACES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                runCatching { array.getJSONObject(index).toStoredPlace(includeDistanceMeters = false) }
                    .getOrNull()
                    ?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun persist(places: List<Place>): Boolean {
        val array = JSONArray().apply {
            places.forEach { place -> put(place.toJsonObject(includeDistanceMeters = false)) }
        }
        return preferences.edit().putString(KEY_PLACES, array.toString()).commit()
    }

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "search_history"
        const val KEY_PLACES = "places"
    }
}
