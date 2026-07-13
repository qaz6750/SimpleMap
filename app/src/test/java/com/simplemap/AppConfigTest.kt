package com.simplemap

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigTest {
    @Test
    fun applicationId_isStable() {
        assertEquals("com.simplemap", BuildConfig.APPLICATION_ID)
    }
}