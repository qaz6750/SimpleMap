package com.simplemap.navigation

import com.amap.api.navi.enums.LaneAction
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLaneTest {
    @Test
    fun mapsAmapLaneActionsToDrivingDirections() {
        assertEquals(NavigationLaneDirection.Ahead, LaneAction.LANE_ACTION_AHEAD.toNavigationLaneDirection())
        assertEquals(NavigationLaneDirection.Left, LaneAction.LANE_ACTION_LEFT.toNavigationLaneDirection())
        assertEquals(NavigationLaneDirection.Right, LaneAction.LANE_ACTION_RIGHT.toNavigationLaneDirection())
        assertEquals(NavigationLaneDirection.AheadLeft, LaneAction.LANE_ACTION_AHEAD_LEFT.toNavigationLaneDirection())
        assertEquals(NavigationLaneDirection.AheadRight, LaneAction.LANE_ACTION_AHEAD_RIGHT.toNavigationLaneDirection())
        assertEquals(NavigationLaneDirection.LeftRight, LaneAction.LANE_ACTION_LEFT_RIGHT.toNavigationLaneDirection())
        assertEquals(
            NavigationLaneDirection.AheadLeftRight,
            LaneAction.LANE_ACTION_AHEAD_LEFT_RIGHT.toNavigationLaneDirection(),
        )
        assertEquals(NavigationLaneDirection.AheadUTurn, LaneAction.LANE_ACTION_AHEAD_LU_TURN.toNavigationLaneDirection())
        assertEquals(NavigationLaneDirection.UTurn, LaneAction.LANE_ACTION_LU_TURN.toNavigationLaneDirection())
        assertEquals(NavigationLaneDirection.Other, LaneAction.LANE_ACTION_BUS.toNavigationLaneDirection())
    }

    @Test
    fun pairsPhysicalLaneDirectionWithRecommendation() {
        val lanes = parseNavigationLanes(
            backgroundLane = intArrayOf(
                LaneAction.LANE_ACTION_AHEAD_RIGHT,
                LaneAction.LANE_ACTION_RIGHT,
                LaneAction.LANE_ACTION_LEFT,
            ),
            recommendedLane = intArrayOf(
                LaneAction.LANE_ACTION_NULL,
                LaneAction.LANE_ACTION_RIGHT,
            ),
            laneCount = 3,
        )

        assertEquals(
            listOf(
                NavigationLane(NavigationLaneDirection.AheadRight, recommended = false),
                NavigationLane(NavigationLaneDirection.Right, recommended = true),
                NavigationLane(NavigationLaneDirection.Left, recommended = false),
            ),
            lanes,
        )
    }

    @Test
    fun clampsLaneCountToAvailableDirections() {
        val lanes = parseNavigationLanes(
            backgroundLane = intArrayOf(LaneAction.LANE_ACTION_AHEAD),
            recommendedLane = intArrayOf(LaneAction.LANE_ACTION_AHEAD, LaneAction.LANE_ACTION_RIGHT),
            laneCount = 4,
        )

        assertEquals(listOf(NavigationLane(NavigationLaneDirection.Ahead, recommended = true)), lanes)
    }
}