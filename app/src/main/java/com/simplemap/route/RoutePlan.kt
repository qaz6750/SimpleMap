package com.simplemap.route

import com.simplemap.search.Place

enum class RouteMode(val label: String) {
    Drive("驾车"),
    Transit("公交"),
    Ride("骑行"),
    Walk("步行"),
}

data class DriveRouteOptions(
    val avoidCongestion: Boolean = false,
    val avoidHighway: Boolean = false,
    val saveMoney: Boolean = false,
    val prioritizeHighway: Boolean = false,
)

enum class DriveRoutePreset(val label: String) {
    Recommended("推荐"),
    Commute("通勤"),
    HighwayFirst("高速优先"),
    ElectricEco("新能源省电"),
}

fun DriveRoutePreset.toOptions(): DriveRouteOptions = when (this) {
    DriveRoutePreset.Recommended -> DriveRouteOptions()
    DriveRoutePreset.Commute -> DriveRouteOptions(avoidCongestion = true)
    DriveRoutePreset.HighwayFirst -> DriveRouteOptions(prioritizeHighway = true)
    DriveRoutePreset.ElectricEco -> DriveRouteOptions(
        avoidCongestion = true,
        avoidHighway = true,
        saveMoney = true,
    )
}

fun DriveRouteOptions.matchingPreset(): DriveRoutePreset? =
    DriveRoutePreset.entries.firstOrNull { it.toOptions() == this }

data class RouteRequest(
    val origin: Place,
    val destination: Place,
    val waypoints: List<Place> = emptyList(),
    val mode: RouteMode = RouteMode.Drive,
    val driveOptions: DriveRouteOptions = DriveRouteOptions(),
    val city: String = "",
    val originCity: String = "",
    val destinationCity: String = "",
) {
    val resolvedOriginCity: String
        get() = originCity.ifBlank { city }

    val resolvedDestinationCity: String
        get() = destinationCity.ifBlank { city.ifBlank { resolvedOriginCity } }
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
)

enum class RouteTrafficStatus {
    Smooth,
    Slow,
    Congested,
    SeverelyCongested,
    Unknown;

    companion object {
        fun fromAmap(status: String): RouteTrafficStatus = when (status.trim()) {
            "畅通" -> Smooth
            "缓行" -> Slow
            "拥堵" -> Congested
            "严重拥堵" -> SeverelyCongested
            else -> Unknown
        }
    }
}

data class RouteTrafficSegment(
    val status: RouteTrafficStatus,
    val polyline: List<RoutePoint>,
)

data class RoutePlan(
    val id: String,
    val mode: RouteMode,
    val durationSeconds: Long,
    val distanceMeters: Int,
    val costYuan: Float?,
    val summary: String,
    val steps: List<String>,
    val polyline: List<RoutePoint>,
    val trafficSegments: List<RouteTrafficSegment> = emptyList(),
)

interface RoutePlanRepository {
    fun plan(request: RouteRequest): Result<List<RoutePlan>>
}