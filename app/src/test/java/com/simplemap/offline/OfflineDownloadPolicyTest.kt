package com.simplemap.offline

import com.simplemap.ui.formatOfflineSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadPolicyTest {
    @Test
    fun wifiOnlyRequiresAvailableWifiConnection() {
        assertFalse(canDownloadOfflineMap(NetworkStatus(available = false, connectedViaWifi = false), wifiOnly = true))
        assertFalse(canDownloadOfflineMap(NetworkStatus(available = true, connectedViaWifi = false), wifiOnly = true))
        assertTrue(canDownloadOfflineMap(NetworkStatus(available = true, connectedViaWifi = true), wifiOnly = true))
    }

    @Test
    fun disablingWifiOnlyAllowsAnyAvailableNetwork() {
        assertTrue(canDownloadOfflineMap(NetworkStatus(available = true, connectedViaWifi = false), wifiOnly = false))
        assertFalse(canDownloadOfflineMap(NetworkStatus(available = false, connectedViaWifi = true), wifiOnly = false))
    }

    @Test
    fun offlineCapacityUsesReadableUnits() {
        assertEquals("128.0 MB", formatOfflineSize(128L * 1024 * 1024))
        assertEquals("1.5 GB", formatOfflineSize(3L * 512 * 1024 * 1024))
        assertEquals("0.0 MB", formatOfflineSize(-1))
    }

    @Test
    fun downloadedCapacityIncludesPartialAndInstalledPackages() {
        val mebibyte = 1024L * 1024L
        val cities = listOf(
            city("installed", 100 * mebibyte, 100, OfflineDownloadState.Installed),
            city("paused", 200 * mebibyte, 25, OfflineDownloadState.Paused),
            city("failed", 300 * mebibyte, 80, OfflineDownloadState.Failed),
        )

        assertEquals(150 * mebibyte, downloadedOfflineBytes(cities))
    }
}

private fun city(
    code: String,
    sizeBytes: Long,
    progress: Int,
    state: OfflineDownloadState,
) = OfflineCity(
    code = code,
    name = code,
    sizeBytes = sizeBytes,
    progress = progress,
    state = state,
)
