package com.simplemap.navigation

import com.simplemap.route.RouteMode
import com.simplemap.route.RouteRequest

data class NavigationAlternativeRoute(
    val pathId: Long,
    val label: String,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val tollCostYuan: Int,
    val selected: Boolean,
)

internal fun supportsMultipleRouteNavigation(
    request: RouteRequest,
    simulated: Boolean,
    directDistanceMeters: Float,
): Boolean = request.mode == RouteMode.Drive &&
    !simulated &&
    request.waypoints.isEmpty() &&
    directDistanceMeters <= MAX_MULTIPLE_ROUTE_DISTANCE_METERS

private const val MAX_MULTIPLE_ROUTE_DISTANCE_METERS = 80_000f