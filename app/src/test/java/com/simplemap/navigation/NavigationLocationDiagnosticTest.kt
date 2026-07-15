package com.simplemap.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationLocationDiagnosticTest {
    @Test
    fun identifiesLowAccuracyAsGpsDrift() {
        assertEquals(
            NavigationLocationIssue.LowAccuracy,
            diagnoseLocation(false, accuracyMeters = 65f, consecutiveUnmatchedCount = 1)?.issue,
        )
    }

    @Test
    fun waitsForThreeAccurateUnmatchedLocations() {
        assertNull(diagnoseLocation(false, accuracyMeters = 12f, consecutiveUnmatchedCount = 2))
        assertEquals(
            NavigationLocationIssue.OffRoute,
            diagnoseLocation(false, accuracyMeters = 12f, consecutiveUnmatchedCount = 3)?.issue,
        )
    }

    @Test
    fun clearsDiagnosticWhenLocationMatchesRoute() {
        assertNull(diagnoseLocation(true, accuracyMeters = 65f, consecutiveUnmatchedCount = 4))
    }
}