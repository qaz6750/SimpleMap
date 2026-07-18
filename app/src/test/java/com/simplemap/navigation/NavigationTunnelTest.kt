package com.simplemap.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTunnelTest {
    @Test
    fun detectsChineseTunnelRoadNames() {
        assertTrue(isTunnelRoad("紫之隧道"))
        assertTrue(isTunnelRoad("延安东路地道"))
        assertFalse(isTunnelRoad("环城北路"))
    }
}