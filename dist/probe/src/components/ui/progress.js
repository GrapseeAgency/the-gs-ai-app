"use strict";
"use client";
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
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.Progress = Progress;
const jsx_runtime_1 = require("react/jsx-runtime");
const ProgressPrimitive = __importStar(require("@radix-ui/react-progress"));
const utils_1 = require("@/lib/utils");
function Progress({ className, value, ...props }) {
    return ((0, jsx_runtime_1.jsx)(ProgressPrimitive.Root, { "data-slot": "progress", className: (0, utils_1.cn)("bg-primary/20 relative h-2 w-full overflow-hidden rounded-full", className), ...props, children: (0, jsx_runtime_1.jsx)(ProgressPrimitive.Indicator, { "data-slot": "progress-indicator", className: "bg-primary h-full w-full flex-1 transition-all", style: { transform: `translateX(-${100 - (value || 0)}%)` } }) }));
}
