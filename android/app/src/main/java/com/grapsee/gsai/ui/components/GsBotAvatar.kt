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
 * Authentic Libraries.dev bot-avatars React canvas mounted in a local WebView.
 * Not a custom Compose recreation, static frame, remote page or CDN proxy.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GsBotAvatar(
    style: AssistantAvatarStyle,
    size: Dp = 38.dp,
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
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowContentAccess = false
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest,
                    ): Boolean = true
                    override fun shouldInterceptRequest(
                        view: WebView, request: WebResourceRequest,
                    ): WebResourceResponse? = if (
                        request.url.scheme == "http" || request.url.scheme == "https"
                    ) WebResourceResponse(
                        "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
                    ) else null
                    override fun onPageFinished(view: WebView, url: String) {
                        (view.tag as? String)?.let { view.evaluateJavascript(it, null) }
                    }
                }
                tag = command
                loadUrl("file:///android_asset/bot-avatar/index.html")
            }
        },
        update = { webView ->
            if (webView.tag != command) {
                webView.tag = command
                webView.evaluateJavascript(command, null)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = modifier.size(size).semantics {
            contentDescription = style.label +
                if (isWorking) " bot, working" else " bot, idle"
        },
    )
}
