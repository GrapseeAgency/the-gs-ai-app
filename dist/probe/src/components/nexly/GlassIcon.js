"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.GlassIcon = GlassIcon;
const jsx_runtime_1 = require("react/jsx-runtime");
const icons_1 = require("@/lib/nexly/icons");
/**
 * The one icon primitive. The glyph is a <use> reference into the 5,184-symbol
 * sprite; the tile supplies the glass. No emoji, no per-icon React component.
 */
function GlassIcon({ name, size = 16, glyph: glyphSize, tile, tone, bare = false, flat = false, className, title, style, }) {
    const id = (0, icons_1.iconNameFor)(name);
    const resolvedTone = (0, icons_1.glassToneFor)(id, tone);
    const palette = icons_1.GLASS_TONES[resolvedTone];
    const resolvedGlyphSize = glyphSize ?? size;
    const resolvedTileSize = tile ?? (glyphSize !== undefined ? size : 32);
    const glyph = ((0, jsx_runtime_1.jsxs)("svg", { className: "nx-glyph", width: resolvedGlyphSize, height: resolvedGlyphSize, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 2, strokeLinecap: "round", strokeLinejoin: "round", "aria-hidden": title ? undefined : true, role: title ? 'img' : undefined, focusable: "false", children: [title ? (0, jsx_runtime_1.jsx)("title", { children: title }) : null, (0, jsx_runtime_1.jsx)("use", { href: `#gs-${id}` })] }));
    if (bare) {
        return ((0, jsx_runtime_1.jsx)("span", { className: `nx-icon-bare ${className ?? ''}`, style: { width: resolvedGlyphSize, height: resolvedGlyphSize, ...style }, children: glyph }));
    }
    const vars = {
        width: resolvedTileSize,
        height: resolvedTileSize,
        color: palette.ink,
        '--tone-top': palette.top,
        '--tone-bottom': palette.bottom,
        '--tone-halo': palette.halo,
        ...style,
    };
    return ((0, jsx_runtime_1.jsx)("span", { className: `nx-glass-icon ${flat ? 'is-flat' : ''} ${className ?? ''}`, style: vars, title: title, "aria-hidden": title ? undefined : true, children: glyph }));
}
