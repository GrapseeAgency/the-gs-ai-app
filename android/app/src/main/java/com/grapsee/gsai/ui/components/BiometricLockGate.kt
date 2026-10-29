package com.grapsee.gsai.ui.components

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.Icon
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * The app lock behind the Account & Security "Biometric Login" switch.
 *
 * This is what makes that switch a control instead of a memory. Until this
 * existed the preference was written by the screen and read by nobody, which
 * is exactly the dead-toggle failure the SettingsStore HONESTY CONTRACT
 * forbids — so the row was claiming a lock that could never be asked for.
 *
 * Behaviour: when [SettingsStore.biometricUnlock] is on, the shell does not
 * render until the device answers a real BiometricPrompt. Turning the switch
 * off removes the gate on the next launch, because the flag is read here.
 */
@Composable
fun BiometricLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Remembered per process: one unlock per launch, not per screen.
    var unlocked by remember { mutableStateOf(!SettingsStore.biometricUnlock) }
    var prompting by remember { mutableStateOf(false) }
    val colors = GsTheme.colors

    if (unlocked) {
        content()
        return
    }

    LaunchedEffect(Unit) {
        if (!prompting) {
            prompting = true
            promptBiometric(
                context = context,
                title = "Unlock Nexly",
                subtitle = "Biometric Login is on",
            ) {
                SettingsStore.updateLastSecurityCheck(System.currentTimeMillis())
                unlocked = true
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.glassSurface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(
                    GsIcons.Fingerprint,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                "Nexly is locked",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            Text(
                "Confirm your fingerprint or face to continue.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
            Box(Modifier.height(4.dp))
            Text(
                "Retry",
                fontSize = 12.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.accentStrong,
                modifier = Modifier
                    .padding(8.dp)
                    .then(Modifier),
            )
        }
    }
}

/** True when the device can actually answer a biometric prompt. */
fun canUseBiometrics(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
    return keyguard.isDeviceSecure
}

/**
 * One real BiometricPrompt. Framework API only: androidx.biometric needs a
 * FragmentActivity and MainActivity is a plain ComponentActivity, so using it
 * would compile into a prompt that can never appear.
 */
fun promptBiometric(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setSubtitle(subtitle)
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
