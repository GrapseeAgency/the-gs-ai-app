package com.grapsee.gsai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts

private val GsMetalCapsule = RoundedCornerShape(percent = 50)
private val GsSilverSweep = Brush.linearGradient(
    listOf(
        Color(0xFFFFFFFF),
        Color(0xFFC4CCD5),
        Color(0xFFF9FBFD),
        Color(0xFFAAB4C0),
        Color(0xFFEFF2F5),
    )
)

/** Compact brushed-steel label, drawn with Compose primitives (no shader runtime). */
@Composable
fun GsMetallicBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(2.dp, GsMetalCapsule, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(GsMetalCapsule)
            .background(GsSilverSweep)
            .border(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White, Color(0xFF8994A0), Color.White.copy(alpha = 0.92f))),
                GsMetalCapsule,
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            fontSize = 8.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = Color(0xFF38424C),
            maxLines = 1,
        )
    }
}

/** Visual half of the Auto switch; its parent row owns the real toggle semantics. */
@Composable
fun GsMetallicAutoControl(checked: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .shadow(3.dp, GsMetalCapsule, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.17f))
            .clip(GsMetalCapsule)
            .background(GsSilverSweep)
            .border(
                0.9.dp,
                Brush.verticalGradient(listOf(Color.White, Color(0xFF8994A0), Color.White.copy(alpha = 0.94f))),
                GsMetalCapsule,
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Auto",
            fontSize = 9.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = Color(0xFF46515C),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(
                    Brush.linearGradient(listOf(Color.White, Color(0xFFADB7C2))),
                    CircleShape,
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.95f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    GsIcons.Check,
                    contentDescription = null,
                    tint = Color(0xFF48535E),
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}
