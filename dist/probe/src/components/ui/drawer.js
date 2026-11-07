"use strict";
"use client";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Drawer = Drawer;
exports.DrawerPortal = DrawerPortal;
exports.DrawerOverlay = DrawerOverlay;
exports.DrawerTrigger = DrawerTrigger;
exports.DrawerClose = DrawerClose;
exports.DrawerContent = DrawerContent;
exports.DrawerHeader = DrawerHeader;
exports.DrawerFooter = DrawerFooter;
exports.DrawerTitle = DrawerTitle;
exports.DrawerDescription = DrawerDescription;
const jsx_runtime_1 = require("react/jsx-runtime");
const vaul_1 = require("vaul");
const utils_1 = require("@/lib/utils");
function Drawer({ ...props }) {
    return (0, jsx_runtime_1.jsx)(vaul_1.Drawer.Root, { "data-slot": "drawer", ...props });
}
function DrawerTrigger({ ...props }) {
    return (0, jsx_runtime_1.jsx)(vaul_1.Drawer.Trigger, { "data-slot": "drawer-trigger", ...props });
}
function DrawerPortal({ ...props }) {
    return (0, jsx_runtime_1.jsx)(vaul_1.Drawer.Portal, { "data-slot": "drawer-portal", ...props });
}
function DrawerClose({ ...props }) {
    return (0, jsx_runtime_1.jsx)(vaul_1.Drawer.Close, { "data-slot": "drawer-close", ...props });
}
function DrawerOverlay({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(vaul_1.Drawer.Overlay, { "data-slot": "drawer-overlay", className: (0, utils_1.cn)("data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 fixed inset-0 z-50 bg-black/50", className), ...props }));
}
function DrawerContent({ className, children, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(DrawerPortal, { "data-slot": "drawer-portal", children: [(0, jsx_runtime_1.jsx)(DrawerOverlay, {}), (0, jsx_runtime_1.jsxs)(vaul_1.Drawer.Content, { "data-slot": "drawer-content", className: (0, utils_1.cn)("group/drawer-content bg-background fixed z-50 flex h-auto flex-col", "data-[vaul-drawer-direction=top]:inset-x-0 data-[vaul-drawer-direction=top]:top-0 data-[vaul-drawer-direction=top]:mb-24 data-[vaul-drawer-direction=top]:max-h-[80vh] data-[vaul-drawer-direction=top]:rounded-b-lg data-[vaul-drawer-direction=top]:border-b", "data-[vaul-drawer-direction=bottom]:inset-x-0 data-[vaul-drawer-direction=bottom]:bottom-0 data-[vaul-drawer-direction=bottom]:mt-24 data-[vaul-drawer-direction=bottom]:max-h-[80vh] data-[vaul-drawer-direction=bottom]:rounded-t-lg data-[vaul-drawer-direction=bottom]:border-t", "data-[vaul-drawer-direction=right]:inset-y-0 data-[vaul-drawer-direction=right]:right-0 data-[vaul-drawer-direction=right]:w-3/4 data-[vaul-drawer-direction=right]:border-l data-[vaul-drawer-direction=right]:sm:max-w-sm", "data-[vaul-drawer-direction=left]:inset-y-0 data-[vaul-drawer-direction=left]:left-0 data-[vaul-drawer-direction=left]:w-3/4 data-[vaul-drawer-direction=left]:border-r data-[vaul-drawer-direction=left]:sm:max-w-sm", className), ...props, children: [(0, jsx_runtime_1.jsx)("div", { className: "bg-muted mx-auto mt-4 hidden h-2 w-[100px] shrink-0 rounded-full group-data-[vaul-drawer-direction=bottom]/drawer-content:block" }), children] })] }));
}
function DrawerHeader({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("div", { "data-slot": "drawer-header", className: (0, utils_1.cn)("flex flex-col gap-0.5 p-4 group-data-[vaul-drawer-direction=bottom]/drawer-content:text-center group-data-[vaul-drawer-direction=top]/drawer-content:text-center md:gap-1.5 md:text-left", className), ...props }));
}
function DrawerFooter({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("div", { "data-slot": "drawer-footer", className: (0, utils_1.cn)("mt-auto flex flex-col gap-2 p-4", className), ...props }));
}
function DrawerTitle({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(vaul_1.Drawer.Title, { "data-slot": "drawer-title", className: (0, utils_1.cn)("text-foreground font-semibold", className), ...props }));
}
function DrawerDescription({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(vaul_1.Drawer.Description, { "data-slot": "drawer-description", className: (0, utils_1.cn)("text-muted-foreground text-sm", className), ...props }));
}
