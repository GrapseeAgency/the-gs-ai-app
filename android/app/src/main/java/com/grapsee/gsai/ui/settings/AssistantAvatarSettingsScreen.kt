package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AssistantAvatarStyle
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.GsBotAvatar
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.theme.GsTheme

/** A real, persisted choice used by every assistant avatar in the chat UI. */
@Composable
fun AssistantAvatarSettingsScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors

    RefSettingsPage(
        title = "Bot avatar",
        subtitle = "Choose how Nexly appears in your chats.",
        onBack = onBack,
    ) {
        RefSection(label = "Assistant identity", role = GsGlassRole.CardLight) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Pick one of the four bot avatars. Your choice updates assistant replies and is saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                AssistantAvatarStyle.entries.chunked(2).forEach { rowStyles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowStyles.forEach { style ->
                            val selected = SettingsStore.assistantAvatar == style
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (selected) colors.accentSoft.copy(alpha = 0.18f)
                                        else colors.raisedSurface,
                                    )
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) colors.accentStrong else colors.borderStrong,
                                        shape = RoundedCornerShape(18.dp),
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { SettingsStore.updateAssistantAvatar(style) },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GsBotAvatar(style = style, size = 58.dp)
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = if (selected) "Selected" else "Tap to select",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) colors.accentStrong else colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RefFootnote("The selected avatar appears beside assistant replies across all chats.")
    }
}
