"use strict";
/**
 * The GenUI web library — public surface.
 *
 * Consumers (the chat page, the gallery) import from here only:
 *   - MessageContent: a full assistant answer, prose + widgets inline
 *   - WidgetRenderer: one validated payload -> one themed component
 *   - parseStream: markdown -> segments (for custom hosts)
 *   - WidgetSurface / tokens: the theme system
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __exportStar = (this && this.__exportStar) || function(m, exports) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports, p)) __createBinding(exports, m, p);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.formatOutput = exports.evaluateExpr = exports.rendererFor = exports.REGISTRY = exports.FONT = exports.RADIUS = exports.SPACE = exports.TYPE = exports.SURFACES = exports.PALETTES = exports.DATA_HUES = exports.hueAt = exports.inkOn = exports.alpha = exports.rampOf = exports.paletteById = exports.accentOf = exports.themeFor = exports.surfaceStyle = exports.useWidgetIdentity = exports.WidgetSurface = exports.parseStream = exports.WidgetRenderer = exports.MessageContent = void 0;
var MessageContent_1 = require("./MessageContent");
Object.defineProperty(exports, "MessageContent", { enumerable: true, get: function () { return MessageContent_1.MessageContent; } });
var WidgetRenderer_1 = require("./WidgetRenderer");
Object.defineProperty(exports, "WidgetRenderer", { enumerable: true, get: function () { return WidgetRenderer_1.WidgetRenderer; } });
var stream_1 = require("./stream");
Object.defineProperty(exports, "parseStream", { enumerable: true, get: function () { return stream_1.parseStream; } });
var WidgetSurface_1 = require("./theme/WidgetSurface");
Object.defineProperty(exports, "WidgetSurface", { enumerable: true, get: function () { return WidgetSurface_1.WidgetSurface; } });
Object.defineProperty(exports, "useWidgetIdentity", { enumerable: true, get: function () { return WidgetSurface_1.useWidgetIdentity; } });
var surfaces_1 = require("./theme/surfaces");
Object.defineProperty(exports, "surfaceStyle", { enumerable: true, get: function () { return surfaces_1.surfaceStyle; } });
var tokens_1 = require("./theme/tokens");
Object.defineProperty(exports, "themeFor", { enumerable: true, get: function () { return tokens_1.themeFor; } });
Object.defineProperty(exports, "accentOf", { enumerable: true, get: function () { return tokens_1.accentOf; } });
Object.defineProperty(exports, "paletteById", { enumerable: true, get: function () { return tokens_1.paletteById; } });
Object.defineProperty(exports, "rampOf", { enumerable: true, get: function () { return tokens_1.rampOf; } });
Object.defineProperty(exports, "alpha", { enumerable: true, get: function () { return tokens_1.alpha; } });
Object.defineProperty(exports, "inkOn", { enumerable: true, get: function () { return tokens_1.inkOn; } });
Object.defineProperty(exports, "hueAt", { enumerable: true, get: function () { return tokens_1.hueAt; } });
Object.defineProperty(exports, "DATA_HUES", { enumerable: true, get: function () { return tokens_1.DATA_HUES; } });
Object.defineProperty(exports, "PALETTES", { enumerable: true, get: function () { return tokens_1.PALETTES; } });
Object.defineProperty(exports, "SURFACES", { enumerable: true, get: function () { return tokens_1.SURFACES; } });
Object.defineProperty(exports, "TYPE", { enumerable: true, get: function () { return tokens_1.TYPE; } });
Object.defineProperty(exports, "SPACE", { enumerable: true, get: function () { return tokens_1.SPACE; } });
Object.defineProperty(exports, "RADIUS", { enumerable: true, get: function () { return tokens_1.RADIUS; } });
Object.defineProperty(exports, "FONT", { enumerable: true, get: function () { return tokens_1.FONT; } });
var registry_1 = require("./registry");
Object.defineProperty(exports, "REGISTRY", { enumerable: true, get: function () { return registry_1.REGISTRY; } });
Object.defineProperty(exports, "rendererFor", { enumerable: true, get: function () { return registry_1.rendererFor; } });
var evaluate_1 = require("./calc/evaluate");
Object.defineProperty(exports, "evaluateExpr", { enumerable: true, get: function () { return evaluate_1.evaluateExpr; } });
Object.defineProperty(exports, "formatOutput", { enumerable: true, get: function () { return evaluate_1.formatOutput; } });
__exportStar(require("./types"), exports);
