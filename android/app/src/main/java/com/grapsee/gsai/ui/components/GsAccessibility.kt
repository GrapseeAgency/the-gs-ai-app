package com.grapsee.gsai.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * AERUO KINETIC — screen-reader hint plumbing.
 *
 * Settings › "Screen reader hints" is REAL: when enabled, TheGsAiTheme
 * provides LocalGsScreenReaderHints and every `gsHint(...)` call site adds an
 * explicit, human-written description for TalkBack. Foundation components
 * consume it; per-screen hints land with each screen's own design pass.
 */
val LocalGsScreenReaderHints = staticCompositionLocalOf { false }

/** Adds a spoken hint when the screen-reader-hints setting is on. */
fun Modifier.gsHint(hint: String): Modifier = composed {
    if (LocalGsScreenReaderHints.current) {
        this.semantics { contentDescription = hint }
    } else {
        this
    }
}
