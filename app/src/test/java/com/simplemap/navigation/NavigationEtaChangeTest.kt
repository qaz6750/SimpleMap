package com.simplemap.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationEtaChangeTest {
    @Test
    fun ignoresNormalCountdown() {
        assertNull(
            etaChangeMinutes(
                baselineArrivalSeconds = 10_000L,
                nowSeconds = 9_000L,
                remainingTimeSeconds = 1_001,
            ),
        )
    }

    @Test
    fun reportsDelayedArrivalAfterThreshold() {
        assertEquals(
            4,
            etaChangeMinutes(
                baselineArrivalSeconds = 10_000L,
                nowSeconds = 9_000L,
                remainingTimeSeconds = 1_181,
            ),
        )
    }

    @Test
    fun reportsEarlierArrivalAfterThreshold() {
        assertEquals(
            -3,
            etaChangeMinutes(
                baselineArrivalSeconds = 10_000L,
                nowSeconds = 9_000L,
                remainingTimeSeconds = 820,
            ),
        )
    }

    @Test
    fun keepsThreeMinuteBoundaryInclusive() {
        assertEquals(
            3,
            etaChangeMinutes(
                baselineArrivalSeconds = 10_000L,
                nowSeconds = 9_000L,
                remainingTimeSeconds = 1_180,
            ),
        )
    }
}