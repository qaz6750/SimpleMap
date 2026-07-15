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
    val nextRoad: String = "",
    val highwayExit: String = "",
    val maneuverIconType: Int = 0,
    val maneuverIconBitmap: Bitmap? = null,
    val junctionViewBitmap: Bitmap? = null,
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
    val remainingTrafficLights: Int = 0,
    val gpsAvailable: Boolean = true,
    val satelliteStatus: NavigationSatelliteStatus = NavigationSatelliteStatus(),
    val routeNotice: NavigationRouteNotice? = null,
    val message: String? = null,
)