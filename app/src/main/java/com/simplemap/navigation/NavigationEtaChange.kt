package com.simplemap.navigation

internal fun etaChangeMinutes(
    baselineArrivalSeconds: Long,
    nowSeconds: Long,
    remainingTimeSeconds: Int,
    thresholdSeconds: Int = ETA_CHANGE_THRESHOLD_SECONDS,
): Int? {
    val difference = nowSeconds + remainingTimeSeconds.coerceAtLeast(0) - baselineArrivalSeconds
    if (kotlin.math.abs(difference) < thresholdSeconds) return null
    val roundedMinutes = kotlin.math.ceil(kotlin.math.abs(difference) / 60.0).toInt()
    return if (difference > 0) roundedMinutes else -roundedMinutes
}

private const val ETA_CHANGE_THRESHOLD_SECONDS = 180