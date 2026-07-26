package com.simplemap

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
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
import com.simplemap.navigation.NavigationSession
import com.simplemap.navigation.NavigationSessionCoordinator
import com.simplemap.navigation.overlay.NavigationOverlayController
import com.simplemap.navigation.overlay.NavigationOverlayPermission
import com.simplemap.privacy.SharedPreferencesPrivacyConsentStore
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.SharedPreferencesNavigationSettingsStore
import com.simplemap.settings.currentMinuteOfDay
import com.simplemap.settings.shouldUseNightTheme
import com.simplemap.startup.MapAccessController
import com.simplemap.ui.SimpleMapRoot
import com.simplemap.ui.theme.SimpleMapTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var darkSystemBars = false
    private var navigationVisible = false
    private val navigationOverlay = NavigationOverlayController()
    private var overlaySession: NavigationSession? = null
    private var overlayStateToken: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = SharedPreferencesNavigationSettingsStore(applicationContext)
        val initialSettings = settingsStore.load()
        applyOrientationMode(initialSettings.orientationMode)
        enableEdgeToEdge()
        configureSystemBars(
            darkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES,
        )
        setContent {
            val rememberedSettingsStore = remember { settingsStore }
            var themeMode by remember { mutableStateOf(initialSettings.themeMode) }
            var minuteOfDay by remember { mutableIntStateOf(currentMinuteOfDay()) }
            LaunchedEffect(themeMode) {
                if (themeMode != NavigationThemeMode.Automatic) return@LaunchedEffect
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
            SideEffect { configureSystemBars(darkTheme) }
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
                    navigationSettingsStore = rememberedSettingsStore,
                    initialNavigationSettings = initialSettings,
                    onThemeModeChanged = { themeMode = it },
                    onOrientationModeChanged = ::applyOrientationMode,
                    onNavigationVisibilityChanged = { visible ->
                        navigationVisible = visible
                        configureSystemBars(darkSystemBars)
                    },
                    onDecline = ::finish,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) configureSystemBars(darkSystemBars)
    }

    override fun onStart() {
        super.onStart()
        hideNavigationOverlay()
    }

    override fun onStop() {
        super.onStop()
        showNavigationOverlayIfNeeded()
    }

    override fun onDestroy() {
        hideNavigationOverlay()
        super.onDestroy()
    }

    private fun showNavigationOverlayIfNeeded() {
        if (isChangingConfigurations) return
        val session = NavigationSessionCoordinator.session.value ?: return
        if (!NavigationOverlayPermission.canDrawOverlays(this)) return
        navigationOverlay.show(this)
        if (!navigationOverlay.isShowing) return
        overlaySession = session
        overlayStateToken = session.controller.addStateListener { state ->
            runOnUiThread {
                if (navigationOverlay.isShowing) navigationOverlay.update(state)
            }
        }
    }

    private fun hideNavigationOverlay() {
        overlayStateToken?.let { token ->
            runCatching { overlaySession?.controller?.removeStateListener(token) }
        }
        overlayStateToken = null
        overlaySession = null
        navigationOverlay.hide()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post { configureSystemBars(darkSystemBars) }
    }

    private fun configureSystemBars(darkTheme: Boolean) {
        darkSystemBars = darkTheme
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (navigationVisible && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun applyOrientationMode(mode: AppOrientationMode) {
        requestedOrientation = when (mode) {
            AppOrientationMode.FollowSystem -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            AppOrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            AppOrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}
