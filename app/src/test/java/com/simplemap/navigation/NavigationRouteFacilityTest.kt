package com.simplemap.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteFacilityTest {
    @Test
    fun advancingRouteUpdatesTollGatesAndKeepsLiveServiceAreaDistance() {
        val facilities = listOf(
            NavigationRouteFacility(
                name = "下沙服务区",
                distanceMeters = 18_000,
                remainingTimeSeconds = 900,
            ),
            NavigationRouteFacility(
                name = "萧山收费站",
                distanceMeters = 28_000,
                remainingTimeSeconds = 1_500,
                kind = NavigationFacilityKind.TollGate,
                routeDistanceMeters = 28_000,
            ),
        )

        val advanced = advanceRouteFacilities(facilities, travelledDistanceMeters = 8_000)

        assertEquals(18_000, advanced.first { it.name == "下沙服务区" }.distanceMeters)
        assertEquals(20_000, advanced.first { it.name == "萧山收费站" }.distanceMeters)
    }

    @Test
    fun advancingPastTollGateRemovesIt() {
        val facilities = listOf(
            NavigationRouteFacility(
                name = "萧山收费站",
                distanceMeters = 4_000,
                remainingTimeSeconds = 300,
                kind = NavigationFacilityKind.TollGate,
                routeDistanceMeters = 28_000,
            ),
        )

        assertEquals(emptyList<NavigationRouteFacility>(), advanceRouteFacilities(facilities, 5_000))
    }
}
