#!/usr/bin/env node
/**
 * gen-orb-spec.mjs — regenerate the native thinking-orbs spec constants.
 *
 * Source of truth (vendored from Jakubantalik/thinking-orbs@0.3.1, MIT):
 *   docs/orbs/orbs-spec.json           — the upstream spec (states, modes, labels, timing)
 *   docs/orbs/orbs-resolved.json       — the 18 fully-resolved (state × size) option sets,
 *                                        extracted from upstream's spec/orbs-golden.json
 *   docs/orbs/orbs-golden-subset.json  — golden dot vectors at frozen timestamps
 *                                        (dot counts + first-3/last-2 draw-order samples)
 *
 * Emits (idempotent, committed):
 *   android/.../ui/orbs/OrbSpec.kt     — typed option classes + exact resolved constants
 *   ios/App/Sources/Orbs/OrbSpec.swift — same, Swift
 *   android/.../test/.../OrbGoldenData.kt
 *   ios/App/Tests/AppTests/OrbGoldenData.swift
 *
 * Run: bun scripts/gen-orb-spec.mjs   (or node scripts/gen-orb-spec.mjs)
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const spec = JSON.parse(readFileSync(join(root, 'docs/orbs/orbs-spec.json'), 'utf8'));
const resolvedJson = JSON.parse(readFileSync(join(root, 'docs/orbs/orbs-resolved.json'), 'utf8'));
const golden = JSON.parse(readFileSync(join(root, 'docs/orbs/orbs-golden-subset.json'), 'utf8'));

// Typed field schemas per mode. i = Int (count-type), f = Float/Double, b = Bool.
const MODE_FIELDS = {
  globe: [
    ['latRings', 'i'], ['lonDensity', 'i'], ['rBase', 'f'], ['rDepth', 'f'], ['rBoost', 'f'],
    ['inkFar', 'f'], ['inkSpan', 'f'], ['rsPow', 'f'], ['rMin', 'f'], ['scanMul', 'f'], ['dimBase', 'f'],
  ],
  orbits: [
    ['orbitN', 'i'], ['ghostN', 'i'], ['ghostR', 'f'], ['ghostA', 'f'], ['particles', 'i'],
    ['partR', 'f'], ['partRDepth', 'f'], ['rsPow', 'f'], ['rMin', 'f'],
  ],
  rubik: [
    ['latRings', 'i'], ['lonDensity', 'i'], ['moveCount', 'i'], ['rBase', 'f'], ['rDepth', 'f'],
    ['rActive', 'f'], ['inkFar', 'f'], ['inkSpan', 'f'], ['rsPow', 'f'], ['rMin', 'f'],
  ],
  wave: [
    ['rings', 'i'], ['lonDensity', 'i'], ['rBase', 'f'], ['rDepth', 'f'], ['rsPow', 'f'], ['rMin', 'f'],
  ],
  web: [
    ['nodeN', 'i'], ['thr', 'f'], ['signals', 'i'], ['nodeR', 'f'], ['nodeRDepth', 'f'],
    ['lineW', 'f'], ['rsPow', 'f'], ['rMin', 'f'],
  ],
  braid: [
    ['strandN', 'i'], ['turns', 'f'], ['ghostN', 'i'], ['rBase', 'f'], ['rDepth', 'f'], ['rsPow', 'f'], ['rMin', 'f'],
  ],
  ribbon: [
    ['lanes', 'i'], ['segs', 'i'], ['ghostN', 'i'], ['spin', 'f'], ['bandMul', 'f'], ['wobMul', 'f'],
    ['faceOn', 'b'], ['rBase', 'f'], ['rDepth', 'f'], ['rsPow', 'f'], ['rMin', 'f'],
  ],
  morph: [
    ['rDot', 'f'], ['iconD', 'f'], ['rMin', 'f'], ['spread', 'f'],
  ],
};

const MODE_FIELD_MAP = new Map(Object.entries(MODE_FIELDS));
// ring shares ribbon's geometry painter — the faceOn flag switches it (upstream registry).
MODE_FIELD_MAP.set('ring', MODE_FIELD_MAP.get('ribbon'));
const RESOLVED_MAP = new Map(Object.entries(resolvedJson));

const STATES = spec.enums.states; // 9, ordered
const SIZES = spec.enums.sizes; // [64, 20]
const LABELS = spec.labels;
const STATE_TO_MODE = spec.stateToMode;

const pascal = (s) => s[0].toUpperCase() + s.slice(1);
const className = (mode) => `${pascal(mode)}Opts`;

function fmtNum(v) {
  if (Number.isInteger(v)) return String(v);
  return String(v);
}

/** Double literal — integral values keep a trailing .0 (Kotlin/Swift type strictness). */
function fmtFloat(v) {
  if (Number.isInteger(v)) return v + '.0';
  return String(v);
}

function kotlinOptsClass(mode) {
  const fields = MODE_FIELD_MAP.get(mode)
    .map(([k, t]) => {
      const ty = t === 'i' ? 'Int' : t === 'b' ? 'Boolean' : 'Double';
      return `    val ${k}: ${ty},`;
    })
    .join('\n');
  return `/** Immutable resolved option set for the ${mode} mode (upstream "fine" profile × preset multipliers). */\ninternal data class ${className(mode)}(\n${fields}\n)`;
}

function swiftOptsStruct(mode) {
  const fields = MODE_FIELD_MAP.get(mode)
    .map(([k, t]) => {
      const ty = t === 'i' ? 'Int' : t === 'b' ? 'Bool' : 'Double';
      return `    let ${k}: ${ty}`;
    })
    .join('\n');
  const args = MODE_FIELD_MAP.get(mode)
    .map(([k, t]) => {
      const ty = t === 'i' ? 'Int' : t === 'b' ? 'Bool' : 'Double';
      return `${k}: ${ty}`;
    })
    .join(', ');
  const assigns = MODE_FIELD_MAP.get(mode)
    .map(([k]) => `        self.${k} = ${k}`)
    .join('\n');
  return `/// Immutable resolved option set for the ${mode} mode (upstream "fine" profile × preset multipliers).\npublic struct ${className(mode)} {\n${fields}\n    public init(${args}) {\n${assigns}\n    }\n}`;
}

function resolvedFor(state, size) {
  const key = `${state}-${size}`;
  const r = RESOLVED_MAP.get(key);
  if (!r) throw new Error(`missing resolved entry for ${key}`);
  const mode = r.mode;
  const schema = MODE_FIELD_MAP.get(mode);
  if (!schema) throw new Error(`no schema for mode ${mode}`);
  const fields = schema
    .map(([k, t]) => {
      const v = r.opts[k];
      if (t === 'b') return `${k} = ${v !== undefined && v !== 0}`; // absent bool = opt-out flag off
      if (v === undefined) throw new Error(`${key}: missing opt ${k}`);
      if (t === 'i') return `${k} = ${Math.round(v)}`;
      return `${k} = ${fmtFloat(v)}`;
    })
    .join(', ');
  // ring shares ribbon's painter AND option class (upstream registry)
  const canonical = mode === 'ring' ? 'ribbon' : mode;
  return { mode, speed: r.speed, args: fields, cls: className(canonical), wrapper: canonical };
}

// ---------- Kotlin ----------
function genKotlin() {
  const statesEnum = STATES.map((s) => {
    const label = LABELS[s].replace(/…/g, '\\u2026');
    return `    ${s.toUpperCase()}("${label}", OrbMode.${STATE_TO_MODE[s].toUpperCase()}),`;
  }).join('\n');

  const classes = Object.keys(MODE_FIELDS).map(kotlinOptsClass).join('\n\n');

  const resolveBranches = STATES.map((state) => {
    const arms = SIZES.map((size) => {
      const r = resolvedFor(state, size);
      const sizeName = size === 20 ? 'INLINE' : 'STANDARD';
      return `            OrbSize.${sizeName} -> ResolvedOrb(OrbMode.${r.mode.toUpperCase()}, ${fmtFloat(r.speed)}, ${r.cls}(${r.args}))`;
    }).join('\n');
    return `        OrbState.${state.toUpperCase()} -> when (size) {\n${arms}\n        }`;
  }).join('\n');

  return `// GENERATED by scripts/gen-orb-spec.mjs from docs/orbs/orbs-spec.json (thinking-orbs@${spec.sourceLibrary.version}, specVersion ${spec.specVersion}). DO NOT hand-edit — regenerate.
package com.grapsee.gsai.ui.orbs

/**
 * The nine activity states an AI assistant can be in — the logical model the
 * product maps its REAL application states onto (never the reverse; see
 * OrbStateMapping.kt for the honest app-state → orb-state layer).
 */
enum class OrbState(val label: String, val mode: OrbMode) {
${statesEnum}
}

/** One distinct dot-geometry animation per state — shape + motion + density, never colour. */
enum class OrbMode { ORBITS, GLOBE, RUBIK, WAVE, WEB, BRAID, RIBBON, RING, MORPH }

/**
 * The two purpose-tuned sizes from the reference (separate designs, not a
 * scale factor): INLINE = message-status scale, STANDARD = assistant-avatar
 * scale. Each carries its own dot count, dot size and speed tuning.
 */
enum class OrbSize(val px: Int) { INLINE(20), STANDARD(64) }

${classes}

/** A fully-resolved orb: which mode draws it, its clock speed, its exact options. */
internal data class ResolvedOrb(val mode: OrbMode, val speed: Double, val opts: Any)

internal object OrbSpec {
    /** Reduced-motion static representative frame (upstream paint contract). */
    const val STATIC_T = 0.6

    fun resolve(state: OrbState, size: OrbSize): ResolvedOrb = when (state) {
${resolveBranches}
    }
}
`;
}

// ---------- Swift ----------
function genSwift() {
  const stateCases = STATES.map((s) => `        case .${s}: return .${STATE_TO_MODE[s]}`).join('\n');
  const labelCases = STATES.map((s) => `        case .${s}: return "${LABELS[s]}"`).join('\n');
  const structs = Object.keys(MODE_FIELDS).map(swiftOptsStruct).join('\n\n');

  const resolveSwitch = STATES.map((state) => {
    const arms = SIZES.map((size) => {
      const r = resolvedFor(state, size);
      const sizeName = size === 20 ? 'inline' : 'standard';
      const swiftArgs = r.args.replace(/ = /g, ': ');
      return `            case .${sizeName}: return ResolvedOrb(mode: .${r.mode}, speed: ${fmtFloat(r.speed)}, opts: .${r.wrapper}(${r.cls}(${swiftArgs})))`;
    }).join('\n');
    return `        case .${state}:\n            switch size {\n${arms}\n            }`;
  }).join('\n');

  return `// GENERATED by scripts/gen-orb-spec.mjs from docs/orbs/orbs-spec.json (thinking-orbs@${spec.sourceLibrary.version}, specVersion ${spec.specVersion}). DO NOT hand-edit — regenerate.
import Foundation

/// The nine activity states an AI assistant can be in — the logical model the
/// product maps its REAL application states onto (never the reverse; see the
/// mapping helpers in ThinkingOrbView.swift).
public enum OrbState: String, CaseIterable, Sendable {
    case working, searching, solving, listening, connecting, weaving, composing, breathing, shaping

    /// Human-readable per-state label (also the accessibility description).
    public var label: String {
        switch self {
${labelCases}
        }
    }

    /// The distinct dot-geometry animation behind this state.
    public var mode: OrbMode {
        switch self {
${stateCases}
        }
    }
}

/// One distinct dot-geometry animation per state — shape + motion + density, never colour.
public enum OrbMode: Sendable {
    case orbits, globe, rubik, wave, web, braid, ribbon, ring, morph
}

/// The two purpose-tuned sizes from the reference (separate designs, not a
/// scale factor): inline = message-status scale, standard = assistant-avatar scale.
public enum OrbSize: Int, Sendable {
    case inline = 20
    case standard = 64
}

${structs}

/// A fully-resolved orb: which mode draws it, its clock speed, its exact options.
public struct ResolvedOrb: Sendable {
    public let mode: OrbMode
    public let speed: Double
    public let opts: OrbOpts
}

public enum OrbOpts: Sendable {
    case orbits(OrbitsOpts)
    case globe(GlobeOpts)
    case rubik(RubikOpts)
    case wave(WaveOpts)
    case web(WebOpts)
    case braid(BraidOpts)
    case ribbon(RibbonOpts)
    case ring(RibbonOpts)
    case morph(MorphOpts)
}

public enum OrbSpec {
    /// Reduced-motion static representative frame (upstream paint contract).
    public static let staticT = 0.6

    public static func resolve(state: OrbState, size: OrbSize) -> ResolvedOrb {
        switch state {
${resolveSwitch}
        }
    }
}
`;
}

// ---------- Golden data ----------
const GOLDEN_CASES = Array.isArray(golden) ? golden : golden.cases;

function kotlinGolden() {
  const cases = GOLDEN_CASES
    .map((c) => {
      // sample is already flat: 5 dots x 6 fields (x,y,z,r,white,alpha)
      const flat = c.sample
        .map((v) => `          ${fmtFloat(v)},`)
        .join('\n');
      const sizeName = c.size === 20 ? 'INLINE' : 'STANDARD';
      return `        OrbGoldenCase("${c.key}", OrbState.${c.state.toUpperCase()}, OrbSize.${sizeName}, ${fmtFloat(c.t)}, OrbMode.${c.mode.toUpperCase()}, ${c.dotCount}, ${c.lineCount}, doubleArrayOf(\n${flat}\n        )),`;
    })
    .join('\n');
  return `// GENERATED by scripts/gen-orb-spec.mjs from docs/orbs/orbs-golden-subset.json
// (thinking-orbs@${spec.sourceLibrary.version} — frozen-time dot vectors, tolerance 0.0001). DO NOT hand-edit.
package com.grapsee.gsai.ui.orbs

/** One frozen golden frame: exact dot count + first-3/last-2 dots of the z-sorted draw order. */
internal data class OrbGoldenCase(
    val key: String,
    val state: OrbState,
    val size: OrbSize,
    val t: Double,
    val mode: OrbMode,
    val dotCount: Int,
    val lineCount: Int,
    /** first-3 + last-2 draw-order dots, flattened as x,y,z,r,white,alpha sextuplets. */
    val sample: DoubleArray,
)

internal object OrbGoldenData {
    val cases: List<OrbGoldenCase> = listOf(
${cases}
    )
}
`;
}

function swiftGolden() {
  const cases = GOLDEN_CASES
    .map((c) => {
      // sample is already flat: 5 dots x 6 fields (x,y,z,r,white,alpha)
      const flat = c.sample
        .map((v) => `            ${fmtFloat(v)},`)
        .join('\n');
      const sizeName = c.size === 20 ? 'inline' : 'standard';
      return `        OrbGoldenCase(key: "${c.key}", state: .${c.state}, size: .${sizeName}, t: ${fmtFloat(c.t)}, mode: .${c.mode}, dotCount: ${c.dotCount}, lineCount: ${c.lineCount}, sample: [\n${flat}\n        ]),`;
    })
    .join('\n');
  return `// GENERATED by scripts/gen-orb-spec.mjs from docs/orbs/orbs-golden-subset.json
// (thinking-orbs@${spec.sourceLibrary.version} — frozen-time dot vectors, tolerance 0.0001). DO NOT hand-edit.
import Foundation
@testable import GSApp

/// One frozen golden frame: exact dot count + first-3/last-2 dots of the z-sorted draw order.
struct OrbGoldenCase {
    let key: String
    let state: OrbState
    let size: OrbSize
    let t: Double
    let mode: OrbMode
    let dotCount: Int
    let lineCount: Int
    /// first-3 + last-2 draw-order dots, flattened as x,y,z,r,white,alpha sextuplets.
    let sample: [Double]
}

enum OrbGoldenData {
    static let cases: [OrbGoldenCase] = [
${cases}
    ]
}
`;
}

const kotlinSpecPath = join(root, 'android/app/src/main/java/com/grapsee/gsai/ui/orbs/OrbSpec.kt');
const swiftSpecPath = join(root, 'ios/App/Sources/Orbs/OrbSpec.swift');
const kotlinGoldPath = join(root, 'android/app/src/test/java/com/grapsee/gsai/ui/orbs/OrbGoldenData.kt');
const swiftGoldPath = join(root, 'ios/App/Tests/AppTests/OrbGoldenData.swift');

writeFileSync(kotlinSpecPath, genKotlin());
writeFileSync(swiftSpecPath, genSwift());
writeFileSync(kotlinGoldPath, kotlinGolden());
writeFileSync(swiftGoldPath, swiftGolden());
console.log('generated: OrbSpec.kt, OrbSpec.swift, OrbGoldenData.kt, OrbGoldenData.swift');
