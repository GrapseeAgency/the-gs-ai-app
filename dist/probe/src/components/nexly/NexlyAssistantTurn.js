"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyAssistantTurn = NexlyAssistantTurn;
const jsx_runtime_1 = require("react/jsx-runtime");
const NexlyOrb_1 = require("./NexlyOrb");
const NexlyPlanCard_1 = require("./NexlyPlanCard");
const NexlyRichText_1 = require("./NexlyRichText");
const NexlyThinkingCard_1 = require("./NexlyThinkingCard");
/**
 * One assistant turn: orb outside, glass card(s) inside. The status card is a
 * child of the same card as the preamble, so it can remain visible while the
 * answer streams instead of vanishing at the first token.
 */
function NexlyAssistantTurn({ preamble, thinking, plan, className, }) {
    return ((0, jsx_runtime_1.jsxs)("div", { className: `nx-turn nx-turn-assistant ${className ?? ''}`, children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-turn-orb", children: (0, jsx_runtime_1.jsx)(NexlyOrb_1.NexlyOrb, { size: 34 }) }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-assistant-stack", children: [preamble || thinking ? ((0, jsx_runtime_1.jsxs)("div", { className: "nx-assistant-card", children: [preamble ? ((0, jsx_runtime_1.jsx)("div", { className: "nx-preamble", children: (0, jsx_runtime_1.jsx)(NexlyRichText_1.NexlyRichText, { text: preamble }) })) : null, thinking ? (0, jsx_runtime_1.jsx)(NexlyThinkingCard_1.NexlyThinkingCard, { thinking: thinking }) : null] })) : null, plan ? (0, jsx_runtime_1.jsx)(NexlyPlanCard_1.NexlyPlanCard, { plan: plan }) : null, plan?.timestamp ? (0, jsx_runtime_1.jsx)("div", { className: "nx-turn-time", children: plan.timestamp }) : null] })] }));
}
