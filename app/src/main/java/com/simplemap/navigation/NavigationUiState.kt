package com.simplemap.navigation

enum class NavigationPhase {
    Preparing,
    Calculating,
    Navigating,
    Arrived,
    Failed,
}

data class NavigationUiState(
    val phase: NavigationPhase = NavigationPhase.Preparing,
    val instruction: String = "正在准备导航",
    val currentRoad: String = "",
    val nextRoad: String = "",
    val maneuverIconType: Int = 0,
    val maneuverDistanceMeters: Int = 0,
    val remainingDistanceMeters: Int = 0,
    val remainingTimeSeconds: Int = 0,
    val currentSpeedKmh: Int = 0,
    val speedLimitKmh: Int? = null,
    val cameraDistanceMeters: Int? = null,
    val intervalAverageSpeedKmh: Int? = null,
    val intervalRemainingMeters: Int? = null,
    val serviceAreaName: String? = null,
    val serviceAreaDistanceMeters: Int? = null,
    val remainingTrafficLights: Int = 0,
    val gpsAvailable: Boolean = true,
    val message: String? = null,
)