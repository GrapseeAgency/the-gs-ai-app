"use strict";
"use client";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Table = Table;
exports.TableHeader = TableHeader;
exports.TableBody = TableBody;
exports.TableFooter = TableFooter;
exports.TableHead = TableHead;
exports.TableRow = TableRow;
exports.TableCell = TableCell;
exports.TableCaption = TableCaption;
const jsx_runtime_1 = require("react/jsx-runtime");
const utils_1 = require("@/lib/utils");
function Table({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("div", { "data-slot": "table-container", className: "relative w-full overflow-x-auto", children: (0, jsx_runtime_1.jsx)("table", { "data-slot": "table", className: (0, utils_1.cn)("w-full caption-bottom text-sm", className), ...props }) }));
}
function TableHeader({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("thead", { "data-slot": "table-header", className: (0, utils_1.cn)("[&_tr]:border-b", className), ...props }));
}
function TableBody({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("tbody", { "data-slot": "table-body", className: (0, utils_1.cn)("[&_tr:last-child]:border-0", className), ...props }));
}
function TableFooter({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("tfoot", { "data-slot": "table-footer", className: (0, utils_1.cn)("bg-muted/50 border-t font-medium [&>tr]:last:border-b-0", className), ...props }));
}
function TableRow({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("tr", { "data-slot": "table-row", className: (0, utils_1.cn)("hover:bg-muted/50 data-[state=selected]:bg-muted border-b transition-colors", className), ...props }));
}
function TableHead({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("th", { "data-slot": "table-head", className: (0, utils_1.cn)("text-foreground h-10 px-2 text-left align-middle font-medium whitespace-nowrap [&:has([role=checkbox])]:pr-0 [&>[role=checkbox]]:translate-y-[2px]", className), ...props }));
}
function TableCell({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("td", { "data-slot": "table-cell", className: (0, utils_1.cn)("p-2 align-middle whitespace-nowrap [&:has([role=checkbox])]:pr-0 [&>[role=checkbox]]:translate-y-[2px]", className), ...props }));
}
function TableCaption({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("caption", { "data-slot": "table-caption", className: (0, utils_1.cn)("text-muted-foreground mt-4 text-sm", className), ...props }));
}
