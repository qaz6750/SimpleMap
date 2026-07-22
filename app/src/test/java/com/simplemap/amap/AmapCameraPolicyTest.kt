package com.simplemap.amap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapCameraPolicyTest {
    @Test
    fun mapScaleUsesFriendlyDistanceAndFitsTargetWidth() {
        val scale = calculateMapScale(zoom = 16f, latitude = 30.0, targetWidthPixels = 96f)

        assertTrue(scale.distanceMeters in setOf(100, 200, 500, 1_000, 2_000, 5_000))
        assertTrue(scale.widthPixels in 1f..96f)
    }

    @Test
    fun perspectiveModePreservesBearing() {
        val initial = AmapCameraOrientation(tilt = 12f, bearing = 85f)

        assertEquals(AmapCameraOrientation(0f, 85f), applyPerspectiveMode(initial, AmapPerspectiveMode.TwoDimensional))
        assertEquals(AmapCameraOrientation(45f, 85f), applyPerspectiveMode(initial, AmapPerspectiveMode.ThreeDimensional))
    }

    @Test
    fun resetNorthPreservesTilt() {
        assertEquals(
            AmapCameraOrientation(45f, 0f),
            resetCameraNorth(AmapCameraOrientation(tilt = 45f, bearing = 190f)),
        )
    }

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
