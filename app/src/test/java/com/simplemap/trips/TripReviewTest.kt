package com.simplemap.trips

import com.simplemap.route.RouteMode
import com.simplemap.search.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class TripReviewTest {
    @Test
    fun calculatesAverageSpeedFromLocalTripSummary() {
        val review = trip(distanceMeters = 12_000, durationSeconds = 1_800).toTripReview()

        assertEquals(24, review.averageSpeedKmh)
        assertEquals(12_000, review.distanceMeters)
        assertEquals(1_800, review.elapsedSeconds)
    }

    @Test
    fun clampsInvalidValuesWithoutInventingMetrics() {
        val review = trip(distanceMeters = -20, durationSeconds = 0).toTripReview()

        assertEquals(0, review.averageSpeedKmh)
        assertEquals(0, review.distanceMeters)
        assertEquals(0, review.elapsedSeconds)
    }

    private fun trip(distanceMeters: Int, durationSeconds: Long) = TripRecord(
        id = "trip-1",
        startedAtMillis = 1_700_000_000_000,
        completedAtMillis = 1_700_001_800_000,
        origin = place("origin", "杭州东站"),
        destination = place("destination", "西湖风景名胜区"),
        mode = RouteMode.Drive,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        status = TripStatus.Arrived,
        simulated = false,
    )

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
}