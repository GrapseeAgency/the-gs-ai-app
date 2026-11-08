package com.grapsee.gsai.ui.chat.content

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest

val LocalReactPreviewScope = staticCompositionLocalOf<String?> { null }

object ReactPreviewRuntimeAndroid {
    @Volatile private var cachedTemplate: String? = null
    fun document(context: Context, source: String): String {
        val template = cachedTemplate ?: synchronized(this) {
            cachedTemplate ?: context.assets.open("react-preview-runtime.json").bufferedReader().use {
                JSONObject(it.readText()).getString("template")
            }.also { cachedTemplate = it }
        }
        val policy = context.assets.open("emoji-dom-policy.json").bufferedReader().use { JSONObject(it.readText()).getString("script") }
        return template.replace("<head>", "<head><script>" + policy.replace("</script", "<\\/script") + "</script>")
            .replace("__GS_REACT_STORAGE_BOOTSTRAP__", "")
            .replace("__GS_REACT_SOURCE_LITERAL__", JSONObject.quote(source).replace("<", "\\u003c"))
    }
    fun origin(scope: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it.toInt() and 255) }.take(48)
        return "https://$hash.gs-react.invalid/"
    }
}

/** A real renderer, with no JS/native bridge or app-origin access. Persistent
 * WebView storage is isolated by the trusted response/snippet origin. */
@Composable
fun ReactPreviewAndroid(source: String, scope: String) {
    val context = LocalContext.current
    val document = remember(context, source) {
        try { ReactPreviewRuntimeAndroid.document(context, source) }
        catch (_: Exception) { "<p>Not executed: the offline React runtime is unavailable.</p>" }
    }
    val origin = remember(scope) { ReactPreviewRuntimeAndroid.origin(scope) }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        factory = { ctx -> WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }
        } },
        update = { view ->
            val identity = scope to source
            if (view.tag != identity) {
                view.tag = identity
                view.loadDataWithBaseURL(origin, document, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() }
    )
}
