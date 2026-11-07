"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.useWidgetIdentity = useWidgetIdentity;
exports.WidgetSurface = WidgetSurface;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * WidgetSurface — the card frame every GenUI block renders in.
 *
 * It resolves the block's face (accent + variant + palette) once and hands it
 * to children through context, so charts, controls and text inside all follow
 * the same identity without re-deriving it.
 */
const react_1 = require("react");
const tokens_1 = require("./tokens");
const surfaces_1 = require("./surfaces");
const IdentityContext = (0, react_1.createContext)(null);
function useWidgetIdentity() {
    const ctx = (0, react_1.useContext)(IdentityContext);
    // Renderers used outside a surface (gallery scaffolding) fall back to the
    // page's dark identity with the default accent.
    return (ctx ?? {
        accent: (0, tokens_1.accentOf)(undefined, undefined),
        variant: undefined,
        theme: (0, tokens_1.themeFor)(true),
        paletteId: undefined,
        ramp: (count) => (0, tokens_1.rampOf)((0, tokens_1.accentOf)(undefined, undefined), undefined, count),
    });
}
function WidgetSurface({ accent, variant, palette, dark = true, bare = false, style, className, children, }) {
    const theme = (0, react_1.useMemo)(() => (0, tokens_1.themeFor)(dark), [dark]);
    const resolvedAccent = (0, tokens_1.accentOf)(accent, palette);
    const paletteHues = (0, tokens_1.paletteById)(palette)?.hues;
    const identity = (0, react_1.useMemo)(() => ({
        accent: resolvedAccent,
        variant,
        theme,
        paletteId: palette,
        ramp: (count) => (0, tokens_1.rampOf)(resolvedAccent, palette, count),
    }), [resolvedAccent, variant, theme, palette, paletteHues]);
    const face = (0, surfaces_1.surfaceStyle)(variant, resolvedAccent, theme);
    const outer = {
        fontFamily: tokens_1.FONT,
        color: theme.text,
        position: 'relative',
        ...face,
        ...(bare ? { background: 'transparent', border: 'none', boxShadow: 'none', padding: 0 } : {}),
        ...style,
    };
    return ((0, jsx_runtime_1.jsx)(IdentityContext.Provider, { value: identity, children: (0, jsx_runtime_1.jsxs)("div", { className: className, style: outer, children: [face.ruleColor && !bare && ((0, jsx_runtime_1.jsx)("span", { "aria-hidden": true, style: {
                        position: 'absolute',
                        left: 0,
                        top: 12,
                        bottom: 12,
                        width: 3,
                        borderRadius: 2,
                        background: face.ruleColor,
                    } })), children] }) }));
}
