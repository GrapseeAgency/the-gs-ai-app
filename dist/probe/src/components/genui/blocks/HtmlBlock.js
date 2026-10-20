"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.HtmlBlock = HtmlBlock;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * HtmlBlock — the bespoke escape hatch. One self-contained HTML document in a
 * sandboxed iframe: allow-scripts, NO allow-same-origin (no cookies, no DOM
 * access to the parent), explicit height via <body data-gs-height="Npx">.
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const Skeleton_1 = require("../primitives/Skeleton");
function HtmlBlock({ source, fallbackHeight = 320 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [loaded, setLoaded] = (0, react_1.useState)(false);
    const [height, setHeight] = (0, react_1.useState)(fallbackHeight);
    const frame = (0, react_1.useRef)(null);
    const themed = (0, react_1.useMemo)(() => {
        const hueVars = Array.from({ length: 12 }, (_, i) => `--gs-hue-${i + 1}`);
        const doc = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
      :root {
        --gs-text:${theme.text}; --gs-text-2:${theme.text2}; --gs-text-3:${theme.text3};
        --gs-surface:${theme.card}; --gs-raised:${theme.raised}; --gs-border:${theme.border};
        --gs-accent:${accent};
        --gs-palette:var(--gs-accent);
        --gs-radius:14px; --gs-space:8px;
        color-scheme:${theme.dark ? 'dark' : 'light'};
      }
      html,body { margin:0; padding:12px; background:transparent; color:var(--gs-text);
        font-family:-apple-system,'SF Pro Text','Segoe UI',Roboto,sans-serif; font-size:14px; }
      * { box-sizing:border-box; max-width:100%; }
    </style></head><body>${source}</body></html>`;
        return { doc, hueVars };
    }, [source, accent, theme]);
    (0, react_1.useEffect)(() => {
        const m = /data-gs-height="(\d+)px"/.exec(source);
        if (m)
            setHeight(Math.min(900, Math.max(80, Number(m[1]))));
    }, [source]);
    return ((0, jsx_runtime_1.jsxs)("div", { style: { position: 'relative' }, children: [!loaded && (0, jsx_runtime_1.jsx)(Skeleton_1.Skeleton, { label: "Rendering\u2026", height: Math.min(height, 200) }), (0, jsx_runtime_1.jsx)("iframe", { ref: frame, title: "interactive view", sandbox: "allow-scripts allow-forms allow-pointer-lock", srcDoc: themed.doc, onLoad: () => setLoaded(true), style: {
                    width: '100%',
                    height,
                    border: `1px solid ${theme.border}`,
                    borderRadius: tokens_1.RADIUS.lg,
                    background: theme.card,
                    display: loaded ? 'block' : 'none',
                } })] }));
}
