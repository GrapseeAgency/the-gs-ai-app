package com.grapsee.gsai.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.R
import com.grapsee.gsai.ui.components.AnimatedGsIllustration
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsSpacing
import com.grapsee.gsai.ui.theme.GsTheme

/** The reference’s first impression: calm, visual, and emotionally inviting. */
@Composable
fun ReferenceWelcomeScreen(onExplore: () -> Unit, onSkip: () -> Unit) {
    val colors = GsTheme.colors
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFFB36D).copy(alpha = .30f), Color(0xFFFFF4EF), colors.appBackground))
        ).padding(horizontal = GsSpacing.m)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = GsSpacing.s), horizontalArrangement = Arrangement.End) {
            Surface(onClick = onSkip, shape = GsRadius.pill, color = Color.White.copy(alpha = .42f)) {
                Text("Skip", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = colors.textPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(GsSpacing.xl))
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = GsRadius.pill, color = Color.White.copy(alpha = .46f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(GsIcons.Wand, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accentStrong)
                    Spacer(Modifier.size(7.dp))
                    Text("Smarter digital assistant", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                }
            }
            Spacer(Modifier.height(GsSpacing.l))
            AnimatedGsIllustration(R.drawable.gs_companion_orb, "GS AI companion", 168.dp, shape = CircleShape, breathDuration = 2900, rotationDegrees = 1.1f, liftPixels = -2f, startDelayMillis = 120)
            Spacer(Modifier.height(GsSpacing.xl))
            Text("Your smarter digital assistant", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("A calmer way to think, create, explore, and get things done.", modifier = Modifier.padding(top = GsSpacing.s), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Speak, ask, solve.", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(GsSpacing.m))
            Surface(onClick = onExplore, modifier = Modifier.fillMaxWidth(), shape = GsRadius.inputShape(), color = colors.accent, shadowElevation = 4.dp) {
                Text("Explore now", modifier = Modifier.padding(vertical = 17.dp), textAlign = TextAlign.Center, color = colors.onAccent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(GsSpacing.l))
        }
    }
}
