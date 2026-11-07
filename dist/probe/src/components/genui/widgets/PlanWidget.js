"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.PlanWidget = PlanWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const NexlyPlanCard_1 = require("@/components/nexly/NexlyPlanCard");
/**
 * Catalogue renderer for the `plan` fence. The native apps own the exact
 * glass composition; this wrapper keeps the web pipeline total so a plan
 * emitted by the model is never downgraded to a code block.
 */
function PlanWidget({ payload }) {
    return (0, jsx_runtime_1.jsx)(NexlyPlanCard_1.NexlyPlanCard, { plan: payload });
}
