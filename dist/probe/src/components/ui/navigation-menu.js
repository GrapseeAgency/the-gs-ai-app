"use strict";
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
exports.navigationMenuTriggerStyle = void 0;
exports.NavigationMenu = NavigationMenu;
exports.NavigationMenuList = NavigationMenuList;
exports.NavigationMenuItem = NavigationMenuItem;
exports.NavigationMenuContent = NavigationMenuContent;
exports.NavigationMenuTrigger = NavigationMenuTrigger;
exports.NavigationMenuLink = NavigationMenuLink;
exports.NavigationMenuIndicator = NavigationMenuIndicator;
exports.NavigationMenuViewport = NavigationMenuViewport;
const jsx_runtime_1 = require("react/jsx-runtime");
const NavigationMenuPrimitive = __importStar(require("@radix-ui/react-navigation-menu"));
const class_variance_authority_1 = require("class-variance-authority");
const lucide_react_1 = require("lucide-react");
const utils_1 = require("@/lib/utils");
function NavigationMenu({ className, children, viewport = true, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(NavigationMenuPrimitive.Root, { "data-slot": "navigation-menu", "data-viewport": viewport, className: (0, utils_1.cn)("group/navigation-menu relative flex max-w-max flex-1 items-center justify-center", className), ...props, children: [children, viewport && (0, jsx_runtime_1.jsx)(NavigationMenuViewport, {})] }));
}
function NavigationMenuList({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(NavigationMenuPrimitive.List, { "data-slot": "navigation-menu-list", className: (0, utils_1.cn)("group flex flex-1 list-none items-center justify-center gap-1", className), ...props }));
}
function NavigationMenuItem({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(NavigationMenuPrimitive.Item, { "data-slot": "navigation-menu-item", className: (0, utils_1.cn)("relative", className), ...props }));
}
const navigationMenuTriggerStyle = (0, class_variance_authority_1.cva)("group inline-flex h-9 w-max items-center justify-center rounded-md bg-background px-4 py-2 text-sm font-medium hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground disabled:pointer-events-none disabled:opacity-50 data-[state=open]:hover:bg-accent data-[state=open]:text-accent-foreground data-[state=open]:focus:bg-accent data-[state=open]:bg-accent/50 focus-visible:ring-ring/50 outline-none transition-[color,box-shadow] focus-visible:ring-[3px] focus-visible:outline-1");
exports.navigationMenuTriggerStyle = navigationMenuTriggerStyle;
function NavigationMenuTrigger({ className, children, ...props }) {
    return ((0, jsx_runtime_1.jsxs)(NavigationMenuPrimitive.Trigger, { "data-slot": "navigation-menu-trigger", className: (0, utils_1.cn)(navigationMenuTriggerStyle(), "group", className), ...props, children: [children, " ", (0, jsx_runtime_1.jsx)(lucide_react_1.ChevronDownIcon, { className: "relative top-[1px] ml-1 size-3 transition duration-300 group-data-[state=open]:rotate-180", "aria-hidden": "true" })] }));
}
function NavigationMenuContent({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(NavigationMenuPrimitive.Content, { "data-slot": "navigation-menu-content", className: (0, utils_1.cn)("data-[motion^=from-]:animate-in data-[motion^=to-]:animate-out data-[motion^=from-]:fade-in data-[motion^=to-]:fade-out data-[motion=from-end]:slide-in-from-right-52 data-[motion=from-start]:slide-in-from-left-52 data-[motion=to-end]:slide-out-to-right-52 data-[motion=to-start]:slide-out-to-left-52 top-0 left-0 w-full p-2 pr-2.5 md:absolute md:w-auto", "group-data-[viewport=false]/navigation-menu:bg-popover group-data-[viewport=false]/navigation-menu:text-popover-foreground group-data-[viewport=false]/navigation-menu:data-[state=open]:animate-in group-data-[viewport=false]/navigation-menu:data-[state=closed]:animate-out group-data-[viewport=false]/navigation-menu:data-[state=closed]:zoom-out-95 group-data-[viewport=false]/navigation-menu:data-[state=open]:zoom-in-95 group-data-[viewport=false]/navigation-menu:data-[state=open]:fade-in-0 group-data-[viewport=false]/navigation-menu:data-[state=closed]:fade-out-0 group-data-[viewport=false]/navigation-menu:top-full group-data-[viewport=false]/navigation-menu:mt-1.5 group-data-[viewport=false]/navigation-menu:overflow-hidden group-data-[viewport=false]/navigation-menu:rounded-md group-data-[viewport=false]/navigation-menu:border group-data-[viewport=false]/navigation-menu:shadow group-data-[viewport=false]/navigation-menu:duration-200 **:data-[slot=navigation-menu-link]:focus:ring-0 **:data-[slot=navigation-menu-link]:focus:outline-none", className), ...props }));
}
function NavigationMenuViewport({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)("div", { className: (0, utils_1.cn)("absolute top-full left-0 isolate z-50 flex justify-center"), children: (0, jsx_runtime_1.jsx)(NavigationMenuPrimitive.Viewport, { "data-slot": "navigation-menu-viewport", className: (0, utils_1.cn)("origin-top-center bg-popover text-popover-foreground data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-90 relative mt-1.5 h-[var(--radix-navigation-menu-viewport-height)] w-full overflow-hidden rounded-md border shadow md:w-[var(--radix-navigation-menu-viewport-width)]", className), ...props }) }));
}
function NavigationMenuLink({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(NavigationMenuPrimitive.Link, { "data-slot": "navigation-menu-link", className: (0, utils_1.cn)("data-[active=true]:focus:bg-accent data-[active=true]:hover:bg-accent data-[active=true]:bg-accent/50 data-[active=true]:text-accent-foreground hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground focus-visible:ring-ring/50 [&_svg:not([class*='text-'])]:text-muted-foreground flex flex-col gap-1 rounded-sm p-2 text-sm transition-all outline-none focus-visible:ring-[3px] focus-visible:outline-1 [&_svg:not([class*='size-'])]:size-4", className), ...props }));
}
function NavigationMenuIndicator({ className, ...props }) {
    return ((0, jsx_runtime_1.jsx)(NavigationMenuPrimitive.Indicator, { "data-slot": "navigation-menu-indicator", className: (0, utils_1.cn)("data-[state=visible]:animate-in data-[state=hidden]:animate-out data-[state=hidden]:fade-out data-[state=visible]:fade-in top-full z-[1] flex h-1.5 items-end justify-center overflow-hidden", className), ...props, children: (0, jsx_runtime_1.jsx)("div", { className: "bg-border relative top-[60%] h-2 w-2 rotate-45 rounded-tl-sm shadow-md" }) }));
}
