"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyHeader = NexlyHeader;
const jsx_runtime_1 = require("react/jsx-runtime");
const GlassIcon_1 = require("./GlassIcon");
const NexlyOrb_1 = require("./NexlyOrb");
/** Demo chrome: the reference header, included so the response is seen in context. */
function NexlyHeader() {
    return ((0, jsx_runtime_1.jsxs)("header", { className: "nx-header", children: [(0, jsx_runtime_1.jsx)(NexlyOrb_1.NexlyOrb, { size: 42 }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-brand", children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-brand-name", children: "Nexly" }), (0, jsx_runtime_1.jsx)("div", { className: "nx-brand-sub", children: "AI Companion" })] }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-header-actions", children: [(0, jsx_runtime_1.jsx)("button", { className: "nx-glass-button", type: "button", "aria-label": "New spark", children: (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "sparkles", size: 18 }) }), (0, jsx_runtime_1.jsx)("button", { className: "nx-glass-button", type: "button", "aria-label": "Menu", children: (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "menu-2", size: 18 }) })] })] }));
}
