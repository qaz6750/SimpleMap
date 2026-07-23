package com.simplemap.navigation

import com.amap.api.navi.enums.NaviIncidentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTrafficIncidentTest {
    @Test
    fun routeReopenedEventIsNotOngoing() {
        assertFalse(isOngoingAmapRouteIncident(NaviIncidentType.TYPE_ROUTE_UNCLOSED_EVENT))
    }

    @Test
    fun routeClosedEventRemainsOngoing() {
        assertTrue(isOngoingAmapRouteIncident(NaviIncidentType.TYPE_ROUTE_CLOSED_EVENT_START))
    }

    @Test
    fun calculatesUpcomingIncidentDistanceAlongRoute() {
        val distance = calculateIncidentDistance(
            route = listOf(point(30.0, 120.0), point(30.0, 120.01), point(30.0, 120.02)),
            incident = point(30.0, 120.01),
            travelledDistanceMeters = 400,
            routeLengthMeters = 2_000,
        )

        assertEquals(600, distance)
    }

    @Test
    fun calculatesStableIncidentAnchorAlongRoute() {
        val distance = calculateIncidentRouteDistance(
            route = listOf(point(30.0, 120.0), point(30.0, 120.01), point(30.0, 120.02)),
            incident = point(30.0, 120.01),
            routeLengthMeters = 2_000,
        )

        assertEquals(1_000, distance)
    }

    @Test
    fun findsIncidentAtMiddleOfLongRouteSegment() {
        val distance = calculateIncidentRouteDistance(
            route = listOf(point(30.0, 120.0), point(30.0, 120.04)),
            incident = point(30.0, 120.02),
            routeLengthMeters = 4_000,
        )

        assertEquals(2_000, distance)
    }

    @Test
    fun ignoresIncidentFarFromRoute() {
        assertNull(
            calculateIncidentDistance(
                route = listOf(point(30.0, 120.0), point(30.0, 120.01)),
                incident = point(31.0, 121.0),
                travelledDistanceMeters = 0,
                routeLengthMeters = 1_000,
            ),
        )
    }

    @Test
    fun ignoresIncidentAlreadyPassed() {
        assertNull(
            calculateIncidentDistance(
                route = listOf(point(30.0, 120.0), point(30.0, 120.01)),
                incident = point(30.0, 120.0),
                travelledDistanceMeters = 100,
                routeLengthMeters = 1_000,
            ),
        )
    }

    private fun point(latitude: Double, longitude: Double) = NavigationCoordinate(latitude, longitude)
}