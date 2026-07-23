package com.simplemap.navigation

data class NavigationTrafficIncident(
    val title: String,
    val typeLabel: String,
    val distanceMeters: Int,
    val latitude: Double,
    val longitude: Double,
)

internal data class NavigationCoordinate(
    val latitude: Double,
    val longitude: Double,
)

internal fun calculateIncidentRouteDistance(
    route: List<NavigationCoordinate>,
    incident: NavigationCoordinate,
    routeLengthMeters: Int,
): Int? {
    if (route.size < 2 || routeLengthMeters <= 0) return null
    var cumulativeDistance = 0.0
    var nearestRouteDistance = 0.0
    var nearestDistance = Double.MAX_VALUE
    route.zipWithNext().forEach { (start, end) ->
        val segmentLength = start.distanceTo(end)
        val projection = incident.projectOnto(start, end)
        val distance = projection.distanceMeters
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestRouteDistance = cumulativeDistance + segmentLength * projection.fraction
        }
        cumulativeDistance += segmentLength
    }
    if (nearestDistance > MAX_INCIDENT_ROUTE_DISTANCE_METERS) return null
    val polylineLength = cumulativeDistance.takeIf { it > 0.0 } ?: return null
    return (nearestRouteDistance / polylineLength * routeLengthMeters).toInt()
}

private data class SegmentProjection(
    val fraction: Double,
    val distanceMeters: Double,
)

private fun NavigationCoordinate.projectOnto(
    start: NavigationCoordinate,
    end: NavigationCoordinate,
): SegmentProjection {
    val referenceLatitude = Math.toRadians((latitude + start.latitude + end.latitude) / 3.0)
    fun NavigationCoordinate.toLocalMeters() = Pair(
        Math.toRadians(longitude) * EARTH_RADIUS_METERS * kotlin.math.cos(referenceLatitude),
        Math.toRadians(latitude) * EARTH_RADIUS_METERS,
    )
    val (pointX, pointY) = toLocalMeters()
    val (startX, startY) = start.toLocalMeters()
    val (endX, endY) = end.toLocalMeters()
    val segmentX = endX - startX
    val segmentY = endY - startY
    val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
    val fraction = if (segmentLengthSquared == 0.0) {
        0.0
    } else {
        (((pointX - startX) * segmentX + (pointY - startY) * segmentY) / segmentLengthSquared)
            .coerceIn(0.0, 1.0)
    }
    val projectedX = startX + segmentX * fraction
    val projectedY = startY + segmentY * fraction
    return SegmentProjection(
        fraction = fraction,
        distanceMeters = kotlin.math.hypot(pointX - projectedX, pointY - projectedY),
    )
}

internal fun calculateIncidentDistance(
    route: List<NavigationCoordinate>,
    incident: NavigationCoordinate,
    travelledDistanceMeters: Int,
    routeLengthMeters: Int,
): Int? {
    val incidentRouteDistance = calculateIncidentRouteDistance(route, incident, routeLengthMeters) ?: return null
    return (incidentRouteDistance - travelledDistanceMeters.coerceAtLeast(0))
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

internal fun formatIncidentDistance(distanceMeters: Int): String =
    if (distanceMeters < 1_000) {
        "前方 ${distanceMeters.coerceAtLeast(0)} 米"
    } else {
        "前方 %.1f 公里".format(distanceMeters / 1_000f)
    }

internal fun NavigationTrafficIncident.sameNodeAs(other: NavigationTrafficIncident): Boolean =
    title == other.title &&
        typeLabel == other.typeLabel &&
        latitude == other.latitude &&
        longitude == other.longitude

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val MAX_INCIDENT_ROUTE_DISTANCE_METERS = 500.0