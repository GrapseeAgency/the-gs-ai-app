"use strict";
'use client';
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
exports.MermaidBlock = MermaidBlock;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * MermaidBlock — renders ```mermaid via the mermaid package (lazy chunk),
 * theme-aware; falls back to a pre block when the source fails to parse.
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function MermaidBlock({ source, height = 280 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [svg, setSvg] = (0, react_1.useState)('');
    const [failed, setFailed] = (0, react_1.useState)(false);
    const idRef = (0, react_1.useRef)(`gs-mermaid-${Math.random().toString(36).slice(2, 9)}`);
    (0, react_1.useEffect)(() => {
        let cancelled = false;
        const render = async () => {
            try {
                const mermaid = (await Promise.resolve().then(() => __importStar(require('mermaid')))).default;
                mermaid.initialize({
                    startOnLoad: false,
                    theme: theme.dark ? 'dark' : 'default',
                    themeVariables: {
                        primaryColor: `${accent}33`,
                        primaryBorderColor: accent,
                        primaryTextColor: theme.text,
                        lineColor: theme.text3,
                        fontFamily: 'inherit',
                    },
                });
                const { svg } = await mermaid.render(idRef.current, source);
                if (!cancelled)
                    setSvg(svg);
            }
            catch {
                if (!cancelled)
                    setFailed(true);
            }
        };
        render();
        return () => {
            cancelled = true;
        };
    }, [source, accent, theme.dark, theme.text, theme.text3]);
    if (failed) {
        return ((0, jsx_runtime_1.jsx)("pre", { style: {
                margin: 0,
                padding: 14,
                borderRadius: tokens_1.RADIUS.md,
                border: `1px solid ${theme.border}`,
                background: theme.dark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.03)',
                color: theme.text2,
                fontSize: tokens_1.TYPE.micro,
                overflowX: 'auto',
                whiteSpace: 'pre-wrap',
            }, children: source }));
    }
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', justifyContent: 'center', overflowX: 'auto', minHeight: 80 }, dangerouslySetInnerHTML: svg ? { __html: svg } : undefined }));
}
