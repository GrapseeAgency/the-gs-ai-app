"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyRichText = NexlyRichText;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const icons_1 = require("@/lib/nexly/icons");
const GlassIcon_1 = require("./GlassIcon");
/**
 * Model prose with two guarantees:
 *  - :icon-name: renders a glass glyph, never literal punctuation;
 *  - known emoji become the same glyphs, unknown pictographs are dropped.
 * The prompt already bans emoji. This is the renderer keeping that promise.
 */
function NexlyRichText({ text, iconTile = 20, iconSize = 11, className, }) {
    const normalised = (0, icons_1.emojiToIconTokens)(text);
    const nodes = [];
    let last = 0;
    let key = 0;
    for (const match of normalised.matchAll(icons_1.ICON_TOKEN_RE)) {
        const index = match.index ?? 0;
        if (index > last) {
            nodes.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: normalised.slice(last, index) }, `t${key++}`));
        }
        const raw = match[1];
        const name = (0, icons_1.canonicalIconName)(raw);
        if (name) {
            nodes.push((0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: name, tile: iconTile, size: iconSize, className: "nx-inline-icon" }, `i${key++}`));
        }
        else {
            nodes.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: match[0] }, `u${key++}`));
        }
        last = index + match[0].length;
    }
    if (last < normalised.length) {
        nodes.push((0, jsx_runtime_1.jsx)(react_1.Fragment, { children: normalised.slice(last) }, `t${key++}`));
    }
    return (0, jsx_runtime_1.jsx)("span", { className: className, children: nodes });
}
