package com.simplemap.ui

import com.simplemap.navigation.NavigationAlternativeRoute
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationPerspectiveMode
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel

internal data class NavigationSettingsPanelState(
    val voiceGuidanceLevel: VoiceGuidanceLevel,
    val quietHoursEnabled: Boolean,
    val quietHoursStartMinutes: Int,
    val quietHoursEndMinutes: Int,
    val trafficLayerEnabled: Boolean,
    val routeAlertsEnabled: Boolean,
    val trafficBarEnabled: Boolean,
    val eagleMapEnabled: Boolean,
    val autoZoomEnabled: Boolean,
    val perspectiveMode: NavigationPerspectiveMode,
    val themeMode: NavigationThemeMode,
    val orientationMode: AppOrientationMode,
    val nightMode: Boolean,
    val isLandscape: Boolean,
    val alternativeRoutes: List<NavigationAlternativeRoute>,
)

internal sealed interface NavigationSettingsEvent {
    data class VoiceGuidanceChanged(val enabled: Boolean) : NavigationSettingsEvent
    data class VoiceGuidanceLevelChanged(val level: VoiceGuidanceLevel) : NavigationSettingsEvent
    data class QuietHoursChanged(val enabled: Boolean) : NavigationSettingsEvent
    data class TrafficLayerChanged(val enabled: Boolean) : NavigationSettingsEvent
    data class RouteAlertsChanged(val enabled: Boolean) : NavigationSettingsEvent
    data class TrafficBarChanged(val enabled: Boolean) : NavigationSettingsEvent
    data class EagleMapChanged(val enabled: Boolean) : NavigationSettingsEvent
    data class AutoZoomChanged(val enabled: Boolean) : NavigationSettingsEvent
    data class PerspectiveModeChanged(val mode: NavigationPerspectiveMode) : NavigationSettingsEvent
    data class ThemeModeChanged(val mode: NavigationThemeMode) : NavigationSettingsEvent
    data class OrientationModeChanged(val mode: AppOrientationMode) : NavigationSettingsEvent
    data class AlternativeRouteSelected(val pathId: Long) : NavigationSettingsEvent
    data object OverviewRequested : NavigationSettingsEvent
    data object Dismissed : NavigationSettingsEvent
}
