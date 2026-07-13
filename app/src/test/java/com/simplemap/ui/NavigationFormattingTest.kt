package com.simplemap.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationFormattingTest {
    @Test
    fun distance_formatsMetersAndKilometers() {
        assertEquals("0 米", formatNavigationDistance(0))
        assertEquals("280 米", formatNavigationDistance(280))
        assertEquals("1.0 公里", formatNavigationDistance(1_000))
        assertEquals("7.4 公里", formatNavigationDistance(7_400))
    }

    @Test
    fun time_roundsUpAndFormatsHours() {
        assertEquals("0 分钟", formatNavigationTime(0))
        assertEquals("1 分钟", formatNavigationTime(1))
        assertEquals("1 小时", formatNavigationTime(3_600))
        assertEquals("1 小时 2 分", formatNavigationTime(3_661))
    }
}