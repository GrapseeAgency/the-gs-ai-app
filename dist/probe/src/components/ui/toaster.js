"use strict";
"use client";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Toaster = Toaster;
const jsx_runtime_1 = require("react/jsx-runtime");
const use_toast_1 = require("@/hooks/use-toast");
const toast_1 = require("@/components/ui/toast");
function Toaster() {
    const { toasts } = (0, use_toast_1.useToast)();
    return ((0, jsx_runtime_1.jsxs)(toast_1.ToastProvider, { children: [toasts.map(function ({ id, title, description, action, ...props }) {
                return ((0, jsx_runtime_1.jsxs)(toast_1.Toast, { ...props, children: [(0, jsx_runtime_1.jsxs)("div", { className: "grid gap-1", children: [title && (0, jsx_runtime_1.jsx)(toast_1.ToastTitle, { children: title }), description && ((0, jsx_runtime_1.jsx)(toast_1.ToastDescription, { children: description }))] }), action, (0, jsx_runtime_1.jsx)(toast_1.ToastClose, {})] }, id));
            }), (0, jsx_runtime_1.jsx)(toast_1.ToastViewport, {})] }));
}
