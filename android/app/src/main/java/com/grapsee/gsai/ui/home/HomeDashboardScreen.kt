package com.grapsee.gsai.ui.home

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.R
import com.grapsee.gsai.ui.components.AnimatedGsIllustration
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsSpacing
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * Native consumer home. This is intentionally a real product surface rather
 * than a demo: every primary action enters the existing chat route, while the
 * cards are pure presentation and never invent backend state.
 */
@Composable
fun HomeDashboardScreen(
    onNewChat: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenAgent: () -> Unit,
) {
    val colors = GsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFB36D).copy(alpha = .28f), colors.appBackground, colors.appBackground)
                )
            )
            .padding(horizontal = GsSpacing.m),
    ) {
        Spacer(Modifier.height(GsSpacing.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(GsIcons.PanelLeft, contentDescription = "Open menu", tint = colors.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("GS AI", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text("Your smarter companion", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Surface(shape = CircleShape, color = colors.accentSoft) {
                IconButton(onClick = onOpenChats) {
                    Icon(GsIcons.History, contentDescription = "Conversation history", tint = colors.accent)
                }
            }
        }
        Spacer(Modifier.height(GsSpacing.s))
        HeroVisual(onClick = onNewChat)
        Spacer(Modifier.height(GsSpacing.xl))
        Text("Intelligence reimagined.", style = MaterialTheme.typography.displayMedium, color = colors.textPrimary)
        Text("Discover a smarter world.", style = MaterialTheme.typography.headlineSmall, color = colors.accentStrong)
        Spacer(Modifier.height(GsSpacing.gap))
        Text(
            "Ask, create, explore, and get things done with your personal AI companion.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(GsSpacing.l))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GsSpacing.s),
        ) {
            QuickChip("Start new chat", GsIcons.SquarePen, onNewChat, selected = true)
            QuickChip("Gemini 3", GsIcons.Brain, onNewChat)
            QuickChip("Grok 4.1", GsIcons.Zap, onNewChat)
        }
        Spacer(Modifier.height(GsSpacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(GsSpacing.s)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                title = "Video\ncreate",
                subtitle = "Create stunning videos with AI",
                icon = GsIcons.Presentation,
                colors = listOf(Color(0xFFFFD4B0), Color(0xFFFFF7F1)),
                onClick = onNewChat,
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                title = "Mind\nguide",
                subtitle = "A calmer place to think",
                icon = GsIcons.Lightbulb,
                colors = listOf(Color(0xFFFFC7B1), Color(0xFFFFF4EF)),
                onClick = onOpenAgent,
            )
        }
        Spacer(Modifier.height(GsSpacing.l))
        ReflectionCard(onClick = onNewChat)
        Spacer(Modifier.height(GsSpacing.l))
        UsageStats()
        Spacer(Modifier.height(GsSpacing.l))
        SectionTitle("Today’s focus", "See all", onOpenChats)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = GsRadius.cardShape(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(Modifier.padding(GsSpacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = colors.accentSoft) {
                        Icon(GsIcons.Lightbulb, contentDescription = null, modifier = Modifier.padding(10.dp), tint = colors.accent)
                    }
                    Spacer(Modifier.width(GsSpacing.s))
                    Column(Modifier.weight(1f)) {
                        Text("Suggestions", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text("Try a thoughtful question first", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                    Icon(GsIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary)
                }
                Spacer(Modifier.height(GsSpacing.m))
                LinearProgressIndicator(
                    progress = { .64f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = colors.accent,
                    trackColor = colors.accentSoft,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        BottomActionDock(onGenerate = onNewChat, onHistory = onOpenChats)
        Spacer(Modifier.height(GsSpacing.l))
    }
}

@Composable
private fun BottomActionDock(onGenerate: () -> Unit, onHistory: () -> Unit) {
    Surface(shape = GsRadius.inputShape(), color = GsTheme.colors.surface, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DockIcon(GsIcons.ImagePlus, "Image", onGenerate)
            DockIcon(GsIcons.Presentation, "Video", onGenerate)
            Surface(onClick = onGenerate, shape = GsRadius.pill, color = GsTheme.colors.accent, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(GsIcons.Wand, contentDescription = null, modifier = Modifier.size(16.dp), tint = GsTheme.colors.onAccent)
                    Spacer(Modifier.width(6.dp))
                    Text("Generate", color = GsTheme.colors.onAccent, fontWeight = FontWeight.SemiBold)
                }
            }
            DockIcon(GsIcons.History, "History", onHistory)
            DockIcon(GsIcons.Settings, "Settings", onGenerate)
        }
    }
}

@Composable
private fun DockIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = GsTheme.colors.accentSoft) {
        Icon(icon, contentDescription = label, modifier = Modifier.padding(11.dp).size(17.dp), tint = GsTheme.colors.accentStrong)
    }
}

@Composable
private fun UsageStats() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GsSpacing.s)) {
        UsageTile("Image generate", "64%", .64f, Modifier.weight(1f))
        UsageTile("Video generate", "15%", .15f, Modifier.weight(1f))
    }
}

@Composable
private fun UsageTile(label: String, value: String, progress: Float, modifier: Modifier) {
    Surface(modifier = modifier, shape = GsRadius.cardShape(), color = GsTheme.colors.surface) {
        Column(Modifier.padding(GsSpacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = GsTheme.colors.accentStrong, modifier = Modifier.weight(1f))
                Icon(GsIcons.Gauge, contentDescription = null, modifier = Modifier.size(16.dp), tint = GsTheme.colors.textSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = GsTheme.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = GsTheme.colors.accent, trackColor = GsTheme.colors.accentSoft)
        }
    }
}

@Composable
private fun ReflectionCard(onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = GsRadius.cardShape(), colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFFFE3D2), Color(0xFFFFC6B7)))).padding(GsSpacing.m)) {
            AnimatedGsIllustration(R.drawable.gs_companion_orb, "Mind guide", 98.dp, Modifier.align(Alignment.BottomEnd).alpha(.22f), breathDuration = 3600, rotationDegrees = 1.4f, liftPixels = -2f)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .65f)) {
                        Icon(GsIcons.Lightbulb, contentDescription = null, modifier = Modifier.padding(9.dp), tint = GsTheme.colors.accentStrong)
                    }
                    Spacer(Modifier.width(GsSpacing.s))
                    Text("Today's reflection", style = MaterialTheme.typography.titleMedium, color = GsTheme.colors.textPrimary)
                }
                Spacer(Modifier.height(GsSpacing.s))
                Text("What are you holding onto that you could gently release?", style = MaterialTheme.typography.bodyMedium, color = GsTheme.colors.textPrimary)
                Spacer(Modifier.height(GsSpacing.s))
                Text("Open mind guide  →", style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.accentStrong, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroVisual(onClick: () -> Unit) {
    val colors = GsTheme.colors
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(204.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFFFFD1A7), Color(0xFFFF9B62), Color(0xFFFF7B45)))
            )
        ) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                shape = GsRadius.pill,
                color = Color.White.copy(alpha = .40f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(GsIcons.Brain, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("GS-5 nano", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                    Icon(GsIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textSecondary)
                }
            }
            AnimatedGsIllustration(R.drawable.gs_companion_orb, "Start with GS AI", 112.dp, Modifier.align(Alignment.Center), CircleShape)
            Text("A calmer way to create", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, selected: Boolean = false) {
    val colors = GsTheme.colors
    Surface(onClick = onClick, shape = GsRadius.pill, color = if (selected) colors.accent else colors.surface, shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (selected) colors.onAccent else colors.textPrimary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.onAccent else colors.textPrimary)
        }
    }
}

@Composable
private fun FeatureCard(modifier: Modifier, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: List<Color>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(164.dp), shape = GsRadius.cardShape(), colors = CardDefaults.cardColors(containerColor = colors.first()), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors)).padding(GsSpacing.m)) {
            Column {
                if (title.startsWith("Video")) {
                    AnimatedGsIllustration(R.drawable.gs_video_create, "Video creation", 54.dp, shape = RoundedCornerShape(16.dp), breathDuration = 3000, rotationDegrees = 2.8f, liftPixels = -2f)
                } else {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .72f)) { Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp), tint = GsTheme.colors.accentStrong) }
                }
                Spacer(Modifier.weight(1f))
                Text(title, style = MaterialTheme.typography.titleMedium, color = GsTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GsTheme.colors.textSecondary, maxLines = 2)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = GsTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Surface(onClick = onAction, shape = GsRadius.pill, color = Color.Transparent) { Text(action, modifier = Modifier.padding(8.dp), color = GsTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium) }
    }
}
