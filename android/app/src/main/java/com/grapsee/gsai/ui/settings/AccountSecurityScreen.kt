package com.grapsee.gsai.ui.settings

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.chat.content.NexlyGlyph
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefRowValue
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetRow
import com.grapsee.gsai.ui.components.RefSheetToggle
import com.grapsee.gsai.ui.components.RefTagPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * Account & Security — docs/reference/pending/account-security.png.
 *
 * The sheet's groups, rows, wells and pills are reproduced in its order and
 * at its own scale (13/9 headers, 10/8 rows, 34×20 switches). What changes is
 * the data behind them, because the mock draws a product this build does not
 * have: the owner's instruction was explicit — "those are unreal not real…
 * people will only have to use the Grapsee to login the nexly… nothing else".
 *
 * So Google and Apple are drawn in their slots but say "Not connected", and
 * there is no destination behind them (contract: no chevron). "Add another
 * sign-in method" keeps its plus well and states that Grapsee Mail is the only
 * credential. There is no password store and no account server, so Change
 * Password and Two-Factor state the truth rather than offering a flow that
 * ends nowhere.
 *
 * What IS real, and therefore live:
 *  - the identity: the name and email the user actually typed (AccountStore);
 *  - the plan pill: PlanStore's plan name (the one plan this build has);
 *  - the session: this install's own client id and the device it is running
 *    on, read from Build — not an invented "Windows • Chrome • Dhaka";
 *  - Biometric Login: a genuine BiometricPrompt gate, stored in SettingsStore
 *    and read by the app-lock check;
 *  - the two "..." surfaces on this screen (session facts, security facts) are
 *    real dialogs over these same values.
 */
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current

    val name = AccountStore.displayName(context)
    val email = AccountStore.email(context)

    var biometricChecked by remember { mutableStateOf(SettingsStore.biometricUnlock) }
    var lastCheckMillis by remember { mutableStateOf(SettingsStore.lastSecurityCheckMillis) }
    var showSessionFacts by remember { mutableStateOf(false) }
    var showSecurityFacts by remember { mutableStateOf(false) }

    // Framework BiometricPrompt (API 28+). androidx.biometric would need a
    // FragmentActivity and MainActivity is a plain ComponentActivity, so using
    // it here would compile into a switch that can never fire.
    val biometricCanRun = remember { com.grapsee.gsai.ui.components.canUseBiometrics(context) }

    // The session is real: this install's id and the hardware it runs on.
    val deviceLabel = listOf(
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        Build.MODEL,
    ).distinct().joinToString(" ")
    val sessionId = remember { SessionStore.stableClientId(context) }

    RefSettingsPage(
        title = "Account & Security",
        subtitle = "Keep your account safe and in your control",
        onBack = onBack,
    ) {
        // ---- Profile ------------------------------------------------------
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoIndication { onNavigate(GsRoutes.PROFILE) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(com.grapsee.gsai.R.drawable.gs_home_avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name.ifBlank { "Grapsee account" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GsFonts.Product,
                            color = GsTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            GsIcons.BadgeCheck,
                            contentDescription = null,
                            tint = GsWellTone.Blue.ink,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        email.ifBlank { "No Grapsee address on this device" },
                        fontSize = 9.sp,
                        fontFamily = GsFonts.Product,
                        color = GsTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    PlanPill()
                }
                com.grapsee.gsai.ui.components.DefaultChevron()
            }
        }

        // ---- Sign-in Methods ---------------------------------------------
        RefGroupHeader("Sign-in Methods", "Manage how you sign in")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            RefSheetRow(
                title = "Grapsee Mail",
                subtitle = email.ifBlank { "Not set" },
                // The one real credential: the address this install signs in
                // with. Editing it is the profile editor.
                onClick = { onNavigate(GsRoutes.PROFILE) },
                leading = { RefWellTile(GsIcons.Mail, GsWellTone.Lilac, 34.dp) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RefTagPill("Primary", GsWellTone.Mint.ink)
                        Spacer(Modifier.width(8.dp))
                        com.grapsee.gsai.ui.components.DefaultChevron()
                    }
                },
            )
            RefRowDivider(startIndent = 57.dp)
            // Google and Apple keep the mock's slots but carry the truth: there
            // is no OAuth client in this build, so they cannot be connected —
            // and no chevron, because there is nowhere to go.
            RefSheetRow(
                title = "Google",
                subtitle = "Not connected",
                onClick = null,
                trailing = {},
                leading = {
                    RefBrandWellFor("brand-google")
                },
            )
            RefRowDivider(startIndent = 57.dp)
            RefSheetRow(
                title = "Apple",
                subtitle = "Not connected",
                onClick = null,
                trailing = {},
                leading = { RefBrandWellFor("brand-apple") },
            )
            RefRowDivider(startIndent = 57.dp)
            RefSheetRow(
                title = "Add another sign-in method",
                subtitle = "Grapsee Mail is the only sign-in — use a different provider",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Plus, GsWellTone.Blue, 34.dp) },
            )
        }

        // ---- Password & Authentication -----------------------------------
        RefGroupHeader("Password & Authentication", "Manage your security")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            RefSheetRow(
                title = "Change Password",
                // No password is stored on this device and no server holds one,
                // so there is no flow to open.
                subtitle = "Grapsee Mail signs you in — no password is stored here",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Lock, GsWellTone.Rose, 34.dp) },
            )
            RefRowDivider(startIndent = 57.dp)
            // The mock's off switch stays in its slot, drawn disabled: there is
            // no account server to enrol a second factor with, so it cannot be
            // flipped on. A disabled switch is the truth; a hidden one is not
            // the sheet.
            RefSheetToggle(
                title = "Two-Factor Authentication",
                subtitle = "Not available — no account server to enrol with",
                checked = false,
                onCheckedChange = {},
                enabled = false,
                leading = { RefWellTile(GsIcons.ShieldCheck, GsWellTone.Lilac, 34.dp) },
            )
            RefRowDivider(startIndent = 57.dp)
            RefSheetToggle(
                title = "Biometric Login",
                subtitle = if (biometricCanRun) {
                    "Use fingerprint or face ID"
                } else {
                    "No biometric hardware enrolled on this device"
                },
                checked = biometricChecked,
                enabled = biometricCanRun,
                onCheckedChange = { want ->
                    if (!want) {
                        SettingsStore.updateBiometricUnlock(false)
                        biometricChecked = false
                        return@RefSheetToggle
                    }
                    // A lock is only real once the user has proved they can
                    // pass it. Enabling it blind would lock them out.
                    com.grapsee.gsai.ui.components.promptBiometric(
                        context = context,
                        title = "Confirm it's you",
                        subtitle = "Biometric Login",
                        onSuccess = {
                            SettingsStore.updateBiometricUnlock(true)
                            biometricChecked = true
                            val now = System.currentTimeMillis()
                            SettingsStore.updateLastSecurityCheck(now)
                            lastCheckMillis = now
                        },
                    )
                },
                leading = { RefWellTile(GsIcons.Fingerprint, GsWellTone.Rose, 34.dp) },
            )
        }

        // ---- Active Sessions ---------------------------------------------
        RefGroupHeader("Active Sessions", "See where you're logged in")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            RefSheetRow(
                title = "Current Session",
                // Real: this install, on the hardware it is running on.
                subtitle = "$deviceLabel • Android ${Build.VERSION.RELEASE} • ${sessionId.take(8)}",
                onClick = { showSessionFacts = true },
                leading = { RefWellTile(GsIcons.Smartphone, GsWellTone.Blue, 34.dp) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(GsWellTone.Mint.ink),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "This device",
                            fontSize = 8.sp,
                            fontFamily = GsFonts.Product,
                            color = GsTheme.colors.textTertiary,
                        )
                        Spacer(Modifier.width(6.dp))
                        com.grapsee.gsai.ui.components.DefaultChevron()
                    }
                },
            )
            RefRowDivider(startIndent = 57.dp)
            RefSheetRow(
                title = "Other Sessions",
                // There is no second session anywhere: this install is the
                // only one. The mock's "2 hours ago" mobile row would have to
                // be invented, so the slot says what is true.
                subtitle = "No other device is signed in",
                onClick = null,
                trailing = { RefRowValue("None") },
                leading = { RefWellTile(GsIcons.MonitorSmartphone, GsWellTone.Rose, 34.dp) },
            )
        }

        // ---- Manage Devices ----------------------------------------------
        RefGroupHeader("Manage Devices", "Control your devices")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 2.dp)) {
            RefSheetRow(
                title = "All Devices",
                // There is no device registry anywhere in this codebase, so this
                // row states the reality instead of promising a list.
                subtitle = "This install is the only signed-in device",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.MonitorSmartphone, GsWellTone.Mint, 34.dp) },
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
        ) {
            RefInfoCard(
                icon = GsIcons.ShieldCheck,
                title = "Your account is protected",
                text = if (lastCheckMillis > 0L) {
                    "Last security check: ${formatStamp(lastCheckMillis)}"
                } else {
                    "No security check yet — turn on Biometric Login to require one"
                },
                trailing = {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            GsIcons.ChevronRight,
                            contentDescription = null,
                            tint = GsTheme.colors.textTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                },
            )
            // The whole card is the tap target for the real facts sheet.
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .clickableNoIndication { showSecurityFacts = true },
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    if (showSessionFacts) {
        FactDialog(
            title = "Current session",
            facts = listOf(
                "Device" to deviceLabel,
                "Platform" to "Android ${Build.VERSION.RELEASE}",
                "Session" to sessionId,
                "Signed in with" to email.ifBlank { "Grapsee Mail" },
            ),
            onDismiss = { showSessionFacts = false },
        )
    }
    if (showSecurityFacts) {
        FactDialog(
            title = "Your account is protected",
            facts = listOf(
                "App lock" to if (SettingsStore.biometricUnlock) "Biometric Login on" else "Off",
                "Last security check" to if (lastCheckMillis > 0L) formatStamp(lastCheckMillis) else "None yet",
                "Plan" to PlanStore.planName,
                "Two-factor" to "Not available in this build",
            ),
            onDismiss = { showSecurityFacts = false },
        )
    }
}

/** The mock's plan pill: crown + plan name on the lilac wash. */
@Composable
private fun PlanPill() {
    val tone = GsWellTone.Lilac
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.wash.copy(alpha = 0.75f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexlyGlyph(name = "crown", size = 9.dp, color = tone.ink)
        Spacer(Modifier.width(3.dp))
        Text(
            PlanStore.planName,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = tone.ink,
            maxLines = 1,
        )
    }
}

/** A brand mark on white paper: the provider rows' leading slot. */
@Composable
private fun RefBrandWellFor(glyph: String) {
    com.grapsee.gsai.ui.components.RefBrandWell(glyph = glyph, size = 34.dp)
}

@Composable
private fun FactDialog(
    title: String,
    facts: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    val colors = GsTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                fontFamily = GsFonts.Product,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                facts.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            label,
                            modifier = Modifier.width(108.dp),
                            fontSize = 11.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textSecondary,
                        )
                        Text(
                            value,
                            fontSize = 11.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    )

/** Real timestamp, device-local, no invented "Today, 9:12 AM". */
private fun formatStamp(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val fmt = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
    return fmt.format(cal.time)
}
