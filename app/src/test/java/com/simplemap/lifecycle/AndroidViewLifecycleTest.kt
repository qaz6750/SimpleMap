package com.simplemap.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidViewLifecycleTest {
    @Test
    fun `lifecycle callbacks are idempotent and ordered`() {
        val events = mutableListOf<String>()
        val lifecycle = AndroidViewLifecycle(
            onResume = { events += "resume" },
            onPause = { events += "pause" },
            onDestroy = { events += "destroy" },
        )

        lifecycle.resume()
        lifecycle.resume()
        lifecycle.pause()
        lifecycle.pause()
        lifecycle.resume()
        lifecycle.destroy()
        lifecycle.destroy()
        lifecycle.resume()

        assertEquals(
            listOf("resume", "pause", "resume", "pause", "destroy"),
            events,
        )
    }

    @Test
    fun `destroy continues after a pause failure`() {
        var pauseAttempts = 0
        var destroyCount = 0
        val lifecycle = AndroidViewLifecycle(
            onResume = {},
            onPause = {
                pauseAttempts += 1
                error("pause failed")
            },
            onDestroy = { destroyCount += 1 },
        )

        lifecycle.resume()
        lifecycle.pause()
        lifecycle.destroy()

        assertEquals(2, pauseAttempts)
        assertEquals(1, destroyCount)
    }
}
