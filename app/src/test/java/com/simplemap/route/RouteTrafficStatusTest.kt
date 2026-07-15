package com.simplemap.route

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTrafficStatusTest {
    @Test
    fun fromAmap_mapsKnownTrafficStates() {
        assertEquals(RouteTrafficStatus.Smooth, RouteTrafficStatus.fromAmap("畅通"))
        assertEquals(RouteTrafficStatus.Slow, RouteTrafficStatus.fromAmap("缓行"))
        assertEquals(RouteTrafficStatus.Congested, RouteTrafficStatus.fromAmap("拥堵"))
        assertEquals(RouteTrafficStatus.SeverelyCongested, RouteTrafficStatus.fromAmap("严重拥堵"))
        assertEquals(RouteTrafficStatus.Unknown, RouteTrafficStatus.fromAmap("未知"))
    }
}