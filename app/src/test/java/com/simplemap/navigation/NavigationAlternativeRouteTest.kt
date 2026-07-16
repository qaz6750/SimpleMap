package com.simplemap.navigation

import com.simplemap.route.RouteMode
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAlternativeRouteTest {
    private val origin = place("origin")
    private val destination = place("destination")

    @Test
    fun multipleRoutesRequireSupportedAmapNavigationConditions() {
        val request = RouteRequest(origin, destination)

        assertTrue(supportsMultipleRouteNavigation(request, simulated = false, directDistanceMeters = 79_999f))
        assertFalse(supportsMultipleRouteNavigation(request, simulated = true, directDistanceMeters = 10_000f))
        assertFalse(
            supportsMultipleRouteNavigation(
                request.copy(waypoints = listOf(place("waypoint"))),
                simulated = false,
                directDistanceMeters = 10_000f,
            ),
        )
        assertFalse(supportsMultipleRouteNavigation(request, simulated = false, directDistanceMeters = 80_001f))
        assertFalse(
            supportsMultipleRouteNavigation(
                request.copy(mode = RouteMode.Walk),
                simulated = false,
                directDistanceMeters = 1_000f,
            ),
        )
    }

    private fun place(id: String) = Place(
        id = id,
        name = id,
        address = "",
        district = "",
        category = "",
        phone = "",
        latitude = 30.25,
        longitude = 120.16,
        distanceMeters = null,
    )
}