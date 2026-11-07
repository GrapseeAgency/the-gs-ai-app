"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyComposer = NexlyComposer;
const jsx_runtime_1 = require("react/jsx-runtime");
const GlassIcon_1 = require("./GlassIcon");
/** Demo chrome: the reference composer, one glass capsule. */
function NexlyComposer() {
    return ((0, jsx_runtime_1.jsx)("div", { className: "nx-composer-wrap", children: (0, jsx_runtime_1.jsxs)("div", { className: "nx-composer", children: [(0, jsx_runtime_1.jsx)("span", { className: "nx-composer-spark", children: (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "sparkles", size: 20 }) }), (0, jsx_runtime_1.jsx)("span", { className: "nx-composer-placeholder", children: "Ask me anything..." }), (0, jsx_runtime_1.jsx)("button", { className: "nx-send", type: "button", "aria-label": "Send", children: (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "send-2", size: 18 }) })] }) }));
}
