package com.simplemap.navigation

enum class NavigationLocationIssue {
    LowAccuracy,
    OffRoute,
}

data class NavigationLocationDiagnostic(
    val issue: NavigationLocationIssue,
    val accuracyMeters: Int,
)

internal fun diagnoseLocation(
    matchedToRoute: Boolean,
    accuracyMeters: Float,
    consecutiveUnmatchedCount: Int,
): NavigationLocationDiagnostic? = when {
    matchedToRoute -> null
    accuracyMeters > LOW_ACCURACY_THRESHOLD_METERS -> NavigationLocationDiagnostic(
        issue = NavigationLocationIssue.LowAccuracy,
        accuracyMeters = accuracyMeters.toInt().coerceAtLeast(0),
    )
    consecutiveUnmatchedCount >= OFF_ROUTE_CONFIRMATION_COUNT -> NavigationLocationDiagnostic(
        issue = NavigationLocationIssue.OffRoute,
        accuracyMeters = accuracyMeters.toInt().coerceAtLeast(0),
    )
    else -> null
}

private const val LOW_ACCURACY_THRESHOLD_METERS = 40f
private const val OFF_ROUTE_CONFIRMATION_COUNT = 3