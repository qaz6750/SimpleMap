package com.simplemap.ui

import androidx.compose.runtime.Immutable
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationUiState

@Immutable
internal data class NavigationGuidanceState(
    val phase: NavigationPhase,
    val instruction: String,
    val nextRoad: String,
    val maneuverIconType: Int,
    val maneuverDistanceMeters: Int,
)

@Immutable
internal data class NavigationTripSummaryState(
    val remainingDistanceMeters: Int,
    val remainingTimeSeconds: Int,
    val remainingTrafficLights: Int,
)

internal fun NavigationUiState.toGuidanceState() = NavigationGuidanceState(
    phase = phase,
    instruction = instruction,
    nextRoad = nextRoad,
    maneuverIconType = maneuverIconType,
    maneuverDistanceMeters = maneuverDistanceMeters,
)

internal fun NavigationUiState.toTripSummaryState() = NavigationTripSummaryState(
    remainingDistanceMeters = remainingDistanceMeters,
    remainingTimeSeconds = remainingTimeSeconds,
    remainingTrafficLights = remainingTrafficLights,
)
