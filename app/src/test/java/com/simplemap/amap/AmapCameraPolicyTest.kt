package com.simplemap.amap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapCameraPolicyTest {
    @Test
    fun showPlace_switchesToFreeBrowseWithoutHidingLocationMarker() {
        val initial = AmapCameraPolicy.setMyLocationMarkerVisible(
            AmapCameraPolicyState(),
            visible = true,
        )

        val state = AmapCameraPolicy.enterFreeBrowse(initial)

        assertTrue(state.showsMyLocationMarker)
        assertEquals(AmapCameraMode.FreeBrowse, state.mode)
        assertFalse(state.automaticallyFollowsMyLocation)
    }

    @Test
    fun restoreFollow_reEnablesAutomaticFollowWhenMarkerIsVisible() {
        val browsing = AmapCameraPolicy.enterFreeBrowse(
            AmapCameraPolicy.setMyLocationMarkerVisible(AmapCameraPolicyState(), visible = true),
        )

        val state = AmapCameraPolicy.restoreFollow(browsing)

        assertEquals(AmapCameraMode.FollowMyLocation, state.mode)
        assertTrue(state.automaticallyFollowsMyLocation)
    }

    @Test
    fun showRouteOverview_takesControlWithoutHidingMarker() {
        val initial = AmapCameraPolicy.setMyLocationMarkerVisible(
            AmapCameraPolicyState(),
            visible = true,
        )

        val state = AmapCameraPolicy.showRouteOverview(initial)

        assertTrue(state.showsMyLocationMarker)
        assertEquals(AmapCameraMode.RouteOverview, state.mode)
        assertFalse(state.automaticallyFollowsMyLocation)
    }

    @Test
    fun clearRouteOverview_returnsToFreeBrowseInsteadOfFollow() {
        val overview = AmapCameraPolicy.showRouteOverview(
            AmapCameraPolicy.setMyLocationMarkerVisible(AmapCameraPolicyState(), visible = true),
        )

        val state = AmapCameraPolicy.clearRouteOverview(overview)

        assertEquals(AmapCameraMode.FreeBrowse, state.mode)
        assertTrue(state.showsMyLocationMarker)
        assertFalse(state.automaticallyFollowsMyLocation)
    }

    @Test
    fun togglingLocationMarkerVisibility_preservesCameraModeChoice() {
        val browsing = AmapCameraPolicy.enterFreeBrowse(
            AmapCameraPolicy.setMyLocationMarkerVisible(AmapCameraPolicyState(), visible = true),
        )

        val hidden = AmapCameraPolicy.setMyLocationMarkerVisible(browsing, visible = false)
        val shownAgain = AmapCameraPolicy.setMyLocationMarkerVisible(hidden, visible = true)

        assertEquals(AmapCameraMode.FreeBrowse, hidden.mode)
        assertFalse(hidden.automaticallyFollowsMyLocation)
        assertEquals(AmapCameraMode.FreeBrowse, shownAgain.mode)
        assertTrue(shownAgain.showsMyLocationMarker)
        assertFalse(shownAgain.automaticallyFollowsMyLocation)
    }
}
