package com.simplemap.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTrafficIncidentTest {
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