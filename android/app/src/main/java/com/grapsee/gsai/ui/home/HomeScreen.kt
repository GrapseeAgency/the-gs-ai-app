package com.grapsee.gsai.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.Aeruo
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress
import com.grapsee.gsai.ui.theme.rememberAuroraBrush
import java.util.Calendar

/**
 * AERUO KINETIC home canvas — benchmark pattern (ChatGPT · Claude · Kimi):
 * obsidian full-bleed, top bar (menu · model pill · new chat), centred brand
 * orb + time-aware serif greeting + upgrade pill, quick-action chips and one
 * hero input bar pinned to the bottom. Navigation lives in the drawer.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Aeruo.Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = GsMotion.spaceM)
        ) {
            TopBar(
                onOpenDrawer = onOpenDrawer,
                onNavigate = onNavigate
            )

            Spacer(Modifier.weight(1.1f))
            HeroBlock(onNavigate = onNavigate)
            Spacer(Modifier.weight(1f))

            SuggestionRows(onNavigate = onNavigate)
            Spacer(Modifier.height(GsMotion.spaceM))
            QuickChips(onNavigate = onNavigate)
            Spacer(Modifier.height(GsMotion.spaceM))
            HeroInput(onNavigate = onNavigate)
            Spacer(Modifier.height(GsMotion.spaceS))
            Text(
                "GS can make mistakes — double-check important info.",
                style = MaterialTheme.typography.labelMedium,
                color = Aeruo.TextMutedDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = GsMotion.spaceS),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun TopBar(
    onOpenDrawer: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GsMotion.spaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Menu — opens the drawer
        CircleButton(
            icon = Icons.Outlined.Menu,
            contentDescription = "Open menu",
            onClick = onOpenDrawer
        )

        Spacer(Modifier.weight(1f))

        // Model pill — "Instant High" pattern
        Surface(
            shape = RoundedCornerShape(GsMotion.radiusChip),
            color = Aeruo.RaisedDark,
            modifier = Modifier.kineticPress()
        ) {
            Row(
                modifier = Modifier.clickable { onNavigate(GsRoutes.MODELS) }.padding(
                    horizontal = GsMotion.spaceM,
                    vertical = 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(rememberAuroraBrush(CircleShape))
                )
                Text(
                    "GS Balanced · High",
                    style = MaterialTheme.typography.labelLarge,
                    color = Aeruo.TextDark
                )
            }
        }

        Spacer(Modifier.weight(1f))

        CircleButton(
            icon = Icons.Outlined.Add,
            contentDescription = "New chat",
            onClick = { onNavigate(GsRoutes.chat(null)) }
        )
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Aeruo.RaisedDark,
        modifier = Modifier
            .size(44.dp)
            .kineticPress()
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Aeruo.TextDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HeroBlock(onNavigate: (String) -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreathe"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brand orb — the one sanctioned aurora mark on the canvas
        Box(
            modifier = Modifier
                .size(84.dp)
                .scale(breathe)
                .clip(CircleShape)
                .background(rememberAuroraBrush(CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(Aeruo.Obsidian.copy(alpha = 0.35f))
            )
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "GS",
                tint = Aeruo.TextDark,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(GsMotion.spaceL))

        Text(
            greeting(),
            style = MaterialTheme.typography.displayLarge,
            color = Aeruo.TextDark,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceS))

        // Claude-style rotating tagline — one quiet line that slowly cycles
        RotatingTagline(modifier = Modifier)

        Spacer(Modifier.height(GsMotion.spaceM))

        // Upgrade pill — subtle, under the greeting (Kimi pattern)
        Surface(
            shape = RoundedCornerShape(GsMotion.radiusChip),
            color = Aeruo.RaisedDark,
            modifier = Modifier.kineticPress()
        ) {
            Row(
                modifier = Modifier.clickable { onNavigate(GsRoutes.BILLING) }.padding(
                    horizontal = GsMotion.spaceM,
                    vertical = 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Aeruo.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Upgrade plan",
                    style = MaterialTheme.typography.labelLarge,
                    color = Aeruo.TextDark
                )
            }
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 23..24 || hour in 0..4 -> "Up late, Admin?"
        hour in 5..11 -> "Good morning, Admin"
        hour in 12..17 -> "Good afternoon, Admin"
        else -> "Good evening, Admin"
    }
}

/** Time-aware tagline set; the hero crossfades one line every few seconds. */
private fun taglines(): List<String> {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val first = if (hour >= 23 || hour < 5) "Working while the world sleeps?" else "What should we make today?"
    return listOf(
        first,
        "Ask, build, refine — all in one thread.",
        "Your move. GS is listening."
    )
}

@Composable
private fun RotatingTagline(modifier: Modifier = Modifier) {
    val lines = remember { taglines() }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(TAGLINE_ROTATE_MS)
            index = (index + 1) % lines.size
        }
    }
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            fadeIn(tween(TAGLINE_FADE_MS)) togetherWith fadeOut(tween(TAGLINE_FADE_MS))
        },
        label = "tagline",
        modifier = modifier
    ) { current ->
        Text(
            lines[current],
            style = MaterialTheme.typography.bodyMedium,
            color = Aeruo.TextMutedDark
        )
    }
}

private const val TAGLINE_ROTATE_MS = 5_200L
private const val TAGLINE_FADE_MS = 700

@Composable
private fun SuggestionRows(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        SuggestionRow(Icons.Outlined.Description, "Summarise a PDF into a brief") {
            onNavigate(GsRoutes.chat(null))
        }
        SuggestionRow(Icons.Outlined.EditNote, "Draft a launch email") {
            onNavigate(GsRoutes.chat(null))
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .kineticPress()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
    ) {
        Surface(
            shape = CircleShape,
            color = Aeruo.RaisedDark,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Aeruo.TextDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Aeruo.TextDark
        )
    }
}

@Composable
private fun QuickChips(onNavigate: (String) -> Unit) {
    data class Chip(val label: String, val icon: ImageVector, val route: String)

    val chips = remember {
        listOf(
            Chip("Projects", Icons.Outlined.Folder, GsRoutes.PROJECTS),
            Chip("Research", Icons.Outlined.TravelExplore, GsRoutes.RESEARCH),
            Chip("Vision", Icons.Outlined.Visibility, GsRoutes.VISION),
            Chip("Image", Icons.Outlined.Palette, GsRoutes.IMAGE_STUDIO),
            Chip("Writing", Icons.Outlined.EditNote, GsRoutes.WRITING_STUDIO),
            Chip("Code", Icons.Outlined.Code, GsRoutes.CODE_WORKSPACE),
            Chip("Voice", Icons.Outlined.Mic, GsRoutes.VOICE),
            Chip("Library", Icons.Outlined.Bookmarks, GsRoutes.LIBRARY),
            Chip("Models", Icons.Outlined.Speed, GsRoutes.MODELS)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        chips.forEach { chip ->
            Surface(
                shape = RoundedCornerShape(GsMotion.radiusChip),
                color = Aeruo.RaisedDark,
                modifier = Modifier.kineticPress()
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onNavigate(chip.route) }
                        .padding(horizontal = GsMotion.spaceM, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        chip.icon,
                        contentDescription = null,
                        tint = Aeruo.TextMutedDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        chip.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Aeruo.TextDark
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroInput(onNavigate: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Aeruo.RaisedDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = GsMotion.spaceS,
                vertical = GsMotion.spaceS
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach — opens a new chat with the attach sheet
            Surface(
                shape = CircleShape,
                color = Aeruo.AccentSoftDark,
                modifier = Modifier
                    .size(42.dp)
                    .kineticPress()
            ) {
                Box(
                    modifier = Modifier.clickable { onNavigate(GsRoutes.chat(null)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Attach and new chat",
                        tint = Aeruo.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(GsMotion.spaceS))

            Text(
                "Ask anything",
                style = MaterialTheme.typography.bodyMedium,
                color = Aeruo.TextMutedDark,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigate(GsRoutes.chat(null)) }
                    .padding(vertical = GsMotion.spaceS)
            )

            // Mic — voice mode
            Surface(
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .size(42.dp)
                    .kineticPress()
            ) {
                Box(
                    modifier = Modifier.clickable { onNavigate(GsRoutes.VOICE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "Voice input",
                        tint = Aeruo.TextMutedDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Aurora wave — full voice mode (the hero affordance)
            Surface(
                shape = CircleShape,
                color = Aeruo.AccentSoftDark,
                modifier = Modifier
                    .size(44.dp)
                    .kineticPress()
            ) {
                Box(
                    modifier = Modifier
                        .background(rememberAuroraBrush(CircleShape), CircleShape)
                        .clickable { onNavigate(GsRoutes.VOICE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = "Voice mode",
                        tint = Aeruo.TextDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
