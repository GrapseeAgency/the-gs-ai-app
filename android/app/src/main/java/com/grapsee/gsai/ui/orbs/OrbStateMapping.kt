package com.grapsee.gsai.ui.orbs

import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.ui.voice.VoicePhase

/**
 * THE HONEST STATE-MAPPING LAYER (Phase 4 §6): application state → orb state.
 *
 * The orb is a picture of what the app is REALLY doing. Nothing here may
 * fabricate a state the pipeline does not have — no pretend "Searching" when
 * no search is happening, no pretend "Solving" during ordinary generation.
 *
 * CURRENT MAPPINGS (the only real activity states the app has):
 *
 *   Chat streaming (ChatStreamController):
 *     phase Streaming, buffer still empty, search phase "searching"
 *                                          → SEARCHING ("Searching…")
 *       — PHASE 8.1: a real `search started` event (or the wire status
 *       "searching") arrived — the backend is actually running queries.
 *       Nothing shows until the wire says so; a plain text turn never
 *       reaches this branch (searchPhase stays null).
 *     phase Streaming, buffer still empty, search phase "working"
 *                                          → WORKING ("Working…")
 *       — PHASE 8.1: a real `source opening/reading` event is in flight —
 *       the backend is genuinely reading pages. "working" is left only when
 *       the last in-flight retrieval settles (the controller tracks this),
 *       never guessed from silence.
 *     phase Streaming, buffer still empty, request carried attachments
 *                                          → WORKING ("Working…")
 *       — PHASE 6: image requests go to the vision model, which genuinely
 *       receives and analyses the attached image(s) before the first token.
 *       — PHASE 7: document requests (PDF/TXT/MD/CSV) go through real
 *       server-side extraction before the first token. Both are real,
 *       distinct pipeline phases — mapped, not invented.
 *     phase Streaming, buffer still empty  → BREATHING  ("Thinking…")
 *       — text-only request with the model, no token has arrived. A
 *       "search_failed" turn also lands here honestly: the search is over
 *       and nothing is in flight, but the answer has not started.
 *     phase Streaming, tokens flowing      → COMPOSING ("Composing…")
 *       — first token wins over every pre-token state, search included.
 *     Finalizing / Done / Cancelled        → null (no orb; the turn is settled)
 *
 *   Voice (VoiceScreen / VoicePhase):
 *     Listening  → LISTENING ("Listening…")
 *     Processing → WORKING   ("Working…")
 *     Idle / Result / Paused / Denied / NoSpeech / Error / Unavailable → null
 *
 *   Fresh workspace (empty state):
 *     BREATHING — the assistant is present and waiting; the composer stays
 *     the dominant interaction (Phase 4 §14).
 *
 * DELIBERATELY UNMAPPED (exist in the catalogue, no real app state yet):
 *   SOLVING (no distinct reasoning phase), CONNECTING (no connection
 *   handshake state), WEAVING, SHAPING (no artifact-assembly state). When the
 *   product grows the real behaviour, map it HERE — never at the call site.
 *   (SEARCHING joined the mapped set in PHASE 8.1, when the backend grew the
 *   real search-event stream it always needed.)
 */
internal fun orbStateForChatStream(
    phase: ChatStreamController.Phase?,
    streamText: String?,
    requestHasAttachments: Boolean = false,
    searchPhase: String? = null
): OrbState? =
    when {
        phase != ChatStreamController.Phase.Streaming -> null
        // PHASE 8.1: the real search chain, before the first token. Specific
        // search activity beats the coarser attachment mapping — it is what
        // the backend is verifiably doing right now.
        streamText.isNullOrBlank() && searchPhase == "searching" -> OrbState.SEARCHING
        streamText.isNullOrBlank() && searchPhase == "working" -> OrbState.WORKING
        streamText.isNullOrBlank() && requestHasAttachments -> OrbState.WORKING
        streamText.isNullOrBlank() -> OrbState.BREATHING
        else -> OrbState.COMPOSING
    }

internal fun orbStateForVoice(phase: VoicePhase): OrbState? = when (phase) {
    VoicePhase.Listening -> OrbState.LISTENING
    VoicePhase.Processing -> OrbState.WORKING
    else -> null
}
