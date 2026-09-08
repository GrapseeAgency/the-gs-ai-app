package com.grapsee.gsai.ui.theme

import android.view.View
import androidx.compose.runtime.Composable
import com.grapsee.gsai.data.SettingsStore

/**
 * Global haptics gate — the Settings "Haptics" toggle actually gates EVERY
 * haptic in the app. Call sites either check [enabled] before firing a compose
 * HapticFeedback, or fire a platform constant through the [View.gsHaptic]
 * extension; both funnel through this one flag.
 */
object GsHaptics {
    /** Synced from [SettingsStore.haptics] at the theme root. @Volatile so the
     *  non-composable call sites (dialogs, listeners) always read the latest. */
    @Volatile
    var enabled: Boolean = true

    fun enabled(): Boolean = enabled
}

/**
 * Theme-root sync point. Reading [SettingsStore.haptics] (a snapshot state)
 * inside composition subscribes the theme root to the Settings toggle: the
 * moment it flips, TheGsAiTheme recomposes and re-publishes the volatile.
 * Called once from TheGsAiTheme — do not call per screen.
 */
@Composable
fun SyncHapticsGate() {
    GsHaptics.enabled = SettingsStore.haptics
}

/**
 * Gate + fire: platform haptic constants through a view, honoring the toggle.
 * Keeps the call sites one-liners (view.gsHaptic(HapticFeedbackConstants.VIRTUAL_KEY)).
 */
fun View.gsHaptic(hapticConstant: Int) {
    if (GsHaptics.enabled()) performHapticFeedback(hapticConstant)
}
