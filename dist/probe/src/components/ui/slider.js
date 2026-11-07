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
exports.Slider = Slider;
const jsx_runtime_1 = require("react/jsx-runtime");
const React = __importStar(require("react"));
const SliderPrimitive = __importStar(require("@radix-ui/react-slider"));
const utils_1 = require("@/lib/utils");
function Slider({ className, defaultValue, value, min = 0, max = 100, ...props }) {
    const _values = React.useMemo(() => Array.isArray(value)
        ? value
        : Array.isArray(defaultValue)
            ? defaultValue
            : [min, max], [value, defaultValue, min, max]);
    return ((0, jsx_runtime_1.jsxs)(SliderPrimitive.Root, { "data-slot": "slider", defaultValue: defaultValue, value: value, min: min, max: max, className: (0, utils_1.cn)("relative flex w-full touch-none items-center select-none data-[disabled]:opacity-50 data-[orientation=vertical]:h-full data-[orientation=vertical]:min-h-44 data-[orientation=vertical]:w-auto data-[orientation=vertical]:flex-col", className), ...props, children: [(0, jsx_runtime_1.jsx)(SliderPrimitive.Track, { "data-slot": "slider-track", className: (0, utils_1.cn)("bg-muted relative grow overflow-hidden rounded-full data-[orientation=horizontal]:h-1.5 data-[orientation=horizontal]:w-full data-[orientation=vertical]:h-full data-[orientation=vertical]:w-1.5"), children: (0, jsx_runtime_1.jsx)(SliderPrimitive.Range, { "data-slot": "slider-range", className: (0, utils_1.cn)("bg-primary absolute data-[orientation=horizontal]:h-full data-[orientation=vertical]:w-full") }) }), Array.from({ length: _values.length }, (_, index) => ((0, jsx_runtime_1.jsx)(SliderPrimitive.Thumb, { "data-slot": "slider-thumb", className: "border-primary bg-background ring-ring/50 block size-4 shrink-0 rounded-full border shadow-sm transition-[color,box-shadow] hover:ring-4 focus-visible:ring-4 focus-visible:outline-hidden disabled:pointer-events-none disabled:opacity-50" }, index)))] }));
}
