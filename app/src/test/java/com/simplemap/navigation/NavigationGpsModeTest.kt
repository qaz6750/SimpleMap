package com.simplemap.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGpsModeTest {
    @Test
    fun reportsUnavailableOnlyWhenGpsIsDisabled() {
        assertEquals(
            NavigationGpsMode.Unavailable,
            determineNavigationGpsMode(
                gpsEnabled = false,
                gpsSignalWeak = false,
                satelliteStatus = NavigationSatelliteStatus(visibleCount = 8, usedInFixCount = 6),
                locationDiagnostic = null,
            ),
        )
    }

    @Test
    fun combinesSdkSatelliteAndAccuracyWeakSignals() {
        assertWeak(gpsSignalWeak = true)
        assertWeak(satelliteStatus = NavigationSatelliteStatus(visibleCount = 5, usedInFixCount = 3))
        assertWeak(
            locationDiagnostic = NavigationLocationDiagnostic(
                issue = NavigationLocationIssue.LowAccuracy,
                accuracyMeters = 60,
            ),
        )
    }

    @Test
    fun waitsForSatelliteSampleBeforeReportingWeakMode() {
        assertEquals(
            NavigationGpsMode.Normal,
            determineNavigationGpsMode(
                gpsEnabled = true,
                gpsSignalWeak = false,
                satelliteStatus = NavigationSatelliteStatus(),
                locationDiagnostic = null,
            ),
        )
    }

    private fun assertWeak(
        gpsSignalWeak: Boolean = false,
        satelliteStatus: NavigationSatelliteStatus = NavigationSatelliteStatus(),
        locationDiagnostic: NavigationLocationDiagnostic? = null,
    ) {
        assertEquals(
            NavigationGpsMode.Weak,
            determineNavigationGpsMode(
                gpsEnabled = true,
                gpsSignalWeak = gpsSignalWeak,
                satelliteStatus = satelliteStatus,
                locationDiagnostic = locationDiagnostic,
            ),
        )
    }
}