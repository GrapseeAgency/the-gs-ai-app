package com.grapsee.gsai.ui.settings

import android.content.Context
import android.os.Build
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.PlanStore
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefAvatar
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefStatusPill
import com.grapsee.gsai.ui.components.RefToggleRow
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * Account & Security — docs/reference/pending/account-security.png.
 *
 * The sheet's groups, rows, wells and pills are reproduced in its order. What
 * changes is the data behind them, because the mock draws a product this
 * build does not have: the owner's instruction was explicit — "those are
 * unreal not real… people will only have to use the Grapsee to login the
 * nexly… nothing else".
 *
 * So Google and Apple are drawn in their slots but say "Not connected", and
 * there is no "Add another sign-in method" destination to promise. There is no
 * password store and no server, so Change Password and Two-Factor state the
 * truth rather than offering a flow that ends nowhere.
 *
 * What IS real, and therefore live:
 *  - the identity: the name and email the user actually typed (AccountStore);
 *  - the plan pill: PlanStore, the same source as Subscription & Usage;
 *  - the session: this install's own client id and the device it is running
 *    on, read from Build — not an invented "Windows • Chrome • Dhaka";
 *  - Biometric Login: a genuine BiometricPrompt gate, stored in SettingsStore
 *    and read by the app-lock check below.
 */
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors

    val name = AccountStore.displayName(context)
    val email = AccountStore.email(context)

    var biometricChecked by remember { mutableStateOf(SettingsStore.biometricUnlock) }
    var lastCheckMillis by remember { mutableStateOf(SettingsStore.lastSecurityCheckMillis) }

    // Framework BiometricPrompt (API 28+). androidx.biometric would need a
    // FragmentActivity and MainActivity is a plain ComponentActivity, so using
    // it here would compile into a switch that can never fire.
    val biometricCanRun = remember { canUseBiometrics(context) }

    // The session is real: this install's id and the hardware it runs on.
    val deviceLabel = listOf(
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        Build.MODEL,
    ).distinct().joinToString(" ")
    val sessionId = remember { SessionStore.stableClientId(context) }

    fun promptBiometric(onSuccess: () -> Unit) {
        promptBiometric(context, onSuccess)
    }

    RefSettingsPage(
        title = "Account & Security",
        subtitle = "Keep your account safe and in your control",
        onBack = onBack,
    ) {
        // ---- Profile ------------------------------------------------------
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(14.dp)) {
            RefNavRow(
                title = name.ifBlank { "Nexly account" },
                subtitle = email.ifBlank { "No Grapsee address on this device" },
                onClick = { onNavigate(GsRoutes.PROFILE) },
                leading = { RefAvatar(initials = initialsFor(name), size = 48.dp) },
                trailing = {
                    // The pill only claims a plan that was actually activated.
                    // There is no purchase path in this build, so an install
                    // that has not activated says so instead of advertising a
                    // subscription nobody bought.
                    if (PlanStore.active) {
                        RefStatusPill(PlanStore.planName)
                    } else {
                        RefStatusPill("Not subscribed", GsTheme.colors.textSecondary)
                    }
                },
            )
        }

        // ---- Sign-in Methods ---------------------------------------------
        RefGroupHeader("Sign-in Methods", "Manage how you sign in")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Grapsee Account",
                subtitle = email.ifBlank { "Not set" },
                // The one real credential: the Grapsee address this install
                // signs in with. Editing it is the profile editor.
                onClick = { onNavigate(GsRoutes.PROFILE) },
                leading = { RefWellTile(GsIcons.AtSign, GsWellTone.Lilac, 28.dp) },
                trailing = { RefStatusPill("Primary") },
            )
            RefRowDivider()
            // Google and Apple keep the mock's slots but carry the truth: there
            // is no OAuth client in this build, so they cannot be connected.
            RefNavRow(
                title = "Google",
                subtitle = "Not connected",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Mail, GsWellTone.Rose, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Apple",
                subtitle = "Not connected",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.Lock, GsWellTone.Blue, 28.dp) },
            )
        }

        // ---- Password & Authentication -----------------------------------
        RefGroupHeader("Password & Authentication", "Manage your security")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Change Password",
                // No password is stored on this device and no server holds one,
                // so there is no flow to open.
                subtitle = "Grapsee accounts sign in without a stored password here",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.KeyRound, GsWellTone.Rose, 28.dp) },
            )
            RefRowDivider()
            RefNavRow(
                title = "Two-Factor Authentication",
                subtitle = "Not available — no account server to enrol with",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.ShieldCheck, GsWellTone.Lilac, 28.dp) },
            )
            RefRowDivider()
            RefToggleRow(
                title = "Biometric Login",
                subtitle = if (biometricCanRun) {
                    "Ask for your fingerprint or face when the app opens"
                } else {
                    "No biometric hardware enrolled on this device"
                },
                checked = biometricChecked,
                enabled = biometricCanRun,
                onCheckedChange = { want ->
                    if (!want) {
                        SettingsStore.updateBiometricUnlock(false)
                        biometricChecked = false
                        return@RefToggleRow
                    }
                    // A lock is only real once the user has proved they can
                    // pass it. Enabling it blind would lock them out.
                    promptBiometric {
                        SettingsStore.updateBiometricUnlock(true)
                        biometricChecked = true
                        val now = System.currentTimeMillis()
                        SettingsStore.updateLastSecurityCheck(now)
                        lastCheckMillis = now
                    }
                },
                leading = { RefWellTile(GsIcons.Fingerprint, GsWellTone.Rose, 28.dp) },
            )
        }

        // ---- Active Sessions ---------------------------------------------
        RefGroupHeader("Active Sessions", "See where you're logged in")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "Current Session",
                // Real: this install, on the hardware it is running on.
                subtitle = "$deviceLabel • Android ${Build.VERSION.RELEASE} • ${sessionId.take(8)}",
                onClick = null,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RefStatusPill("This device")
                    }
                },
                leading = { RefWellTile(GsIcons.Smartphone, GsWellTone.Blue, 28.dp) },
            )
        }

        // ---- Manage Devices ----------------------------------------------
        RefGroupHeader("Manage Devices", "Control your devices")
        RefCard(role = GsGlassRole.CardLight) {
            RefNavRow(
                title = "All Devices",
                // There is no device registry anywhere in this codebase, so this
                // row states the reality instead of promising a list.
                subtitle = "Nexly stores no device list — this install is the only session",
                onClick = null,
                trailing = {},
                leading = { RefWellTile(GsIcons.MonitorSmartphone, GsWellTone.Mint, 28.dp) },
            )
        }

        Spacer(Modifier.height(14.dp))
        RefInfoCard(
            icon = GsIcons.ShieldCheck,
            title = "Your account is protected",
            text = if (lastCheckMillis > 0L) {
                "Last security check: ${formatStamp(lastCheckMillis)}"
            } else {
                "No security check yet — turn on Biometric Login to require one"
            },
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** Initials for the avatar, from whatever the user actually typed. */
private fun initialsFor(name: String): String {
    val parts = name.trim().split(' ').filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "NX"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

/** Real timestamp, device-local, no invented "Today, 9:12 AM". */
private fun formatStamp(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val fmt = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
    return fmt.format(cal.time)
}

/**
 * Biometric Login is only offered where the device can actually answer it:
 * API 28+ (framework BiometricPrompt) with a secure lock screen enrolled.
 */
private fun canUseBiometrics(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
    return keyguard.isDeviceSecure
}

/**
 * The real check behind the switch. `view.context` is the Activity, which the
 * framework requires; a success is the only thing that enables the preference,
 * so the lock can never be switched on by someone who cannot pass it.
 */
private fun promptBiometric(context: Context, onSuccess: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt.Builder(context)
        .setTitle("Confirm it's you")
        .setSubtitle("Biometric Login")
        .setNegativeButton("Cancel", executor) { _, _ -> }
        .build()
    prompt.authenticate(
        CancellationSignal(),
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult?,
            ) {
                onSuccess()
            }
        },
    )
}
