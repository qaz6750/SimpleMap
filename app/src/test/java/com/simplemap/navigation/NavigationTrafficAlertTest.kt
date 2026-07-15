package com.simplemap.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTrafficAlertTest {
    @Test
    fun findsNearestTrafficAfterTravelledSegments() {
        val alert = calculateUpcomingTraffic(
            segments = listOf(
                segment(NavigationTrafficLevel.Smooth, 1_000),
                segment(NavigationTrafficLevel.Slow, 600),
            ),
            travelledDistanceMeters = 400,
        )

        assertEquals(
            NavigationTrafficAlert(NavigationTrafficLevel.Slow, 600, 600),
            alert,
        )
    }

    @Test
    fun reportsZeroDistanceAndRemainingLengthInsideTraffic() {
        val alert = calculateUpcomingTraffic(
            segments = listOf(segment(NavigationTrafficLevel.Congested, 1_000)),
            travelledDistanceMeters = 350,
        )

        assertEquals(
            NavigationTrafficAlert(NavigationTrafficLevel.Congested, 0, 650),
            alert,
        )
    }

    @Test
    fun mergesAdjacentTrafficUsingMostSevereLevel() {
        val alert = calculateUpcomingTraffic(
            segments = listOf(
                segment(NavigationTrafficLevel.Smooth, 500),
                segment(NavigationTrafficLevel.Slow, 300),
                segment(NavigationTrafficLevel.SeverelyCongested, 400),
                segment(NavigationTrafficLevel.Smooth, 800),
            ),
            travelledDistanceMeters = 200,
        )

        assertEquals(
            NavigationTrafficAlert(NavigationTrafficLevel.SeverelyCongested, 300, 700),
            alert,
        )
    }

    @Test
    fun returnsNullWhenRemainingRouteIsSmooth() {
        assertNull(
            calculateUpcomingTraffic(
                segments = listOf(
                    segment(NavigationTrafficLevel.Slow, 500),
                    segment(NavigationTrafficLevel.Smooth, 900),
                ),
                travelledDistanceMeters = 500,
            ),
        )
    }

    private fun segment(level: NavigationTrafficLevel, lengthMeters: Int) =
        NavigationTrafficSegment(level, lengthMeters)
}