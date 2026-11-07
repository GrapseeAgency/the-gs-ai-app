"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.WidgetRenderer = WidgetRenderer;
const jsx_runtime_1 = require("react/jsx-runtime");
const registry_1 = require("./registry");
const WidgetSurface_1 = require("./theme/WidgetSurface");
const CodeBlock_1 = require("./blocks/CodeBlock");
function WidgetRenderer({ kind, payload, dark = true, onPrompt, }) {
    const Renderer = (0, registry_1.rendererFor)(kind);
    const accent = typeof payload.accent === 'string' ? payload.accent : undefined;
    const variant = typeof payload.variant === 'string' ? payload.variant : undefined;
    const palette = typeof payload.palette === 'string' ? payload.palette : undefined;
    if (!Renderer) {
        return ((0, jsx_runtime_1.jsx)(WidgetSurface_1.WidgetSurface, { accent: accent, variant: variant, palette: palette, dark: dark, children: (0, jsx_runtime_1.jsx)(CodeBlock_1.CodeBlock, { language: kind, code: JSON.stringify(payload, null, 2) }) }));
    }
    return ((0, jsx_runtime_1.jsx)(WidgetSurface_1.WidgetSurface, { accent: accent, variant: variant, palette: palette, dark: dark, children: (0, jsx_runtime_1.jsx)(Renderer, { payload: payload, onPrompt: onPrompt }) }));
}
