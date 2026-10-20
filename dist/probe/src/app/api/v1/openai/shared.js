"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.authOkShim = authOkShim;
exports.flattenContentShim = flattenContentShim;
function authOkShim(req) {
    const required = process.env.GS_BENCH_API_KEY;
    if (!required)
        return true;
    return req.headers.get('authorization') === `Bearer ${required}`;
}
function flattenContentShim(content) {
    if (typeof content === 'string')
        return content;
    if (Array.isArray(content)) {
        return content
            .map((part) => part && typeof part === 'object' && typeof part.text === 'string'
            ? part.text
            : '')
            .join('');
    }
    return '';
}
