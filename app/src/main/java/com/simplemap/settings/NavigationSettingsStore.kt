package com.simplemap.settings

import android.content.Context

data class NavigationSettings(
    val voiceGuidance: Boolean = true,
    val trafficLayer: Boolean = true,
    val routeAlerts: Boolean = true,
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
    )

    override fun save(settings: NavigationSettings): Boolean = preferences.edit()
        .putBoolean(KEY_VOICE, settings.voiceGuidance)
        .putBoolean(KEY_TRAFFIC, settings.trafficLayer)
        .putBoolean(KEY_ROUTE_ALERTS, settings.routeAlerts)
        .commit()

    private companion object {
        const val FILE_NAME = "navigation_settings"
        const val KEY_VOICE = "voice_guidance"
        const val KEY_TRAFFIC = "traffic_layer"
        const val KEY_ROUTE_ALERTS = "route_alerts"
    }
}