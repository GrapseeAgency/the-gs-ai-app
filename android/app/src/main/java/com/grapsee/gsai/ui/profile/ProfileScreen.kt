package com.grapsee.gsai.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

/**
 * Profile — the honest version. The identity is the REAL stored account
 * (the name/email the user typed at sign-in, read from AccountStore — the
 * same source the drawer header uses). Nothing is invented: a blank store
 * degrades to neutral lines, never a fabricated name. The fake usage card
 * (1,284 messages / 45m voice / 32 images / 68% bar) and the fake
 * Devices/Security/Sessions values that used to live here are gone — no
 * data, no numbers. Below the identity: the only two destinations that
 * actually exist, Settings and Billing.
 */
@Composable
fun ProfileScreen(onNavigate: (String) -> Unit) {
    // AccountStore is a SharedPreferences read (same pattern as the drawer
    // account header): a stable snapshot per entry into this screen.
    val context = LocalContext.current
    val storedName = AccountStore.displayName(context)
    val storedEmail = AccountStore.email(context)

    GsScreenScaffold(title = "Profile") {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            // Identity
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            // Initials of the REAL stored name, or the neutral
                            // "GS" mark — never initials of a name that does
                            // not exist (mirrors the drawer's rule).
                            text = initialsFor(storedName),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = storedName.ifBlank { "GS account" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    // The stored email, else a neutral line — never a
                    // fabricated address.
                    text = storedEmail.ifBlank { "Signed in" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Account — navigation only. Every row is a real destination.
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                GsSectionHeader(title = "Account")
                GsListItem(
                    title = "Settings",
                    subtitle = "Appearance, privacy, security, accessibility",
                    leading = { AccountIcon(Icons.Outlined.Settings) },
                    onClick = { onNavigate(GsRoutes.SETTINGS) }
                )
                GsListItem(
                    title = "Billing",
                    subtitle = "GS AI is in early access",
                    leading = { AccountIcon(Icons.Outlined.CreditCard) },
                    onClick = { onNavigate(GsRoutes.BILLING) }
                )
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
        }
    }
}

@Composable
private fun AccountIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(20.dp)
            .padding(1.dp)
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
