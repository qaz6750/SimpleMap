package com.simplemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.simplemap.amap.AndroidAmapRuntime
import com.simplemap.privacy.SharedPreferencesPrivacyConsentStore
import com.simplemap.settings.SharedPreferencesNavigationSettingsStore
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.startup.MapAccessController
import com.simplemap.ui.SimpleMapRoot
import com.simplemap.ui.theme.SimpleMapTheme
import kotlinx.coroutines.delay
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { SharedPreferencesNavigationSettingsStore(applicationContext) }
            var themeMode by remember { mutableStateOf(settingsStore.load().themeMode) }
            var minuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
            LaunchedEffect(themeMode) {
                while (true) {
                    minuteOfDay = currentMinuteOfDay()
                    delay(60_000L)
                }
            }
            val darkTheme = shouldUseNightTheme(
                mode = themeMode,
                systemInDarkTheme = isSystemInDarkTheme(),
                minuteOfDay = minuteOfDay,
                inTunnel = false,
            )
            SimpleMapTheme(darkTheme = darkTheme) {
                val controller = remember {
                    MapAccessController(
                        consentStore = SharedPreferencesPrivacyConsentStore(applicationContext),
                        apiKeyPresent = BuildConfig.AMAP_API_KEY_PRESENT,
                        runtime = AndroidAmapRuntime(applicationContext),
                    )
                }
                SimpleMapRoot(
                    controller = controller,
                    navigationSettingsStore = settingsStore,
                    onThemeModeChanged = { themeMode = it },
                    onDecline = ::finish,
                )
            }
        }
    }
}

private fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }