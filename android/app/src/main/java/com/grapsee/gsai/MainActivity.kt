package com.grapsee.gsai

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.liveupdate.LiveUpdater
import com.grapsee.gsai.data.notifications.AppForeground
import com.grapsee.gsai.ui.components.ReferenceBackdrop
import com.grapsee.gsai.ui.navigation.GsNavHost
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
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
        // The app's single foreground truth: the finished-reply notification
        // must never fire while the user is already looking at the reply.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    AppForeground.inForeground = true
                    // GS LiveUpdate: the manifest check that makes the About
                    // screen's "Automatic via GS LiveUpdate" row TRUE. It is
                    // throttled to 10 minutes inside the updater and silent on
                    // every failure — offline users never see it do anything.
                    runCatching { LiveUpdater.syncFrom(this) }
                }
                Lifecycle.Event.ON_STOP -> AppForeground.inForeground = false
                else -> Unit
            }
        })
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
                // The route lives here because the atmosphere does. Hoisting the
                // controller costs GsNavHost nothing (its parameter defaults to
                // the same rememberNavController() call it used to make).
                val navController: NavHostController = rememberNavController()
                val entry by navController.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                // Home is the one screen that ships its own atmosphere: the
                // authored full-bleed backdrop it paints over the bloom. The
                // bloom's first layer is an opaque rect, so under Home the whole
                // canvas was always invisible — a full-screen wash, three large
                // radial gradients, seven bokeh discs and a branch of blossoms,
                // re-recorded into the frame's display list on top of the page.
                //
                // It is dropped once Home has SETTLED, not the instant the route
                // changes: navigation fires the route at the start of a 220-320 ms
                // transition, and pulling the atmosphere out from under a screen
                // that is still fading would flash the flat Surface colour. The
                // other direction needs no wait — leaving Home puts the bloom back
                // immediately, and Home's own backdrop fades out over it.
                var ambient by remember { mutableStateOf(true) }
                LaunchedEffect(route) {
                    if (route == GsRoutes.HOME) {
                        delay(GsMotion.NAV_TWEEN_MS + 40L)
                        ambient = false
                    } else {
                        ambient = true
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Box, not a wrapper composable: the dialog must render
                    // ABOVE the whole nav graph without adding a node to it. No
                    // new route, no new screen, no change to GsNavHost.
                    Box(Modifier.fillMaxSize()) {
                        // THE BLOOM — one ambient canvas behind every screen.
                        // Screens own content, never atmosphere; a screen that
                        // paints its own background is a bug in the design
                        // system, not a preference. Home is the exception it
                        // declares out loud, with its own artwork.
                        if (ambient) ReferenceBackdrop()
                        // The first-run model consent is composed INSIDE the
                        // nav host, where it can wait for the onboarding gate
                        // to finish. Hosting it here painted the dialog over
                        // the welcome pages.
                        GsNavHost(navController = navController)
                    }
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
