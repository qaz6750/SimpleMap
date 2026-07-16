package com.simplemap.trips

import android.content.Context
import com.simplemap.search.Place

interface ParkingLocationStore {
    fun load(): Place?
    fun save(place: Place): Boolean
    fun clear(): Boolean
}

class SharedPreferencesParkingLocationStore(context: Context) : ParkingLocationStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): Place? {
        if (!preferences.contains(KEY_LATITUDE) || !preferences.contains(KEY_LONGITUDE)) return null
        return Place(
            id = PARKING_PLACE_ID,
            name = "停车位置",
            address = "上次手动保存的位置",
            district = "",
            category = "停车",
            phone = "",
            latitude = Double.fromBits(preferences.getLong(KEY_LATITUDE, 0L)),
            longitude = Double.fromBits(preferences.getLong(KEY_LONGITUDE, 0L)),
            distanceMeters = null,
        )
    }

    override fun save(place: Place): Boolean = preferences.edit()
        .putLong(KEY_LATITUDE, place.latitude.toBits())
        .putLong(KEY_LONGITUDE, place.longitude.toBits())
        .putLong(KEY_SAVED_AT, System.currentTimeMillis())
        .commit()

    override fun clear(): Boolean = preferences.edit().clear().commit()

    private companion object {
        const val FILE_NAME = "parking_location"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_SAVED_AT = "saved_at"
        const val PARKING_PLACE_ID = "saved-parking-location"
    }
}