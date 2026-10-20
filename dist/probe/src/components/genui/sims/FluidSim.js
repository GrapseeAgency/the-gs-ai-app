"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.FluidSim = FluidSim;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const Stage2D_1 = require("./Stage2D");
/**
 * Fluid — semi-Lagrangian stable fluids on a coarse grid: velocity advection,
 * a few Jacobi pressure iterations, dye advection. Stir with the pointer.
 */
function FluidSim({ payload }) {
    const state = (0, react_1.useRef)({ init: false, token: null, N: 0, M: 0, u: [], v: [], u2: [], v2: [], dye: [], dye2: [], pux: -1, puy: -1 });
    return ((0, jsx_runtime_1.jsx)(Stage2D_1.Stage2D, { title: payload.title || 'Fluid — stable semi-Lagrangian', height: payload.size * 3, knobs: payload, hint: "drag to stir the fluid", onStep: (ctx, w, h, dt, _t, pointer, _ui, resetToken) => {
            const s = state.current;
            const N = 48;
            const M = Math.max(16, Math.round((N * h) / Math.max(1, w)));
            if (!s.init || s.token !== resetToken || s.N !== N || s.M !== M) {
                s.init = true;
                s.token = resetToken;
                s.N = N;
                s.M = M;
                const cells = N * M;
                s.u = new Array(cells).fill(0);
                s.v = new Array(cells).fill(0);
                s.u2 = new Array(cells).fill(0);
                s.v2 = new Array(cells).fill(0);
                s.dye = new Array(cells).fill(0);
                s.dye2 = new Array(cells).fill(0);
                s.pux = -1;
                s.puy = -1;
            }
            const cellW = w / N;
            const cellH = h / M;
            const idx = (x, y) => y * N + x;
            // pointer stir: inject velocity + dye along the drag
            if (pointer.down && s.pux >= 0) {
                const gx = Math.floor(pointer.x / cellW);
                const gy = Math.floor(pointer.y / cellH);
                const dvx = (pointer.x - s.pux) / cellW;
                const dvy = (pointer.y - s.puy) / cellH;
                for (let dy = -1; dy <= 1; dy++)
                    for (let dx = -1; dx <= 1; dx++) {
                        const x = gx + dx;
                        const y = gy + dy;
                        if (x < 1 || y < 1 || x >= N - 1 || y >= M - 1)
                            continue;
                        s.u[idx(x, y)] += dvx * 3;
                        s.v[idx(x, y)] += dvy * 3;
                        s.dye[idx(x, y)] = Math.min(1, s.dye[idx(x, y)] + 0.5);
                    }
            }
            s.pux = pointer.down ? pointer.x : -1;
            s.puy = pointer.down ? pointer.y : -1;
            // advect (semi-Lagrangian)
            const advect = (src, dst) => {
                for (let y = 1; y < M - 1; y++)
                    for (let x = 1; x < N - 1; x++) {
                        const bx = x - s.u[idx(x, y)] * dt * N * 0.12;
                        const by = y - s.v[idx(x, y)] * dt * M * 0.12;
                        const x0 = Math.max(0, Math.min(N - 2, Math.floor(bx)));
                        const y0 = Math.max(0, Math.min(M - 2, Math.floor(by)));
                        const tx = Math.max(0, Math.min(1, bx - x0));
                        const ty = Math.max(0, Math.min(1, by - y0));
                        dst[idx(x, y)] =
                            src[idx(x0, y0)] * (1 - tx) * (1 - ty) +
                                src[idx(x0 + 1, y0)] * tx * (1 - ty) +
                                src[idx(x0, y0 + 1)] * (1 - tx) * ty +
                                src[idx(x0 + 1, y0 + 1)] * tx * ty;
                    }
            };
            advect(s.u, s.u2);
            advect(s.v, s.v2);
            advect(s.dye, s.dye2);
            // pressure-ish diffusion: 8 Jacobi iterations of smoothing the velocity
            for (let k = 0; k < 8; k++) {
                for (let y = 1; y < M - 1; y++)
                    for (let x = 1; x < N - 1; x++) {
                        s.u2[idx(x, y)] = (s.u[idx(x, y)] * 0.6 + (s.u[idx(x - 1, y)] + s.u[idx(x + 1, y)] + s.u[idx(x, y - 1)] + s.u[idx(x, y + 1)]) * 0.1);
                        s.v2[idx(x, y)] = (s.v[idx(x, y)] * 0.6 + (s.v[idx(x - 1, y)] + s.v[idx(x + 1, y)] + s.v[idx(x, y - 1)] + s.v[idx(x, y + 1)]) * 0.1);
                    }
                ;
                [s.u, s.u2] = [s.u2, s.u];
                [s.v, s.v2] = [s.v2, s.v];
            }
            ;
            [s.dye, s.dye2] = [s.dye2, s.dye];
            // decay
            for (let i = 0; i < s.dye.length; i++)
                s.dye[i] *= 0.995;
            // render
            for (let y = 0; y < M; y++)
                for (let x = 0; x < N; x++) {
                    const d = Math.min(1, s.dye[idx(x, y)]);
                    if (d < 0.01)
                        continue;
                    ctx.fillStyle = `hsla(${(200 - d * 140 + 360) % 360}, 85%, ${45 + d * 25}%, ${0.15 + d * 0.8})`;
                    ctx.fillRect(x * cellW, y * cellH, cellW + 1, cellH + 1);
                }
        } }));
}
