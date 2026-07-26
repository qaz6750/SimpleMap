package com.simplemap.storage

import com.simplemap.search.Place
import org.json.JSONObject

internal fun Place.toJsonObject(includeDistanceMeters: Boolean): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("address", address)
    put("district", district)
    put("category", category)
    put("phone", phone)
    put("latitude", latitude)
    put("longitude", longitude)
    if (includeDistanceMeters && distanceMeters != null) {
        put("distanceMeters", distanceMeters)
    }
}

internal fun JSONObject.toStoredPlace(includeDistanceMeters: Boolean): Place = Place(
    id = getString("id"),
    name = getString("name"),
    address = optString("address"),
    district = optString("district"),
    category = optString("category"),
    phone = optString("phone"),
    latitude = getDouble("latitude"),
    longitude = getDouble("longitude"),
    distanceMeters = if (includeDistanceMeters && has("distanceMeters")) {
        getInt("distanceMeters")
    } else {
        null
    },
)
