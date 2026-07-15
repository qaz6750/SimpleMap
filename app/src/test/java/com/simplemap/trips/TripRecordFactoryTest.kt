package com.simplemap.trips

import com.simplemap.navigation.NavigationPhase
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePoint
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecordFactoryTest {
    private val origin = place("origin", "杭州东站")
    private val destination = place("destination", "西湖风景名胜区")
    private val request = RouteRequest(origin, destination, mode = RouteMode.Drive)
    private val plan = RoutePlan(
        id = "drive-0",
        mode = RouteMode.Drive,
        durationSeconds = 1_800,
        distanceMeters = 12_000,
        costYuan = null,
        summary = "推荐路线",
        steps = emptyList(),
        polyline = listOf(RoutePoint(origin.latitude, origin.longitude)),
    )

    @Test
    fun cancelledTripUsesElapsedTimeAndTravelledDistance() {
        val record = createTripRecord(
            startedAtMillis = 1_000,
            completedAtMillis = 601_000,
            request = request,
            plan = plan,
            phase = NavigationPhase.Navigating,
            remainingDistanceMeters = 8_000,
            simulated = false,
        )

        assertEquals(TripStatus.Cancelled, record.status)
        assertEquals(600, record.durationSeconds)
        assertEquals(4_000, record.distanceMeters)
    }

    @Test
    fun arrivedTripUsesFullDistanceAndKeepsSimulationFlag() {
        val record = createTripRecord(
            startedAtMillis = 10_000,
            completedAtMillis = 10_500,
            request = request,
            plan = plan,
            phase = NavigationPhase.Arrived,
            remainingDistanceMeters = 900,
            simulated = true,
        )

        assertEquals(TripStatus.Arrived, record.status)
        assertEquals(1, record.durationSeconds)
        assertEquals(12_000, record.distanceMeters)
        assertTrue(record.simulated)
    }

    @Test
    fun failedTripClampsInvalidRemainingDistance() {
        val record = createTripRecord(
            startedAtMillis = 1_000,
            completedAtMillis = 2_000,
            request = request,
            plan = plan,
            phase = NavigationPhase.Failed,
            remainingDistanceMeters = 20_000,
            simulated = false,
        )

        assertEquals(TripStatus.Failed, record.status)
        assertEquals(0, record.distanceMeters)
    }
}

private fun place(id: String, name: String) = Place(
    id = id,
    name = name,
    address = "",
    district = "杭州市",
    category = "",
    phone = "",
    latitude = 30.25,
    longitude = 120.16,
    distanceMeters = null,
)
