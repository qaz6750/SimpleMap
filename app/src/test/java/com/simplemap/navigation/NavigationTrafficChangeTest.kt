package com.simplemap.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTrafficChangeTest {
    @Test
    fun announcesNewAndWorseningCongestion() {
        val congested = traffic(NavigationTrafficLevel.Congested)
        val severe = traffic(NavigationTrafficLevel.SeverelyCongested)

        assertEquals("前方路况变为拥堵", trafficChangeMessage(null, congested))
        assertEquals("前方路况变为严重拥堵", trafficChangeMessage(congested, severe))
    }

    @Test
    fun announcesCongestionRelief() {
        assertEquals(
            "前方拥堵已缓解",
            trafficChangeMessage(traffic(NavigationTrafficLevel.SeverelyCongested), null),
        )
        assertEquals(
            "前方拥堵已缓解",
            trafficChangeMessage(
                traffic(NavigationTrafficLevel.Congested),
                traffic(NavigationTrafficLevel.Slow),
            ),
        )
    }

    @Test
    fun ignoresDistanceOnlyUpdates() {
        val congested = traffic(NavigationTrafficLevel.Congested)

        assertNull(trafficChangeMessage(congested, congested.copy(distanceMeters = 200)))
    }

    private fun traffic(level: NavigationTrafficLevel) = NavigationTrafficAlert(level, 500, 1_000)
}