package com.grapsee.gsai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A one-shot composer pre-fill. Explore/Discover tool chips carry a partial
 * prompt ("Write code that ") that the reader finishes before sending — that is
 * a SEED, not a transcript, so it belongs on the pre-chat hero (which owns the
 * composer), never on the chat surface, which would otherwise open on a blank
 * dark screen holding an unfinished draft.
 *
 * The bus holds the text for [com.grapsee.gsai.ui.home.NewChatScreen], which
 * consumes it exactly once on arrival. A blank seed is dropped.
 */
object ComposerSeed {
    private val _seed = MutableStateFlow<String?>(null)
    val seed: StateFlow<String?> = _seed.asStateFlow()

    fun set(text: String) {
        _seed.value = text.takeIf { it.isNotBlank() }
    }

    fun consume() {
        _seed.value = null
    }
}
