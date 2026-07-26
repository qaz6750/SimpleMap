package com.simplemap.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationSettingsStore
import com.simplemap.settings.NavigationThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

internal data class PersistedSettingsMutation<T>(
    val value: T,
    val settings: NavigationSettings,
)

internal class NavigationSettingsStateHolder(
    initialSettings: NavigationSettings,
    private val store: NavigationSettingsStore,
    private val coroutineScope: CoroutineScope,
) {
    var settings by mutableStateOf(initialSettings)
        private set

    private val revision = AtomicInteger()
    private val storeMutex = Mutex()

    fun publishCurrentSettings(
        onThemeModeChanged: (NavigationThemeMode) -> Unit,
        onOrientationModeChanged: (AppOrientationMode) -> Unit,
    ) {
        publishSettings(settings, onThemeModeChanged, onOrientationModeChanged)
    }

    fun update(
        updatedSettings: NavigationSettings,
        onThemeModeChanged: (NavigationThemeMode) -> Unit,
        onOrientationModeChanged: (AppOrientationMode) -> Unit,
        onSaveFailed: () -> Unit,
    ) {
        if (updatedSettings == settings) return

        settings = updatedSettings
        onThemeModeChanged(updatedSettings.themeMode)
        val updateRevision = revision.incrementAndGet()
        coroutineScope.launch {
            val saved = withContext(NonCancellable + Dispatchers.IO) {
                storeMutex.withLock {
                    if (updateRevision != revision.get()) return@withLock null
                    store.save(updatedSettings)
                }
            } ?: return@launch
            if (updateRevision != revision.get()) return@launch
            if (saved) {
                onOrientationModeChanged(updatedSettings.orientationMode)
                return@launch
            }

            val restoredSettings = withContext(NonCancellable + Dispatchers.IO) {
                storeMutex.withLock { store.load() }
            }
            if (updateRevision != revision.get()) return@launch
            publishSettings(restoredSettings, onThemeModeChanged, onOrientationModeChanged)
            onSaveFailed()
        }
    }

    suspend fun <T> mutatePersistedSettings(
        mutation: () -> PersistedSettingsMutation<T>,
        onThemeModeChanged: (NavigationThemeMode) -> Unit,
        onOrientationModeChanged: (AppOrientationMode) -> Unit,
    ): T {
        val mutationRevision = revision.incrementAndGet()
        val result = withContext(NonCancellable + Dispatchers.IO) {
            storeMutex.withLock { mutation() }
        }
        if (mutationRevision == revision.get()) {
            publishSettings(result.settings, onThemeModeChanged, onOrientationModeChanged)
        }
        return result.value
    }

    private fun publishSettings(
        storedSettings: NavigationSettings,
        onThemeModeChanged: (NavigationThemeMode) -> Unit,
        onOrientationModeChanged: (AppOrientationMode) -> Unit,
    ) {
        settings = storedSettings
        onThemeModeChanged(storedSettings.themeMode)
        onOrientationModeChanged(storedSettings.orientationMode)
    }
}
