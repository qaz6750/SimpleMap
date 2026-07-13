package com.simplemap.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteFormattingTest {
    @Test
    fun duration_roundsUpToNextMinute() {
        assertEquals("1 分钟", formatRouteDuration(1))
        assertEquals("1 小时", formatRouteDuration(3_600))
        assertEquals("1 小时 2 分钟", formatRouteDuration(3_661))
    }

    @Test
    fun distance_switchesToKilometersAtOneThousandMeters() {
        assertEquals("999 米", formatRouteDistance(999))
        assertEquals("1.0 公里", formatRouteDistance(1_000))
        assertEquals("4.2 公里", formatRouteDistance(4_200))
    }
}