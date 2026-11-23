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
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import com.grapsee.gsai.data.AssistantAvatarStyle
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decodes only bounded PNG frames from our OFFLINE, original bot-avatars npm
 * canvas. Public class is intentional: Android's reflection-backed
 * @JavascriptInterface bridge needs a publicly accessible declaring class.
 */
class AuthenticCanvasBridge(private val publish: (Bitmap) -> Unit) {
    private val live = AtomicBoolean(true)
    private val received = AtomicInteger()
    private val decoded = AtomicInteger()
    @Volatile private var failure = "none"

    fun release() { live.set(false) }

    @JavascriptInterface
    fun frame(dataUrl: String) {
        received.incrementAndGet()
        if (!live.get()) return
        if (!dataUrl.startsWith("data:image/png;base64,") || dataUrl.length > 140_000) {
            failure = "bad-data-url length=" + dataUrl.length
            return
        }
        try {
            val raw = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
            val frame = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                ?: error("BitmapFactory returned null")
            decoded.incrementAndGet()
            publish(frame)
        } catch (e: Exception) {
            failure = e.javaClass.simpleName + ":" + e.message
        }
    }

    @JavascriptInterface
    fun diagnostics(): String =
        "received=" + received.get() + ",decoded=" + decoded.get() + ",error=" + failure
}

private data class AvatarViewHolder(
    val browser: WebView,
    val image: ImageView,
    val bridge: AuthenticCanvasBridge,
    var command: String,
)

/**
 * Runs the real bot-avatars React renderer locally, then presents its live
 * canvas frames using a native Android ImageView OVER the WebView. The image
 * is not re-drawn by Kotlin, nor is it a static picture: every frame comes
 * from the package's JavaScript canvas.
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
    val main = remember { Handler(Looper.getMainLooper()) }
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
            val image = ImageView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                scaleType = ImageView.ScaleType.FIT_XY
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false
            }
            val bridge = AuthenticCanvasBridge { bitmap ->
                main.post { image.setImageBitmap(bitmap) }
            }
            val browser = WebView(ctx).apply {
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
                // Only a trusted bundled file loads; URLs cannot navigate away
                // or fetch arbitrary remote resources.
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
                                "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
                            )
                        } else null

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(command, null)
                    }
                }
            }
            FrameLayout(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                addView(browser, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
                // A native ImageView ABOVE Chromium's texture: Compose or
                // WebView surface ordering cannot hide the delivered pixels.
                addView(image, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
                tag = AvatarViewHolder(browser, image, bridge, command)
                browser.loadUrl("file:///android_asset/bot-avatar/index.html")
            }
        },
        update = { container ->
            val holder = container.tag as AvatarViewHolder
            if (holder.command != command) {
                holder.command = command
                holder.browser.evaluateJavascript(command, null)
            }
        },
        onRelease = { container ->
            val holder = container.tag as AvatarViewHolder
            holder.bridge.release()
            holder.browser.stopLoading()
            holder.browser.removeJavascriptInterface("AndroidAvatarFrames")
            container.removeAllViews()
            holder.browser.destroy()
            holder.image.setImageDrawable(null)
        },
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = style.label +
                    if (isWorking) " bot, working" else " bot, idle"
            },
    )
}
