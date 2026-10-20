"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.SvgBlock = SvgBlock;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * SvgBlock — one inline <svg>, themed: the source's currentColor and the
 * --gs-* variables resolve inside the bubble.
 */
const react_1 = require("react");
const WidgetSurface_1 = require("../theme/WidgetSurface");
function SvgBlock({ source }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const themed = (0, react_1.useMemo)(() => {
        const vars = `:root{--gs-text:${theme.text};--gs-text-2:${theme.text2};--gs-text-3:${theme.text3};--gs-accent:${accent};--gs-surface:${theme.card};--gs-border:${theme.border};}`;
        if (/<style[^>]*>/i.test(source))
            return source.replace(/<style([^>]*)>/i, `<style$1>${vars}`);
        return source.replace(/<svg([^>]*)>/i, `<svg$1><style>${vars}</style>`);
    }, [source, accent, theme]);
    return ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', justifyContent: 'center', lineHeight: 0 }, dangerouslySetInnerHTML: { __html: themed } }));
}
