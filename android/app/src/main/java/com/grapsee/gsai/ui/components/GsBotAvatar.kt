package com.grapsee.gsai.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.util.Log
import android.view.View
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
import java.util.concurrent.Executors

/**
 * Lossless native playback of the actual bot-avatars@0.2.2 React canvas
 * captured by scripts/native-avatar/build.mjs. All artwork and animated
 * poses come from the package. No WebView, hand-drawn avatar or frame
 * transfer on the device. This is recorded original-library animation,
 * not a Kotlin implementation of the procedural engine.
 */
private object OriginalAvatarAssets {
    const val FRAME_SIZE = 144
    const val COLUMNS = 12
    const val FRAME_COUNT = 180
    const val FPS = 30
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    private val atlases = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val posters = LruCache<String, Bitmap>(16)
    private val pending = mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>()

    private fun decode(context: Context, name: String): Bitmap =
        context.assets.open("bot-avatar/native/" + name).use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })) { "Invalid original avatar asset: " + name }
        }

    fun poster(context: Context, key: String): Bitmap =
        posters.get(key) ?: decode(context, key + "-poster.png").also { posters.put(key, it) }

    // All calls are on the UI thread. Share each decoded atlas across rows.
    fun load(context: Context, key: String, callback: (Bitmap?) -> Unit) {
        atlases.get(key)?.let { callback(it); return }
        pending[key]?.let { it.add(callback); return }
        pending[key] = mutableListOf(callback)
        val app = context.applicationContext
        pool.execute {
            val atlas = runCatching {
                decode(app, key + "-atlas.png").also {
                    check(it.width == FRAME_SIZE * COLUMNS &&
                        it.height == FRAME_SIZE * (FRAME_COUNT / COLUMNS)) {
                        "Invalid original frame atlas dimensions"
                    }
                }
            }.onFailure { Log.e("GsBotAvatar", "Original atlas load failed: " + key, it) }
                .getOrNull()
            main.post {
                atlas?.let { atlases.put(key, it) }
                pending.remove(key)?.forEach { it(atlas) }
            }
        }
    }
}

internal class OriginalBotAvatarView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()
    private var poster: Bitmap? = null
    private var atlas: Bitmap? = null
    private var motionDisabled = false
    private var originMs = 0L
    private var lastFrame = -1
    private var request = 0
    internal var animationKey = ""
        private set
    internal var presentedFrames = 0
        private set
    internal val animationReady get() = atlas != null

    init {
        tag = "gs-original-native-avatar"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun display(style: AssistantAvatarStyle, working: Boolean, reducedMotion: Boolean) {
        val key = style.id + "-" + if (working) "working" else "default"
        if (animationKey == key && motionDisabled == reducedMotion) return
        animationKey = key
        motionDisabled = reducedMotion
        val ticket = ++request
        lastFrame = -1
        atlas = null
        // A valid original frame is visible even while the animation texture loads.
        poster = OriginalAvatarAssets.poster(context, key)
        invalidate()
        if (!reducedMotion) {
            OriginalAvatarAssets.load(context, key) { bitmap ->
                if (ticket == request && animationKey == key) {
                    atlas = bitmap
                    originMs = SystemClock.uptimeMillis()
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val side = minOf(width, height).toFloat()
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        target.set(left, top, left + side, top + side)
        val texture = atlas
        if (texture == null || motionDisabled) {
            poster?.let { canvas.drawBitmap(it, null, target, paint) }
            return
        }
        val elapsed = (SystemClock.uptimeMillis() - originMs).coerceAtLeast(0L)
        val ticks = elapsed * OriginalAvatarAssets.FPS / 1000L
        val frame = (ticks % OriginalAvatarAssets.FRAME_COUNT).toInt()
        val x = (frame % OriginalAvatarAssets.COLUMNS) * OriginalAvatarAssets.FRAME_SIZE
        val y = (frame / OriginalAvatarAssets.COLUMNS) * OriginalAvatarAssets.FRAME_SIZE
        source.set(x, y, x + OriginalAvatarAssets.FRAME_SIZE, y + OriginalAvatarAssets.FRAME_SIZE)
        canvas.drawBitmap(texture, source, target, paint)
        if (frame != lastFrame) {
            lastFrame = frame
            presentedFrames++
        }
        if (isAttachedToWindow && isShown && windowVisibility == VISIBLE) {
            val nextMs = ((ticks + 1) * 1000L + OriginalAvatarAssets.FPS - 1) /
                OriginalAvatarAssets.FPS
            postInvalidateDelayed((nextMs - elapsed).coerceIn(1L, 34L))
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate()
    }

    fun release() {
        request++
        atlas = null
        poster = null
        animationKey = ""
    }
}

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
    AndroidView(
        factory = { OriginalBotAvatarView(it) },
        update = { it.display(style, isWorking, reducedMotion) },
        onRelease = { it.release() },
        modifier = modifier.size(size).semantics {
            contentDescription = style.label +
                if (isWorking) " bot, working" else " bot, idle"
        },
    )
}
