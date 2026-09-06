package com.grapsee.gsai.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsListItem
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.components.GsSectionHeader
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.rememberAuroraBrush

@Composable
fun ProfileScreen(onNavigate: (String) -> Unit) {
    GsScreenScaffold(
        title = "Profile",
        actions = {
            IconButton(onClick = { onNavigate(GsRoutes.SETTINGS) }) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = { onNavigate(GsRoutes.NOTIFICATIONS) }) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
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
                            text = "GA",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "Grapsee Admin",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "graphesee@gmail.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Monthly usage
            GsCard {
                Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)) {
                    Text(
                        text = "Monthly AI usage",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        UsageStat(value = "1,284", label = "Messages", modifier = Modifier.weight(1f))
                        UsageStat(value = "45m", label = "Voice", modifier = Modifier.weight(1f))
                        UsageStat(value = "32", label = "Images", modifier = Modifier.weight(1f))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(4.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(8.dp)
                                .background(rememberAuroraBrush(), RoundedCornerShape(4.dp))
                        )
                    }
                    Text(
                        text = "68% of monthly allowance · Resets in 12 days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Account
            Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
                GsSectionHeader(title = "Account")
                GsListItem(
                    title = "Personal information",
                    leading = { AccountIcon(Icons.Outlined.Person) },
                    onClick = {}
                )
                GsListItem(
                    title = "Subscription",
                    leading = { AccountIcon(Icons.Outlined.WorkspacePremium) },
                    trailing = { GsChip(text = "Pro", selected = true, onClick = {}) },
                    onClick = { onNavigate(GsRoutes.BILLING) }
                )
                GsListItem(
                    title = "Connected services",
                    leading = { AccountIcon(Icons.Outlined.Link) },
                    onClick = {}
                )
                GsListItem(
                    title = "Devices",
                    leading = { AccountIcon(Icons.Outlined.Devices) },
                    trailing = {
                        Text(
                            text = "3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {}
                )
                GsListItem(
                    title = "Security",
                    leading = { AccountIcon(Icons.Outlined.Shield) },
                    trailing = {
                        Text(
                            text = "Strong",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {}
                )
                GsListItem(
                    title = "Sessions",
                    leading = { AccountIcon(Icons.Outlined.History) },
                    trailing = {
                        Text(
                            text = "2 active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {}
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

@Composable
private fun UsageStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
