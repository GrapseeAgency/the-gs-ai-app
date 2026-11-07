"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Pagination = Pagination;
exports.PaginationContent = PaginationContent;
exports.PaginationLink = PaginationLink;
exports.PaginationItem = PaginationItem;
exports.PaginationPrevious = PaginationPrevious;
exports.PaginationNext = PaginationNext;
exports.PaginationEllipsis = PaginationEllipsis;
const jsx_runtime_1 = require("react/jsx-runtime");
const lucide_react_1 = require("lucide-react");
const utils_1 = require("@/lib/utils");
const button_1 = require("@/components/ui/button");
function Pagination({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("nav", { role: "navigation", "aria-label": "pagination", "data-slot": "pagination", className: (0, utils_1.cn)("mx-auto flex w-full justify-center", className), ...props }));
}
function PaginationContent({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("ul", { "data-slot": "pagination-content", className: (0, utils_1.cn)("flex flex-row items-center gap-1", className), ...props }));
}
function PaginationItem({ ...props }) {
    return (0, jsx_runtime_1.jsx)("li", { "data-slot": "pagination-item", ...props });
}
function PaginationLink({ className, isActive, size = "icon", ...props }) {
    return ((0, jsx_runtime_1.jsx)("a", { "aria-current": isActive ? "page" : undefined, "data-slot": "pagination-link", "data-active": isActive, className: (0, utils_1.cn)((0, button_1.buttonVariants)({
            variant: isActive ? "outline" : "ghost",
            size,
        }), className), ...props }));
}
function PaginationPrevious({ className, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(PaginationLink, { "aria-label": "Go to previous page", size: "default", className: (0, utils_1.cn)("gap-1 px-2.5 sm:pl-2.5", className), ...props, children: [(0, jsx_runtime_1.jsx)(lucide_react_1.ChevronLeftIcon, {}), (0, jsx_runtime_1.jsx)("span", { className: "hidden sm:block", children: "Previous" })] }));
}
function PaginationNext({ className, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(PaginationLink, { "aria-label": "Go to next page", size: "default", className: (0, utils_1.cn)("gap-1 px-2.5 sm:pr-2.5", className), ...props, children: [(0, jsx_runtime_1.jsx)("span", { className: "hidden sm:block", children: "Next" }), (0, jsx_runtime_1.jsx)(lucide_react_1.ChevronRightIcon, {})] }));
}
function PaginationEllipsis({ className, ...props }) {
    return ((0, jsx_runtime_1.jsxs)("span", { "aria-hidden": true, "data-slot": "pagination-ellipsis", className: (0, utils_1.cn)("flex size-9 items-center justify-center", className), ...props, children: [(0, jsx_runtime_1.jsx)(lucide_react_1.MoreHorizontalIcon, { className: "size-4" }), (0, jsx_runtime_1.jsx)("span", { className: "sr-only", children: "More pages" })] }));
}
