"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyThinkingCard = NexlyThinkingCard;
const jsx_runtime_1 = require("react/jsx-runtime");
const GlassIcon_1 = require("./GlassIcon");
/**
 * The three-stage status card. This is a sibling of the answer, not a
 * pre-first-token placeholder: callers can keep it mounted while the preamble
 * and the plan are already on screen, which is exactly the reference order.
 */
function NexlyThinkingCard({ thinking }) {
    return ((0, jsx_runtime_1.jsx)("div", { className: "nx-thinking", "data-active": thinking.active ? 'true' : 'false', children: thinking.rows.map((row, index) => ((0, jsx_runtime_1.jsxs)("div", { className: "nx-thinking-row", children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: row.icon, tone: row.tone, tile: 34, size: 17, title: row.title }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-thinking-copy", children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-thinking-title", children: row.title }), (0, jsx_runtime_1.jsx)("div", { className: "nx-thinking-subtitle", children: row.subtitle })] })] }, `${row.title}-${index}`))) }));
}
