package com.simplemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.simplemap.amap.AndroidAmapRuntime
import com.simplemap.privacy.SharedPreferencesPrivacyConsentStore
import com.simplemap.startup.MapAccessController
import com.simplemap.ui.SimpleMapRoot
import com.simplemap.ui.theme.SimpleMapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleMapTheme {
                val controller = remember {
                    MapAccessController(
                        consentStore = SharedPreferencesPrivacyConsentStore(applicationContext),
                        apiKeyPresent = BuildConfig.AMAP_API_KEY_PRESENT,
                        runtime = AndroidAmapRuntime(applicationContext),
                    )
                }
                SimpleMapRoot(controller = controller, onDecline = ::finish)
            }
        }
    }
}