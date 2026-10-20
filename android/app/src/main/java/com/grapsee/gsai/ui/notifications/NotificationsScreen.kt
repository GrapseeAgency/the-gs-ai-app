package com.grapsee.gsai.ui.notifications

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefEmptyState
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefRow

/**
 * Notifications — honest gate with a way out.
 *
 * No notification feed is wired, so there is nothing to list. The empty state
 * is hand-drawn, and the two real controls live below it: the reply notice
 * switch and the system notification settings. The old page was a blank
 * centered label with no action at all.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    RefPage(
        title = "Notifications",
        subtitle = "Only what finished while you were away.",
        onBack = onBack
    ) {
        RefEmptyState(
            art = RefArt.Notifications,
            title = "You're all caught up",
            message = "Finished replies will appear here when GS is working in the background. There is no feed to scroll."
        )
        Spacer(Modifier.height(6.dp))
        RefSection(label = "Controls") {
            RefRow(
                title = "Notification settings",
                subtitle = "Reply notices, permission and channel",
                icon = GsIcons.SlidersHorizontal,
                onClick = { onNavigate(GsRoutes.SETTINGS_NOTIFICATIONS) }
            )
        }
    }
}
