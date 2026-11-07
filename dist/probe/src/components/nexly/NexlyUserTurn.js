"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyUserTurn = NexlyUserTurn;
const jsx_runtime_1 = require("react/jsx-runtime");
const GlassIcon_1 = require("./GlassIcon");
const NexlyRichText_1 = require("./NexlyRichText");
/** Demo chrome: the user turn the assistant is answering. */
function NexlyUserTurn({ text, time, delivered = true, }) {
    return ((0, jsx_runtime_1.jsxs)("div", { className: "nx-turn nx-turn-user", children: [(0, jsx_runtime_1.jsxs)("div", { className: "nx-user-main", children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-user-bubble", children: (0, jsx_runtime_1.jsx)(NexlyRichText_1.NexlyRichText, { text: text, iconTile: 18, iconSize: 10 }) }), (0, jsx_runtime_1.jsx)("span", { className: "nx-user-avatar", "aria-hidden": "true", children: (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "user", size: 17 }) })] }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-user-meta", children: [(0, jsx_runtime_1.jsx)("span", { children: time }), delivered ? ((0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "checks", size: 13, className: "nx-delivered" })) : null] })] }));
}
