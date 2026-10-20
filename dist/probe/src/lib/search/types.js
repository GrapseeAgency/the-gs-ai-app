"use strict";
/**
 * PHASE 8.1/8.2 — shared types for the backend-owned search foundation.
 *
 * Architecture rule (8.2 §1): the model is the reasoning/synthesis layer;
 * search is OUR backend capability. The model never invents the result set —
 * it only ever sees evidence produced by `runResearch()`, and every visible
 * research step in the clients maps 1:1 to an event emitted here.
 *
 * Wire contract: docs/search-event-protocol.md (FROZEN v2).
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.NOOP_EMIT = void 0;
const NOOP_EMIT = () => undefined;
exports.NOOP_EMIT = NOOP_EMIT;
