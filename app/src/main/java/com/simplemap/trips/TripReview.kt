package com.simplemap.trips

import kotlin.math.roundToInt

data class TripReview(
    val averageSpeedKmh: Int,
    val elapsedSeconds: Long,
    val distanceMeters: Int,
)

fun TripRecord.toTripReview(): TripReview {
    val elapsedSeconds = durationSeconds.coerceAtLeast(0L)
    val distanceMeters = distanceMeters.coerceAtLeast(0)
    val averageSpeedKmh = if (elapsedSeconds == 0L) {
        0
    } else {
        (distanceMeters * 3.6 / elapsedSeconds).roundToInt().coerceAtLeast(0)
    }
    return TripReview(
        averageSpeedKmh = averageSpeedKmh,
        elapsedSeconds = elapsedSeconds,
        distanceMeters = distanceMeters,
    )
}