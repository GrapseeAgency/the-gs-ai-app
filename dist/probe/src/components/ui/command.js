"use strict";
"use client";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Command = Command;
exports.CommandDialog = CommandDialog;
exports.CommandInput = CommandInput;
exports.CommandList = CommandList;
exports.CommandEmpty = CommandEmpty;
exports.CommandGroup = CommandGroup;
exports.CommandItem = CommandItem;
exports.CommandShortcut = CommandShortcut;
exports.CommandSeparator = CommandSeparator;
const jsx_runtime_1 = require("react/jsx-runtime");
const cmdk_1 = require("cmdk");
const lucide_react_1 = require("lucide-react");
const utils_1 = require("@/lib/utils");
const dialog_1 = require("@/components/ui/dialog");
function Command({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(cmdk_1.Command, { "data-slot": "command", className: (0, utils_1.cn)("bg-popover text-popover-foreground flex h-full w-full flex-col overflow-hidden rounded-md", className), ...props }));
}
function CommandDialog({ title = "Command Palette", description = "Search for a command to run...", children, className, showCloseButton = true, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(dialog_1.Dialog, { ...props, children: [(0, jsx_runtime_1.jsxs)(dialog_1.DialogHeader, { className: "sr-only", children: [(0, jsx_runtime_1.jsx)(dialog_1.DialogTitle, { children: title }), (0, jsx_runtime_1.jsx)(dialog_1.DialogDescription, { children: description })] }), (0, jsx_runtime_1.jsx)(dialog_1.DialogContent, { className: (0, utils_1.cn)("overflow-hidden p-0", className), showCloseButton: showCloseButton, children: (0, jsx_runtime_1.jsx)(Command, { className: "[&_[cmdk-group-heading]]:text-muted-foreground **:data-[slot=command-input-wrapper]:h-12 [&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:font-medium [&_[cmdk-group]]:px-2 [&_[cmdk-group]:not([hidden])_~[cmdk-group]]:pt-0 [&_[cmdk-input-wrapper]_svg]:h-5 [&_[cmdk-input-wrapper]_svg]:w-5 [&_[cmdk-input]]:h-12 [&_[cmdk-item]]:px-2 [&_[cmdk-item]]:py-3 [&_[cmdk-item]_svg]:h-5 [&_[cmdk-item]_svg]:w-5", children: children }) })] }));
}
function CommandInput({ className, ...props }) {
    return ((0, jsx_runtime_1.jsxs)("div", { "data-slot": "command-input-wrapper", className: "flex h-9 items-center gap-2 border-b px-3", children: [(0, jsx_runtime_1.jsx)(lucide_react_1.SearchIcon, { className: "size-4 shrink-0 opacity-50" }), (0, jsx_runtime_1.jsx)(cmdk_1.Command.Input, { "data-slot": "command-input", className: (0, utils_1.cn)("placeholder:text-muted-foreground flex h-10 w-full rounded-md bg-transparent py-3 text-sm outline-hidden disabled:cursor-not-allowed disabled:opacity-50", className), ...props })] }));
}
function CommandList({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(cmdk_1.Command.List, { "data-slot": "command-list", className: (0, utils_1.cn)("max-h-[300px] scroll-py-1 overflow-x-hidden overflow-y-auto", className), ...props }));
}
function CommandEmpty({ ...props }) {
    return ((0, jsx_runtime_1.jsx)(cmdk_1.Command.Empty, { "data-slot": "command-empty", className: "py-6 text-center text-sm", ...props }));
}
function CommandGroup({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(cmdk_1.Command.Group, { "data-slot": "command-group", className: (0, utils_1.cn)("text-foreground [&_[cmdk-group-heading]]:text-muted-foreground overflow-hidden p-1 [&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-medium", className), ...props }));
}
function CommandSeparator({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(cmdk_1.Command.Separator, { "data-slot": "command-separator", className: (0, utils_1.cn)("bg-border -mx-1 h-px", className), ...props }));
}
function CommandItem({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(cmdk_1.Command.Item, { "data-slot": "command-item", className: (0, utils_1.cn)("data-[selected=true]:bg-accent data-[selected=true]:text-accent-foreground [&_svg:not([class*='text-'])]:text-muted-foreground relative flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-hidden select-none data-[disabled=true]:pointer-events-none data-[disabled=true]:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4", className), ...props }));
}
function CommandShortcut({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("span", { "data-slot": "command-shortcut", className: (0, utils_1.cn)("text-muted-foreground ml-auto text-xs tracking-widest", className), ...props }));
}
