"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Breadcrumb = Breadcrumb;
exports.BreadcrumbList = BreadcrumbList;
exports.BreadcrumbItem = BreadcrumbItem;
exports.BreadcrumbLink = BreadcrumbLink;
exports.BreadcrumbPage = BreadcrumbPage;
exports.BreadcrumbSeparator = BreadcrumbSeparator;
exports.BreadcrumbEllipsis = BreadcrumbEllipsis;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_slot_1 = require("@radix-ui/react-slot");
const lucide_react_1 = require("lucide-react");
const utils_1 = require("@/lib/utils");
function Breadcrumb({ ...props }) {
    return (0, jsx_runtime_1.jsx)("nav", { "aria-label": "breadcrumb", "data-slot": "breadcrumb", ...props });
}
function BreadcrumbList({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("ol", { "data-slot": "breadcrumb-list", className: (0, utils_1.cn)("text-muted-foreground flex flex-wrap items-center gap-1.5 text-sm break-words sm:gap-2.5", className), ...props }));
}
function BreadcrumbItem({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("li", { "data-slot": "breadcrumb-item", className: (0, utils_1.cn)("inline-flex items-center gap-1.5", className), ...props }));
}
function BreadcrumbLink({ asChild, className, ...props }) {
    const Comp = asChild ? react_slot_1.Slot : "a";
    return ((0, jsx_runtime_1.jsx)(Comp, { "data-slot": "breadcrumb-link", className: (0, utils_1.cn)("hover:text-foreground transition-colors", className), ...props }));
}
function BreadcrumbPage({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("span", { "data-slot": "breadcrumb-page", role: "link", "aria-disabled": "true", "aria-current": "page", className: (0, utils_1.cn)("text-foreground font-normal", className), ...props }));
}
function BreadcrumbSeparator({ children, className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("li", { "data-slot": "breadcrumb-separator", role: "presentation", "aria-hidden": "true", className: (0, utils_1.cn)("[&>svg]:size-3.5", className), ...props, children: children ?? (0, jsx_runtime_1.jsx)(lucide_react_1.ChevronRight, {}) }));
}
function BreadcrumbEllipsis({ className, ...props }) {
    return ((0, jsx_runtime_1.jsxs)("span", { "data-slot": "breadcrumb-ellipsis", role: "presentation", "aria-hidden": "true", className: (0, utils_1.cn)("flex size-9 items-center justify-center", className), ...props, children: [(0, jsx_runtime_1.jsx)(lucide_react_1.MoreHorizontal, { className: "size-4" }), (0, jsx_runtime_1.jsx)("span", { className: "sr-only", children: "More" })] }));
}
