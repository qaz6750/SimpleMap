package com.simplemap.settings

import android.content.Context
import com.simplemap.route.DriveRouteOptions

enum class NavigationThemeMode(val label: String) {
    FollowSystem("跟随系统"),
    Automatic("按时间自动"),
    Day("始终日间"),
    Night("始终夜间"),
}

enum class VoiceGuidanceLevel(val label: String) {
    Detailed("详细播报"),
    Concise("简洁播报"),
    Muted("静音"),
}

enum class NavigationPerspectiveMode(val label: String, val tiltDegrees: Int) {
    TwoDimensional("2D", 0),
    ThreeDimensional("3D", 45),
}

data class NavigationSettings(
    val voiceGuidance: Boolean = true,
    val voiceGuidanceLevel: VoiceGuidanceLevel = VoiceGuidanceLevel.Detailed,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinutes: Int = 22 * 60,
    val quietHoursEndMinutes: Int = 7 * 60,
    val importantAlertsEnabled: Boolean = true,
    val trafficLayer: Boolean = true,
    val routeAlerts: Boolean = true,
    val trafficBar: Boolean = true,
    val eagleMap: Boolean = false,
    val autoZoom: Boolean = true,
    val perspectiveMode: NavigationPerspectiveMode = NavigationPerspectiveMode.ThreeDimensional,
    val nightMode: Boolean = false,
    val themeMode: NavigationThemeMode = NavigationThemeMode.FollowSystem,
    val wifiOnlyOfflineDownloads: Boolean = true,
    val driveRouteOptions: DriveRouteOptions = DriveRouteOptions(),
) {
    val resolvedVoiceGuidanceLevel: VoiceGuidanceLevel
        get() = if (voiceGuidance) voiceGuidanceLevel else VoiceGuidanceLevel.Muted
}

internal fun NavigationSettings.withVoiceGuidanceLevel(
    level: VoiceGuidanceLevel,
): NavigationSettings = copy(
    voiceGuidance = level != VoiceGuidanceLevel.Muted,
    voiceGuidanceLevel = level,
)

internal fun shouldUseNightTheme(
    mode: NavigationThemeMode,
    systemInDarkTheme: Boolean,
    minuteOfDay: Int,
    inTunnel: Boolean,
): Boolean = when (mode) {
    NavigationThemeMode.FollowSystem -> inTunnel || systemInDarkTheme
    NavigationThemeMode.Automatic -> inTunnel || minuteOfDay !in 6 * 60 until 19 * 60
    NavigationThemeMode.Day -> false
    NavigationThemeMode.Night -> true
}

internal fun isQuietHoursActive(
    enabled: Boolean,
    startMinutes: Int,
    endMinutes: Int,
    minuteOfDay: Int,
): Boolean {
    if (!enabled) return false
    val start = startMinutes.coerceIn(0, 24 * 60 - 1)
    val end = endMinutes.coerceIn(0, 24 * 60 - 1)
    val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return when {
        start == end -> true
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }
}

internal fun shouldPlayNavigationAlert(
    voiceLevel: VoiceGuidanceLevel,
    quietHoursActive: Boolean,
    important: Boolean,
    importantAlertsEnabled: Boolean,
): Boolean {
    if (voiceLevel == VoiceGuidanceLevel.Muted) return false
    if (important && !importantAlertsEnabled) return false
    return !quietHoursActive || important
}

interface NavigationSettingsStore {
    fun load(): NavigationSettings
    fun save(settings: NavigationSettings): Boolean
}

class SharedPreferencesNavigationSettingsStore(context: Context) : NavigationSettingsStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): NavigationSettings {
        val legacyVoiceGuidance = preferences.getBoolean(KEY_VOICE, true)
        val legacyNightMode = preferences.getBoolean(KEY_NIGHT_MODE, false)
        return NavigationSettings(
            voiceGuidance = legacyVoiceGuidance,
            voiceGuidanceLevel = preferences.enumValue(
                KEY_VOICE_LEVEL,
                if (legacyVoiceGuidance) VoiceGuidanceLevel.Detailed else VoiceGuidanceLevel.Muted,
            ),
            quietHoursEnabled = preferences.getBoolean(KEY_QUIET_HOURS_ENABLED, false),
            quietHoursStartMinutes = preferences.getInt(KEY_QUIET_HOURS_START, 22 * 60),
            quietHoursEndMinutes = preferences.getInt(KEY_QUIET_HOURS_END, 7 * 60),
            importantAlertsEnabled = preferences.getBoolean(KEY_IMPORTANT_ALERTS, true),
            trafficLayer = preferences.getBoolean(KEY_TRAFFIC, true),
            routeAlerts = preferences.getBoolean(KEY_ROUTE_ALERTS, true),
            trafficBar = preferences.getBoolean(KEY_TRAFFIC_BAR, true),
            eagleMap = preferences.getBoolean(KEY_EAGLE_MAP, false),
            autoZoom = preferences.getBoolean(KEY_AUTO_ZOOM, true),
            perspectiveMode = preferences.enumValue(KEY_PERSPECTIVE_MODE, NavigationPerspectiveMode.ThreeDimensional),
            nightMode = legacyNightMode,
            themeMode = preferences.enumValue(
                KEY_THEME_MODE,
                if (legacyNightMode) NavigationThemeMode.Night else NavigationThemeMode.FollowSystem,
            ),
            wifiOnlyOfflineDownloads = preferences.getBoolean(KEY_WIFI_ONLY_OFFLINE, true),
            driveRouteOptions = DriveRouteOptions(
                avoidCongestion = preferences.getBoolean(KEY_AVOID_CONGESTION, false),
                avoidHighway = preferences.getBoolean(KEY_AVOID_HIGHWAY, false),
                saveMoney = preferences.getBoolean(KEY_SAVE_MONEY, false),
                prioritizeHighway = preferences.getBoolean(KEY_PRIORITIZE_HIGHWAY, false),
            ),
        )
    }

    override fun save(settings: NavigationSettings): Boolean = preferences.edit()
        .putBoolean(KEY_VOICE, settings.resolvedVoiceGuidanceLevel != VoiceGuidanceLevel.Muted)
        .putString(KEY_VOICE_LEVEL, settings.resolvedVoiceGuidanceLevel.name)
        .putBoolean(KEY_QUIET_HOURS_ENABLED, settings.quietHoursEnabled)
        .putInt(KEY_QUIET_HOURS_START, settings.quietHoursStartMinutes)
        .putInt(KEY_QUIET_HOURS_END, settings.quietHoursEndMinutes)
        .putBoolean(KEY_IMPORTANT_ALERTS, settings.importantAlertsEnabled)
        .putBoolean(KEY_TRAFFIC, settings.trafficLayer)
        .putBoolean(KEY_ROUTE_ALERTS, settings.routeAlerts)
        .putBoolean(KEY_TRAFFIC_BAR, settings.trafficBar)
        .putBoolean(KEY_EAGLE_MAP, settings.eagleMap)
        .putBoolean(KEY_AUTO_ZOOM, settings.autoZoom)
        .putString(KEY_PERSPECTIVE_MODE, settings.perspectiveMode.name)
        .putBoolean(KEY_NIGHT_MODE, settings.themeMode == NavigationThemeMode.Night)
        .putString(KEY_THEME_MODE, settings.themeMode.name)
        .putBoolean(KEY_WIFI_ONLY_OFFLINE, settings.wifiOnlyOfflineDownloads)
        .putBoolean(KEY_AVOID_CONGESTION, settings.driveRouteOptions.avoidCongestion)
        .putBoolean(KEY_AVOID_HIGHWAY, settings.driveRouteOptions.avoidHighway)
        .putBoolean(KEY_SAVE_MONEY, settings.driveRouteOptions.saveMoney)
        .putBoolean(KEY_PRIORITIZE_HIGHWAY, settings.driveRouteOptions.prioritizeHighway)
        .commit()

    private companion object {
        const val FILE_NAME = "navigation_settings"
        const val KEY_VOICE = "voice_guidance"
        const val KEY_VOICE_LEVEL = "voice_guidance_level"
        const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_HOURS_START = "quiet_hours_start_minutes"
        const val KEY_QUIET_HOURS_END = "quiet_hours_end_minutes"
        const val KEY_IMPORTANT_ALERTS = "important_alerts"
        const val KEY_TRAFFIC = "traffic_layer"
        const val KEY_ROUTE_ALERTS = "route_alerts"
        const val KEY_TRAFFIC_BAR = "traffic_bar"
        const val KEY_EAGLE_MAP = "eagle_map"
        const val KEY_AUTO_ZOOM = "auto_zoom"
        const val KEY_PERSPECTIVE_MODE = "perspective_mode"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_WIFI_ONLY_OFFLINE = "wifi_only_offline_downloads"
        const val KEY_AVOID_CONGESTION = "drive_avoid_congestion"
        const val KEY_AVOID_HIGHWAY = "drive_avoid_highway"
        const val KEY_SAVE_MONEY = "drive_save_money"
        const val KEY_PRIORITIZE_HIGHWAY = "drive_prioritize_highway"
    }
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
    key: String,
    default: T,
): T = getString(key, null)?.let { stored ->
    enumValues<T>().firstOrNull { it.name == stored }
} ?: default