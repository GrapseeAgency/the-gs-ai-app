package com.grapsee.gsai

import android.app.Activity
import android.content.Intent
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
import com.grapsee.gsai.ui.navigation.GsNavHost
import com.grapsee.gsai.ui.theme.TheGsAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
