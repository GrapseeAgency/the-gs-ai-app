package com.grapsee.gsai

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.navigation.GsNavHost
import com.grapsee.gsai.ui.theme.TheGsAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Launch-window tint for a FORCED theme (Task 86-d): SettingsStore hydrates
        // from SharedPreferences in GSApplication.onCreate (before any activity),
        // so the mode resolves synchronously here — before super.onCreate installs
        // the window. The manifest theme (Theme.TheGsAiApp) still follows the
        // system via the values/values-night qualifier; a forced Light/Dark gets
        // the matching named variant (values/themes.xml, explicit per-variant
        // windowBackground + windowLightStatusBar), so the cold-start and rotation
        // window background always matches the app's resolved palette. The system
        // starting-window snapshot still follows the manifest theme — it cannot be
        // changed at runtime; the activity's own window is exact from the first frame.
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val forcedDark = when (SettingsStore.themeMode) {
            "Light" -> false
            "Dark" -> true
            else -> systemDark
        }
        setTheme(if (forcedDark) R.style.Theme_TheGsAiApp_Dark else R.style.Theme_TheGsAiApp_Light)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold start via a home-screen quick action: publish before composing.
        ShortcutBus.publish(intent)
        setContent {
            // First-frame marker: tells the system the app is interactive —
            // sharpens ART background optimization timing and is the anchor
            // every future startup measurement hangs from.
            val activityContext = LocalContext.current as? Activity
            LaunchedEffect(Unit) {
                runCatching { activityContext?.reportFullyDrawn() }
            }
            TheGsAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GsNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm start via a quick action (singleTop delivers here).
        ShortcutBus.publish(intent)
    }
}
