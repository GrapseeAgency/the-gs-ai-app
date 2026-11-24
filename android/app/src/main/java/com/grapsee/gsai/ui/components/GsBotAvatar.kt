package com.grapsee.gsai.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.provider.Settings
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.grapsee.gsai.data.AssistantAvatarStyle
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * The original bot-avatars package draws directly to the hardware-composited
 * WebView canvas. No bitmap copies, JPEG/PNG frame encoding, synthetic rig,
 * hidden oversized browser, or second layer. The bundled page fits the entire
 * vendor overscan canvas into the native transcript slot using CSS transform.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GsBotAvatar(
    style: AssistantAvatarStyle,
    size: Dp = 34.dp,
    isWorking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val command = remember(style, isWorking, reducedMotion) {
        val state = JSONObject().apply {
            put("type", style.id)
            put("state", if (isWorking) "working" else "default")
            put("paused", reducedMotion)
        }
        "window.gsAvatarUpdate && window.gsAvatarUpdate(" + state.toString() + ");"
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                tag = command
                setBackgroundColor(Color.TRANSPARENT)
                // Keep the actual vendor canvas on Android's hardware layer.
                // Do not suppress Chromium's visibility/animation scheduler.
                setLayerType(View.LAYER_TYPE_NONE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowContentAccess = false
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportZoom(false)
                settings.useWideViewPort = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest,
                    ): Boolean = true

                    override fun shouldInterceptRequest(
                        view: WebView, request: WebResourceRequest,
                    ): WebResourceResponse? =
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            WebResourceResponse(
                                "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
                            )
                        } else null

                    override fun onPageFinished(view: WebView, url: String) {
                        (view.tag as? String)?.let { view.evaluateJavascript(it, null) }
                    }
                }
                loadUrl("file:///android_asset/bot-avatar/index.html")
            }
        },
        update = { browser ->
            if (browser.tag != command) {
                browser.tag = command
                browser.evaluateJavascript(command, null)
            }
        },
        onRelease = { browser ->
            browser.stopLoading()
            browser.destroy()
        },
        modifier = modifier.size(size).semantics {
            contentDescription = style.label +
                if (isWorking) " bot, working" else " bot, idle"
        },
    )
}
