package com.simplemap.navigation

import com.amap.api.maps.AMap
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationMapThemeTest {
    @Test
    fun dayModeUsesNavigationMapStyle() {
        assertEquals(AMap.MAP_TYPE_NAVI, navigationMapType(nightMode = false))
    }

    @Test
    fun nightModeUsesNavigationNightMapStyle() {
        assertEquals(AMap.MAP_TYPE_NAVI_NIGHT, navigationMapType(nightMode = true))
    }
}