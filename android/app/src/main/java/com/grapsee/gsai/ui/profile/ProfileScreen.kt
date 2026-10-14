package com.grapsee.gsai.ui.profile

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.ui.components.gsContentWidth
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.kineticPress

/**
 * Profile — the honest version, DESIGN 3 IDENTITY. The screen opens with a
 * 34sp bold large title sitting straight on the background (no boxed
 * header), then the identity: a 96dp primary monogram circle, the real
 * stored name at 22sp bold, the email at 14sp. Below, the Account group is
 * ONE flat grouped container (16dp radius, surfaceContainer fill, no
 * border, no shadow) with hairline separators indented to the row text —
 * not per-row cards.
 *
 * The identity is the REAL stored account (the name/email the user typed at
 * sign-in, read from AccountStore — the same source the drawer footer
 * uses). Nothing is invented: a blank store degrades to neutral lines,
 * never a fabricated name. The fake usage card and the fake
 * Devices/Security/Sessions values that used to live here are gone — no
 * data, no numbers. Below the identity: the only two destinations that
 * actually exist, Settings and Billing.
 */
@Composable
fun ProfileScreen(onNavigate: (String) -> Unit) {
    // AccountStore is a SharedPreferences read (same pattern as the drawer
    // account footer): a stable snapshot per entry into this screen.
    val context = LocalContext.current
    val storedName = AccountStore.displayName(context)
    val storedEmail = AccountStore.email(context)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .gsContentWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = GsMotion.spaceM, bottom = GsMotion.spaceL)
    ) {
        // Large title — the ramp's top step: 34sp bold, zero tracking,
        // onBackground, left-aligned, straight on the background.
        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 34.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(horizontal = GsMotion.spaceM)
                .padding(bottom = 8.dp)
        )

        // Identity — centered: monogram, name, email.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GsMotion.spaceL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    // Initials of the REAL stored name, or the neutral "GS"
                    // mark — never initials of a name that does not exist
                    // (mirrors the drawer footer's rule).
                    text = initialsFor(storedName),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = storedName.ifBlank { "GS account" },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                // The stored email, else a neutral line — never a
                // fabricated address.
                text = storedEmail.ifBlank { "Signed in" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Account — ONE flat grouped container, rows separated by indented
        // hairlines. Every row is a real destination.
        Text(
            text = "Account".uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = GsMotion.spaceM)
                .padding(top = GsMotion.spaceM, bottom = GsMotion.spaceS)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AccountRow(
                title = "Settings",
                subtitle = "Appearance, privacy, security, accessibility",
                icon = GsIcons.Settings,
                onClick = { onNavigate(GsRoutes.SETTINGS) }
            )
            GroupSeparator()
            AccountRow(
                title = "Billing",
                subtitle = "GS AI is in early access",
                icon = GsIcons.CreditCard,
                onClick = { onNavigate(GsRoutes.BILLING) }
            )
        }

        Spacer(modifier = Modifier.height(GsMotion.spaceL))
    }
}

/**
 * One grouped row: min 56dp, 16dp inner padding, the 40dp/12dp row glyph
 * container, a 16sp/500 title over a 14sp supporting line, trailing
 * ChevronRight. Press feedback via kineticPress — reduce-motion holds it
 * (the existing gate inside the modifier).
 */
@Composable
private fun AccountRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .kineticPress(rowInteraction)
            .clickable(
                interactionSource = rowInteraction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Open"
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = GsMotion.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = GsIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Hairline between grouped rows: 1dp outlineVariant, indented 68dp from the
 * left so it starts at the row TEXT (16 inner + 40 glyph + 12 gap) and ends
 * 16dp from the right — not full-bleed.
 */
@Composable
private fun GroupSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * Initials from the REAL stored name: the first letter of each of the first
 * two words, uppercased. No stored name → the neutral "GS" mark.
 */
private fun initialsFor(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "GS"
    return words.take(2).map { it.first().uppercaseChar() }.joinToString("")
}
