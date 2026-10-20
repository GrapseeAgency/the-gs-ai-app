"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Alert = Alert;
exports.AlertTitle = AlertTitle;
exports.AlertDescription = AlertDescription;
const jsx_runtime_1 = require("react/jsx-runtime");
const class_variance_authority_1 = require("class-variance-authority");
const utils_1 = require("@/lib/utils");
const alertVariants = (0, class_variance_authority_1.cva)("relative w-full rounded-lg border px-4 py-3 text-sm grid has-[>svg]:grid-cols-[calc(var(--spacing)*4)_1fr] grid-cols-[0_1fr] has-[>svg]:gap-x-3 gap-y-0.5 items-start [&>svg]:size-4 [&>svg]:translate-y-0.5 [&>svg]:text-current", {
    variants: {
        variant: {
            default: "bg-card text-card-foreground",
            destructive: "text-destructive bg-card [&>svg]:text-current *:data-[slot=alert-description]:text-destructive/90",
        },
    },
    defaultVariants: {
        variant: "default",
    },
});
function Alert({ className, variant, ...props }) {
    return ((0, jsx_runtime_1.jsx)("div", { "data-slot": "alert", role: "alert", className: (0, utils_1.cn)(alertVariants({ variant }), className), ...props }));
}
function AlertTitle({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("div", { "data-slot": "alert-title", className: (0, utils_1.cn)("col-start-2 line-clamp-1 min-h-4 font-medium tracking-tight", className), ...props }));
}
function AlertDescription({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("div", { "data-slot": "alert-description", className: (0, utils_1.cn)("text-muted-foreground col-start-2 grid justify-items-start gap-1 text-sm [&_p]:leading-relaxed", className), ...props }));
}
