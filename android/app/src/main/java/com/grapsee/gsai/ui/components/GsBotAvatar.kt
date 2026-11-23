package com.grapsee.gsai.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.grapsee.gsai.data.AssistantAvatarStyle
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Authenticate the rendering source: pixel frames come ONLY from the actual
 * bot-avatars npm canvas, never a custom Kotlin drawing or substitute icon.
 * JavaScript captures the original live canvas roughly 10 fps and feeds the
 * painted PNG into Compose. The WebView's own GPU layer is invisible in some
 * Android builds, even when its DOM canvas paints and animates correctly.
 */
private class AuthenticCanvasBridge(
    private val publish: (Bitmap) -> Unit,
) {
    private val live = AtomicBoolean(true)
    fun release() { live.set(false) }

    @JavascriptInterface
    fun frame(dataUrl: String) {
        if (!live.get() ||
            !dataUrl.startsWith("data:image/png;base64,") ||
            dataUrl.length > 140_000
        ) return
        runCatching {
            val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.let { bitmap ->
            if (live.get()) publish(bitmap)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GsBotAvatar(
    style: AssistantAvatarStyle,
    size: Dp = 38.dp,
    isWorking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var originalCanvasFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        AuthenticCanvasBridge { bitmap ->
            main.post { originalCanvasFrame = bitmap.asImageBitmap() }
        }
    }
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

    Box(
        modifier = modifier.size(size).semantics {
            contentDescription = style.label +
                if (isWorking) " bot, working" else " bot, idle"
        },
    ) {
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
                    // Exposes only a bounded PNG sink to this trusted local
                    // file; HTTP(S) requests and remote navigation are blocked.
                    addJavascriptInterface(bridge, "AndroidAvatarFrames")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest,
                        ): Boolean = true

                        override fun shouldInterceptRequest(
                            view: WebView, request: WebResourceRequest,
                        ): WebResourceResponse? =
                            if (request.url.scheme == "http" || request.url.scheme == "https") {
                                WebResourceResponse(
                                    "text/plain", "UTF-8",
                                    ByteArrayInputStream(ByteArray(0)),
                                )
                            } else null

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
                bridge.release()
                webView.stopLoading()
                webView.removeJavascriptInterface("AndroidAvatarFrames")
                webView.destroy()
            },
            modifier = Modifier.matchParentSize(),
        )
        // Draw the authentic live React-canvas pixels in the actual native
        // Compose tree. This bypasses the invisible WebView texture entirely.
        originalCanvasFrame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
