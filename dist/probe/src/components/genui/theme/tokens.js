"use strict";
/**
 * The GenUI theme tokens — the web twin of the design rulebook.
 *
 * The values are the rulebook's values (src/lib/design/rulebook.ts), which are
 * the Android WidgetPalette's values. One vocabulary, three renderers. A widget
 * compiled on the backend names an accent/variant/palette; this file is where
 * the web client turns those names into pixels.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.FONT = exports.RADIUS = exports.SPACE = exports.TYPE = exports.SURFACES = exports.PALETTES = exports.DATA_HUES = void 0;
exports.themeFor = themeFor;
exports.hueAt = hueAt;
exports.paletteById = paletteById;
exports.accentOf = accentOf;
exports.rampOf = rampOf;
exports.alpha = alpha;
exports.luminance = luminance;
exports.inkOn = inkOn;
const rulebook_1 = require("@/lib/design/rulebook");
var rulebook_2 = require("@/lib/design/rulebook");
Object.defineProperty(exports, "DATA_HUES", { enumerable: true, get: function () { return rulebook_2.DATA_HUES; } });
Object.defineProperty(exports, "PALETTES", { enumerable: true, get: function () { return rulebook_2.PALETTES; } });
Object.defineProperty(exports, "SURFACES", { enumerable: true, get: function () { return rulebook_2.SURFACES; } });
function themeFor(dark) {
    const ink = dark ? rulebook_1.INK.dark : rulebook_1.INK.light;
    const surf = dark ? rulebook_1.SURFACE.dark : rulebook_1.SURFACE.light;
    return {
        dark,
        text: ink.primary,
        text2: ink.secondary,
        text3: ink.tertiary,
        canvas: surf.canvas,
        card: surf.card,
        raised: surf.raised,
        border: ink.rule,
    };
}
/** Hue by palette position (1-12) — mirrors the --gs-hue-N custom properties. */
function hueAt(index) {
    return rulebook_1.DATA_HUES[((index % rulebook_1.DATA_HUES.length) + rulebook_1.DATA_HUES.length) % rulebook_1.DATA_HUES.length];
}
function paletteById(id) {
    if (!id)
        return null;
    return rulebook_1.PALETTES.find((p) => p.id === id.toLowerCase()) ?? null;
}
/** A safe accent: the named hex, or the palette lead, or azure. */
function accentOf(accent, paletteId) {
    if (accent && /^#[0-9a-fA-F]{6}$/.test(accent))
        return accent.toLowerCase();
    const palette = paletteById(paletteId);
    return palette ? palette.hues[0] : rulebook_1.DATA_HUES[0];
}
/** The palette's hues with the accent first — the series colour ramp. */
function rampOf(accent, paletteId, count) {
    const palette = paletteById(paletteId);
    const rest = palette ? palette.hues.filter((h) => h.toLowerCase() !== accent) : [];
    const pool = [accent, ...rest, ...rulebook_1.DATA_HUES.filter((h) => h.toLowerCase() !== accent)];
    return Array.from({ length: Math.max(1, count) }, (_, i) => pool[i % pool.length]);
}
/** Alpha compositing on any hex — the tinted surface wash, chart fills. */
function alpha(hex, a) {
    const value = hex.replace('#', '');
    const r = parseInt(value.slice(0, 2), 16);
    const g = parseInt(value.slice(2, 4), 16);
    const b = parseInt(value.slice(4, 6), 16);
    return `rgba(${r},${g},${b},${a})`;
}
/** Perceived luminance 0-1, so text on an accent stays readable. */
function luminance(hex) {
    const value = hex.replace('#', '');
    const channel = (i) => {
        const c = parseInt(value.slice(i, i + 2), 16) / 255;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4);
}
/** Black or white text on a coloured fill — never hue-on-hue. */
function inkOn(hex) {
    return luminance(hex) > 0.4 ? '#14171C' : '#FFFFFF';
}
exports.TYPE = {
    display: 28,
    title: 22,
    heading: 17,
    body: 15,
    label: 13,
    micro: 11,
};
exports.SPACE = [4, 8, 12, 16, 24, 32];
exports.RADIUS = { md: 12, lg: 14, xl: 16, pill: 999 };
exports.FONT = "-apple-system, 'SF Pro Text', 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif";
