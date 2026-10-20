"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.metadata = void 0;
exports.default = RootLayout;
const jsx_runtime_1 = require("react/jsx-runtime");
const google_1 = require("next/font/google");
require("./globals.css");
const toaster_1 = require("@/components/ui/toaster");
const geistSans = (0, google_1.Geist)({
    variable: "--font-geist-sans",
    subsets: ["latin"],
});
const geistMono = (0, google_1.Geist_Mono)({
    variable: "--font-geist-mono",
    subsets: ["latin"],
});
exports.metadata = {
    title: "GS AI — Grapsee Agency",
    description: "GS AI platform command centre — native Android & iOS clients, one streaming API contract, built by Grapsee Agency.",
    keywords: ["GS AI", "Grapsee Agency", "AI assistant", "Android", "iOS", "chat"],
    authors: [{ name: "Grapsee Agency" }],
    icons: {
        icon: "/logo.svg",
    },
    openGraph: {
        title: "GS AI",
        description: "AI-powered development with modern React stack",
        url: "/",
        siteName: "Grapsee Agency",
        type: "website",
    },
    twitter: {
        card: "summary_large_image",
        title: "GS AI",
        description: "AI-powered development with modern React stack",
    },
};
function RootLayout({ children, }) {
    return ((0, jsx_runtime_1.jsx)("html", { lang: "en", suppressHydrationWarning: true, children: (0, jsx_runtime_1.jsxs)("body", { className: `${geistSans.variable} ${geistMono.variable} antialiased bg-background text-foreground`, children: [children, (0, jsx_runtime_1.jsx)(toaster_1.Toaster, {})] }) }));
}
