package com.grapsee.gsai.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress

/**
 * AERUO KINETIC drawer — the primary navigation, exactly like the benchmark
 * AI apps (ChatGPT / Claude / Kimi): obsidian panel, account header, one-tap
 * new chat, recents, and the section map. The home canvas stays clean.
 */
@Composable
fun GsDrawerContent(
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(304.dp)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(Aeruo.Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GsMotion.spaceM)
    ) {
        Spacer(Modifier.height(GsMotion.spaceL))

        // Account header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Aeruo.AccentSoftDark),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "GA",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Aeruo.Accent
                )
            }
            Spacer(Modifier.width(GsMotion.spaceS))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Grapsee Admin",
                    style = MaterialTheme.typography.titleMedium,
                    color = Aeruo.TextDark
                )
                Text(
                    "graphesee@gmail.com",
                    style = MaterialTheme.typography.labelMedium,
                    color = Aeruo.TextMutedDark
                )
            }
            GsChip(text = "Pro", selected = true, onClick = { onNavigate(GsRoutes.BILLING) })
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        // New chat — the one loud action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GsMotion.radiusCard))
                .background(Aeruo.RaisedDark)
                .kineticPress()
                .clickable {
                    onClose()
                    onNavigate(GsRoutes.chat(null))
                }
                .padding(horizontal = GsMotion.spaceM, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = Aeruo.Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(GsMotion.spaceS))
            Text(
                "New chat",
                style = MaterialTheme.typography.titleMedium,
                color = Aeruo.TextDark
            )
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        DrawerLabel("Recent")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Q3 pricing strategy") {
            onClose()
            onNavigate(GsRoutes.chat("demo-1"))
        }
        DrawerRow("Kyoto trip plan") {
            onClose()
            onNavigate(GsRoutes.chat("demo-2"))
        }
        DrawerRow("Kotlin coroutines notes") {
            onClose()
            onNavigate(GsRoutes.chat("demo-3"))
        }
        DrawerRow("Brand voice guidelines") {
            onClose()
            onNavigate(GsRoutes.chat("demo-4"))
        }
        DrawerRow("Research: AI market") {
            onClose()
            onNavigate(GsRoutes.chat("demo-5"))
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        HorizontalDivider(color = Aeruo.OutlineDark, thickness = 1.dp)
        Spacer(Modifier.height(GsMotion.spaceL))

        DrawerLabel("Explore")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Chats", icon = Icons.Outlined.Edit) {
            onClose(); onNavigate(GsRoutes.CHATS)
        }
        DrawerRow("Explore", icon = Icons.Outlined.Explore) {
            onClose(); onNavigate(GsRoutes.EXPLORE)
        }
        DrawerRow("Create", icon = Icons.Outlined.AutoAwesome) {
            onClose(); onNavigate(GsRoutes.CREATE)
        }
        DrawerRow("Library", icon = Icons.Outlined.Bookmarks) {
            onClose(); onNavigate(GsRoutes.LIBRARY)
        }
        DrawerRow("Projects", icon = Icons.Outlined.Folder) {
            onClose(); onNavigate(GsRoutes.PROJECTS)
        }
        DrawerRow("Assistants", icon = Icons.Outlined.SmartToy) {
            onClose(); onNavigate(GsRoutes.ASSISTANTS)
        }
        DrawerRow("Models", icon = Icons.Outlined.Speed) {
            onClose(); onNavigate(GsRoutes.MODELS)
        }
        DrawerRow("Search", icon = Icons.Outlined.Search) {
            onClose(); onNavigate(GsRoutes.SEARCH)
        }

        Spacer(Modifier.height(GsMotion.spaceL))
        HorizontalDivider(color = Aeruo.OutlineDark, thickness = 1.dp)
        Spacer(Modifier.height(GsMotion.spaceL))

        DrawerLabel("Account")
        Spacer(Modifier.height(GsMotion.spaceS))
        DrawerRow("Upgrade plan", icon = Icons.Outlined.AutoAwesome) {
            onClose(); onNavigate(GsRoutes.BILLING)
        }
        DrawerRow("Notifications", icon = Icons.Outlined.Notifications) {
            onClose(); onNavigate(GsRoutes.NOTIFICATIONS)
        }
        DrawerRow("Profile", icon = Icons.Outlined.Person) {
            onClose(); onNavigate(GsRoutes.PROFILE)
        }
        DrawerRow("Settings", icon = Icons.Outlined.Settings) {
            onClose(); onNavigate(GsRoutes.SETTINGS)
        }

        Spacer(Modifier.height(GsMotion.spaceXL))
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Aeruo.TextMutedDark,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DrawerRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .kineticPress()
            .clickable(onClick = onClick)
            .padding(horizontal = GsMotion.spaceM, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = Aeruo.TextMutedDark,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(2.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = Aeruo.TextDark,
            maxLines = 1
        )
    }
}
