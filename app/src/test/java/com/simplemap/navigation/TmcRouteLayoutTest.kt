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

    @Test
    fun portraitRouteBarIsNarrowerAndLonger() {
        listOf(
            320 to 480,
            360 to 640,
            412 to 915,
        ).forEach { (width, height) ->
            val layout = calculateTmcRouteLayout(width, height, density = 1f, isLandscape = false)
            assertTrue(layout.width <= 18)
            assertTrue(layout.height >= layout.width * 10)
        }
    }

    @Test
    fun landscapeRouteBarRemainsTallWithoutExceedingViewport() {
        listOf(
            480 to 320,
            640 to 360,
            1280 to 480,
        ).forEach { (width, height) ->
            val layout = calculateTmcRouteLayout(width, height, density = 1f, isLandscape = true)
            assertTrue(layout.width in 18..22)
            assertTrue(layout.height >= layout.width * 7)
            assertTrue(layout.x + layout.width <= width)
            assertTrue(layout.y + layout.height <= height)
        }
    }

    @Test
    fun measuredSafeAreasKeepRouteBarWithinVisibleViewport() {
        val layout = calculateTmcRouteLayout(
            viewportWidth = 360,
            viewportHeight = 780,
            density = 1f,
            isLandscape = false,
            overlaySafeAreaTopPx = 208,
            overlaySafeAreaBottomPx = 156,
        )

        assertTrue(layout.y >= 208)
        assertTrue(layout.y + layout.height <= 780 - 156)
        assertTrue(layout.height >= layout.width * 8)
    }
}
