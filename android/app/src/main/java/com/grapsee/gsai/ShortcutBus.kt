package com.grapsee.gsai

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Home-screen quick actions (static shortcuts) hand MainActivity an intent
 * carrying a `gs.route` extra; the bus holds it for GsNavHost, which consumes
 * it exactly once and navigates with the routes the drawer already uses.
 * An unknown or blank route is dropped silently; an unauthenticated launch
 * drops it too (the session gate in GsNavHost owns that decision).
 */
object ShortcutBus {
    private const val EXTRA_ROUTE = "gs.route"

    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    /** Safe to call with any intent — only quick-action extras register. */
    fun publish(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE)?.takeIf { it.isNotBlank() } ?: return
        _route.value = route
    }

    fun consume() {
        _route.value = null
    }
}
