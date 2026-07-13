package com.simplemap.trips

import android.content.Context
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.search.Place
import org.json.JSONArray
import org.json.JSONObject

data class TripRecord(
    val id: String,
    val startedAtMillis: Long,
    val origin: Place,
    val destination: Place,
    val mode: RouteMode,
    val durationSeconds: Long,
    val distanceMeters: Int,
)

interface TripHistoryStore {
    fun load(): List<TripRecord>
    fun add(origin: Place, destination: Place, plan: RoutePlan): Boolean
    fun clear(): Boolean
}

class SharedPreferencesTripHistoryStore(context: Context) : TripHistoryStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): List<TripRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_TRIPS, "[]"))
        buildList {
            for (index in 0 until array.length()) add(array.getJSONObject(index).toTrip())
        }
    }.getOrDefault(emptyList())

    override fun add(origin: Place, destination: Place, plan: RoutePlan): Boolean {
        val now = System.currentTimeMillis()
        val record = TripRecord(
            id = "$now-${origin.id}-${destination.id}",
            startedAtMillis = now,
            origin = origin,
            destination = destination,
            mode = plan.mode,
            durationSeconds = plan.durationSeconds,
            distanceMeters = plan.distanceMeters,
        )
        return persist((listOf(record) + load()).take(MAX_TRIPS))
    }

    override fun clear(): Boolean = persist(emptyList())

    private fun persist(records: List<TripRecord>): Boolean {
        val array = JSONArray().apply { records.forEach { put(it.toJson()) } }
        return preferences.edit().putString(KEY_TRIPS, array.toString()).commit()
    }

    private fun TripRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("startedAtMillis", startedAtMillis)
        put("origin", origin.toJson())
        put("destination", destination.toJson())
        put("mode", mode.name)
        put("durationSeconds", durationSeconds)
        put("distanceMeters", distanceMeters)
    }

    private fun JSONObject.toTrip() = TripRecord(
        id = getString("id"),
        startedAtMillis = getLong("startedAtMillis"),
        origin = getJSONObject("origin").toPlace(),
        destination = getJSONObject("destination").toPlace(),
        mode = runCatching { RouteMode.valueOf(getString("mode")) }.getOrDefault(RouteMode.Drive),
        durationSeconds = getLong("durationSeconds"),
        distanceMeters = getInt("distanceMeters"),
    )

    private fun Place.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("address", address)
        put("district", district)
        put("category", category)
        put("phone", phone)
        put("latitude", latitude)
        put("longitude", longitude)
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
        distanceMeters = null,
    )

    private companion object {
        const val FILE_NAME = "trip_history"
        const val KEY_TRIPS = "trips"
        const val MAX_TRIPS = 50
    }
}