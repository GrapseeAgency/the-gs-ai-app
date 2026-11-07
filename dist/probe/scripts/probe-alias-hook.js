"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * Node-side alias hook: lets a compiled-out probe require the app's '@/...'
 * imports. Compile with tsconfig.probe.json (emits to dist/probe), then:
 *   node dist/probe/scripts/test-genui-pipeline.js
 */
/* eslint-disable @typescript-eslint/no-explicit-any */
const module_1 = __importDefault(require("module"));
const path_1 = require("path");
const M = module_1.default;
const orig = M._resolveFilename;
const compiledSrc = (0, path_1.join)(__dirname, '..', 'src');
M._resolveFilename = function (request, ...rest) {
    if (request.startsWith('@/'))
        request = (0, path_1.join)(compiledSrc, request.slice(2));
    return orig.call(this, request, ...rest);
};
