package com.simplemap.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

internal data class LocationPermissionAccess(
    val coarseLocation: Boolean,
    val fineLocation: Boolean,
) {
    val canShowLocation: Boolean
        get() = coarseLocation || fineLocation

    val canNavigate: Boolean
        get() = fineLocation
}

internal fun Context.locationPermissionAccess(): LocationPermissionAccess =
    LocationPermissionAccess(
        coarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
        fineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
    )

internal fun Map<String, Boolean>.locationPermissionAccess(): LocationPermissionAccess =
    LocationPermissionAccess(
        coarseLocation = this[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        fineLocation = this[Manifest.permission.ACCESS_FINE_LOCATION] == true,
    )

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
