package com.simplemap.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSessionDisplayTest {
    @Test
    fun simulationDoesNotWaitForServiceSession() {
        assertTrue(canShowNavigation(simulated = true, sessionReady = false))
    }

    @Test
    fun liveNavigationWaitsForServiceSession() {
        assertFalse(canShowNavigation(simulated = false, sessionReady = false))
        assertTrue(canShowNavigation(simulated = false, sessionReady = true))
    }
}
