package com.simplemap.ui

import com.simplemap.navigation.NavigationRouteNotice
import com.simplemap.navigation.NavigationTrafficAlert
import com.simplemap.navigation.NavigationTrafficIncident
import com.simplemap.navigation.NavigationTrafficLevel
import com.simplemap.navigation.NavigationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationSafetyNoticeTest {
    @Test
    fun importantRouteNoticeTakesPriority() {
        val important = NavigationRouteNotice(1L, "立即驶离", important = true)
        assertEquals(important, selectNavigationSafetyNotice(fullTrafficState(), important))
    }

    @Test
    fun incidentStaysOnMapAndDoesNotReplaceNormalNotice() {
        val normalNotice = NavigationRouteNotice(2L, "普通提示")
        val selected = selectNavigationSafetyNotice(
            fullTrafficState(),
            normalNotice,
        )
        assertEquals(normalNotice, selected)
    }

    @Test
    fun trafficStaysOffTheInstructionCard() {
        val selected = selectNavigationSafetyNotice(
            NavigationUiState(
                trafficAlert = NavigationTrafficAlert(
                    NavigationTrafficLevel.SeverelyCongested,
                    distanceMeters = 900,
                    affectedLengthMeters = 2_400,
                ),
            ),
            null,
        )
        assertNull(selected)
    }

    @Test
    fun noSafetyStateProducesNoNotice() {
        assertNull(selectNavigationSafetyNotice(NavigationUiState(), null))
    }

    private fun fullTrafficState() = NavigationUiState(
        trafficIncident = NavigationTrafficIncident(
            title = "环城西路施工封闭",
            typeLabel = "道路封闭",
            distanceMeters = 1_100,
            latitude = 30.2741,
            longitude = 120.1551,
        ),
        trafficAlert = NavigationTrafficAlert(
            NavigationTrafficLevel.SeverelyCongested,
            distanceMeters = 900,
            affectedLengthMeters = 2_400,
        ),
    )
}