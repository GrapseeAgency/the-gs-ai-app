package com.grapsee.gsai.ui.research

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsScreenScaffold

/**
 * Research — honest gate. Multi-source research with citations does not
 * exist yet: the fake pipeline that used to live here ("Reading 5
 * sources…", canned synthesis, fabricated sources with relevance scores,
 * export chips that only toasted "queued") simulated work that never
 * happened. The screen now says exactly what is true: the feature is
 * coming, and chat is available today.
 */
@Composable
fun ResearchScreen(onBack: () -> Unit) {
    GsScreenScaffold(title = "Research", onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GsEmptyState(
                icon = Icons.Outlined.TravelExplore,
                title = "Research is coming soon",
                message = "Multi-source research with citations isn't available yet. " +
                    "You can ask questions in a chat today — Research will appear here when it's ready.",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
