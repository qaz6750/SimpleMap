package com.simplemap.navigation

data class NavigationTrafficIncident(
    val title: String,
    val typeLabel: String,
    val distanceMeters: Int,
)

internal data class NavigationCoordinate(
    val latitude: Double,
    val longitude: Double,
)

internal fun calculateIncidentDistance(
    route: List<NavigationCoordinate>,
    incident: NavigationCoordinate,
    travelledDistanceMeters: Int,
    routeLengthMeters: Int,
): Int? {
    if (route.size < 2 || routeLengthMeters <= 0) return null
    var cumulativeDistance = 0.0
    var nearestRouteDistance = 0.0
    var nearestDistance = Double.MAX_VALUE
    route.forEachIndexed { index, point ->
        if (index > 0) cumulativeDistance += route[index - 1].distanceTo(point)
        val distance = point.distanceTo(incident)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestRouteDistance = cumulativeDistance
        }
    }
    if (nearestDistance > MAX_INCIDENT_ROUTE_DISTANCE_METERS) return null
    val polylineLength = cumulativeDistance.takeIf { it > 0.0 } ?: return null
    val incidentRouteDistance = nearestRouteDistance / polylineLength * routeLengthMeters
    return (incidentRouteDistance.toInt() - travelledDistanceMeters.coerceAtLeast(0))
        .takeIf { it >= 0 }
}

private fun NavigationCoordinate.distanceTo(other: NavigationCoordinate): Double {
    val latitudeDelta = Math.toRadians(other.latitude - latitude)
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val firstLatitude = Math.toRadians(latitude)
    val secondLatitude = Math.toRadians(other.latitude)
    val haversine = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
        kotlin.math.cos(firstLatitude) * kotlin.math.cos(secondLatitude) *
        kotlin.math.sin(longitudeDelta / 2).let { it * it }
    return EARTH_RADIUS_METERS * 2 *
        kotlin.math.atan2(kotlin.math.sqrt(haversine), kotlin.math.sqrt(1 - haversine))
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val MAX_INCIDENT_ROUTE_DISTANCE_METERS = 500.0