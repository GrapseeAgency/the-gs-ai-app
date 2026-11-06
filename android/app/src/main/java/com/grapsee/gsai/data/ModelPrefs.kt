package com.grapsee.gsai.data

import android.content.Context
import com.grapsee.gsai.data.model.ModelCatalog

/**
 * Model preference that sticks: the default model and reasoning mode picked
 * in the Model Centre survive relaunches via SharedPreferences, and the chat
 * send path reads the default id here. The id only reaches the backend when
 * the remote model registry advertises it (see ChatRepository) — otherwise
 * the server default is used silently.
 */
object ModelPrefs {
    private const val PREFS = "gs_models"
    private const val KEY_DEFAULT_ID = "gs.models.defaultId"
    private const val KEY_MODE = "gs.models.mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun defaultId(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_ID, null) ?: ModelCatalog.default.id

    fun setDefaultId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_DEFAULT_ID, id).apply()
    }

    fun mode(context: Context): String =
        prefs(context).getString(KEY_MODE, null) ?: "Balanced"

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_MODE, mode).apply()
    }
}
