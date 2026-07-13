package com.simplemap.route

import com.simplemap.search.Place

enum class RouteMode(val label: String) {
    Drive("驾车"),
    Transit("公交"),
    Ride("骑行"),
    Walk("步行"),
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
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
)

interface RoutePlanRepository {
    fun plan(
        origin: Place,
        destination: Place,
        mode: RouteMode,
        city: String = "",
    ): Result<List<RoutePlan>>
}