package com.simplemap.settings

import android.content.Context

data class NavigationSettings(
    val voiceGuidance: Boolean = true,
    val trafficLayer: Boolean = true,
    val routeAlerts: Boolean = true,
    val trafficBar: Boolean = true,
    val eagleMap: Boolean = false,
    val autoZoom: Boolean = true,
    val nightMode: Boolean = false,
    val wifiOnlyOfflineDownloads: Boolean = true,
)

interface NavigationSettingsStore {
    fun load(): NavigationSettings
    fun save(settings: NavigationSettings): Boolean
}

class SharedPreferencesNavigationSettingsStore(context: Context) : NavigationSettingsStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load() = NavigationSettings(
        voiceGuidance = preferences.getBoolean(KEY_VOICE, true),
        trafficLayer = preferences.getBoolean(KEY_TRAFFIC, true),
        routeAlerts = preferences.getBoolean(KEY_ROUTE_ALERTS, true),
        trafficBar = preferences.getBoolean(KEY_TRAFFIC_BAR, true),
        eagleMap = preferences.getBoolean(KEY_EAGLE_MAP, false),
        autoZoom = preferences.getBoolean(KEY_AUTO_ZOOM, true),
        nightMode = preferences.getBoolean(KEY_NIGHT_MODE, false),
        wifiOnlyOfflineDownloads = preferences.getBoolean(KEY_WIFI_ONLY_OFFLINE, true),
    )

    override fun save(settings: NavigationSettings): Boolean = preferences.edit()
        .putBoolean(KEY_VOICE, settings.voiceGuidance)
        .putBoolean(KEY_TRAFFIC, settings.trafficLayer)
        .putBoolean(KEY_ROUTE_ALERTS, settings.routeAlerts)
        .putBoolean(KEY_TRAFFIC_BAR, settings.trafficBar)
        .putBoolean(KEY_EAGLE_MAP, settings.eagleMap)
        .putBoolean(KEY_AUTO_ZOOM, settings.autoZoom)
        .putBoolean(KEY_NIGHT_MODE, settings.nightMode)
        .putBoolean(KEY_WIFI_ONLY_OFFLINE, settings.wifiOnlyOfflineDownloads)
        .commit()

    private companion object {
        const val FILE_NAME = "navigation_settings"
        const val KEY_VOICE = "voice_guidance"
        const val KEY_TRAFFIC = "traffic_layer"
        const val KEY_ROUTE_ALERTS = "route_alerts"
        const val KEY_TRAFFIC_BAR = "traffic_bar"
        const val KEY_EAGLE_MAP = "eagle_map"
        const val KEY_AUTO_ZOOM = "auto_zoom"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_WIFI_ONLY_OFFLINE = "wifi_only_offline_downloads"
    }
}