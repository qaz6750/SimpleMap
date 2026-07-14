package com.simplemap.navigation

import android.graphics.Bitmap

enum class NavigationPhase {
    Preparing,
    Calculating,
    Navigating,
    Arrived,
    Failed,
}

data class NavigationServiceArea(
    val name: String,
    val distanceMeters: Int,
    val remainingTimeSeconds: Int,
)

data class NavigationSatelliteStatus(
    val visibleCount: Int = 0,
    val usedInFixCount: Int = 0,
    val averageCn0DbHz: Float = 0f,
    val systems: Map<String, Int> = emptyMap(),
)

data class NavigationUiState(
    val phase: NavigationPhase = NavigationPhase.Preparing,
    val instruction: String = "正在准备导航",
    val currentRoad: String = "",
    val nextRoad: String = "",
    val maneuverIconType: Int = 0,
    val maneuverIconBitmap: Bitmap? = null,
    val maneuverDistanceMeters: Int = 0,
    val remainingDistanceMeters: Int = 0,
    val remainingTimeSeconds: Int = 0,
    val currentSpeedKmh: Int = 0,
    val speedLimitKmh: Int? = null,
    val cameraDistanceMeters: Int? = null,
    val intervalAverageSpeedKmh: Int? = null,
    val intervalRemainingMeters: Int? = null,
    val serviceAreas: List<NavigationServiceArea> = emptyList(),
    val remainingTrafficLights: Int = 0,
    val gpsAvailable: Boolean = true,
    val satelliteStatus: NavigationSatelliteStatus = NavigationSatelliteStatus(),
    val message: String? = null,
)