"use strict";
"use client";
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
exports.ContextMenu = ContextMenu;
exports.ContextMenuTrigger = ContextMenuTrigger;
exports.ContextMenuContent = ContextMenuContent;
exports.ContextMenuItem = ContextMenuItem;
exports.ContextMenuCheckboxItem = ContextMenuCheckboxItem;
exports.ContextMenuRadioItem = ContextMenuRadioItem;
exports.ContextMenuLabel = ContextMenuLabel;
exports.ContextMenuSeparator = ContextMenuSeparator;
exports.ContextMenuShortcut = ContextMenuShortcut;
exports.ContextMenuGroup = ContextMenuGroup;
exports.ContextMenuPortal = ContextMenuPortal;
exports.ContextMenuSub = ContextMenuSub;
exports.ContextMenuSubContent = ContextMenuSubContent;
exports.ContextMenuSubTrigger = ContextMenuSubTrigger;
exports.ContextMenuRadioGroup = ContextMenuRadioGroup;
const jsx_runtime_1 = require("react/jsx-runtime");
const ContextMenuPrimitive = __importStar(require("@radix-ui/react-context-menu"));
const lucide_react_1 = require("lucide-react");
const utils_1 = require("@/lib/utils");
function ContextMenu({ ...props }) {
    return (0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Root, { "data-slot": "context-menu", ...props });
}
function ContextMenuTrigger({ ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Trigger, { "data-slot": "context-menu-trigger", ...props }));
}
function ContextMenuGroup({ ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Group, { "data-slot": "context-menu-group", ...props }));
}
function ContextMenuPortal({ ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Portal, { "data-slot": "context-menu-portal", ...props }));
}
function ContextMenuSub({ ...props }) {
    return (0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Sub, { "data-slot": "context-menu-sub", ...props });
}
function ContextMenuRadioGroup({ ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.RadioGroup, { "data-slot": "context-menu-radio-group", ...props }));
}
function ContextMenuSubTrigger({ className, inset, children, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(ContextMenuPrimitive.SubTrigger, { "data-slot": "context-menu-sub-trigger", "data-inset": inset, className: (0, utils_1.cn)("focus:bg-accent focus:text-accent-foreground data-[state=open]:bg-accent data-[state=open]:text-accent-foreground flex cursor-default items-center rounded-sm px-2 py-1.5 text-sm outline-hidden select-none data-[inset]:pl-8 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4", className), ...props, children: [children, (0, jsx_runtime_1.jsx)(lucide_react_1.ChevronRightIcon, { className: "ml-auto" })] }));
}
function ContextMenuSubContent({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.SubContent, { "data-slot": "context-menu-sub-content", className: (0, utils_1.cn)("bg-popover text-popover-foreground data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 z-50 min-w-[8rem] origin-(--radix-context-menu-content-transform-origin) overflow-hidden rounded-md border p-1 shadow-lg", className), ...props }));
}
function ContextMenuContent({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Portal, { children: (0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Content, { "data-slot": "context-menu-content", className: (0, utils_1.cn)("bg-popover text-popover-foreground data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 z-50 max-h-(--radix-context-menu-content-available-height) min-w-[8rem] origin-(--radix-context-menu-content-transform-origin) overflow-x-hidden overflow-y-auto rounded-md border p-1 shadow-md", className), ...props }) }));
}
function ContextMenuItem({ className, inset, variant = "default", ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Item, { "data-slot": "context-menu-item", "data-inset": inset, "data-variant": variant, className: (0, utils_1.cn)("focus:bg-accent focus:text-accent-foreground data-[variant=destructive]:text-destructive data-[variant=destructive]:focus:bg-destructive/10 dark:data-[variant=destructive]:focus:bg-destructive/20 data-[variant=destructive]:focus:text-destructive data-[variant=destructive]:*:[svg]:!text-destructive [&_svg:not([class*='text-'])]:text-muted-foreground relative flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[inset]:pl-8 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4", className), ...props }));
}
function ContextMenuCheckboxItem({ className, children, checked, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(ContextMenuPrimitive.CheckboxItem, { "data-slot": "context-menu-checkbox-item", className: (0, utils_1.cn)("focus:bg-accent focus:text-accent-foreground relative flex cursor-default items-center gap-2 rounded-sm py-1.5 pr-2 pl-8 text-sm outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4", className), checked: checked, ...props, children: [(0, jsx_runtime_1.jsx)("span", { className: "pointer-events-none absolute left-2 flex size-3.5 items-center justify-center", children: (0, jsx_runtime_1.jsx)(ContextMenuPrimitive.ItemIndicator, { children: (0, jsx_runtime_1.jsx)(lucide_react_1.CheckIcon, { className: "size-4" }) }) }), children] }));
}
function ContextMenuRadioItem({ className, children, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(ContextMenuPrimitive.RadioItem, { "data-slot": "context-menu-radio-item", className: (0, utils_1.cn)("focus:bg-accent focus:text-accent-foreground relative flex cursor-default items-center gap-2 rounded-sm py-1.5 pr-2 pl-8 text-sm outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4", className), ...props, children: [(0, jsx_runtime_1.jsx)("span", { className: "pointer-events-none absolute left-2 flex size-3.5 items-center justify-center", children: (0, jsx_runtime_1.jsx)(ContextMenuPrimitive.ItemIndicator, { children: (0, jsx_runtime_1.jsx)(lucide_react_1.CircleIcon, { className: "size-2 fill-current" }) }) }), children] }));
}
function ContextMenuLabel({ className, inset, ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Label, { "data-slot": "context-menu-label", "data-inset": inset, className: (0, utils_1.cn)("text-foreground px-2 py-1.5 text-sm font-medium data-[inset]:pl-8", className), ...props }));
}
function ContextMenuSeparator({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(ContextMenuPrimitive.Separator, { "data-slot": "context-menu-separator", className: (0, utils_1.cn)("bg-border -mx-1 my-1 h-px", className), ...props }));
}
function ContextMenuShortcut({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("span", { "data-slot": "context-menu-shortcut", className: (0, utils_1.cn)("text-muted-foreground ml-auto text-xs tracking-widest", className), ...props }));
}
