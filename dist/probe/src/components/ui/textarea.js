"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Textarea = Textarea;
const jsx_runtime_1 = require("react/jsx-runtime");
const utils_1 = require("@/lib/utils");
function Textarea({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("textarea", { "data-slot": "textarea", className: (0, utils_1.cn)("border-input placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:bg-input/30 flex field-sizing-content min-h-16 w-full rounded-md border bg-transparent px-3 py-2 text-base shadow-xs transition-[color,box-shadow] outline-none focus-visible:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50 md:text-sm", className), ...props }));
}
