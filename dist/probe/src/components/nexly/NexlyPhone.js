"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyPhone = NexlyPhone;
const jsx_runtime_1 = require("react/jsx-runtime");
const GlassIcon_1 = require("./GlassIcon");
const NexlyAssistantTurn_1 = require("./NexlyAssistantTurn");
const NexlyComposer_1 = require("./NexlyComposer");
const NexlyHeader_1 = require("./NexlyHeader");
const NexlyUserTurn_1 = require("./NexlyUserTurn");
const demo_1 = require("@/lib/nexly/demo");
const plan_1 = require("@/lib/nexly/plan");
/** The reference screen, assembled. Response components are reusable; the
 *  header/user/composer here are demo context, not the deliverable. */
function NexlyPhone() {
    // The demo goes through the real wire parser, not a hard-coded component tree.
    const reply = (0, plan_1.parseNexlyReply)(demo_1.NEXLY_DEMO_WIRE);
    return ((0, jsx_runtime_1.jsx)("div", { className: "nx-phone-shell", children: (0, jsx_runtime_1.jsxs)("div", { className: "nx-phone", children: [(0, jsx_runtime_1.jsxs)("div", { className: "nx-status", children: [(0, jsx_runtime_1.jsx)("span", { className: "nx-status-time", children: "9:41" }), (0, jsx_runtime_1.jsxs)("span", { className: "nx-status-icons", children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "antenna-bars-5", size: 13 }), (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "wifi", size: 14 }), (0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "battery-4", size: 15 })] })] }), (0, jsx_runtime_1.jsx)(NexlyHeader_1.NexlyHeader, {}), (0, jsx_runtime_1.jsxs)("main", { className: "nx-phone-scroll", children: [(0, jsx_runtime_1.jsx)(NexlyUserTurn_1.NexlyUserTurn, { text: demo_1.NEXLY_DEMO_USER.text, time: demo_1.NEXLY_DEMO_USER.time, delivered: demo_1.NEXLY_DEMO_USER.delivered }), (0, jsx_runtime_1.jsx)(NexlyAssistantTurn_1.NexlyAssistantTurn, { preamble: reply.preamble, thinking: demo_1.NEXLY_DEMO_THINKING }), (0, jsx_runtime_1.jsx)(NexlyAssistantTurn_1.NexlyAssistantTurn, { plan: reply.plan })] }), (0, jsx_runtime_1.jsx)(NexlyComposer_1.NexlyComposer, {}), (0, jsx_runtime_1.jsx)("div", { className: "nx-home-indicator", "aria-hidden": "true" })] }) }));
}
