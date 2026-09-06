package com.grapsee.gsai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grapsee.gsai.ui.home.HomeScreen
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. All navigation (once wired) happens inside Compose
 * via Navigation Compose; system bars are drawn edge-to-edge and handled by
 * Material 3 [androidx.compose.material3.Scaffold] window insets.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TheGsAiTheme {
                HomeScreen()
            }
        }
    }
}
