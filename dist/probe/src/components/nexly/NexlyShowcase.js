"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.NexlyShowcase = NexlyShowcase;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const GlassIcon_1 = require("./GlassIcon");
const NexlyBackdrop_1 = require("./NexlyBackdrop");
const NexlyPhone_1 = require("./NexlyPhone");
const icons_1 = require("@/lib/nexly/icons");
const WIRE = `\`\`\`plan
{
  "title": "Here's your personalized plan for tomorrow:",
  "rows": [
    { "time": "8:00 – 8:30 AM", "icon": "sunrise", "text": "Morning routine + light exercise" },
    { "time": "9:00 – 11:00 AM", "icon": "book-2", "text": "Classes" }
  ],
  "closing": "You got this, Arafat!",
  "closingIcon": "flame"
}
\`\`\``;
function NexlyShowcase() {
    const [query, setQuery] = (0, react_1.useState)('');
    const [limit, setLimit] = (0, react_1.useState)(96);
    const [copied, setCopied] = (0, react_1.useState)('');
    const filtered = (0, react_1.useMemo)(() => {
        const q = query.trim().toLowerCase();
        if (!q)
            return icons_1.NEXLY_ICON_NAMES;
        return icons_1.NEXLY_ICON_NAMES.filter((name) => name.includes(q));
    }, [query]);
    const shown = filtered.slice(0, limit);
    const copy = async (name) => {
        const token = `:${name}:`;
        try {
            await navigator.clipboard.writeText(token);
        }
        catch {
            // Clipboard can be blocked in a preview iframe; the label still teaches.
        }
        setCopied(name);
        window.setTimeout(() => setCopied(''), 1400);
    };
    return ((0, jsx_runtime_1.jsxs)("div", { className: "nx-page", children: [(0, jsx_runtime_1.jsx)(NexlyBackdrop_1.NexlyBackdrop, {}), (0, jsx_runtime_1.jsxs)("div", { className: "nx-hero", children: [(0, jsx_runtime_1.jsxs)("div", { className: "nx-phone-column", children: [(0, jsx_runtime_1.jsxs)("div", { className: "nx-phone-caption", children: [(0, jsx_runtime_1.jsx)("span", { className: "nx-live-dot" }), "Response renderer \u00B7 live preview"] }), (0, jsx_runtime_1.jsx)(NexlyPhone_1.NexlyPhone, {})] }), (0, jsx_runtime_1.jsxs)("aside", { className: "nx-panel", children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-eyebrow", children: "NEXLY \u00B7 RESPONSE SYSTEM" }), (0, jsx_runtime_1.jsxs)("h1", { className: "nx-title", children: ["The reply is", (0, jsx_runtime_1.jsx)("br", {}), "the interface."] }), (0, jsx_runtime_1.jsxs)("p", { className: "nx-lede", children: ["A validated ", (0, jsx_runtime_1.jsx)("code", { children: "plan" }), " block, a", ' ', (0, jsx_runtime_1.jsxs)("strong", { children: [icons_1.NEXLY_ICON_COUNT.toLocaleString('en-US'), "-glyph"] }), " glass icon vocabulary, and a Thinking card that survives the first token. No emoji reaches the DOM."] }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-stats", children: [(0, jsx_runtime_1.jsxs)("div", { className: "nx-stat", children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-stat-value", children: icons_1.NEXLY_ICON_COUNT.toLocaleString('en-US') }), (0, jsx_runtime_1.jsx)("div", { className: "nx-stat-label", children: "glass icons" })] }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-stat", children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-stat-value", children: "0" }), (0, jsx_runtime_1.jsx)("div", { className: "nx-stat-label", children: "emoji allowed" })] }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-stat", children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-stat-value", children: "9" }), (0, jsx_runtime_1.jsx)("div", { className: "nx-stat-label", children: "icon rows" })] })] }), (0, jsx_runtime_1.jsx)("div", { className: "nx-rail", children: icons_1.FEATURED_ICONS.slice(0, 12).map((icon) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", className: "nx-rail-item", onClick: () => copy(icon.name), title: `Copy :${icon.name}:`, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: icon.name, tone: icon.tone, tile: 42, size: 20, flat: true }), (0, jsx_runtime_1.jsx)("span", { children: icon.label })] }, icon.name))) }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-contract", children: [(0, jsx_runtime_1.jsxs)("div", { className: "nx-contract-head", children: [(0, jsx_runtime_1.jsx)("span", { children: "Wire contract" }), (0, jsx_runtime_1.jsx)("span", { className: "nx-contract-badge", children: "plan" })] }), (0, jsx_runtime_1.jsx)("pre", { children: WIRE }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-contract-foot", children: ["Inline ", (0, jsx_runtime_1.jsx)("code", { children: ":sunrise:" }), " \u00B7 emoji are normalised to a glass glyph, unknown pictographs are dropped."] })] }), (0, jsx_runtime_1.jsx)("p", { className: "nx-scope", children: "Scope: the assistant response slice. Header, user turn and composer are demo context; the iOS/Android chat shells are untouched." })] })] }), (0, jsx_runtime_1.jsxs)("section", { className: "nx-library", "aria-label": "Glass icon library", children: [(0, jsx_runtime_1.jsxs)("div", { className: "nx-library-head", children: [(0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)("div", { className: "nx-eyebrow", children: "THE VOCABULARY" }), (0, jsx_runtime_1.jsx)("h2", { className: "nx-library-title", children: "Every glyph is glass." })] }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-search-wrap", children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { bare: true, name: "search", size: 16 }), (0, jsx_runtime_1.jsx)("input", { className: "nx-search", value: query, onChange: (event) => {
                                            setQuery(event.target.value);
                                            setLimit(96);
                                        }, placeholder: `Search ${icons_1.NEXLY_ICON_COUNT.toLocaleString('en-US')} icons…`, "aria-label": "Search glass icons" })] })] }), (0, jsx_runtime_1.jsxs)("div", { className: "nx-library-meta", children: [(0, jsx_runtime_1.jsxs)("span", { children: ["Showing ", shown.length.toLocaleString('en-US'), " of", ' ', filtered.length.toLocaleString('en-US')] }), (0, jsx_runtime_1.jsx)("span", { className: "nx-copy-hint", children: copied ? `Copied :${copied}:` : 'Tap any glyph to copy its :name: token' })] }), (0, jsx_runtime_1.jsx)("div", { className: "nx-icon-grid", children: shown.map((name) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", className: "nx-icon-cell", onClick: () => copy(name), title: `Copy :${name}:`, children: [(0, jsx_runtime_1.jsx)(GlassIcon_1.GlassIcon, { name: name, tile: 44, size: 20, flat: true }), (0, jsx_runtime_1.jsx)("span", { className: "nx-icon-name", children: name })] }, name))) }), shown.length < filtered.length ? ((0, jsx_runtime_1.jsx)("div", { className: "nx-library-more", children: (0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => setLimit((value) => value + 96), children: "Show 96 more" }) })) : null, (0, jsx_runtime_1.jsx)("p", { className: "nx-attribution", children: "Glyphs: Tabler Icons (MIT). Glass tile system, icon grammar and plan renderer: Nexly." })] })] }));
}
