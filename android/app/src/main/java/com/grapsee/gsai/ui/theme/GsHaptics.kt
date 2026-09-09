package com.grapsee.gsai.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.grapsee.gsai.data.SettingsStore

/**
 * AERUO KINETIC — the one haptic vocabulary, gated by the Settings › Haptics
 * switch (which is therefore REAL: off means the app never vibrates).
 *
 * Vocabulary (mirrors iOS GSHaptics):
 *  - press / gsHaptic(VIRTUAL_KEY|CLOCK_TICK) — taps, sends, selections
 *  - longPress                                — long-press menus, hold-to-talk
 *  - success                                  — copy, pin, archive completed
 *  - warning                                  — destructive arm, delete
 *  - error                                    — failure feedback
 *
 * The gate is @Volatile and synced from [SettingsStore.haptics] at the theme
 * root ([SyncHapticsGate]) so non-composable call sites (dialogs, listeners)
 * always read the latest value; the vocabulary helpers additionally read the
 * snapshot state directly, so both entry surfaces are reactive.
 */
object GsHaptics {
    /** Synced from [SettingsStore.haptics] at the theme root. */
    @Volatile
    var enabled: Boolean = true

    fun enabled(): Boolean = SettingsStore.haptics

    fun press(view: View) {
        if (enabled()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun longPress(haptics: HapticFeedback) {
        if (enabled()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun success(view: View) {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun warning(view: View) {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun error(view: View) {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
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
