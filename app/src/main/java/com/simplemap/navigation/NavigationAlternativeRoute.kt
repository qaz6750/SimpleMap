package com.simplemap.navigation

import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePoint
import com.simplemap.route.RouteRequest
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NavigationAlternativeRoute(
    val pathId: Long,
    val label: String,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val tollCostYuan: Int,
    val selected: Boolean,
)

internal data class NavigationPathCandidate(
    val pathId: Long,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val polyline: List<RoutePoint>,
)

internal fun findMatchingNavigationPath(
    plan: RoutePlan,
    candidates: List<NavigationPathCandidate>,
): Long? {
    val distanceTolerance = maxOf(800, (plan.distanceMeters * 0.1f).toInt())
    val durationTolerance = maxOf(600L, (plan.durationSeconds * 0.2f).toLong())
    return candidates
        .asSequence()
        .filter { candidate ->
            abs(candidate.distanceMeters - plan.distanceMeters) <= distanceTolerance &&
                abs(candidate.durationSeconds.toLong() - plan.durationSeconds) <= durationTolerance
        }
        .map { candidate ->
            val distanceError = abs(candidate.distanceMeters - plan.distanceMeters).toDouble() /
                plan.distanceMeters.coerceAtLeast(1)
            val durationError = abs(candidate.durationSeconds.toLong() - plan.durationSeconds).toDouble() /
                plan.durationSeconds.coerceAtLeast(1L)
            val shapeError = averageRouteSeparationMeters(plan.polyline, candidate.polyline)
            candidate.pathId to distanceError * 2.0 + durationError + shapeError / 500.0
        }
        .minByOrNull { (_, score) -> score }
        ?.takeIf { (_, score) -> score <= 1.0 }
        ?.first
}

private fun averageRouteSeparationMeters(
    expected: List<RoutePoint>,
    candidate: List<RoutePoint>,
): Double {
    if (expected.size < 2 || candidate.size < 2) return 0.0
    val expectedSamples = expected.evenlySampled(maxCount = 16)
    val candidateSamples = candidate.evenlySampled(maxCount = 64)
    return expectedSamples
        .map { point -> candidateSamples.minOf { haversineMeters(point, it) } }
        .average()
}

internal fun List<RoutePoint>.evenlySampled(maxCount: Int): List<RoutePoint> {
    if (size <= maxCount) return this
    return List(maxCount) { index ->
        this[index * (lastIndex) / (maxCount - 1)]
    }
}

private fun haversineMeters(first: RoutePoint, second: RoutePoint): Double {
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val firstLatitude = Math.toRadians(first.latitude)
    val secondLatitude = Math.toRadians(second.latitude)
    val value = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(firstLatitude) * cos(secondLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(value.coerceIn(0.0, 1.0)))
}

internal fun supportsMultipleRouteNavigation(
    request: RouteRequest,
    simulated: Boolean,
    directDistanceMeters: Float,
): Boolean = request.mode == RouteMode.Drive &&
    !simulated &&
    request.waypoints.isEmpty() &&
    directDistanceMeters <= MAX_MULTIPLE_ROUTE_DISTANCE_METERS

private const val MAX_MULTIPLE_ROUTE_DISTANCE_METERS = 80_000f
private const val EARTH_RADIUS_METERS = 6_371_000.0