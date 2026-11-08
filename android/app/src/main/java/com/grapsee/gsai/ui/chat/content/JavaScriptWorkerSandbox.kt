package com.grapsee.gsai.ui.chat.content

import android.content.Context

/** Exact shared Web/iOS worker document. Source is data, not a host script. */
internal object JavaScriptWorkerSandboxAndroid {
    fun document(context: Context, source: String): String = runCatching {
        val contract = context.assets.open("javascript-worker-sandbox.json").bufferedReader().use {
            org.json.JSONObject(it.readText())
        }
        val template = contract.getString("template")
        val token = contract.getString("sourcePlaceholder")
        require(template.split(token).size == 2)
        val literal = org.json.JSONObject.quote(source).replace("<", "\\u003c")
        template.replace(token, literal)
    }.getOrElse { "<p>Not executed: the JavaScript worker contract is unavailable.</p>" }
}
