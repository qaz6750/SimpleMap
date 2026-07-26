package com.simplemap.ui

import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val NavigationArrivalTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatNavigationDistance(distanceMeters: Int): String =
    formatNavigationDistance(distanceMeters.toLong())

internal fun formatNavigationDistance(distanceMeters: Long): String = when {
    distanceMeters < 0 -> "--"
    distanceMeters < 1_000 -> "$distanceMeters 米"
    else -> "%.1f 公里".format(distanceMeters / 1_000.0)
}

internal fun formatNavigationTime(remainingSeconds: Int): String {
    val minutes = (remainingSeconds.coerceAtLeast(0) + 59) / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "$minutes 分钟"
        remainingMinutes == 0 -> "$hours 小时"
        else -> "$hours 小时 $remainingMinutes 分"
    }
}

internal fun formatNavigationArrivalTime(
    remainingSeconds: Int,
    clock: Clock = Clock.systemDefaultZone(),
): String = LocalTime.now(clock)
    .plusSeconds(remainingSeconds.coerceAtLeast(0).toLong())
    .format(NavigationArrivalTimeFormatter)
