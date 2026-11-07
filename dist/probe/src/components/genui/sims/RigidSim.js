"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.RigidSim = RigidSim;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const Stage2D_1 = require("./Stage2D");
/** Rigid — stacked boxes under gravity with impulse resolution and toppling. */
function RigidSim({ payload }) {
    const state = (0, react_1.useRef)({ boxes: [], init: false, token: null });
    const n = Math.max(4, Math.min(24, Math.round(payload.count / 3)));
    return ((0, jsx_runtime_1.jsx)(Stage2D_1.Stage2D, { title: payload.title || 'Rigid bodies — stacking & toppling', height: payload.size * 3, knobs: payload, hint: "boxes knock each other over", onStep: (ctx, w, h, dt, _t, pointer, ui, resetToken) => {
            const s = state.current;
            let rng = () => 0.5;
            if (!s.init || s.token !== resetToken) {
                let a = payload.seed * 7919;
                rng = () => ((a = (a * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
                s.init = true;
                s.token = resetToken;
                s.boxes = Array.from({ length: n }, (_, i) => ({
                    x: w / 2 + (rng() - 0.5) * w * 0.3,
                    y: h - 20 - i * 24,
                    vx: 0,
                    vy: 0,
                    w: 30 + rng() * 22,
                    h: 16 + rng() * 12,
                    a: (rng() - 0.5) * 0.4,
                    va: 0,
                    settled: false,
                }));
            }
            const floor = h - 14;
            for (const b of s.boxes) {
                if (pointer.down) {
                    const dx = b.x - pointer.x;
                    const dy = b.y - pointer.y;
                    if (Math.hypot(dx, dy) < 40) {
                        b.vx += dx * 6 * dt * 10;
                        b.vy += dy * 6 * dt * 10;
                        b.va += (rng() - 0.5) * 4 * dt * 10;
                    }
                }
                b.vy += ui.gravity * 60 * dt;
                b.vx *= ui.damping;
                b.vy *= ui.damping;
                b.x += b.vx * dt;
                b.y += b.vy * dt;
                b.a += b.va * dt;
                if (b.y + b.h / 2 > floor) {
                    b.y = floor - b.h / 2;
                    if (b.vy > 20)
                        b.va += (rng() - 0.5) * 3;
                    b.vy *= -0.28;
                    b.vx *= 0.86;
                    b.va *= 0.9;
                }
                if (b.x - b.w / 2 < 0 && b.vx < 0) {
                    b.x = b.w / 2;
                    b.vx *= -0.5;
                }
                if (b.x + b.w / 2 > w && b.vx > 0) {
                    b.x = w - b.w / 2;
                    b.vx *= -0.5;
                }
            }
            // pairwise separation (AABB approx with angular wobble)
            for (let i = 0; i < s.boxes.length; i++)
                for (let j = i + 1; j < s.boxes.length; j++) {
                    const a = s.boxes[i];
                    const b = s.boxes[j];
                    const dx = b.x - a.x;
                    const dy = b.y - a.y;
                    const ox = (a.w + b.w) / 2 - Math.abs(dx);
                    const oy = (a.h + b.h) / 2 - Math.abs(dy);
                    if (ox > 0 && oy > 0) {
                        if (oy < ox) {
                            const sign = dy >= 0 ? 1 : -1;
                            b.y += (oy / 2) * sign;
                            a.y -= (oy / 2) * sign;
                            b.vy *= 0.7;
                            a.vy *= 0.7;
                            b.va += dx * 0.01;
                            a.va -= dx * 0.01;
                        }
                        else {
                            const sign = dx >= 0 ? 1 : -1;
                            b.x += (ox / 2) * sign;
                            a.x -= (ox / 2) * sign;
                            b.vx *= 0.7;
                            a.vx *= 0.7;
                        }
                    }
                }
            for (const b of s.boxes) {
                ctx.save();
                ctx.translate(b.x, b.y);
                ctx.rotate(b.a);
                ctx.fillStyle = `hsla(${(b.x / Math.max(1, w)) * 60 + 160}, 70%, 55%, 0.85)`;
                ctx.strokeStyle = 'rgba(255,255,255,0.25)';
                ctx.beginPath();
                ctx.roundRect(-b.w / 2, -b.h / 2, b.w, b.h, 4);
                ctx.fill();
                ctx.stroke();
                ctx.restore();
            }
            ctx.fillStyle = 'rgba(128,140,155,0.5)';
            ctx.fillRect(0, floor + 6, w, 2);
        } }));
}
