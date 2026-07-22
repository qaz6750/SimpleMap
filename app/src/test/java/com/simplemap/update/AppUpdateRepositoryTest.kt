package com.simplemap.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun newerStableReleaseIsAvailable() {
        assertTrue(isNewerReleaseVersion("v0.2.0", "0.1.0"))
    }

    @Test
    fun equalOrOlderReleaseIsNotAvailable() {
        assertFalse(isNewerReleaseVersion("v0.1.0", "0.1.0"))
        assertFalse(isNewerReleaseVersion("v0.1.0+release.4", "0.1.0+local.2"))
        assertFalse(isNewerReleaseVersion("v0.0.9", "0.1.0"))
    }

    @Test
    fun stableReleaseIsNewerThanPrerelease() {
        assertTrue(isNewerReleaseVersion("1.0.0", "1.0.0-beta.2"))
        assertFalse(isNewerReleaseVersion("1.0.0-beta.2", "1.0.0"))
    }

    @Test
    fun invalidReleaseTagDoesNotTriggerUpdate() {
        assertFalse(isNewerReleaseVersion("latest", "0.1.0"))
    }
}