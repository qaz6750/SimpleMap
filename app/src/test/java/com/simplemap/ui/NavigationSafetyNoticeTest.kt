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
    fun incidentTakesPriorityOverTrafficAndNormalNotice() {
        val selected = selectNavigationSafetyNotice(
            fullTrafficState(),
            NavigationRouteNotice(2L, "普通提示"),
        )
        assertEquals("道路封闭", selected?.title)
        assertEquals("环城西路施工封闭", selected?.detail)
        assertEquals(1_100, selected?.distanceMeters)
    }

    @Test
    fun severeTrafficAppearsWithoutRouteNotice() {
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
        assertEquals("前方严重拥堵", selected?.title)
        assertEquals("拥堵路段约 2.4 公里", selected?.detail)
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
        ),
        trafficAlert = NavigationTrafficAlert(
            NavigationTrafficLevel.SeverelyCongested,
            distanceMeters = 900,
            affectedLengthMeters = 2_400,
        ),
    )
}