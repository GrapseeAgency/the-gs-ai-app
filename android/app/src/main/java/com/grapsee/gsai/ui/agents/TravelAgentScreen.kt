package com.grapsee.gsai.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.R
import com.grapsee.gsai.ui.components.AnimatedGsIllustration
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsSpacing
import com.grapsee.gsai.ui.theme.GsTheme

@Composable
fun TravelAgentScreen(onBack: () -> Unit, onSendToChat: (String) -> Unit) {
    val colors = GsTheme.colors
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFB36D).copy(alpha = .24f), colors.appBackground, colors.appBackground)))
            .padding(horizontal = GsSpacing.m)
    ) {
        Spacer(Modifier.height(GsSpacing.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onBack, shape = CircleShape, color = colors.surface) {
                Icon(GsIcons.ChevronLeft, contentDescription = "Back", modifier = Modifier.padding(12.dp), tint = colors.textPrimary)
            }
            Spacer(Modifier.width(GsSpacing.s))
            Text("Travel Agent", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Surface(shape = CircleShape, color = colors.accentSoft) {
                Icon(GsIcons.Globe, contentDescription = "Travel tools", modifier = Modifier.padding(11.dp), tint = colors.accent)
            }
        }
        Spacer(Modifier.height(GsSpacing.xl))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AnimatedGsIllustration(R.drawable.gs_travel_agent_orb, "Travel Agent", 148.dp, shape = CircleShape, breathDuration = 4100, rotationDegrees = 1.6f, liftPixels = -4f, startDelayMillis = 260)
            Spacer(Modifier.height(GsSpacing.m))
            Text("Welcome to Travel Agent", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(GsSpacing.s))
            Text("Your own smart companion is here to make every journey feel easier.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Spacer(Modifier.height(GsSpacing.l))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(GsSpacing.s)) {
            AgentChip("Yesterday", GsIcons.History, onSendToChat)
            AgentChip("Creative", GsIcons.Wand, onSendToChat)
            AgentChip("Planning", GsIcons.Compass, onSendToChat)
        }
        Spacer(Modifier.height(GsSpacing.l))
        Surface(shape = GsRadius.cardShape(), color = colors.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(GsSpacing.m), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = colors.accentSoft) { Icon(GsIcons.Globe, contentDescription = null, modifier = Modifier.padding(10.dp), tint = colors.accent) }
                Spacer(Modifier.width(GsSpacing.s))
                Column(Modifier.weight(1f)) {
                    Text("Plan your next escape", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("Destinations, stays, routes, and beautiful details.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Icon(GsIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(GsSpacing.s))
        Surface(shape = GsRadius.cardShape(), color = Color.White.copy(alpha = .58f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(GsSpacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFFFFB169).copy(alpha = .35f)) {
                        Icon(GsIcons.Globe, contentDescription = null, modifier = Modifier.padding(9.dp), tint = colors.accent)
                    }
                    Spacer(Modifier.width(GsSpacing.s))
                    Text("Travel Agent", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                }
                Spacer(Modifier.height(GsSpacing.s))
                Text("Tell me where you want to go, and I’ll shape the whole journey around you.", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                Spacer(Modifier.height(GsSpacing.m))
                Row(horizontalArrangement = Arrangement.spacedBy(GsSpacing.s)) {
                    MediaAction("Gif", GsIcons.Image, onSendToChat)
                    MediaAction("Image", GsIcons.ImagePlus, onSendToChat)
                    MediaAction("Video", GsIcons.Presentation, onSendToChat)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(onClick = { onSendToChat("Help me plan a thoughtful trip") }, shape = GsRadius.inputShape(), color = colors.surface, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = GsSpacing.m, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ask me anything...", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = colors.accent) { Icon(GsIcons.ArrowUp, contentDescription = "Send", modifier = Modifier.padding(9.dp), tint = colors.onAccent) }
            }
        }
        Spacer(Modifier.height(GsSpacing.l))
    }
}

@Composable
private fun MediaAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onSend: (String) -> Unit) {
    Surface(onClick = { onSend("Generate a $label for my trip") }, shape = GsRadius.pill, color = GsTheme.colors.accentSoft) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = GsTheme.colors.accent)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun AgentChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onSend: (String) -> Unit) {
    Surface(onClick = { onSend("Open my $label travel ideas") }, shape = GsRadius.pill, color = GsTheme.colors.surface, shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = GsTheme.colors.accent)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textPrimary)
        }
    }
}
