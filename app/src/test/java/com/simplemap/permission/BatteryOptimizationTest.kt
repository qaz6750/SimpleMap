package com.simplemap.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryOptimizationTest {
    @Test
    fun packageUriUsesPackageScheme() {
        assertEquals("package:com.simplemap", batteryOptimizationPackageUri("com.simplemap"))
    }
}
