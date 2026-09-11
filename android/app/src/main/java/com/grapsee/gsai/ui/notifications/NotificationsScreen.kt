package com.grapsee.gsai.ui.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsScreenScaffold

/**
 * Notifications — honest gate. No notification channel is wired yet, so
 * there is nothing to list: the eight hardcoded sample alerts that used to
 * populate this screen (with deep links into fake destinations) simulated
 * activity that never happened. The screen keeps its place in the shell and
 * states the truth: nothing here, updates will land when they are real.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    GsScreenScaffold(title = "Notifications", onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GsEmptyState(
                icon = Icons.Outlined.Notifications,
                title = "You're all caught up",
                message = "Updates about your conversations will appear here.",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
