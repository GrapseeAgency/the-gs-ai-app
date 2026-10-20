package com.grapsee.gsai.ui.orbs

import com.grapsee.gsai.data.chat.ChatStreamController
import com.grapsee.gsai.ui.voice.VoicePhase

/*
 * THE LUMINOUS ORB — the 12-state product contract (mandate).
 *
 * Two layers live here, and the split is deliberate:
 *
 *   1. GEOMETRY — the nine dot-field animations in [OrbState]/[OrbMode]. They
 *      are GENERATED (OrbSpec.kt) and locked by the golden-vector test; this
 *      file never touches them. Geometry is HOW the orb moves.
 *
 *   2. PRESENTATION — the twelve product PHASES below and the [LuminousProfile]
 *      that colours and lights them. Presentation is HOW the orb GLOWS, and it
 *      is driven entirely through the existing state machine: every phase names
 *      an existing [OrbState] as its [geometry], so the honest animation is
 *      reused verbatim while the luminous layer (the accent bloom, the iridescent
 *      wash, the reactive core) is drawn *around* it, additively.
 *
 * This is the reconciliation the design review landed on — keep the iridescent,
 * luminous material the reference calls for, but do not fork the motion engine
 * to get it: map the twelve product states onto the nine generated geometries
 * and light them from one place. The geometry test stays green because no
 * geometry changes; the presentation contract is what lives here.
 */

/**
 * The twelve product phases the interface can be in. Nine describe live
 * assistant work; the last three are non-motion / connectivity states. This is
 * the vocabulary screens speak, wider than the nine geometries it projects onto.
 */
enum class OrbPhase {
    /** Present, waiting; no request in flight. */
    IDLE,

    /** The recogniser is open and quiet — listening for speech to start. */
    LISTENING,

    /** The recogniser is capturing — the live microphone level drives [amplitude]. */
    RECORDING,

    /** A turn has begun; the model is engaged but no token or tool result is back yet. */
    THINKING,

    /** Multi-step work: retrieval, source reading, reasoning chains in flight. */
    REASONING,

    /** The first tokens are landing; the answer is being composed. */
    GENERATING,

    /** A settled stream is flowing to the transcript. */
    STREAMING,

    /** The turn completed cleanly. */
    SUCCESS,

    /** The turn failed or the model returned an error. */
    ERROR,

    /** Motion frozen by the user or the OS (backgrounded); shape survives. */
    PAUSED,

    /** No reachable backend / capability absent — present but not alive. */
    OFFLINE,

    /** Reduced motion honoured: one static representative frame, never hidden. */
    REDUCED_MOTION,
}

/**
 * The hue family a phase borrows. Resolved to real [androidx.compose.ui.graphics.Color]s
 * at composition from [com.grapsee.gsai.ui.theme.GsColors] — a role, not a literal,
 * so an accent or appearance flip relights every orb with no code change.
 */
enum class OrbLuminousRole {
    /** The calm standby wash — the iridescent aurora, low intensity. */
    CALM,

    /** Live work — the accent, brightest, breathing. */
    ACTIVE,

    /** A clean completion — the success hue, one soft lift. */
    SUCCESS,

    /** A failure — the error hue, no breathing (alarm should not look playful). */
    ALERT,

    /** Present but inert — a muted, desaturated ember. */
    MUTED,
}

/**
 * How a phase lights the geometry beneath it.
 *
 * @param glowAlpha   the outer bloom strength (0..1) behind the whole field.
 * @param coreAlpha   the inner halo directly behind the dots; [amplitude] lifts it.
 * @param pulse       whether intensity breathes with the shared orb clock.
 * @param pulseSpeed  radians/second of that breath when [pulse]; ignored otherwise.
 */
data class LuminousProfile(
    val role: OrbLuminousRole,
    val glowAlpha: Float,
    val coreAlpha: Float,
    val pulse: Boolean,
    val pulseSpeed: Float = 1.6f,
)

/** The geometry (dot field) a product phase projects onto — always one of the nine. */
val OrbPhase.geometry: OrbState
    get() = when (this) {
        OrbPhase.IDLE -> OrbState.BREATHING
        OrbPhase.LISTENING -> OrbState.LISTENING
        OrbPhase.RECORDING -> OrbState.LISTENING
        OrbPhase.THINKING -> OrbState.BREATHING
        OrbPhase.REASONING -> OrbState.SOLVING
        OrbPhase.GENERATING -> OrbState.COMPOSING
        OrbPhase.STREAMING -> OrbState.COMPOSING
        OrbPhase.SUCCESS -> OrbState.SHAPING
        OrbPhase.ERROR -> OrbState.CONNECTING
        OrbPhase.PAUSED -> OrbState.BREATHING
        OrbPhase.OFFLINE -> OrbState.CONNECTING
        OrbPhase.REDUCED_MOTION -> OrbState.BREATHING
    }

/** The luminous treatment a product phase carries over its geometry. */
val OrbPhase.luminance: LuminousProfile
    get() = when (this) {
        OrbPhase.IDLE -> LuminousProfile(OrbLuminousRole.CALM, glowAlpha = 0.16f, coreAlpha = 0.10f, pulse = true, pulseSpeed = 0.9f)
        OrbPhase.LISTENING -> LuminousProfile(OrbLuminousRole.CALM, glowAlpha = 0.20f, coreAlpha = 0.14f, pulse = true, pulseSpeed = 1.1f)
        OrbPhase.RECORDING -> LuminousProfile(OrbLuminousRole.ACTIVE, glowAlpha = 0.26f, coreAlpha = 0.18f, pulse = false)
        OrbPhase.THINKING -> LuminousProfile(OrbLuminousRole.ACTIVE, glowAlpha = 0.24f, coreAlpha = 0.16f, pulse = true, pulseSpeed = 1.3f)
        OrbPhase.REASONING -> LuminousProfile(OrbLuminousRole.ACTIVE, glowAlpha = 0.30f, coreAlpha = 0.20f, pulse = true, pulseSpeed = 1.8f)
        OrbPhase.GENERATING -> LuminousProfile(OrbLuminousRole.ACTIVE, glowAlpha = 0.28f, coreAlpha = 0.22f, pulse = true, pulseSpeed = 2.0f)
        OrbPhase.STREAMING -> LuminousProfile(OrbLuminousRole.ACTIVE, glowAlpha = 0.26f, coreAlpha = 0.20f, pulse = true, pulseSpeed = 1.7f)
        OrbPhase.SUCCESS -> LuminousProfile(OrbLuminousRole.SUCCESS, glowAlpha = 0.34f, coreAlpha = 0.24f, pulse = false)
        OrbPhase.ERROR -> LuminousProfile(OrbLuminousRole.ALERT, glowAlpha = 0.30f, coreAlpha = 0.22f, pulse = false)
        OrbPhase.PAUSED -> LuminousProfile(OrbLuminousRole.MUTED, glowAlpha = 0.12f, coreAlpha = 0.08f, pulse = false)
        OrbPhase.OFFLINE -> LuminousProfile(OrbLuminousRole.MUTED, glowAlpha = 0.10f, coreAlpha = 0.06f, pulse = false)
        OrbPhase.REDUCED_MOTION -> LuminousProfile(OrbLuminousRole.CALM, glowAlpha = 0.16f, coreAlpha = 0.10f, pulse = false)
    }

/*
 * The default luminous treatment for a bare [OrbState]. Screens that render the
 * orb through the honest geometry mapping (ChatScreen today) get the luminous
 * material for free without each one naming a phase: the nine geometries each
 * borrow the presentation of the product phase they most closely describe.
 */

/** The phase each geometry presents as by default — the presentation half of the contract. */
fun luminousPhaseFor(state: OrbState): OrbPhase = when (state) {
    OrbState.WORKING -> OrbPhase.REASONING
    OrbState.SEARCHING -> OrbPhase.REASONING
    OrbState.SOLVING -> OrbPhase.REASONING
    OrbState.LISTENING -> OrbPhase.RECORDING
    OrbState.CONNECTING -> OrbPhase.OFFLINE
    OrbState.WEAVING -> OrbPhase.GENERATING
    OrbState.COMPOSING -> OrbPhase.STREAMING
    OrbState.BREATHING -> OrbPhase.THINKING
    OrbState.SHAPING -> OrbPhase.SUCCESS
}

/** The luminous profile a geometry presents by default. */
fun luminousProfileFor(state: OrbState): LuminousProfile = luminousPhaseFor(state).luminance

/**
 * The honest product-phase resolver for the chat stream. It reads exactly the
 * same real signals as [orbStateForChatStream] — never inventing a phase the
 * pipeline does not have — and names the product phase, whose [geometry] is the
 * very [OrbState] the tested mapping would return. A settled turn returns null:
 * no orb, no glow.
 */
internal fun orbPhaseForChatStream(
    phase: ChatStreamController.Phase?,
    streamText: String?,
    requestHasAttachments: Boolean = false,
    searchPhase: String? = null,
    completed: Boolean = false,
    errored: Boolean = false,
): OrbPhase? = when {
    errored -> OrbPhase.ERROR
    completed -> OrbPhase.SUCCESS
    phase != ChatStreamController.Phase.Streaming -> null
    !streamText.isNullOrBlank() -> OrbPhase.STREAMING
    searchPhase == "searching" || searchPhase == "working" -> OrbPhase.REASONING
    requestHasAttachments -> OrbPhase.REASONING
    else -> OrbPhase.THINKING
}

/**
 * The honest product-phase resolver for the voice pipeline. The recogniser being
 * open and quiet is [LISTENING]; a live capture level (the microphone is feeding
 * frames) is [RECORDING]; processing the utterance is [REASONING]. Every other
 * voice phase (idle, result, permission, error, no-speech) shows no orb, exactly
 * as [orbStateForVoice] already rules — an absent state is never faked.
 */
internal fun orbPhaseForVoice(phase: VoicePhase, amplitude: Float = 0f): OrbPhase? = when (phase) {
    VoicePhase.Listening -> if (amplitude > 0.02f) OrbPhase.RECORDING else OrbPhase.LISTENING
    VoicePhase.Processing -> OrbPhase.REASONING
    else -> null
}
