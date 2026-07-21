package com.simplemap.navigation

import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePoint
import com.simplemap.route.RouteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun selectedPlanMatchesNavigationPathByMetricsAndShape() {
        val selected = routePlan(
            distanceMeters = 12_000,
            durationSeconds = 1_200,
            polyline = listOf(point(30.25, 120.16), point(30.28, 120.20), point(30.31, 120.24)),
        )
        val candidates = listOf(
            NavigationPathCandidate(
                pathId = 11L,
                durationSeconds = 1_180,
                distanceMeters = 11_900,
                polyline = listOf(point(30.25, 120.16), point(30.24, 120.20), point(30.31, 120.24)),
            ),
            NavigationPathCandidate(
                pathId = 22L,
                durationSeconds = 1_210,
                distanceMeters = 12_050,
                polyline = listOf(point(30.25, 120.16), point(30.28, 120.20), point(30.31, 120.24)),
            ),
        )

        assertEquals(22L, findMatchingNavigationPath(selected, candidates))
    }

    @Test
    fun selectedPlanDoesNotForceUnrelatedNavigationPath() {
        val selected = routePlan(
            distanceMeters = 12_000,
            durationSeconds = 1_200,
            polyline = listOf(point(30.25, 120.16), point(30.28, 120.20), point(30.31, 120.24)),
        )
        val unrelated = NavigationPathCandidate(
            pathId = 33L,
            durationSeconds = 2_400,
            distanceMeters = 25_000,
            polyline = listOf(point(30.25, 120.16), point(30.18, 120.30), point(30.31, 120.24)),
        )

        assertNull(findMatchingNavigationPath(selected, listOf(unrelated)))
    }

    private fun routePlan(
        distanceMeters: Int,
        durationSeconds: Long,
        polyline: List<RoutePoint>,
    ) = RoutePlan(
        id = "drive-selected",
        mode = RouteMode.Drive,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        costYuan = null,
        summary = "selected",
        steps = emptyList(),
        polyline = polyline,
    )

    private fun point(latitude: Double, longitude: Double) = RoutePoint(latitude, longitude)

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