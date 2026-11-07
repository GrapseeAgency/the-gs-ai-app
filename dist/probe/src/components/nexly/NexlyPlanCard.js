"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyPlanCard = NexlyPlanCard;
const jsx_runtime_1 = require("react/jsx-runtime");
const GlassIcon_1 = require("./GlassIcon");
const NexlyRichText_1 = require("./NexlyRichText");
const icons_1 = require("@/lib/nexly/icons");
/**
 * The timetable. One glass card, one row per time block, one glass glyph per
 * row, a closing line and a timestamp. This is the surface that used to fall
 * through to markdown bullets.
 */
function NexlyPlanCard({ plan }) {
    const closingHasIcon = plan.closing
        ? [...(0, icons_1.emojiToIconTokens)(plan.closing).matchAll(icons_1.ICON_TOKEN_RE)].length > 0
        : false;
    return ((0, jsx_runtime_1.jsxs)("div", { className: "nx-plan", "aria-label": plan.title, children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-plan-title", children: (0, jsx_runtime_1.jsx)(NexlyRichText_1.NexlyRichText, { text: plan.title, iconTile: 20, iconSize: 11 }) }), (0, jsx_runtime_1.jsx)("div", { className: "nx-plan-rows", children: plan.rows.map((row, index) => ((0, jsx_runtime_1.jsxs)("div", { className: "nx-plan-row", children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: row.icon, tone: row.tone, tile: 28, size: 14, title: row.icon }), (0, jsx_runtime_1.jsx)("span", { className: "nx-plan-time", children: row.time }), (0, jsx_runtime_1.jsx)("span", { className: "nx-plan-text", children: (0, jsx_runtime_1.jsx)(NexlyRichText_1.NexlyRichText, { text: row.text, iconTile: 18, iconSize: 10 }) })] }, `${row.time}-${index}`))) }), plan.closing ? ((0, jsx_runtime_1.jsxs)("div", { className: "nx-plan-closing", children: [(0, jsx_runtime_1.jsx)(NexlyRichText_1.NexlyRichText, { text: plan.closing, iconTile: 22, iconSize: 12, className: "nx-plan-closing-text" }), plan.closingIcon && !closingHasIcon ? ((0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: plan.closingIcon, tone: "peach", tile: 22, size: 12, title: plan.closingIcon })) : null] })) : null] }));
}
