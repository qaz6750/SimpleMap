package com.simplemap.navigation

import android.graphics.Bitmap

enum class NavigationPhase {
    Preparing,
    Calculating,
    Navigating,
    Arrived,
    Failed,
}

enum class NavigationFacilityKind {
    ServiceArea,
    TollGate,
}

enum class NavigationTrafficLevel {
    Smooth,
    Slow,
    Congested,
    SeverelyCongested,
    Unknown,
}

enum class NavigationLaneDirection(val label: String, val symbol: String) {
    Ahead("直行", "↑"),
    Left("左转", "←"),
    Right("右转", "→"),
    AheadLeft("直行或左转", "↑/←"),
    AheadRight("直行或右转", "↑/→"),
    LeftRight("左转或右转", "←/→"),
    AheadLeftRight("直行、左转或右转", "←/↑/→"),
    AheadUTurn("直行或掉头", "↑/↶"),
    UTurn("掉头", "↶"),
    Other("按道路标线行驶", "·"),
}

data class NavigationLane(
    val direction: NavigationLaneDirection,
    val recommended: Boolean,
)

data class NavigationTrafficSegment(
    val level: NavigationTrafficLevel,
    val lengthMeters: Int,
)

data class NavigationTrafficAlert(
    val level: NavigationTrafficLevel,
    val distanceMeters: Int,
    val affectedLengthMeters: Int,
)

fun calculateUpcomingTraffic(
    segments: List<NavigationTrafficSegment>,
    travelledDistanceMeters: Int,
): NavigationTrafficAlert? {
    val travelledDistance = travelledDistanceMeters.coerceAtLeast(0)
    var segmentStart = 0
    segments.forEachIndexed { index, segment ->
        val segmentLength = segment.lengthMeters.coerceAtLeast(0)
        val segmentEnd = segmentStart + segmentLength
        if (segment.level.isSlowerThanSmooth && segmentEnd > travelledDistance) {
            var level = segment.level
            var affectedLength = segmentEnd - maxOf(segmentStart, travelledDistance)
            var nextIndex = index + 1
            while (nextIndex < segments.size && segments[nextIndex].level.isSlowerThanSmooth) {
                val nextSegment = segments[nextIndex]
                affectedLength += nextSegment.lengthMeters.coerceAtLeast(0)
                if (nextSegment.level.severity > level.severity) level = nextSegment.level
                nextIndex += 1
            }
            return NavigationTrafficAlert(
                level = level,
                distanceMeters = (segmentStart - travelledDistance).coerceAtLeast(0),
                affectedLengthMeters = affectedLength,
            )
        }
        segmentStart = segmentEnd
    }
    return null
}

private val NavigationTrafficLevel.isSlowerThanSmooth: Boolean
    get() = this == NavigationTrafficLevel.Slow ||
        this == NavigationTrafficLevel.Congested ||
        this == NavigationTrafficLevel.SeverelyCongested

private val NavigationTrafficLevel.severity: Int
    get() = when (this) {
        NavigationTrafficLevel.SeverelyCongested -> 3
        NavigationTrafficLevel.Congested -> 2
        NavigationTrafficLevel.Slow -> 1
        NavigationTrafficLevel.Smooth, NavigationTrafficLevel.Unknown -> 0
    }

data class NavigationRouteFacility(
    val name: String,
    val distanceMeters: Int,
    val remainingTimeSeconds: Int,
    val kind: NavigationFacilityKind = NavigationFacilityKind.ServiceArea,
    val routeDistanceMeters: Int? = null,
)

fun advanceRouteFacilities(
    facilities: List<NavigationRouteFacility>,
    travelledDistanceMeters: Int,
): List<NavigationRouteFacility> = facilities
    .map { facility ->
        if (facility.routeDistanceMeters == null) {
            facility
        } else {
            facility.copy(
                distanceMeters = (facility.distanceMeters - travelledDistanceMeters.coerceAtLeast(0))
                    .coerceAtLeast(0),
            )
        }
    }
    .filter { facility -> facility.routeDistanceMeters == null || facility.distanceMeters > 0 }
    .sortedBy(NavigationRouteFacility::distanceMeters)

data class NavigationSatelliteStatus(
    val visibleCount: Int = 0,
    val usedInFixCount: Int = 0,
    val averageCn0DbHz: Float = 0f,
    val systems: Map<String, Int> = emptyMap(),
)

enum class NavigationGpsMode {
    Normal,
    Weak,
    Unavailable,
}

internal fun determineNavigationGpsMode(
    gpsEnabled: Boolean,
    gpsSignalWeak: Boolean,
    satelliteStatus: NavigationSatelliteStatus,
    locationDiagnostic: NavigationLocationDiagnostic?,
): NavigationGpsMode = when {
    !gpsEnabled -> NavigationGpsMode.Unavailable
    gpsSignalWeak -> NavigationGpsMode.Weak
    locationDiagnostic?.issue == NavigationLocationIssue.LowAccuracy -> NavigationGpsMode.Weak
    satelliteStatus.visibleCount > 0 && satelliteStatus.usedInFixCount < MIN_SATELLITES_FOR_FIX -> {
        NavigationGpsMode.Weak
    }
    else -> NavigationGpsMode.Normal
}

private const val MIN_SATELLITES_FOR_FIX = 4

data class NavigationRouteNotice(
    val id: Long,
    val title: String,
    val detail: String = "",
    val distanceMeters: Int? = null,
    val important: Boolean = false,
)

data class NavigationUiState(
    val phase: NavigationPhase = NavigationPhase.Preparing,
    val instruction: String = "正在准备导航",
    val currentRoad: String = "",
    val inTunnel: Boolean = false,
    val nextRoad: String = "",
    val highwayExit: String = "",
    val maneuverIconType: Int = 0,
    val maneuverIconBitmap: Bitmap? = null,
    val junctionViewBitmap: Bitmap? = null,
    val lanes: List<NavigationLane> = emptyList(),
    val maneuverDistanceMeters: Int = 0,
    val remainingDistanceMeters: Int = 0,
    val remainingTimeSeconds: Int = 0,
    val currentSpeedKmh: Int = 0,
    val speedLimitKmh: Int? = null,
    val cameraType: Int? = null,
    val cameraDistanceMeters: Int? = null,
    val intervalAverageSpeedKmh: Int? = null,
    val intervalRemainingMeters: Int? = null,
    val intervalRecommendedSpeedKmh: Int? = null,
    val routeFacilities: List<NavigationRouteFacility> = emptyList(),
    val trafficAlert: NavigationTrafficAlert? = null,
    val trafficIncident: NavigationTrafficIncident? = null,
    val remainingTrafficLights: Int = 0,
    val gpsEnabled: Boolean = true,
    val gpsSignalWeak: Boolean = false,
    val satelliteStatus: NavigationSatelliteStatus = NavigationSatelliteStatus(),
    val locationDiagnostic: NavigationLocationDiagnostic? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val alternativeRoutes: List<NavigationAlternativeRoute> = emptyList(),
    val routeNotice: NavigationRouteNotice? = null,
    val message: String? = null,
)

internal fun isTunnelRoad(roadName: String): Boolean {
    val normalized = roadName.trim()
    return normalized.contains("隧道") || normalized.contains("地道")
}