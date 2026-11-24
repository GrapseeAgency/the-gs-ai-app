package com.grapsee.gsai.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Pixel transport for the actual bot-avatars npm canvas. Never draw a
 * replacement character in Kotlin. Decode on the JS bridge thread and only
 * present the newest available frame on the Android UI thread: old queued
 * frames must not delay the current hop/turn during a busy chat.
 */
class AuthenticCanvasBridge(private val image: ImageView) {
    private val live = AtomicBoolean(true)
    private val received = AtomicInteger()
    private val decoded = AtomicInteger()
    private val presented = AtomicInteger()
    private val pending = AtomicReference<Bitmap?>(null)
    private val scheduled = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var failure = "none"
    @Volatile private var dimensions = "0x0"

    fun release() {
        live.set(false)
        pending.set(null)
    }

    private fun schedulePresentation() {
        if (!live.get() || !scheduled.compareAndSet(false, true)) return
        main.post {
            scheduled.set(false)
            if (live.get()) {
                pending.getAndSet(null)?.let { bitmap ->
                    image.setImageBitmap(bitmap)
                    (image.drawable as? BitmapDrawable)?.setFilterBitmap(true)
                    presented.incrementAndGet()
                }
                if (pending.get() != null) schedulePresentation()
            } else {
                pending.set(null)
            }
        }
    }

    @JavascriptInterface
    fun frame(dataUrl: String) {
        if (!live.get()) return
        received.incrementAndGet()
        if (!dataUrl.startsWith("data:image/png;base64,") || dataUrl.length > 180_000) {
            failure = "invalid PNG frame length=" + dataUrl.length
            return
        }
        try {
            val raw = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
            val frame = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                ?: error("BitmapFactory returned null")
            check(frame.width in 1..256 && frame.height in 1..256) {
                "Unexpected avatar frame dimensions"
            }
            dimensions = frame.width.toString() + "x" + frame.height
            decoded.incrementAndGet()
            if (live.get()) {
                pending.set(frame)
                schedulePresentation()
            }
        } catch (e: Exception) {
            failure = e.javaClass.simpleName + ":" + e.message
        }
    }

    @JavascriptInterface
    fun diagnostics(): String =
        "received=" + received.get() + ",decoded=" + decoded.get() +
            ",presented=" + presented.get() + ",size=" + dimensions + ",error=" + failure
}

private data class AvatarViewHolder(
    val browser: WebView,
    val image: ImageView,
    val bridge: AuthenticCanvasBridge,
    var command: String,
)

/**
 * Original React bot-avatars engine, with its own eyes, lighting, rig and
 * working/default transitions. The 96dp source viewport comfortably fits the library's
 * complete overscan canvas (48 CSS px body * 1.5); it is not the visual size
 * of the chat avatar. Only the native ImageView is visible. Rendering the
 * WebView beneath its bitmap used to superimpose two differently scaled,
 * differently timed characters and clip the top of the figure.
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
            val image = ImageView(ctx).apply {
                tag = "gs-original-avatar-image"
                setBackgroundColor(Color.TRANSPARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false
            }
            val bridge = AuthenticCanvasBridge(image)
            val browser = WebView(ctx).apply {
                // The bundled page hides its source canvas via CSS opacity.
                // Keep the Android WebView itself VISIBLE: alpha=0 at the
                // native view level can cause Chromium to throttle its rAF.
                // The source pixels are never composited under the ImageView.
                alpha = 1f
                setBackgroundColor(Color.TRANSPARENT)
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
                        (view.tag as? String)?.let { view.evaluateJavascript(it, null) }
                    }
                }
                tag = command
            }
            FrameLayout(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                clipChildren = true
                clipToPadding = true
                // A small Chromium viewport (72 CSS px or below in a direct
                // browser probe) can throttle rAF to ~1 FPS. Keep this source
                // at 96dp, with transparent padding around the 72px canvas.
                // The 34dp native output is independent of the source view.
                val sourcePx = kotlin.math.ceil(
                    96f * ctx.resources.displayMetrics.density
                ).toInt()
                addView(browser, FrameLayout.LayoutParams(sourcePx, sourcePx))
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
                holder.browser.tag = command
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
        modifier = modifier.size(size).semantics {
            contentDescription = style.label +
                if (isWorking) " bot, working" else " bot, idle"
        },
    )
}
