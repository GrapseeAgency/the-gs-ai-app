"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.SoftBodySim = SoftBodySim;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const Stage2D_1 = require("./Stage2D");
/** SoftBody — a pressurized spring ring that squishes, bounces and recovers. */
function SoftBodySim({ payload }) {
    const state = (0, react_1.useRef)({ ring: [], init: false, token: null });
    const n = Math.max(10, Math.min(30, Math.round(payload.count / 3) + 8));
    return ((0, jsx_runtime_1.jsx)(Stage2D_1.Stage2D, { title: payload.title || 'Soft body — pressure model', height: payload.size * 3, knobs: payload, hint: "grab and fling the blob", onStep: (ctx, w, h, dt, _t, pointer, ui, resetToken) => {
            const s = state.current;
            if (!s.init || s.token !== resetToken) {
                s.init = true;
                s.token = resetToken;
                const R = Math.min(w, h) * 0.22;
                s.ring = Array.from({ length: n }, (_, i) => {
                    const a = (i / n) * Math.PI * 2;
                    const x = w / 2 + R * Math.cos(a);
                    const y = h * 0.45 + R * Math.sin(a);
                    return { x, y, px: x, py: y };
                });
            }
            const g = ui.gravity * 60;
            const R0 = Math.min(w, h) * 0.22;
            // grab nearest node
            let grabbed = -1;
            if (pointer.down) {
                let bd = 900;
                s.ring.forEach((p, i) => {
                    const d = (p.x - pointer.x) ** 2 + (p.y - pointer.y) ** 2;
                    if (d < bd) {
                        bd = d;
                        grabbed = i;
                    }
                });
            }
            const sub = 2;
            const sdt = dt / sub;
            for (let k = 0; k < sub; k++) {
                for (const p of s.ring) {
                    const vx = (p.x - p.px) * ui.damping * 0.995;
                    const vy = (p.y - p.py) * ui.damping * 0.995;
                    p.px = p.x;
                    p.py = p.y;
                    p.x += vx;
                    p.y += vy + g * sdt * sdt;
                }
                if (grabbed >= 0 && pointer.down) {
                    const p = s.ring[grabbed];
                    p.px = p.x;
                    p.py = p.y;
                    p.x = pointer.x;
                    p.y = pointer.y;
                }
                // springs around the ring + area (pressure) constraint
                const area = polygonArea(s.ring);
                const target = Math.PI * R0 * R0;
                const pressure = (target - area) / target;
                const cx = s.ring.reduce((acc, p) => acc + p.x, 0) / n;
                const cy = s.ring.reduce((acc, p) => acc + p.y, 0) / n;
                for (let i = 0; i < n; i++) {
                    const a = s.ring[i];
                    const b = s.ring[(i + 1) % n];
                    const rest = 2 * R0 * Math.sin(Math.PI / n);
                    const dx = b.x - a.x;
                    const dy = b.y - a.y;
                    const d = Math.hypot(dx, dy) || 1e-6;
                    const diff = ((d - rest) / d) * 0.35;
                    a.x += dx * diff;
                    a.y += dy * diff;
                    b.x -= dx * diff;
                    b.y -= dy * diff;
                    // radial pressure push
                    const rx = a.x - cx;
                    const ry = a.y - cy;
                    const rd = Math.hypot(rx, ry) || 1e-6;
                    const push = pressure * 60 * sdt;
                    a.x += (rx / rd) * push;
                    a.y += (ry / rd) * push;
                }
                // floor
                for (const p of s.ring) {
                    if (p.y > h - 16) {
                        p.y = h - 16;
                        p.px = p.x - (p.x - p.px) * 0.6;
                        p.py = p.y + (p.y - p.py) * 0.4;
                    }
                    if (p.x < 10)
                        p.x = 10;
                    if (p.x > w - 10)
                        p.x = w - 10;
                }
            }
            // draw
            ctx.beginPath();
            ctx.moveTo(s.ring[0].x, s.ring[0].y);
            for (let i = 1; i <= n; i++) {
                const a = s.ring[i % n];
                const b = s.ring[(i + 1) % n];
                ctx.quadraticCurveTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2);
            }
            ctx.closePath();
            ctx.fillStyle = 'rgba(80,200,160,0.30)';
            ctx.fill();
            ctx.strokeStyle = 'rgba(80,220,180,0.95)';
            ctx.lineWidth = 2;
            ctx.stroke();
        } }));
}
function polygonArea(ring) {
    let a = 0;
    for (let i = 0; i < ring.length; i++) {
        const p = ring[i];
        const q = ring[(i + 1) % ring.length];
        a += p.x * q.y - q.x * p.y;
    }
    return Math.abs(a) / 2;
}
