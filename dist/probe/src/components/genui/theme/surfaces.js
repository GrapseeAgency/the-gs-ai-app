"use strict";
/**
 * Surface styles — the six variants of the rulebook, as CSS.
 *
 * plain | tinted | hero | outline | editorial | inset
 *
 * The same role rules as WidgetBlocks.kt CardTone: one vocabulary, and the
 * art director guarantees adjacent widgets never wear the same one.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.surfaceStyle = surfaceStyle;
const tokens_1 = require("./tokens");
function surfaceStyle(variant, accent, theme) {
    switch (variant) {
        case 'hero':
            return {
                background: `linear-gradient(160deg, ${(0, tokens_1.alpha)(accent, theme.dark ? 0.26 : 0.18)}, ${(0, tokens_1.alpha)(accent, theme.dark ? 0.10 : 0.06)} 55%, ${theme.card})`,
                border: `1px solid ${(0, tokens_1.alpha)(accent, theme.dark ? 0.35 : 0.25)}`,
                radius: tokens_1.RADIUS.xl,
                padding: 20,
            };
        case 'outline':
            return {
                background: 'transparent',
                border: `1px solid ${theme.border}`,
                radius: tokens_1.RADIUS.lg,
                padding: 16,
            };
        case 'editorial':
            return {
                background: 'transparent',
                border: 'none',
                radius: tokens_1.RADIUS.md,
                padding: 16,
                ruleColor: accent,
            };
        case 'inset':
            return {
                background: theme.dark ? 'rgba(0,0,0,0.28)' : 'rgba(0,0,0,0.045)',
                border: `1px solid ${theme.border}`,
                radius: tokens_1.RADIUS.lg,
                padding: 16,
                boxShadow: 'inset 0 1px 3px rgba(0,0,0,0.14)',
            };
        case 'tinted':
            return {
                background: `linear-gradient(180deg, ${(0, tokens_1.alpha)(accent, theme.dark ? 0.16 : 0.10)}, ${theme.card})`,
                border: `1px solid ${(0, tokens_1.alpha)(accent, theme.dark ? 0.22 : 0.14)}`,
                radius: tokens_1.RADIUS.lg,
                padding: 16,
            };
        case 'plain':
        default:
            return {
                background: theme.card,
                border: `1px solid ${theme.border}`,
                radius: tokens_1.RADIUS.lg,
                padding: 16,
            };
    }
}
