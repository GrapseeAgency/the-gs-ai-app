"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
// Node-side alias hook so the probe can require the app's '@/...' imports.
/* eslint-disable @typescript-eslint/no-explicit-any */
const module_1 = __importDefault(require("module"));
const M = module_1.default;
const orig = M._resolveFilename;
M._resolveFilename = function (request, ...rest) {
    if (request.startsWith('@/'))
        request = '/home/user/the-gs-ai-app/dist/probe/src/' + request.slice(2);
    return orig.call(this, request, ...rest);
};
