package com.simplemap.search

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesFavoritePlaceStore(context: Context) : FavoritePlaceStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun load(): List<Place> = synchronized(lock) { loadUnlocked() }

    override fun save(place: Place): Boolean = synchronized(lock) {
        val places = loadUnlocked().filterNot { it.id == place.id } + place
        persist(places)
    }

    override fun remove(placeId: String): Boolean = synchronized(lock) {
        persist(loadUnlocked().filterNot { it.id == placeId })
    }

    override fun clear(): Boolean = synchronized(lock) { persist(emptyList()) }

    private fun loadUnlocked(): List<Place> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PLACES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                runCatching { array.getJSONObject(index).toPlace() }
                    .getOrNull()
                    ?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun persist(places: List<Place>): Boolean {
        val array = JSONArray().apply {
            places.forEach { place -> put(place.toJson()) }
        }
        return preferences.edit().putString(KEY_PLACES, array.toString()).commit()
    }

    private fun Place.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("address", address)
        put("district", district)
        put("category", category)
        put("phone", phone)
        put("latitude", latitude)
        put("longitude", longitude)
        if (distanceMeters != null) put("distanceMeters", distanceMeters)
    }

    private fun JSONObject.toPlace() = Place(
        id = getString("id"),
        name = getString("name"),
        address = optString("address"),
        district = optString("district"),
        category = optString("category"),
        phone = optString("phone"),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        distanceMeters = if (has("distanceMeters")) getInt("distanceMeters") else null,
    )

    private companion object {
        const val FILE_NAME = "favorite_places"
        const val KEY_PLACES = "places"
    }
}