"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyOrb = NexlyOrb;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
/** The assistant identity: a small glass torus, not an emoji or a letter. */
function NexlyOrb({ size = 34 }) {
    const uid = (0, react_1.useId)().replace(/[^a-zA-Z0-9_-]/g, '');
    const bodyId = `nx-orb-body-${uid}`;
    const swirlId = `nx-orb-swirl-${uid}`;
    return ((0, jsx_runtime_1.jsx)("span", { className: "nx-orb", style: { width: size, height: size }, "aria-hidden": "true", children: (0, jsx_runtime_1.jsxs)("svg", { viewBox: "0 0 40 40", width: size, height: size, children: [(0, jsx_runtime_1.jsxs)("defs", { children: [(0, jsx_runtime_1.jsxs)("radialGradient", { id: bodyId, cx: "34%", cy: "28%", r: "72%", children: [(0, jsx_runtime_1.jsx)("stop", { offset: "0%", stopColor: "#fffdf9" }), (0, jsx_runtime_1.jsx)("stop", { offset: "28%", stopColor: "#ffd7a8" }), (0, jsx_runtime_1.jsx)("stop", { offset: "62%", stopColor: "#ff9a4d" }), (0, jsx_runtime_1.jsx)("stop", { offset: "100%", stopColor: "#ef6425" })] }), (0, jsx_runtime_1.jsxs)("linearGradient", { id: swirlId, x1: "0", y1: "0", x2: "1", y2: "1", children: [(0, jsx_runtime_1.jsx)("stop", { offset: "0%", stopColor: "#ffffff", stopOpacity: "0.95" }), (0, jsx_runtime_1.jsx)("stop", { offset: "55%", stopColor: "#fff3e3", stopOpacity: "0.4" }), (0, jsx_runtime_1.jsx)("stop", { offset: "100%", stopColor: "#ffffff", stopOpacity: "0.05" })] })] }), (0, jsx_runtime_1.jsx)("circle", { cx: "20", cy: "20", r: "19", fill: `url(#${bodyId})` }), (0, jsx_runtime_1.jsx)("circle", { cx: "20", cy: "20", r: "18.4", fill: "none", stroke: "#ffffff", strokeOpacity: "0.6" }), (0, jsx_runtime_1.jsx)("path", { d: "M8 24c2.6-8.4 9.8-13.8 17.6-12.4 5.2.9 7.8 4.6 6.4 8.4-1.5 4-7.2 6.2-12.6 4.6-4.3-1.3-6.2-4.4-4.7-7.1 1.2-2.2 4.2-3.3 7.1-2.4", fill: "none", stroke: `url(#${swirlId})`, strokeWidth: "2.1", strokeLinecap: "round" }), (0, jsx_runtime_1.jsx)("ellipse", { cx: "13.5", cy: "11.5", rx: "5.2", ry: "2.6", fill: "#ffffff", opacity: "0.55", transform: "rotate(-28 13.5 11.5)" })] }) }));
}
