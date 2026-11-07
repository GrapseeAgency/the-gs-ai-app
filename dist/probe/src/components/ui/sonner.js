"use strict";
"use client";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Toaster = void 0;
const jsx_runtime_1 = require("react/jsx-runtime");
const next_themes_1 = require("next-themes");
const sonner_1 = require("sonner");
const Toaster = ({ ...props }) => {
    const { theme = "system" } = (0, next_themes_1.useTheme)();
    return ((0, jsx_runtime_1.jsx)(sonner_1.Toaster, { theme: theme, className: "toaster group", style: {
            "--normal-bg": "var(--popover)",
            "--normal-text": "var(--popover-foreground)",
            "--normal-border": "var(--border)",
        }, ...props }));
};
exports.Toaster = Toaster;
