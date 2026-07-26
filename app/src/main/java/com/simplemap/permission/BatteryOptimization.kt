package com.simplemap.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

internal fun batteryOptimizationPackageUri(packageName: String): String = "package:$packageName"

internal fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

internal fun requestIgnoreBatteryOptimizationsIntent(packageName: String): Intent =
    Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse(batteryOptimizationPackageUri(packageName)),
    )

internal fun batteryOptimizationSettingsIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

internal fun Context.openBatteryOptimizationExemption() {
    try {
        startActivity(requestIgnoreBatteryOptimizationsIntent(packageName))
    } catch (_: ActivityNotFoundException) {
        startActivity(batteryOptimizationSettingsIntent())
    }
}
