package com.simplemap.trips

import android.content.Context
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RouteRequest
import com.simplemap.navigation.NavigationPhase
import com.simplemap.search.Place
import com.simplemap.storage.toJsonObject
import com.simplemap.storage.toStoredPlace
import org.json.JSONArray
import org.json.JSONObject

data class TripRecord(
    val id: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val origin: Place,
    val destination: Place,
    val mode: RouteMode,
    val durationSeconds: Long,
    val distanceMeters: Int,
    val status: TripStatus,
    val simulated: Boolean,
)

enum class TripStatus {
    Arrived,
    Cancelled,
    Failed,
}

fun createTripRecord(
    startedAtMillis: Long,
    completedAtMillis: Long,
    request: RouteRequest,
    plan: RoutePlan,
    phase: NavigationPhase,
    remainingDistanceMeters: Int,
    simulated: Boolean,
): TripRecord {
    val elapsedSeconds = ((completedAtMillis - startedAtMillis) / 1_000L).coerceAtLeast(1L)
    val travelledDistance = if (phase == NavigationPhase.Arrived) {
        plan.distanceMeters
    } else {
        (plan.distanceMeters - remainingDistanceMeters).coerceIn(0, plan.distanceMeters)
    }
    return TripRecord(
        id = "$startedAtMillis-${request.origin.id}-${request.destination.id}",
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        origin = request.origin,
        destination = request.destination,
        mode = plan.mode,
        durationSeconds = elapsedSeconds,
        distanceMeters = travelledDistance,
        status = when (phase) {
            NavigationPhase.Arrived -> TripStatus.Arrived
            NavigationPhase.Failed -> TripStatus.Failed
            else -> TripStatus.Cancelled
        },
        simulated = simulated,
    )
}

interface TripHistoryStore {
    fun load(): List<TripRecord>
    fun add(record: TripRecord): Boolean
    fun remove(tripId: String): Boolean
    fun clear(): Boolean
}

class SharedPreferencesTripHistoryStore(context: Context) : TripHistoryStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): List<TripRecord> = synchronized(LOCK) { loadUnlocked() }

    override fun add(record: TripRecord): Boolean = synchronized(LOCK) {
        persist((listOf(record) + loadUnlocked().filterNot { it.id == record.id }).take(MAX_TRIPS))
    }

    override fun remove(tripId: String): Boolean = synchronized(LOCK) {
        persist(loadUnlocked().filterNot { it.id == tripId })
    }

    override fun clear(): Boolean = synchronized(LOCK) { persist(emptyList()) }

    private fun loadUnlocked(): List<TripRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_TRIPS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                runCatching { array.getJSONObject(index).toTrip() }
                    .getOrNull()
                    ?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun persist(records: List<TripRecord>): Boolean {
        val array = JSONArray().apply { records.forEach { put(it.toJson()) } }
        return preferences.edit().putString(KEY_TRIPS, array.toString()).commit()
    }

    private fun TripRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("startedAtMillis", startedAtMillis)
        put("completedAtMillis", completedAtMillis)
        put("origin", origin.toJsonObject(includeDistanceMeters = false))
        put("destination", destination.toJsonObject(includeDistanceMeters = false))
        put("mode", mode.name)
        put("durationSeconds", durationSeconds)
        put("distanceMeters", distanceMeters)
        put("status", status.name)
        put("simulated", simulated)
    }

    private fun JSONObject.toTrip() = TripRecord(
        id = getString("id"),
        startedAtMillis = getLong("startedAtMillis"),
        completedAtMillis = optLong("completedAtMillis", getLong("startedAtMillis")),
        origin = getJSONObject("origin").toStoredPlace(includeDistanceMeters = false),
        destination = getJSONObject("destination").toStoredPlace(includeDistanceMeters = false),
        mode = runCatching { RouteMode.valueOf(getString("mode")) }.getOrDefault(RouteMode.Drive),
        durationSeconds = getLong("durationSeconds"),
        distanceMeters = getInt("distanceMeters"),
        status = runCatching { TripStatus.valueOf(optString("status")) }.getOrDefault(TripStatus.Arrived),
        simulated = optBoolean("simulated", false),
    )

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "trip_history"
        const val KEY_TRIPS = "trips"
        const val MAX_TRIPS = 50
    }
}
