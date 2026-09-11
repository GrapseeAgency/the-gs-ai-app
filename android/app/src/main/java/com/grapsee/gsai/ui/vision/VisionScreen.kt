package com.grapsee.gsai.ui.vision

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsScreenScaffold

/**
 * Vision — honest gate. Image analysis is not wired to a model yet: the
 * screen that used to live here faked an entire pipeline (a phantom
 * "IMG_2041.jpg · 3.2 MB" capture, a canned progress run, fabricated
 * object-detection confidence bars, OCR lines and chart readings, and
 * scripted follow-up answers). None of that happens, so none of it is
 * shown. The screen states what is coming and gets out of the way.
 */
@Composable
fun VisionScreen(onBack: () -> Unit) {
    GsScreenScaffold(title = "Vision", onBack = onBack) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            GsEmptyState(
                icon = Icons.Outlined.Image,
                title = "Vision is coming soon",
                message = "Image analysis isn't available yet. When it arrives " +
                    "you'll be able to analyse photos, screenshots and charts here.",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
