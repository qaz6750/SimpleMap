package com.simplemap.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class TmcRouteLayoutTest {
    @Test
    fun routeBarStaysInsideSupportedViewports() {
        listOf(
            Triple(320, 480, false),
            Triple(360, 640, false),
            Triple(640, 320, true),
            Triple(640, 360, true),
            Triple(1280, 480, true),
        ).forEach { (width, height, landscape) ->
            val layout = calculateTmcRouteLayout(width, height, density = 1f, isLandscape = landscape)
            assertTrue(layout.x >= 0 && layout.y >= 0)
            assertTrue(layout.width > 0 && layout.height > 0)
            assertTrue(layout.x + layout.width <= width)
            assertTrue(layout.y + layout.height <= height)
            if (landscape) assertTrue(layout.x >= width / 2)
        }
    }
}