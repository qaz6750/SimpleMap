package com.simplemap.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveRoutePresetTest {
    @Test
    fun presetsMapToSupportedRouteOptions() {
        assertEquals(
            DriveRouteOptions(avoidCongestion = true),
            DriveRoutePreset.Commute.toOptions(),
        )
        assertEquals(
            DriveRouteOptions(prioritizeHighway = true),
            DriveRoutePreset.HighwayFirst.toOptions(),
        )
        assertEquals(
            DriveRouteOptions(avoidCongestion = true, avoidHighway = true, saveMoney = true),
            DriveRoutePreset.ElectricEco.toOptions(),
        )
    }

    @Test
    fun customizedOptionsDoNotClaimPreset() {
        assertEquals(DriveRoutePreset.Commute, DriveRoutePreset.Commute.toOptions().matchingPreset())
        assertNull(DriveRouteOptions(saveMoney = true).matchingPreset())
    }
}