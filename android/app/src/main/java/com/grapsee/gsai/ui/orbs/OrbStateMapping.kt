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
 *     phase Streaming, buffer still empty  → BREATHING  ("Thinking…")
 *       — the request is with the model, no token has arrived.
 *     phase Streaming, tokens flowing      → COMPOSING ("Composing…")
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
 *   SEARCHING (no tool/retrieval phase in the chat pipeline),
 *   SOLVING (no distinct reasoning phase), CONNECTING (no connection
 *   handshake state), WEAVING, SHAPING (no artifact-assembly state). When the
 *   product grows the real behaviour, map it HERE — never at the call site.
 */
internal fun orbStateForChatStream(phase: ChatStreamController.Phase?, streamText: String?): OrbState? =
    when {
        phase != ChatStreamController.Phase.Streaming -> null
        streamText.isNullOrBlank() -> OrbState.BREATHING
        else -> OrbState.COMPOSING
    }

internal fun orbStateForVoice(phase: VoicePhase): OrbState? = when (phase) {
    VoicePhase.Listening -> OrbState.LISTENING
    VoicePhase.Processing -> OrbState.WORKING
    else -> null
}
