package com.simplemap.settings

import java.time.Clock
import java.time.LocalTime

/**
 * Returns the current local minute of day from an injectable clock.
 *
 * Keeping wall-clock access behind this function gives theme and quiet-hours logic one time source
 * and lets boundary behavior be verified with a fixed clock.
 */
internal fun currentMinuteOfDay(clock: Clock = Clock.systemDefaultZone()): Int =
    LocalTime.now(clock).let { time -> time.hour * MINUTES_PER_HOUR + time.minute }

private const val MINUTES_PER_HOUR = 60
