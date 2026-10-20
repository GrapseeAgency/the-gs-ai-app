"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.config = void 0;
exports.middleware = middleware;
const server_1 = require("next/server");
// TEMP-DIAG: reveal the public origin the platform edge presents on browser
// traffic. Removed after the upload-transport fix ships.
function middleware(req) {
    console.log(`[mw-diag] path=${req.nextUrl.pathname} host=${req.headers.get('host')} xfh=${req.headers.get('x-forwarded-host')} proto=${req.headers.get('x-forwarded-proto')}`);
    return server_1.NextResponse.next();
}
exports.config = {
    matcher: ['/'],
};
