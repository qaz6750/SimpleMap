package com.simplemap

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private var darkSystemBars = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveSystemBars(
            darkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES,
        )
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
            SideEffect { configureImmersiveSystemBars(darkTheme) }
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureImmersiveSystemBars(darkSystemBars)
    }

    private fun configureImmersiveSystemBars(darkTheme: Boolean) {
        darkSystemBars = darkTheme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}

private fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }